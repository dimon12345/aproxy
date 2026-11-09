package com.aproxy.app.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * Starts real proxy listeners against a local echo server, so the socket
 * handling is verified end-to-end without an Android device.
 */
class ProxyServersTest {

    private lateinit var echoServer: ServerSocket
    private lateinit var scope: CoroutineScope
    private var echoPort: Int = 0

    @Before
    fun setUp() {
        echoServer = ServerSocket(0)
        echoPort = echoServer.localPort
        thread(isDaemon = true, name = "echo-server") {
            while (!echoServer.isClosed) {
                val client = try {
                    echoServer.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) {
                    client.use { socket ->
                        socket.getInputStream().copyTo(socket.getOutputStream())
                    }
                }
            }
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        runCatching { echoServer.close() }
    }

    @Test
    fun httpProxyTunnelsConnectTraffic() {
        val port = freePort()
        val proxy = HttpProxyServer(port, null, TrafficMeter())
        proxy.start(scope)

        try {
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 5_000
                val output = client.getOutputStream()
                val input = BufferedInputStream(client.getInputStream())

                output.write(
                    "CONNECT 127.0.0.1:$echoPort HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1),
                )
                output.flush()

                val response = readHeader(input)
                assertTrue(response, response.startsWith("HTTP/1.1 200"))

                output.write("hello proxy".toByteArray())
                output.flush()

                val echoed = readExactly(input, "hello proxy".length)
                assertEquals("hello proxy", echoed)
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun socks5ProxyTunnelsConnectTraffic() {        val port = freePort()
        val proxy = Socks5Server(port, TrafficMeter())
        proxy.start(scope)

        try {
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 5_000
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val output = client.getOutputStream()

                output.write(byteArrayOf(5, 1, 0))
                output.flush()
                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())

                output.write(
                    byteArrayOf(
                        5,
                        1,
                        0,
                        1,
                        127,
                        0,
                        0,
                        1,
                        (echoPort shr 8).toByte(),
                        (echoPort and 0xFF).toByte(),
                    ),
                )
                output.flush()

                assertEquals(5, input.readUnsignedByte())
                assertEquals(0, input.readUnsignedByte())
                input.readUnsignedByte() // reserved
                val addressType = input.readUnsignedByte()
                val addressLength = when (addressType) {
                    1 -> 4
                    4 -> 16
                    else -> 0
                }
                repeat(addressLength + 2) { input.readUnsignedByte() }

                output.write("hello socks".toByteArray())
                output.flush()
                assertEquals("hello socks", readExactly(input, "hello socks".length))
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun httpProxyRewritesAbsoluteUri() {
        val targetServer = ServerSocket(0)
        val targetPort = targetServer.localPort
        val receivedHeaders = AtomicReference("")

        thread(isDaemon = true, name = "http-target") {
            try {
                targetServer.accept().use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())
                    receivedHeaders.set(readHeader(input))
                    socket.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: 2\r\n" +
                                "Connection: close\r\n\r\nok"
                            ).toByteArray(Charsets.ISO_8859_1),
                    )
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // The test assertion below will fail with the captured request.
            }
        }

        val port = freePort()
        val proxy = HttpProxyServer(port, null, TrafficMeter())
        proxy.start(scope)

        try {
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 5_000
                client.getOutputStream().write(
                    (
                        "GET http://127.0.0.1:$targetPort/path?q=1 HTTP/1.1\r\n" +
                            "Host: 127.0.0.1:$targetPort\r\n" +
                            "Proxy-Connection: keep-alive\r\n\r\n"
                        ).toByteArray(Charsets.ISO_8859_1),
                )
                client.getOutputStream().flush()

                val response = readHeader(BufferedInputStream(client.getInputStream()))
                assertTrue(response, response.startsWith("HTTP/1.1 200 OK"))

                val forwarded = receivedHeaders.get()
                assertTrue(forwarded, forwarded.startsWith("GET /path?q=1 HTTP/1.1"))
                assertTrue(forwarded, !forwarded.contains("Proxy-Connection", ignoreCase = true))
                assertTrue(forwarded, forwarded.contains("Connection: close", ignoreCase = true))
            }
        } finally {
            proxy.stop()
            runCatching { targetServer.close() }
        }
    }

    @Test
    fun httpsProxyAcceptsTlsAndTunnelsTraffic() {
        val port = freePort()
        val proxy = HttpProxyServer(port, createTlsContext(), TrafficMeter())
        proxy.start(scope)

        try {
            val socket = trustAllTlsContext()
                .socketFactory
                .createSocket("127.0.0.1", port) as SSLSocket
            socket.use { client ->
                client.soTimeout = 5_000
                client.startHandshake()

                val output = client.getOutputStream()
                val input = BufferedInputStream(client.getInputStream())
                output.write(
                    "CONNECT 127.0.0.1:$echoPort HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n"
                        .toByteArray(Charsets.ISO_8859_1),
                )
                output.flush()

                val response = readHeader(input)
                assertTrue(response, response.startsWith("HTTP/1.1 200"))

                output.write("hello tls".toByteArray())
                output.flush()
                assertEquals("hello tls", readExactly(input, "hello tls".length))
            }
        } finally {
            proxy.stop()
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun createTlsContext(): SSLContext {
        val asset = listOf(
            java.io.File("src/main/assets/aproxy.p12"),
            java.io.File("app/src/main/assets/aproxy.p12"),
        ).first { it.exists() }
        val keyStore = KeyStore.getInstance("PKCS12")
        asset.inputStream().use { keyStore.load(it, "aproxy".toCharArray()) }
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, "aproxy".toCharArray())
        return SSLContext.getInstance("TLS").apply {
            init(factory.keyManagers, null, null)
        }
    }

    private fun trustAllTlsContext(): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
    }

    private fun readHeader(input: InputStream): String {
        val bytes = StringBuilder()
        var matched = 0
        while (matched < 4) {
            val value = input.read()
            if (value < 0) break
            bytes.append(value.toChar())
            matched = when {
                value == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                value == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                value == '\r'.code -> 1
                else -> 0
            }
        }
        return bytes.toString()
    }

    private fun readExactly(input: InputStream, length: Int): String {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return String(buffer, 0, offset)
    }
}
