package com.wao.iptv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage

private enum class TvSection { LIVE, MOVIES, SERIES, SETTINGS }

@Composable
private fun TvSideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> Cyan
                    selected -> Slate800
                    else -> Color.Transparent
                },
                shape
            )
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.6f), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) { onClick(); true } else false
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (focused) Slate950 else if (selected) Cyan else Slate400, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Txt(label, 14, if (focused) Slate950 else if (selected) Color.White else Slate400, FontWeight.Bold)
    }
}

@Composable
private fun TvCategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .background(if (focused) Cyan else if (selected) Slate800 else Slate900, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Slate700, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) { onClick(); true } else false
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Txt(name, 13, if (focused) Slate950 else if (selected) Color.White else Slate400, FontWeight.Bold)
    }
}

@Composable
private fun TvChannelCard(ch: Channel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(170.dp)
            .background(if (focused) Cyan.copy(alpha = 0.15f) else Slate900, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Slate800, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) { onClick(); true } else false
            }
            .padding(12.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp))
                .background(Slate950),
            contentAlignment = Alignment.Center
        ) {
            if (ch.icon.isNotBlank()) {
                AsyncImage(model = ch.icon, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = Slate600, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(ch.name, 12, if (focused) Color.White else Slate300, FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TvVodCard(v: VodItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(140.dp)
            .background(if (focused) Cyan.copy(alpha = 0.15f) else Color.Transparent, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) Cyan else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown &&
                    (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) { onClick(); true } else false
            }
            .padding(6.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(10.dp)).background(Slate900)
        ) {
            if (v.poster.isNotBlank()) {
                AsyncImage(model = v.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(
                    if (v.isSeries) Icons.Filled.Theaters else Icons.Filled.Movie,
                    null, tint = Slate600, modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(v.name, 11, if (focused) Color.White else Slate300, FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun TvHomeScreen(vm: AppViewModel, nav: NavController) {
    var section by remember { mutableStateOf(TvSection.LIVE) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val cats = when (section) {
        TvSection.LIVE -> vm.liveCats
        TvSection.MOVIES -> vm.movieCats
        TvSection.SERIES -> vm.seriesCats
        TvSection.SETTINGS -> emptyList()
    }

    LaunchedEffect(section, cats) {
        if (cats.isNotEmpty() && (selectedCatId == null || cats.none { it.id == selectedCatId })) {
            selectedCatId = cats.first().id
        }
        if (section == TvSection.SERIES && selectedCatId != null) {
            vm.loadSeriesCategory(selectedCatId!!)
        }
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        // Left sidebar — permanent, D-pad navigable
        Column(
            Modifier.width(230.dp).fillMaxHeight().background(Slate950).padding(16.dp)
        ) {
            BigLogo()
            Spacer(Modifier.height(28.dp))
            TvSideButton(Icons.Filled.LiveTv, "Live TV", section == TvSection.LIVE) { section = TvSection.LIVE; selectedCatId = null }
            Spacer(Modifier.height(10.dp))
            TvSideButton(Icons.Filled.Movie, "Movies", section == TvSection.MOVIES) { section = TvSection.MOVIES; selectedCatId = null }
            Spacer(Modifier.height(10.dp))
            TvSideButton(Icons.Filled.Theaters, "Series", section == TvSection.SERIES) { section = TvSection.SERIES; selectedCatId = null }
            Spacer(Modifier.height(10.dp))
            TvSideButton(Icons.Filled.Settings, "Settings", section == TvSection.SETTINGS) {
                nav.navigate("settings")
            }
            Spacer(Modifier.weight(1f))
            Txt(tr(vm.settings.lang, "powered_by"), 10, Slate600, modifier = Modifier.fillMaxWidth())
        }

        // Main content
        Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp).focusRequester(focusRequester).focusable()) {
            if (cats.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cats) { c ->
                        TvCategoryChip(c.name, c.id == selectedCatId) { selectedCatId = c.id }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            when (section) {
                TvSection.LIVE -> {
                    val list = vm.visibleChannels().filter { selectedCatId == null || it.categoryId == selectedCatId }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(list) { ch ->
                            TvChannelCard(ch) {
                                vm.playChannel(ch)
                                nav.navigate("player")
                            }
                        }
                    }
                }
                TvSection.MOVIES -> {
                    val list = vm.movies.filter { selectedCatId == null || it.categoryId == selectedCatId }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list) { m ->
                            TvVodCard(m) {
                                vm.playMovie(m)
                                nav.navigate("player")
                            }
                        }
                    }
                }
                TvSection.SERIES -> {
                    val list = selectedCatId?.let { vm.seriesByCat[it] } ?: emptyList()
                    if (vm.seriesCatLoading == selectedCatId) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cyan)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list) { s ->
                                TvVodCard(s) {
                                    vm.openSeries(s)
                                    nav.navigate("tv_series_detail")
                                }
                            }
                        }
                    }
                }
                TvSection.SETTINGS -> {}
            }
        }
    }
}

@Composable
fun TvSeriesDetailScreen(vm: AppViewModel, nav: NavController) {
    val series = vm.selectedSeries ?: run { nav.popBackStack(); return }
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var backFocused by remember { mutableStateOf(false) }
            Box(
                Modifier.size(44.dp).background(if (backFocused) Cyan else Slate900, CircleShape)
                    .onFocusChanged { backFocused = it.isFocused }.focusable()
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                            nav.popBackStack(); true
                        } else false
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ArrowBack, null, tint = if (backFocused) Slate950 else Color.White) }
            Spacer(Modifier.width(16.dp))
            Txt(series.name, 22, Color.White, FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        if (vm.episodesLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.episodes) { ep ->
                    var f by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (f) Cyan else Slate900, shape)
                            .border(if (f) 2.dp else 1.dp, if (f) Color.White else Slate800, shape)
                            .onFocusChanged { f = it.isFocused }.focusable()
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                                    vm.playEpisode(series, ep); nav.navigate("player"); true
                                } else false
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayCircle, null, tint = if (f) Slate950 else Cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Txt("S${ep.season}:E${ep.number} — ${ep.title}", 13, if (f) Slate950 else Color.White, FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvQuadViewScreen(vm: AppViewModel, nav: NavController) {
    // Placeholder retained for nav-graph compatibility
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Txt("Quad View", 16, Slate400)
    }
}
