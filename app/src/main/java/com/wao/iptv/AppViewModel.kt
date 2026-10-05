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

    var session by mutableStateOf<Session?>(store.loadSession())
    var loading by mutableStateOf(false)
    var loadingStep by mutableStateOf(0) // 0=idle/connecting, 1=live, 2=movies, 3=series
    var switching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var account by mutableStateOf(AccountInfo())

    var liveCats by mutableStateOf<List<Category>>(emptyList())
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var movieCats by mutableStateOf<List<Category>>(emptyList())
    var movies by mutableStateOf<List<VodItem>>(emptyList())
    var seriesCats by mutableStateOf<List<Category>>(emptyList())
    var seriesList by mutableStateOf<List<VodItem>>(emptyList())

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
        favorites = if (favorites.any { it.key == key }) {
            favorites.filter { it.key != key }
        } else {
            favorites + FavoriteItem(key, "live", ch.name, ch.icon)
        }
        store.saveFavorites(favorites)
    }

    fun toggleFavoriteVod(v: VodItem) {
        val key = (if (v.isSeries) "series:" else "movie:") + v.id
        favorites = if (favorites.any { it.key == key }) {
            favorites.filter { it.key != key }
        } else {
            favorites + FavoriteItem(key, if (v.isSeries) "series" else "movie", v.name, v.poster)
        }
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
        val keys = favorites.filter { it.kind == "series" }.map { it.key }.toSet()
        return seriesList.filter { "series:${it.id}" in keys }
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
        connect(
            acc.session,
            onFail = { switching = false; onFail() }
        ) {
            switching = false
            onSuccess()
        }
    }

    // Days until expiry; null if unknown/not applicable
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
            if (newLive > 0) parts.add("$newLive naye channels")
            if (newMovies > 0) parts.add("$newMovies nayi movies")
            if (newSeries > 0) parts.add("$newSeries nayi series")
            if (parts.isNotEmpty()) {
                newContentMessage = parts.joinToString(", ") + " add hui hain"
            }
        }
        store.saveSnapshot(accId, snap)
    }

    fun dismissNewContentMessage() { newContentMessage = null }
    fun dismissExpiryWarning() { expiryWarningDismissed = true }

    fun connect(s: Session, onFail: () -> Unit = {}, onSuccess: () -> Unit) {
        if (loading) return
        loading = true
        error = null
        loadingStep = 0
        newContentMessage = null
        viewModelScope.launch {
            try {
                if (s.type == "m3u") {
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
                    account = AccountInfo("M3U Playlist", "Active")
                } else {
                    val api = XtreamApi(s.server, s.username, s.password)
                    val acc = withContext(Dispatchers.IO) { api.authenticate() }

                    loadingStep = 1
                    var liveErr: Exception? = null
                    val live = withContext(Dispatchers.IO) {
                        runCatching { api.liveChannels() }
                            .onFailure { e -> if (e is Exception) liveErr = e }
                            .getOrDefault(emptyList())
                    }
                    liveCats = withContext(Dispatchers.IO) {
                        runCatching { api.liveCategories() }.getOrDefault(emptyList())
                    }
                    channels = live

                    loadingStep = 2
                    movies = withContext(Dispatchers.IO) {
                        runCatching { api.movies() }.getOrDefault(emptyList())
                    }
                    movieCats = withContext(Dispatchers.IO) {
                        runCatching { api.movieCategories() }.getOrDefault(emptyList())
                    }

                    loadingStep = 3
                    seriesList = withContext(Dispatchers.IO) {
                        runCatching { api.series() }.getOrDefault(emptyList())
                    }
                    seriesCats = withContext(Dispatchers.IO) {
                        runCatching { api.seriesCategories() }.getOrDefault(emptyList())
                    }

                    if (channels.isEmpty() && movies.isEmpty() && seriesList.isEmpty()) {
                        throw (liveErr ?: RuntimeException(tr(currentLang, "err_no_content")))
                    }
                    account = acc
                }

                session = s
                store.saveSession(s)
                if (s.type == "xtream") store.saveLast(s.server, s.username)
                maybeSaveAccount(s)
                expiryWarningDismissed = false
                checkNewContent(accountId(s), ContentSnapshot(channels.size, movies.size, seriesList.size))
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
        liveCats = emptyList()
        channels = emptyList()
        movieCats = emptyList()
        movies = emptyList()
        seriesCats = emptyList()
        seriesList = emptyList()
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
            url = liveUrl(s, ch),
            title = ch.name,
            subtitle = cur?.title ?: "Live Channel",
            isLive = true,
            poster = ch.icon,
            historyKey = "live:${ch.id}:${ch.num}"
        )
    }

    fun playMovie(m: VodItem) {
        val s = session ?: return
        nowPlaying = PlayItem(
            url = movieUrl(s, m),
            title = m.name,
            subtitle = listOf(m.year, "Movie").filter { it.isNotBlank() }.joinToString(" • "),
            isLive = false,
            poster = m.poster,
            historyKey = "movie:${m.id}"
        )
    }

    fun playEpisode(series: VodItem, ep: Episode) {
        val s = session ?: return
        nowPlaying = PlayItem(
            url = episodeUrl(s, ep),
            title = "${series.name} • S${ep.season}:E${ep.number}",
            subtitle = ep.title,
            isLive = false,
            poster = series.poster,
            historyKey = "ep:${ep.id}"
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
}
