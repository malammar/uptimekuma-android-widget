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
        const val EXTRA_SUCCESS = "success"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WidgetUpdateService started")
        Thread {
            var lastException: Exception? = null
            for (attempt in 1..3) {
                try {
                    Log.d(TAG, "Fetching monitors (attempt $attempt)...")
                    val groups = UptimeRepository(this).fetchGroups()
                    Log.d(TAG, "Fetched ${groups.sumOf { it.monitors.size }} monitors in ${groups.size} groups")
                    Prefs.saveCachedGroups(this, groupsToJson(groups))
                    updateWidget(success = true)
                    lastException = null
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Attempt $attempt failed: ${e.javaClass.simpleName}: ${e.message}")
                    lastException = e
                    if (attempt < 3) Thread.sleep(2000)
                }
            }
            val success = lastException == null
            if (!success) updateWidget(success = false)
            sendBroadcast(Intent(ACTION_UPDATE_COMPLETE).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUCCESS, success)
            })
            stopSelf(startId)
        }.start()
        return START_NOT_STICKY
    }

    private fun updateWidget(success: Boolean) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, UptimeWidget::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, buildRemoteViews(success))
        if (success) manager.notifyAppWidgetViewDataChanged(ids, R.id.monitor_container)
    }

    private fun buildRemoteViews(success: Boolean): RemoteViews {
        val views = RemoteViews(packageName, R.layout.widget_layout)

        // Attach the ListView adapter
        views.setRemoteAdapter(R.id.monitor_container, Intent(this, MonitorListService::class.java))

        if (success) {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.last_updated, "Updated $ts")
        } else {
            views.setTextViewText(R.id.last_updated, getString(R.string.error_loading))
        }

        // Header tap -> open browser
        views.setOnClickPendingIntent(
            R.id.widget_header,
            PendingIntent.getActivity(
                this, 0,
                Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.hostname(this))),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Refresh button tap
        views.setOnClickPendingIntent(
            R.id.btn_refresh,
            PendingIntent.getBroadcast(
                this, 1,
                Intent(this, UptimeWidget::class.java).apply { action = UptimeWidget.ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        return views
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
