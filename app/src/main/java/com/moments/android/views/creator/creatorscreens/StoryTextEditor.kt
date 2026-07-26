package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryColorPickerPanel
import com.moments.android.views.creator.components.StoryDominantColorsExtractor
import com.moments.android.views.creator.components.StoryMomentsEditorChrome
import com.moments.android.views.creator.components.StoryTextBackgroundFill
import com.moments.android.views.creator.components.StoryTextEditorContext
import com.moments.android.views.creator.components.StoryTextEditorInput
import com.moments.android.views.creator.components.StoryTextEffect
import com.moments.android.views.creator.components.StoryTextGradientSettings
import com.moments.android.views.creator.components.StoryTextRenderConfiguration
import com.moments.android.views.creator.components.StoryTextStroke
import com.moments.android.views.creator.components.StoryTextStyle
import com.moments.android.views.creator.components.parseStoryColorHex
import com.moments.android.views.creator.components.toStoryHex
import com.moments.android.views.creator.creatoruikit.creatorMomentsCaptureRect
import androidx.compose.ui.geometry.Size as ComposeSize

/**
 * Port de StoryTextEditor.swift.
 *
 * Todo el estado que Swift mantiene mediante bindings vive en storyeditor.kt;
 * así cerrar o reabrir el editor conserva los raws que se persisten en
 * StoryTextOverlayDraft, en vez de dejar motion/gradiente como UI efímera.
 *
 * Los tipos legacy del final del Swift (`StoryStyledTextView`, `TextStyleOption`,
 * `ColorOption`) no se portan aquí: el input Compose es `StoryTextEditorInput`
 * y el chrome es `StoryMomentsEditorChrome` (ya [x]).
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
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    // Solo para levantar el slider; el chrome usa imePadding().
    // No aplicamos IME al texto centrado: el canvas/dialog ya no debe encogerse.
    val fontSliderLift = if (imeBottomPx > 0) (-40).dp else 0.dp

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
    val suggestedColors = remember(mediaSampleImage) {
        StoryDominantColorsExtractor.extract(mediaSampleImage)
    }
    var activeContext by remember { mutableStateOf(StoryTextEditorContext.FONTS) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var isColorPickerOpen by remember { mutableStateOf(false) }
    var isTextFieldFocused by remember { mutableStateOf(false) }
    var localReplayToken by remember { mutableIntStateOf(0) }
    val motionReplayToken = remember(visualEffectRaw, gradientStops, gradientAngle, textMotionRaw, localReplayToken) {
        (visualEffectRaw + gradientStops.joinToString { it.toStoryHex() } + gradientAngle + textMotionRaw + localReplayToken).hashCode()
    }

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
        // ≡ TextEffect.opensColorContextOnSelect
        if (StoryTextEffect.fromRaw(effect).opensColorContextOnSelect) {
            activeContext = StoryTextEditorContext.COLORS
        }
        localReplayToken++
        HapticManager.shared.lightImpact()
    }

    fun applyStyle(style: StoryTextStyle) {
        val applied = style.applyPreset()
        onStyleChange(style)
        onColorHexChange(applied.colorHex)
        onTextBackgroundFillRawChange(applied.backgroundFill.raw)
        onVisualEffectRawChange(applied.effect.raw)
        onTextStrokeRawChange(applied.stroke.raw)
        // iOS fuerza thick otra vez para meme tras applyPreset (preset ya lo trae).
        if (style == StoryTextStyle.MEME) {
            onTextStrokeRawChange(StoryTextStroke.THICK.raw)
        }
        onForcesAllCapsChange(applied.forcesAllCaps)
        localReplayToken++
        HapticManager.shared.lightImpact()
    }

    fun cycleTextBackgroundFill() {
        onTextBackgroundFillRawChange(
            StoryTextBackgroundFill.fromRaw(textBackgroundFillRaw).cycled().raw,
        )
        HapticManager.shared.lightImpact()
    }

    fun cycleTextAlignment() {
        onTextAlignmentRawChange(
            when (textAlignmentRaw.lowercase()) {
                "leading", "left" -> "trailing"
                "trailing", "right" -> "center"
                else -> "leading"
            },
        )
        HapticManager.shared.lightImpact()
    }

    LaunchedEffect(Unit) {
        if (selectedStyle.usesAllCaps) onForcesAllCapsChange(true)
        isTextFieldFocused = true
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.45f),
                    ),
                ),
            )
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
                        HapticManager.shared.lightImpact()
                    } else {
                        // ≡ hideKeyboard() al tocar el fondo
                        isTextFieldFocused = false
                        focusManager.clearFocus()
                        isColorPickerOpen = false
                    }
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val captureRect = creatorMomentsCaptureRect(
                inSize = ComposeSize(
                    constraints.maxWidth.toFloat(),
                    constraints.maxHeight.toFloat(),
                ),
                topInsetPx = 0f,
                bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat(),
                density = density,
            )
            val chromeHeightPx = with(density) { 92.dp.toPx() }
            val canvasBottomGap = (constraints.maxHeight - captureRect.bottom).coerceAtLeast(0f)
            val centeredGapPadding = maxOf(
                with(density) { 8.dp.toPx() },
                (canvasBottomGap - chromeHeightPx) / 2f,
            )
            val bottomToolbarPadding = if (imeBottomPx > 0) {
                with(density) { 8.dp.toPx() }
            } else {
                centeredGapPadding
            }
            val bottomToolbarPaddingDp = with(density) { bottomToolbarPadding.toDp() }

        if (isEyedropperActive) {
            Text(
                text = stringResource(R.string.story_text_editor_eyedropper_hint),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = .65f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // ≡ top bar: X + Done capsule (no check icon)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
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
            Text(
                text = stringResource(R.string.story_text_editor_done),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        StoryTextEditorInput(
            text = text,
            onTextChange = { raw ->
                onTextChange(if (forcesAllCaps || selectedStyle.usesAllCaps) raw.uppercase() else raw)
            },
            isFocused = isTextFieldFocused,
            onFocusedChange = { isTextFieldFocused = it },
            configuration = configuration,
            motionRaw = textMotionRaw,
            maxWidth = 280.dp,
            replayToken = motionReplayToken,
            placeholder = stringResource(R.string.story_editor_text_placeholder),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .padding(bottom = bottomToolbarPaddingDp),
        )

        if (isTextFieldFocused) {
            StoryFontSizeSlider(
                value = textFontSize.coerceIn(16f, 72f),
                onValueChange = onTextFontSizeChange,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .padding(bottom = bottomToolbarPaddingDp)
                    .offset(y = fontSliderLift),
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
                    .imePadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = bottomToolbarPaddingDp + 120.dp),
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
                .imePadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = bottomToolbarPaddingDp),
        )
        } // BoxWithConstraints
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
