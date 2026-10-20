package com.wao.iptv

import com.google.gson.JsonParser

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val forceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 0
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

const val UPDATE_CHECK_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/version.json"

fun checkForUpdate(currentVersionCode: Int): UpdateResult {
    return try {
        val body = httpGet(UPDATE_CHECK_URL)
        val root = JsonParser.parseString(body)
        if (!root.isJsonObject) return UpdateResult.Failed("Update file sahi format me nahi hai")
        val o = root.asJsonObject

        fun str(k: String): String {
            val e = o.get(k)
            return if (e == null || e.isJsonNull) "" else e.asString
        }
        fun int(k: String): Int {
            val e = o.get(k)
            return if (e == null || e.isJsonNull || !e.isJsonPrimitive) 0 else e.asInt
        }
        fun bool(k: String): Boolean {
            val e = o.get(k)
            return e != null && !e.isJsonNull && e.isJsonPrimitive && e.asBoolean
        }

        val remoteCode = int("versionCode")
        val info = UpdateInfo(
            versionCode = remoteCode,
            versionName = str("versionName"),
            apkUrl = str("apkUrl"),
            notes = str("notes"),
            forceUpdate = bool("forceUpdate"),
            minSupportedVersionCode = int("minSupportedVersionCode")
        )

        if (remoteCode > currentVersionCode) UpdateResult.Available(info) else UpdateResult.UpToDate
    } catch (e: Exception) {
        UpdateResult.Failed(e.message ?: "Update check nahi ho saka")
    }
}
