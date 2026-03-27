package com.sifrlabs.uptimekuma

import android.content.Context
import android.net.ConnectivityManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UptimeRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun fetchGroups(): List<MonitorGroup> {
        val hostname = Prefs.hostname(context)
        val slug = Prefs.slug(context)

        val configBody = get("$hostname/api/status-page/$slug")
        val heartbeatBody = get("$hostname/api/status-page/heartbeat/$slug")

        // Build per-monitor data from heartbeat response
        data class MonitorData(val status: Int, val uptimePct: Float, val history: List<Int>)
        val monitorData = HashMap<Int, MonitorData>()
        val heartbeatRoot = JSONObject(heartbeatBody)
        val heartbeatList = heartbeatRoot.getJSONObject("heartbeatList")
        val uptimeList = heartbeatRoot.optJSONObject("uptimeList")

        val keys = heartbeatList.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = heartbeatList.getJSONArray(key)
            if (arr.length() == 0) continue
            val history = (0 until arr.length()).map { arr.getJSONObject(it).getInt("status") }
            // Use pre-calculated 24h uptime from uptimeList (fraction 0–1), fall back to computed
            val uptimePct = uptimeList?.optDouble("${key}_24", -1.0)
                ?.takeIf { it >= 0 }
                ?.let { (it * 100).toFloat() }
                ?: (history.count { it == MonitorStatus.STATUS_UP } * 100f / history.size)
            val lastStatus = history.last()
            monitorData[key.toInt()] = MonitorData(lastStatus, uptimePct, history)
        }

        // Build grouped list preserving publicGroupList order
        val groups = mutableListOf<MonitorGroup>()
        val publicGroupList = JSONObject(configBody).getJSONArray("publicGroupList")
        for (i in 0 until publicGroupList.length()) {
            val group = publicGroupList.getJSONObject(i)
            val groupName = group.getString("name")
            val monitorList = group.optJSONArray("monitorList") ?: continue
            val monitors = mutableListOf<MonitorStatus>()
            for (j in 0 until monitorList.length()) {
                val m = monitorList.getJSONObject(j)
                if (m.optString("type", "") == "group") continue
                val id = m.getInt("id")
                val data = monitorData[id] ?: continue
                monitors.add(MonitorStatus(id, m.getString("name"), data.status, data.uptimePct, data.history))
            }
            if (monitors.isNotEmpty()) groups.add(MonitorGroup(groupName, monitors))
        }
        return groups
    }

    private fun get(url: String): String {
        // Explicitly use the active network to avoid OxygenOS background DNS restrictions
        val network = connectivityManager.activeNetwork
        val conn = (if (network != null) network.openConnection(URL(url))
                    else URL(url).openConnection()) as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Connection", "close")
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
