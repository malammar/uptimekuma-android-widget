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
                        val showGroupMonitors = widgetIds.any { Prefs.getWidgetShowGroupMonitors(this, it) }
                        val groups = UptimeRepository(this).fetchGroups(profile, showGroupMonitors)
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
        val bgColor = Prefs.getWidgetBgColor(this, appWidgetId)
        views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

        // ── Appearance settings ────────────────────────────────────────────────
        val darkMode   = when (Prefs.getWidgetTheme(this, appWidgetId)) {
            0    -> true
            1    -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val scalePct   = Prefs.getWidgetTextScalePct(this, appWidgetId)
        val scale      = scalePct / 100f

        // Font colors
        val autoPrimary   = if (darkMode) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt()
        val autoSecondary = if (darkMode) 0xFFB0BEC5.toInt() else 0xFF607080.toInt()
        val fontPref      = Prefs.getWidgetFontColor(this, appWidgetId)
        val primaryColor  = if (fontPref == 0) autoPrimary else fontPref
        val secondaryColor = if (fontPref == 0) autoSecondary
                             else (fontPref and 0x00FFFFFF) or 0xCC000000.toInt()

        // Header background
        val r = (bgColor shr 16) and 0xFF
        val g = (bgColor shr 8) and 0xFF
        val b = bgColor and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        val shift = 25
        val hr = if (lum < 128) (r + shift).coerceAtMost(255) else (r - shift).coerceAtLeast(0)
        val hg = if (lum < 128) (g + shift).coerceAtMost(255) else (g - shift).coerceAtLeast(0)
        val hb = if (lum < 128) (b + shift).coerceAtMost(255) else (b - shift).coerceAtLeast(0)
        val autoHeaderColor = ((bgColor ushr 24) shl 24) or (hr shl 16) or (hg shl 8) or hb
        val headerBgPref = Prefs.getWidgetHeaderBg(this, appWidgetId)
        val headerColor  = if (headerBgPref == 0) autoHeaderColor else headerBgPref
        views.setInt(R.id.widget_header_row, "setBackgroundColor", headerColor)
        views.setTextColor(R.id.widget_header, primaryColor)
        views.setTextViewTextSize(R.id.widget_header, android.util.TypedValue.COMPLEX_UNIT_SP, 14f * scale)
        views.setTextColor(R.id.btn_refresh, secondaryColor)

        // Footer background
        val footerBgPref = Prefs.getWidgetFooterBg(this, appWidgetId)
        val footerColor  = if (footerBgPref == 0) bgColor else footerBgPref
        views.setInt(R.id.widget_footer_row, "setBackgroundColor", footerColor)
        views.setTextColor(R.id.last_updated, secondaryColor)
        views.setTextViewTextSize(R.id.last_updated, android.util.TypedValue.COMPLEX_UNIT_SP, 10f * scale)

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
