package com.moments.android.views.creator.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject

/** Port de `Views/Creator/Components/StickerGiphyViews.swift`. */
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

    val previewAspectRatio: Float
        get() = (images.fixedHeight.width.toFloatOrNull() ?: 1f)
            .coerceAtLeast(1f) / (images.fixedHeight.height.toFloatOrNull() ?: 1f).coerceAtLeast(1f)

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

/** Compose equivalente de `AnimatedGIFView`; Coil mantiene la animación y su caché de memoria. */
@Composable
fun AnimatedGIFView(
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) {
        Box(modifier.background(Color.White.copy(alpha = 0.06f)))
        return
    }
    val request = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
        .data(url)
        .crossfade(false)
        .decoderFactory(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ImageDecoderDecoder.Factory() else GifDecoder.Factory(),
        )
        .build()
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 1.5.dp)
            }
        },
        success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
    )
}

/** Port de `ModernGiphyGridView`; también alimenta el catálogo GIF de Story. */
@Composable
fun ModernGiphyGridView(
    gifs: List<GiphyGif>,
    onSelect: (GiphyGif) -> Unit,
    modifier: Modifier = Modifier,
    onReachEnd: (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        items(gifs, key = { it.id }) { gif ->
            AnimatedGIFView(
                url = gif.images.fixedHeight.url,
                modifier = Modifier
                    .aspectRatio(0.88f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onSelect(gif) },
            )
            if (gif.id == gifs.lastOrNull()?.id) {
                LaunchedEffect(gif.id) { onReachEnd?.invoke() }
            }
        }
    }
}
