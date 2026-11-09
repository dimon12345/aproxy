package com.aproxy.app.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * HTTP proxy. Handles CONNECT tunnels (HTTPS traffic) and absolute-URI
 * requests. When [tlsContext] is provided the proxy itself speaks TLS, which
 * is the "HTTPS proxy" mode.
 */
class HttpProxyServer(
    port: Int,
    private val tlsContext: SSLContext?,
    meter: TrafficMeter,
) : SocketProxyServer(port, meter) {

    override fun createServerSocket(): ServerSocket {
        val context = tlsContext ?: return super.createServerSocket()
        return context.serverSocketFactory.createServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port), 50)
        }
    }

    override suspend fun handle(client: Socket) = withContext(Dispatchers.IO) {
        client.soTimeout = 30_000
        client.tcpNoDelay = true

        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())

        val headerBytes = readHttpHeader(input) ?: return@withContext
        val header = String(headerBytes, Charsets.ISO_8859_1)
        val lines = header.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            respondError(output, 400, "Bad Request")
            return@withContext
        }

        val requestParts = lines.first().split(' ', limit = 3)
        if (requestParts.size < 2) {
            respondError(output, 400, "Bad Request")
            return@withContext
        }

        val method = requestParts[0].uppercase(Locale.US)
        val target = requestParts[1]
        val version = requestParts.getOrElse(2) { "HTTP/1.1" }

        if (method == "CONNECT") {
            handleConnect(client, input, output, target)
        } else {
            handleAbsoluteRequest(client, input, output, method, target, version, lines.drop(1))
        }
    }

    private suspend fun handleConnect(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        target: String,
    ) {
        val hostPort = parseHostPort(target, 443)
        if (hostPort == null) {
            respondError(output, 400, "Bad CONNECT target")
            return
        }

        val upstream = try {
            openSocket(hostPort.host, hostPort.port)
        } catch (error: Exception) {
            respondError(output, 502, "Bad Gateway: ${error.message ?: "connection failed"}")
            return
        }

        output.write(
            (
                "HTTP/1.1 200 Connection Established\r\n" +
                    "Proxy-Agent: AProxy\r\n\r\n"
                ).toByteArray(Charsets.ISO_8859_1),
        )
        output.flush()

        client.soTimeout = 0
        upstream.soTimeout = 0

        try {
            relaySockets(client, input, output, upstream, meter)
        } finally {
            runCatching { upstream.close() }
        }
    }

    private suspend fun handleAbsoluteRequest(
        client: Socket,
        input: InputStream,
        output: OutputStream,
        method: String,
        target: String,
        version: String,
        headers: List<String>,
    ) {
        val uri = runCatching { URI(target) }.getOrNull()
        val host = uri?.host
        if (uri == null || !uri.isAbsolute || host.isNullOrBlank()) {
            respondError(
                output,
                400,
                "Bad Request: this is a proxy server, use an absolute URL or CONNECT",
            )
            return
        }

        val scheme = uri.scheme.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            respondError(output, 400, "Unsupported scheme: ${uri.scheme}")
            return
        }

        val port = if (uri.port > 0) {
            uri.port
        } else if (scheme == "https") {
            443
        } else {
            80
        }

        val path = buildString {
            append(uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }

        val upstream = try {
            openSocket(host, port, secure = scheme == "https")
        } catch (error: Exception) {
            respondError(output, 502, "Bad Gateway: ${error.message ?: "connection failed"}")
            return
        }

        client.soTimeout = 0
        upstream.soTimeout = 0

        val forwardedHeaders = headers.filterNot { isHopByHopHeader(it) }
        val request = buildString {
            append(method).append(' ').append(path).append(' ').append(version).append("\r\n")
            forwardedHeaders.forEach { append(it).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }

        try {
            upstream.getOutputStream().apply {
                write(request.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            relaySockets(client, input, output, upstream, meter)
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun openSocket(host: String, port: Int, secure: Boolean = false): Socket {
        val plain = Socket()
        plain.tcpNoDelay = true
        plain.connect(InetSocketAddress(host, port), 15_000)
        if (!secure) return plain

        val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(plain, host, port, true) as SSLSocket
        tls.startHandshake()
        return tls
    }

    private fun isHopByHopHeader(line: String): Boolean {
        val name = line.substringBefore(':').trim().lowercase(Locale.US)
        return name in HOP_BY_HOP_HEADERS
    }

    private fun respondError(output: OutputStream, code: Int, message: String) {
        val body = "$code $message\n"
        val response = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ").append(body.toByteArray(Charsets.UTF_8).size).append("\r\n")
            append("Connection: close\r\n")
            append("Proxy-Agent: AProxy\r\n\r\n")
            append(body)
        }
        runCatching {
            output.write(response.toByteArray(Charsets.ISO_8859_1))
            output.flush()
        }
    }

    private fun reason(code: Int): String = when (code) {
        400 -> "Bad Request"
        405 -> "Method Not Allowed"
        502 -> "Bad Gateway"
        else -> "Error"
    }

    private companion object {
        val HOP_BY_HOP_HEADERS = setOf(
            "proxy-connection",
            "proxy-authorization",
            "connection",
            "keep-alive",
        )
    }
}
