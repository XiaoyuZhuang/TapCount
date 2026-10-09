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
    fun counted(ctx: Context, count: Int) {
        if (UiPrefs.bool(ctx, "vibration", true)) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= 31)
                    ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION")
                    (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                if (vibrator.hasVibrator())
                    vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }
        if (UiPrefs.bool(ctx, "sound")) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 200)
            } catch (_: Exception) {}
        }
        if (UiPrefs.bool(ctx, "notification")) {
            try {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
                val nm = ctx.getSystemService(NotificationManager::class.java)
                val channel = "counter_confirmation"
                nm.createNotificationChannel(NotificationChannel(channel,
                    ctx.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
                val notification = Notification.Builder(ctx, channel)
                    .setSmallIcon(android.R.drawable.ic_input_add)
                    .setContentTitle(ctx.getString(R.string.app_name))
                    .setContentText(ctx.getString(R.string.count_added, count))
                    .setAutoCancel(true).build()
                nm.notify(1001, notification)
            } catch (_: Exception) {}
        }
    }
}
