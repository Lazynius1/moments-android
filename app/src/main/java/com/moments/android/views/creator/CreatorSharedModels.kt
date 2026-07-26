package com.moments.android.views.creator

import android.net.Uri
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

/** Espejo de `CreatorMedia.AspectRatio`. */
enum class CreatorAspectRatio(val displayName: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 0.8f),
    LANDSCAPE("16:9", 16f / 9f),
    NINE_BY_SIXTEEN("9:16", 9f / 16f);

    /** Alias iOS `value` (== [ratio] salvo landscape float legacy 1.777). */
    val value: Float get() = when (this) {
        SQUARE -> 1f
        PORTRAIT -> 0.8f
        LANDSCAPE -> 1.777f
        NINE_BY_SIXTEEN -> 0.5625f
    }

    companion object {
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
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFFFF9800)),
                ),
            )
            .clickable(enabled = !isLoading) {
                HapticManager.shared.mediumImpact()
                onClick()
            }
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(titleRes),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = textSize,
                )
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
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
