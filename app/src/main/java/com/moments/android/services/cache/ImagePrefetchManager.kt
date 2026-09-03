package com.moments.android.services.cache

import android.content.Context
import coil.imageLoader
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.moments.android.services.video.VideoPlaybackSelector
import java.net.URL
import java.util.Collections

/**
 * Port de `ImagePrefetchManager.swift` — Kingfisher ImagePrefetcher → Coil enqueue.
 * Tope global 20 URLs en vuelo; cancelAll detiene descargas reales.
 */
object ImagePrefetchManager {

    private val currentlyPrefetchingUrls = Collections.synchronizedSet(mutableSetOf<String>())
    private val inFlightDisposables = Collections.synchronizedMap(mutableMapOf<String, Disposable>())
    private const val MAX_IN_FLIGHT_URLS = 20
    /** iOS DelayRetryStrategy(maxRetryCount: 2, retryInterval: .seconds(2)). */
    private const val MAX_RETRY_COUNT = 2

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun prefetchUrls(urls: List<URL>) {
        val context = appContext ?: return
        if (urls.isEmpty()) return

        val urlsToProcess: List<URL>
        val imageUrls = urls.filter { !VideoPlaybackSelector.isLikelyVideoUrl(it.toString()) }
        if (imageUrls.isEmpty()) return
        synchronized(currentlyPrefetchingUrls) {
            val availableSlots = (MAX_IN_FLIGHT_URLS - currentlyPrefetchingUrls.size).coerceAtLeast(0)
            if (availableSlots == 0) {
                urlsToProcess = emptyList()
            } else {
                val newUrls = imageUrls.filter { !currentlyPrefetchingUrls.contains(it.toString()) }
                urlsToProcess = newUrls.take(availableSlots)
                urlsToProcess.forEach { currentlyPrefetchingUrls.add(it.toString()) }
            }
        }
        if (urlsToProcess.isEmpty()) return

        val loader = context.imageLoader
        for (url in urlsToProcess) {
            enqueueWithRetry(context, loader, url, attempt = 0)
        }
    }

    fun prefetch(urlStrings: List<String>) {
        prefetchUrls(urlStrings.mapNotNull { runCatching { URL(it) }.getOrNull() })
    }

    fun cancelAll() {
        synchronized(inFlightDisposables) {
            inFlightDisposables.values.forEach { it.dispose() }
            inFlightDisposables.clear()
        }
        currentlyPrefetchingUrls.clear()
    }

    private fun enqueueWithRetry(
        context: Context,
        loader: coil.ImageLoader,
        url: URL,
        attempt: Int,
    ) {
        val key = url.toString()
        val request = ImageRequest.Builder(context)
            .data(url)
            // Prefetch al ancho típico de card (~pantalla), no full-res.
            .size(1080, 1920)
            .listener(
                object : ImageRequest.Listener {
                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        finish(key)
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        if (attempt < MAX_RETRY_COUNT) {
                            // Reintento tras ~2s (paridad DelayRetryStrategy).
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (currentlyPrefetchingUrls.contains(key)) {
                                    enqueueWithRetry(context, loader, url, attempt + 1)
                                }
                            }, 2_000L)
                        } else {
                            finish(key)
                        }
                    }

                    override fun onCancel(request: ImageRequest) {
                        finish(key)
                    }
                },
            )
            .build()
        val disposable = loader.enqueue(request)
        synchronized(inFlightDisposables) {
            inFlightDisposables[key]?.dispose()
            inFlightDisposables[key] = disposable
        }
    }

    private fun finish(key: String) {
        currentlyPrefetchingUrls.remove(key)
        synchronized(inFlightDisposables) { inFlightDisposables.remove(key) }
    }
}
