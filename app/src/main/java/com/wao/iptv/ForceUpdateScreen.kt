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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ForceUpdateScreen(info: UpdateInfo, lang: String) {
    val uriHandler = LocalUriHandler.current

    BackHandler { }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
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
            Spacer(Modifier.height(28.dp))
            val btnShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .background(Brush.horizontalGradient(listOf(Cyan, Blue)), btnShape)
                    .clickable { if (info.apkUrl.isNotBlank()) uriHandler.openUri(info.apkUrl) }
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Download, null, tint = Slate950, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Txt(tr(lang, "update_now"), 14, Slate950, FontWeight.Black)
            }
        }
    }
}
