package com.wao.iptv

import android.content.Context
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashHandler {
    private const val PREF = "wao_iptv_crash"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, sw.toString())
                    .apply()
            } catch (e: Exception) {
                // ignore
            }
            if (default != null) {
                default.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
        }
    }

    fun readAndClear(context: Context): String? {
        val sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val v = sp.getString(KEY, null)
        if (v != null) sp.edit().remove(KEY).apply()
        return v
    }
}
