package com.moments.android.views.creator.components

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * ≡ `Notification.Name.momentsDidReceiveMemoryWarning`.
 * En Android se dispara vía [ComponentCallbacks2] en [ChatGIFImageCache.initialize].
 */
const val MOMENTS_DID_RECEIVE_MEMORY_WARNING = "momentsDidReceiveMemoryWarning"

/**
 * ≡ `ChatGIFImageCache` — caché RAM de GIFs/stickers (sobrevive al reciclado de celdas).
 * countLimit ≈ 60, totalCostLimit ≈ 40MB.
 */
object ChatGIFImageCache {
    private val lock = Any()
    private val memory = object : LruCache<String, Drawable>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Drawable): Int = estimatedCost(value)
    }
    private val inFlight = ConcurrentHashMap<String, MutableMap<UUID, (Drawable?) -> Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var callbacksRegistered = false
    private var imageLoader: ImageLoader? = null

    fun initialize(context: Context) {
        if (callbacksRegistered) return
        synchronized(this) {
            if (callbacksRegistered) return
            val app = context.applicationContext
            imageLoader = ImageLoader.Builder(app)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
            app.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() {
                    clearMemory()
                }
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        clearMemory()
                    }
                }
            })
            callbacksRegistered = true
        }
    }

    fun cachedImage(url: String): Drawable? = synchronized(lock) { memory.get(url) }

    fun clearMemory() {
        synchronized(lock) { memory.evictAll() }
    }

    fun load(context: Context, url: String, completion: (Drawable?) -> Unit) {
        initialize(context)
        cachedImage(url)?.let {
            mainHandler.post { completion(it) }
            return
        }

        val token = UUID.randomUUID()
        synchronized(lock) {
            val handlers = inFlight.getOrPut(url) { mutableMapOf() }
            handlers[token] = completion
            if (handlers.size > 1) return
        }

        val appContext = context.applicationContext
        val loader = imageLoader ?: ImageLoader(appContext)
        val request = ImageRequest.Builder(appContext)
            .data(url)
            .allowHardware(false)
            .build()

        scope.launch {
            val drawable = runCatching {
                (loader.execute(request) as? SuccessResult)?.drawable
            }.getOrNull()
            finish(url, drawable)
        }
    }

    fun prefetch(context: Context, url: String) {
        load(context, url) { }
    }

    private fun finish(url: String, drawable: Drawable?) {
        if (drawable != null) {
            synchronized(lock) {
                val snapshot = memory.snapshot()
                if (snapshot.size >= 60) {
                    val overflow = snapshot.size - 59
                    snapshot.keys.take(overflow).forEach { memory.remove(it) }
                }
                memory.put(url, drawable)
            }
        }
        val handlers: List<(Drawable?) -> Unit>
        synchronized(lock) {
            handlers = inFlight.remove(url)?.values?.toList().orEmpty()
        }
        mainHandler.post {
            handlers.forEach { it(drawable) }
        }
    }

    private fun estimatedCost(drawable: Drawable): Int {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null) return max(bitmap.byteCount, 1)
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        return w * h * 4
    }
}

/** Port de modelos Giphy de `StickerGiphyViews.swift`. */
data class GiphyResponse(
    val data: List<GiphyGif>,
    val pagination: GiphyPagination?,
) {
    companion object {
        fun fromJson(payload: String): GiphyResponse {
            val root = JSONObject(payload)
            val data = root.optJSONArray("data") ?: JSONArray()
            return GiphyResponse(
                data = buildList {
                    for (index in 0 until data.length()) {
                        data.optJSONObject(index)?.let { add(GiphyGif.fromJson(it)) }
                    }
                },
                pagination = root.optJSONObject("pagination")?.let(GiphyPagination::fromJson),
            )
        }
    }
}

data class GiphyPagination(
    val totalCount: Int,
    val count: Int,
    val offset: Int,
) {
    companion object {
        fun fromJson(json: JSONObject) = GiphyPagination(
            totalCount = json.optInt("total_count"),
            count = json.optInt("count"),
            offset = json.optInt("offset"),
        )
    }
}

data class GiphyGif(
    val id: String,
    val images: GiphyImages,
) {
    val preferredStickerUrl: String?
        get() = images.original?.url?.takeIf { it.isNotBlank() }
            ?: images.fixedHeight.url.takeIf { it.isNotBlank() }

    /** Relación ancho/alto del preview `fixed_height` (masonry). */
    val previewAspectRatio: Float
        get() {
            val w = max(images.fixedHeight.width.toFloatOrNull() ?: 1f, 1f)
            val h = max(images.fixedHeight.height.toFloatOrNull() ?: 1f, 1f)
            return w / h
        }

    companion object {
        fun fromJson(json: JSONObject): GiphyGif {
            val images = json.optJSONObject("images") ?: JSONObject()
            return GiphyGif(
                id = json.optString("id"),
                images = GiphyImages(
                    fixedHeight = GiphyImage.fromJson(images.optJSONObject("fixed_height") ?: JSONObject()),
                    original = images.optJSONObject("original")?.let(GiphyImage::fromJson),
                ),
            )
        }
    }
}

data class GiphyImages(
    val fixedHeight: GiphyImage,
    val original: GiphyImage?,
)

data class GiphyImage(
    val url: String,
    val width: String,
    val height: String,
) {
    companion object {
        fun fromJson(json: JSONObject) = GiphyImage(
            url = json.optString("url"),
            width = json.optString("width"),
            height = json.optString("height"),
        )
    }
}

/**
 * Compose ≡ `AnimatedGIFView` (Coil anima; [ChatGIFImageCache] para prefetch/clear).
 * Sin spinner (iOS muestra vacío hasta cargar).
 */
@Composable
fun AnimatedGIFView(
    url: String?,
    modifier: Modifier = Modifier,
    onIntrinsicSize: ((Size) -> Unit)? = null,
) {
    val context = LocalContext.current
    val sizeCallback by rememberUpdatedState(onIntrinsicSize)
    LaunchedEffect(Unit) { ChatGIFImageCache.initialize(context) }

    if (url.isNullOrBlank()) {
        Box(modifier)
        return
    }

    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .decoderFactory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoderDecoder.Factory()
                } else {
                    GifDecoder.Factory()
                },
            )
            .listener(
                onSuccess = { _, result ->
                    val d = result.drawable
                    val w = d.intrinsicWidth.toFloat()
                    val h = d.intrinsicHeight.toFloat()
                    if (w > 0f && h > 0f) {
                        sizeCallback?.invoke(Size(w, h))
                    }
                },
            )
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * Port de `MomentsTrendingGrid` (detail GIF) / `ModernGiphyGridView`.
 * iOS: 4 columnas, aspect 1:1, spacing 8, corner 14.
 * [onReachEnd] ≡ pagination en MomentsTrendingGrid.onAppear(last).
 */
@Composable
fun ModernGiphyGridView(
    gifs: List<GiphyGif>,
    onSelect: (GiphyGif) -> Unit,
    modifier: Modifier = Modifier,
    onReachEnd: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(gifs, key = { it.id }) { gif ->
            val url = gif.images.fixedHeight.url.takeIf { it.isNotBlank() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .then(if (url != null) Modifier.clickable { onSelect(gif) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    AnimatedGIFView(url = url, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (gif.id == gifs.lastOrNull()?.id) {
                LaunchedEffect(gif.id) { onReachEnd?.invoke() }
            }
        }
    }
}
