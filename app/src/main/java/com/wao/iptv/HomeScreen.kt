package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage

@Composable
private fun QuickTile(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.glass(shape).tvClick(shape, onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(40.dp).background(tint.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Txt(label, 11, Slate200, FontWeight.Bold)
    }
}

private fun remainingText(h: HistoryItem): String {
    if (h.isLive || h.duration <= 0) return "Live channel"
    val rem = ((h.duration - h.position) / 60000L).coerceAtLeast(0)
    return if (rem >= 60) "${rem / 60}h ${rem % 60}m remaining" else "${rem}m remaining"
}

@Composable
private fun HistoryCard(h: HistoryItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(192.dp).glass(shape).tvClick(shape, onClick = onClick).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Slate900)) {
            AsyncImage(
                model = h.poster,
                contentDescription = null,
                contentScale = if (h.isLive) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(if (h.isLive) 16.dp else 0.dp)
            )
            if (h.duration > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Slate700)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((h.position.toFloat() / h.duration).coerceIn(0.02f, 1f))
                            .background(Cyan)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(h.title, 12, Color.White, FontWeight.Bold, maxLines = 1)
        Txt(remainingText(h), 10, Slate400, maxLines = 1)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val all = remember(vm.channels, vm.lockActive) { vm.visibleChannels() }
    val featured = remember(all) { all.firstOrNull { it.icon.isNotBlank() } ?: all.firstOrNull() }
    val catName = remember(vm.liveCats) { vm.liveCats.associate { it.id to it.name } }
    LaunchedEffect(featured?.id) { featured?.let { vm.loadEpg(it) } }
    val now = System.currentTimeMillis() / 1000
    val cur = featured?.let { f -> vm.epg[f.id]?.firstOrNull { now in it.start..it.end } }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(Cyan, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Slate950, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Txt("Wao IPTV Smarter", 16, Color.White, FontWeight.Bold)
                    Txt("PREMIUM UHD", 10, Cyan, FontWeight.SemiBold, spacing = 1f)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .glass(RoundedCornerShape(12.dp))
                        .tvClick(RoundedCornerShape(12.dp)) { nav.navigate("live") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Search, null, tint = Slate300, modifier = Modifier.size(16.dp))
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Brush.linearGradient(listOf(Cyan, PurpleDeep)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Txt("VIP", 12, Color.White, FontWeight.Bold)
                }
            }
        }
        HLine()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (featured != null) {
                val heroShape = RoundedCornerShape(24.dp)
                Box(
                    Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(heroShape)
                        .border(1.dp, Cyan.copy(alpha = 0.2f), heroShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF17233F), Slate900)))
                ) {
                    AsyncImage(
                        model = featured.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(100.dp)
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = 0.4f), Bg))
                        )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Pill("🔴 LIVE NOW", Red, Color.White)
                        Txt(featured.name, 20, Color.White, FontWeight.Black, Modifier.padding(top = 6.dp), maxLines = 2)
                        Txt(
                            cur?.title ?: (catName[featured.categoryId] ?: "Live TV"),
                            12, Slate300, modifier = Modifier.padding(top = 2.dp), maxLines = 1
                        )
                        val btn = RoundedCornerShape(12.dp)
                        Row(
                            Modifier
                                .padding(top = 12.dp)
                                .background(Cyan, btn)
                                .tvClick(btn) {
                                    vm.playChannel(featured)
                                    nav.navigate("player")
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Slate950, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Txt("Watch Stream", 12, Slate950, FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickTile("Live TV", Icons.Filled.LiveTv, Cyan, Modifier.weight(1f)) { nav.navigate("live") }
                QuickTile("Movies", Icons.Filled.Movie, Purple, Modifier.weight(1f)) {
                    vm.vodTab = 0
                    nav.navigate("vod")
                }
                QuickTile("Series", Icons.Filled.Tv, Pink, Modifier.weight(1f)) {
                    vm.vodTab = 1
                    nav.navigate("vod")
                }
                QuickTile("Settings", Icons.Filled.Settings, Amber, Modifier.weight(1f)) { nav.navigate("settings") }
            }

            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("CONTINUE WATCHING", 14, Slate400, FontWeight.Bold, spacing = 1f)
                Txt(
                    "See All", 12, Cyan, FontWeight.SemiBold,
                    modifier = Modifier.tvClick(RoundedCornerShape(8.dp)) { nav.navigate("vod") }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (vm.history.isEmpty()) {
                Txt("Abhi tak kuch nahi dekha. Kuch chalayein, yahan aa jayega.", 12, Slate500, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(vm.history) { h ->
                        HistoryCard(h) {
                            vm.nowPlaying = PlayItem(
                                url = h.url,
                                title = h.title,
                                subtitle = h.subtitle,
                                isLive = h.isLive,
                                poster = h.poster,
                                historyKey = h.key,
                                startPosition = if (h.isLive) 0L else h.position
                            )
                            nav.navigate("player")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        BottomNav("home", vm, nav)
    }
}
