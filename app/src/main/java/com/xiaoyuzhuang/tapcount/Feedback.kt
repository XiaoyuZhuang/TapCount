package com.xiaoyuzhuang.tapcount

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Feedback {
    private const val CHANNEL_ID = "tapcount_count_success_v2"

    fun counted(ctx: Context, count: Int) {
        if (UiPrefs.bool(ctx, "vibration", true)) {
            try {
                val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                    ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                }
                if (vibrator.hasVibrator())
                    vibrator.vibrate(VibrationEffect.createOneShot(65L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) { }
        }
        if (UiPrefs.bool(ctx, "sound", false)) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 240)
            } catch (_: Exception) { }
        }

        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            // Clean up the old, reused notification from v0.1.0.
            nm.cancel(1001)
            if (!UiPrefs.bool(ctx, "notification", true)) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) return

            // High priority is required to qualify for heads-up on Android 8+.
            // App-controlled vibration and sound remain separate settings.
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, ctx.getString(R.string.count_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ctx.getString(R.string.count_notification_channel_desc)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            })

            val prefs = ctx.getSharedPreferences("tapcount", Context.MODE_PRIVATE)
            val notificationId = prefs.getInt("notification_serial", 2000) + 1
            prefs.edit().putInt("notification_serial", notificationId).apply()
            val notice = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_input_add)
                .setContentTitle(ctx.getString(R.string.count_success))
                .setContentText(ctx.getString(R.string.count_added, count))
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOngoing(false)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(5_000L)
                .build()
            nm.notify(notificationId, notice)
        } catch (_: Exception) {
            // A notification failure must not roll back the saved count.
        }
    }
}
