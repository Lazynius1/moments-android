package com.moments.android.services.cache

import android.content.Context
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import java.net.URL
import java.util.Collections

/** Port de ImagePrefetchManager.swift — Kingfisher → Coil. */
object ImagePrefetchManager {

    private val currentlyPrefetchingUrls = Collections.synchronizedSet(mutableSetOf<String>())
    private val inFlightDisposables = Collections.synchronizedList(mutableListOf<Disposable>())
    private val maxInFlightUrls = 20

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun prefetchUrls(urls: List<URL>) {
        val context = appContext ?: return
        if (urls.isEmpty()) return

        val urlsToProcess: List<URL>
        synchronized(currentlyPrefetchingUrls) {
            val availableSlots = (maxInFlightUrls - currentlyPrefetchingUrls.size).coerceAtLeast(0)
            if (availableSlots == 0) {
                urlsToProcess = emptyList()
            } else {
                val newUrls = urls.filter { !currentlyPrefetchingUrls.contains(it.toString()) }
                urlsToProcess = newUrls.take(availableSlots)
                urlsToProcess.forEach { currentlyPrefetchingUrls.add(it.toString()) }
            }
        }
        if (urlsToProcess.isEmpty()) return

        val loader = context.imageLoader
        for (url in urlsToProcess) {
            val key = url.toString()
            val request = ImageRequest.Builder(context)
                .data(url)
                .listener(
                    onSuccess = { _, _ -> currentlyPrefetchingUrls.remove(key) },
                    onError = { _, _ -> currentlyPrefetchingUrls.remove(key) },
                    onCancel = { currentlyPrefetchingUrls.remove(key) },
                )
                .build()
            val disposable = loader.enqueue(request)
            synchronized(inFlightDisposables) { inFlightDisposables.add(disposable) }
        }
    }

    fun prefetch(urlStrings: List<String>) {
        prefetchUrls(urlStrings.mapNotNull { runCatching { URL(it) }.getOrNull() })
    }

    fun cancelAll() {
        synchronized(inFlightDisposables) {
            inFlightDisposables.forEach { it.dispose() }
            inFlightDisposables.clear()
        }
        currentlyPrefetchingUrls.clear()
    }
}
