package com.sifrlabs.uptimekuma

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupsJsonTest {

    @Test
    fun emptyGroups_returnsEmptyArray() {
        assertEquals("[]", groupsToJson(emptyList()))
    }

    @Test
    fun roundTrip_preservesGroupAndMonitorFields() {
        val history = listOf(
            MonitorStatus.STATUS_UP,
            MonitorStatus.STATUS_DOWN,
            MonitorStatus.STATUS_UP
        )
        val monitor = MonitorStatus(
            id         = 42,
            name       = "API",
            status     = MonitorStatus.STATUS_UP,
            uptimePct  = 98.5f,
            history    = history
        )
        val group = MonitorGroup(name = "Services", monitors = listOf(monitor))

        val json = groupsToJson(listOf(group))
        val arr  = JSONArray(json)

        assertEquals(1, arr.length())
        val g = arr.getJSONObject(0)
        assertEquals("Services", g.getString("name"))

        val monitors = g.getJSONArray("monitors")
        assertEquals(1, monitors.length())
        val m = monitors.getJSONObject(0)
        assertEquals(42,            m.getInt("id"))
        assertEquals("API",         m.getString("name"))
        assertEquals(MonitorStatus.STATUS_UP, m.getInt("status"))
        assertEquals(98.5f,         m.getDouble("pct").toFloat(), 0.001f)

        val hist = m.getJSONArray("history")
        assertEquals(3, hist.length())
        assertEquals(MonitorStatus.STATUS_UP,   hist.getInt(0))
        assertEquals(MonitorStatus.STATUS_DOWN, hist.getInt(1))
        assertEquals(MonitorStatus.STATUS_UP,   hist.getInt(2))
    }

    @Test
    fun roundTrip_multipleGroupsAndMonitors() {
        val groups = listOf(
            MonitorGroup("Group A", listOf(
                MonitorStatus(1, "Svc1", MonitorStatus.STATUS_UP,   100f),
                MonitorStatus(2, "Svc2", MonitorStatus.STATUS_DOWN,  0f)
            )),
            MonitorGroup("Group B", listOf(
                MonitorStatus(3, "Svc3", MonitorStatus.STATUS_PENDING, 50f)
            ))
        )
        val arr = JSONArray(groupsToJson(groups))
        assertEquals(2, arr.length())
        assertEquals("Group A", arr.getJSONObject(0).getString("name"))
        assertEquals(2, arr.getJSONObject(0).getJSONArray("monitors").length())
        assertEquals("Group B", arr.getJSONObject(1).getString("name"))
        assertEquals(1, arr.getJSONObject(1).getJSONArray("monitors").length())
    }

    @Test
    fun uptimePctFormat_exactlyHundred_showsNoDecimal() {
        assertEquals("100%", formatUptime(100f))
    }

    @Test
    fun uptimePctFormat_decimal_showsOnePlace() {
        assertEquals("99.5%",  formatUptime(99.5f))
        assertEquals("0.0%",   formatUptime(0f))
        assertEquals("87.3%",  formatUptime(87.3f))
    }

    // Mirrors the production expression in MonitorListFactory.getViewAt
    private fun formatUptime(pct: Float): String =
        if (pct >= 100f) "100%" else "%.1f%%".format(pct)
}
