package com.xiaoyuzhuang.tapcount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

class LaunchActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.localize(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); processLaunch() }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent); setIntent(intent); processLaunch()
    }
    private fun openDashboard() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }
    private fun processLaunch() {
        val prefs = getSharedPreferences("tapcount", Context.MODE_PRIVATE)
        val mode = prefs.getString("entry_mode", "double")
        val now = SystemClock.elapsedRealtime()
        val previous = prefs.getLong("last_launch_elapsed", 0L)
        val interval = prefs.getInt("entry_interval_seconds", 5).coerceIn(1, 60) * 1_000L
        if (mode != "periodic" && previous > 0L && now >= previous && now - previous < interval) {
            prefs.edit().putLong("last_launch_elapsed", 0L).commit()
            openDashboard()
            return
        }
        try {
            val outcome = CounterStore(applicationContext).tapActive()
            prefs.edit().putLong("last_launch_elapsed", if (mode == "periodic") 0L else now).commit()
            Feedback.counted(applicationContext, outcome)
            Shortcuts.refresh(applicationContext, outcome.project.count)
            if (outcome.openDashboard) { openDashboard(); return }
        } catch (ex: Exception) {
            Log.e("TapCount", "Counter failed", ex)
        }
        finish()
    }
}
