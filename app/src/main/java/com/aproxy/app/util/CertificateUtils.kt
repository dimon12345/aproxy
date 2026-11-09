package com.aproxy.app.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.aproxy.app.R
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/** Exports the HTTPS proxy certificate so clients can trust it. */
object CertificateUtils {

    private const val ASSET = "aproxy.p12"
    private const val PASSWORD = "aproxy"
    private const val ALIAS = "aproxy"
    private const val FILE_NAME = "aproxy.crt"
    private const val MIME_TYPE = "application/x-x509-ca-cert"

    fun share(context: Context): Boolean = runCatching {
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open(ASSET).use { stream ->
            keyStore.load(stream, PASSWORD.toCharArray())
        }
        val certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        val pem = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            append(Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
            append("\n-----END CERTIFICATE-----\n")
        }

        val file = File(context.cacheDir, FILE_NAME).apply { writeText(pem) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_cert))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
