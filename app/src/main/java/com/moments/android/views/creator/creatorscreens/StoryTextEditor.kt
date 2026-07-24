package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryColorPickerPanel
import com.moments.android.views.creator.components.StoryDominantColorsExtractor
import com.moments.android.views.creator.components.StoryMomentsEditorChrome
import com.moments.android.views.creator.components.StoryTextAttributesBuilder
import com.moments.android.views.creator.components.StoryTextEditorContext
import com.moments.android.views.creator.components.StoryTextGradientSettings
import com.moments.android.views.creator.components.StoryTextRenderConfiguration
import com.moments.android.views.creator.components.StoryTextStyle
import com.moments.android.views.creator.components.parseStoryColorHex
import com.moments.android.views.creator.components.rememberStoryFontFamily
import com.moments.android.views.creator.components.rememberStoryTextMotionFrame
import com.moments.android.views.creator.components.resolvedGradientStops
import com.moments.android.views.creator.components.storyTextMotion
import com.moments.android.views.creator.components.toStoryHex

/**
 * Port de StoryTextEditor.swift.
 *
 * Todo el estado que Swift mantiene mediante bindings vive en storyeditor.kt;
 * así cerrar o reabrir el editor conserva los raws que se persisten en
 * StoryTextOverlayDraft, en vez de dejar motion/gradiente como UI efímera.
 */
@Composable
fun StoryTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    selectedStyle: StoryTextStyle,
    onStyleChange: (StoryTextStyle) -> Unit,
    colorHex: String,
    onColorHexChange: (String) -> Unit,
    textAlignmentRaw: String,
    onTextAlignmentRawChange: (String) -> Unit,
    textBackgroundFillRaw: String,
    onTextBackgroundFillRawChange: (String) -> Unit,
    textFontSize: Float,
    onTextFontSizeChange: (Float) -> Unit,
    textStrokeRaw: String,
    onTextStrokeRawChange: (String) -> Unit,
    textMotionRaw: String,
    onTextMotionRawChange: (String) -> Unit,
    visualEffectRaw: String,
    onVisualEffectRawChange: (String) -> Unit,
    gradientStops: List<Color>,
    onGradientStopsChange: (List<Color>) -> Unit,
    gradientAngle: Int,
    onGradientAngleChange: (Int) -> Unit,
    selectedGradientStopIndex: Int,
    onSelectedGradientStopIndexChange: (Int) -> Unit,
    forcesAllCaps: Boolean,
    onForcesAllCapsChange: (Boolean) -> Unit,
    mediaSampleImage: Bitmap?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val textColor = parseStoryColorHex(colorHex)
    val selectedGradientIndex = selectedGradientStopIndex.coerceIn(
        0,
        (gradientStops.size - 1).coerceAtLeast(0),
    )
    val configuration = StoryTextRenderConfiguration(
        text = text,
        style = selectedStyle,
        visualEffectRaw = visualEffectRaw,
        textColor = textColor,
        textAlignmentRaw = textAlignmentRaw,
        textBackgroundFillRaw = textBackgroundFillRaw,
        fontSize = textFontSize,
        textStrokeRaw = textStrokeRaw,
        forcesAllCaps = forcesAllCaps,
        gradientStops = StoryTextGradientSettings.normalizedStops(gradientStops, textColor),
        gradientAngle = gradientAngle,
    )
    val attributes = StoryTextAttributesBuilder.coreAttributes(configuration)
    val fontFamily = rememberStoryFontFamily(selectedStyle)
    val motionReplayToken = remember(visualEffectRaw, gradientStops, gradientAngle, textMotionRaw) {
        (visualEffectRaw + gradientStops.joinToString { it.toStoryHex() } + gradientAngle + textMotionRaw).hashCode()
    }
    val motionFrame = rememberStoryTextMotionFrame(textMotionRaw, motionReplayToken)
    val suggestedColors = remember(mediaSampleImage) {
        StoryDominantColorsExtractor.extract(mediaSampleImage)
    }
    var activeContext by remember { mutableStateOf(StoryTextEditorContext.FONTS) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var isColorPickerOpen by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var localReplayToken by remember { mutableIntStateOf(0) }

    fun applyTextColor(color: Color) {
        onColorHexChange(color.toStoryHex())
        if (visualEffectRaw == "gradient" && gradientStops.indices.contains(selectedGradientIndex)) {
            onGradientStopsChange(
                gradientStops.mapIndexed { index, stop -> if (index == selectedGradientIndex) color else stop },
            )
        }
        localReplayToken++
    }

    fun applyVisualEffect(effect: String) {
        onVisualEffectRawChange(effect)
        if (effect == "gradient" && gradientStops.size < StoryTextGradientSettings.minStops) {
            onGradientStopsChange(StoryTextGradientSettings.defaultStops(textColor))
            onSelectedGradientStopIndexChange(0)
        }
        if (effect == "gradient") activeContext = StoryTextEditorContext.COLORS
        localReplayToken++
    }

    fun applyStyle(style: StoryTextStyle) {
        onStyleChange(style)
        onColorHexChange(style.defaultColorHex)
        onTextBackgroundFillRawChange(
            when (style) {
                StoryTextStyle.TYPEWRITER, StoryTextStyle.BOLD -> "solid"
                else -> "none"
            },
        )
        onVisualEffectRawChange(
            when (style) {
                StoryTextStyle.MARKER -> "marker"
                StoryTextStyle.NEON -> "neon"
                StoryTextStyle.CHALK -> "chalk"
                else -> "none"
            },
        )
        onTextStrokeRawChange(if (style == StoryTextStyle.MEME) "thick" else "none")
        onForcesAllCapsChange(style.usesAllCaps)
        localReplayToken++
    }

    fun cycleTextBackgroundFill() {
        onTextBackgroundFillRawChange(
            when (textBackgroundFillRaw.lowercase()) {
                "none" -> "solid"
                "solid" -> "inverted"
                "inverted" -> "semiTransparent"
                else -> "none"
            },
        )
    }

    fun cycleTextAlignment() {
        onTextAlignmentRawChange(
            when (textAlignmentRaw.lowercase()) {
                "leading", "left" -> "trailing"
                "trailing", "right" -> "center"
                else -> "leading"
            },
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .35f))
            .pointerInput(isEyedropperActive, mediaSampleImage) {
                detectTapGestures { location ->
                    val image = mediaSampleImage
                    if (isEyedropperActive && image != null) {
                        applyTextColor(
                            StoryDominantColorsExtractor.sampleColor(
                                location,
                                image,
                                androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
                            ),
                        )
                        isEyedropperActive = false
                    }
                }
            },
    ) {
        if (isEyedropperActive) {
            Text(
                text = "Tap photo to pick color",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(42.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(1.dp, Color.White.copy(alpha = .2f), CircleShape)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        BasicTextField(
            value = text,
            onValueChange = { raw ->
                onTextChange(if (forcesAllCaps || selectedStyle.usesAllCaps) raw.uppercase() else raw)
            },
            textStyle = LocalTextStyle.current.copy(
                color = attributes.foreground,
                background = attributes.background ?: Color.Transparent,
                fontSize = textFontSize.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = attributes.letterSpacing.em,
                textAlign = attributes.textAlign,
                fontFamily = fontFamily,
            ),
            cursorBrush = SolidColor(attributes.foreground),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .shadow(
                    elevation = if (visualEffectRaw in setOf("neon", "glow", "depth", "echo")) 8.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = textColor.copy(alpha = .65f),
                    spotColor = textColor.copy(alpha = .65f),
                )
                .storyTextMotion(motionFrame.copy(typewriterProgress = motionFrame.typewriterProgress + localReplayToken * 0f))
                .focusRequester(focusRequester)
                .onFocusChanged { isTextFieldFocused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.story_editor_text_placeholder),
                            color = attributes.foreground.copy(alpha = .45f),
                            fontSize = textFontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = attributes.textAlign,
                            fontFamily = fontFamily,
                        )
                    }
                    inner()
                }
            },
        )

        if (isTextFieldFocused) {
            StoryFontSizeSlider(
                value = textFontSize.coerceIn(16f, 72f),
                onValueChange = onTextFontSizeChange,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )
        }

        if (isColorPickerOpen) {
            StoryColorPickerPanel(
                selectedColor = textColor,
                onSelectedColorChange = ::applyTextColor,
                swatchColors = editorPalette,
                suggestedColors = suggestedColors,
                onPickFromCanvas = mediaSampleImage?.let {
                    {
                        isColorPickerOpen = false
                        isEyedropperActive = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 132.dp),
            )
        }

        StoryMomentsEditorChrome(
            activeContext = activeContext,
            onActiveContextChange = { activeContext = it },
            selectedStyle = selectedStyle,
            textColor = textColor,
            textMotionRaw = textMotionRaw,
            visualEffectRaw = visualEffectRaw,
            gradientStops = gradientStops,
            gradientAngle = gradientAngle,
            selectedGradientStopIndex = selectedGradientIndex,
            forcesAllCaps = forcesAllCaps,
            textAlignmentRaw = textAlignmentRaw,
            textBackgroundFillRaw = textBackgroundFillRaw,
            swatchColors = editorPalette,
            suggestedColors = suggestedColors,
            onStyleSelect = ::applyStyle,
            onTextColorChange = ::applyTextColor,
            onMotionSelect = {
                onTextMotionRawChange(it)
                localReplayToken++
            },
            onVisualEffectSelect = ::applyVisualEffect,
            onGradientStopsChange = onGradientStopsChange,
            onGradientAngleChange = onGradientAngleChange,
            onSelectedGradientStopIndexChange = onSelectedGradientStopIndexChange,
            onForcesAllCapsChange = onForcesAllCapsChange,
            onCycleAlignment = ::cycleTextAlignment,
            onCycleBackground = ::cycleTextBackgroundFill,
            onPickFromCanvas = mediaSampleImage?.let {
                {
                    isColorPickerOpen = false
                    isEyedropperActive = true
                }
            },
            onOpenColorPicker = { isColorPickerOpen = !isColorPickerOpen },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp),
        )
    }
}

private val editorPalette = listOf(
    "FFFFFF", "000000", "FF3B30", "FF9500", "FFCC00", "34C759",
    "007AFF", "5856D6", "AF52DE", "FF2D55", "A2845E", "F2C94C",
    "00C7BE", "8E8E93", "FFD60A", "BF5AF2", "64D2FF", "FF6B6B",
    "C4B5A5", "1C1C1E",
).map(::parseStoryColorHex)

/** Equivalente Compose del control vertical cónico FontSizeSlider de Swift. */
@Composable
private fun StoryFontSizeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticStep by remember { mutableIntStateOf(-1) }
    androidx.compose.foundation.Canvas(
        modifier
            .size(width = 44.dp, height = 220.dp)
            .pointerInput(Unit) {
                fun update(location: Offset) {
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val trackHeight = (height - 32f).coerceAtLeast(1f)
                    val clampedY = location.y.coerceIn(16f, height - 16f)
                    val inverseProgress = 1f - ((clampedY - 16f) / trackHeight)
                    onValueChange((16f + inverseProgress * (72f - 16f)).coerceIn(16f, 72f))
                    val step = (inverseProgress * 16f).toInt()
                    if (step != lastHapticStep) {
                        lastHapticStep = step
                        HapticManager.shared.lightImpact()
                    }
                }
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        update(it)
                    },
                    onDrag = { change, _ -> update(change.position) },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            },
    ) {
        val trackTop = 16f
        val trackBottom = size.height - 16f
        val centerX = size.width / 2f
        val topRadius = 6f
        val bottomRadius = 1.25f
        val track = Path().apply {
            moveTo(centerX - topRadius, trackTop)
            quadraticTo(centerX + topRadius, trackTop, centerX + topRadius, trackTop + topRadius)
            lineTo(centerX + bottomRadius, trackBottom - bottomRadius)
            quadraticTo(centerX + bottomRadius, trackBottom, centerX, trackBottom)
            quadraticTo(centerX - bottomRadius, trackBottom, centerX - bottomRadius, trackBottom - bottomRadius)
            lineTo(centerX - topRadius, trackTop + topRadius)
            quadraticTo(centerX - topRadius, trackTop, centerX, trackTop)
            close()
        }
        drawPath(track, Color.White.copy(alpha = .32f))

        val progress = ((value - 16f) / (72f - 16f)).coerceIn(0f, 1f)
        val knobY = 16f + (1f - progress) * (size.height - 32f)
        drawCircle(
            color = Color.Black.copy(alpha = if (isDragging) .35f else .22f),
            radius = if (isDragging) 17f else 16f,
            center = Offset(centerX, knobY + if (isDragging) 3f else 1f),
        )
        drawCircle(
            color = Color.White,
            radius = if (isDragging) 15.7f else 14f,
            center = Offset(centerX, knobY),
        )
    }
}
