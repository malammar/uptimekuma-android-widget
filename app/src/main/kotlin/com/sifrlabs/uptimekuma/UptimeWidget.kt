package com.sifrlabs.uptimekuma

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

class UptimeWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "UptimeWidget"
        const val ACTION_REFRESH = "com.sifrlabs.uptimekuma.ACTION_REFRESH"

        fun triggerUpdate(context: Context) {
            Log.d(TAG, "triggerUpdate called, starting service")
            try {
                val intent = Intent(context, WidgetUpdateService::class.java)
                context.startService(intent)
                Log.d(TAG, "startService called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "startService failed: ${e.javaClass.simpleName}: ${e.message}", e)
            }
        }

        fun scheduleAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = buildAlarmPendingIntent(context)
            val intervalMs = Prefs.intervalMinutes(context) * 60 * 1000L
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalMs,
                intervalMs,
                pi
            )
            Log.d(TAG, "Alarm scheduled every ${Prefs.intervalMinutes(context)} min")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(buildAlarmPendingIntent(context))
        }

        private fun buildAlarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, UptimeWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        triggerUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            triggerUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled")
        super.onEnabled(context)
        scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelAlarm(context)
    }
}
