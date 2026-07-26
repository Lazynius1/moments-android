package com.moments.android.views.explore

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.Moment
import com.moments.android.services.cache.VideoThumbnailCache
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.settings.isReelCandidate
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlin.math.max
import kotlin.math.roundToInt

// MARK: - Explore bento layout (exclusivo de Explore)

enum class ExploreBentoTileKind(val colSpan: Int, val rowSpan: Int) {
    UNIT(1, 1),
    TALL(1, 2),
    HERO(2, 2),
}

enum class ExploreGridVisualRole {
    PHOTO,
    VIDEO,
    REEL_HERO,
    REEL_TALL,
}

data class ExploreGridTileDescriptor(
    val layoutKind: ExploreBentoTileKind,
    val visualRole: ExploreGridVisualRole,
    val showsPlayCue: Boolean,
    val showsDuration: Boolean,
) {
    val usesPortraitCrop: Boolean
        get() = visualRole == ExploreGridVisualRole.REEL_HERO || visualRole == ExploreGridVisualRole.REEL_TALL

    companion object {
        fun standard(
            moment: Moment,
            layoutKind: ExploreBentoTileKind = ExploreBentoTileKind.UNIT,
        ): ExploreGridTileDescriptor {
            val isVideo = moment.hasVideoMedia
            val visualRole = when {
                moment.isReelCandidate && layoutKind == ExploreBentoTileKind.HERO -> ExploreGridVisualRole.REEL_HERO
                moment.isReelCandidate && layoutKind == ExploreBentoTileKind.TALL -> ExploreGridVisualRole.REEL_TALL
                isVideo -> ExploreGridVisualRole.VIDEO
                else -> ExploreGridVisualRole.PHOTO
            }
            return ExploreGridTileDescriptor(
                layoutKind = layoutKind,
                visualRole = visualRole,
                showsPlayCue = isVideo,
                showsDuration = isVideo && (layoutKind == ExploreBentoTileKind.HERO || layoutKind == ExploreBentoTileKind.TALL),
            )
        }
    }
}

/** Patrón mosaic fijo de Explore (cada 12 ítems). ≡ `ExploreBentoTileAssigner`. */
object ExploreBentoTileAssigner {
    fun assign(moments: List<Moment>): List<ExploreGridTileDescriptor> =
        moments.mapIndexed { index, moment ->
            ExploreGridTileDescriptor.standard(moment, layoutKind(moment, index))
        }

    private fun layoutKind(moment: Moment, index: Int): ExploreBentoTileKind {
        val slot = index % 12
        return when (slot) {
            0, 11 -> ExploreBentoTileKind.HERO
            4, 7 -> if (moment.hasVideoMedia || moment.isReelCandidate) {
                ExploreBentoTileKind.TALL
            } else {
                ExploreBentoTileKind.UNIT
            }
            else -> ExploreBentoTileKind.UNIT
        }
    }
}

object ExploreMomentsGridMetrics {
    val spacing: Dp = 1.dp
    const val columns = 3

    fun columnWidth(availableWidth: Dp): Dp {
        val totalSpacing = spacing * (columns - 1)
        return (availableWidth - totalSpacing) / columns
    }

    fun tileWidth(kind: ExploreBentoTileKind, unitWidth: Dp): Dp =
        when (kind) {
            ExploreBentoTileKind.UNIT, ExploreBentoTileKind.TALL -> unitWidth
            ExploreBentoTileKind.HERO -> unitWidth * 2 + spacing
        }

    fun tileHeight(kind: ExploreBentoTileKind, unitWidth: Dp): Dp =
        when (kind) {
            ExploreBentoTileKind.UNIT -> unitWidth
            ExploreBentoTileKind.TALL, ExploreBentoTileKind.HERO -> unitWidth * 2 + spacing
        }
}

data class ExploreBentoPlacement(
    val index: Int,
    val kind: ExploreBentoTileKind,
    val startColumn: Int,
    val y: Float,
)

/**
 * Masonry shortest-column — mismo algoritmo que `ExploreBentoLayout` / `bentoHeight` en Swift
 * (alturas en “unidades de fila”, luego convertidas a Dp).
 */
object ExploreBentoLayoutPlanner {
    fun plan(
        kinds: List<ExploreBentoTileKind>,
        columns: Int = ExploreMomentsGridMetrics.columns,
    ): List<ExploreBentoPlacement> {
        val heights = IntArray(columns)
        return kinds.mapIndexed { index, kind ->
            val start = (0..(columns - kind.colSpan)).minBy { candidate ->
                (candidate until candidate + kind.colSpan).maxOf { heights[it] }
            }
            val yUnits = (start until start + kind.colSpan).maxOf { heights[it] }
            repeat(kind.colSpan) { heights[start + it] = yUnits + kind.rowSpan }
            ExploreBentoPlacement(index, kind, start, yUnits.toFloat())
        }
    }

    fun heightUnits(kinds: List<ExploreBentoTileKind>): Int =
        plan(kinds).maxOfOrNull { (it.y + it.kind.rowSpan).roundToInt() } ?: 0
}

fun exploreBentoGridHeight(moments: List<Moment>, availableWidth: Dp): Dp {
    val descriptors = ExploreBentoTileAssigner.assign(moments)
    val kinds = descriptors.map { it.layoutKind }
    val rows = ExploreBentoLayoutPlanner.heightUnits(kinds)
    if (rows == 0) return 0.dp
    val unit = ExploreMomentsGridMetrics.columnWidth(availableWidth)
    val gap = ExploreMomentsGridMetrics.spacing
    return unit * rows + gap * (rows - 1)
}

// MARK: - Grid público de Explore

/**
 * Port de `ExploreMomentsBentoGrid`.
 * Nombre `ExploreMomentsGrid` conservado por call sites Android existentes.
 */
@Composable
fun ExploreMomentsGrid(
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExploreMomentsBentoGrid(
        moments = moments,
        onMomentTap = onMomentTap,
        modifier = modifier,
    )
}

@Composable
fun ExploreMomentsBentoGrid(
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (moments.isEmpty()) return

    val descriptors = remember(moments) { ExploreBentoTileAssigner.assign(moments) }
    val kinds = descriptors.map { it.layoutKind }
    val placements = remember(kinds) { ExploreBentoLayoutPlanner.plan(kinds) }
    val rows = ExploreBentoLayoutPlanner.heightUnits(kinds)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ExploreMomentsGridMetrics.spacing
        val unit = ExploreMomentsGridMetrics.columnWidth(maxWidth)
        val height = if (rows == 0) 0.dp else unit * rows + gap * (rows - 1)

        Box(Modifier.fillMaxWidth().height(height)) {
            placements.forEach { placement ->
                val moment = moments.getOrNull(placement.index) ?: return@forEach
                val descriptor = descriptors.getOrNull(placement.index)
                    ?: ExploreGridTileDescriptor.standard(moment)
                val tileW = ExploreMomentsGridMetrics.tileWidth(placement.kind, unit)
                val tileH = ExploreMomentsGridMetrics.tileHeight(placement.kind, unit)
                Box(
                    Modifier
                        .offset(
                            x = (unit + gap) * placement.startColumn,
                            y = (unit + gap) * placement.y.toInt(),
                        )
                        .width(tileW)
                        .height(tileH),
                ) {
                    ScreenshotProtectedView(
                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                        fillsContainer = true,
                    ) {
                        ExploreMomentThumbnail(
                            moment = moment,
                            descriptor = descriptor,
                            onTap = { onMomentTap(moment, placement.index, moments) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Celda de momento

@Composable
fun ExploreMomentThumbnail(
    moment: Moment,
    descriptor: ExploreGridTileDescriptor,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier
            .scale(if (pressed) 0.97f else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap,
            )
            .clip(RoundedCornerShape(0.dp)),
    ) {
        ExploreThumbnailMedia(moment = moment, usesPortraitCrop = descriptor.usesPortraitCrop)
        if (descriptor.showsPlayCue) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(0.1f),
                                Color.Transparent,
                                Color.Black.copy(if (descriptor.usesPortraitCrop) 0.32f else 0.16f),
                            ),
                        ),
                    ),
            )
        }
        // topChrome — carousel
        if (moment.isCarouselMoment) {
            Icon(
                Icons.Filled.Collections,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(16.dp),
            )
        }
        // bottomChrome — play + duration
        if (descriptor.showsPlayCue) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(0.34f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(10.dp))
                if (descriptor.showsDuration) {
                    moment.videoDuration?.let { duration ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatVideoDuration(duration),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreThumbnailMedia(moment: Moment, usesPortraitCrop: Boolean) {
    val media = moment.primaryVisibleMediaItem
    val isVideo = media?.type?.raw == "video" || moment.hasVideoMedia

    when {
        usesPortraitCrop -> {
            val url = when {
                media?.type?.raw == "image" && media.url.isNotBlank() -> media.url
                !media?.thumbnailUrl.isNullOrBlank() -> media?.thumbnailUrl
                !moment.previewImageURLString.isNullOrBlank() -> moment.previewImageURLString
                else -> null
            }
            if (url != null) {
                FillImage(url)
            } else {
                val video = media?.url?.takeIf { it.isNotBlank() }
                    ?: moment.previewVideoURLString?.takeIf { it.isNotBlank() }
                if (video != null) GeneratedVideoThumbnail(video) else Placeholder()
            }
        }
        media != null && media.url.isNotBlank() -> {
            if (media.type.raw == "video") {
                val thumb = media.thumbnailUrl?.takeIf { it.isNotBlank() }
                if (thumb != null) FillImage(thumb) else GeneratedVideoThumbnail(media.url)
            } else {
                FillImage(media.url)
            }
        }
        !moment.previewImageURLString.isNullOrBlank() -> FillImage(moment.previewImageURLString!!)
        isVideo && !moment.previewVideoURLString.isNullOrBlank() -> GeneratedVideoThumbnail(moment.previewVideoURLString!!)
        else -> Placeholder()
    }
}

@Composable
private fun FillImage(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun GeneratedVideoThumbnail(videoURL: String) {
    var bitmap by remember(videoURL) { mutableStateOf<Bitmap?>(VideoThumbnailCache.cachedThumbnail(videoURL)) }
    var loading by remember(videoURL) { mutableStateOf(bitmap == null) }

    LaunchedEffect(videoURL) {
        if (bitmap != null) return@LaunchedEffect
        loading = true
        bitmap = VideoThumbnailCache.thumbnail(videoURL)
        loading = false
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Placeholder()
            if (loading) {
                CircularProgressIndicator(
                    color = Color(0xFF667EEA),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Gray.copy(0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Image, null, tint = Color.Gray.copy(0.5f), modifier = Modifier.size(20.dp))
    }
}

private fun formatVideoDuration(duration: Double): String {
    val totalSeconds = max(0, duration.roundToInt())
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
