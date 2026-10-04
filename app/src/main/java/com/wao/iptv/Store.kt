package com.wao.iptv

import android.content.Context
import com.google.gson.Gson

class Store(context: Context) {
    private val sp = context.getSharedPreferences("wao_iptv", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadSession(): Session? {
        val j = sp.getString("session", null) ?: return null
        return try {
            gson.fromJson(j, Session::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSession(s: Session?) {
        val e = sp.edit()
        if (s == null) e.remove("session") else e.putString("session", gson.toJson(s))
        e.apply()
    }

    fun lastServer(): String = sp.getString("last_server", "") ?: ""
    fun lastUser(): String = sp.getString("last_user", "") ?: ""

    fun saveLast(server: String, user: String) {
        sp.edit().putString("last_server", server).putString("last_user", user).apply()
    }

    fun loadSettings(): AppSettings {
        val j = sp.getString("settings", null) ?: return AppSettings()
        return try {
            val s = gson.fromJson(j, AppSettings::class.java) ?: return AppSettings()
            val safeLang = if (s.lang == null || (s.lang as String).isBlank()) "en" else s.lang
            s.copy(lang = safeLang)
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(s: AppSettings) {
        sp.edit().putString("settings", gson.toJson(s)).apply()
    }

    fun loadHistory(): List<HistoryItem> {
        val j = sp.getString("history", null) ?: return emptyList()
        return try {
            gson.fromJson(j, Array<HistoryItem>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<HistoryItem>) {
        sp.edit().putString("history", gson.toJson(list)).apply()
    }

    fun loadAccounts(): List<SavedAccount> {
        val j = sp.getString("accounts", null) ?: return emptyList()
        return try {
            gson.fromJson(j, Array<SavedAccount>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(list: List<SavedAccount>) {
        sp.edit().putString("accounts", gson.toJson(list)).apply()
    }

    fun loadFavorites(): List<FavoriteItem> {
        val j = sp.getString("favorites", null) ?: return emptyList()
        return try {
            gson.fromJson(j, Array<FavoriteItem>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFavorites(list: List<FavoriteItem>) {
        sp.edit().putString("favorites", gson.toJson(list)).apply()
    }

    private fun snapshotKey(accountId: String) = "snapshot_$accountId"

    fun loadSnapshot(accountId: String): ContentSnapshot? {
        val j = sp.getString(snapshotKey(accountId), null) ?: return null
        return try {
            gson.fromJson(j, ContentSnapshot::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSnapshot(accountId: String, snap: ContentSnapshot) {
        sp.edit().putString(snapshotKey(accountId), gson.toJson(snap)).apply()
    }
}
