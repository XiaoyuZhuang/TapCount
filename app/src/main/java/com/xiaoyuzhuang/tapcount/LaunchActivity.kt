package com.xiaoyuzhuang.tapcount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log

/** Isolated transparent entry for the ordinary app icon and numbered shortcut. */
class LaunchActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.localize(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processLaunch()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        processLaunch()
    }

    private fun processLaunch() {
        val prefs = getSharedPreferences("tapcount", Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val previous = prefs.getLong("last_launch_elapsed", 0L)
        // The SECOND launch within 5 s opens management, without an extra count.
        if (previous > 0L && now >= previous && now - previous < 5_000L) {
            prefs.edit().putLong("last_launch_elapsed", 0L).commit()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        try {
            // Store the click before any feedback or shortcut work.
            val project = CounterStore(applicationContext).incrementActive()
            prefs.edit().putLong("last_launch_elapsed", now).commit()
            Feedback.counted(applicationContext, project.count)
            Shortcuts.refresh(applicationContext, project.count)
        } catch (ex: Exception) {
            Log.e("TapCount", "Failed to save counter", ex)
        }
        // No activity opens on an ordinary first tap.
        finish()
    }
}
