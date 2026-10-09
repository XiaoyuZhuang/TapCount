package com.xiaoyuzhuang.tapcount

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock

class LaunchActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiPrefs.localize(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("tapcount", Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val previous = prefs.getLong("last_launch_elapsed", 0L)
        if (previous > 0L && now >= previous && now - previous <= 5_000L) {
            prefs.edit().putLong("last_launch_elapsed", 0L).commit()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // Commit synchronously so a second launch can always observe this tap.
        prefs.edit().putLong("last_launch_elapsed", now).commit()
        try {
            val result = CounterStore(this).incrementActive()
            Feedback.counted(this, result.count)
            Shortcuts.refresh(applicationContext, result.count)
        } catch (ex: Exception) {
            // A failed database write is not reported as a successful count.
            android.util.Log.e("TapCount", "Counter write failed", ex)
        }
        finish()
    }
}
