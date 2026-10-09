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
    private const val SUCCESS = "tapcount_success_v3"
    private const val BONUS = "tapcount_bonus_v1"
    private const val TOTAL = "tapcount_total_v1"
    private const val TOTAL_ID = 501

    private fun canNotify(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshPersistent(ctx: Context, count: Int) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!UiPrefs.bool(ctx, "persistent_notification", true)) {
            nm.cancel(TOTAL_ID)
            return
        }
        nm.createNotificationChannel(NotificationChannel(TOTAL,
            ctx.getString(R.string.total_channel), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        val n = Notification.Builder(ctx, TOTAL)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(ctx.getString(R.string.persistent_count, count))
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        nm.notify(TOTAL_ID, n)
    }

    fun counted(ctx: Context, result: TapOutcome) {
        val rewarded = result.rewarded
        if (UiPrefs.bool(ctx, "vibration", true)) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= 31)
                    ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else {
                    @Suppress("DEPRECATION")
                    (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                }
                if (vibrator.hasVibrator()) {
                    val effect = if (rewarded)
                        VibrationEffect.createWaveform(longArrayOf(0L, 90L, 100L, 100L), -1)
                    else VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                }
            } catch (_: Exception) {}
        }
        if (UiPrefs.bool(ctx, "sound", false)) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                tone.startTone(if (rewarded) ToneGenerator.TONE_PROP_ACK
                    else ToneGenerator.TONE_PROP_BEEP, if (rewarded) 210 else 90)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 350)
            } catch (_: Exception) {}
        }
        try { refreshPersistent(ctx, result.project.count) } catch (_: Exception) {}
        if (!canNotify(ctx)) return
        if (!rewarded && !UiPrefs.bool(ctx, "notification", true)) return
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channelId = if (rewarded) BONUS else SUCCESS
            nm.createNotificationChannel(NotificationChannel(channelId,
                ctx.getString(if (rewarded) R.string.bonus_channel else R.string.count_notification_channel),
                NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
            val text = if (rewarded)
                ctx.getString(R.string.bonus_notification, result.points, result.project.count)
                else ctx.getString(R.string.count_added, result.project.count)
            val notification = Notification.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_input_add)
                .setContentTitle(ctx.getString(if (rewarded) R.string.bonus_title else R.string.count_success))
                .setContentText(text)
                .setAutoCancel(true).setOngoing(false)
                .setOnlyAlertOnce(false).setTimeoutAfter(if (rewarded) 6500L else 4500L)
                .setPriority(Notification.PRIORITY_HIGH).build()
            val prefs = ctx.getSharedPreferences("tapcount", Context.MODE_PRIVATE)
            val id = prefs.getInt("notification_serial", 3000) + 1
            prefs.edit().putInt("notification_serial", id).apply()
            nm.notify(id, notification)
        } catch (_: Exception) {}
    }
}
