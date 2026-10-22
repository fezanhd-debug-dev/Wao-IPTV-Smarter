package com.wao.iptv

import java.net.URLEncoder

const val ACTIVITY_LOG_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/sys-x7f3k2/log_activity.php"

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

// Sirf tab call hota hai jab Xtream server ne asal me login/authenticate successful
// kiya ho — app khulna ya screen dikhna is me shamil nahi, isi liye "Today Successful
// Login" ka number bharosemand hota hai.
fun logLoginOk(deviceId: String, label: String) {
    try {
        httpGet("$ACTIVITY_LOG_URL?id=${enc(deviceId)}&type=login_ok&title=${enc(label)}")
    } catch (e: Exception) { /* best-effort */ }
}

// Har kuch second me player se bheja jata hai — isi se reseller panel "Now Watching"
// aur "LIVE NOW" badge (saath me video preview) dikhata hai
fun sendHeartbeat(deviceId: String, title: String, kind: String, url: String, positionSec: Long) {
    try {
        httpGet(
            "$ACTIVITY_LOG_URL?id=${enc(deviceId)}&type=heartbeat&title=${enc(title)}" +
                "&kind=${enc(kind)}&url=${enc(url)}&position=$positionSec"
        )
    } catch (e: Exception) { /* best-effort */ }
}
