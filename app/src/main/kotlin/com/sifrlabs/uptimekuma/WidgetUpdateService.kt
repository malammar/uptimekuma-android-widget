package com.sifrlabs.uptimekuma

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetUpdateService : Service() {

    companion object {
        private const val TAG = "UptimeWidget"
        const val ACTION_UPDATE_COMPLETE = "com.sifrlabs.uptimekuma.ACTION_UPDATE_COMPLETE"
        const val EXTRA_SUCCESS          = "success"
        const val EXTRA_WIDGET_IDS       = "widget_ids"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WidgetUpdateService started")
        Thread {
            val manager  = AppWidgetManager.getInstance(this)
            val allIds   = manager.getAppWidgetIds(ComponentName(this, UptimeWidget::class.java))
            val requested = intent?.getIntArrayExtra(EXTRA_WIDGET_IDS)
            val targetIds = if (requested != null && requested.isNotEmpty())
                requested.filter { it in allIds }.toIntArray()
            else
                allIds

            if (targetIds.isEmpty()) {
                stopSelf(startId)
                return@Thread
            }

            // Group widget IDs by profileId so we fetch each profile only once
            val profileToWidgets = targetIds
                .groupBy { Prefs.getProfileIdForWidget(this, it) ?: "" }
                .filterKeys { it.isNotEmpty() }

            var anySuccess = false
            for ((profileId, widgetIds) in profileToWidgets) {
                val profile = Prefs.getProfile(this, profileId) ?: continue
                var success = false
                for (attempt in 1..3) {
                    try {
                        Log.d(TAG, "Fetching profile '${profile.name}' (attempt $attempt)")
                        val groups = UptimeRepository(this).fetchGroups(profile)
                        val json   = groupsToJson(groups)
                        widgetIds.forEach { Prefs.saveCachedGroups(this, it, json) }
                        widgetIds.toIntArray().let { ids ->
                            ids.forEach { manager.updateAppWidget(it, buildRemoteViews(it, success = true)) }
                            manager.notifyAppWidgetViewDataChanged(ids, R.id.monitor_container)
                        }
                        success    = true
                        anySuccess = true
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                        if (attempt < 3) Thread.sleep(2000)
                    }
                }
                if (!success) widgetIds.forEach { manager.updateAppWidget(it, buildRemoteViews(it, success = false)) }
            }

            sendBroadcast(Intent(ACTION_UPDATE_COMPLETE).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUCCESS, anySuccess)
            })
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }

    private fun buildRemoteViews(appWidgetId: Int, success: Boolean): RemoteViews {
        val views = RemoteViews(packageName, R.layout.widget_layout)
        views.setInt(R.id.widget_root, "setBackgroundColor", Prefs.getWidgetBgColor(this, appWidgetId))

        // Unique URI per widget forces a separate RemoteViewsFactory instance
        val serviceIntent = Intent(this, MonitorListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("content://com.sifrlabs.uptimekuma/widget/$appWidgetId")
        }
        views.setRemoteAdapter(R.id.monitor_container, serviceIntent)

        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        views.setTextViewText(
            R.id.last_updated,
            if (success) "Updated $ts" else getString(R.string.error_loading)
        )

        // Reset refresh button back to the refresh symbol
        views.setTextViewText(R.id.btn_refresh, "\u21BA")

        // Widget title = profile name, header tap opens status page
        val profileId = Prefs.getProfileIdForWidget(this, appWidgetId)
        val profile   = profileId?.let { Prefs.getProfile(this, it) }
        val hostname  = profile?.hostname ?: ""
        views.setTextViewText(R.id.widget_header, profile?.name ?: getString(R.string.widget_title))
        views.setOnClickPendingIntent(
            R.id.widget_header,
            PendingIntent.getActivity(
                this, appWidgetId,
                Intent(Intent.ACTION_VIEW, Uri.parse(hostname.ifEmpty { "https://uptime.kuma.pet" })),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Refresh button — only refreshes this specific widget
        val refreshIntent = Intent(this, UptimeWidget::class.java).apply {
            action = UptimeWidget.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getBroadcast(
                this, appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        return views
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
