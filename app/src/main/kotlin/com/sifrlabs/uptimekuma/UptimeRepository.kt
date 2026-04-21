package com.sifrlabs.uptimekuma

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UptimeRepository(private val context: Context) {

    fun fetchGroups(profile: Profile, showGroupMonitors: Boolean = false): List<MonitorGroup> {
        val hostname = profile.hostname
        val slug     = profile.slug.ifEmpty { "default" }

        val configBody    = get("$hostname/api/status-page/$slug", profile)
        val heartbeatBody = get("$hostname/api/status-page/heartbeat/$slug", profile)

        data class MonitorData(val status: Int, val uptimePct: Float, val history: List<Int>)
        val monitorData = HashMap<Int, MonitorData>()
        val heartbeatRoot = JSONObject(heartbeatBody)
        val heartbeatList = heartbeatRoot.getJSONObject("heartbeatList")
        val uptimeList    = heartbeatRoot.optJSONObject("uptimeList")

        val keys = heartbeatList.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = heartbeatList.getJSONArray(key)
            if (arr.length() == 0) continue
            val history = (0 until arr.length()).map { arr.getJSONObject(it).getInt("status") }
            val uptimePct = uptimeList?.optDouble("${key}_24", -1.0)
                ?.takeIf { it >= 0 }
                ?.let { (it * 100).toFloat() }
                ?: (history.count { it == MonitorStatus.STATUS_UP } * 100f / history.size)
            monitorData[key.toInt()] = MonitorData(history.last(), uptimePct, history)
        }

        val groups = mutableListOf<MonitorGroup>()
        val publicGroupList = JSONObject(configBody).getJSONArray("publicGroupList")
        for (i in 0 until publicGroupList.length()) {
            val group      = publicGroupList.getJSONObject(i)
            val groupName  = group.getString("name")
            val monitorList = group.optJSONArray("monitorList") ?: continue
            val monitors   = mutableListOf<MonitorStatus>()
            for (j in 0 until monitorList.length()) {
                val m = monitorList.getJSONObject(j)
                if (m.optString("type", "") == "group" && !showGroupMonitors) continue
                val id   = m.getInt("id")
                val data = monitorData[id] ?: continue
                monitors.add(MonitorStatus(id, m.getString("name"), data.status, data.uptimePct, data.history))
            }
            if (monitors.isNotEmpty()) {
                val avgPct = monitors.map { it.uptimePct }.average().toFloat()
                val minLen = monitors.minOf { it.history.size }
                val aggHistory = (0 until minLen).map { i ->
                    val statuses = monitors.map { it.history[i] }
                    when {
                        statuses.any { it == MonitorStatus.STATUS_DOWN }        -> MonitorStatus.STATUS_DOWN
                        statuses.any { it == MonitorStatus.STATUS_PENDING }     -> MonitorStatus.STATUS_PENDING
                        statuses.any { it == MonitorStatus.STATUS_MAINTENANCE } -> MonitorStatus.STATUS_MAINTENANCE
                        else                                                    -> MonitorStatus.STATUS_UP
                    }
                }
                groups.add(MonitorGroup(groupName, monitors, avgPct, aggHistory))
            }
        }
        return groups
    }

    private fun get(url: String, profile: Profile): String =
        openConn(url, profile, "application/json").let { conn ->
            try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
        }

    private fun openConn(url: String, profile: Profile, accept: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout    = 15_000
        conn.requestMethod  = "GET"
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("Connection", "close")
        if (profile.authEnabled && profile.username.isNotEmpty()) {
            val credentials = "${profile.username}:${profile.password}"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $encoded")
        }
        return conn
    }
}
