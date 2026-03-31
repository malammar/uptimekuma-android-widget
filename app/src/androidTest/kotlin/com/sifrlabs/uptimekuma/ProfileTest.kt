package com.sifrlabs.uptimekuma

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileTest {

    private val sample = Profile(
        id                = "test-id-123",
        name              = "Home Lab",
        hostname          = "https://uptime.example.com",
        slug              = "home",
        intervalMinutes   = 10,
        authEnabled       = true,
        username          = "admin",
        password          = "secret",
        showGroupMonitors = true
    )

    @Test
    fun roundTrip_allFields() {
        val decoded = Profile.fromJson(Profile.toJson(sample))
        assertEquals(sample, decoded)
    }

    @Test
    fun fromJson_missingOptionals_usesDefaults() {
        val json = org.json.JSONObject().apply {
            put("id", "x")
            put("name", "Test")
        }
        val p = Profile.fromJson(json)
        assertEquals("",        p.hostname)
        assertEquals("default", p.slug)
        assertEquals(5,         p.intervalMinutes)
        assertFalse(p.authEnabled)
        assertEquals("", p.username)
        assertEquals("", p.password)
        assertFalse(p.showGroupMonitors)
    }

    @Test
    fun fromJson_showGroupMonitors_roundTrip() {
        val on  = sample.copy(showGroupMonitors = true)
        val off = sample.copy(showGroupMonitors = false)
        assertTrue(Profile.fromJson(Profile.toJson(on)).showGroupMonitors)
        assertFalse(Profile.fromJson(Profile.toJson(off)).showGroupMonitors)
    }

    @Test
    fun newId_isValidUuidFormat() {
        val id = Profile.newId()
        val uuidRegex = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
        assertTrue("newId() '$id' is not a valid UUID", uuidRegex.matches(id))
    }

    @Test
    fun newId_isUnique() {
        assertNotEquals(Profile.newId(), Profile.newId())
    }
}
