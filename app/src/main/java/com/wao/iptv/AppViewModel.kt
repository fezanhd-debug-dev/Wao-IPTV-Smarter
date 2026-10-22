package com.wao.iptv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    init { currentLang = store.loadSettings().lang }

    val myDeviceId: String by lazy { getDeviceId(getApplication()) }

    var session by mutableStateOf<Session?>(store.loadSession())
    var loading by mutableStateOf(false)
    var loadingStep by mutableStateOf(0)
    var switching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var account by mutableStateOf(AccountInfo())

    var liveCats by mutableStateOf<List<Category>>(emptyList())
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var movieCats by mutableStateOf<List<Category>>(emptyList())
    var movies by mutableStateOf<List<VodItem>>(emptyList())
    var seriesCats by mutableStateOf<List<Category>>(emptyList())
    var seriesList by mutableStateOf<List<VodItem>>(emptyList())

    val seriesByCat = mutableStateMapOf<String, List<VodItem>>()
    var seriesCatLoading by mutableStateOf<String?>(null)

    var settings by mutableStateOf(store.loadSettings())
    var history by mutableStateOf(store.loadHistory())
    var nowPlaying by mutableStateOf<PlayItem?>(null)

    var savedAccounts by mutableStateOf(store.loadAccounts())
    var favorites by mutableStateOf(store.loadFavorites())

    var selectedSeries by mutableStateOf<VodItem?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var episodesLoading by mutableStateOf(false)

    var vodTab by mutableStateOf(0)
    var unlocked by mutableStateOf(false)
    var homeRoute: String = "home"

    var testingConnection by mutableStateOf(false)
    var connectionTestResult by mutableStateOf<ConnectionTestResult?>(null)

    var newContentMessage by mutableStateOf<String?>(null)
    var expiryWarningDismissed by mutableStateOf(false)

    val epg = mutableStateMapOf<String, List<EpgEntry>>()
    private val epgGate = Semaphore(4)

    val lastServer: String get() = store.lastServer()
    val lastUser: String get() = store.lastUser()

    val lockActive: Boolean
        get() = settings.pinEnabled && settings.pin.length == 4 && !unlocked

    private val adultRegex = Regex("adult|xxx|18\\s*\\+|porn|erotic", RegexOption.IGNORE_CASE)

    fun adultIds(cats: List<Category>): Set<String> =
        cats.filter { adultRegex.containsMatchIn(it.name) }.map { it.id }.toSet()

    fun visibleChannels(): List<Channel> {
        if (!lockActive) return channels
        val ids = adultIds(liveCats)
        return channels.filter { it.categoryId !in ids }
    }

    fun checkPin(p: String): Boolean = p == settings.pin

    fun updateSettings(s: AppSettings) {
        settings = s
        currentLang = s.lang
        store.saveSettings(s)
    }

    fun isFavorite(key: String): Boolean = favorites.any { it.key == key }

    fun toggleFavoriteChannel(ch: Channel) {
        val key = "live:${ch.id}"
        favorites = if (favorites.any { it.key == key }) favorites.filter { it.key != key }
        else favorites + FavoriteItem(key, "live", ch.name, ch.icon)
        store.saveFavorites(favorites)
    }

    fun toggleFavoriteVod(v: VodItem) {
        val key = (if (v.isSeries) "series:" else "movie:") + v.id
        favorites = if (favorites.any { it.key == key }) favorites.filter { it.key != key }
        else favorites + FavoriteItem(key, if (v.isSeries) "series" else "movie", v.name, v.poster)
        store.saveFavorites(favorites)
    }

    fun favoriteChannels(): List<Channel> {
        val keys = favorites.filter { it.kind == "live" }.map { it.key }.toSet()
        return visibleChannels().filter { "live:${it.id}" in keys }
    }

    fun favoriteMovies(): List<VodItem> {
        val keys = favorites.filter { it.kind == "movie" }.map { it.key }.toSet()
        return movies.filter { "movie:${it.id}" in keys }
    }

    fun favoriteSeries(): List<VodItem> {
        val favs = favorites.filter { it.kind == "series" }
        return favs.map { f ->
            val id = f.key.removePrefix("series:")
            seriesList.firstOrNull { it.id == id } ?: VodItem(
                id = id, name = f.title, poster = f.poster, rating = "", year = "",
                categoryId = "", ext = "", isSeries = true
            )
        }
    }

    private fun accountId(s: Session): String =
        if (s.type == "xtream") "${s.server}|${s.username}" else "m3u|${s.m3uUrl.hashCode()}"

    fun currentAccountId(): String? = session?.let { accountId(it) }

    private fun maybeSaveAccount(s: Session) {
        val id = accountId(s)
        if (savedAccounts.any { it.id == id }) return
        val label = if (s.type == "xtream") {
            val host = s.server.removePrefix("http://").removePrefix("https://")
            "${s.username} • $host"
        } else "M3U Playlist"
        savedAccounts = savedAccounts + SavedAccount(id, label, s)
        store.saveAccounts(savedAccounts)
    }

    fun removeAccount(id: String) {
        savedAccounts = savedAccounts.filter { it.id != id }
        store.saveAccounts(savedAccounts)
    }

    fun switchAccount(acc: SavedAccount, onFail: () -> Unit = {}, onSuccess: () -> Unit) {
        if (switching || loading) return
        switching = true
        expiryWarningDismissed = false
        connect(acc.session, onFail = { switching = false; onFail() }) {
            switching = false
            onSuccess()
        }
    }

    fun daysUntilExpiry(): Int? {
        val exp = account.expDate
        if (exp <= 0) return null
        val now = System.currentTimeMillis() / 1000
        val diff = exp - now
        if (diff < 0) return -1
        return (diff / 86400).toInt()
    }

    fun testConnection() {
        val s = session ?: return
        if (s.type != "xtream" || testingConnection) return
        testingConnection = true
        connectionTestResult = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { XtreamApi(s.server, s.username, s.password).testConnection() }
            connectionTestResult = r
            testingConnection = false
        }
    }

    private fun checkNewContent(accId: String, snap: ContentSnapshot) {
        val prev = store.loadSnapshot(accId)
        if (prev != null) {
            val newLive = (snap.liveCount - prev.liveCount).coerceAtLeast(0)
            val newMovies = (snap.movieCount - prev.movieCount).coerceAtLeast(0)
            val newSeries = (snap.seriesCount - prev.seriesCount).coerceAtLeast(0)
            val parts = mutableListOf<String>()
            if (newLive > 0) parts.add(tr(currentLang, "new_item_live").replace("{n}", newLive.toString()))
            if (newMovies > 0) parts.add(tr(currentLang, "new_item_movies").replace("{n}", newMovies.toString()))
            if (newSeries > 0) parts.add(tr(currentLang, "new_item_series").replace("{n}", newSeries.toString()))
            if (parts.isNotEmpty()) {
                newContentMessage = parts.joinToString(", ") + " " + tr(currentLang, "new_content_suffix")
            }
        }
        store.saveSnapshot(accId, snap)
    }

    fun dismissNewContentMessage() { newContentMessage = null }
    fun dismissExpiryWarning() { expiryWarningDismissed = true }

    private fun applyCache(cached: CachedContent) {
        liveCats = cached.liveCats
        channels = cached.channels
        movieCats = cached.movieCats
        movies = cached.movies
        seriesCats = cached.seriesCats
        seriesList = cached.seriesList
        account = cached.account
        seriesByCat.clear()
        cached.seriesList.groupBy { it.categoryId }.forEach { (catId, items) ->
            if (catId.isNotBlank()) seriesByCat[catId] = items
        }
    }

    private suspend fun saveCacheSafe(accId: String, content: CachedContent) {
        withContext(Dispatchers.IO) { store.saveCache(accId, content) }
    }

    private suspend fun loadCacheSafe(accId: String): CachedContent? =
        withContext(Dispatchers.IO) { store.loadCache(accId) }

    // Sirf asal successful authentication ke baad call hota hai (cache-hit path me
    // bhi authenticate() pehle chal chuka hota hai) — is liye "Today Successful
    // Login" ka number hamesha sahi hota hai, app sirf kholne se nahi badhta.
    private fun logSuccessfulLogin(s: Session) {
        val label = if (s.type == "xtream") s.username.ifBlank { "Xtream" } else "M3U"
        viewModelScope.launch(Dispatchers.IO) { logLoginOk(myDeviceId, label) }
    }

    fun loadSeriesCategory(categoryId: String) {
        if (categoryId.isBlank()) return
        if (seriesByCat.containsKey(categoryId)) return
        val s = session ?: return
        if (s.type != "xtream") return
        if (seriesCatLoading == categoryId) return
        seriesCatLoading = categoryId
        viewModelScope.launch {
            try {
                val api = XtreamApi(s.server, s.username, s.password)
                val list = withContext(Dispatchers.IO) {
                    runCatching { api.seriesByCategory(categoryId) }.getOrDefault(emptyList())
                }
                seriesByCat[categoryId] = list
                val existingIds = seriesList.map { it.id }.toSet()
                seriesList = seriesList + list.filter { it.id !in existingIds }
                currentAccountId()?.let { accId ->
                    saveCacheSafe(accId, CachedContent(liveCats, channels, movieCats, movies, seriesCats, seriesList, account))
                }
            } finally {
                if (seriesCatLoading == categoryId) seriesCatLoading = null
            }
        }
    }

    fun connect(s: Session, onFail: () -> Unit = {}, onSuccess: () -> Unit) {
        if (loading) return
        loading = true
        error = null
        loadingStep = 0
        newContentMessage = null
        viewModelScope.launch {
            try {
                val accId = accountId(s)

                if (s.type == "m3u") {
                    val cached = loadCacheSafe(accId)
                    if (cached != null && (cached.channels.isNotEmpty() || cached.movies.isNotEmpty())) {
                        applyCache(cached)
                        session = s
                        store.saveSession(s)
                        maybeSaveAccount(s)
                        expiryWarningDismissed = false
                        loading = false
                        logSuccessfulLogin(s)
                        onSuccess()
                        refreshM3uInBackground(s, accId)
                        return@launch
                    }

                    val text = withContext(Dispatchers.IO) { httpGet(s.m3uUrl) }
                    if (!text.contains("#EXTINF")) throw RuntimeException(tr(currentLang, "err_invalid_m3u"))
                    loadingStep = 1
                    val r = withContext(Dispatchers.Default) { parseM3u(text) }
                    liveCats = r.liveCats
                    channels = r.live
                    loadingStep = 2
                    movieCats = r.movieCats
                    movies = r.movies
                    loadingStep = 3
                    seriesCats = emptyList()
                    seriesList = emptyList()
                    seriesByCat.clear()
                    account = AccountInfo("M3U Playlist", "Active")
                    saveCacheSafe(accId, CachedContent(liveCats, channels, movieCats, movies, seriesCats, seriesList, account))
                } else {
                    val api = XtreamApi(s.server, s.username, s.password)
                    val acc = withContext(Dispatchers.IO) { api.authenticate() }

                    val cached = loadCacheSafe(accId)
                    if (cached != null && (cached.channels.isNotEmpty() || cached.movies.isNotEmpty() || cached.seriesCats.isNotEmpty())) {
                        applyCache(cached)
                        account = acc
                        session = s
                        store.saveSession(s)
                        store.saveLast(s.server, s.username)
                        maybeSaveAccount(s)
                        expiryWarningDismissed = false
                        loading = false
                        logSuccessfulLogin(s)
                        onSuccess()
                        refreshXtreamInBackground(s, api, accId)
                        return@launch
                    }

                    loadingStep = 1
                    var liveErr: Exception? = null
                    val live = withContext(Dispatchers.IO) {
                        runCatching { api.liveChannels() }.onFailure { e -> if (e is Exception) liveErr = e }.getOrDefault(emptyList())
                    }
                    liveCats = withContext(Dispatchers.IO) { runCatching { api.liveCategories() }.getOrDefault(emptyList()) }
                    channels = live

                    loadingStep = 2
                    movies = withContext(Dispatchers.IO) { runCatching { api.movies() }.getOrDefault(emptyList()) }
                    movieCats = withContext(Dispatchers.IO) { runCatching { api.movieCategories() }.getOrDefault(emptyList()) }

                    loadingStep = 3
                    seriesCats = withContext(Dispatchers.IO) { runCatching { api.seriesCategories() }.getOrDefault(emptyList()) }
                    seriesList = emptyList()
                    seriesByCat.clear()

                    if (channels.isEmpty() && movies.isEmpty() && seriesCats.isEmpty()) {
                        throw (liveErr ?: RuntimeException(tr(currentLang, "err_no_content")))
                    }
                    account = acc
                    saveCacheSafe(accId, CachedContent(liveCats, channels, movieCats, movies, seriesCats, seriesList, account))
                }

                session = s
                store.saveSession(s)
                if (s.type == "xtream") store.saveLast(s.server, s.username)
                maybeSaveAccount(s)
                expiryWarningDismissed = false
                checkNewContent(accId, ContentSnapshot(channels.size, movies.size, seriesCats.size))
                logSuccessfulLogin(s)
                onSuccess()
            } catch (e: Exception) {
                error = friendly(e)
                onFail()
            } finally {
                loading = false
                loadingStep = 0
            }
        }
    }

    private fun refreshXtreamInBackground(s: Session, api: XtreamApi, accId: String) {
        viewModelScope.launch {
            try {
                val live = withContext(Dispatchers.IO) { runCatching { api.liveChannels() }.getOrDefault(emptyList()) }
                val liveC = withContext(Dispatchers.IO) { runCatching { api.liveCategories() }.getOrDefault(emptyList()) }
                val mov = withContext(Dispatchers.IO) { runCatching { api.movies() }.getOrDefault(emptyList()) }
                val movC = withContext(Dispatchers.IO) { runCatching { api.movieCategories() }.getOrDefault(emptyList()) }
                val serC = withContext(Dispatchers.IO) { runCatching { api.seriesCategories() }.getOrDefault(emptyList()) }
                if (live.isNotEmpty() || mov.isNotEmpty() || serC.isNotEmpty()) {
                    liveCats = liveC; channels = live
                    movieCats = movC; movies = mov
                    seriesCats = serC
                    saveCacheSafe(accId, CachedContent(liveCats, channels, movieCats, movies, seriesCats, seriesList, account))
                    checkNewContent(accId, ContentSnapshot(channels.size, movies.size, serC.size))
                }
            } catch (e: Exception) { }
        }
    }

    private fun refreshM3uInBackground(s: Session, accId: String) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { httpGet(s.m3uUrl) }
                if (text.contains("#EXTINF")) {
                    val r = withContext(Dispatchers.Default) { parseM3u(text) }
                    liveCats = r.liveCats; channels = r.live
                    movieCats = r.movieCats; movies = r.movies
                    saveCacheSafe(accId, CachedContent(liveCats, channels, movieCats, movies, seriesCats, seriesList, account))
                    checkNewContent(accId, ContentSnapshot(channels.size, movies.size, seriesCats.size))
                }
            } catch (e: Exception) { }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> tr(currentLang, "err_no_server")
        is SocketTimeoutException -> tr(currentLang, "err_timeout")
        is javax.net.ssl.SSLException -> tr(currentLang, "err_ssl")
        is IllegalArgumentException -> tr(currentLang, "err_bad_url")
        else -> e.message?.takeIf { it.isNotBlank() } ?: tr(currentLang, "err_connect_failed")
    }

    fun logout() {
        session = null
        store.saveSession(null)
        liveCats = emptyList(); channels = emptyList()
        movieCats = emptyList(); movies = emptyList()
        seriesCats = emptyList(); seriesList = emptyList()
        seriesByCat.clear()
        epg.clear()
        nowPlaying = null
        unlocked = false
        error = null
        connectionTestResult = null
        newContentMessage = null
    }

    suspend fun loadEpg(ch: Channel) {
        val s = session ?: return
        if (s.type != "xtream" || ch.id.isBlank()) return
        if (epg.containsKey(ch.id)) return
        try {
            val list = withContext(Dispatchers.IO) {
                epgGate.withPermit { XtreamApi(s.server, s.username, s.password).shortEpg(ch.id) }
            }
            epg[ch.id] = list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            epg[ch.id] = emptyList()
        }
    }

    fun playChannel(ch: Channel) {
        val s = session ?: return
        val now = System.currentTimeMillis() / 1000
        val cur = epg[ch.id]?.firstOrNull { now in it.start..it.end }
        nowPlaying = PlayItem(
            url = liveUrl(s, ch), title = ch.name, subtitle = cur?.title ?: "Live Channel",
            isLive = true, poster = ch.icon, historyKey = "live:${ch.id}:${ch.num}", channelId = ch.id
        )
    }

    fun playMovie(m: VodItem) {
        val s = session ?: return
        val key = "movie:${m.id}"
        val resumePos = history.firstOrNull { it.key == key }?.position ?: 0L
        nowPlaying = PlayItem(
            url = movieUrl(s, m),
            title = m.name,
            subtitle = listOf(m.year, "Movie").filter { it.isNotBlank() }.joinToString(" • "),
            isLive = false, poster = m.poster, historyKey = key, movieId = m.id,
            startPosition = resumePos
        )
    }

    fun playEpisode(series: VodItem, ep: Episode) {
        val s = session ?: return
        val key = "ep:${ep.id}"
        val resumePos = history.firstOrNull { it.key == key }?.position ?: 0L
        nowPlaying = PlayItem(
            url = episodeUrl(s, ep),
            title = "${series.name} • S${ep.season}:E${ep.number}",
            subtitle = ep.title, isLive = false, poster = series.poster, historyKey = key,
            seriesId = series.id, episodeId = ep.id, startPosition = resumePos
        )
    }

    fun openSeries(item: VodItem) {
        selectedSeries = item
        episodes = emptyList()
        val s = session ?: return
        viewModelScope.launch {
            episodesLoading = true
            try {
                episodes = withContext(Dispatchers.IO) {
                    XtreamApi(s.server, s.username, s.password).seriesEpisodes(item.id)
                }
            } catch (e: Exception) {
                episodes = emptyList()
            } finally {
                episodesLoading = false
            }
        }
    }

    fun recordHistory(h: HistoryItem) {
        history = (listOf(h) + history.filter { it.key != h.key }).take(10)
        store.saveHistory(history)
    }

    fun canGoNext(): Boolean {
        val cur = nowPlaying ?: return false
        return when {
            cur.isLive -> visibleChannels().size > 1
            cur.seriesId.isNotBlank() -> {
                val idx = episodes.indexOfFirst { it.id == cur.episodeId }
                idx != -1 && idx < episodes.size - 1
            }
            cur.movieId.isNotBlank() -> {
                val idx = movies.indexOfFirst { it.id == cur.movieId }
                idx != -1 && idx < movies.size - 1
            }
            else -> false
        }
    }

    fun canGoPrev(): Boolean {
        val cur = nowPlaying ?: return false
        return when {
            cur.isLive -> visibleChannels().size > 1
            cur.seriesId.isNotBlank() -> episodes.indexOfFirst { it.id == cur.episodeId } > 0
            cur.movieId.isNotBlank() -> movies.indexOfFirst { it.id == cur.movieId } > 0
            else -> false
        }
    }

    fun goNext() {
        val cur = nowPlaying ?: return
        when {
            cur.isLive -> {
                val list = visibleChannels()
                if (list.isEmpty()) return
                val idx = list.indexOfFirst { it.id == cur.channelId }
                playChannel(if (idx == -1) list.first() else list[(idx + 1) % list.size])
            }
            cur.seriesId.isNotBlank() -> {
                val series = selectedSeries ?: return
                if (series.id != cur.seriesId) return
                val idx = episodes.indexOfFirst { it.id == cur.episodeId }
                val next = episodes.getOrNull(idx + 1) ?: return
                playEpisode(series, next)
            }
            cur.movieId.isNotBlank() -> {
                val idx = movies.indexOfFirst { it.id == cur.movieId }
                val next = movies.getOrNull(idx + 1) ?: return
                playMovie(next)
            }
        }
    }

    fun goPrev() {
        val cur = nowPlaying ?: return
        when {
            cur.isLive -> {
                val list = visibleChannels()
                if (list.isEmpty()) return
                val idx = list.indexOfFirst { it.id == cur.channelId }
                playChannel(if (idx == -1) list.first() else list[(idx - 1 + list.size) % list.size])
            }
            cur.seriesId.isNotBlank() -> {
                val series = selectedSeries ?: return
                if (series.id != cur.seriesId) return
                val idx = episodes.indexOfFirst { it.id == cur.episodeId }
                if (idx <= 0) return
                val prev = episodes.getOrNull(idx - 1) ?: return
                playEpisode(series, prev)
            }
            cur.movieId.isNotBlank() -> {
                val idx = movies.indexOfFirst { it.id == cur.movieId }
                if (idx <= 0) return
                val prev = movies.getOrNull(idx - 1) ?: return
                playMovie(prev)
            }
        }
    }
}
