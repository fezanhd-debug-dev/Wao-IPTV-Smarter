package com.wao.iptv

import com.google.gson.JsonParser

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Failed(val reason: String) : UpdateResult()
}

const val UPDATE_CHECK_URL =
    "https://raw.githubusercontent.com/fezanhd-debug-dev/Wao-IPTV-Smarter/main/version.json"

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

        val remoteCode = o.get("versionCode")?.let { if (it.isJsonPrimitive) it.asInt else 0 } ?: 0
        val info = UpdateInfo(remoteCode, str("versionName"), str("apkUrl"), str("notes"))

        if (remoteCode > currentVersionCode) UpdateResult.Available(info) else UpdateResult.UpToDate
    } catch (e: Exception) {
        UpdateResult.Failed(e.message ?: "Update check nahi ho saka")
    }
}
