package com.sifrlabs.uptimekuma

import org.json.JSONObject

data class Profile(
    val id: String,
    val name: String,
    val hostname: String,
    val slug: String,
    val intervalMinutes: Int,
    val authEnabled: Boolean,
    val username: String,
    val password: String,
) {
    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString()

        fun fromJson(obj: JSONObject) = Profile(
            id                = obj.getString("id"),
            name              = obj.getString("name"),
            hostname          = obj.optString("hostname", ""),
            slug              = obj.optString("slug", "default"),
            intervalMinutes   = obj.optInt("intervalMinutes", 5),
            authEnabled       = obj.optBoolean("authEnabled", false),
            username          = obj.optString("username", ""),
            password          = obj.optString("password", ""),
        )

        fun toJson(p: Profile): JSONObject = JSONObject().apply {
            put("id",                p.id)
            put("name",              p.name)
            put("hostname",          p.hostname)
            put("slug",              p.slug)
            put("intervalMinutes",   p.intervalMinutes)
            put("authEnabled",       p.authEnabled)
            put("username",          p.username)
            put("password",          p.password)
        }
    }
}
