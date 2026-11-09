package com.aproxy.app.proxy

import android.content.Context
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Loads the self-signed certificate shipped in assets/aproxy.p12 and builds
 * the TLS context used by the HTTPS proxy listener.
 */
object TlsContext {

    private const val ASSET = "aproxy.p12"
    private const val PASSWORD = "aproxy"

    fun create(context: Context): SSLContext {
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(ASSET).use { stream ->
            keyStore.load(stream, PASSWORD.toCharArray())
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm(),
        )
        keyManagerFactory.init(keyStore, PASSWORD.toCharArray())

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, null)
        }
    }
}
