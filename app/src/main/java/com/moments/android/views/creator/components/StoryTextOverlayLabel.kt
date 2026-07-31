package com.moments.android.views.creator.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port de `StoryTextOverlayLabel.swift`:
 * - `StoryTextOverlayLabel` / `StoryTextOverlayContainerRepresentable`
 * - `StoryTextOverlayContainerView.apply` (treatments visuales)
 * - `StoryTextEditorInputRepresentable` (texto editable + efectos detrás)
 * - `StoryTextAnimationModifier` → [storyTextMotion] / [rememberStoryTextMotionFrame]
 */

/** ≡ `StoryTextOverlayLabel` / container representable (viewer). */
@Composable
fun StoryTextOverlayLabel(
    configuration: StoryTextRenderConfiguration,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    motionRaw: String = "none",
    replayToken: Int = 0,
) {
    val fontFamily = rememberStoryFontFamily(configuration.style)
    val motion = rememberStoryTextMotionFrame(
        motionRaw = motionRaw,
        replayToken = replayToken,
        textLength = configuration.displayText.length,
    )
    val display = storyTextForMotion(configuration.displayText, motionRaw, motion)
    val configForDraw = configuration.copy(
        text = display,
        appliesDisplayTransform = false,
    )
    Box(
        modifier
            .widthIn(max = maxWidth)
            .storyTextMotion(motion)
            .wrapContentSize(align = alignmentFor(configuration.textAlign)),
    ) {
        StoryTextOverlayContainer(configuration = configForDraw, fontFamily = fontFamily)
    }
}

/**
 * ≡ `StoryTextEditorInputRepresentable` — efectos detrás + TextField transparente
 * (caret contraste según fill, como iOS).
 */
@Composable
fun StoryTextEditorInput(
    text: String,
    onTextChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusedChange: (Boolean) -> Unit,
    configuration: StoryTextRenderConfiguration,
    motionRaw: String,
    maxWidth: Dp,
    replayToken: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val fontFamily = rememberStoryFontFamily(configuration.style)
    val attrs = StoryTextAttributesBuilder.typingAttributes(configuration)
    val motion = rememberStoryTextMotionFrame(motionRaw, replayToken, text.length.coerceAtLeast(1))
    val focusRequester = remember { FocusRequester() }
    val caret = when (configuration.textBackgroundFillRaw.lowercase()) {
        "solid", "semitransparent" -> StoryTextAttributesBuilder.contrastColor(configuration.textColor)
        else -> configuration.textColor
    }
    val effectConfig = configuration.copy(text = text.ifEmpty { " " })

    Box(
        modifier
            .widthIn(min = 80.dp, max = maxWidth)
            .heightIn(min = 140.dp, max = 280.dp)
            .storyTextMotion(motion)
            .wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) {
            StoryTextOverlayContainer(
                configuration = effectConfig,
                fontFamily = fontFamily,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = LocalTextStyle.current.copy(
                color = Color.Transparent,
                fontSize = configuration.fontSize.sp,
                fontWeight = FontWeight.SemiBold,
                // ≡ iOS `NSKernAttributeName` en puntos (preset.letterSpacing), no em.
                letterSpacing = attrs.letterSpacing.sp,
                textAlign = attrs.textAlign,
                fontFamily = fontFamily,
            ),
            cursorBrush = SolidColor(caret),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusedChange(it.isFocused) },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder.ifEmpty { "Aa" },
                            color = configuration.textColor.copy(alpha = 0.45f),
                            fontSize = configuration.fontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = attrs.textAlign,
                            fontFamily = fontFamily,
                        )
                    }
                    inner()
                }
            },
        )
    }

    LaunchedEffect(isFocused) {
        if (isFocused) runCatching { focusRequester.requestFocus() }
    }
}
/**
 * ≡ `StoryTextOverlayContainerView.apply` — switch de `visualTreatment`.
 */
@Composable
fun StoryTextOverlayContainer(
    configuration: StoryTextRenderConfiguration,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val attrs = StoryTextAttributesBuilder.coreAttributes(configuration)
    val treatment = configuration.visualTreatment
    val baseStyle = TextStyle(
        color = attrs.foreground,
        fontSize = configuration.fontSize.sp,
        fontWeight = FontWeight.SemiBold,
        // ≡ iOS kern en puntos (`preset.letterSpacing`).
        letterSpacing = attrs.letterSpacing.sp,
        textAlign = attrs.textAlign,
        fontFamily = fontFamily,
        shadow = attrs.shadow?.let {
            Shadow(color = it.color, offset = it.offset, blurRadius = it.blurRadius)
        },
    )

    when (treatment) {
        StoryTextVisualTreatment.SPARKLE_PULSE -> SparklePulseText(configuration, baseStyle, attrs)
        StoryTextVisualTreatment.NEON_GLOW -> NeonGlowText(configuration, baseStyle)
        StoryTextVisualTreatment.SOFT_GLOW -> SoftGlowText(configuration, baseStyle)
        StoryTextVisualTreatment.PULSE_HALO -> PulseHaloText(configuration, baseStyle)
        StoryTextVisualTreatment.OUTLINE_POP -> OutlinePopText(configuration, baseStyle)
        StoryTextVisualTreatment.STICKER_CUTOUT -> StickerCutoutText(configuration, baseStyle)
        StoryTextVisualTreatment.GRADIENT_FILL -> GradientFillText(configuration, baseStyle, animated = false)
        StoryTextVisualTreatment.HOLOGRAPHIC_FILL -> GradientFillText(
            configuration = if (configuration.gradientStops.size < StoryTextGradientSettings.minStops) {
                configuration.copy(gradientStops = StoryTextGradientSettings.presetMoments)
            } else {
                configuration
            },
            baseStyle = baseStyle,
            animated = true,
        )
        StoryTextVisualTreatment.TEXT_SHIMMER -> TextShimmerLabel(configuration, baseStyle)
        StoryTextVisualTreatment.GLASS_TEXT -> GlassText(configuration, baseStyle)
        StoryTextVisualTreatment.TAPE_LABEL -> TapeLabelText(configuration, baseStyle)
        StoryTextVisualTreatment.ECHO_STACK -> EchoStackText(configuration, baseStyle, depth = false)
        StoryTextVisualTreatment.LONG_SHADOW -> EchoStackText(configuration, baseStyle, depth = true)
        StoryTextVisualTreatment.GLITCH_SPLIT -> GlitchSplitText(configuration, baseStyle)
        StoryTextVisualTreatment.MARKER_HIGHLIGHT -> MarkerHighlightText(configuration, baseStyle)
        StoryTextVisualTreatment.CHALK_DUST -> ChalkDustText(configuration, baseStyle)
        StoryTextVisualTreatment.PIXEL_BITMAP -> PixelBitmapText(configuration, baseStyle)
        StoryTextVisualTreatment.BOXED_CAPTION -> BoxedCaptionText(configuration, baseStyle, attrs)
        StoryTextVisualTreatment.MEME_STRONG -> MemeStrongText(configuration, baseStyle)
        StoryTextVisualTreatment.PLAIN -> {
            val bg = attrs.background
            Text(
                text = configuration.displayText,
                style = baseStyle,
                modifier = modifier.then(
                    if (bg != null) {
                        Modifier
                            .background(bg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    } else {
                        Modifier
                    },
                ),
            )
        }
    }
}

// region Treatments

@Composable
private fun SoftGlowText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            color = configuration.textColor,
            shadow = Shadow(configuration.textColor.copy(alpha = 0.42f), Offset.Zero, 16f),
        ),
    )
}

@Composable
private fun PulseHaloText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    val transition = rememberInfiniteTransition(label = "pulseHalo")
    val radius by transition.animateFloat(
        initialValue = 8f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "pulseRadius",
    )
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            color = configuration.textColor,
            shadow = Shadow(configuration.textColor.copy(alpha = 0.75f), Offset.Zero, radius),
        ),
    )
}

@Composable
private fun NeonGlowText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Box {
        Text(
            configuration.displayText,
            style = baseStyle.copy(
                color = configuration.textColor,
                shadow = Shadow(configuration.textColor, Offset.Zero, 5f),
            ),
        )
        Text(
            configuration.displayText,
            style = baseStyle.copy(
                color = Color.White.copy(alpha = 0.98f),
                shadow = Shadow(configuration.textColor.copy(alpha = 0.92f), Offset.Zero, 3f),
            ),
        )
    }
}

@Composable
private fun OutlinePopText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    val stroke = StoryTextAttributesBuilder.contrastColor(configuration.textColor)
    Box {
        Text(
            configuration.displayText,
            style = baseStyle.copy(
                color = stroke,
                shadow = Shadow(stroke, Offset.Zero, 0.5f),
            ),
            modifier = Modifier.offset(1.dp, 1.dp),
        )
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = configuration.textColor, shadow = null),
        )
    }
}

@Composable
private fun StickerCutoutText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Box {
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color.Black, shadow = null),
            modifier = Modifier.offset(1.5.dp, 1.5.dp),
        )
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color.White, shadow = null),
        )
    }
}

@Composable
private fun MemeStrongText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Box {
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color.Black, shadow = null),
            modifier = Modifier.offset(2.dp, 2.dp),
        )
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color.White, shadow = null),
        )
    }
}

@Composable
private fun GradientFillText(
    configuration: StoryTextRenderConfiguration,
    baseStyle: TextStyle,
    animated: Boolean,
) {
    val stops = configuration.resolvedGradientStops
    val points = configuration.gradientUnitPoints
    val transition = rememberInfiniteTransition(label = "holo")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing)),
        label = "holoShift",
    )
    val colors = if (animated && stops.isNotEmpty()) {
        val rot = ((shift * stops.size).toInt() % stops.size).coerceAtLeast(0)
        stops.drop(rot) + stops.take(rot)
    } else {
        stops
    }
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            brush = unitLinearGradientBrush(colors.ifEmpty { listOf(configuration.textColor) }, points),
            shadow = null,
        ),
    )
}

@Composable
private fun TextShimmerLabel(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    val c = configuration.textColor
    val stops = listOf(
        c.copy(alpha = 0.35f),
        c,
        Color.White.copy(alpha = 0.95f),
        c,
        c.copy(alpha = 0.35f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
        label = "shimmerT",
    )
    val points = Offset(-0.2f + t, 0f) to Offset(0.8f + t, 1f)
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            brush = unitLinearGradientBrush(stops, points),
            shadow = null,
        ),
    )
}

@Composable
private fun GlassText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            color = Color.White.copy(alpha = 0.96f),
            shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f),
        ),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun TapeLabelText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Text(
        configuration.displayText,
        style = baseStyle.copy(shadow = null),
        modifier = Modifier
            .graphicsLayer { rotationZ = -2.5f }
            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun EchoStackText(
    configuration: StoryTextRenderConfiguration,
    baseStyle: TextStyle,
    depth: Boolean,
) {
    val count = if (depth) 14 else 3
    val step = if (depth) 1.dp else 6.dp
    val alphaStep = if (depth) 0.045f else 0.16f
    Box {
        repeat(count) { i ->
            val layer = count - i
            Text(
                configuration.displayText,
                style = baseStyle.copy(
                    color = if (depth) {
                        Color.Black.copy(alpha = (0.32f - layer * alphaStep).coerceAtLeast(0.02f))
                    } else {
                        configuration.textColor.copy(alpha = (0.5f - layer * alphaStep).coerceAtLeast(0.08f))
                    },
                    shadow = null,
                ),
                modifier = Modifier.offset(step * layer, step * layer),
            )
        }
        Text(configuration.displayText, style = baseStyle.copy(shadow = null))
    }
}

@Composable
private fun GlitchSplitText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    val transition = rememberInfiniteTransition(label = "glitch")
    val jitter by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2_400
                0f at 0
                1.6f at 144
                -1.2f at 288
                0f at 480
                0f at 1_320
                2f at 1_440
                0f at 1_632
                -1.4f at 1_776
                0f at 2_400
            },
        ),
        label = "glitchJitter",
    )
    Box {
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color(0f, 1f, 0.94f, 0.85f), shadow = null),
            modifier = Modifier.offset((-2.2f + jitter).dp, (-1.2f).dp),
        )
        Text(
            configuration.displayText,
            style = baseStyle.copy(color = Color(1f, 0.17f, 0.84f, 0.85f), shadow = null),
            modifier = Modifier.offset((2.2f - jitter).dp, 1.2.dp),
        )
        Text(configuration.displayText, style = baseStyle.copy(shadow = null))
    }
}

@Composable
private fun MarkerHighlightText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    val contrast = StoryTextAttributesBuilder.contrastColor(configuration.textColor)
    Text(
        configuration.displayText,
        style = baseStyle.copy(color = contrast, shadow = null),
        modifier = Modifier
            .background(configuration.textColor.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChalkDustText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Text(
        configuration.displayText,
        style = baseStyle.copy(
            shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(2f, 2f), 0f),
        ),
        modifier = Modifier.shadow(
            elevation = 1.dp,
            ambientColor = Color.White.copy(alpha = 0.25f),
            spotColor = Color.White.copy(alpha = 0.25f),
        ),
    )
}

@Composable
private fun PixelBitmapText(configuration: StoryTextRenderConfiguration, baseStyle: TextStyle) {
    Text(
        configuration.displayText,
        style = baseStyle,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
                // Aproximación low-res: ligero blur inverso no disponible → scale crunch
            },
    )
}

@Composable
private fun BoxedCaptionText(
    configuration: StoryTextRenderConfiguration,
    baseStyle: TextStyle,
    attrs: StoryTextCoreAttributes,
) {
    val fill = StoryTextAttributesBuilder.backgroundColor(configuration) ?: attrs.background
    Text(
        configuration.displayText,
        style = baseStyle.copy(shadow = null),
        modifier = Modifier
            .then(
                if (fill != null) {
                    Modifier
                        .background(fill, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun SparklePulseText(
    configuration: StoryTextRenderConfiguration,
    baseStyle: TextStyle,
    @Suppress("UNUSED_PARAMETER") attrs: StoryTextCoreAttributes,
) {
    val tint = configuration.textColor
    BoxWithConstraints {
        Text(
            configuration.displayText,
            style = baseStyle.copy(
                color = tint,
                shadow = Shadow(tint.copy(alpha = 0.55f), Offset.Zero, 12f),
            ),
        )
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        Canvas(Modifier.matchParentSize()) {
            val anchors = listOf(
                Offset(0.12f, 0.18f), Offset(0.88f, 0.22f), Offset(0.18f, 0.82f),
                Offset(0.82f, 0.78f), Offset(0.50f, 0.08f), Offset(0.50f, 0.92f),
            )
            anchors.forEachIndexed { index, unit ->
                val cx = unit.x * w
                val cy = unit.y * h
                val starSize = with(density) { (if (index % 2 == 0) 10.dp else 7.dp).toPx() }
                drawPath(
                    path = sparklePath(Offset(cx, cy), starSize),
                    color = tint.copy(alpha = 0.85f),
                )
                drawPath(
                    path = sparklePath(Offset(cx, cy), starSize),
                    color = Color.White.copy(alpha = 0.35f),
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}

private fun sparklePath(center: Offset, size: Float): Path {
    val outer = size / 2f
    val inner = outer * 0.34f
    return Path().apply {
        for (index in 0 until 8) {
            val angle = (index * (PI / 4)) - (PI / 2)
            val radius = if (index % 2 == 0) outer else inner
            val point = Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius,
            )
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
}

// endregion

private fun alignmentFor(align: TextAlign): Alignment = when (align) {
    TextAlign.Start -> Alignment.CenterStart
    TextAlign.End -> Alignment.CenterEnd
    else -> Alignment.Center
}

/** Brush con puntos unitarios (0..1) → píxeles del layout del Text. */
private fun unitLinearGradientBrush(
    colors: List<Color>,
    points: Pair<Offset, Offset>,
): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val from = Offset(points.first.x * size.width, points.first.y * size.height)
        val to = Offset(points.second.x * size.width, points.second.y * size.height)
        return LinearGradientShader(
            from = from,
            to = to,
            colors = colors,
            colorStops = null,
            tileMode = TileMode.Clamp,
        )
    }
}

/**
 * Convenience desde draft del editor/canvas.
 */
@Composable
fun StoryTextOverlayLabel(
    overlay: StoryTextOverlayDraft,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val config = StoryTextRenderConfiguration(
        text = overlay.text,
        style = StoryTextStyle.fromRaw(overlay.styleRaw),
        visualEffectRaw = overlay.visualEffectRaw,
        textColor = parseStoryColorHex(overlay.colorHex),
        textAlignmentRaw = overlay.alignmentRaw,
        textBackgroundFillRaw = overlay.backgroundFillRaw,
        fontSize = overlay.fontSize.toFloat(),
        textStrokeRaw = overlay.strokeRaw,
        forcesAllCaps = overlay.forcesAllCaps,
        gradientStops = overlay.gradientColors,
        gradientAngle = overlay.gradientAngle,
    )
    StoryTextOverlayLabel(
        configuration = config,
        maxWidth = maxWidth,
        modifier = modifier,
        motionRaw = overlay.motionRaw,
    )
}
