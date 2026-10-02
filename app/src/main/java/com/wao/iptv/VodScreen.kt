package com.wao.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage

@Composable
private fun PosterCard(vm: AppViewModel, item: VodItem, onClick: () -> Unit) {
    val key = (if (item.isSeries) "series:" else "movie:") + item.id
    val fav = vm.isFavorite(key)
    Column(Modifier.glass(RoundedCornerShape(16.dp)).tvClick(RoundedCornerShape(16.dp), onClick = onClick).padding(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate900)
        ) {
            AsyncImage(model = item.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.rating.isNotBlank()) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xB3000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Star, null, tint = Amber, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(2.dp))
                    Txt(item.rating, 9, Color.White, FontWeight.Bold)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                FavButton(fav, size = 26.dp) { vm.toggleFavoriteVod(item) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Txt(item.name, 12, Color.White, FontWeight.Bold, maxLines = 1)
        Txt(
            listOfNotNull(item.year.ifBlank { null }, if (item.isSeries) "Series" else "Movie").joinToString(" • "),
            10, Slate400, maxLines = 1
        )
    }
}

@Composable
private fun SeriesDetail(vm: AppViewModel, series: VodItem, nav: NavController, onBack: () -> Unit) {
    val fav = vm.isFavorite("series:${series.id}")
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).glass(RoundedCornerShape(12.dp)).tvClick(RoundedCornerShape(12.dp), onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(12.dp))
                Txt(series.name, 16, Color.White, FontWeight.Bold, maxLines = 1)
            }
            FavButton(fav) { vm.toggleFavoriteVod(series) }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (series.plot.isNotBlank()) {
                item { Txt(series.plot, 12, Slate300) }
            }
            val bySeason = vm.episodes.groupBy { it.season }
            bySeason.toSortedMap().forEach { (season, eps) ->
                item {
                    Column {
                        Txt("Season $season", 13, Cyan, FontWeight.Bold, Modifier.padding(bottom = 8.dp))
                        eps.forEach { ep ->
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .glass(shape)
                                    .tvClick(shape) { vm.playEpisode(series, ep); nav.navigate("player") }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Cyan, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt("Episode ${ep.number}", 12, Color.White, FontWeight.Bold)
                                    Txt(ep.title, 11, Slate400, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (vm.episodesLoading) item { Txt("Episodes load ho rahe hain...", 12, Slate500) }
            else if (vm.episodes.isEmpty()) item { Txt("Is series ke episodes nahi milay", 12, Slate500) }
        }
    }
}

@Composable
fun VodScreen(vm: AppViewModel, nav: NavController) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var showFavorites by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<String?>(null) }

    if (vm.selectedSeries != null) {
        SeriesDetail(vm, vm.selectedSeries!!, nav) { vm.selectedSeries = null }
        return
    }

    val isSeries = vm.vodTab == 1
    val items = if (isSeries) vm.seriesList else vm.movies
    val cats = if (isSeries) vm.seriesCats else vm.movieCats
    val locked = remember(cats, vm.lockActive) { if (vm.lockActive) vm.adultIds(cats) else emptySet() }
    val favCount = remember(vm.favorites, isSeries) {
        vm.favorites.count { it.kind == (if (isSeries) "series" else "movie") }
    }
    val baseList = if (showFavorites) (if (isSeries) vm.favoriteSeries() else vm.favoriteMovies()) else items
    val filtered = remember(baseList, query, cat, showFavorites) {
        baseList.filter { (cat.isEmpty() || it.categoryId == cat) && (query.isBlank() || it.name.contains(query, true)) }
    }
    val hero = if (showFavorites) null else filtered.firstOrNull()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt("VOD Entertainment", 18, Color.White, FontWeight.Black)
                Pill("${fmtCount(vm.movies.size + vm.seriesList.size)} Titles", Color(0x33C084FC), Purple)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(Slate900, RoundedCornerShape(12.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Movies (${fmtCount(vm.movies.size)})" to 0, "TV Series (${fmtCount(vm.seriesList.size)})" to 1).forEach { (label, idx) ->
                    val sel = vm.vodTab == idx
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.weight(1f).background(if (sel) Cyan else Color.Transparent, shape)
                            .tvClick(shape) { vm.vodTab = idx; cat = ""; query = ""; showFavorites = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Txt(label, 11, if (sel) Slate950 else Slate400, FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "Search titles...")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavoritesChip(showFavorites, favCount) { showFavorites = !showFavorites }
                Spacer(Modifier.width(8.dp))
                if (!showFavorites) {
                    CategoryChips(cats, cat, locked) { id -> if (id in locked) pinFor = id else cat = id }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hero != null) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    val heroShape = RoundedCornerShape(20.dp)
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(heroShape).background(Slate900)) {
                        AsyncImage(model = hero.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            if (hero.rating.isNotBlank()) Pill("⭐ ${hero.rating}", Cyan, Slate950)
                            Txt(hero.name, 18, Color.White, FontWeight.Bold, Modifier.padding(top = 4.dp), maxLines = 2)
                            val btn = RoundedCornerShape(10.dp)
                            Row(
                                Modifier.padding(top = 10.dp).background(Cyan, btn)
                                    .tvClick(btn) {
                                        if (hero.isSeries) vm.openSeries(hero) else { vm.playMovie(hero); nav.navigate("player") }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Slate950, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Txt(if (hero.isSeries) "View Episodes" else "Watch Movie", 11, Slate950, FontWeight.Bold)
                            }
                        }
                        Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                            FavButton(vm.isFavorite((if (hero.isSeries) "series:" else "movie:") + hero.id)) {
                                vm.toggleFavoriteVod(hero)
                            }
                        }
                    }
                }
            }
            items(filtered, key = { it.id }) { v ->
                PosterCard(vm, v) { if (v.isSeries) vm.openSeries(v) else { vm.playMovie(v); nav.navigate("player") } }
            }
            if (filtered.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Txt(if (showFavorites) "Abhi koi favorite nahi hai" else "Kuch nahi mila", 12, Slate500)
                }
            }
        }
        BottomNav("vod", vm, nav)
    }

    pinFor?.let { catId ->
        PinDialog("Parental PIN darj karein", { pinFor = null }) { p ->
            if (vm.checkPin(p)) { vm.unlocked = true; cat = catId; pinFor = null; null } else "Ghalat PIN"
        }
    }
}
