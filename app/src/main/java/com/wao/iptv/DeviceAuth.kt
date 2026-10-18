package com.wao.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.gson.JsonParser
import java.util.UUID

data class DeviceActivationResult(
    val activated: Boolean,
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val error: String = ""
)

const val DEVICE_CHECK_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/sys-x7f3k2/check_device.php"

// Device ID ab app ke apne storage me ek dafa generate ho kar hamesha ke liye save
// hoti hai — sirf app uninstall karne par hi badlegi, future updates par nahi.
@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    val store = Store(context)
    store.loadDeviceId()?.let { return it }

    // Pehli dafa: purane ANDROID_ID se continuity rakhte hain (jin devices ki ID
    // pehle se activate ho chuki hai unko dobara ID share na karni pare)
    val seed = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (e: Exception) { null }

    val id = (seed?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", "")).uppercase()
    store.saveDeviceId(id)
    return id
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
        DeviceActivationResult(activated = bool("activated"), host = str("host"), user = str("user"), pass = str("pass"))
    } catch (e: Exception) {
        DeviceActivationResult(false, error = e.message ?: "Connect nahi ho saka")
    }
}
