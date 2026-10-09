package com.xiaoyuzhuang.tapcount

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon

object Shortcuts {
    private const val ID = "tapcount_numeric"

    private fun icon(count: Int): Icon {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(33, 49, 77)
        canvas.drawRoundRect(RectF(2f, 2f, 190f, 190f), 42f, 42f, p)
        val number = if (count > 99999) "99k+" else count.toString()
        p.color = Color.WHITE
        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        p.textSize = when (number.length) {
            1, 2 -> 103f
            3 -> 85f
            4 -> 67f
            else -> 55f
        }
        p.textAlign = Paint.Align.CENTER
        val bounds = p.fontMetrics
        canvas.drawText(number, 96f, 96f - (bounds.ascent + bounds.descent) / 2, p)
        return Icon.createWithBitmap(bitmap)
    }

    private fun info(ctx: Context, count: Int): ShortcutInfo {
        val intent = Intent(ctx, LaunchActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ShortcutInfo.Builder(ctx, ID)
            .setShortLabel(ctx.getString(R.string.app_name))
            .setLongLabel(ctx.getString(R.string.current_count) + ": " + count)
            .setIntent(intent)
            .setIcon(icon(count))
            .build()
    }

    fun refresh(ctx: Context, count: Int) {
        Thread {
            try {
                val mgr = ctx.getSystemService(ShortcutManager::class.java)
                if (mgr.isRateLimitingActive) return@Thread
                if (mgr.dynamicShortcuts.any { it.id == ID } ||
                    mgr.pinnedShortcuts.any { it.id == ID })
                    mgr.updateShortcuts(listOf(info(ctx, count)))
                else mgr.addDynamicShortcuts(listOf(info(ctx, count)))
            } catch (_: Exception) {
                // Icon refresh must never block the counter.
            }
        }.start()
    }

    fun pin(ctx: Context, count: Int): Boolean {
        val mgr = ctx.getSystemService(ShortcutManager::class.java)
        if (!mgr.isRequestPinShortcutSupported) return false
        return try {
            mgr.requestPinShortcut(info(ctx, count), null)
            true
        } catch (_: Exception) { false }
    }
}
