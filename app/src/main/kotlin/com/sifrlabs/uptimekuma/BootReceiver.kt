package com.sifrlabs.uptimekuma

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, UptimeWidget::class.java))
        if (ids.isNotEmpty()) {
            UptimeWidget.scheduleAlarm(context)
            UptimeWidget.triggerUpdate(context)
        }
    }
}
