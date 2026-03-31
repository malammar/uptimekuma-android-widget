package com.sifrlabs.uptimekuma

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import org.json.JSONArray

object Prefs {
    private const val NAME = "uptime_widget"

    // ── Profiles ──────────────────────────────────────────────────────────────

    fun getProfiles(ctx: Context): List<Profile> {
        val json = prefs(ctx).getString("profiles", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveProfiles(ctx: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(Profile.toJson(it)) }
        prefs(ctx).edit().putString("profiles", arr.toString()).apply()
    }

    fun getProfile(ctx: Context, id: String): Profile? =
        getProfiles(ctx).find { it.id == id }

    fun upsertProfile(ctx: Context, profile: Profile) {
        val list = getProfiles(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        saveProfiles(ctx, list)
        // Clear any cached slug so a changed hostname re-triggers discovery.
        clearCachedSlug(ctx, profile.id)
    }

    fun deleteProfile(ctx: Context, id: String) {
        saveProfiles(ctx, getProfiles(ctx).filter { it.id != id })
        clearCachedSlug(ctx, id)
    }

    // ── Auto-discovered slug cache ─────────────────────────────────────────────

    fun getCachedSlug(ctx: Context, profileId: String): String? =
        prefs(ctx).getString("slug_$profileId", null)

    fun setCachedSlug(ctx: Context, profileId: String, slug: String) =
        prefs(ctx).edit().putString("slug_$profileId", slug).apply()

    private fun clearCachedSlug(ctx: Context, profileId: String) =
        prefs(ctx).edit().remove("slug_$profileId").apply()

    // ── Widget ↔ Profile mapping ───────────────────────────────────────────────

    fun getProfileIdForWidget(ctx: Context, appWidgetId: Int): String? =
        prefs(ctx).getString("widget_profile_$appWidgetId", null)

    fun setProfileIdForWidget(ctx: Context, appWidgetId: Int, profileId: String) =
        prefs(ctx).edit().putString("widget_profile_$appWidgetId", profileId).apply()

    fun removeWidget(ctx: Context, appWidgetId: Int) {
        prefs(ctx).edit()
            .remove("widget_profile_$appWidgetId")
            .remove("cache_$appWidgetId")
            .remove("bg_color_$appWidgetId")
            .apply()
    }

    // ── Per-widget appearance ──────────────────────────────────────────────────

    fun getWidgetBgColor(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("bg_color_$appWidgetId", 0xCC1A1A2E.toInt())

    fun setWidgetBgColor(ctx: Context, appWidgetId: Int, color: Int) =
        prefs(ctx).edit().putInt("bg_color_$appWidgetId", color).apply()

    // ── Per-widget cache ───────────────────────────────────────────────────────

    fun cachedGroupsJson(ctx: Context, appWidgetId: Int): String? =
        prefs(ctx).getString("cache_$appWidgetId", null)

    fun saveCachedGroups(ctx: Context, appWidgetId: Int, json: String) =
        prefs(ctx).edit().putString("cache_$appWidgetId", json).apply()

    // ── Wizard flag ────────────────────────────────────────────────────────────

    fun isWizardDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean("wizard_done", false)

    fun setWizardDone(ctx: Context) =
        prefs(ctx).edit().putBoolean("wizard_done", true).apply()

    // ── Migration from v1.1 flat keys ─────────────────────────────────────────

    fun migrateIfNeeded(ctx: Context) {
        val raw = prefs(ctx)
        if (!raw.contains("hostname")) return   // already migrated or fresh install

        val hostname = raw.getString("hostname", "") ?: ""
        val slug     = raw.getString("slug", "default") ?: "default"
        val interval = raw.getInt("interval_minutes", 5)
        val cached   = raw.getString("cached_groups", null)

        if (hostname.isNotEmpty()) {
            val profile = Profile(
                id              = Profile.newId(),
                name            = "Default",
                hostname        = hostname,
                slug            = slug,
                intervalMinutes = interval,
                authEnabled     = false,
                username        = "",
                password        = ""
            )
            saveProfiles(ctx, listOf(profile))

            val manager = AppWidgetManager.getInstance(ctx)
            val widgetIds = manager.getAppWidgetIds(ComponentName(ctx, UptimeWidget::class.java))
            for (id in widgetIds) {
                setProfileIdForWidget(ctx, id, profile.id)
                if (cached != null) saveCachedGroups(ctx, id, cached)
            }
            setWizardDone(ctx)
        }

        raw.edit()
            .remove("hostname")
            .remove("slug")
            .remove("interval_minutes")
            .remove("cached_groups")
            .apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, MODE_PRIVATE)
}
