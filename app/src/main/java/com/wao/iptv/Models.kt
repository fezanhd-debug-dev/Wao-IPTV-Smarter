package com.wao.iptv

data class Session(
    val type: String = "xtream",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = ""
)

data class SavedAccount(
    val id: String,
    val label: String,
    val session: Session
)

data class Category(val id: String, val name: String)

data class Channel(
    val num: Int,
    val id: String,
    val name: String,
    val icon: String,
    val categoryId: String,
    val epgId: String,
    val directUrl: String = ""
)

data class VodItem(
    val id: String,
    val name: String,
    val poster: String,
    val rating: String,
    val year: String,
    val categoryId: String,
    val ext: String,
    val plot: String = "",
    val directUrl: String = "",
    val isSeries: Boolean = false
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

data class AccountInfo(
    val username: String = "",
    val status: String = "",
    val expDate: Long = 0L,
    val maxConnections: String = ""
)

data class PlayItem(
    val url: String,
    val title: String,
    val subtitle: String,
    val isLive: Boolean,
    val poster: String = "",
    val historyKey: String = "",
    val startPosition: Long = 0L
)

data class HistoryItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val poster: String,
    val url: String,
    val isLive: Boolean,
    val position: Long,
    val duration: Long
)

data class AppSettings(
    val engine: Int = 0,
    val bufferMode: Int = 1,
    val pinEnabled: Boolean = false,
    val pin: String = "",
    val uiMode: Int = 0
)
