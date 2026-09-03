package com.moments.android.services.cache

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * ImageLoader de app: límites de RAM/disco para el feed.
 * MomentsApplication implementa ImageLoaderFactory y delega aquí.
 */
object MomentsImageLoader {
    fun create(context: Context): ImageLoader {
        val app = context.applicationContext
        return ImageLoader.Builder(app)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(app)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(app.cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(400L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
