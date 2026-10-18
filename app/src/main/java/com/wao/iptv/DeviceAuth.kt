package com.wao.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.gson.JsonParser

data class DeviceActivationResult(
    val activated: Boolean,
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val error: String = ""
)

// Device-activation check API — secret folder me, download link se alag
const val DEVICE_CHECK_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/sys-x7f3k2/check_device.php"

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    return raw.uppercase()
}

fun checkDeviceActivation(deviceId: String): DeviceActivationResult {
    return try {
        val body = httpGet("$DEVICE_CHECK_URL?id=$deviceId")
        val root = try {
            JsonParser.parseString(body)
        } catch (e: Exception) {
            return DeviceActivationResult(false, error = "Server se sahi jawab nahi mila. Baad me try karein.")
        }
        if (!root.isJsonObject) return DeviceActivationResult(false, error = "Server ne sahi jawab nahi diya")
        val o = root.asJsonObject
        fun str(k: String): String {
            val e = o.get(k)
            return if (e == null || e.isJsonNull) "" else e.asString
        }
        fun bool(k: String): Boolean {
            val e = o.get(k)
            return e != null && !e.isJsonNull && e.isJsonPrimitive && e.asBoolean
        }
        DeviceActivationResult(
            activated = bool("activated"),
            host = str("host"),
            user = str("user"),
            pass = str("pass")
        )
    } catch (e: Exception) {
        DeviceActivationResult(false, error = e.message ?: "Connect nahi ho saka")
    }
}
