package com.wao.iptv

import android.util.Base64
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) WAO-IPTV/4.2"

val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

fun normalizeServer(input: String): String {
    var s = input.trim()
    if (s.isEmpty()) return s
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
    return s.trimEnd('/')
}

fun httpGet(url: String): String {
    val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
    return httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("Server error ${resp.code}")
        resp.body?.string() ?: ""
    }
}

fun <T> httpJsonArray(url: String, mapper: (HashMap<String, String>) -> T?): List<T> {
    val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
    return httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw RuntimeException("Server error ${resp.code}")
        val reader = JsonReader(resp.body!!.charStream())
        reader.isLenient = true
        val out = ArrayList<T>()
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                val map = HashMap<String, String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> map[name] = reader.nextString()
                        JsonToken.BOOLEAN -> map[name] = reader.nextBoolean().toString()
                        JsonToken.NULL -> reader.nextNull()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val item = mapper(map)
                if (item != null) out.add(item)
            }
            reader.endArray()
        }
        out
    }
}

fun guessYear(name: String): String = Regex("(19|20)\\d{2}").find(name)?.value ?: ""

fun cleanRating(r: String?): String {
    val v = r?.toDoubleOrNull()
    return if (v == null || v <= 0.0) "" else String.format(Locale.US, "%.1f", v)
}

private fun decodeB64(s: String): String {
    return try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: Exception) {
        s
    }
}

class XtreamApi(private val server: String, private val user: String, private val pass: String) {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun base() = "$server/player_api.php?username=${enc(user)}&password=${enc(pass)}"
    private fun url(action: String, extra: String = "") = "${base()}&action=$action$extra"

    fun authenticate(): AccountInfo {
        val root: JsonElement = try {
            JsonParser.parseString(httpGet(base()))
        } catch (e: Exception) {
            if (e is RuntimeException && e.message?.startsWith("Server error") == true) throw e
            throw RuntimeException("Server se galat jawab aaya. Xtream API check karein.")
        }
        if (!root.isJsonObject) throw RuntimeException("Server ne sahi jawab nahi diya")
        val ui = root.asJsonObject.get("user_info")
        if (ui == null || !ui.isJsonObject) throw RuntimeException("Username ya password ghalat hai")
        val o = ui.asJsonObject
        fun f(k: String): String {
            val e = o.get(k)
            return if (e == null || e.isJsonNull || !e.isJsonPrimitive) "" else e.asString
        }
        if (f("auth") == "0") throw RuntimeException("Username ya password ghalat hai")
        val status = f("status")
        if (status.isNotEmpty() && !status.equals("Active", true)) throw RuntimeException("Account $status hai")
        return AccountInfo(
            username = f("username"),
            status = status.ifBlank { "Active" },
            expDate = f("exp_date").toLongOrNull() ?: 0L,
            maxConnections = f("max_connections")
        )
    }

    private fun cats(action: String): List<Category> =
        httpJsonArray(url(action)) { m -> Category(m["category_id"] ?: "", m["category_name"] ?: "") }

    fun liveCategories() = cats("get_live_categories")
    fun movieCategories() = cats("get_vod_categories")
    fun seriesCategories() = cats("get_series_categories")

    fun liveChannels(): List<Channel> {
        var n = 0
        return httpJsonArray(url("get_live_streams")) { m ->
            n++
            val id = m["stream_id"] ?: ""
            if (id.isBlank()) null
            else Channel(
                num = m["num"]?.toIntOrNull() ?: n,
                id = id,
                name = m["name"] ?: "",
                icon = m["stream_icon"] ?: "",
                categoryId = m["category_id"] ?: "",
                epgId = m["epg_channel_id"] ?: ""
            )
        }
    }

    fun movies(): List<VodItem> =
        httpJsonArray(url("get_vod_streams")) { m ->
            val id = m["stream_id"] ?: ""
            val name = m["name"] ?: ""
            if (id.isBlank()) null
            else VodItem(
                id = id,
                name = name,
                poster = m["stream_icon"] ?: "",
                rating = cleanRating(m["rating"] ?: m["rating_5based"]),
                year = (m["year"] ?: "").ifBlank { guessYear(name) },
                categoryId = m["category_id"] ?: "",
                ext = (m["container_extension"] ?: "").ifBlank { "mp4" }
            )
        }

    fun series(): List<VodItem> =
        httpJsonArray(url("get_series")) { m ->
            val id = m["series_id"] ?: ""
            if (id.isBlank()) null
            else VodItem(
                id = id,
                name = m["name"] ?: "",
                poster = m["cover"] ?: "",
                rating = cleanRating(m["rating"] ?: m["rating_5based"]),
                year = (m["releaseDate"] ?: m["release_date"] ?: m["year"] ?: "").take(4),
                categoryId = m["category_id"] ?: "",
                ext = "",
                plot = m["plot"] ?: "",
                isSeries = true
            )
        }

    fun seriesEpisodes(seriesId: String): List<Episode> {
        val root = try {
            JsonParser.parseString(httpGet(url("get_series_info", "&series_id=$seriesId")))
        } catch (e: Exception) {
            return emptyList()
        }
        if (!root.isJsonObject) return emptyList()
        val eps = root.asJsonObject.get("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()

        fun str(o: com.google.gson.JsonObject, k: String): String {
            val e = o.get(k)
            return if (e == null || e.isJsonNull || !e.isJsonPrimitive) "" else e.asString
        }

        fun addSeason(seasonKey: String, arr: com.google.gson.JsonArray) {
            for (e in arr) {
                if (!e.isJsonObject) continue
                val o = e.asJsonObject
                out.add(
                    Episode(
                        id = str(o, "id"),
                        season = str(o, "season").toIntOrNull() ?: seasonKey.toIntOrNull() ?: 1,
                        number = str(o, "episode_num").toIntOrNull() ?: (out.size + 1),
                        title = str(o, "title").ifBlank { "Episode" },
                        ext = str(o, "container_extension").ifBlank { "mp4" }
                    )
                )
            }
        }

        if (eps.isJsonObject) {
            for ((k, v) in eps.asJsonObject.entrySet()) {
                if (v.isJsonArray) addSeason(k, v.asJsonArray)
            }
        } else if (eps.isJsonArray) {
            eps.asJsonArray.forEachIndexed { i, v ->
                if (v.isJsonArray) addSeason((i + 1).toString(), v.asJsonArray)
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }

    fun shortEpg(streamId: String): List<EpgEntry> {
        val root = try {
            JsonParser.parseString(httpGet(url("get_short_epg", "&stream_id=$streamId&limit=3")))
        } catch (e: Exception) {
            return emptyList()
        }
        if (!root.isJsonObject) return emptyList()
        val arr = root.asJsonObject.get("epg_listings") ?: return emptyList()
        if (!arr.isJsonArray) return emptyList()
        val out = ArrayList<EpgEntry>()
        for (e in arr.asJsonArray) {
            if (!e.isJsonObject) continue
            val o = e.asJsonObject
            fun g(k: String): String {
                val x = o.get(k)
                return if (x == null || x.isJsonNull || !x.isJsonPrimitive) "" else x.asString
            }
            val title = decodeB64(g("title"))
            val st = g("start_timestamp").toLongOrNull() ?: 0L
            val en = g("stop_timestamp").toLongOrNull() ?: 0L
            if (title.isNotBlank() && en > 0L) out.add(EpgEntry(title, st, en))
        }
        return out
    }
}

fun liveUrl(s: Session, ch: Channel): String =
    if (ch.directUrl.isNotEmpty()) ch.directUrl
    else "${s.server}/live/${s.username}/${s.password}/${ch.id}.m3u8"

fun movieUrl(s: Session, m: VodItem): String =
    if (m.directUrl.isNotEmpty()) m.directUrl
    else "${s.server}/movie/${s.username}/${s.password}/${m.id}.${m.ext}"

fun episodeUrl(s: Session, ep: Episode): String =
    "${s.server}/series/${s.username}/${s.password}/${ep.id}.${ep.ext}"

class M3uResult(
    val liveCats: List<Category>,
    val live: List<Channel>,
    val movieCats: List<Category>,
    val movies: List<VodItem>
)

fun parseM3u(text: String): M3uResult {
    val attrRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    val live = ArrayList<Channel>()
    val movies = ArrayList<VodItem>()
    val liveGroups = LinkedHashSet<String>()
    val movieGroups = LinkedHashSet<String>()
    var info: String? = null
    var n = 0
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val cur = info
        if (line.startsWith("#EXTINF")) {
            info = line
        } else if (!line.startsWith("#") && cur != null) {
            val attrs = attrRegex.findAll(cur).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val name = cur.substringAfterLast(",").trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
            val group = (attrs["group-title"] ?: "").ifBlank { "Other" }
            val logo = attrs["tvg-logo"] ?: ""
            n++
            if (line.contains("/movie/")) {
                movieGroups.add(group)
                movies.add(VodItem(n.toString(), name, logo, "", guessYear(name), group, "", directUrl = line))
            } else {
                liveGroups.add(group)
                live.add(Channel(n, n.toString(), name, logo, group, attrs["tvg-id"] ?: "", line))
            }
            info = null
        }
    }
    return M3uResult(
        liveGroups.map { Category(it, it) },
        live,
        movieGroups.map { Category(it, it) },
        movies
    )
}
