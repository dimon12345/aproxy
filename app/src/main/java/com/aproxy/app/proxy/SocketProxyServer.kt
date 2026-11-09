package com.aproxy.app.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface ProxyServer {
    val port: Int
    val activeClients: Int
    fun start(scope: CoroutineScope)
    fun stop()
}

/**
 * Accepts TCP connections and runs [handle] in its own coroutine. Active
 * sockets are tracked so that [stop] tears everything down immediately.
 */
abstract class SocketProxyServer(
    final override val port: Int,
    protected val meter: TrafficMeter,
) : ProxyServer {

    private val clients: MutableSet<Socket> =
        Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val clientCounter = AtomicInteger(0)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var stopped = true

    override val activeClients: Int
        get() = clientCounter.get()

    protected open fun createServerSocket(): ServerSocket =
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port), 50)
        }

    protected abstract suspend fun handle(client: Socket)

    override fun start(scope: CoroutineScope) {
        if (serverSocket != null) return
        val socket = createServerSocket()
        serverSocket = socket
        stopped = false

        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !stopped) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                clients.add(client)
                clientCounter.incrementAndGet()

                scope.launch(Dispatchers.IO) {
                    try {
                        handle(client)
                    } catch (_: Exception) {
                        // A broken client must not stop the server.
                    } finally {
                        clients.remove(client)
                        runCatching { client.close() }
                        clientCounter.decrementAndGet()
                    }
                }
            }
        }
    }

    override fun stop() {
        stopped = true
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        clientCounter.set(0)
    }
}
