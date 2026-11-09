package com.aproxy.app.util

import android.content.Context
import com.aproxy.app.proxy.ProxyConfig

object Prefs {

    private const val FILE = "aproxy_prefs"

    private const val KEY_HTTP_ENABLED = "http_enabled"
    private const val KEY_HTTP_PORT = "http_port"
    private const val KEY_HTTPS_ENABLED = "https_enabled"
    private const val KEY_HTTPS_PORT = "https_port"
    private const val KEY_SOCKS_ENABLED = "socks_enabled"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_WANTS_RUNNING = "wants_running"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadConfig(context: Context): ProxyConfig {
        val prefs = prefs(context)
        return ProxyConfig(
            httpEnabled = prefs.getBoolean(KEY_HTTP_ENABLED, true),
            httpPort = prefs.getInt(KEY_HTTP_PORT, 8080),
            httpsEnabled = prefs.getBoolean(KEY_HTTPS_ENABLED, false),
            httpsPort = prefs.getInt(KEY_HTTPS_PORT, 8443),
            socksEnabled = prefs.getBoolean(KEY_SOCKS_ENABLED, true),
            socksPort = prefs.getInt(KEY_SOCKS_PORT, 1080),
        )
    }

    fun saveConfig(context: Context, config: ProxyConfig) {
        prefs(context).edit()
            .putBoolean(KEY_HTTP_ENABLED, config.httpEnabled)
            .putInt(KEY_HTTP_PORT, config.httpPort)
            .putBoolean(KEY_HTTPS_ENABLED, config.httpsEnabled)
            .putInt(KEY_HTTPS_PORT, config.httpsPort)
            .putBoolean(KEY_SOCKS_ENABLED, config.socksEnabled)
            .putInt(KEY_SOCKS_PORT, config.socksPort)
            .apply()
    }

    fun wantsRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WANTS_RUNNING, false)

    fun setWantsRunning(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WANTS_RUNNING, value).apply()
    }
}
