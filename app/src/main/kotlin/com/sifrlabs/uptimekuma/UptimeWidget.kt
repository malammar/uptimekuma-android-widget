package com.sifrlabs.uptimekuma

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews

class UptimeWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "UptimeWidget"
        const val ACTION_REFRESH      = "com.sifrlabs.uptimekuma.ACTION_REFRESH"
        const val ACTION_ALARM_TICK   = "com.sifrlabs.uptimekuma.ACTION_ALARM_TICK"

        fun triggerUpdate(context: Context, widgetIds: IntArray? = null) {
            Log.d(TAG, "triggerUpdate called")
            try {
                val intent = Intent(context, WidgetUpdateService::class.java)
                if (widgetIds != null) intent.putExtra(WidgetUpdateService.EXTRA_WIDGET_IDS, widgetIds)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startService failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        fun scheduleAlarm(context: Context) {
            val profiles  = Prefs.getProfiles(context)
            val intervalMs = if (profiles.isEmpty()) 5 * 60 * 1000L
                             else profiles.minOf { it.intervalMinutes } * 60 * 1000L
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                buildAlarmPendingIntent(context)
            )
            Log.d(TAG, "Alarm scheduled, interval=${intervalMs / 60000}min")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(buildAlarmPendingIntent(context))
        }

        private fun buildAlarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, UptimeWidget::class.java).apply {
                action = ACTION_ALARM_TICK
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate for ${appWidgetIds.size} widgets")
        triggerUpdate(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)
        if (intent.action == ACTION_ALARM_TICK) {
            triggerUpdate(context)
        } else if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val ids = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) intArrayOf(widgetId) else null

            // Immediately update the button + footer so the tap feels responsive
            val manager = AppWidgetManager.getInstance(context)
            val targetIds = ids ?: manager.getAppWidgetIds(
                ComponentName(context, UptimeWidget::class.java)
            )
            val feedback = RemoteViews(context.packageName, R.layout.widget_layout)
            feedback.setTextViewText(R.id.btn_refresh, "…")
            feedback.setTextViewText(R.id.last_updated, "Refreshing…")
            targetIds.forEach { manager.partiallyUpdateAppWidget(it, feedback) }

            triggerUpdate(context, ids)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { Prefs.removeWidget(context, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelAlarm(context)
    }
}
