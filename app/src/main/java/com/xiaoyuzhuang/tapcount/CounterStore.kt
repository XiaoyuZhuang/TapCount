package com.xiaoyuzhuang.tapcount

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class CounterProject(
    val id: Long, val name: String, val folderId: Long,
    val count: Int, val lastDay: String, val resetDaily: Boolean
)
data class CounterFolder(val id: Long, val name: String)
data class DayScore(val day: String, val count: Int)
data class CounterEvent(val at: Long, val kind: String, val delta: Int, val after: Int)
data class HourScore(val hour: Int, val taps: Int, val points: Int)
data class TapOutcome(
    val project: CounterProject, val rewarded: Boolean,
    val points: Int, val openDashboard: Boolean
)

class CounterStore(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "tapcount.db", null, 2) {

    private val prefs get() = ctx.getSharedPreferences("tapcount", Context.MODE_PRIVATE)
    private fun day() = LocalDate.now().toString()
    private fun stateTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS counter_state (project_id INTEGER PRIMARY KEY, pity INTEGER NOT NULL DEFAULT 0, periodic_taps INTEGER NOT NULL DEFAULT 0)")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, folder_id INTEGER NOT NULL, current_count INTEGER NOT NULL DEFAULT 0, last_day TEXT NOT NULL, auto_reset INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE daily (project_id INTEGER NOT NULL, day TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(project_id, day))")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, day TEXT NOT NULL, at_ms INTEGER NOT NULL, kind TEXT NOT NULL, delta INTEGER NOT NULL, after_count INTEGER NOT NULL)")
        stateTable(db)
        val folderId = db.insertOrThrow("folders", null,
            ContentValues().apply { put("name", ctx.getString(R.string.default_folder)) })
        val projectId = db.insertOrThrow("projects", null, ContentValues().apply {
            put("name", ctx.getString(R.string.default_project))
            put("folder_id", folderId); put("current_count", 0)
            put("last_day", day()); put("auto_reset", 1)
        })
        db.execSQL("INSERT OR IGNORE INTO counter_state(project_id) VALUES (?)", arrayOf(projectId))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) stateTable(db) // Preserve existing counters, events and projects.
    }
    private fun readProject(db: SQLiteDatabase, id: Long): CounterProject? {
        db.rawQuery("SELECT id,name,folder_id,current_count,last_day,auto_reset FROM projects WHERE id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return CounterProject(c.getLong(0), c.getString(1), c.getLong(2),
                c.getInt(3), c.getString(4), c.getInt(5) != 0)
        }
    }
    fun project(id: Long) = readProject(readableDatabase, id)
    fun folders(): List<CounterFolder> {
        val out = mutableListOf<CounterFolder>()
        readableDatabase.rawQuery("SELECT id,name FROM folders ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(CounterFolder(c.getLong(0), c.getString(1)))
        }
        return out
    }
    fun projects(): List<CounterProject> {
        val out = mutableListOf<CounterProject>()
        readableDatabase.rawQuery("SELECT id,name,folder_id,current_count,last_day,auto_reset FROM projects ORDER BY id", null).use { c ->
            while (c.moveToNext()) out.add(CounterProject(c.getLong(0), c.getString(1),
                c.getLong(2), c.getInt(3), c.getString(4), c.getInt(5) != 0))
        }
        return out
    }
    fun activeId() = prefs.getLong("active_project", 1L)
    fun setActive(id: Long) {
        if (project(id) != null) prefs.edit().putLong("active_project", id).apply()
    }
    private fun rollover(db: SQLiteDatabase, p: CounterProject): CounterProject {
        if (p.lastDay == day()) return p
        val next = if (p.resetDaily) 0 else p.count
        db.execSQL("UPDATE projects SET current_count=?,last_day=? WHERE id=?",
            arrayOf(next, day(), p.id))
        return p.copy(count = next, lastDay = day())
    }
    fun activeProject(): CounterProject {
        val db = writableDatabase
        val p = readProject(db, activeId()) ?: projects().first()
        return rollover(db, p)
    }
    fun addFolder(name: String) = writableDatabase.insertOrThrow("folders", null,
        ContentValues().apply { put("name", name.trim().take(60)) })
    fun addProject(name: String, folderId: Long): Long {
        val db = writableDatabase
        val id = db.insertOrThrow("projects", null, ContentValues().apply {
            put("name", name.trim().take(60)); put("folder_id", folderId)
            put("current_count", 0); put("last_day", day()); put("auto_reset", 1)
        })
        db.execSQL("INSERT OR IGNORE INTO counter_state(project_id) VALUES (?)", arrayOf(id))
        return id
    }
    fun renameProject(id: Long, name: String) {
        writableDatabase.execSQL("UPDATE projects SET name=? WHERE id=?",
            arrayOf(name.trim().take(60), id))
    }
    fun setDailyReset(id: Long, enabled: Boolean) {
        val db = writableDatabase
        val p = readProject(db, id) ?: return
        rollover(db, p)
        db.execSQL("UPDATE projects SET auto_reset=? WHERE id=?", arrayOf(if (enabled) 1 else 0, id))
    }

    private fun dayValue(db: SQLiteDatabase, id: Long, date: String): Int {
        db.rawQuery("SELECT count FROM daily WHERE project_id=? AND day=?",
            arrayOf(id.toString(), date)).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
    fun dailyCount(id: Long, date: String) = dayValue(readableDatabase, id, date)

    private fun record(db: SQLiteDatabase, p: CounterProject, next: Int, kind: String): CounterProject {
        val delta = next - p.count
        db.execSQL("UPDATE projects SET current_count=?,last_day=? WHERE id=?", arrayOf(next, day(), p.id))
        val dayCount = (dayValue(db, p.id, day()).toLong() + delta)
            .coerceIn(0L, 1_000_000_000L).toInt()
        db.insertWithOnConflict("daily", null, ContentValues().apply {
            put("project_id", p.id); put("day", day()); put("count", dayCount)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        db.insertOrThrow("events", null, ContentValues().apply {
            put("project_id", p.id); put("day", day())
            put("at_ms", System.currentTimeMillis()); put("kind", kind)
            put("delta", delta); put("after_count", next)
        })
        return p.copy(count = next, lastDay = day())
    }

    /** One atomic transaction per physical tap; pity counts taps, NOT awarded points. */
    fun tapActive(): TapOutcome {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val p = rollover(db, readProject(db, activeId()) ?: projects().first())
            db.execSQL("INSERT OR IGNORE INTO counter_state(project_id) VALUES (?)", arrayOf(p.id))
            var pity = 0
            var periodic = 0
            db.rawQuery("SELECT pity,periodic_taps FROM counter_state WHERE project_id=?",
                arrayOf(p.id.toString())).use { c ->
                if (c.moveToFirst()) { pity = c.getInt(0); periodic = c.getInt(1) }
            }
            val rewardOn = prefs.getBoolean("bonus_enabled", false)
            val guarantee = prefs.getInt("bonus_pity", 10).coerceIn(1, 1000)
            val award = prefs.getInt("bonus_amount", 10).coerceIn(1, 100000)
            val nextPity = pity + 1
            val won = rewardOn && (nextPity >= guarantee || Random.nextInt(guarantee) == 0)
            val points = if (won) award else 1
            val next = (p.count.toLong() + points).coerceAtMost(1_000_000_000L).toInt()
            val mode = prefs.getString("entry_mode", "double")
            val every = prefs.getInt("periodic_interval", 10).coerceIn(2, 1000)
            val sequence = if (mode == "periodic") periodic + 1 else 0
            val opens = mode == "periodic" && sequence >= every
            db.execSQL("UPDATE counter_state SET pity=?,periodic_taps=? WHERE project_id=?",
                arrayOf(if (rewardOn) (if (won) 0 else nextPity) else pity,
                    if (opens) 0 else sequence, p.id))
            val updated = record(db, p, next, if (won) "bonus" else "tap")
            db.setTransactionSuccessful()
            return TapOutcome(updated, won, next - p.count, opens)
        } finally { db.endTransaction() }
    }

    fun adjustActive(kind: String, value: Int = 0): CounterProject {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val p = rollover(db, readProject(db, activeId()) ?: projects().first())
            val next = when (kind) {
                "add" -> (p.count.toLong() + value.toLong()).coerceIn(0L, 1_000_000_000L).toInt()
                "set" -> value.coerceIn(0, 1_000_000_000)
                "reset" -> 0
                else -> error("Invalid operation")
            }
            val updated = record(db, p, next, kind)
            db.setTransactionSuccessful()
            return updated
        } finally { db.endTransaction() }
    }
    fun total(id: Long): Long {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(count),0) FROM daily WHERE project_id=?",
            arrayOf(id.toString())).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0L }
    }
    /** Any historical window, not just the last 7 or 30 days. */
    fun dayWindow(id: Long, end: LocalDate, count: Int): List<DayScore> =
        (count - 1 downTo 0).map { offset ->
            val date = end.minusDays(offset.toLong()).toString()
            DayScore(date, dailyCount(id, date))
        }
    fun recent(id: Long, count: Int): List<DayScore> =
        dayWindow(id, LocalDate.now(), count)
    fun events(id: Long, date: String): List<CounterEvent> {
        val list = mutableListOf<CounterEvent>()
        readableDatabase.rawQuery(
            "SELECT at_ms,kind,delta,after_count FROM events WHERE project_id=? AND day=? ORDER BY at_ms DESC,id DESC",
            arrayOf(id.toString(), date)).use { c ->
            while (c.moveToNext()) list.add(CounterEvent(c.getLong(0),c.getString(1),c.getInt(2),c.getInt(3)))
        }
        return list
    }
    /** Activity is measured in completed taps: bonus points belong to their actual tap hour. */
    fun hourly(id: Long, date: String): List<HourScore> {
        val taps = IntArray(24)
        val points = IntArray(24)
        for (event in events(id, date)) {
            if (event.kind != "tap" && event.kind != "bonus") continue
            val hour = Instant.ofEpochMilli(event.at).atZone(ZoneId.systemDefault()).hour
            taps[hour]++
            points[hour] += event.delta
        }
        val first = (0..23).firstOrNull { taps[it] > 0 } ?: return emptyList()
        val last = (23 downTo 0).first { taps[it] > 0 }
        return (first..last).map { HourScore(it, taps[it], points[it]) }
    }

    fun exportJson(): String {
        val db = readableDatabase
        val root = JSONObject().put("format", 2).put("active_project", activeId())
        val definitions = listOf(
            "folders" to "SELECT id,name FROM folders",
            "projects" to "SELECT id,name,folder_id,current_count,last_day,auto_reset FROM projects",
            "daily" to "SELECT project_id,day,count FROM daily",
            "events" to "SELECT id,project_id,day,at_ms,kind,delta,after_count FROM events",
            "counter_state" to "SELECT project_id,pity,periodic_taps FROM counter_state"
        )
        for ((table, query) in definitions) {
            val arr = JSONArray()
            db.rawQuery(query, null).use { c ->
                while (c.moveToNext()) {
                    val obj = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val v: Any = when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            else -> c.getString(i) ?: ""
                        }
                        obj.put(c.getColumnName(i), v)
                    }
                    arr.put(obj)
                }
            }
            root.put(table, arr)
        }
        return root.toString(2)
    }
    fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.getInt("format") in 1..2)
        val tables = listOf("folders", "projects", "daily", "events")
        val all = if (root.optJSONArray("counter_state") != null) tables + "counter_state" else tables
        for (table in all) require(root.getJSONArray(table).length() <= 200_000)
        require(root.getJSONArray("projects").length() > 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("counter_state", "events", "daily", "projects", "folders"))
                db.delete(table, null, null)
            for (table in all) {
                val entries = root.getJSONArray(table)
                for (i in 0 until entries.length()) {
                    val row = entries.getJSONObject(i)
                    val cv = ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        when (val v = row.get(key)) {
                            is Int -> cv.put(key, v)
                            is Long -> cv.put(key, v)
                            is String -> cv.put(key, v)
                            else -> error("Unsupported backup value")
                        }
                    }
                    db.insertOrThrow(table, null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        prefs.edit().putLong("active_project", root.optLong("active_project", 1L)).apply()
    }
}
