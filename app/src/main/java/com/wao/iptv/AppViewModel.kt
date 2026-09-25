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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class Loaded(
    val account: AccountInfo,
    val liveCats: List<Category>,
    val channels: List<Channel>,
    val movieCats: List<Category>,
    val movies: List<VodItem>,
    val seriesCats: List<Category>,
    val series: List<VodItem>
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var session by mutableStateOf<Session?>(store.loadSession())
    var loading by mutableStateOf(false)
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

    var selectedSeries by mutableStateOf<VodItem?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var episodesLoading by mutableStateOf(false)

    var vodTab by mutableStateOf(0)
    var unlocked by mutableStateOf(false)
    var homeRoute: String = "home"

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
        store.saveSettings(s)
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
        connect(
            acc.session,
            onFail = { switching = false; onFail() }
        ) {
            switching = false
            onSuccess()
        }
    }

    fun connect(s: Session, onFail: () -> Unit = {}, onSuccess: () -> Unit) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { fetchAll(s) }
                account = r.account
                liveCats = r.liveCats
                channels = r.channels
                movieCats = r.movieCats
                movies = r.movies
                seriesCats = r.seriesCats
                seriesList = r.series
                session = s
                store.saveSession(s)
                if (s.type == "xtream") store.saveLast(s.server, s.username)
                maybeSaveAccount(s)
                onSuccess()
            } catch (e: Exception) {
                error = friendly(e)
                onFail()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun fetchAll(s: Session): Loaded {
        if (s.type == "m3u") {
            val text = httpGet(s.m3uUrl)
            if (!text.contains("#EXTINF")) throw RuntimeException("Ye valid M3U playlist nahi hai")
            val r = parseM3u(text)
            return Loaded(
                AccountInfo("M3U Playlist", "Active"),
                r.liveCats, r.live, r.movieCats, r.movies, emptyList(), emptyList()
            )
        }
        val api = XtreamApi(s.server, s.username, s.password)
        val acc = api.authenticate()
        return coroutineScope {
            val liveC = async { runCatching { api.liveCategories() }.getOrDefault(emptyList()) }
            val liveS = async { runCatching { api.liveChannels() } }
            val movC = async { runCatching { api.movieCategories() }.getOrDefault(emptyList()) }
            val movS = async { runCatching { api.movies() }.getOrDefault(emptyList()) }
            val serC = async { runCatching { api.seriesCategories() }.getOrDefault(emptyList()) }
            val serS = async { runCatching { api.series() }.getOrDefault(emptyList()) }

            val liveRes = liveS.await()
            val live = liveRes.getOrDefault(emptyList())
            val mov = movS.await()
            val ser = serS.await()
            if (live.isEmpty() && mov.isEmpty() && ser.isEmpty()) {
                throw (liveRes.exceptionOrNull() ?: RuntimeException("Is account me koi channel ya movie nahi mili"))
            }
            Loaded(acc, liveC.await(), live, movC.await(), mov, serC.await(), ser)
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Server nahi mil raha. URL aur internet check karein."
        is SocketTimeoutException -> "Server ne waqt par jawab nahi diya (timeout)."
        is javax.net.ssl.SSLException -> "SSL/HTTPS ka masla hai. http:// try karein."
        is IllegalArgumentException -> "Server URL sahi nahi hai."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Connect nahi ho saka."
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
