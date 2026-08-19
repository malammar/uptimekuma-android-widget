package com.sifrlabs.uptimekuma

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

// 1x1 widget showing a single dot with the overall status of all monitors.
class CompactUptimeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        UptimeWidget.triggerUpdate(context, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { Prefs.removeWidget(context, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        UptimeWidget.scheduleAlarm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val manager = AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetIds(ComponentName(context, UptimeWidget::class.java)).isEmpty()) {
            UptimeWidget.cancelAlarm(context)
        }
    }
}
