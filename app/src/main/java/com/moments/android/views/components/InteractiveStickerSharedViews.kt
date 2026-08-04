package com.moments.android.views.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.rawPadding
import com.moments.android.services.cache.PersistentAudioCache
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import java.net.URL
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// MARK: - StoryPolaroidFrameStyle

/** Port de `StoryPolaroidFrameStyle`. */
enum class StoryPolaroidFrameStyle(val raw: String) {
    CLASSIC("classic"),
    CLEAN("clean"),
    VINTAGE("vintage"),
    ALBUM("album");

    companion object {
        fun fromRawOrDefault(raw: String?) = entries.firstOrNull { it.raw == raw } ?: CLASSIC
    }
}

// MARK: - Palette / tap-cycle helpers (funcs iOS → top-level Kotlin)

fun momentsStickerSurface(isDark: Boolean): Color =
    if (isDark) Color.fromHex("0B1215") else Color.fromHex("FAF9F6")

fun momentsStickerInk(isDark: Boolean): Color =
    if (isDark) Color.fromHex("FAF9F6") else Color.fromHex("0B1215")

fun momentsStickerInverseSurface(isDark: Boolean): Color = momentsStickerInk(isDark)

fun momentsStickerInverseInk(isDark: Boolean): Color = momentsStickerSurface(isDark)

fun normalizedTapCycleStickerVariant(styleVariant: Int, count: Int = 4): Int =
    ((styleVariant % count) + count) % count

fun momentsStickerRainbowGradientColors(): List<Color> = listOf(
    Color.fromHex("FF5F6D"),
    Color.fromHex("FF8C42"),
    Color.fromHex("FFD166"),
    Color.fromHex("6BCB77"),
    Color.fromHex("4D96FF"),
    Color.fromHex("9D4EDD"),
)

fun momentsStickerRainbowBrush(): Brush =
    Brush.horizontalGradient(momentsStickerRainbowGradientColors())

fun momentsTapCycleStickerBackground(isDark: Boolean, styleVariant: Int): Color {
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val surface = momentsStickerSurface(isDark)
    val ink = momentsStickerInk(isDark)
    return when (normalized) {
        1 -> ink
        2 -> surface.copy(alpha = if (isDark) 0.78f else 0.96f)
        3 -> if (isDark) surface.copy(alpha = 0.98f) else Color.White
        else -> surface
    }
}

/** Foreground sólido o rainbow — iOS `AnyShapeStyle`. */
sealed class MomentsTapCycleForeground {
    data class Solid(val color: Color) : MomentsTapCycleForeground()
    data class Rainbow(val brush: Brush) : MomentsTapCycleForeground()
}

fun momentsTapCycleStickerForeground(isDark: Boolean, styleVariant: Int): MomentsTapCycleForeground {
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val surface = momentsStickerSurface(isDark)
    val ink = momentsStickerInk(isDark)
    return when (normalized) {
        1 -> MomentsTapCycleForeground.Solid(surface)
        3 -> MomentsTapCycleForeground.Rainbow(momentsStickerRainbowBrush())
        else -> MomentsTapCycleForeground.Solid(ink)
    }
}

fun momentsTapCycleStickerStroke(isDark: Boolean, styleVariant: Int): Color {
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val ink = momentsStickerInk(isDark)
    return when (normalized) {
        2 -> ink.copy(alpha = if (isDark) 0.34f else 0.22f)
        3 -> Color.fromHex("FF5F6D").copy(alpha = if (isDark) 0.24f else 0.18f)
        else -> Color.Transparent
    }
}

fun momentsTapCycleStickerStrokeWidth(styleVariant: Int): Dp {
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    return if (normalized == 2 || normalized == 3) 1.25.dp else 0.dp
}

fun normalizedStickerURL(raw: String): Uri? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val direct = runCatching { Uri.parse(trimmed) }.getOrNull()
    val scheme = direct?.scheme?.lowercase()
    if (scheme == "https" || scheme == "http") return direct
    return runCatching { Uri.parse("https://$trimmed") }.getOrNull()
}

fun stickerHostLabel(raw: String): String {
    val url = normalizedStickerURL(raw) ?: return raw.trim()
    val host = url.host ?: raw
    return host.replace(Regex("^www\\."), "")
}

/**
 * Port de `linkStickerRenderingSize(for:)`.
 * [fallbackTitle] = `storyEditor.link.fallbackTitle` (pasar desde Compose con stringResource).
 */
fun linkStickerRenderingSize(title: String, fallbackTitle: String, context: Context): DpSize {
    val trimmed = title.trim()
    val measured = if (trimmed.isEmpty()) fallbackTitle else trimmed
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * context.resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val textWidth = ceil(paint.measureText(measured))
    val horizontalChrome = 18f + 18f + 12f + 18f
    val density = context.resources.displayMetrics.density
    val widthDp = min(max(textWidth + horizontalChrome, 118f), 280f) / density
    return DpSize(widthDp.dp, 50.dp)
}

fun emojiSliderHasPrompt(prompt: String): Boolean = prompt.trim().isNotEmpty()

fun emojiSliderRenderingSize(prompt: String = ""): DpSize =
    if (emojiSliderHasPrompt(prompt)) DpSize(260.dp, 110.dp) else DpSize(260.dp, 78.dp)

fun emojiSliderMomentsGradientColors(): List<Color> = listOf(
    Color(0xFF007AFF), // system blue
    Color(0xFFAF52DE), // system purple
    Color(0xFFFF2D55), // system pink
)

data class EmojiSliderTrackMetrics(
    val leading: Float,
    val width: Float,
    val thumbBaseSize: Float,
    val trackHeight: Float,
)

fun emojiSliderTrackMetrics(totalWidth: Float, scale: Float = 1f): EmojiSliderTrackMetrics {
    val thumbBaseSize = 48f * scale
    val horizontalInset = 16f * scale
    val trackWidth = max(totalWidth - (horizontalInset * 2) - thumbBaseSize, 1f)
    return EmojiSliderTrackMetrics(
        leading = horizontalInset + (thumbBaseSize / 2),
        width = trackWidth,
        thumbBaseSize = thumbBaseSize,
        trackHeight = 12f * scale,
    )
}

fun emojiSliderThumbSize(value: Double, baseSize: Float, scale: Float = 1f): Float {
    val clamped = min(max(value, 0.0), 1.0)
    return baseSize + (clamped * 22 * scale).toFloat()
}

data class EmojiSliderTrackFrame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

fun emojiSliderTrackFrame(
    totalWidth: Float,
    totalHeight: Float,
    showsPrompt: Boolean = true,
    scale: Float = 1f,
): EmojiSliderTrackFrame {
    val metrics = emojiSliderTrackMetrics(totalWidth, scale)
    val centerY = totalHeight * (if (showsPrompt) 0.62f else 0.52f)
    return EmojiSliderTrackFrame(
        x = metrics.leading,
        y = centerY - (metrics.trackHeight / 2),
        width = metrics.width,
        height = metrics.trackHeight,
    )
}

data class EmojiSliderPoint(val x: Float, val y: Float)

fun emojiSliderThumbCenter(
    totalWidth: Float,
    totalHeight: Float,
    value: Double,
    showsPrompt: Boolean = true,
    scale: Float = 1f,
): EmojiSliderPoint {
    val clamped = min(max(value, 0.0), 1.0).toFloat()
    val track = emojiSliderTrackFrame(totalWidth, totalHeight, showsPrompt, scale)
    return EmojiSliderPoint(x = track.x + (track.width * clamped), y = track.y + track.height / 2)
}

/** Port de `createEmojiSliderFallbackImage` (UIImage → Bitmap). */
fun createEmojiSliderFallbackImage(
    context: Context,
    prompt: String,
    emoji: String,
    value: Double = 0.5,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizeDp = emojiSliderRenderingSize(prompt)
    val widthPx = max((sizeDp.width.value * density).toInt(), 1)
    val heightPx = max((sizeDp.height.value * density).toInt(), 1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val corner = 24f * density
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((0.96f * 255).toInt(), 255, 255, 255)
        setShadowLayer(20f * density, 0f, 8f * density, android.graphics.Color.argb((0.12f * 255).toInt(), 0, 0, 0))
    }
    canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), corner, corner, cardPaint)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = android.graphics.Color.argb((0.06f * 255).toInt(), 0, 0, 0)
    }
    canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), corner, corner, strokePaint)

    val clampedValue = min(max(value, 0.0), 1.0)
    val showsPrompt = emojiSliderHasPrompt(prompt)
    val track = emojiSliderTrackFrame(widthPx.toFloat(), heightPx.toFloat(), showsPrompt)

    val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(237, 237, 237) }
    val trackRadius = track.height / 2f
    canvas.drawRoundRect(track.x, track.y, track.x + track.width, track.y + track.height, trackRadius, trackRadius, trackPaint)

    val fillW = max(track.width * clampedValue.toFloat(), track.height)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb((0.98f * 255).toInt(), (0.73f * 255).toInt(), (0.18f * 255).toInt())
    }
    canvas.drawRoundRect(track.x, track.y, track.x + fillW, track.y + track.height, trackRadius, trackRadius, fillPaint)

    val promptText = prompt.trim()
    if (promptText.isNotEmpty()) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((0.92f * 255).toInt(), 0, 0, 0)
            textSize = 15f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(promptText, widthPx / 2f, 15f * density + textPaint.textSize, textPaint)
    }

    val emojiString = emoji.trim().ifEmpty { "😍" }
    val thumbCenter = emojiSliderThumbCenter(widthPx.toFloat(), heightPx.toFloat(), clampedValue, showsPrompt)
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (28f + (clampedValue * 8).toFloat()) * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f * density, 0f, 4f * density, android.graphics.Color.argb((0.16f * 255).toInt(), 0, 0, 0))
    }
    val fm = emojiPaint.fontMetrics
    val textY = thumbCenter.y - (fm.ascent + fm.descent) / 2f
    canvas.drawText(emojiString, thumbCenter.x, textY, emojiPaint)
    return bitmap
}

fun countdownClockString(targetAtMs: Double, now: Date = Date()): String {
    val targetMs = targetAtMs
    val totalSeconds = max(((targetMs - now.time) / 1000.0).toInt(), 0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

// MARK: - NeutralStickerCard / NeutralStickerAccentPill

/** Port de `NeutralStickerCard`. */
@Composable
fun NeutralStickerCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(cornerRadius), ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(0.12f))
            .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(cornerRadius))
            .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(cornerRadius)),
        content = content,
    )
}

/**
 * Port de `NeutralStickerAccentPill`.
 * iOS usa SF Symbol; aquí [icon] es el ImageVector equivalente del call site.
 */
@Composable
fun NeutralStickerAccentPill(
    icon: ImageVector,
    title: String,
    fill: Color,
    foreground: Color = Color.White,
    usesUppercase: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(fill, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(11.dp))
        Text(
            text = if (usesUppercase) title.uppercase() else title,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (usesUppercase) 0.2.sp else 0.sp,
        )
    }
}

/** Port de `StickerCountdownDigitBox`. */
@Composable
fun StickerCountdownDigitBox(value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 26.dp, height = 32.dp)
            .background(Color.White.copy(0.18f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// MARK: - StickerPolaroidFrameView

private const val PolaroidImageViewportSize = 180f

/** Equivalente Compose de `StickerPolaroidFrameView`. */
@Composable
fun StickerPolaroidFrameView(
    image: Bitmap?,
    caption: String? = null,
    frameStyle: StoryPolaroidFrameStyle = StoryPolaroidFrameStyle.CLASSIC,
    contentScale: Float = 1f,
    contentOffsetX: Float = 0f,
    contentOffsetY: Float = 0f,
    progress: Float = 1f,
    /** Editor: caption editable en la franja blanca (sin TextField flotante). */
    isEditingContent: Boolean = false,
    onCaptionChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isShaking by remember { mutableStateOf(false) }
    var shakeJob by remember { mutableStateOf<Job?>(null) }
    var didReceiveProgressChange by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val captionFocus = remember { FocusRequester() }
    val shakeOpacity by animateFloatAsState(
        targetValue = if (isShaking) 1f else 0f,
        animationSpec = tween(durationMillis = if (isShaking) 250 else 450, easing = FastOutSlowInEasing),
        label = "polaroidShakeOpacity",
    )

    LaunchedEffect(isEditingContent) {
        if (isEditingContent && onCaptionChange != null) {
            runCatching { captionFocus.requestFocus() }
        }
    }

    LaunchedEffect(progress) {
        // iOS: onChange(of: progress) — no shake en el primer compose
        if (!didReceiveProgressChange) {
            didReceiveProgressChange = true
            return@LaunchedEffect
        }
        isShaking = true
        shakeJob?.cancel()
        shakeJob = scope.launch {
            delay(400)
            isShaking = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { shakeJob?.cancel() }
    }

    val frameColor = polaroidFrameColor(frameStyle)
    val framePadding = polaroidFramePadding(frameStyle)
    val outerCornerRadius = polaroidOuterCornerRadius(frameStyle)
    val outerShape = RoundedCornerShape(outerCornerRadius)
    val frameRotation = polaroidFrameRotation(frameStyle)

    Box(
        modifier = modifier
            .rotate(frameRotation)
            .shadow(
                elevation = polaroidFrameShadowRadius(frameStyle),
                shape = outerShape,
                spotColor = polaroidFrameShadowColor(frameStyle),
                ambientColor = polaroidFrameShadowColor(frameStyle),
            ),
    ) {
        Column(
            Modifier
                .background(frameColor, outerShape)
                .clip(outerShape)
                .border(
                    width = polaroidFrameBorderLineWidth(frameStyle),
                    color = polaroidFrameBorderStrokeColor(frameStyle),
                    shape = outerShape,
                ),
        ) {
            Box(
                Modifier
                    .background(frameColor)
                    .size((PolaroidImageViewportSize + framePadding.value * 2f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(PolaroidImageViewportSize.dp)
                        .clip(RectangleShape),
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(red = 0.08f, green = 0.09f, blue = 0.12f),
                                        Color(red = 0.04f, green = 0.04f, blue = 0.06f),
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(PolaroidImageViewportSize, PolaroidImageViewportSize),
                                ),
                            ),
                    )

                    if (progress < 1f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .drawBehind {
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.10f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.25f),
                                            ),
                                            start = Offset.Zero,
                                            end = Offset(size.width, size.height),
                                        ),
                                        blendMode = BlendMode.Overlay,
                                    )
                                },
                        )
                    }

                    image?.let { bitmap ->
                        val drawSize = polaroidFrameImageSize(
                            imageWidth = bitmap.width.toFloat(),
                            imageHeight = bitmap.height.toFloat(),
                            contentScale = contentScale,
                        )
                        val clampedOffset = polaroidClampedContentOffset(
                            drawSize = drawSize,
                            offsetX = contentOffsetX,
                            offsetY = contentOffsetY,
                        )
                        val imageAlpha = if (progress > 0.05f) {
                            min(1f, (progress - 0.05f) / 0.95f)
                        } else {
                            0f
                        }
                        // ≡ iOS `.frame(w,h)` + clip del viewport: la foto puede ser más
                        // grande que 180×180. `size()` respetaría maxConstraints del padre
                        // y aplastaría a cuadrado; `requiredSize` fuerza el aspect real.
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(drawSize.width.dp, drawSize.height.dp)
                                .offset(x = clampedOffset.x.dp, y = clampedOffset.y.dp)
                                .graphicsLayer {
                                    alpha = imageAlpha
                                    colorFilter = polaroidImageColorFilter(progress)
                                }
                                .blur(((1f - progress) * 16f).dp),
                        )
                    }

                    if (progress < 1f) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .drawBehind {
                                    drawRect(
                                        color = Color.White.copy(alpha = (1f - progress) * 0.18f),
                                        blendMode = BlendMode.Overlay,
                                    )
                                },
                        )
                    }
                }

                PolaroidImageViewportDecoration(
                    frameStyle = frameStyle,
                    framePadding = framePadding,
                )
            }

            Box(
                Modifier
                    .width(200.dp)
                    .height(40.dp)
                    .background(frameColor),
                contentAlignment = Alignment.Center,
            ) {
                if (isEditingContent && onCaptionChange != null) {
                    val captionValue = caption.orEmpty()
                    BasicTextField(
                        value = captionValue,
                        onValueChange = onCaptionChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = polaroidCaptionColor(frameStyle),
                            fontFamily = polaroidCaptionFontFamily(frameStyle),
                            fontWeight = polaroidCaptionFontWeight(frameStyle),
                            fontSize = polaroidCaptionFontSize(frameStyle),
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(polaroidCaptionColor(frameStyle)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .focusRequester(captionFocus)
                            .rotate(polaroidCaptionRotation(frameStyle))
                            .offset(y = polaroidCaptionVerticalOffset(frameStyle)),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (captionValue.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.story_editor_polaroid_add_note),
                                        color = polaroidCaptionColor(frameStyle).copy(alpha = 0.42f),
                                        fontFamily = polaroidCaptionFontFamily(frameStyle),
                                        fontWeight = polaroidCaptionFontWeight(frameStyle),
                                        fontSize = polaroidCaptionFontSize(frameStyle),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                } else {
                    caption?.takeIf { it.isNotEmpty() }?.let { text ->
                        val visibleCount = (text.length * progress).toInt()
                        Text(
                            text = buildAnnotatedString {
                                append(text.take(visibleCount))
                                withStyle(SpanStyle(color = Color.Transparent)) {
                                    append(text.drop(visibleCount))
                                }
                            },
                            color = polaroidCaptionColor(frameStyle),
                            fontFamily = polaroidCaptionFontFamily(frameStyle),
                            fontWeight = polaroidCaptionFontWeight(frameStyle),
                            fontSize = polaroidCaptionFontSize(frameStyle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .rotate(polaroidCaptionRotation(frameStyle))
                                .offset(y = polaroidCaptionVerticalOffset(frameStyle)),
                        )
                    }
                }
            }
        }

        PolaroidFrameStyleDecorationOverlay(
            frameStyle = frameStyle,
            outerCornerRadius = outerCornerRadius,
            modifier = Modifier.matchParentSize(),
        )

        if (progress < 1f) {
            PolaroidParticleOverlay(
                progress = progress,
                shakeOpacity = shakeOpacity,
                modifier = Modifier
                    .matchParentSize()
                    .rawPadding((-36).dp),
            )
        }
    }
}

@Composable
private fun BoxScope.PolaroidImageViewportDecoration(
    frameStyle: StoryPolaroidFrameStyle,
    framePadding: Dp,
) {
    when (frameStyle) {
        StoryPolaroidFrameStyle.VINTAGE -> {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(4.dp)
                        .border(
                            width = 1.dp,
                            color = Color(red = 0.38f, green = 0.29f, blue = 0.19f).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Transparent,
                                    Color(red = 0.42f, green = 0.28f, blue = 0.08f).copy(alpha = 0.06f),
                                ),
                                start = Offset.Zero,
                                end = Offset(PolaroidImageViewportSize, PolaroidImageViewportSize),
                            ),
                        ),
                )
                Column(
                    Modifier
                        .matchParentSize()
                        .padding(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(30.dp)
                                .height(1.dp)
                                .background(Color(red = 0.46f, green = 0.33f, blue = 0.19f).copy(alpha = 0.10f)),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(1.dp)
                                .background(Color(red = 0.36f, green = 0.27f, blue = 0.18f).copy(alpha = 0.08f)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .background(Color(red = 0.36f, green = 0.27f, blue = 0.18f).copy(alpha = 0.09f)),
                        )
                    }
                }
            }
        }

        StoryPolaroidFrameStyle.ALBUM -> {
            val paddedWidth = PolaroidImageViewportSize + (framePadding.value * 2f)
            val paddedHeight = PolaroidImageViewportSize + (framePadding.value * 2f)
            Box(Modifier.matchParentSize()) {
                StoryPolaroidCornerAccent(
                    rotationDegrees = 0f,
                    modifier = Modifier.offset(x = 9.dp, y = 9.dp),
                )
                StoryPolaroidCornerAccent(
                    rotationDegrees = 90f,
                    modifier = Modifier.offset(x = (paddedWidth - 16f).dp - 7.dp, y = 9.dp),
                )
                StoryPolaroidCornerAccent(
                    rotationDegrees = -90f,
                    modifier = Modifier.offset(x = 9.dp, y = (paddedHeight - 16f).dp - 7.dp),
                )
                StoryPolaroidCornerAccent(
                    rotationDegrees = 180f,
                    modifier = Modifier.offset(
                        x = (paddedWidth - 16f).dp - 7.dp,
                        y = (paddedHeight - 16f).dp - 7.dp,
                    ),
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun PolaroidFrameStyleDecorationOverlay(
    frameStyle: StoryPolaroidFrameStyle,
    outerCornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(outerCornerRadius)
    when (frameStyle) {
        StoryPolaroidFrameStyle.VINTAGE -> {
            Box(modifier.clip(shape)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color(red = 0.36f, green = 0.26f, blue = 0.14f).copy(alpha = 0.10f),
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height),
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(outerCornerRadius.toPx()),
                                blendMode = BlendMode.Multiply,
                            )
                        },
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(0.6.dp)
                        .border(
                            width = 1.6.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(red = 0.44f, green = 0.33f, blue = 0.20f).copy(alpha = 0.12f),
                                    Color.Transparent,
                                    Color(red = 0.30f, green = 0.22f, blue = 0.14f).copy(alpha = 0.18f),
                                ),
                                start = Offset.Zero,
                                end = Offset(PolaroidImageViewportSize, PolaroidImageViewportSize),
                            ),
                            shape = shape,
                        ),
                )
                VintageWearOverlay(
                    cornerRadius = outerCornerRadius,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(2.dp),
                )
            }
        }

        StoryPolaroidFrameStyle.ALBUM -> {
            Box(
                modifier
                    .padding(2.dp)
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.45f),
                        shape = shape,
                    ),
            )
        }

        else -> Unit
    }
}

@Composable
private fun PolaroidParticleOverlay(
    progress: Float,
    shakeOpacity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = shakeOpacity
        },
    ) {
        var rng = StickerSeededRandom(seed = 77)
        val area = max(size.width * size.height, 1f)
        val particleCount = min(max((area / 110f).toInt(), 90), 320)
        val timeFactor = progress * 30f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxDist = sqrt(centerX * centerX + centerY * centerY)

        repeat(particleCount) {
            val baseX = rng.next().toFloat() * size.width
            val baseY = rng.next().toFloat() * size.height
            val speedX = rng.next().toFloat() * 3.5f + 1.5f
            val speedY = rng.next().toFloat() * 4f + 2f
            val driftPhase = rng.next().toFloat() * Math.PI.toFloat() * 2f
            val offsetX = sin(timeFactor * 0.25f * speedX + driftPhase) * 22f
            val offsetY = cos(timeFactor * 0.18f * speedY + driftPhase) * 26f
            val x = baseX + offsetX
            val y = baseY + offsetY
            val dotSize = rng.next().toFloat() * 2.5f + 1f
            val dx = x - centerX
            val dy = y - centerY
            val dist = sqrt(dx * dx + dy * dy)
            val edgeFade = max(0f, min(1f, 1f - (dist / maxDist).pow(2.5f)))
            val opacity = (0.28f + rng.next().toFloat() * 0.42f) * (1f - progress) * edgeFade
            drawCircle(
                color = Color.White.copy(alpha = opacity),
                radius = dotSize / 2f,
                center = Offset(x + dotSize / 2f, y + dotSize / 2f),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

@Composable
private fun StoryPolaroidCornerAccent(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(14.dp)
            .rotate(rotationDegrees),
    ) {
        val path = Path().apply {
            moveTo(0f, 14.dp.toPx())
            lineTo(0f, 0f)
            lineTo(14.dp.toPx(), 0f)
        }
        drawPath(
            path = path,
            color = Color.Black.copy(alpha = 0.16f),
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun VintageWearOverlay(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
    ) {
        val width = maxWidth
        val height = maxHeight
        Box(
            Modifier
                .size(18.dp)
                .offset(x = 12.dp, y = 14.dp)
                .background(Color(red = 0.48f, green = 0.37f, blue = 0.23f).copy(alpha = 0.05f), CircleShape),
        )
        Box(
            Modifier
                .size(14.dp)
                .offset(x = width - 14.dp - 14.dp, y = height - 16.dp - 14.dp)
                .background(Color(red = 0.30f, green = 0.22f, blue = 0.14f).copy(alpha = 0.04f), CircleShape),
        )
        Box(
            Modifier
                .size(width = 22.dp, height = 1.2.dp)
                .rotate(-18f)
                .offset(x = width - 28.dp - 11.dp, y = 18.dp)
                .background(Color(red = 0.40f, green = 0.28f, blue = 0.16f).copy(alpha = 0.05f), RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .size(width = 16.dp, height = 1.2.dp)
                .rotate(24f)
                .offset(x = 20.dp, y = height - 20.dp - 0.6.dp)
                .background(Color(red = 0.30f, green = 0.22f, blue = 0.14f).copy(alpha = 0.04f), RoundedCornerShape(50)),
        )
    }
}

private class StickerSeededRandom(seed: Int) {
    private var state: ULong = abs(seed).toULong()

    fun next(): Double {
        state = state + 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return ((z xor (z shr 31)).toDouble()) / ULong.MAX_VALUE.toDouble()
    }
}

private fun polaroidFrameImageSize(
    imageWidth: Float,
    imageHeight: Float,
    contentScale: Float,
): Size {
    val safeScale = max(contentScale, 1f)
    val imageRatio = imageWidth / max(imageHeight, 0.0001f)
    val viewportRatio = PolaroidImageViewportSize / PolaroidImageViewportSize
    val baseSize = if (imageRatio > viewportRatio) {
        Size(PolaroidImageViewportSize * imageRatio, PolaroidImageViewportSize)
    } else {
        Size(PolaroidImageViewportSize, PolaroidImageViewportSize / max(imageRatio, 0.0001f))
    }
    return Size(baseSize.width * safeScale, baseSize.height * safeScale)
}

private fun polaroidClampedContentOffset(
    drawSize: Size,
    offsetX: Float,
    offsetY: Float,
): Offset {
    val maxOffsetX = max(0f, (drawSize.width - PolaroidImageViewportSize) / 2f)
    val maxOffsetY = max(0f, (drawSize.height - PolaroidImageViewportSize) / 2f)
    return Offset(
        offsetX.coerceIn(-maxOffsetX, maxOffsetX),
        offsetY.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

private fun polaroidImageColorFilter(progress: Float): ColorFilter {
    val brightness = (progress - 1f) * 0.42f
    val contrast = 0.55f + progress * 0.45f
    val rMul = 1f
    val gMul = 0.88f + 0.12f * progress
    val bMul = 0.62f + 0.38f * progress
    val contrastTranslate = 128f * (1f - contrast) + brightness * 255f
    val matrix = ColorMatrix(
        floatArrayOf(
            contrast * rMul, 0f, 0f, 0f, contrastTranslate * rMul,
            0f, contrast * gMul, 0f, 0f, contrastTranslate * gMul,
            0f, 0f, contrast * bMul, 0f, contrastTranslate * bMul,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    return ColorFilter.colorMatrix(matrix)
}

private fun polaroidFrameColor(style: StoryPolaroidFrameStyle): Color = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> Color.White
    StoryPolaroidFrameStyle.CLEAN -> Color.White.copy(alpha = 0.94f)
    StoryPolaroidFrameStyle.VINTAGE -> Color(red = 0.95f, green = 0.91f, blue = 0.82f)
    StoryPolaroidFrameStyle.ALBUM -> Color(red = 0.985f, green = 0.965f, blue = 0.93f)
}

private fun polaroidFramePadding(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> 10.dp
    StoryPolaroidFrameStyle.CLEAN -> 8.dp
    StoryPolaroidFrameStyle.VINTAGE -> 13.dp
    StoryPolaroidFrameStyle.ALBUM -> 12.dp
}

private fun polaroidOuterCornerRadius(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> 0.dp
    StoryPolaroidFrameStyle.CLEAN -> 18.dp
    StoryPolaroidFrameStyle.VINTAGE -> 4.dp
    StoryPolaroidFrameStyle.ALBUM -> 20.dp
}

private fun polaroidCaptionFontSize(style: StoryPolaroidFrameStyle) = when (style) {
    StoryPolaroidFrameStyle.CLEAN, StoryPolaroidFrameStyle.VINTAGE -> 18.sp
    StoryPolaroidFrameStyle.ALBUM -> 17.sp
    StoryPolaroidFrameStyle.CLASSIC -> 21.sp
}

private fun polaroidCaptionFontFamily(style: StoryPolaroidFrameStyle): FontFamily = when (style) {
    StoryPolaroidFrameStyle.VINTAGE -> FontFamily.Serif
    StoryPolaroidFrameStyle.CLASSIC -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

private fun polaroidCaptionFontWeight(style: StoryPolaroidFrameStyle): FontWeight = when (style) {
    StoryPolaroidFrameStyle.CLEAN, StoryPolaroidFrameStyle.ALBUM -> FontWeight.SemiBold
    StoryPolaroidFrameStyle.VINTAGE -> FontWeight.Medium
    StoryPolaroidFrameStyle.CLASSIC -> FontWeight.Medium
}

private fun polaroidCaptionColor(style: StoryPolaroidFrameStyle): Color = when (style) {
    StoryPolaroidFrameStyle.VINTAGE -> Color(red = 0.22f, green = 0.18f, blue = 0.14f).copy(alpha = 0.82f)
    StoryPolaroidFrameStyle.ALBUM -> Color.Black.copy(alpha = 0.78f)
    else -> Color.Black.copy(alpha = 0.85f)
}

private fun polaroidCaptionRotation(style: StoryPolaroidFrameStyle): Float = when (style) {
    StoryPolaroidFrameStyle.CLEAN, StoryPolaroidFrameStyle.ALBUM -> 0f
    else -> -1f
}

private fun polaroidCaptionVerticalOffset(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLEAN -> (-1).dp
    StoryPolaroidFrameStyle.ALBUM -> 0.dp
    else -> (-2).dp
}

private fun polaroidFrameRotation(style: StoryPolaroidFrameStyle): Float = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> -2f
    StoryPolaroidFrameStyle.CLEAN -> 0f
    StoryPolaroidFrameStyle.VINTAGE -> -1.4f
    StoryPolaroidFrameStyle.ALBUM -> 0.35f
}

private fun polaroidFrameShadowColor(style: StoryPolaroidFrameStyle): Color = when (style) {
    StoryPolaroidFrameStyle.CLEAN -> Color.Black.copy(alpha = 0.14f)
    StoryPolaroidFrameStyle.VINTAGE -> Color(red = 0.18f, green = 0.13f, blue = 0.09f).copy(alpha = 0.22f)
    else -> Color.Black.copy(alpha = 0.2f)
}

private fun polaroidFrameShadowRadius(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLEAN -> 14.dp
    StoryPolaroidFrameStyle.VINTAGE -> 6.dp
    else -> 8.dp
}

private fun polaroidFrameShadowYOffset(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLEAN -> 7.dp
    StoryPolaroidFrameStyle.VINTAGE -> 5.dp
    else -> 4.dp
}

private fun polaroidFrameBorderStrokeColor(style: StoryPolaroidFrameStyle): Color = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> Color.Transparent
    StoryPolaroidFrameStyle.CLEAN -> Color.Black.copy(alpha = 0.06f)
    StoryPolaroidFrameStyle.VINTAGE -> Color(red = 0.48f, green = 0.38f, blue = 0.27f).copy(alpha = 0.24f)
    StoryPolaroidFrameStyle.ALBUM -> Color.Black.copy(alpha = 0.08f)
}

private fun polaroidFrameBorderLineWidth(style: StoryPolaroidFrameStyle): Dp = when (style) {
    StoryPolaroidFrameStyle.CLASSIC -> 0.dp
    StoryPolaroidFrameStyle.VINTAGE -> 1.2.dp
    else -> 1.dp
}

/** Helpers Compose: surface/ink según tema actual. */
@Composable
fun rememberMomentsStickerSurface(): Color = momentsStickerSurface(isSystemInDarkTheme())

@Composable
fun rememberMomentsStickerInk(): Color = momentsStickerInk(isSystemInDarkTheme())

@Composable
fun linkStickerFallbackTitle(): String = stringResource(R.string.story_editor_link_fallback_title)

// MARK: - StickerLinkCardView / Hashtag / Time / Countdown helpers

@Composable
private fun TapCycleForegroundText(
    text: String,
    foreground: MomentsTapCycleForeground,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    maxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    when (foreground) {
        is MomentsTapCycleForeground.Solid -> Text(
            text = text,
            color = foreground.color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            maxLines = maxLines,
            modifier = modifier,
        )
        is MomentsTapCycleForeground.Rainbow -> Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            maxLines = maxLines,
            modifier = modifier
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = foreground.brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
                    }
                },
        )
    }
}

/** Port de `StickerLinkCardView`. */
@Composable
fun StickerLinkCardView(
    title: String,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    Row(
        modifier = modifier
            .height(50.dp)
            .background(bg, RoundedCornerShape(percent = 50))
            .then(if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconTint = when (foreground) {
            is MomentsTapCycleForeground.Solid -> foreground.color
            is MomentsTapCycleForeground.Rainbow -> Color.fromHex("FF5F6D")
        }
        Icon(
            imageVector = Icons.Filled.Link,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        TapCycleForegroundText(
            text = title.uppercase(),
            foreground = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

/** Port de `StickerHashtagCardView` (lectura + edición inline). */
@Composable
fun StickerHashtagCardView(
    hashtag: String,
    onHashtagChange: ((String) -> Unit)? = null,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    val ink = momentsStickerInk(isDark)
    val focusRequester = remember { FocusRequester() }
    val placeholder = stringResource(R.string.story_editor_hashtag_placeholder)

    LaunchedEffect(isEditingInline) {
        if (isEditingInline) {
            kotlinx.coroutines.delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .then(if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hashFg = if (normalized == 3) {
            foreground
        } else {
            MomentsTapCycleForeground.Solid(ink.copy(alpha = 0.58f))
        }
        TapCycleForegroundText(
            text = "#",
            foreground = hashFg,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.graphicsLayer { alpha = if (normalized == 3) 1f else 0.7f },
        )
        if (isEditingInline && onHashtagChange != null) {
            val fieldColor = when (foreground) {
                is MomentsTapCycleForeground.Solid -> foreground.color
                is MomentsTapCycleForeground.Rainbow -> ink
            }
            BasicTextField(
                value = hashtag,
                onValueChange = onHashtagChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = fieldColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                ),
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 260.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (hashtag.isEmpty()) {
                            Text(placeholder, color = fieldColor.copy(alpha = 0.45f), fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        inner()
                    }
                },
            )
        } else {
            TapCycleForegroundText(
                text = hashtag.uppercase(),
                foreground = foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Visual de `InteractiveMentionSticker` (sin botón).
 * El editor lo usa con hit-testing del contenedor; el viewer envuelve con `clickable`.
 */
@Composable
fun StickerMentionCardView(
    username: String,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val ink = momentsStickerInk(isDark)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .then(if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val atFg = if (normalized == 3) {
            foreground
        } else {
            MomentsTapCycleForeground.Solid(ink.copy(alpha = 0.58f))
        }
        TapCycleForegroundText(
            text = "@",
            foreground = atFg,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.graphicsLayer { alpha = if (normalized == 3) 1f else 0.7f },
        )
        TapCycleForegroundText(
            text = username.uppercase(),
            foreground = foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

/**
 * Visual de `InteractiveLocationSticker` (sin botón / sin mapa).
 * Usa `momentsTapCycleSticker*` + `AttachmentIconView` como iOS.
 */
@Composable
fun StickerLocationCardView(
    locationName: String,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    val iconTint = when (foreground) {
        is MomentsTapCycleForeground.Solid -> foreground.color
        is MomentsTapCycleForeground.Rainbow -> Color.fromHex("FF5F6D")
    }
    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .then(if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AttachmentIconView(
            icon = AttachmentIcon.LOCATION,
            preset = AttachmentIconPreset.STORY_LOCATION_STICKER,
            tintColor = iconTint,
        )
        TapCycleForegroundText(
            text = locationName.uppercase(),
            foreground = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
    }
}

/** Port de `StickerTimeCardView`. */
@Composable
fun StickerTimeCardView(
    timeText: String,
    dateText: String,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val foreground = momentsTapCycleStickerForeground(isDark, styleVariant)
    val ink = momentsStickerInk(isDark)
    val normalized = normalizedTapCycleStickerVariant(styleVariant)
    val bg = momentsTapCycleStickerBackground(isDark, styleVariant)
    val stroke = momentsTapCycleStickerStroke(isDark, styleVariant)
    val strokeW = momentsTapCycleStickerStrokeWidth(styleVariant)
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .then(if (strokeW > 0.dp) Modifier.border(strokeW, stroke, RoundedCornerShape(percent = 50)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TapCycleForegroundText(timeText, foreground, 26.sp, FontWeight.ExtraBold)
        val dateFg = if (normalized == 3) foreground else MomentsTapCycleForeground.Solid(ink.copy(alpha = 0.58f))
        TapCycleForegroundText(dateText.uppercase(), dateFg, 12.sp, FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Port de `CountdownComponents` + `getCountdownComponents`. */
data class CountdownComponents(
    val days: String,
    val hours: String,
    val minutes: String,
    val seconds: String,
)

fun getCountdownComponents(targetAtMs: Double, now: Date = Date()): CountdownComponents {
    val totalSeconds = max(((targetAtMs - now.time) / 1000.0).toInt(), 0)
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return CountdownComponents(
        days = String.format("%02d", days),
        hours = String.format("%02d", hours),
        minutes = String.format("%02d", minutes),
        seconds = String.format("%02d", seconds),
    )
}

/**
 * Ancho del card = 4 timers + gaps + inset lateral (paridad con iOS `CountdownCardLayout`).
 * Sin huecos laterales sobrantes: el título vive dentro de ese ancho (máx. 2 líneas).
 */
object CountdownCardLayout {
    val segmentSize = 52.dp
    val segmentSpacing = 8.dp
    val sideInset = 12.dp
    const val titleMaxChars = 48
    val timersWidth = segmentSize * 4 + segmentSpacing * 3
    val cardWidth = timersWidth + sideInset * 2
}

/** Port de `CountdownSegment`. */
@Composable
fun CountdownSegment(
    value: String,
    label: String,
    ink: Color,
    boxBg: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(CountdownCardLayout.segmentSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(CountdownCardLayout.segmentSize)
                .background(boxBg, RoundedCornerShape(12.dp))
                .border(0.5.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        Text(
            label.uppercase(),
            color = ink.copy(alpha = 0.64f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Port de `momentsCardStickerBackgroundGradient`. */
@Composable
fun MomentsCardStickerBackgroundGradient(
    styleVariant: Int,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val normalized = styleVariant % 6
    when (normalized) {
        1 -> Box(
            modifier.background(
                Brush.linearGradient(listOf(Color.fromHex("FF5F6D"), Color.fromHex("FFC371"))),
            ),
        )
        2 -> Box(
            modifier.background(
                Brush.linearGradient(listOf(Color.fromHex("9D4EDD"), Color.fromHex("FF70A6"))),
            ),
        )
        3 -> Box(
            modifier.background(
                Brush.linearGradient(listOf(Color.fromHex("4A00E0"), Color.fromHex("8E2DE2"))),
            ),
        )
        4 -> Box(
            modifier.background(
                Brush.linearGradient(listOf(Color.fromHex("00B09B"), Color.fromHex("96C93D"))),
            ),
        )
        5 -> Box(
            modifier.background(
                Brush.linearGradient(listOf(Color.fromHex("1E293B"), Color.fromHex("0F172A"))),
            ),
        )
        else -> Box(
            modifier.background(if (isDark) Color.fromHex("1C2529") else Color.White),
        )
    }
}

/** Port de `momentsCardStickerTextColor`. */
fun momentsCardStickerTextColor(styleVariant: Int, isDark: Boolean): Color {
    val normalized = styleVariant % 6
    if (normalized == 0) {
        return if (isDark) Color.fromHex("FAF9F6") else Color.fromHex("0B1215")
    }
    return Color.White
}

/** Port de `AnimatedMomentsCardStickerSurface`. */
@Composable
fun AnimatedMomentsCardStickerSurface(
    styleVariant: Int,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    var previousVariant by remember { mutableStateOf(styleVariant) }
    val overlayOpacity = remember { Animatable(1f) }
    var isFirst by remember { mutableStateOf(true) }

    LaunchedEffect(styleVariant) {
        if (isFirst) {
            isFirst = false
            previousVariant = styleVariant
            overlayOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        overlayOpacity.snapTo(0f)
        overlayOpacity.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        previousVariant = styleVariant
    }

    Box(modifier) {
        MomentsCardStickerBackgroundGradient(
            styleVariant = previousVariant,
            isDark = isDark,
            modifier = Modifier.fillMaxSize(),
        )
        MomentsCardStickerBackgroundGradient(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayOpacity.value },
        )
    }
}

/** Port de `AnimatedMomentsCardStickerHeaderSurface`. */
@Composable
fun AnimatedMomentsCardStickerHeaderSurface(
    styleVariant: Int,
    isDark: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    var previousVariant by remember { mutableStateOf(styleVariant) }
    val overlayOpacity = remember { Animatable(1f) }
    var isFirst by remember { mutableStateOf(true) }

    LaunchedEffect(styleVariant) {
        if (isFirst) {
            isFirst = false
            previousVariant = styleVariant
            overlayOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        overlayOpacity.snapTo(0f)
        overlayOpacity.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
        previousVariant = styleVariant
    }

    fun headerColor(variant: Int): Color {
        val isLight = variant % 6 == 0
        return if (isLight) momentsStickerInverseSurface(isDark) else Color.White.copy(alpha = 0.12f)
    }

    Box(modifier) {
        Box(Modifier.fillMaxSize().background(headerColor(previousVariant)))
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayOpacity.value }
                .background(headerColor(styleVariant)),
        )
    }
}

/**
 * Port de `StickerCountdownCardView`.
 * [onTitleChange]/[onTargetAtMsChange] solo se usan con [isEditingInline] = true.
 */
@Composable
fun StickerCountdownCardView(
    title: String,
    targetAtMs: Double,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    onTitleChange: ((String) -> Unit)? = null,
    onTargetAtMsChange: ((Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val headerInk = if (isLight) momentsStickerInverseInk(isDark) else Color.White
    var now by remember { mutableStateOf(Date()) }
    val eventTitlePlaceholder = stringResource(R.string.story_editor_countdown_event_title)
    val placeholder = stringResource(R.string.story_editor_countdown_placeholder)
    val labelDays = stringResource(R.string.story_editor_countdown_days)
    val labelHours = stringResource(R.string.story_editor_countdown_hours)
    val labelMinutes = stringResource(R.string.story_editor_countdown_minutes)
    val labelSeconds = stringResource(R.string.story_editor_countdown_seconds)

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            now = Date()
        }
    }

    fun openDatePicker() {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = targetAtMs.toLong().coerceAtLeast(System.currentTimeMillis())
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                        cal.set(java.util.Calendar.MINUTE, minute)
                        cal.set(java.util.Calendar.SECOND, 0)
                        val minMs = System.currentTimeMillis() + 60_000L
                        onTargetAtMsChange?.invoke(maxOf(cal.timeInMillis, minMs).toDouble())
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true,
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    Box(
        modifier = modifier
            .width(CountdownCardLayout.cardWidth)
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.width(CountdownCardLayout.cardWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                AnimatedMomentsCardStickerHeaderSurface(
                    styleVariant = styleVariant,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize(),
                )
                if (isEditingInline && onTitleChange != null) {
                    BasicTextField(
                        value = title,
                        onValueChange = { onTitleChange(it.take(CountdownCardLayout.titleMaxChars)) },
                        maxLines = 2,
                        textStyle = TextStyle(
                            color = headerInk,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CountdownCardLayout.sideInset,
                                vertical = 10.dp,
                            ),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (title.isEmpty()) {
                                    Text(
                                        eventTitlePlaceholder,
                                        color = headerInk.copy(alpha = 0.45f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                } else {
                    Text(
                        text = if (title.isEmpty()) placeholder else title.uppercase(),
                        color = headerInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = CountdownCardLayout.sideInset,
                                vertical = 10.dp,
                            ),
                    )
                }
            }

            val comps = getCountdownComponents(targetAtMs, now)
            val boxBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.15f)
            Row(
                modifier = Modifier
                    .clickable(enabled = isEditingInline) {
                        HapticManager.shared.mediumImpact()
                        openDatePicker()
                    }
                    .padding(
                        horizontal = CountdownCardLayout.sideInset,
                        vertical = 14.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(CountdownCardLayout.segmentSpacing),
            ) {
                CountdownSegment(comps.days, labelDays, ink, boxBg)
                CountdownSegment(comps.hours, labelHours, ink, boxBg)
                CountdownSegment(comps.minutes, labelMinutes, ink, boxBg)
                CountdownSegment(comps.seconds, labelSeconds, ink, boxBg)
            }
        }
    }
}

/**
 * Port de `StickerEmojiSliderCardView`.
 * [onPromptChange] solo con [isEditingInline] = true.
 */
@Composable
fun StickerEmojiSliderCardView(
    prompt: String,
    emoji: String,
    value: Double,
    averageValue: Double? = null,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    onPromptChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val textColor = momentsCardStickerTextColor(styleVariant, isDark)
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val clampedValue = min(max(value, 0.0), 1.0)
    val questionPrompt = stringResource(R.string.story_editor_slider_question_prompt)
    val showsPrompt = emojiSliderHasPrompt(prompt) || isEditingInline
    val size = emojiSliderRenderingSize(prompt = if (showsPrompt) questionPrompt else "")
    val w = size.width.value
    val h = size.height.value
    val metrics = emojiSliderTrackMetrics(w)
    val trackFrame = emojiSliderTrackFrame(w, h, showsPrompt)
    val thumbCenter = emojiSliderThumbCenter(w, h, clampedValue, showsPrompt)
    val thumbSize = emojiSliderThumbSize(clampedValue, metrics.thumbBaseSize)
    val thumbScale = 1f + (clampedValue * 0.15).toFloat()
    val animatedScale by animateFloatAsState(
        targetValue = if (MotionPolicy.reduceMotion) 1f else thumbScale,
        animationSpec = if (MotionPolicy.reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 400f)
        },
        label = "emojiThumbScale",
    )

    Box(
        modifier = modifier
            .size(size.width, size.height)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.fillMaxSize(),
        )

        if (showsPrompt) {
            if (isEditingInline && onPromptChange != null) {
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = textColor.copy(alpha = 0.92f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .width(size.width - 32.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (prompt.isEmpty()) {
                                Text(
                                    questionPrompt,
                                    color = textColor.copy(alpha = 0.45f),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            inner()
                        }
                    },
                )
            } else {
                Text(
                    text = prompt,
                    color = textColor.copy(alpha = 0.92f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .width(size.width - 32.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp),
                )
            }
        }

        Box(
            Modifier
                .offset(x = trackFrame.x.dp, y = trackFrame.y.dp)
                .size(trackFrame.width.dp, trackFrame.height.dp)
                .background(ink.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)),
        )
        val fillW = max(trackFrame.width * clampedValue.toFloat(), trackFrame.height)
        Box(
            Modifier
                .offset(x = trackFrame.x.dp, y = trackFrame.y.dp)
                .size(fillW.dp, trackFrame.height.dp)
                .background(ink.copy(alpha = 0.22f), RoundedCornerShape(percent = 50)),
        )

        averageValue?.let { avg ->
            val avgClamped = min(max(avg, 0.0), 1.0)
            val avgCenter = emojiSliderThumbCenter(w, h, avgClamped, showsPrompt)
            Box(
                Modifier
                    .offset(x = (avgCenter.x - 14).dp, y = (avgCenter.y - 14).dp)
                    .size(28.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ink.copy(alpha = 0.35f), Color.Transparent),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(11.dp)
                        .shadow(4.dp, CircleShape, spotColor = Color(0xFFAF52DE).copy(alpha = 0.5f))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0.3f, 0.1f, 0.5f), Color.Black.copy(alpha = 0.8f)),
                            ),
                            shape = CircleShape,
                        )
                        .border(
                            1.dp,
                            if (isLight) momentsStickerSurface(isDark).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f),
                            CircleShape,
                        ),
                )
            }
        }

        Text(
            text = emoji,
            fontSize = (28 + (clampedValue * 14)).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .offset(
                    x = (thumbCenter.x - thumbSize / 2).dp,
                    y = (thumbCenter.y - thumbSize / 2).dp,
                )
                .size(thumbSize.dp)
                .wrapContentSize(unbounded = true, align = Alignment.Center)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                },
        )
    }
}

private val quizOptionLetters = listOf("A", "B", "C", "D")

/**
 * Port de `StickerQuizCardView`.
 * Callbacks de edición solo con [isEditingInline] = true.
 */
@Composable
fun StickerQuizCardView(
    question: String,
    options: List<String>,
    selectedIndex: Int?,
    correctIndex: Int?,
    onSelect: (Int) -> Unit,
    styleVariant: Int = 0,
    isEditingInline: Boolean = false,
    onQuestionChange: ((String) -> Unit)? = null,
    onOptionsChange: ((List<String>) -> Unit)? = null,
    onCorrectIndexChange: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val isLight = styleVariant % 6 == 0
    val textColor = momentsCardStickerTextColor(styleVariant, isDark)
    val headerInk = if (isLight) momentsStickerInverseInk(isDark) else Color.White
    val questionEditPlaceholder = stringResource(R.string.story_editor_quiz_question_prompt)
    val questionPlaceholder = stringResource(R.string.sticker_quiz_question_placeholder)
    val optionPrompt = stringResource(R.string.story_editor_quiz_option_prompt)
    val addOptionLabel = stringResource(R.string.sticker_quiz_add_option)

    Box(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = styleVariant,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Column(horizontalAlignment = Alignment.Start) {
            Box(Modifier.fillMaxWidth()) {
                AnimatedMomentsCardStickerHeaderSurface(
                    styleVariant = styleVariant,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize(),
                )
                if (isEditingInline && onQuestionChange != null) {
                    BasicTextField(
                        value = question,
                        onValueChange = onQuestionChange,
                        textStyle = TextStyle(
                            color = headerInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (question.isEmpty()) {
                                    Text(
                                        questionEditPlaceholder,
                                        color = headerInk.copy(alpha = 0.45f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                } else {
                    Text(
                        text = if (question.isEmpty()) questionPlaceholder else question,
                        color = headerInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEachIndexed { index, option ->
                    QuizOptionRow(
                        index = index,
                        optionText = option,
                        selectedIndex = selectedIndex,
                        correctIndex = correctIndex,
                        styleVariant = styleVariant,
                        isDark = isDark,
                        isEditingInline = isEditingInline,
                        optionPrompt = optionPrompt,
                        onSelect = onSelect,
                        onCorrectIndexChange = onCorrectIndexChange,
                        onOptionTextChange = { newValue ->
                            if (onOptionsChange != null && index < options.size) {
                                onOptionsChange(options.toMutableList().also { it[index] = newValue })
                            }
                        },
                    )
                }

                if (isEditingInline && options.size < 4 && onOptionsChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOptionsChange(options + "")
                                HapticManager.shared.selection()
                            }
                            .background(
                                textColor.copy(alpha = if (isLight) 0.08f else 0.14f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddCircle,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            addOptionLabel,
                            color = textColor.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizOptionRow(
    index: Int,
    optionText: String,
    selectedIndex: Int?,
    correctIndex: Int?,
    styleVariant: Int,
    isDark: Boolean,
    isEditingInline: Boolean,
    optionPrompt: String,
    onSelect: (Int) -> Unit,
    onCorrectIndexChange: ((Int?) -> Unit)?,
    onOptionTextChange: (String) -> Unit,
) {
    val isSelected = selectedIndex == index
    val isCorrect = correctIndex == index
    val hasVoted = selectedIndex != null
    val letter = quizOptionLetters.getOrNull(index) ?: "${index + 1}"

    if (isEditingInline) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    optionBgColor(styleVariant, isDark, hasVoted = true, isCorrect = correctIndex == index, isSelected = false),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clickable {
                        onCorrectIndexChange?.invoke(index)
                        HapticManager.shared.heavyImpact()
                    }
                    .background(
                        optionCircleColor(styleVariant, isDark, hasVoted = true, isCorrect = correctIndex == index, isSelected = false),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = optionLetterColor(styleVariant, isDark, hasVoted = true, isCorrect = correctIndex == index, isSelected = false),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            BasicTextField(
                value = optionText,
                onValueChange = onOptionTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = optionTextColor(styleVariant, isDark, hasVoted = false, isCorrect = false, isSelected = false),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (optionText.isEmpty()) {
                            Text(
                                "$optionPrompt ${index + 1}...",
                                color = optionTextColor(styleVariant, isDark, false, false, false).copy(alpha = 0.45f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    } else {
        QuizOptionButton(
            enabled = !hasVoted,
            onClick = { onSelect(index) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        optionBgColor(styleVariant, isDark, hasVoted, isCorrect, isSelected),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            optionCircleColor(styleVariant, isDark, hasVoted, isCorrect, isSelected),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        color = optionLetterColor(styleVariant, isDark, hasVoted, isCorrect, isSelected),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    optionText,
                    color = optionTextColor(styleVariant, isDark, hasVoted, isCorrect, isSelected),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (hasVoted) {
                    when {
                        isCorrect -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759), // system green
                            modifier = Modifier.size(16.dp),
                        )
                        isSelected -> Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Port de `QuizOptionButtonStyle` (scale 0.97 / opacity 0.85 / easeOut 0.1s). */
@Composable
private fun QuizOptionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(100, easing = FastOutLinearInEasing),
        label = "quizOptionScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(100, easing = FastOutLinearInEasing),
        label = "quizOptionAlpha",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

private fun optionBgColor(
    styleVariant: Int,
    isDark: Boolean,
    hasVoted: Boolean,
    isCorrect: Boolean,
    isSelected: Boolean,
): Color {
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    if (!hasVoted) return ink.copy(alpha = if (isLight) 0.08f else 0.18f)
    if (isCorrect) return Color.Green.copy(alpha = 0.78f)
    if (isSelected) return Color.Red.copy(alpha = 0.74f)
    return ink.copy(alpha = if (isLight) 0.06f else 0.12f)
}

private fun optionCircleColor(
    styleVariant: Int,
    isDark: Boolean,
    hasVoted: Boolean,
    isCorrect: Boolean,
    isSelected: Boolean,
): Color {
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val surface = if (isLight) momentsStickerSurface(isDark) else Color.Black
    if (!hasVoted) return ink.copy(alpha = 0.14f)
    if (isCorrect) return surface.copy(alpha = 0.26f)
    if (isSelected) return surface.copy(alpha = 0.24f)
    return ink.copy(alpha = 0.1f)
}

private fun optionLetterColor(
    styleVariant: Int,
    isDark: Boolean,
    hasVoted: Boolean,
    isCorrect: Boolean,
    isSelected: Boolean,
): Color {
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val surface = if (isLight) momentsStickerSurface(isDark) else Color.Black
    if (!hasVoted) return ink.copy(alpha = 0.82f)
    if (isCorrect) return surface
    if (isSelected) return surface
    return ink.copy(alpha = 0.48f)
}

private fun optionTextColor(
    styleVariant: Int,
    isDark: Boolean,
    hasVoted: Boolean,
    isCorrect: Boolean,
    isSelected: Boolean,
): Color {
    val isLight = styleVariant % 6 == 0
    val ink = if (isLight) momentsStickerInk(isDark) else Color.White
    val surface = if (isLight) momentsStickerSurface(isDark) else Color.Black
    if (!hasVoted) return ink.copy(alpha = 0.9f)
    if (isCorrect || isSelected) return surface
    return ink.copy(alpha = 0.58f)
}

// MARK: - StickerDitherPattern

/** Port de `StickerDitherPattern`. */
@Composable
fun StickerDitherPattern(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos ->
                timeState.floatValue = nanos / 1_000_000_000f
            }
        }
    }
    Canvas(modifier = modifier.graphicsLayer { alpha = 0.85f }) {
        val clock = timeState.floatValue
        val dotSize = 2.5f
        val spacing = 6f
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = 0f
            val offset = if (row % 2 == 0) spacing / 2f else 0f
            while (x < size.width) {
                val waveX = sin(clock * 2f + y * 0.05f) * 2f
                val waveY = cos(clock * 2f + x * 0.05f) * 2f
                drawCircle(
                    color = color,
                    radius = dotSize / 2f,
                    center = Offset(x + offset + waveX + dotSize / 2f, y + waveY + dotSize / 2f),
                )
                x += spacing
            }
            y += spacing
            row += 1
        }
    }
}

// MARK: - InteractiveAudioStickerView

/** Port de `InteractiveAudioStickerView`. */
@Composable
fun InteractiveAudioStickerView(
    audioURL: String,
    duration: Double,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedDuration = duration

    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var progressJob by remember { mutableStateOf<Job?>(null) }
    var waveJob by remember { mutableStateOf<Job?>(null) }
    var animatedHeights by remember { mutableStateOf(listOf(10f, 14f, 10f)) }
    var didConfigureAudioSession by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val foregroundColor = if (isSystemInDarkTheme()) {
        Color.White
    } else {
        Color.Black.copy(alpha = 0.82f)
    }

    fun stopPlayback() {
        progressJob?.cancel()
        progressJob = null
        waveJob?.cancel()
        waveJob = null
        player?.stop()
        player?.release()
        player = null
        isPlaying = false
        progress = 0f
        animatedHeights = listOf(10f, 14f, 10f)
        if (didConfigureAudioSession) {
            MomentsAudioSession.restore()
            didConfigureAudioSession = false
        }
    }

    fun startProgressUpdates(activePlayer: MediaPlayer) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                progress = activePlayer.currentPosition.toFloat() / max(activePlayer.duration.toFloat(), 1f)
                if (!activePlayer.isPlaying) {
                    stopPlayback()
                    break
                }
                delay(100)
            }
        }
    }

    fun startWaveAnimation() {
        waveJob?.cancel()
        if (!isPlaying) {
            animatedHeights = listOf(10f, 14f, 10f)
            return
        }
        if (MotionPolicy.reduceMotion) {
            animatedHeights = listOf(12f, 16f, 12f)
            return
        }
        waveJob = scope.launch {
            while (isActive && isPlaying) {
                animatedHeights = listOf(
                    (6..16).random().toFloat(),
                    (10..20).random().toFloat(),
                    (6..16).random().toFloat(),
                )
                delay(200)
            }
            animatedHeights = listOf(10f, 14f, 10f)
        }
    }

    fun startPlayback() {
        val parsed = runCatching { Uri.parse(audioURL) }.getOrNull() ?: return
        if (!didConfigureAudioSession) {
            didConfigureAudioSession = true
        }
        scope.launch {
            MomentsAudioSession.activate()
            val mediaPlayer = runCatching {
                when {
                    parsed.scheme == "file" -> MediaPlayer().apply {
                        setDataSource(context, parsed)
                    }
                    audioURL.startsWith("http://") || audioURL.startsWith("https://") -> {
                        val cached = PersistentAudioCache.localURL(URL(audioURL))
                        MediaPlayer().apply { setDataSource(cached.absolutePath) }
                    }
                    else -> MediaPlayer().apply { setDataSource(audioURL) }
                }
            }.getOrNull()?.apply {
                setOnCompletionListener { stopPlayback() }
                prepare()
            } ?: return@launch

            player = mediaPlayer
            mediaPlayer.start()
            isPlaying = true
            startProgressUpdates(mediaPlayer)
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
            progressJob?.cancel()
            progressJob = null
            waveJob?.cancel()
            waveJob = null
            animatedHeights = listOf(10f, 14f, 10f)
        } else {
            val active = player
            if (active != null) {
                active.start()
                isPlaying = true
                startProgressUpdates(active)
            } else {
                startPlayback()
            }
        }
    }

    LaunchedEffect(Unit) {
        startPlayback()
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) startWaveAnimation()
        else {
            waveJob?.cancel()
            waveJob = null
            animatedHeights = listOf(10f, 14f, 10f)
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPlayback() }
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = false)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { togglePlayback() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(foregroundColor, foregroundColor.copy(alpha = 0.8f)),
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.Mic,
                contentDescription = null,
                tint = foregroundColor,
                modifier = Modifier.size(20.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                animatedHeights.forEach { barHeight ->
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(barHeight.dp)
                            .background(foregroundColor, RoundedCornerShape(1.5.dp)),
                    )
                }
            }
        }
    }
}
