package com.wao.iptv

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

data class DeviceActivationResult(
    val activated: Boolean,
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val error: String = ""
)

const val DEVICE_CHECK_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/sys-x7f3k2/check_device.php"
private const val BACKUP_FILE_NAME = ".wao_device_id.txt"

// Downloads folder me Device ID ka extra backup — yeh folder app uninstall hone par
// bhi nahi mitta (scoped storage, Android 10+), is liye kisi bhi wajah se agar
// internal storage saaf ho jaye (naya signing key, app data clear waghera) to
// yahan se purani ID wapas mil jati hai aur client ko dobara ID share nahi karni parti.
@SuppressLint("HardwareIds")
private fun readBackupId(ctx: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return try {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val sel = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        resolver.query(collection, proj, sel, arrayOf(BACKUP_FILE_NAME), null)?.use { c ->
            if (c.moveToFirst()) {
                val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(idCol))
                resolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readLine()?.trim()?.takeIf { it.isNotBlank() }
                }
            } else null
        }
    } catch (e: Exception) { null }
}

private fun writeBackupId(ctx: Context, id: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    try {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val sel = "${MediaStore.Downloads.DISPLAY_NAME}=?"
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), sel, arrayOf(BACKUP_FILE_NAME), null)?.use { c ->
            if (c.moveToFirst()) return // already exists, don't overwrite
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(id.toByteArray()) }
    } catch (e: Exception) { /* best-effort only */ }
}

@SuppressLint("HardwareIds")
fun getDeviceId(context: Context): String {
    val store = Store(context)
    store.loadDeviceId()?.let { return it }

    // Internal storage khali hai — pehle backup check karein
    readBackupId(context)?.let { backup ->
        store.saveDeviceId(backup)
        return backup
    }

    val seed = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (e: Exception) { null }

    val id = (seed?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", "")).uppercase()
    store.saveDeviceId(id)
    writeBackupId(context, id)
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
