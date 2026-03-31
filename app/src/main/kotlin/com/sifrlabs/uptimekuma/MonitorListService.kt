package com.sifrlabs.uptimekuma

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import org.json.JSONObject

class MonitorListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return MonitorListFactory(applicationContext, appWidgetId)
    }
}

class MonitorListFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private data class Item(
        val type: Int,
        val label: String,
        val status: Int = -1,
        val uptimePct: Float = 100f,
        val history: List<Int> = emptyList()
    )

    private companion object {
        const val TYPE_HEADER  = 0
        const val TYPE_MONITOR = 1
        const val TAG = "UptimeWidget"
    }

    private val items = mutableListOf<Item>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items.clear()
        val json = Prefs.cachedGroupsJson(context, appWidgetId) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val group = arr.getJSONObject(i)
                val grpPct     = group.optDouble("pct", 100.0).toFloat()
                val grpHistArr = group.optJSONArray("history")
                val grpHistory = if (grpHistArr != null) (0 until grpHistArr.length()).map { grpHistArr.getInt(it) } else emptyList()
                items.add(Item(TYPE_HEADER, group.getString("name"), uptimePct = grpPct, history = grpHistory))
                val monitors = group.getJSONArray("monitors")
                for (j in 0 until monitors.length()) {
                    val m = monitors.getJSONObject(j)
                    val histArr = m.optJSONArray("history")
                    val history = if (histArr != null) (0 until histArr.length()).map { histArr.getInt(it) } else emptyList()
                    items.add(Item(TYPE_MONITOR, m.getString("name"), m.getInt("status"), m.optDouble("pct", 100.0).toFloat(), history))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached groups for widget $appWidgetId: ${e.message}")
        }
    }

    override fun onDestroy() { items.clear() }

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_monitor_row)

        // Per-widget appearance
        val darkMode      = when (Prefs.getWidgetTheme(context, appWidgetId)) {
            0    -> true
            1    -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val scale         = Prefs.getWidgetTextScalePct(context, appWidgetId) / 100f
        val fontPref      = Prefs.getWidgetFontColor(context, appWidgetId)
        val autoPrimary   = if (darkMode) 0xFFFFFFFF.toInt() else 0xFF1A1A2E.toInt()
        val autoSecondary = if (darkMode) 0xFFB0BEC5.toInt() else 0xFF607080.toInt()
        val primaryColor  = if (fontPref == 0) autoPrimary else fontPref
        val secondaryColor = if (fontPref == 0) autoSecondary
                             else (fontPref and 0x00FFFFFF) or 0xCC000000.toInt()

        return if (item.type == TYPE_HEADER) {
            RemoteViews(context.packageName, R.layout.widget_group_header).also {
                it.setTextViewText(R.id.group_name, item.label)
                it.setTextColor(R.id.group_name, secondaryColor)
                it.setTextViewTextSize(R.id.group_name, android.util.TypedValue.COMPLEX_UNIT_SP, 10f * scale)
            }
        } else {
            val pctColor = when {
                item.uptimePct >= 90f -> 0xFF4CAF50.toInt()
                item.uptimePct >= 70f -> 0xFFFF9800.toInt()
                else                  -> 0xFFF44336.toInt()
            }
            val pctText = if (item.uptimePct >= 100f) "100%" else "%.1f%%".format(item.uptimePct)
            RemoteViews(context.packageName, R.layout.widget_monitor_row).also {
                it.setTextViewText(R.id.uptime_pct, pctText)
                it.setTextColor(R.id.uptime_pct, pctColor)
                it.setTextViewTextSize(R.id.uptime_pct, android.util.TypedValue.COMPLEX_UNIT_SP, 10f * scale)
                it.setTextViewText(R.id.monitor_name, item.label)
                it.setTextColor(R.id.monitor_name, primaryColor)
                it.setTextViewTextSize(R.id.monitor_name, android.util.TypedValue.COMPLEX_UNIT_SP, 11f * scale)
                it.setImageViewBitmap(R.id.uptime_bars, drawBars(item.history))
            }
        }
    }

    override fun getLoadingView()   = null
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds()     = false

    private fun drawBars(history: List<Int>): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (72 * density).toInt().coerceAtLeast(1)
        val h = (14 * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (history.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val count  = history.size
        val gap    = density * 0.8f
        val barW   = (w - gap * (count - 1)) / count
        val radius = density * 0.8f
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        history.forEachIndexed { i, status ->
            paint.color = when (status) {
                MonitorStatus.STATUS_UP          -> 0xFF4CAF50.toInt()
                MonitorStatus.STATUS_DOWN        -> 0xFFF44336.toInt()
                MonitorStatus.STATUS_PENDING     -> 0xFFFFC107.toInt()
                MonitorStatus.STATUS_MAINTENANCE -> 0xFF9E9E9E.toInt()
                else                             -> 0xFF444444.toInt()
            }
            val left = i * (barW + gap)
            canvas.drawRoundRect(left, 0f, left + barW, h.toFloat(), radius, radius, paint)
        }
        return bmp
    }
}

// JSON serialisation helpers used by WidgetUpdateService
fun groupsToJson(groups: List<MonitorGroup>): String {
    val arr = JSONArray()
    for (group in groups) {
        val monitors = JSONArray()
        for (m in group.monitors) {
            monitors.put(JSONObject().apply {
                put("id",      m.id)
                put("name",    m.name)
                put("status",  m.status)
                put("pct",     m.uptimePct)
                put("history", JSONArray(m.history))
            })
        }
        arr.put(JSONObject().apply {
            put("name",     group.name)
            put("pct",      group.uptimePct)
            put("history",  JSONArray(group.history))
            put("monitors", monitors)
        })
    }
    return arr.toString()
}
