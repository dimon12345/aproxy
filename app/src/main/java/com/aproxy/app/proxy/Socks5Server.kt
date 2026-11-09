package com.aproxy.app.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * Minimal SOCKS5 server (RFC 1928) with the "no authentication" method and
 * CONNECT command support.
 */
class Socks5Server(
    port: Int,
    meter: TrafficMeter,
) : SocketProxyServer(port, meter) {

    override suspend fun handle(client: Socket) = withContext(Dispatchers.IO) {
        client.soTimeout = 15_000
        client.tcpNoDelay = true

        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val data = DataInputStream(input)

        // Greeting: VER, NMETHODS, METHODS
        if (data.readUnsignedByte() != 5) return@withContext
        val methodCount = data.readUnsignedByte()
        if (methodCount == 0) return@withContext
        val methods = ByteArray(methodCount)
        data.readFully(methods)

        if (methods.none { it.toInt() == METHOD_NO_AUTH.toInt() }) {
            output.write(byteArrayOf(5, 0xFF.toByte()))
            output.flush()
            return@withContext
        }
        output.write(byteArrayOf(5, METHOD_NO_AUTH))
        output.flush()

        // Request: VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT
        if (data.readUnsignedByte() != 5) return@withContext
        val command = data.readUnsignedByte()
        data.readUnsignedByte() // reserved
        if (command != CMD_CONNECT) {
            writeReply(output, REPLY_COMMAND_NOT_SUPPORTED)
            return@withContext
        }
        val addressType = try {
            data.readUnsignedByte()
        } catch (_: Exception) {
            return@withContext
        }

        val host = when (addressType) {
            ATYP_IPV4 -> {
                val bytes = ByteArray(4)
                data.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress
            }

            ATYP_DOMAIN -> {
                val length = data.readUnsignedByte()
                if (length == 0) {
                    writeReply(output, REPLY_GENERAL_FAILURE)
                    return@withContext
                }
                val bytes = ByteArray(length)
                data.readFully(bytes)
                String(bytes, Charsets.US_ASCII)
            }

            ATYP_IPV6 -> {
                val bytes = ByteArray(16)
                data.readFully(bytes)
                InetAddress.getByAddress(bytes).hostAddress
            }

            else -> {
                writeReply(output, REPLY_ADDRESS_NOT_SUPPORTED)
                return@withContext
            }
        }

        val port = (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
        if (port == 0) {
            writeReply(output, REPLY_GENERAL_FAILURE)
            return@withContext
        }

        val upstream = try {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), 15_000)
            }
        } catch (error: Exception) {
            writeReply(output, replyFor(error))
            return@withContext
        }

        try {
            writeSuccessReply(output, upstream)
            client.soTimeout = 0
            upstream.soTimeout = 0
            relaySockets(client, input, output, upstream, meter)
        } finally {
            runCatching { upstream.close() }
        }
    }

    private fun writeSuccessReply(output: OutputStream, upstream: Socket) {
        val address = upstream.localAddress.address
        val localPort = upstream.localPort
        val reply = ByteArray(4 + address.size + 2)
        reply[0] = 5
        reply[1] = REPLY_SUCCESS
        reply[2] = 0
        reply[3] = if (address.size == 4) ATYP_IPV4.toByte() else ATYP_IPV6.toByte()
        address.copyInto(reply, destinationOffset = 4)
        reply[4 + address.size] = ((localPort shr 8) and 0xFF).toByte()
        reply[5 + address.size] = (localPort and 0xFF).toByte()
        output.write(reply)
        output.flush()
    }

    private fun writeReply(output: OutputStream, code: Byte) {
        val reply = byteArrayOf(
            5.toByte(),
            code,
            0.toByte(),
            ATYP_IPV4.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
            0.toByte(),
        )
        runCatching {
            output.write(reply)
            output.flush()
        }
    }

    private fun replyFor(error: Exception): Byte = when (error) {
        is UnknownHostException -> REPLY_HOST_UNREACHABLE
        is ConnectException -> REPLY_CONNECTION_REFUSED
        is NoRouteToHostException -> REPLY_NETWORK_UNREACHABLE
        is SocketTimeoutException -> REPLY_TTL_EXPIRED
        else -> REPLY_GENERAL_FAILURE
    }

    private companion object {
        const val METHOD_NO_AUTH: Byte = 0
        const val CMD_CONNECT = 1

        const val ATYP_IPV4 = 1
        const val ATYP_DOMAIN = 3
        const val ATYP_IPV6 = 4

        const val REPLY_SUCCESS: Byte = 0
        const val REPLY_GENERAL_FAILURE: Byte = 1
        const val REPLY_NETWORK_UNREACHABLE: Byte = 3
        const val REPLY_HOST_UNREACHABLE: Byte = 4
        const val REPLY_CONNECTION_REFUSED: Byte = 5
        const val REPLY_TTL_EXPIRED: Byte = 6
        const val REPLY_COMMAND_NOT_SUPPORTED: Byte = 7
        const val REPLY_ADDRESS_NOT_SUPPORTED: Byte = 8
    }
}
