package com.moments.android.views.creator

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItemFeedCrop
import com.moments.android.models.PhotoTag
import com.moments.android.utilities.HapticManager
import kotlin.math.abs
import kotlin.math.max

/**
 * Port de `CreatorSharedModels.swift`.
 * Modelos + UI compartidos del flujo Creator (sin orquestación de [CreatorView]).
 */

typealias ProcessedMedia = CreatorMedia

enum class StoryMediaPresentationMode { FILL, FIT_WITH_BLUR }

/** ≡ `StoryMediaLayoutRules`. */
object StoryMediaLayoutRules {
    private const val fillTolerance = 0.035f

    fun presentationMode(mediaAspectRatio: Float, canvasAspectRatio: Float): StoryMediaPresentationMode {
        if (!mediaAspectRatio.isFinite() || mediaAspectRatio <= 0f ||
            !canvasAspectRatio.isFinite() || canvasAspectRatio <= 0f
        ) {
            return StoryMediaPresentationMode.FILL
        }
        return if (abs(mediaAspectRatio - canvasAspectRatio) <= fillTolerance) {
            StoryMediaPresentationMode.FILL
        } else {
            StoryMediaPresentationMode.FIT_WITH_BLUR
        }
    }

    fun presentationMode(mediaSize: Size, canvasSize: Size): StoryMediaPresentationMode =
        presentationMode(
            mediaSize.width / max(mediaSize.height, 1f),
            canvasSize.width / max(canvasSize.height, 1f),
        )
}

/**
 * Espejo de `CreatorMedia.AspectRatio`.
 * iOS: enum con `.custom(CGFloat)` para ratios intermedios del feed.
 * Android: data class — presets en companion + [custom] para el continuo 3:4…1.91:1.
 */
data class CreatorAspectRatio(
    val displayName: String,
    val ratio: Float,
    private val valueOverride: Float? = null,
) {
    /** Alias iOS `value` (== [ratio] salvo landscape float legacy 1.777). */
    val value: Float
        get() = valueOverride ?: if (displayName == "16:9") 1.777f else ratio

    companion object {
        val SQUARE = CreatorAspectRatio("1:1", 1f)
        val PORTRAIT = CreatorAspectRatio("4:5", 0.8f)
        val LANDSCAPE = CreatorAspectRatio("16:9", 16f / 9f, valueOverride = 1.777f)
        val NINE_BY_SIXTEEN = CreatorAspectRatio("9:16", 9f / 16f, valueOverride = 0.5625f)
        /** 3:4 — retrato alto de post. */
        val REELS_GRID = CreatorAspectRatio("3:4", 1080f / 1440f)
        /** 1.91:1 — apaisado IG. */
        val FEED_LANDSCAPE = CreatorAspectRatio("1.91:1", 1080f / 566f)

        /** Presets para UI que cicla (≡ enum cases, sin custom). */
        val entries: List<CreatorAspectRatio> = listOf(
            SQUARE, PORTRAIT, LANDSCAPE, NINE_BY_SIXTEEN, REELS_GRID, FEED_LANDSCAPE,
        )

        /** ≡ iOS `.custom(safe)` + `displayName` del case custom. */
        fun custom(safe: Float): CreatorAspectRatio {
            val m = com.moments.android.views.creator.creatoruikit.MomentFeedCrop
            val name = when {
                abs(safe - m.reelsGridAspect) < 0.02f -> "3:4"
                abs(safe - m.landscapeMax) < 0.03f -> "1.91:1"
                abs(safe - 3f / 2f) < 0.02f -> "3:2"
                abs(safe - 4f / 3f) < 0.02f -> "4:3"
                else -> "%.4f".format(safe).trimEnd('0').trimEnd('.')
            }
            return when (name) {
                "3:4" -> REELS_GRID
                "1.91:1" -> FEED_LANDSCAPE
                else -> CreatorAspectRatio(name, safe, valueOverride = safe)
            }
        }

        fun fromRatio(imageRatio: Float): CreatorAspectRatio {
            val tolerance = 0.15f
            return when {
                abs(imageRatio - 0.5625f) < tolerance -> NINE_BY_SIXTEEN
                abs(imageRatio - 0.8f) < tolerance -> PORTRAIT
                abs(imageRatio - 1f) < tolerance -> SQUARE
                abs(imageRatio - 1.777f) < tolerance -> LANDSCAPE
                imageRatio < 0.65f -> NINE_BY_SIXTEEN
                imageRatio < 0.85f -> PORTRAIT
                imageRatio < 1.15f -> SQUARE
                else -> LANDSCAPE
            }
        }

        /** ≡ iOS `fromFeedPostRatio` — card continua; Reels 9:16 → 4:5. */
        fun fromFeedPostRatio(ratio: Float): CreatorAspectRatio {
            val safe = com.moments.android.views.creator.creatoruikit.MomentFeedCrop.feedCardAspect(ratio)
            val m = com.moments.android.views.creator.creatoruikit.MomentFeedCrop
            return when {
                abs(safe - m.squareAspect) < 0.008f -> SQUARE
                abs(safe - m.portraitMax) < 0.008f -> PORTRAIT
                abs(safe - m.reelsGridAspect) < 0.008f -> REELS_GRID
                abs(safe - m.landscapeMax) < 0.015f -> FEED_LANDSCAPE
                else -> custom(safe)
            }
        }

        fun parsePersisted(raw: String?): CreatorAspectRatio {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return SQUARE
            return when (trimmed) {
                "1:1" -> SQUARE
                "4:5" -> PORTRAIT
                "3:4" -> REELS_GRID
                "1.91:1" -> FEED_LANDSCAPE
                "16:9" -> LANDSCAPE
                "9:16" -> NINE_BY_SIXTEEN
                else -> {
                    val parts = trimmed.split(":")
                    if (parts.size == 2) {
                        val w = parts[0].toFloatOrNull()
                        val h = parts[1].toFloatOrNull()
                        if (w != null && h != null && h > 0f) return fromFeedPostRatio(w / h)
                    }
                    trimmed.toFloatOrNull()?.takeIf { it > 0f }?.let { return fromFeedPostRatio(it) }
                    SQUARE
                }
            }
        }
    }
}

/** Espejo de `CreatorMedia.StoryVideoMode`. */
enum class StoryVideoMode(val raw: String) {
    NORMAL("normal"),
    TRIMMED("trimmed"),
    AUTO_SPLIT("autoSplit");

    companion object {
        fun from(raw: String?): StoryVideoMode =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * Espejo de `CreatorMedia`.
 * iOS usa `UIImage` + `videoURL`; Android unifica en [uri] (+ [isVideo]).
 */
data class CreatorMedia(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val isVideo: Boolean = false,
    val durationSeconds: Double? = null,
    /** `CreatorMedia.thumbnailURL` — cover custom del editor. */
    val thumbnailUri: Uri? = null,
    val storyVideoMode: StoryVideoMode = StoryVideoMode.NORMAL,
    val aspectRatio: CreatorAspectRatio = CreatorAspectRatio.SQUARE,
    val recommendedAspectRatio: CreatorAspectRatio? = null,
    val hasEdits: Boolean = false,
    val tags: List<PhotoTag> = emptyList(),
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    /** ≡ iOS `feedCrop` — encuadre no destructivo de la card. */
    val feedCrop: MediaItemFeedCrop? = null,
    /** ≡ iOS `immersiveImage` — archivo publicado (peek ≤9:16); null = usar [uri]. */
    val immersiveUri: Uri? = null,
    /** Aspect del archivo inmersivo si difiere de [aspectRatio]. */
    val immersiveAspectRatio: Float? = null,
) {
    companion object {
        /** iOS `maxMomentVideoDuration` = 5 min */
        const val MAX_MOMENT_VIDEO_DURATION_SECONDS = 5.0 * 60.0

        /** iOS `maxMomentVideoUploadSizeBytes` */
        const val MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES: Long = 300L * 1024 * 1024

        /** iOS `maxMomentVideoReadySizeBytes` */
        const val MAX_MOMENT_VIDEO_READY_SIZE_BYTES: Long = 100L * 1024 * 1024

        /** iOS `maxStoryVideoReadySizeBytes` */
        const val MAX_STORY_VIDEO_READY_SIZE_BYTES: Long = 60L * 1024 * 1024
    }
}

/** Álbumes MediaStore — espejo de `AlbumInfo` sin PHAssetCollection. */
data class CreatorAlbumInfo(
    val id: String,
    val title: String,
    val bucketId: String?,
    val assetCount: Int,
)

/**
 * Port de `GlowSharePill`.
 * [titleRes] = clave Localizable (`creator.share` / `creator.next`).
 */
@Composable
fun GlowSharePill(
    titleRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Filled.Send,
    isLoading: Boolean = false,
    isSmall: Boolean = false,
) {
    val hPad = if (isSmall) 14.dp else 20.dp
    val vPad = if (isSmall) 8.dp else 10.dp
    val textSize = if (isSmall) 13.sp else 15.sp
    val iconSize = if (isSmall) 10.dp else 12.dp
    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    Box(
        modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .clickable(enabled = !isLoading) {
                HapticManager.shared.mediumImpact()
                onClick()
            }
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = ink,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(titleRes),
                    color = ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = textSize,
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(iconSize),
                )
            }
        }
    }
}

/**
 * Port de `SelectedMediaBlurView`.
 * En CreatorScreens Android el canvas suele ser AdaptiveColors sólido (decisión de plataforma);
 * este composable queda disponible para paridad / usos que sí quieran el collage blur.
 */
@Composable
fun SelectedMediaBlurView(
    mediaItems: List<CreatorMedia>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (mediaItems.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.Black,
                                Color(0xFF4A148C).copy(alpha = 0.2f),
                                Color(0xFF1565C0).copy(alpha = 0.1f),
                            ),
                        ),
                    ),
            )
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val display = mediaItems.take(4)
            Box(Modifier.fillMaxSize().blur(40.dp)) {
                when (display.size) {
                    1 -> AsyncImage(
                        model = display[0].uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    2 -> display.forEachIndexed { index, item ->
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(w, h * 0.6f)
                                .align(Alignment.TopCenter)
                                .offset(y = if (index == 0) h * 0.25f - h * 0.3f else h * 0.75f - h * 0.3f),
                        )
                    }
                    3 -> {
                        AsyncImage(
                            model = display[0].uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(w, h * 0.6f)
                                .align(Alignment.TopCenter)
                                .offset(y = h * 0.25f - h * 0.3f),
                        )
                        listOf(1, 2).forEach { index ->
                            val isRight = index == 2
                            AsyncImage(
                                model = display[index].uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(w * 0.6f, h * 0.6f)
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = if (isRight) w * 0.75f - w * 0.3f else w * 0.25f - w * 0.3f,
                                        y = h * 0.75f - h * 0.3f,
                                    ),
                            )
                        }
                    }
                    else -> display.forEachIndexed { index, item ->
                        val isRight = index % 2 != 0
                        val isBottom = index >= 2
                        AsyncImage(
                            model = item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(w * 0.6f, h * 0.6f)
                                .align(Alignment.TopStart)
                                .offset(
                                    x = if (isRight) w * 0.75f - w * 0.3f else w * 0.25f - w * 0.3f,
                                    y = if (isBottom) h * 0.75f - h * 0.3f else h * 0.25f - h * 0.3f,
                                ),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
    }
}

/** Icono flecha para `GlowSharePill` de “Next”. */
val GlowSharePillNextIcon: ImageVector get() = Icons.Filled.ArrowForward
