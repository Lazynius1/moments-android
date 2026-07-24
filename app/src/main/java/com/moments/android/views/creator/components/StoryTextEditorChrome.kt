package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Port de las métricas de `StoryTextEditorChrome` de SwiftUI. */
object StoryTextEditorChrome {
    val selectionFill = Color.White
    val chipIdleFill = Color.White.copy(alpha = .14f)
    val toolbarFill = Color.White.copy(alpha = .14f)
    val toolbarHeight = 44.dp
    val contextRowHeight = 40.dp
    val chromeSpacing = 8.dp
    val keyboardChromeGap = 18.dp
    val chromeBottomPadding = 12.dp
    val totalHeight = contextRowHeight + chromeSpacing + toolbarHeight
}

/** Port de `StoryTextEditorContext`. */
enum class StoryTextEditorContext { FONTS, COLORS, MOTION, VISUAL }

/** Port de `StoryMomentsFontRow`. */
@Composable
fun StoryMomentsFontRow(
    selectedStyle: StoryTextStyle,
    onSelect: (StoryTextStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTextStyle.fontPickerStyles.forEach { style ->
            val selected = style == selectedStyle
            Text(
                text = style.displayName,
                color = if (selected) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) StoryTextEditorChrome.selectionFill else StoryTextEditorChrome.chipIdleFill)
                    .clickable { onSelect(style) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Primer chunk de `StoryTextEditorContextRow`.
 * El estado se conserva en el editor padre para que publicación/reedición usen
 * los mismos raws que la metadata de Swift.
 */
@Composable
fun StoryTextEditorContextRow(
    context: StoryTextEditorContext,
    selectedStyle: StoryTextStyle,
    textColor: Color,
    textMotionRaw: String,
    visualEffectRaw: String,
    gradientStops: List<Color> = emptyList(),
    gradientAngle: Int = 0,
    selectedGradientStopIndex: Int = 0,
    swatchColors: List<Color>,
    suggestedColors: List<Color>,
    onStyleSelect: (StoryTextStyle) -> Unit,
    onTextColorChange: (Color) -> Unit,
    onMotionSelect: (String) -> Unit,
    onVisualEffectSelect: (String) -> Unit,
    onGradientStopsChange: (List<Color>) -> Unit = {},
    onGradientAngleChange: (Int) -> Unit = {},
    onSelectedGradientStopIndexChange: (Int) -> Unit = {},
    onPickFromCanvas: (() -> Unit)? = null,
    onOpenColorPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.height(StoryTextEditorChrome.contextRowHeight)) {
        when (context) {
            StoryTextEditorContext.FONTS -> StoryMomentsFontRow(selectedStyle, onStyleSelect)
            StoryTextEditorContext.COLORS -> if (visualEffectRaw == "gradient") {
                StoryTextGradientContext(
                    textColor, gradientStops, gradientAngle, selectedGradientStopIndex,
                    onTextColorChange, onGradientStopsChange, onGradientAngleChange,
                    onSelectedGradientStopIndexChange, onPickFromCanvas,
                )
            } else StoryTextColorContext(
                textColor = textColor,
                swatchColors = swatchColors,
                suggestedColors = suggestedColors,
                onTextColorChange = onTextColorChange,
                onPickFromCanvas = onPickFromCanvas,
                onOpenColorPicker = onOpenColorPicker,
            )
            StoryTextEditorContext.MOTION -> StoryTextPillContext(
                items = storyTextMomentMotionItems,
                selectedRaw = textMotionRaw,
                onSelect = onMotionSelect,
            )
            StoryTextEditorContext.VISUAL -> StoryTextPillContext(
                items = storyTextVisualToolbarEffects.map { it.storyTextEffectLabel() to it },
                selectedRaw = visualEffectRaw,
                onSelect = onVisualEffectSelect,
            )
        }
    }
}

@Composable
private fun StoryTextGradientContext(
    textColor: Color,
    gradientStops: List<Color>,
    gradientAngle: Int,
    selectedIndex: Int,
    onTextColorChange: (Color) -> Unit,
    onStopsChange: (List<Color>) -> Unit,
    onAngleChange: (Int) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    onPickFromCanvas: (() -> Unit)?,
) {
    val resolved = StoryTextGradientSettings.normalizedStops(gradientStops, textColor)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        resolved.forEachIndexed { index, color ->
            StoryTextColorChip(color, index == selectedIndex) {
                onSelectedIndexChange(index)
                onTextColorChange(color)
            }
        }
        if (resolved.size < StoryTextGradientSettings.maxStops) {
            Text(
                "+", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(.18f))
                    .clickable {
                        onStopsChange(resolved + textColor)
                        onSelectedIndexChange(resolved.size)
                    }.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (resolved.size > StoryTextGradientSettings.minStops && resolved.indices.contains(selectedIndex)) {
            Text(
                "−", color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(.18f))
                    .clickable {
                        val next = resolved.toMutableList().also { it.removeAt(selectedIndex) }
                        onStopsChange(next)
                        onSelectedIndexChange(selectedIndex.coerceAtMost(next.lastIndex))
                    }.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Text(
            StoryTextGradientSettings.angleSymbol(gradientAngle),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(.14f))
                .clickable { onAngleChange(StoryTextGradientSettings.cycleAngle(gradientAngle)) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        StoryTextGradientPreset("Moments", StoryTextGradientSettings.presetMoments, onStopsChange, onSelectedIndexChange, onTextColorChange)
        StoryTextGradientPreset("Sunset", StoryTextGradientSettings.presetSunset, onStopsChange, onSelectedIndexChange, onTextColorChange)
        StoryTextGradientPreset("Ocean", StoryTextGradientSettings.presetOcean, onStopsChange, onSelectedIndexChange, onTextColorChange)
        onPickFromCanvas?.let { pick ->
            Text("⌾", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable(onClick = pick).padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun StoryTextGradientPreset(
    title: String,
    colors: List<Color>,
    onStopsChange: (List<Color>) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    onTextColorChange: (Color) -> Unit,
) {
    Text(
        title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(.14f))
            .clickable {
                onStopsChange(colors.take(StoryTextGradientSettings.maxStops))
                onSelectedIndexChange(0)
                onTextColorChange(colors.first())
            }.padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun StoryTextColorContext(
    textColor: Color,
    swatchColors: List<Color>,
    suggestedColors: List<Color>,
    onTextColorChange: (Color) -> Unit,
    onPickFromCanvas: (() -> Unit)?,
    onOpenColorPicker: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTextColorChip(textColor, selected = false, onClick = onOpenColorPicker)
        StoryTextColorChip(parseStoryColorHex("FAF9F6"), parseStoryColorHex("FAF9F6").toArgb() == textColor.toArgb()) {
            onTextColorChange(parseStoryColorHex("FAF9F6"))
        }
        StoryTextColorChip(parseStoryColorHex("0B1215"), parseStoryColorHex("0B1215").toArgb() == textColor.toArgb()) {
            onTextColorChange(parseStoryColorHex("0B1215"))
        }
        suggestedColors.forEach { color ->
            StoryTextColorChip(color, color.toArgb() == textColor.toArgb()) { onTextColorChange(color) }
        }
        swatchColors.forEach { color ->
            StoryTextColorChip(color, color.toArgb() == textColor.toArgb()) { onTextColorChange(color) }
        }
        onPickFromCanvas?.let { pick ->
            Text(
                text = "⌾",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = pick).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StoryTextColorChip(color: Color, selected: Boolean, onClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (selected) Modifier.background(Color.White.copy(alpha = .24f), CircleShape) else Modifier,
            ),
    )
}

@Composable
private fun StoryTextPillContext(
    items: List<Pair<String, String>>,
    selectedRaw: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, raw) ->
            val selected = raw.equals(selectedRaw, ignoreCase = true)
            Text(
                text = label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) StoryTextEditorChrome.selectionFill else StoryTextEditorChrome.chipIdleFill)
                    .clickable { onSelect(raw) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private val storyTextMomentMotionItems = listOf(
    "None" to "none",
    "Type" to "typewriter",
    "Pop" to "pop",
    "Jump" to "bounce",
)

private fun String.storyTextEffectLabel(): String = when (this) {
    "none" -> "None"
    "sticker" -> "Sticker"
    "outline" -> "Outline"
    "gradient" -> "Gradient"
    "neon" -> "Neon"
    "glitch" -> "Glitch"
    "echo" -> "Echo"
    "depth" -> "Depth"
    "glow" -> "Glow"
    "glass" -> "Glass"
    "sparkle" -> "Sparkle"
    "pixel" -> "Pixel"
    "holographic" -> "Holo"
    "tape" -> "Tape"
    "pulse" -> "Pulse"
    else -> replaceFirstChar { it.uppercase() }
}

/** Port de `StoryMomentsTextToolbar`: seis herramientas sin IA. */
@Composable
fun StoryMomentsTextToolbar(
    activeContext: StoryTextEditorContext,
    onActiveContextChange: (StoryTextEditorContext) -> Unit,
    forcesAllCaps: Boolean,
    onForcesAllCapsChange: (Boolean) -> Unit,
    styleUsesCaps: Boolean,
    textAlignmentRaw: String,
    onCycleAlignment: () -> Unit,
    textBackgroundFillRaw: String,
    selectedColor: Color,
    onCycleBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(StoryTextEditorChrome.toolbarHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(StoryTextEditorChrome.toolbarFill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTextToolbarItem(
            label = if (forcesAllCaps || styleUsesCaps) "AA" else "Aa",
            active = activeContext == StoryTextEditorContext.FONTS,
            onTap = { onActiveContextChange(StoryTextEditorContext.FONTS) },
            onLongPress = { onForcesAllCapsChange(!forcesAllCaps) },
        )
        StoryTextToolbarDivider()
        StoryTextToolbarItem("◉", activeContext == StoryTextEditorContext.COLORS, onTap = { onActiveContextChange(StoryTextEditorContext.COLORS) })
        StoryTextToolbarDivider()
        StoryTextToolbarItem("↝", activeContext == StoryTextEditorContext.MOTION, onTap = { onActiveContextChange(StoryTextEditorContext.MOTION) })
        StoryTextToolbarDivider()
        StoryTextToolbarItem("A✦", activeContext == StoryTextEditorContext.VISUAL, onTap = { onActiveContextChange(StoryTextEditorContext.VISUAL) })
        StoryTextToolbarDivider()
        val alignment = when (textAlignmentRaw.lowercase()) {
            "leading", "left" -> "≡"
            "trailing", "right" -> "≡"
            else -> "☰"
        }
        StoryTextToolbarItem(alignment, active = true, onTap = onCycleAlignment)
        StoryTextToolbarDivider()
        StoryTextBackgroundToolbarItem(textBackgroundFillRaw, selectedColor, onCycleBackground)
    }
}

@Composable
private fun RowScope.StoryTextToolbarItem(
    label: String,
    active: Boolean,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Text(
        label,
        color = if (active) Color.White else Color.White.copy(.55f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f).height(StoryTextEditorChrome.toolbarHeight)
            .pointerInput(onTap, onLongPress) {
                detectTapGestures(onTap = { onTap() }, onLongPress = onLongPress?.let { { it() } })
            }.padding(top = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun StoryTextToolbarDivider() {
    Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color.White.copy(.12f)))
}

@Composable
private fun RowScope.StoryTextBackgroundToolbarItem(
    fillRaw: String,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    val fill = when (fillRaw.lowercase()) {
        "solid" -> selectedColor
        "semitransparent" -> selectedColor.copy(.70f)
        "inverted" -> StoryTextAttributesBuilder.contrastColor(selectedColor)
        else -> Color.Transparent
    }
    Box(
        Modifier.weight(1f).height(StoryTextEditorChrome.toolbarHeight).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 22.dp, height = 18.dp).clip(RoundedCornerShape(5.dp))
            .background(fill)) {
            Text("A", color = if (fillRaw == "none") Color.White else StoryTextAttributesBuilder.contrastColor(fill),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Contenedor Compose de las dos filas de `StoryMomentsEditorChrome`. */
@Composable
fun StoryMomentsEditorChrome(
    activeContext: StoryTextEditorContext,
    onActiveContextChange: (StoryTextEditorContext) -> Unit,
    selectedStyle: StoryTextStyle,
    textColor: Color,
    textMotionRaw: String,
    visualEffectRaw: String,
    gradientStops: List<Color>,
    gradientAngle: Int,
    selectedGradientStopIndex: Int,
    forcesAllCaps: Boolean,
    textAlignmentRaw: String,
    textBackgroundFillRaw: String,
    swatchColors: List<Color>,
    suggestedColors: List<Color>,
    onStyleSelect: (StoryTextStyle) -> Unit,
    onTextColorChange: (Color) -> Unit,
    onMotionSelect: (String) -> Unit,
    onVisualEffectSelect: (String) -> Unit,
    onGradientStopsChange: (List<Color>) -> Unit,
    onGradientAngleChange: (Int) -> Unit,
    onSelectedGradientStopIndexChange: (Int) -> Unit,
    onForcesAllCapsChange: (Boolean) -> Unit,
    onCycleAlignment: () -> Unit,
    onCycleBackground: () -> Unit,
    onPickFromCanvas: (() -> Unit)? = null,
    onOpenColorPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(StoryTextEditorChrome.chromeSpacing),
    ) {
        StoryTextEditorContextRow(
            context = activeContext,
            selectedStyle = selectedStyle,
            textColor = textColor,
            textMotionRaw = textMotionRaw,
            visualEffectRaw = visualEffectRaw,
            gradientStops = gradientStops,
            gradientAngle = gradientAngle,
            selectedGradientStopIndex = selectedGradientStopIndex,
            swatchColors = swatchColors,
            suggestedColors = suggestedColors,
            onStyleSelect = onStyleSelect,
            onTextColorChange = onTextColorChange,
            onMotionSelect = onMotionSelect,
            onVisualEffectSelect = onVisualEffectSelect,
            onGradientStopsChange = onGradientStopsChange,
            onGradientAngleChange = onGradientAngleChange,
            onSelectedGradientStopIndexChange = onSelectedGradientStopIndexChange,
            onPickFromCanvas = onPickFromCanvas,
            onOpenColorPicker = onOpenColorPicker,
        )
        StoryMomentsTextToolbar(
            activeContext = activeContext,
            onActiveContextChange = { context ->
                onActiveContextChange(
                    if (activeContext == context && context != StoryTextEditorContext.FONTS) StoryTextEditorContext.FONTS else context,
                )
            },
            forcesAllCaps = forcesAllCaps,
            onForcesAllCapsChange = onForcesAllCapsChange,
            styleUsesCaps = selectedStyle.usesAllCaps,
            textAlignmentRaw = textAlignmentRaw,
            onCycleAlignment = onCycleAlignment,
            textBackgroundFillRaw = textBackgroundFillRaw,
            selectedColor = textColor,
            onCycleBackground = onCycleBackground,
        )
    }
}
