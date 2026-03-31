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
    }

    fun deleteProfile(ctx: Context, id: String) {
        saveProfiles(ctx, getProfiles(ctx).filter { it.id != id })
    }

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
            .remove("widget_theme_$appWidgetId")
            .remove("header_bg_$appWidgetId")
            .remove("footer_bg_$appWidgetId")
            .remove("font_color_$appWidgetId")
            .remove("text_scale_$appWidgetId")
            .remove("show_group_monitors_$appWidgetId")
            .apply()
    }

    // ── Per-widget appearance ──────────────────────────────────────────────────

    fun getWidgetBgColor(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("bg_color_$appWidgetId", 0xCC1A1A2E.toInt())

    fun setWidgetBgColor(ctx: Context, appWidgetId: Int, color: Int) =
        prefs(ctx).edit().putInt("bg_color_$appWidgetId", color).apply()

    // ── Per-widget appearance extras ───────────────────────────────────────────

    // 0 = Dark, 1 = Light, 2 = System (default)
    fun getWidgetTheme(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("widget_theme_$appWidgetId", 2)
    fun setWidgetTheme(ctx: Context, appWidgetId: Int, v: Int) =
        prefs(ctx).edit().putInt("widget_theme_$appWidgetId", v).apply()

    // 0 = auto-compute from bg color
    fun getWidgetHeaderBg(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("header_bg_$appWidgetId", 0)
    fun setWidgetHeaderBg(ctx: Context, appWidgetId: Int, v: Int) =
        prefs(ctx).edit().putInt("header_bg_$appWidgetId", v).apply()

    // 0 = same as widget bg
    fun getWidgetFooterBg(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("footer_bg_$appWidgetId", 0)
    fun setWidgetFooterBg(ctx: Context, appWidgetId: Int, v: Int) =
        prefs(ctx).edit().putInt("footer_bg_$appWidgetId", v).apply()

    // 0 = auto from dark mode
    fun getWidgetFontColor(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("font_color_$appWidgetId", 0)
    fun setWidgetFontColor(ctx: Context, appWidgetId: Int, v: Int) =
        prefs(ctx).edit().putInt("font_color_$appWidgetId", v).apply()

    fun getWidgetShowGroupMonitors(ctx: Context, appWidgetId: Int): Boolean =
        prefs(ctx).getBoolean("show_group_monitors_$appWidgetId", false)
    fun setWidgetShowGroupMonitors(ctx: Context, appWidgetId: Int, v: Boolean) =
        prefs(ctx).edit().putBoolean("show_group_monitors_$appWidgetId", v).apply()

    // stored as integer percent: 85, 100, 120, 140
    fun getWidgetTextScalePct(ctx: Context, appWidgetId: Int): Int =
        prefs(ctx).getInt("text_scale_$appWidgetId", 100)
    fun setWidgetTextScalePct(ctx: Context, appWidgetId: Int, v: Int) =
        prefs(ctx).edit().putInt("text_scale_$appWidgetId", v).apply()

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
