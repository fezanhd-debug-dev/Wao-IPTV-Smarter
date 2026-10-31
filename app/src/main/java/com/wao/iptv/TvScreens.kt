package com.wao.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvSection { LIVE, MOVIES, SERIES, FAVORITES, SETTINGS }

private val TvSlateMuted = Color(0xFF475569)

private fun isConfirm(e: KeyEvent): Boolean =
    e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER

// MENU / INFO / Yellow button se favorite on-off
private fun isFavKey(e: KeyEvent): Boolean =
    e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU ||
        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_INFO ||
        e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_PROG_YELLOW

private fun FocusRequester.go() { runCatching { requestFocus() } }

private fun Modifier.optReq(r: FocusRequester, on: Boolean): Modifier =
    if (on) this.focusRequester(r) else this

private fun Modifier.onConfirm(action: () -> Unit): Modifier = this.onKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && isConfirm(e) && e.nativeKeyEvent.repeatCount == 0) {
        action(); true
    } else false
}

private fun Modifier.keyNav(
    left: (() -> Unit)? = null,
    right: (() -> Unit)? = null,
    up: (() -> Unit)? = null,
    down: (() -> Unit)? = null
): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) {
        false
    } else {
        val h = when (e.nativeKeyEvent.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> left
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> right
            android.view.KeyEvent.KEYCODE_DPAD_UP -> up
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> down
            else -> null
        }
        if (h != null) { h(); true } else false
    }
}

@Composable
private fun TvSideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onRight: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(if (focused) Cyan else if (selected) Slate800 else Color.Transparent, shape)
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.6f), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(right = onRight)
            .onConfirm(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (focused) Slate950 else if (selected) Cyan else Slate400, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Txt(label, 14, if (focused) Slate950 else if (selected) Color.White else Slate400, FontWeight.Bold)
    }
}

@Composable
private fun TvCategoryChip(
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLeft: (() -> Unit)?,
    onDown: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .background(if (focused) Cyan else if (selected) Slate800 else Slate900, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Slate700, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(left = onLeft, down = onDown)
            .onConfirm(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Txt(name, 13, if (focused) Slate950 else if (selected) Color.White else Slate400, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TvSearchBar(query: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Slate900, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyan else Slate700, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Slate400, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Cyan),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Txt("Search channels, movies, series...", 14, Slate500)
                    inner()
                }
            }
        )
    }
}

@Composable
private fun TvChannelCard(
    ch: Channel,
    isFavorite: Boolean,
    modifier: Modifier,
    onLeft: (() -> Unit)?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .background(if (focused) Cyan.copy(alpha = 0.15f) else Slate900, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Slate800, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(left = onLeft)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && isFavKey(e)) { onToggleFavorite(); true }
                else if (!isConfirm(e)) false
                else when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount >= 8 && !longDone) { longDone = true; onToggleFavorite() }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longDone) onPlay()
                        longDone = false
                        true
                    }
                    else -> false
                }
            }
            .padding(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)).background(Slate950)) {
            if (ch.icon.isNotBlank()) {
                AsyncImage(model = ch.icon, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Filled.LiveTv, null, tint = TvSlateMuted, modifier = Modifier.size(32.dp).align(Alignment.Center))
            }
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                tint = if (isFavorite) Red else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp).align(Alignment.TopEnd).padding(2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Txt(ch.name, 12, if (focused) Color.White else Slate300, FontWeight.SemiBold, maxLines = 1)
        if (focused) Txt("OK: play  |  MENU: favorite", 9, Slate500, maxLines = 1)
    }
}

@Composable
private fun TvVodCard(
    v: VodItem,
    isFavorite: Boolean,
    modifier: Modifier,
    onLeft: (() -> Unit)?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    var longDone by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .background(if (focused) Cyan.copy(alpha = 0.15f) else Color.Transparent, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) Cyan else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(left = onLeft)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && isFavKey(e)) { onToggleFavorite(); true }
                else if (!isConfirm(e)) false
                else when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount >= 8 && !longDone) { longDone = true; onToggleFavorite() }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (!longDone) onPlay()
                        longDone = false
                        true
                    }
                    else -> false
                }
            }
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(10.dp)).background(Slate900)) {
            if (v.poster.isNotBlank()) {
                AsyncImage(model = v.poster, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(
                    if (v.isSeries) Icons.Filled.Theaters else Icons.Filled.Movie,
                    null, tint = TvSlateMuted, modifier = Modifier.size(32.dp).align(Alignment.Center)
                )
            }
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                tint = if (isFavorite) Red else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp).align(Alignment.TopEnd).padding(2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Txt(v.name, 11, if (focused) Color.White else Slate300, FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun TvRadioRow(
    label: String,
    sub: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLeft: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth()
            .background(Slate900, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyan else if (selected) Cyan.copy(alpha = 0.5f) else Slate800, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(left = onLeft)
            .onConfirm(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Txt(label, 14, Color.White, FontWeight.SemiBold)
            if (sub.isNotBlank()) Txt(sub, 11, Slate500, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .background(if (selected) Cyan else Color.Transparent)
                .border(2.dp, if (selected) Cyan else TvSlateMuted, CircleShape)
        )
    }
}

@Composable
private fun TvActionButton(label: String, color: Color, onLeft: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth()
            .background(if (focused) color else color.copy(alpha = 0.15f), shape)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .keyNav(left = onLeft)
            .onConfirm(onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(label, 13, if (focused) Slate950 else color, FontWeight.Bold)
    }
}

@Composable
private fun TvSettingsPanel(vm: AppViewModel, nav: NavController, entry: FocusRequester, onLeft: () -> Unit) {
    val lang = vm.settings.lang
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Txt("Settings", 22, Color.White, FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Txt("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", 12, Slate500)
        }
        item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "buffer_mode").uppercase(), 12, Cyan, FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                TvRadioRow(tr(lang, "buffer_low_title"), tr(lang, "buffer_low_desc"), vm.settings.bufferMode == 0,
                    Modifier.focusRequester(entry), onLeft) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 0))
                }
                Spacer(Modifier.height(8.dp))
                TvRadioRow(tr(lang, "buffer_balanced_title"), tr(lang, "buffer_balanced_desc"), vm.settings.bufferMode == 1,
                    Modifier, onLeft) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 1))
                }
                Spacer(Modifier.height(8.dp))
                TvRadioRow(tr(lang, "buffer_smooth_title"), tr(lang, "buffer_smooth_desc"), vm.settings.bufferMode == 2,
                    Modifier, onLeft) {
                    vm.updateSettings(vm.settings.copy(bufferMode = 2))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "select_language"), 12, Cyan, FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LanguagePicker(lang, onSelect = { code -> vm.updateSettings(vm.settings.copy(lang = code)) })
            }
        }
        item {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(16.dp)) {
                Txt(tr(lang, "app_update").uppercase(), 12, Cyan, FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                TvActionButton(
                    if (updateChecking) tr(lang, "checking_for_update") else tr(lang, "check_for_updates"),
                    Cyan, onLeft
                ) {
                    if (!updateChecking) {
                        updateChecking = true
                        updateResult = null
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { checkForUpdate(BuildConfig.VERSION_CODE) }
                            updateResult = r
                            updateChecking = false
                        }
                    }
                }
                when (val r = updateResult) {
                    is UpdateResult.Available -> {
                        Spacer(Modifier.height(8.dp))
                        Txt(tr(lang, "new_version_available").replace("{v}", r.info.versionName), 12, Emerald, FontWeight.Bold)
                    }
                    UpdateResult.UpToDate -> {
                        Spacer(Modifier.height(8.dp))
                        Txt(tr(lang, "up_to_date"), 12, Slate400)
                    }
                    is UpdateResult.Failed -> {
                        Spacer(Modifier.height(8.dp))
                        Txt(r.reason, 11, RedSoft)
                    }
                    null -> {}
                }
            }
        }
        item {
            TvActionButton(tr(lang, "logout"), RedSoft, onLeft) {
                vm.logout()
                nav.navigate("login") { popUpTo(0) }
            }
        }
    }
}

@Composable
fun TvHomeScreen(vm: AppViewModel, nav: NavController) {
    var section by remember { mutableStateOf(TvSection.LIVE) }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val sideEntry = remember { FocusRequester() }
    val chipEntry = remember { FocusRequester() }
    val gridEntry = remember { FocusRequester() }

    val cats = when (section) {
        TvSection.LIVE -> vm.liveCats
        TvSection.MOVIES -> vm.movieCats
        TvSection.SERIES -> vm.seriesCats
        else -> emptyList()
    }

    LaunchedEffect(Unit) {
        delay(350)
        sideEntry.go()
    }

    LaunchedEffect(section, cats) {
        if (cats.isNotEmpty() && (selectedCatId == null || cats.none { it.id == selectedCatId })) {
            selectedCatId = cats.first().id
        }
        if (section == TvSection.SERIES && selectedCatId != null) {
            vm.loadSeriesCategory(selectedCatId!!)
        }
    }

    val toSide: () -> Unit = { sideEntry.go() }
    val toGrid: () -> Unit = { gridEntry.go() }
    val enterContent: () -> Unit = {
        val hasChips = (section == TvSection.LIVE || section == TvSection.MOVIES || section == TvSection.SERIES) && cats.isNotEmpty()
        if (hasChips) chipEntry.go() else gridEntry.go()
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.width(220.dp).fillMaxHeight().background(Slate950).padding(14.dp)) {
            BigLogo()
            Spacer(Modifier.height(24.dp))
            TvSideButton(Icons.Filled.LiveTv, "Live TV", section == TvSection.LIVE,
                Modifier.optReq(sideEntry, section == TvSection.LIVE), enterContent) {
                section = TvSection.LIVE; selectedCatId = null; query = ""
            }
            Spacer(Modifier.height(8.dp))
            TvSideButton(Icons.Filled.Movie, "Movies", section == TvSection.MOVIES,
                Modifier.optReq(sideEntry, section == TvSection.MOVIES), enterContent) {
                section = TvSection.MOVIES; selectedCatId = null; query = ""
            }
            Spacer(Modifier.height(8.dp))
            TvSideButton(Icons.Filled.Theaters, "Series", section == TvSection.SERIES,
                Modifier.optReq(sideEntry, section == TvSection.SERIES), enterContent) {
                section = TvSection.SERIES; selectedCatId = null; query = ""
            }
            Spacer(Modifier.height(8.dp))
            TvSideButton(Icons.Filled.Favorite, "Favorites", section == TvSection.FAVORITES,
                Modifier.optReq(sideEntry, section == TvSection.FAVORITES), enterContent) {
                section = TvSection.FAVORITES; query = ""
            }
            Spacer(Modifier.height(8.dp))
            TvSideButton(Icons.Filled.Settings, "Settings", section == TvSection.SETTINGS,
                Modifier.optReq(sideEntry, section == TvSection.SETTINGS), enterContent) {
                section = TvSection.SETTINGS
            }
            Spacer(Modifier.weight(1f))
            Txt(tr(vm.settings.lang, "powered_by"), 10, TvSlateMuted, modifier = Modifier.fillMaxWidth())
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            val chCols = (maxWidth / 180.dp).toInt().coerceAtLeast(2)
            val vodCols = (maxWidth / 150.dp).toInt().coerceAtLeast(2)

            Column(Modifier.fillMaxSize()) {
                when (section) {
                    TvSection.SETTINGS -> TvSettingsPanel(vm, nav, gridEntry, toSide)

                    TvSection.FAVORITES -> {
                        val favChannels = vm.favoriteChannels()
                        val favMovies = vm.favoriteMovies()
                        val favSeries = vm.favoriteSeries()
                        val firstRow = when {
                            favChannels.isNotEmpty() -> 0
                            favMovies.isNotEmpty() -> 1
                            else -> 2
                        }
                        if (favChannels.isEmpty() && favMovies.isEmpty() && favSeries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Txt("Abhi koi favorite nahi hai. Kisi channel, movie ya series par remote ka MENU button dabayen.", 13, Slate500)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (favChannels.isNotEmpty()) item {
                                    Column {
                                        Txt("Live Channels", 14, Cyan, FontWeight.Bold)
                                        Spacer(Modifier.height(10.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            itemsIndexed(favChannels) { i, ch ->
                                                TvChannelCard(
                                                    ch, true,
                                                    Modifier.width(170.dp).optReq(gridEntry, firstRow == 0 && i == 0),
                                                    if (i == 0) toSide else null,
                                                    { vm.playChannel(ch); nav.navigate("player") }
                                                ) { vm.toggleFavoriteChannel(ch) }
                                            }
                                        }
                                    }
                                }
                                if (favMovies.isNotEmpty()) item {
                                    Column {
                                        Txt("Movies", 14, Cyan, FontWeight.Bold)
                                        Spacer(Modifier.height(10.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            itemsIndexed(favMovies) { i, m ->
                                                TvVodCard(
                                                    m, true,
                                                    Modifier.width(140.dp).optReq(gridEntry, firstRow == 1 && i == 0),
                                                    if (i == 0) toSide else null,
                                                    { vm.playMovie(m); nav.navigate("player") }
                                                ) { vm.toggleFavoriteVod(m) }
                                            }
                                        }
                                    }
                                }
                                if (favSeries.isNotEmpty()) item {
                                    Column {
                                        Txt("Series", 14, Cyan, FontWeight.Bold)
                                        Spacer(Modifier.height(10.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            itemsIndexed(favSeries) { i, s ->
                                                TvVodCard(
                                                    s, true,
                                                    Modifier.width(140.dp).optReq(gridEntry, firstRow == 2 && i == 0),
                                                    if (i == 0) toSide else null,
                                                    { vm.openSeries(s); nav.navigate("tv_series_detail") }
                                                ) { vm.toggleFavoriteVod(s) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        TvSearchBar(query) { query = it }
                        Spacer(Modifier.height(12.dp))
                        if (cats.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(cats) { i, c ->
                                    TvCategoryChip(
                                        c.name, c.id == selectedCatId,
                                        Modifier.optReq(chipEntry, i == 0),
                                        if (i == 0) toSide else null,
                                        toGrid
                                    ) { selectedCatId = c.id }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        when (section) {
                            TvSection.LIVE -> {
                                val list = vm.visibleChannels()
                                    .filter { selectedCatId == null || it.categoryId == selectedCatId }
                                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(chCols),
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(list) { i, ch ->
                                        TvChannelCard(
                                            ch, vm.isFavorite("live:${ch.id}"),
                                            Modifier.fillMaxWidth().optReq(gridEntry, i == 0),
                                            if (i % chCols == 0) toSide else null,
                                            { vm.playChannel(ch); nav.navigate("player") }
                                        ) { vm.toggleFavoriteChannel(ch) }
                                    }
                                }
                            }
                            TvSection.MOVIES -> {
                                val list = vm.movies
                                    .filter { selectedCatId == null || it.categoryId == selectedCatId }
                                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(vodCols),
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(list) { i, m ->
                                        TvVodCard(
                                            m, vm.isFavorite("movie:${m.id}"),
                                            Modifier.fillMaxWidth().optReq(gridEntry, i == 0),
                                            if (i % vodCols == 0) toSide else null,
                                            { vm.playMovie(m); nav.navigate("player") }
                                        ) { vm.toggleFavoriteVod(m) }
                                    }
                                }
                            }
                            TvSection.SERIES -> {
                                val baseList = selectedCatId?.let { vm.seriesByCat[it] } ?: emptyList()
                                val list = baseList.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                                if (vm.seriesCatLoading == selectedCatId) {
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Cyan)
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(vodCols),
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(list) { i, s ->
                                            TvVodCard(
                                                s, vm.isFavorite("series:${s.id}"),
                                                Modifier.fillMaxWidth().optReq(gridEntry, i == 0),
                                                if (i % vodCols == 0) toSide else null,
                                                { vm.openSeries(s); nav.navigate("tv_series_detail") }
                                            ) { vm.toggleFavoriteVod(s) }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvSeriesDetailScreen(vm: AppViewModel, nav: NavController) {
    val series = vm.selectedSeries ?: run { nav.popBackStack(); return }
    val backReq = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(300)
        backReq.go()
    }
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var backFocused by remember { mutableStateOf(false) }
            Box(
                Modifier.size(44.dp).background(if (backFocused) Cyan else Slate900, CircleShape)
                    .onFocusChanged { backFocused = it.isFocused }
                    .focusRequester(backReq)
                    .focusable()
                    .onConfirm { nav.popBackStack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.ArrowBack, null, tint = if (backFocused) Slate950 else Color.White) }
            Spacer(Modifier.width(16.dp))
            Txt(series.name, 22, Color.White, FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
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
                            .onFocusChanged { f = it.isFocused }
                            .focusable()
                            .onConfirm { vm.playEpisode(series, ep); nav.navigate("player") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayCircle, null, tint = if (f) Slate950 else Cyan, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Txt("S${ep.season}:E${ep.number} - ${ep.title}", 13, if (f) Slate950 else Color.White, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TvQuadViewScreen(vm: AppViewModel, nav: NavController) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Txt("Quad View", 16, Slate400)
    }
}
