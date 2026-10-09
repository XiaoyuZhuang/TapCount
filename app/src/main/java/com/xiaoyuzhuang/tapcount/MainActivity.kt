package com.xiaoyuzhuang.tapcount

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.app.NotificationManager
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
import android.widget.HorizontalScrollView
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
    private var historyEnd: LocalDate = LocalDate.now()
    private var isDark = false
    private var pendingNotificationEntry = false

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
        if (intent?.getBooleanExtra("open_settings", false) == true) page = 3
        @Suppress("DEPRECATION")
        window.statusBarColor = bg
        @Suppress("DEPRECATION")
        window.navigationBarColor = bg
        render()
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
            Feedback.refreshPersistent(applicationContext,store.activeProject().count)
        }
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("open_settings", false) == true) {
            page = 3
            render()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.any { it == PackageManager.PERMISSION_GRANTED })
            Feedback.refreshPersistent(applicationContext,store.activeProject().count)
        if (requestCode == 101) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (pendingNotificationEntry && granted) {
                pendingNotificationEntry = false
                selectNotificationEntry()
            } else {
                pendingNotificationEntry = false
                toast(getString(R.string.notification_mode_permission_required))
            }
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

    private fun showHome() {
        val project = store.activeProject()
        val date = LocalDate.now().toString()
        val hourly = store.hourly(project.id, date)

        // The daily hourly-points chart stays at the top, with compact feedback.
        val scores = store.recent(project.id, 2)
        val yesterday = scores.first().count
        val today = scores.last().count
        val difference = today - yesterday
        val chart = card()
        chart.addView(txt(getString(R.string.today_hour_points), 16f, true))
        val compare = row().apply { setPadding(0,dp(9),0,dp(9)) }
        fun summaryCell(label: String, value: String, highlighted: Boolean): LinearLayout {
            return column().apply {
                val backgroundColor = if (isDark) Color.rgb(43, 53, 70)
                    else Color.rgb(241, 245, 252)
                background=round(backgroundColor,10)
                setPadding(dp(9),dp(8),dp(9),dp(8))
                addView(txt(label,11f,false,muted))
                addView(txt(value,18f,true,if (highlighted) primary else fg))
            }
        }
        val summary = listOf(
            getString(R.string.today) to today.toString(),
            getString(R.string.yesterday) to yesterday.toString(),
            getString(R.string.day_difference) to
                ((if (difference>=0) "+" else "") + difference)
        )
        summary.forEachIndexed { i, pair ->
            compare.addView(summaryCell(pair.first,pair.second,i==2),
                LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
                    .apply { if (i<2) rightMargin=dp(6) })
        }
        chart.addView(compare)
        if (hourly.isEmpty()) {
            chart.addView(txt(getString(R.string.today_no_events),13f,false,muted),
                LinearLayout.LayoutParams(-1,-2).apply {
                    topMargin=dp(8); bottomMargin=dp(8)
                })
        } else {
            // A long active day can include up to 24 hours. Maintain readable
            // numbers rather than squeezing all labels into tiny columns.
            val scroller=HorizontalScrollView(this).apply {
                isFillViewport=true
                isHorizontalScrollBarEnabled=hourly.size>9
            }
            scroller.addView(HourlyChart(this,hourly,isDark,true),
                android.widget.FrameLayout.LayoutParams(
                    dp((hourly.size*38).coerceAtLeast(275)),dp(146)))
            chart.addView(scroller)
        }

        val logCard = card()
        val logTitle = row()
        logTitle.addView(txt(getString(R.string.today_activity), 16f, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val editButton = action(getString(R.string.edit_count), { editMenu() })
        logTitle.addView(editButton)
        val deleteButton = action(getString(R.string.delete_today), {
            StyledDialogs.confirm(this, getString(R.string.delete_today),
                getString(R.string.delete_today_warning)) { clearTodayData() }
        })
        logTitle.addView(deleteButton,
            LinearLayout.LayoutParams(-2, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(7) })
        logCard.addView(logTitle)

        val events = store.events(project.id, date)
        if (events.isEmpty()) {
            logCard.addView(txt(getString(R.string.today_no_events), 13f, false, muted),
                LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(11); bottomMargin=dp(7) })
        } else {
            val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            events.forEach { event ->
                val isBonus = event.kind == "bonus"
                val label = when (event.kind) {
                    "tap" -> getString(R.string.hour_log_tap)
                    "bonus" -> getString(R.string.hour_log_bonus)
                    "add" -> getString(R.string.hour_log_add)
                    "set" -> getString(R.string.hour_log_set)
                    else -> getString(R.string.hour_log_reset)
                }
                val record = row().apply {
                    setPadding(dp(6),dp(8),dp(6),dp(8))
                    background = round(if (isBonus)
                        (if (isDark) Color.rgb(52, 62, 73) else Color.rgb(239, 245, 255))
                        else surface, 8)
                }
                val whenText = txt(fmt.format(java.util.Date(event.at)), 12f, false, muted)
                record.addView(whenText, LinearLayout.LayoutParams(dp(75),
                    ViewGroup.LayoutParams.WRAP_CONTENT))
                val deltaText = (if (event.delta >= 0) "+" else "") + event.delta
                val description = "$label  $deltaText → ${event.after}"
                record.addView(txt(description, 13f, isBonus, if (isBonus) primary else fg),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                logCard.addView(record)
                val separator = View(this).apply {
                    setBackgroundColor(if (isDark) Color.rgb(51,60,76)
                        else Color.rgb(234,238,246))
                }
                logCard.addView(separator, LinearLayout.LayoutParams(-1,dp(1)))
            }
        }
    }

    private fun showHistory() {
        val project = store.activeProject()
        val records = store.dayWindow(project.id, historyEnd, days)
        val first = records.first().day
        val last = records.last().day

        val selectorCard = card()
        val periodSelector = row().apply { setPadding(0,dp(3),0,dp(7)) }
        val seven = action(getString(R.string.recent_7), { days=7; render() }, days==7)
        val thirty = action(getString(R.string.recent_30), { days=30; render() }, days==30)
        periodSelector.addView(seven, LinearLayout.LayoutParams(0,-2,1f)
            .apply { rightMargin=dp(7) })
        periodSelector.addView(thirty, LinearLayout.LayoutParams(0,-2,1f))
        selectorCard.addView(periodSelector)
        val range = txt(getString(R.string.history_date_range,first,last), 13f, false, muted)
        range.gravity=Gravity.CENTER
        selectorCard.addView(range)

        val navigation=row().apply { setPadding(0,dp(6),0,dp(5)) }
        val prev=action("‹ "+getString(R.string.history_before), {
            historyEnd=historyEnd.minusDays(days.toLong()); render()
        })
        val jump=action(getString(R.string.history_jump), {
            StyledDialogs.input(this, getString(R.string.history_date_format),
                historyEnd.toString()) { typed ->
                val selected=try { LocalDate.parse(typed,DateTimeFormatter.ISO_LOCAL_DATE) }
                    catch (_: Exception) { null }
                if (selected == null || selected.isAfter(LocalDate.now())) {
                    toast(getString(R.string.history_bad_date)); false
                } else { historyEnd=selected; render(); true }
            }
        })
        val next=action(getString(R.string.history_after)+" ›", {
            historyEnd=historyEnd.plusDays(days.toLong()).coerceAtMost(LocalDate.now())
            render()
        })
        next.isEnabled=historyEnd.isBefore(LocalDate.now())
        next.alpha=if (next.isEnabled) 1f else 0.4f
        navigation.addView(prev,LinearLayout.LayoutParams(0,-2,1f)
            .apply { rightMargin=dp(6) })
        navigation.addView(jump,LinearLayout.LayoutParams(0,-2,1.1f)
            .apply { rightMargin=dp(6) })
        navigation.addView(next,LinearLayout.LayoutParams(0,-2,1f))
        selectorCard.addView(navigation)
        selectorCard.addView(BarChart(this,records,isDark))

        val listCard=card()
        listCard.addView(txt(getString(R.string.daily_history),16f,true))
        for (pair in records.reversed().chunked(2)) {
            val r=row().apply { setPadding(0,dp(2),0,dp(2)) }
            for ((index,record) in pair.withIndex()) {
                val item=row().apply {
                    setPadding(dp(8),dp(7),dp(8),dp(7))
                    background=round(if (isDark) Color.rgb(42,50,67)
                        else Color.rgb(241,245,251),9)
                }
                item.addView(txt(record.day,11f,false,muted),
                    LinearLayout.LayoutParams(0,-2,1f))
                item.addView(txt(record.count.toString(),14f,true))
                item.setOnClickListener { showDayDetails(project.id,record.day) }
                r.addView(item,LinearLayout.LayoutParams(0,-2,1f)
                    .apply { if(index==0) rightMargin=dp(8) })
            }
            if (pair.size==1) r.addView(View(this),
                LinearLayout.LayoutParams(0,dp(1),1f))
            listCard.addView(r)
        }
    }

    private fun showDayDetails(projectId: Long, date: String) {
        val events = store.events(projectId, date)
        val activity = if (events.isEmpty()) getString(R.string.no_records)
        else events.joinToString("\n") { event ->
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(event.at))
            val kind = when (event.kind) {
                "tap" -> getString(R.string.log_tap)
                "bonus" -> getString(R.string.bonus_title)
                "add" -> getString(R.string.log_add)
                "set" -> getString(R.string.log_set)
                else -> getString(R.string.log_reset)
            }
            time + "  " + kind + "  " +
                (if (event.delta >= 0) "+" else "") + event.delta + "  → " + event.after
        }
        StyledDialogs.dayDetails(this, getString(R.string.activity_history, date),
            store.hourly(projectId, date), activity)
    }

    private fun askName(title: String, initial: String = "", onAccept: (String) -> Unit) {
        StyledDialogs.input(this,title,initial) { name ->
            if (name.isEmpty()) { toast(getString(R.string.empty_name)); false }
            else { onAccept(name); true }
        }
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
                    Feedback.refreshPersistent(applicationContext, store.activeProject().count)
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
                    Feedback.refreshPersistent(applicationContext, 0)
                    page = 0; render()
                }
            }
        }
    }

    private fun clearTodayData() {
        try {
            val p = store.clearTodayActive()
            Shortcuts.refresh(applicationContext, p.count)
            Feedback.refreshPersistent(applicationContext, p.count)
            render()
            toast(getString(R.string.today_deleted))
        } catch (e: Exception) {
            toast(e.message ?: getString(R.string.backup_failed))
        }
    }

    private fun clearEverything() {
        try {
            val p = store.resetAllData()
            getSystemService(NotificationManager::class.java).cancelAll()
            Shortcuts.refresh(applicationContext, p.count)
            Feedback.refreshPersistent(applicationContext, p.count)
            days = 7
            historyEnd = LocalDate.now()
            page = 0
            intent?.removeExtra("open_settings")
            // Delay theme/language recreation until confirmation dialog has closed.
            window.decorView.post { recreate() }
            toast(getString(R.string.all_data_deleted))
        } catch (e: Exception) {
            toast(e.message ?: getString(R.string.backup_failed))
        }
    }

    private fun editMenu() {
        val labels = arrayOf(getString(R.string.add_amount),
            getString(R.string.subtract_amount),getString(R.string.set_count),
            getString(R.string.reset_count))
        StyledDialogs.options(this,getString(R.string.edit_count),labels) { option ->
            when (option) {
                0 -> askNumber(labels[0], "add")
                1 -> askNumber(labels[1], "subtract")
                2 -> askNumber(labels[2], "set")
                3 -> StyledDialogs.confirm(this,getString(R.string.reset_count),
                    getString(R.string.confirm_reset)) { commitCount("reset",0) }
            }
        }
    }

    private fun askNumber(title: String, kind: String) {
        StyledDialogs.input(this,title,numeric=true) { typed ->
            val value = typed.toLongOrNull()
            if (value == null || value < 0L || value > 1_000_000_000L) {
                toast(getString(R.string.invalid_number)); false
            } else {
                commitCount(if (kind=="subtract") "add" else kind,
                    if (kind=="subtract") -value.toInt() else value.toInt())
                true
            }
        }
    }

    private fun askSettingNumber(titleId: Int, key: String,
                                 min: Int, max: Int, fallback: Int) {
        val current = getSharedPreferences("tapcount", Context.MODE_PRIVATE)
            .getInt(key,fallback)
        StyledDialogs.input(this,getString(titleId),current.toString(),numeric=true) { typed ->
            val value = typed.toIntOrNull()
            if (value == null || value !in min..max) {
                toast(getString(R.string.range_error,min,max))
                false
            } else {
                UiPrefs.setInt(this,key,value)
                render()
                true
            }
        }
    }

    private fun commitCount(kind: String, value: Int) {
        try {
            val p = store.adjustActive(kind, value)
            Shortcuts.refresh(applicationContext, p.count)
            Feedback.refreshPersistent(applicationContext, p.count)
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

    private fun notificationAvailable(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    private fun selectNotificationEntry() {
        if (!notificationAvailable()) {
            StyledDialogs.message(this,getString(R.string.notification_mode),
                getString(R.string.notification_mode_disabled))
            return
        }
        // Notification-only mode must have a persistent, tappable way back
        // to the management screen even with transient feedback disabled.
        UiPrefs.setBool(this,"persistent_notification",true)
        UiPrefs.setText(this,"entry_mode","notification")
        getSharedPreferences("tapcount",Context.MODE_PRIVATE).edit()
            .putLong("last_launch_elapsed",0L).apply()
        Feedback.refreshPersistent(applicationContext,store.activeProject().count)
        render()
    }

    private fun choose(title: String, labels: Array<String>,
                       values: Array<String>, key: String) {
        StyledDialogs.options(this,title,labels) { index ->
            val selected = values[index]
            if (key=="entry_mode" && selected=="notification") {
                if (Build.VERSION.SDK_INT>=33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
                    pendingNotificationEntry=true
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),101)
                } else {
                    selectNotificationEntry()
                }
            } else {
                UiPrefs.setText(this,key,selected)
                if (key=="entry_mode") {
                    getSharedPreferences("tapcount",Context.MODE_PRIVATE).edit()
                        .putLong("last_launch_elapsed",0L).apply()
                }
                if (key=="theme" || key=="language") recreate() else render()
            }
        }
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
        switchLine(feedback,getString(R.string.persistent_notification),
            UiPrefs.bool(this,"persistent_notification",true)) {
            if (!it && UiPrefs.text(this,"entry_mode","open")=="notification") {
                toast(getString(R.string.notification_mode_requires_persistent))
                render()
            } else {
                UiPrefs.setBool(this,"persistent_notification",it)
                Feedback.refreshPersistent(applicationContext,store.activeProject().count)
                if (it && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
            }
        }

        val mode = card()
        mode.addView(txt(getString(R.string.entry_settings),17f,true))
        val entryMode = UiPrefs.text(this,"entry_mode","open")
        val entryLabel = when (entryMode) {
            "double" -> R.string.double_mode
            "periodic" -> R.string.periodic_mode
            "notification" -> R.string.notification_mode
            else -> R.string.open_mode
        }
        line(mode,getString(R.string.entry_mode), getString(entryLabel)) {
            choose(getString(R.string.entry_mode),
                arrayOf(getString(R.string.open_mode),
                    getString(R.string.double_mode),
                    getString(R.string.periodic_mode),
                    getString(R.string.notification_mode)),
                arrayOf("open","double","periodic","notification"),"entry_mode")
        }
        if (entryMode=="double") {
            line(mode,getString(R.string.entry_interval),
                getString(R.string.seconds_unit,UiPrefs.int(this,"entry_interval_seconds",5))) {
                askSettingNumber(R.string.entry_interval,"entry_interval_seconds",1,60,5)
            }
        } else if (entryMode=="periodic") {
            line(mode,getString(R.string.periodic_interval),
                getString(R.string.taps_unit,UiPrefs.int(this,"periodic_interval",10))) {
                askSettingNumber(R.string.periodic_interval,"periodic_interval",2,1000,10)
            }
        }
        mode.addView(txt(getString(R.string.periodic_explainer),12f,false,muted))

        val bonus = card()
        bonus.addView(txt(getString(R.string.bonus_settings),17f,true))
        switchLine(bonus,getString(R.string.bonus_enabled),
            UiPrefs.bool(this,"bonus_enabled",false)) {
            UiPrefs.setBool(this,"bonus_enabled",it)
            render()
        }
        if (UiPrefs.bool(this,"bonus_enabled",false)) {
            line(bonus,getString(R.string.bonus_pity),
                getString(R.string.taps_unit,UiPrefs.int(this,"bonus_pity",10))) {
                askSettingNumber(R.string.bonus_pity,"bonus_pity",1,1000,10)
            }
            line(bonus,getString(R.string.bonus_amount),
                "+"+UiPrefs.int(this,"bonus_amount",10)) {
                askSettingNumber(R.string.bonus_amount,"bonus_amount",2,100000,10)
            }
            bonus.addView(txt(getString(R.string.bonus_description),12f,false,muted))
        }

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

        // The widget is the recommended default numeric desktop button.
        // Android launchers must still ask the user to place it.
        val shortcut = card()
        shortcut.addView(txt(getString(R.string.shortcut), 17f, true))
        shortcut.addView(space(9))
        shortcut.addView(txt(getString(R.string.widget_explainer), 12f, false, muted))
        addAction(shortcut, getString(R.string.pin_widget), true) {
            val manager = getSystemService(AppWidgetManager::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                val component = ComponentName(this, TapWidgetProvider::class.java)
                try {
                    val accepted = manager.requestPinAppWidget(component, null, null)
                    toast(getString(if (accepted) R.string.widget_requested
                        else R.string.widget_manual))
                } catch (_: Exception) {
                    toast(getString(R.string.widget_manual))
                }
            } else {
                toast(getString(R.string.widget_manual))
            }
        }
        shortcut.addView(space(14))
        shortcut.addView(txt(getString(R.string.legacy_shortcut_label), 13f, true, muted))
        shortcut.addView(space(5))
        shortcut.addView(txt(getString(R.string.shortcut_explainer), 12f, false, muted))
        addAction(shortcut, getString(R.string.pin_shortcut)) {
            val ok = Shortcuts.pin(this, store.activeProject().count)
            toast(getString(if (ok) R.string.shortcut_requested else R.string.shortcut_unsupported))
        }
        addAction(shortcut, getString(R.string.refresh_shortcut)) {
            Shortcuts.refresh(applicationContext, store.activeProject().count)
            TapWidgetProvider.refresh(applicationContext, store.activeProject().count)
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
        addAction(data,getString(R.string.import_backup)) {
            StyledDialogs.confirm(this,getString(R.string.import_backup),
                getString(R.string.import_warning)) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(i,202)
            }
        }
        addAction(data, getString(R.string.clear_all_data)) {
            StyledDialogs.confirm(this, getString(R.string.clear_all_data),
                getString(R.string.clear_all_warning)) { clearEverything() }
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
                    Feedback.refreshPersistent(applicationContext, store.activeProject().count)
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
                    StyledDialogs.confirm(this,getString(R.string.check_update),
                        getString(R.string.update_failed)) { openReleases() }
                }
            }
        }.start()
    }
}
