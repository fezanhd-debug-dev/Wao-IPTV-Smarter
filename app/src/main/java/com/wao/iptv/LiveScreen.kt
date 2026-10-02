package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
private fun ChannelRow(
    vm: AppViewModel,
    ch: Channel,
    active: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    LaunchedEffect(ch.id) { vm.loadEpg(ch) }
    val now = System.currentTimeMillis() / 1000
    val list = vm.epg[ch.id]
    val cur = list?.firstOrNull { now in it.start..it.end } ?: list?.firstOrNull()
    val frac = if (cur != null && cur.end > cur.start)
        ((now - cur.start).toFloat() / (cur.end - cur.start)).coerceIn(0f, 1f) else 0f
    val fav = vm.isFavorite("live:${ch.id}")

    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .then(if (active) Modifier.background(NeonCyan.copy(alpha = 0.08f), shape).border(1.dp, NeonCyan, shape) else Modifier)
            .tvClick(shape, onClick = onSelect)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (active) Cyan.copy(alpha = 0.2f) else Slate800, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Txt(ch.num.toString(), 12, if (active) Cyan else Slate300, FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(ch.name, 14, Color.White, FontWeight.Bold, maxLines = 1)
                Txt(cur?.title ?: "Live", 11, if (active) Cyan else Slate400, maxLines = 1)
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .width(144.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Slate800)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(if (active) Cyan else Slate500)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clickable { vm.toggleFavoriteChannel(ch) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (fav) RedSoft else Slate500,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .background(if (active) Cyan else Slate800, CircleShape)
                    .tvClick(CircleShape, onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = if (active) Slate950 else Slate300, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LiveScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val all = remember(vm.channels, vm.lockActive) { vm.visibleChannels() }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var pinFor by remember { mutableStateOf<String?>(null) }

    val locked = remember(vm.liveCats, vm.lockActive) {
        if (vm.lockActive) vm.adultIds(vm.liveCats) else emptySet()
    }
    val favCount = remember(vm.favorites) { vm.favorites.count { it.kind == "live" } }
    val baseList = if (showFavorites) vm.favoriteChannels() else all
    val filtered = remember(baseList, query, cat, showFavorites) {
        baseList.filter { (cat.isEmpty() || it.categoryId == cat) && (query.isBlank() || it.name.contains(query, true)) }
    }
    val active = selected ?: filtered.firstOrNull()

    val player = remember { buildPlayer(ctx, 0).also { it.volume = 0f } }
    val fb = remember { LiveFallback(player) }
    DisposableEffect(Unit) {
        player.addListener(fb)
        onDispose {
            player.removeListener(fb)
            player.release()
        }
    }
    LaunchedEffect(active?.id) {
        val s = vm.session
        if (active != null && s != null) {
            delay(700)
            fb.play(liveUrl(s, active))
        }
    }

    fun openPlayer(ch: Channel) {
        player.stop()
        vm.playChannel(ch)
        nav.navigate("player")
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SearchField(query, { query = it }, "Search ${fmtInt(all.size)}+ channels...")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavoritesChip(showFavorites, favCount) { showFavorites = !showFavorites; selected = null }
                Spacer(Modifier.width(8.dp))
                if (!showFavorites) {
                    CategoryChips(vm.liveCats, cat, locked) { id ->
                        if (id in locked) pinFor = id else { cat = id; selected = null }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            val previewShape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(previewShape)
                    .background(Color.Black)
                    .border(1.dp, Cyan.copy(alpha = 0.3f), previewShape)
            ) {
                if (active != null) {
                    VideoSurface(player, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, Modifier.fillMaxSize())
                    Pill("LIVE", Red, Color.White, Modifier.align(Alignment.TopStart).padding(8.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        FavButton(vm.isFavorite("live:${active.id}")) {
                            vm.toggleFavoriteChannel(active)
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Txt("${active.num} ${active.name}", 12, Color.White, FontWeight.Bold, Modifier.weight(1f), maxLines = 1)
                        val btn = RoundedCornerShape(8.dp)
                        Row(
                            Modifier.background(Cyan, btn).tvClick(btn) { openPlayer(active) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Fullscreen, null, tint = Slate950, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Txt("Fullscreen", 10, Slate950, FontWeight.Bold)
                        }
                    }
                } else {
                    Txt(
                        if (showFavorites) "Abhi koi favorite channel nahi hai" else "Is category me koi channel nahi mila",
                        12, Slate500, modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { ch ->
                ChannelRow(vm, ch, ch.id == active?.id, { selected = ch }) { openPlayer(ch) }
            }
        }
        BottomNav("live", vm, nav)
    }

    pinFor?.let { catId ->
        PinDialog("Parental PIN darj karein", { pinFor = null }) { p ->
            if (vm.checkPin(p)) {
                vm.unlocked = true
                cat = catId
                selected = null
                pinFor = null
                null
            } else "Ghalat PIN"
        }
    }
}
