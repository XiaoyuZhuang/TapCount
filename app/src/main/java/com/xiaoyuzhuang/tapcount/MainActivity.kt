package com.xiaoyuzhuang.tapcount

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var store: CounterStore
    private lateinit var body: LinearLayout
    private var page = 0
    private var days = 7
    private var isDark = false

    private val bg get() = if (isDark) Color.rgb(17, 21, 30) else Color.rgb(246, 248, 252)
    private val surface get() = if (isDark) Color.rgb(34, 41, 55) else Color.WHITE
    private val primary get() = if (isDark) Color.rgb(156, 187, 255) else Color.rgb(45, 85, 174)
    private val fg get() = if (isDark) Color.WHITE else Color.rgb(26, 36, 53)
    private val muted get() = if (isDark) Color.rgb(171, 183, 204) else Color.rgb(99, 111, 130)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.localize(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isDark = UiPrefs.dark(this)
        setTheme(if (isDark) R.style.AppThemeDark else R.style.AppTheme)
        super.onCreate(savedInstanceState)
        store = CounterStore(this)
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = bg
        render()
        // Android 13+ permission can only be requested in visible management UI,
        // never from the invisible one-tap launcher activity.
        if (Build.VERSION.SDK_INT >= 33 && UiPrefs.bool(this, "notification", true) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED &&
            !UiPrefs.bool(this, "notification_permission_asked")) {
            UiPrefs.setBool(this, "notification_permission_asked", true)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun round(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun txt(text: String, size: Float = 15f, bold: Boolean = false, color: Int = fg): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun space(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun card(): LinearLayout = column().apply {
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = round(surface, 13)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        body.addView(this)
    }

    private fun action(label: String, onClick: () -> Unit, emphasized: Boolean = false): TextView =
        txt(label, 14f, true, if (emphasized) Color.WHITE else primary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = round(if (emphasized) primary else
                if (isDark) Color.rgb(48, 61, 83) else Color.rgb(231, 238, 255), 12)
            setOnClickListener { onClick() }
        }

    private fun addAction(holder: LinearLayout, label: String, emphasized: Boolean = false, onClick: () -> Unit) {
        holder.addView(action(label, onClick, emphasized),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
    }

    private fun line(holder: LinearLayout, title: String, value: String, clickable: (() -> Unit)? = null) {
        val r = row()
        r.setPadding(0, dp(6), 0, dp(6))
        val label = txt(title, 14f, false, fg)
        r.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(txt(value, 14f, true, muted))
        if (clickable != null) r.setOnClickListener { clickable() }
        holder.addView(r)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun render() {
        isDark = UiPrefs.dark(this)
        val outer = column().apply { setBackgroundColor(bg) }
        val header = column().apply { setPadding(dp(16), dp(11), dp(16), dp(6)) }
        header.addView(txt(getString(R.string.app_name), 22f, true))
        outer.addView(header)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        body = column().apply {
            setPadding(dp(12), dp(2), dp(12), dp(10))
        }
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = row().apply {
            setPadding(dp(8), dp(4), dp(8), dp(5))
            setBackgroundColor(surface)
        }
        val tabs = intArrayOf(R.string.home, R.string.history, R.string.projects, R.string.settings)
        tabs.forEachIndexed { index, stringId ->
            val tab = txt(getString(stringId), 13f, index == page,
                if (index == page) primary else muted)
            tab.gravity = Gravity.CENTER
            tab.setPadding(0, dp(7), 0, dp(7))
            tab.setOnClickListener { page = index; render() }
            nav.addView(tab, LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        outer.addView(nav)
        setContentView(outer)
        when (page) {
            0 -> showHome()
            1 -> showHistory()
            2 -> showProjects()
            else -> showSettings()
        }
    }

    private fun statCell(label: String, value: String): LinearLayout = column().apply {
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = round(if (isDark) Color.rgb(42, 50, 67) else Color.rgb(241, 245, 251), 10)
        addView(txt(label, 11f, false, muted))
        addView(txt(value, 19f, true, fg))
    }

    private fun statPair(holder: LinearLayout,
                         label1: String, value1: String,
                         label2: String, value2: String) {
        val r = row().apply { setPadding(0, dp(3), 0, dp(3)) }
        r.addView(statCell(label1, value1),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dp(8) })
        r.addView(statCell(label2, value2),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        holder.addView(r)
    }

    private fun showHome() {
        val p = store.activeProject()
        val recent = store.recent(p.id, 2)
        val yesterday = recent.first().count
        val daily = recent.last().count

        val top = card()
        val headline = row()
        val labelBlock = column()
        labelBlock.addView(txt(p.name, 16f, true))
        labelBlock.addView(txt(getString(R.string.current_count), 11f, false, muted))
        headline.addView(labelBlock,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headline.addView(txt(p.count.toString(), 42f, true, primary))
        top.addView(headline)
        top.addView(space(6))
        statPair(top, getString(R.string.today), daily.toString(),
            getString(R.string.yesterday), yesterday.toString())
        statPair(top, getString(R.string.all_time), store.total(p.id).toString(),
            getString(R.string.day_difference),
            (if (daily - yesterday >= 0) "+" else "") + (daily - yesterday))
        addAction(top, getString(R.string.edit_count), true) { editMenu() }

        val c = card()
        c.addView(txt(getString(R.string.day_chart), 15f, true))
        c.addView(BarChart(this, store.recent(p.id, 7), isDark))
        addAction(c, getString(R.string.daily_history)) { page = 1; render() }
    }

    private fun showHistory() {
        val p = store.activeProject()
        val c = card()
        c.addView(txt(p.name + " · " + getString(R.string.day_chart), 15f, true))
        val selector = row().apply { setPadding(0, dp(6), 0, dp(5)) }
        val seven = action(getString(R.string.recent_7), { days = 7; render() }, days == 7)
        val thirty = action(getString(R.string.recent_30), { days = 30; render() }, days == 30)
        selector.addView(seven, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(7) })
        selector.addView(thirty, LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(selector)
        c.addView(BarChart(this, store.recent(p.id, days), isDark))

        val listCard = card()
        listCard.addView(txt(getString(R.string.daily_history), 17f, true))
        val records = store.recent(p.id, days).reversed()
        for (pair in records.chunked(2)) {
            val r = row().apply { setPadding(0, dp(2), 0, dp(2)) }
            for ((index, record) in pair.withIndex()) {
                val item = row().apply {
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                    background = round(if (isDark) Color.rgb(42, 50, 67) else
                        Color.rgb(241, 245, 251), 9)
                }
                val compactDate = record.day.substring(5)
                item.addView(txt(compactDate, 12f, false, muted),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                item.addView(txt(record.count.toString(), 14f, true))
                item.setOnClickListener { showDayDetails(p.id, record.day) }
                r.addView(item, LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (index == 0) rightMargin = dp(8) })
            }
            if (pair.size == 1) r.addView(View(this),
                LinearLayout.LayoutParams(0, dp(1), 1f))
            listCard.addView(r)
        }
    }

    private fun showDayDetails(projectId: Long, date: String) {
        val events = store.events(projectId, date)
        val content = if (events.isEmpty()) getString(R.string.no_records)
        else events.joinToString("\n") { event ->
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(event.at))
            val kind = when (event.kind) {
                "tap" -> getString(R.string.log_tap)
                "add" -> getString(R.string.log_add)
                "set" -> getString(R.string.log_set)
                else -> getString(R.string.log_reset)
            }
            time + "  " + kind + "  " +
                (if (event.delta >= 0) "+" else "") + event.delta + "  → " + event.after
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.activity_history, date))
            .setMessage(content)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun askName(title: String, initial: String = "", onAccept: (String) -> Unit) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            setSelection(text.length)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title).setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast(getString(R.string.empty_name))
                else { onAccept(name); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun showProjects() {
        val p = store.activeProject()
        val note = card()
        note.addView(txt(getString(R.string.tap_to_activate), 13f, false, muted))
        addAction(note, getString(R.string.new_folder)) {
            askName(getString(R.string.folder_name)) { name ->
                store.addFolder(name); render()
            }
        }
        for (folder in store.folders()) {
            val c = card()
            c.addView(txt("▣  " + folder.name, 17f, true))
            store.projects().filter { it.folderId == folder.id }.forEach { project ->
                val isActive = project.id == p.id
                val label = project.name + (if (isActive) "  ✓" else "")
                val item = row().apply { setPadding(0, dp(12), 0, dp(12)) }
                item.addView(txt(label, 15f, isActive, if (isActive) primary else fg),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                item.addView(txt(project.count.toString(), 14f, true, muted))
                item.setOnClickListener {
                    store.setActive(project.id)
                    Shortcuts.refresh(applicationContext, store.activeProject().count)
                    page = 0; render()
                }
                item.setOnLongClickListener {
                    askName(getString(R.string.rename), project.name) { name ->
                        store.renameProject(project.id, name); render()
                    }
                    true
                }
                c.addView(item)
            }
            addAction(c, getString(R.string.new_project)) {
                askName(getString(R.string.project_name)) { name ->
                    val id = store.addProject(name, folder.id)
                    store.setActive(id)
                    Shortcuts.refresh(applicationContext, 0)
                    page = 0; render()
                }
            }
        }
    }

    private fun editMenu() {
        val labels = arrayOf(
            getString(R.string.add_amount), getString(R.string.subtract_amount),
            getString(R.string.set_count), getString(R.string.reset_count)
        )
        AlertDialog.Builder(this).setTitle(R.string.edit_count).setItems(labels) { _, option ->
            when (option) {
                0 -> askNumber(labels[0], "add")
                1 -> askNumber(labels[1], "subtract")
                2 -> askNumber(labels[2], "set")
                3 -> AlertDialog.Builder(this)
                    .setTitle(R.string.reset_count).setMessage(R.string.confirm_reset)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        commitCount("reset", 0)
                    }.show()
            }
        }.show()
    }

    private fun askNumber(title: String, kind: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0"
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toLongOrNull()
                if (value == null || value < 0L || value > 1_000_000_000L) {
                    toast(getString(R.string.invalid_number))
                } else {
                    commitCount(if (kind == "subtract") "add" else kind,
                        if (kind == "subtract") -value.toInt() else value.toInt())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun commitCount(kind: String, value: Int) {
        try {
            val p = store.adjustActive(kind, value)
            Shortcuts.refresh(applicationContext, p.count)
            render()
        } catch (e: Exception) { toast(e.message ?: getString(R.string.invalid_number)) }
    }

    private fun switchLine(parent: LinearLayout, title: String, checked: Boolean,
                           onToggle: (Boolean) -> Unit) {
        val r = row().apply { setPadding(0, dp(6), 0, dp(6)) }
        r.addView(txt(title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, enabled -> onToggle(enabled) }
        }
        r.addView(sw)
        parent.addView(r)
    }

    private fun choose(title: String, labels: Array<String>, values: Array<String>,
                       key: String) {
        val selected = values.indexOf(UiPrefs.text(this, key)).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, index ->
                UiPrefs.setText(this, key, values[index])
                dialog.dismiss()
                recreate()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showSettings() {
        val p = store.activeProject()
        val feedback = card()
        feedback.addView(txt(getString(R.string.feedback), 17f, true))
        switchLine(feedback, getString(R.string.vibration), UiPrefs.bool(this, "vibration", true)) {
            UiPrefs.setBool(this, "vibration", it)
        }
        switchLine(feedback, getString(R.string.sound), UiPrefs.bool(this, "sound")) {
            UiPrefs.setBool(this, "sound", it)
        }
        switchLine(feedback, getString(R.string.notification), UiPrefs.bool(this, "notification", true)) {
            UiPrefs.setBool(this, "notification", it)
            if (it && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
        switchLine(feedback, getString(R.string.auto_reset), p.resetDaily) {
            store.setDailyReset(p.id, it)
        }
        feedback.addView(txt(getString(R.string.auto_reset_desc), 12f, false, muted))

        val appearance = card()
        appearance.addView(txt(getString(R.string.appearance), 17f, true))
        line(appearance, getString(R.string.theme), UiPrefs.text(this, "theme")) {
            choose(getString(R.string.theme),
                arrayOf(getString(R.string.system), getString(R.string.light), getString(R.string.dark)),
                arrayOf("system", "light", "dark"), "theme")
        }
        line(appearance, getString(R.string.language), UiPrefs.text(this, "language")) {
            choose(getString(R.string.language),
                arrayOf(getString(R.string.system), getString(R.string.chinese), getString(R.string.english)),
                arrayOf("system", "zh", "en"), "language")
        }

        val shortcut = card()
        shortcut.addView(txt(getString(R.string.shortcut), 17f, true))
        shortcut.addView(space(9))
        shortcut.addView(txt(getString(R.string.shortcut_explainer), 12f, false, muted))
        addAction(shortcut, getString(R.string.pin_shortcut)) {
            val ok = Shortcuts.pin(this, store.activeProject().count)
            toast(getString(if (ok) R.string.shortcut_requested else R.string.shortcut_unsupported))
        }
        addAction(shortcut, getString(R.string.refresh_shortcut)) {
            Shortcuts.refresh(applicationContext, store.activeProject().count)
            toast(getString(R.string.shortcut_refreshing))
        }

        val data = card()
        data.addView(txt(getString(R.string.daily_history), 17f, true))
        addAction(data, getString(R.string.export_backup)) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "tapcount-backup.json")
            }
            startActivityForResult(i, 201)
        }
        addAction(data, getString(R.string.import_backup)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_backup).setMessage(R.string.import_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    startActivityForResult(i, 202)
                }.show()
        }

        val update = card()
        update.addView(txt(getString(R.string.updates), 17f, true))
        line(update, getString(R.string.version_label, BuildConfig.VERSION_NAME), "")
        addAction(update, getString(R.string.check_update)) { checkForUpdates() }
        addAction(update, getString(R.string.open_releases)) { openReleases() }
    }

    @Deprecated("Activity result API is sufficient for this small standalone app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            when (requestCode) {
                201 -> {
                    val stream = contentResolver.openOutputStream(uri) ?: error("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(store.exportJson()) }
                    toast(getString(R.string.backup_success))
                }
                202 -> {
                    val stream = contentResolver.openInputStream(uri) ?: error("No input stream")
                    val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    store.importJson(json)
                    Shortcuts.refresh(applicationContext, store.activeProject().count)
                    page = 0
                    render()
                    toast(getString(R.string.import_success))
                }
            }
        } catch (ex: Exception) {
            toast(getString(R.string.backup_failed))
        }
    }

    private fun newer(remote: String, local: String): Boolean {
        val a = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val b = local.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun openReleases() {
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/XiaoyuZhuang/TapCount/releases/latest")))
    }

    private fun checkForUpdates() {
        toast(getString(R.string.checking))
        Thread {
            try {
                val conn = URL("https://api.github.com/repos/XiaoyuZhuang/TapCount/releases/latest")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "TapCount-Android")
                try {
                    if (conn.responseCode != 200) error("HTTP " + conn.responseCode)
                    val result = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = result.getString("tag_name")
                    runOnUiThread {
                        if (newer(tag, BuildConfig.VERSION_NAME)) {
                            toast(getString(R.string.new_version))
                            openReleases()
                        } else toast(getString(R.string.up_to_date))
                    }
                } finally { conn.disconnect() }
            } catch (_: Exception) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.update_failed)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.open_releases) { _, _ -> openReleases() }
                        .show()
                }
            }
        }.start()
    }
}
