package com.aproxy.app.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

internal data class HostPort(val host: String, val port: Int)

private const val MAX_HEADER_BYTES = 64 * 1024

/**
 * Parses "host:port", "[::1]:443" or plain "host" (with [defaultPort]).
 * Returns null for malformed authorities.
 */
internal fun parseHostPort(authority: String, defaultPort: Int): HostPort? {
    val value = authority.trim()
    if (value.isEmpty()) return null

    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end <= 1) return null
        val host = value.substring(1, end)
        val port = if (end + 1 < value.length) {
            if (value[end + 1] != ':') return null
            value.substring(end + 2).toIntOrNull() ?: return null
        } else {
            defaultPort
        }
        return if (port in 1..65535) HostPort(host, port) else null
    }

    if (value.count { it == ':' } > 1) return null
    val separator = value.lastIndexOf(':')
    if (separator < 0) {
        return if (defaultPort in 1..65535) HostPort(value, defaultPort) else null
    }
    val host = value.substring(0, separator)
    if (host.isEmpty()) return null
    val port = value.substring(separator + 1).toIntOrNull() ?: return null
    return if (port in 1..65535) HostPort(host, port) else null
}

/** Reads an HTTP header block including the final CRLFCRLF, or null on EOF/overflow. */
internal fun readHttpHeader(input: InputStream): ByteArray? {
    val out = ByteArrayOutputStream(1024)
    var matched = 0
    while (out.size() < MAX_HEADER_BYTES) {
        val byte = input.read()
        if (byte == -1) return null
        out.write(byte)
        matched = when {
            byte == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
            byte == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
            byte == '\r'.code -> 1
            else -> 0
        }
        if (matched == 4) return out.toByteArray()
    }
    return null
}

/**
 * Copies data in both directions until the sockets close. Upload is
 * client -> upstream, download is upstream -> client.
 */
internal suspend fun relaySockets(
    client: Socket,
    clientInput: InputStream,
    clientOutput: OutputStream,
    upstream: Socket,
    meter: TrafficMeter,
) {
    coroutineScope {
        val upstreamOutput = upstream.getOutputStream()
        val upstreamInput = upstream.getInputStream()

        val toUpstream = launch(Dispatchers.IO) {
            try {
                pump(clientInput, upstreamOutput) { meter.addUpload(it) }
            } finally {
                runCatching { upstream.shutdownOutput() }
            }
        }
        val toClient = launch(Dispatchers.IO) {
            try {
                pump(upstreamInput, clientOutput) { meter.addDownload(it) }
            } finally {
                runCatching { client.shutdownOutput() }
            }
        }

        toUpstream.join()
        toClient.join()
    }
}

private suspend fun pump(
    input: InputStream,
    output: OutputStream,
    onBytes: (Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val read = try {
            input.read(buffer)
        } catch (_: Exception) {
            -1
        }
        if (read < 0) break
        if (read == 0) continue
        try {
            output.write(buffer, 0, read)
            output.flush()
        } catch (_: Exception) {
            break
        }
        onBytes(read.toLong())
    }
}
