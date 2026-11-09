package com.aproxy.app.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aproxy.app.proxy.EngineState
import com.aproxy.app.proxy.ProxyConfig
import com.aproxy.app.proxy.ProxyEngine
import com.aproxy.app.proxy.ProxyProtocol
import com.aproxy.app.proxy.TrafficState
import com.aproxy.app.service.ProxyService
import com.aproxy.app.util.NetworkUtils
import com.aproxy.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _config = MutableStateFlow(Prefs.loadConfig(appContext))
    val config: StateFlow<ProxyConfig> = _config.asStateFlow()

    private val _deviceIp = MutableStateFlow<String?>(null)
    val deviceIp: StateFlow<String?> = _deviceIp.asStateFlow()

    val engine: StateFlow<EngineState> = ProxyEngine.state
    val traffic: StateFlow<TrafficState> = ProxyEngine.traffic

    init {
        refreshIp()
    }

    fun setProtocolEnabled(protocol: ProxyProtocol, enabled: Boolean) {
        val current = _config.value
        if (!enabled && current.enabledCount == 1 && current.isEnabled(protocol)) return

        var next = current.withEnabled(protocol, enabled)
        if (enabled) {
            val used = current.usedPorts(except = protocol)
            val port = next.portOf(protocol)
            if (port in used) {
                next = next.withPort(protocol, nextFreePort(used, port))
            }
        }
        update(next)
    }

    fun setPort(protocol: ProxyProtocol, port: Int) {
        if (port !in 1024..65535) return
        update(_config.value.withPort(protocol, port))
    }

    fun start() {
        Prefs.saveConfig(appContext, _config.value)
        Prefs.setWantsRunning(appContext, true)
        val intent = Intent(appContext, ProxyService::class.java)
            .setAction(ProxyService.ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop() {
        Prefs.setWantsRunning(appContext, false)
        val intent = Intent(appContext, ProxyService::class.java)
            .setAction(ProxyService.ACTION_STOP)
        try {
            appContext.startService(intent)
        } catch (_: Exception) {
            ProxyEngine.stop()
        }
    }

    fun refreshIp() {
        viewModelScope.launch(Dispatchers.IO) {
            _deviceIp.value = NetworkUtils.primaryAddress()
        }
    }

    private fun update(next: ProxyConfig) {
        _config.value = next
        Prefs.saveConfig(appContext, next)
        ProxyEngine.applyConfig(appContext, next)
    }

    private fun nextFreePort(used: Set<Int>, preferred: Int): Int {
        for (candidate in preferred..65535) {
            if (candidate !in used) return candidate
        }
        for (candidate in 1024 until preferred) {
            if (candidate !in used) return candidate
        }
        return preferred
    }
}
