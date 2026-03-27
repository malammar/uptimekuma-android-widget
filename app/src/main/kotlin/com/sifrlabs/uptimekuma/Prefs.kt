package com.sifrlabs.uptimekuma

import android.content.Context

object Prefs {
    private const val NAME = "uptime_widget"
    private const val KEY_HOSTNAME = "hostname"
    private const val KEY_SLUG = "slug"
    private const val KEY_INTERVAL = "interval_minutes"

    fun hostname(ctx: Context): String =
        prefs(ctx).getString(KEY_HOSTNAME, "") ?: ""

    fun slug(ctx: Context): String =
        prefs(ctx).getString(KEY_SLUG, "home") ?: "home"

    fun intervalMinutes(ctx: Context): Int =
        prefs(ctx).getInt(KEY_INTERVAL, 5)

    fun save(ctx: Context, hostname: String, slug: String, intervalMinutes: Int) {
        prefs(ctx).edit()
            .putString(KEY_HOSTNAME, hostname)
            .putString(KEY_SLUG, slug)
            .putInt(KEY_INTERVAL, intervalMinutes)
            .apply()
    }

    fun cachedGroupsJson(ctx: Context): String? = prefs(ctx).getString("cached_groups", null)

    fun saveCachedGroups(ctx: Context, json: String) =
        prefs(ctx).edit().putString("cached_groups", json).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
