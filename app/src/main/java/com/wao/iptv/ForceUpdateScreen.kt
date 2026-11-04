package com.wao.iptv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ForceUpdateScreen(info: UpdateInfo, lang: String, onDismiss: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf(0) }
    var apk by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPermission by remember { mutableStateOf(false) }

    // Force update me back band, normal update me back se "Later"
    BackHandler { if (!info.forceUpdate && !downloading) onDismiss() }

    fun startDownload() {
        if (downloading || info.apkUrl.isBlank()) return
        downloading = true
        error = null
        needPermission = false
        percent = 0
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    Updater.download(ctx, info.apkUrl) { p -> percent = if (p < 0) 0 else p }
                }
                apk = f
                percent = 100
                downloading = false
                val opened = Updater.install(ctx, f)
                if (!opened) needPermission = true
            } catch (e: Exception) {
                downloading = false
                error = e.message ?: "Download nahi ho saka"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).widthIn(max = 420.dp)
        ) {
            BigLogo()
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Filled.SystemUpdate, null, tint = Cyan, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Txt(tr(lang, "update_required_title"), 20, Color.White, FontWeight.Black, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Txt(
                tr(lang, "update_required_desc"), 13, Slate400,
                modifier = Modifier.fillMaxWidth(), align = TextAlign.Center
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Txt(info.notes, 11, Slate500, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
            if (info.versionName.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Txt("v${info.versionName}", 11, Slate500)
            }
            Spacer(Modifier.height(24.dp))

            if (downloading) {
                val shape = RoundedCornerShape(50)
                Txt("Downloading... $percent%", 14, Cyan, FontWeight.Bold, align = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(Slate800, shape)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(percent / 100f)
                            .background(Brush.horizontalGradient(listOf(Cyan, Blue)), shape)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Txt("Meharbani kar ke intezar karein, app band na karein", 11, Slate400, align = TextAlign.Center)
            } else {
                val btnShape = RoundedCornerShape(16.dp)
                val label = when {
                    apk != null -> "Install Now"
                    error != null -> "Retry"
                    else -> tr(lang, "update_now")
                }
                Row(
                    Modifier
                        .background(Brush.horizontalGradient(listOf(Cyan, Blue)), btnShape)
                        .clickable {
                            val f = apk
                            if (f != null && f.exists()) {
                                if (!Updater.install(ctx, f)) needPermission = true
                            } else startDownload()
                        }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Download, null, tint = Slate950, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Txt(label, 14, Slate950, FontWeight.Black)
                }
                if (needPermission) {
                    Spacer(Modifier.height(12.dp))
                    Txt(
                        "Settings me 'Allow from this source' on karein, phir wapas aa kar Install Now dabayen",
                        11, Amber, align = TextAlign.Center
                    )
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Txt(it, 11, RedSoft, align = TextAlign.Center)
                }
                if (!info.forceUpdate) {
                    Spacer(Modifier.height(16.dp))
                    Txt(
                        "Later", 12, Slate400, FontWeight.SemiBold,
                        modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                    )
                }
            }
        }
    }
}
