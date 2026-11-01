package com.wao.iptv

import java.net.URLEncoder

const val ACTIVITY_LOG_URL = "https://iptv-updates.duckdns.org:10066/wao-iptv/sys-x7f3k2/log_activity.php"

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

// Sirf asal successful Xtream login par: reseller panel ka "Today Successful Logins" isi se barhta hai
fun logLoginOk(deviceId: String, label: String) {
    try {
        httpGet("$ACTIVITY_LOG_URL?id=${enc(deviceId)}&type=login_ok&title=${enc(label)}")
    } catch (e: Exception) { }
}

// Channel / movie / episode play shuru hone par ek dafa: reseller panel ki History me entry
fun logWatchStart(deviceId: String, title: String, kind: String) {
    try {
        httpGet("$ACTIVITY_LOG_URL?id=${enc(deviceId)}&type=watch_start&title=${enc(title)}&kind=${enc(kind)}")
    } catch (e: Exception) { }
}

// Har 5 second: LIVE NOW status aur kitni der se dekh raha hai
fun sendHeartbeat(deviceId: String, title: String, kind: String, positionSec: Long) {
    try {
        httpGet("$ACTIVITY_LOG_URL?id=${enc(deviceId)}&type=heartbeat&title=${enc(title)}&kind=${enc(kind)}&position=$positionSec")
    } catch (e: Exception) { }
}
