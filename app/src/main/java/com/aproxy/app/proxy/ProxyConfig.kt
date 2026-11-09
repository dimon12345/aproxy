package com.aproxy.app.proxy

enum class ProxyProtocol {
    HTTP,
    HTTPS,
    SOCKS5,
}

data class ProxyConfig(
    val httpEnabled: Boolean = true,
    val httpPort: Int = 8080,
    val httpsEnabled: Boolean = false,
    val httpsPort: Int = 8443,
    val socksEnabled: Boolean = true,
    val socksPort: Int = 1080,
) {
    val enabledCount: Int
        get() = listOf(httpEnabled, httpsEnabled, socksEnabled).count { it }

    fun isEnabled(protocol: ProxyProtocol): Boolean = when (protocol) {
        ProxyProtocol.HTTP -> httpEnabled
        ProxyProtocol.HTTPS -> httpsEnabled
        ProxyProtocol.SOCKS5 -> socksEnabled
    }

    fun portOf(protocol: ProxyProtocol): Int = when (protocol) {
        ProxyProtocol.HTTP -> httpPort
        ProxyProtocol.HTTPS -> httpsPort
        ProxyProtocol.SOCKS5 -> socksPort
    }

    fun withEnabled(protocol: ProxyProtocol, enabled: Boolean): ProxyConfig = when (protocol) {
        ProxyProtocol.HTTP -> copy(httpEnabled = enabled)
        ProxyProtocol.HTTPS -> copy(httpsEnabled = enabled)
        ProxyProtocol.SOCKS5 -> copy(socksEnabled = enabled)
    }

    fun withPort(protocol: ProxyProtocol, port: Int): ProxyConfig = when (protocol) {
        ProxyProtocol.HTTP -> copy(httpPort = port)
        ProxyProtocol.HTTPS -> copy(httpsPort = port)
        ProxyProtocol.SOCKS5 -> copy(socksPort = port)
    }

    fun usedPorts(except: ProxyProtocol? = null): Set<Int> = ProxyProtocol.entries
        .filter { it != except && isEnabled(it) }
        .map { portOf(it) }
        .toSet()
}
