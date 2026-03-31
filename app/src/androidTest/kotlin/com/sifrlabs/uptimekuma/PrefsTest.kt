package com.sifrlabs.uptimekuma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        ctx.getSharedPreferences("uptime_widget", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    @Test
    fun getProfiles_fresh_returnsEmpty() {
        assertTrue(Prefs.getProfiles(ctx).isEmpty())
    }

    @Test
    fun upsertProfile_add_retrievable() {
        val p = makeProfile("1", "Alpha")
        Prefs.upsertProfile(ctx, p)
        assertEquals(p, Prefs.getProfile(ctx, "1"))
    }

    @Test
    fun upsertProfile_update_overwrites() {
        val original = makeProfile("1", "Alpha")
        val updated  = original.copy(name = "Beta")
        Prefs.upsertProfile(ctx, original)
        Prefs.upsertProfile(ctx, updated)
        val profiles = Prefs.getProfiles(ctx)
        assertEquals(1, profiles.size)
        assertEquals("Beta", profiles[0].name)
    }

    @Test
    fun deleteProfile_removesCorrectOne() {
        Prefs.upsertProfile(ctx, makeProfile("1", "Alpha"))
        Prefs.upsertProfile(ctx, makeProfile("2", "Beta"))
        Prefs.deleteProfile(ctx, "1")
        val profiles = Prefs.getProfiles(ctx)
        assertEquals(1, profiles.size)
        assertEquals("2", profiles[0].id)
    }

    @Test
    fun getProfile_unknownId_returnsNull() {
        assertNull(Prefs.getProfile(ctx, "nonexistent"))
    }

    // ── Widget ↔ Profile mapping ───────────────────────────────────────────────

    @Test
    fun widgetProfileMapping_setAndGet() {
        Prefs.setProfileIdForWidget(ctx, 42, "profile-abc")
        assertEquals("profile-abc", Prefs.getProfileIdForWidget(ctx, 42))
    }

    @Test
    fun widgetProfileMapping_unknownWidget_returnsNull() {
        assertNull(Prefs.getProfileIdForWidget(ctx, 999))
    }

    @Test
    fun removeWidget_clearsProfileMapping() {
        Prefs.setProfileIdForWidget(ctx, 7, "p1")
        Prefs.removeWidget(ctx, 7)
        assertNull(Prefs.getProfileIdForWidget(ctx, 7))
    }

    @Test
    fun removeWidget_clearsCache() {
        Prefs.saveCachedGroups(ctx, 7, "[{\"name\":\"grp\",\"monitors\":[]}]")
        Prefs.removeWidget(ctx, 7)
        assertNull(Prefs.cachedGroupsJson(ctx, 7))
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    @Test
    fun cachedGroupsJson_saveAndRead() {
        val json = "[{\"name\":\"Services\",\"monitors\":[]}]"
        Prefs.saveCachedGroups(ctx, 3, json)
        assertEquals(json, Prefs.cachedGroupsJson(ctx, 3))
    }

    @Test
    fun cachedGroupsJson_unknownWidget_returnsNull() {
        assertNull(Prefs.cachedGroupsJson(ctx, 999))
    }

    // ── Wizard flag ────────────────────────────────────────────────────────────

    @Test
    fun wizardFlag_defaultFalse() {
        assertFalse(Prefs.isWizardDone(ctx))
    }

    @Test
    fun wizardFlag_setToTrue() {
        Prefs.setWizardDone(ctx)
        assertTrue(Prefs.isWizardDone(ctx))
    }

    // ── Migration ─────────────────────────────────────────────────────────────

    @Test
    fun migrate_withHostname_createsProfile() {
        val prefs = ctx.getSharedPreferences("uptime_widget", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("hostname", "https://uptime.example.com")
            .putString("slug", "home")
            .putInt("interval_minutes", 10)
            .commit()

        Prefs.migrateIfNeeded(ctx)

        val profiles = Prefs.getProfiles(ctx)
        assertEquals(1, profiles.size)
        assertEquals("Default",                  profiles[0].name)
        assertEquals("https://uptime.example.com", profiles[0].hostname)
        assertEquals("home",                     profiles[0].slug)
        assertEquals(10,                         profiles[0].intervalMinutes)
        assertFalse(profiles[0].authEnabled)
    }

    @Test
    fun migrate_withHostname_removesOldKeys() {
        val prefs = ctx.getSharedPreferences("uptime_widget", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("hostname", "https://uptime.example.com")
            .putString("slug", "home")
            .putInt("interval_minutes", 5)
            .commit()

        Prefs.migrateIfNeeded(ctx)

        assertFalse(prefs.contains("hostname"))
        assertFalse(prefs.contains("slug"))
        assertFalse(prefs.contains("interval_minutes"))
        assertFalse(prefs.contains("cached_groups"))
    }

    @Test
    fun migrate_withHostname_setsWizardDone() {
        val prefs = ctx.getSharedPreferences("uptime_widget", Context.MODE_PRIVATE)
        prefs.edit().putString("hostname", "https://uptime.example.com").commit()

        Prefs.migrateIfNeeded(ctx)

        assertTrue(Prefs.isWizardDone(ctx))
    }

    @Test
    fun migrate_emptyHostname_noProfileCreated() {
        val prefs = ctx.getSharedPreferences("uptime_widget", Context.MODE_PRIVATE)
        prefs.edit().putString("hostname", "").commit()

        Prefs.migrateIfNeeded(ctx)

        assertTrue(Prefs.getProfiles(ctx).isEmpty())
        assertFalse(prefs.contains("hostname"))
    }

    @Test
    fun migrate_alreadyMigrated_isNoOp() {
        val p = makeProfile("existing", "Existing")
        Prefs.upsertProfile(ctx, p)

        Prefs.migrateIfNeeded(ctx)

        val profiles = Prefs.getProfiles(ctx)
        assertEquals(1, profiles.size)
        assertEquals("Existing", profiles[0].name)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeProfile(id: String, name: String) = Profile(
        id              = id,
        name            = name,
        hostname        = "https://example.com",
        slug            = "default",
        intervalMinutes = 5,
        authEnabled     = false,
        username        = "",
        password        = ""
    )
}
