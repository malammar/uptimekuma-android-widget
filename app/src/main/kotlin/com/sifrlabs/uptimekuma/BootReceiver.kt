package com.sifrlabs.uptimekuma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (UptimeWidget.allWidgetIds(context).isNotEmpty()) {
            UptimeWidget.scheduleAlarm(context)
            UptimeWidget.triggerUpdate(context)
        }
    }
}
