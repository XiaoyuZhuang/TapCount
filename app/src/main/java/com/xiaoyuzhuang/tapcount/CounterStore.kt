package com.xiaoyuzhuang.tapcount

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class CounterProject(
    val id: Long, val name: String, val folderId: Long,
    val count: Int, val lastDay: String, val resetDaily: Boolean
)
data class CounterFolder(val id: Long, val name: String)
data class DayScore(val day: String, val count: Int)
data class CounterEvent(val at: Long, val kind: String, val delta: Int, val after: Int)

class CounterStore(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "tapcount.db", null, 1) {

    private val prefs get() = ctx.getSharedPreferences("tapcount", Context.MODE_PRIVATE)
    private fun day() = LocalDate.now().toString()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, folder_id INTEGER NOT NULL, current_count INTEGER NOT NULL DEFAULT 0, last_day TEXT NOT NULL, auto_reset INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE daily (project_id INTEGER NOT NULL, day TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(project_id, day))")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, project_id INTEGER NOT NULL, day TEXT NOT NULL, at_ms INTEGER NOT NULL, kind TEXT NOT NULL, delta INTEGER NOT NULL, after_count INTEGER NOT NULL)")
        val folder = ContentValues().apply { put("name", ctx.getString(R.string.default_folder)) }
        val folderId = db.insertOrThrow("folders", null, folder)
        db.insertOrThrow("projects", null, ContentValues().apply {
            put("name", ctx.getString(R.string.default_project))
            put("folder_id", folderId); put("current_count", 0)
            put("last_day", day()); put("auto_reset", 1)
        })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun readProject(db: SQLiteDatabase, id: Long): CounterProject? {
        db.rawQuery("SELECT id,name,folder_id,current_count,last_day,auto_reset FROM projects WHERE id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return CounterProject(c.getLong(0), c.getString(1), c.getLong(2),
                c.getInt(3), c.getString(4), c.getInt(5) != 0)
        }
    }

    fun project(id: Long): CounterProject? = readProject(readableDatabase, id)
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

    fun addFolder(name: String): Long {
        return writableDatabase.insertOrThrow("folders", null,
            ContentValues().apply { put("name", name.trim().take(60)) })
    }

    fun addProject(name: String, folderId: Long): Long {
        return writableDatabase.insertOrThrow("projects", null, ContentValues().apply {
            put("name", name.trim().take(60)); put("folder_id", folderId)
            put("current_count", 0); put("last_day", day()); put("auto_reset", 1)
        })
    }

    fun renameProject(id: Long, name: String) {
        writableDatabase.execSQL("UPDATE projects SET name=? WHERE id=?",
            arrayOf(name.trim().take(60), id))
    }

    fun setDailyReset(projectId: Long, enabled: Boolean) {
        val db = writableDatabase
        val p = readProject(db, projectId) ?: return
        rollover(db, p)
        db.execSQL("UPDATE projects SET auto_reset=? WHERE id=?",
            arrayOf(if (enabled) 1 else 0, projectId))
    }

    private fun dayValue(db: SQLiteDatabase, projectId: Long, date: String): Int {
        db.rawQuery("SELECT count FROM daily WHERE project_id=? AND day=?",
            arrayOf(projectId.toString(), date)).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun dailyCount(projectId: Long, date: String): Int =
        dayValue(readableDatabase, projectId, date)

    private fun change(projectId: Long, kind: String, value: Int): CounterProject {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var p = readProject(db, projectId) ?: error("Unknown counter")
            p = rollover(db, p)
            val next = when (kind) {
                "tap" -> (p.count.toLong() + 1L).coerceAtMost(1_000_000_000L).toInt()
                "add" -> (p.count.toLong() + value.toLong()).coerceIn(0L, 1_000_000_000L).toInt()
                "set" -> value.coerceIn(0, 1_000_000_000)
                "reset" -> 0
                else -> error("Invalid operation")
            }
            val delta = next - p.count
            db.execSQL("UPDATE projects SET current_count=?,last_day=? WHERE id=?",
                arrayOf(next, day(), p.id))
            val oldDaily = dayValue(db, p.id, day())
            val dailyAfter = (oldDaily.toLong() + delta).coerceIn(0L, 1_000_000_000L).toInt()
            db.insertWithOnConflict("daily", null, ContentValues().apply {
                put("project_id", p.id); put("day", day()); put("count", dailyAfter)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.insertOrThrow("events", null, ContentValues().apply {
                put("project_id", p.id); put("day", day())
                put("at_ms", System.currentTimeMillis()); put("kind", kind)
                put("delta", delta); put("after_count", next)
            })
            db.setTransactionSuccessful()
            return p.copy(count = next, lastDay = day())
        } finally {
            db.endTransaction()
        }
    }

    fun incrementActive(): CounterProject = change(activeProject().id, "tap", 1)
    fun adjustActive(kind: String, value: Int = 0): CounterProject =
        change(activeProject().id, kind, value)

    fun total(projectId: Long): Long {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(count),0) FROM daily WHERE project_id=?",
            arrayOf(projectId.toString())).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    fun recent(projectId: Long, count: Int): List<DayScore> =
        (count - 1 downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong()).toString()
            DayScore(date, dailyCount(projectId, date))
        }

    fun events(projectId: Long, date: String): List<CounterEvent> {
        val list = mutableListOf<CounterEvent>()
        readableDatabase.rawQuery(
            "SELECT at_ms,kind,delta,after_count FROM events WHERE project_id=? AND day=? ORDER BY at_ms DESC,id DESC",
            arrayOf(projectId.toString(), date)
        ).use { c ->
            while (c.moveToNext())
                list.add(CounterEvent(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
        }
        return list
    }

    fun exportJson(): String {
        val db = readableDatabase
        val root = JSONObject().put("format", 1).put("active_project", activeId())
        val definitions = listOf(
            "folders" to "SELECT id,name FROM folders",
            "projects" to "SELECT id,name,folder_id,current_count,last_day,auto_reset FROM projects",
            "daily" to "SELECT project_id,day,count FROM daily",
            "events" to "SELECT id,project_id,day,at_ms,kind,delta,after_count FROM events"
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
        require(root.getInt("format") == 1)
        val tables = listOf("folders", "projects", "daily", "events")
        for (table in tables) require(root.getJSONArray(table).length() <= 200_000)
        require(root.getJSONArray("projects").length() > 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in listOf("events", "daily", "projects", "folders")) db.delete(table, null, null)
            for (table in tables) {
                val entries = root.getJSONArray(table)
                for (i in 0 until entries.length()) {
                    val row = entries.getJSONObject(i)
                    val cv = ContentValues()
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val v = row.get(key)
                        when (v) {
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
        } finally {
            db.endTransaction()
        }
        prefs.edit().putLong("active_project", root.optLong("active_project", 1L)).apply()
    }
}
