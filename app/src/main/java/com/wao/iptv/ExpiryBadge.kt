package com.wao.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

// Pakistan Standard Time (Asia/Karachi) ke hisab se bache hue din.
// -1 = expire ho chuka, 0 = aaj expire hoga
fun pktDaysLeft(expEpochSec: Long, nowMs: Long = System.currentTimeMillis()): Int {
    val expMs = expEpochSec * 1000L
    if (expMs < nowMs) return -1
    val tz = TimeZone.getTimeZone("Asia/Karachi")
    fun dayStart(ms: Long): Long {
        val c = Calendar.getInstance(tz)
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    return ((dayStart(expMs) - dayStart(nowMs)) / 86400000L).toInt()
}

@Composable
fun ExpiryBadge(expDate: Long) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val days = pktDaysLeft(expDate, now)
    val color = when {
        days >= 5 -> Color(0xFF34D399)   // green
        days == 4 -> Color(0xFFFB923C)   // orange
        else -> Color(0xFFEF4444)        // red (3 ya kam / expired)
    }
    val label = when {
        days < 0 -> "Expired"
        days == 0 -> "Today"
        days == 1 -> "day left"
        else -> "days left"
    }
    Box(
        Modifier
            .size(66.dp)
            .background(Color(0xE60A0E1A), CircleShape)
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (days < 0) "0" else days.toString(), 20, color, FontWeight.Black, align = TextAlign.Center)
            Txt(label, 8, Color.White, FontWeight.SemiBold, align = TextAlign.Center)
        }
    }
}
