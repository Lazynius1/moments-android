package com.moments.android.debugscreenshots

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory

/**
 * Corta en modo capturas todas las llamadas `HttpURLConnection` a Google / Firebase
 * (Cloud Functions, Cloud Run, tokens…) para que nada llegue a la cuenta real, y sirve
 * las imágenes de demo (`moments-screenshots.invalid`) desde los assets de debug.
 * El resto de hosts sigue con el manejador del sistema.
 */
object ScreenshotNetworkGuard {
    private const val TAG = "ScreenshotNetworkGuard"

    @Volatile
    private var installed = false

    fun isBlockedHost(rawHost: String): Boolean {
        val host = rawHost.lowercase()
        return host.endsWith("googleapis.com") ||
            host.endsWith("cloudfunctions.net") ||
            host.endsWith("firebaseio.com") ||
            host.endsWith("firebaseapp.com") ||
            host.endsWith(".run.app") ||
            host.contains("firebasestorage")
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        // Plantillas creadas antes de instalar la fábrica: conservan el manejador del sistema.
        val systemHttp = URL("http://${ScreenshotImages.HOST}/")
        val systemHttps = URL("https://${ScreenshotImages.HOST}/")
        runCatching {
            URL.setURLStreamHandlerFactory(
                URLStreamHandlerFactory { protocol ->
                    when (protocol.lowercase()) {
                        "http" -> GuardedHandler(app, systemHttp, 80)
                        "https" -> GuardedHandler(app, systemHttps, 443)
                        else -> null
                    }
                },
            )
        }.onFailure { Log.e(TAG, "No se pudo instalar el filtro de red", it) }
    }

    private class GuardedHandler(
        private val context: Context,
        private val systemTemplate: URL,
        private val defaultPort: Int,
    ) : URLStreamHandler() {

        override fun openConnection(u: URL): URLConnection = route(u) {
            // Con la plantilla como contexto, la URL nueva hereda el manejador del sistema.
            URL(systemTemplate, u.toExternalForm()).openConnection()
        }

        override fun openConnection(u: URL, p: Proxy): URLConnection = route(u) {
            URL(systemTemplate, u.toExternalForm()).openConnection(p)
        }

        private inline fun route(u: URL, system: () -> URLConnection): URLConnection {
            val host = u.host.orEmpty().lowercase()
            return when {
                host == ScreenshotImages.HOST -> AssetConnection(u, context)
                ScreenshotNetworkGuard.isBlockedHost(host) -> BlockedConnection(u)
                else -> system()
            }
        }

        override fun getDefaultPort(): Int = defaultPort

        // Sin resolver DNS al comparar o usar URLs como clave.
        override fun hashCode(u: URL): Int = u.toExternalForm().hashCode()

        override fun hostsEqual(u1: URL, u2: URL): Boolean =
            u1.host.orEmpty().equals(u2.host.orEmpty(), ignoreCase = true)
    }

    /** Respuesta local con el JPEG de demo. */
    private class AssetConnection(url: URL, private val context: Context) : HttpURLConnection(url) {
        private var bytes: ByteArray? = null

        override fun connect() {
            if (bytes != null) return
            val asset = ScreenshotImages.assetName(url.toString())
            bytes = ScreenshotImages.assetBytes(context, asset)
                ?: throw FileNotFoundException(url.toString())
            connected = true
        }

        override fun getInputStream(): InputStream {
            connect()
            return ByteArrayInputStream(bytes ?: ByteArray(0))
        }

        override fun getResponseCode(): Int = try {
            connect()
            HttpURLConnection.HTTP_OK
        } catch (_: IOException) {
            HttpURLConnection.HTTP_NOT_FOUND
        }

        override fun getResponseMessage(): String = "OK"

        override fun getContentType(): String = "image/jpeg"

        override fun getContentLength(): Int = runCatching { connect(); bytes?.size ?: -1 }.getOrDefault(-1)

        override fun getContentLengthLong(): Long = getContentLength().toLong()

        override fun getHeaderField(name: String?): String? = when {
            name.equals("Content-Type", ignoreCase = true) -> "image/jpeg"
            name.equals("Content-Length", ignoreCase = true) -> getContentLength().toString()
            else -> null
        }

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false
    }

    /** Se comporta como una conexión rechazada: falla al conectar, nunca sale del teléfono. */
    private class BlockedConnection(url: URL) : HttpURLConnection(url) {
        private fun refuse(): IOException = IOException("Bloqueado en modo capturas: ${url.host}")

        override fun connect() {
            throw refuse()
        }

        override fun getInputStream(): InputStream = throw refuse()

        override fun getOutputStream(): OutputStream = throw refuse()

        override fun getResponseCode(): Int = throw refuse()

        override fun getErrorStream(): InputStream? = null

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false
    }
}
