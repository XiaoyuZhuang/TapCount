package com.xiaoyuzhuang.tapcount

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

/**
 * Broadcast-only home button: neither tapping nor updating opens an Activity,
 * so Android's app-launch splash screen is not involved.
 */
class TapWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_TAP = "com.xiaoyuzhuang.tapcount.WIDGET_TAP"

        fun refresh(ctx: Context, count: Int) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, TapWidgetProvider::class.java))
                if (ids.isEmpty()) return
                val pending = PendingIntent.getBroadcast(
                    ctx, 319, Intent(ctx, TapWidgetProvider::class.java).apply {
                        action = ACTION_TAP
                    }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                for (id in ids) {
                    val views = RemoteViews(ctx.packageName, R.layout.tap_widget)
                    views.setTextViewText(R.id.tap_widget_count, count.toString())
                    views.setOnClickPendingIntent(R.id.tap_widget_root, pending)
                    mgr.updateAppWidget(id, views)
                }
            } catch (ex: Exception) {
                Log.e("TapCount", "Widget refresh failed", ex)
            }
        }
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        try { refresh(context, CounterStore(context).activeProject().count) }
        catch (ex: Exception) { Log.e("TapCount", "Widget initialization failed", ex) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) {
            super.onReceive(context, intent)
            return
        }
        val pendingResult = goAsync()
        val ctx = context.applicationContext
        Thread {
            try {
                // Widgets always count; they never consume a double-tap,
                // trigger the periodic dashboard, or display an Activity.
                val outcome = CounterStore(ctx).tapActive(allowAutomaticEntry = false)
                Feedback.counted(ctx, outcome)
                Shortcuts.refresh(ctx, outcome.project.count)
            } catch (ex: Exception) {
                Log.e("TapCount", "Widget count failed", ex)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
