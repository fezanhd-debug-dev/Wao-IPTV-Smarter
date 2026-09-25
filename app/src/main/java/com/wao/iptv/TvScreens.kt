package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
private fun TvRailIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .size(48.dp)
            .background(if (active) Cyan else Slate900, shape)
            .tvClick(shape, focusRequester = focusRequester, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = if (active) Slate950 else Slate400, modifier = Modifier.size(20.dp)) }
}

@Composable
fun TvHomeScreen(vm: AppViewModel, nav: NavController) {
    val all = remember(vm.channels, vm.lockActive) { vm.visibleChannels() }
    val hero = all.firstOrNull()

    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { homeFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize().background(TvBg)) {
        Column(
            Modifier.width(96.dp).fillMaxHeight().background(Bg).padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(Modifier.size(48.dp).background(Cyan, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Slate950, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TvRailIcon(Icons.Filled.Home, true, homeFocus) {}
                TvRailIcon(Icons.Filled.GridView, false) { nav.navigate("tv_quad") }
                TvRailIcon(Icons.Filled.Movie, false) { nav.navigate("vod") }
                TvRailIcon(Icons.Filled.Settings, false) { nav.navigate("settings") }
            }
            Txt("16:9 TV", 10, Slate500, FontWeight.Bold)
        }

        Column(Modifier.weight(1f).padding(32.dp).verticalScroll(rememberScrollState())) {
            if (hero != null) {
                val shape = RoundedCornerShape(24.dp)
                Box(Modifier.fillMaxWidth().aspectRatio(21f / 9f).clip(shape).background(Slate900)) {
                    AsyncImage(model = hero.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(48.dp))
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, TvBg))))
                    Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                        Pill("FEATURED", Cyan, Slate950)
                        Txt(hero.name, 34, Color.White, FontWeight.Black, Modifier.padding(top = 8.dp))
                        val btn = RoundedCornerShape(12.dp)
                        Row(
                            Modifier.padding(top = 16.dp).background(Cyan, btn)
                                .tvClick(btn) { vm.playChannel(hero); nav.navigate("player") }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Slate950, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Txt("Watch Now", 14, Slate950, FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Txt("Live Channels (D-Pad Select)", 14, Slate400, FontWeight.Bold, spacing = 1f)
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(all.take(30), key = { it.id }) { ch ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.width(240.dp).background(Slate900.copy(alpha = 0.8f), shape).border(1.dp, Slate800, shape)
                            .tvClick(shape) { vm.playChannel(ch); nav.navigate("player") }
                            .padding(10.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Slate800)) {
                            AsyncImage(model = ch.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            Pill("LIVE", Red, Color.White, Modifier.align(Alignment.TopStart).padding(6.dp))
                        }
                        Txt(ch.name, 14, Color.White, FontWeight.Bold, Modifier.padding(top = 6.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun TvQuadViewScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val all = remember(vm.channels, vm.lockActive) { vm.visibleChannels() }
    var slots by remember { mutableStateOf(all.take(4)) }
    var focus by remember { mutableStateOf(0) }
    val session = vm.session

    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstTileFocus.requestFocus() }
    }

    val players = remember { List(4) { buildPlayer(ctx, 0) } }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() } } }
    LaunchedEffect(slots, session) {
        players.forEachIndexed { i, p ->
            p.volume = if (i == focus) 1f else 0f
            val ch = slots.getOrNull(i)
            if (ch != null && session != null) {
                delay(150L * i)
                p.setMediaItem(androidx.media3.common.MediaItem.fromUri(liveUrl(session, ch)))
                p.prepare()
                p.playWhenReady = true
            }
        }
    }
    LaunchedEffect(focus) { players.forEachIndexed { i, p -> p.volume = if (i == focus) 1f else 0f } }

    Column(Modifier.fillMaxSize().background(TvBg).padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Slate900.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(8.dp)
                Txt(
                    "← Exit", 11, Color.White, FontWeight.Bold,
                    modifier = Modifier.background(Color(0x1AFFFFFF), shape).tvClick(shape) { nav.popBackStack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(12.dp))
                Txt("Multi-Screen Quad Matrix", 12, Color.White, FontWeight.Bold)
            }
            Txt("Audio Focus: Slot ${focus + 1}", 11, Cyan, FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        val ch = slots.getOrNull(i)
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(Color.Black)
                                .then(if (i == focus) Modifier.border(3.dp, NeonCyan, shape) else Modifier.border(1.dp, Slate800, shape))
                                .tvClick(shape, focusRequester = if (i == 0) firstTileFocus else null) { focus = i }
                        ) {
                            VideoSurface(players[i], AspectRatioFrameLayout.RESIZE_MODE_ZOOM, Modifier.fillMaxSize())
                            Row(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                                Pill("SLOT ${i + 1}", if (i == focus) Cyan else Slate800, if (i == focus) Slate950 else Slate300)
                                Spacer(Modifier.width(6.dp))
                                if (ch != null) Pill("LIVE", Red, Color.White)
                            }
                            if (ch != null) {
                                Txt(
                                    ch.name, 11, Color.White, FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                                        .background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
