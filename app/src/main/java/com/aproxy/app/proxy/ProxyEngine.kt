package com.aproxy.app.proxy

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.net.ssl.SSLContext

data class EngineState(
    val config: ProxyConfig = ProxyConfig(),
    val starting: Boolean = false,
    val running: Boolean = false,
    val active: Set<ProxyProtocol> = emptySet(),
    val clients: Int = 0,
    val error: String? = null,
)

/**
 * Owns all proxy listeners. Lives in the app process and is controlled by
 * [com.aproxy.app.service.ProxyService]; the UI observes its state directly.
 */
object ProxyEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val meter = TrafficMeter()

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()
    val traffic: StateFlow<TrafficState> = meter.state

    private var httpServer: HttpProxyServer? = null
    private var httpsServer: HttpProxyServer? = null
    private var socksServer: Socks5Server? = null
    private var tlsContext: SSLContext? = null
    private var samplerJob: Job? = null

    @Synchronized
    fun start(context: Context, config: ProxyConfig) {
        if (config.enabledCount == 0) {
            _state.value = EngineState(
                config = config,
                error = "Enable at least one protocol",
            )
            return
        }

        stopServers()
        meter.reset()
        _state.value = EngineState(config = config, starting = true)

        val active = linkedSetOf<ProxyProtocol>()
        val errors = mutableListOf<String>()

        if (config.httpEnabled) {
            try {
                httpServer = HttpProxyServer(config.httpPort, null, meter)
                    .also { it.start(scope) }
                active += ProxyProtocol.HTTP
            } catch (error: Throwable) {
                errors += describe("HTTP", config.httpPort, error)
            }
        }

        if (config.httpsEnabled) {
            try {
                val contextTls = tlsContext ?: TlsContext.create(context).also {
                    tlsContext = it
                }
                httpsServer = HttpProxyServer(config.httpsPort, contextTls, meter)
                    .also { it.start(scope) }
                active += ProxyProtocol.HTTPS
            } catch (error: Throwable) {
                errors += describe("HTTPS", config.httpsPort, error)
            }
        }

        if (config.socksEnabled) {
            try {
                socksServer = Socks5Server(config.socksPort, meter)
                    .also { it.start(scope) }
                active += ProxyProtocol.SOCKS5
            } catch (error: Throwable) {
                errors += describe("SOCKS5", config.socksPort, error)
            }
        }

        if (active.isEmpty()) {
            stopServers()
            _state.value = EngineState(
                config = config,
                error = errors.joinToString("\n").ifEmpty { "Unable to start proxy" },
            )
            return
        }

        startSampler()
        _state.value = EngineState(
            config = config,
            running = true,
            active = active,
            error = errors.joinToString("\n").ifEmpty { null },
        )
    }

    @Synchronized
    fun stop() {
        stopServers()
        meter.idle()
        _state.update {
            it.copy(
                starting = false,
                running = false,
                active = emptySet(),
                clients = 0,
                error = null,
            )
        }
    }

    /** Applies a new configuration, restarting listeners if they are running. */
    @Synchronized
    fun applyConfig(context: Context, config: ProxyConfig) {
        val current = _state.value
        if (current.running || current.starting) {
            start(context, config)
        } else {
            _state.value = current.copy(config = config, error = null)
        }
    }

    private fun startSampler() {
        samplerJob?.cancel()
        samplerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                meter.sample()
                val clients = (httpServer?.activeClients ?: 0) +
                    (httpsServer?.activeClients ?: 0) +
                    (socksServer?.activeClients ?: 0)
                _state.update {
                    if (it.running) it.copy(clients = clients) else it
                }
            }
        }
    }

    private fun stopServers() {
        samplerJob?.cancel()
        samplerJob = null
        httpServer?.stop()
        httpServer = null
        httpsServer?.stop()
        httpsServer = null
        socksServer?.stop()
        socksServer = null
    }

    private fun describe(protocol: String, port: Int, error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return "$protocol :$port — $message"
    }
}
