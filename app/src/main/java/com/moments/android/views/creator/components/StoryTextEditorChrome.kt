@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager

/** Port de las métricas de `StoryTextEditorChrome` de SwiftUI. */
object StoryTextEditorChrome {
    val selectionFill = Color.White
    val chipIdleFill = Color.White.copy(alpha = .14f)
    val toolbarFill = Color.White.copy(alpha = .14f)
    val toolbarHeight = 44.dp
    val contextRowHeight = 40.dp
    val chromeSpacing = 8.dp
    /** Extra gap between keyboard top and chrome. */
    val keyboardChromeGap = 18.dp
    val chromeBottomPadding = 12.dp
    val totalHeight: Dp = contextRowHeight + chromeSpacing + toolbarHeight

    /** ≡ `totalHeight(for:)` — misma altura para todos los contextos. */
    fun totalHeight(forContext: StoryTextEditorContext): Dp {
        @Suppress("UNUSED_VARIABLE")
        val ignored = forContext
        return totalHeight
    }
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
            val fontFamily = rememberStoryFontFamily(style)
            Text(
                text = style.displayName,
                color = if (selected) Color.Black else Color.White,
                fontSize = 15.sp,
                fontFamily = fontFamily,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) StoryTextEditorChrome.selectionFill else StoryTextEditorChrome.chipIdleFill)
                    .clickable {
                        onSelect(style)
                        HapticManager.shared.lightImpact()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Port de `StoryTextEditorContextRow`.
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
    val contextResources = LocalContext.current
    Box(modifier = modifier.height(StoryTextEditorChrome.contextRowHeight)) {
        when (context) {
            StoryTextEditorContext.FONTS -> StoryMomentsFontRow(selectedStyle, onStyleSelect)
            StoryTextEditorContext.COLORS -> if (visualEffectRaw.equals("gradient", ignoreCase = true)) {
                StoryTextGradientContext(
                    textColor = textColor,
                    gradientStops = gradientStops,
                    gradientAngle = gradientAngle,
                    selectedIndex = selectedGradientStopIndex,
                    onTextColorChange = onTextColorChange,
                    onStopsChange = onGradientStopsChange,
                    onAngleChange = onGradientAngleChange,
                    onSelectedIndexChange = onSelectedGradientStopIndexChange,
                    onPickFromCanvas = onPickFromCanvas,
                    onOpenColorPicker = onOpenColorPicker,
                )
            } else {
                StoryTextColorContext(
                    textColor = textColor,
                    swatchColors = swatchColors,
                    suggestedColors = suggestedColors,
                    onTextColorChange = onTextColorChange,
                    onPickFromCanvas = onPickFromCanvas,
                    onOpenColorPicker = onOpenColorPicker,
                )
            }
            StoryTextEditorContext.MOTION -> StoryTextPillContext(
                items = StoryTextMotion.momentsToolbarMotions.map { it.displayName to it.raw },
                selectedRaw = textMotionRaw,
                onSelect = onMotionSelect,
            )
            StoryTextEditorContext.VISUAL -> StoryTextPillContext(
                items = storyTextVisualToolbarEffects.map {
                    storyTextEffectMomentsToolbarLabel(it, contextResources) to it
                },
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
    onOpenColorPicker: (() -> Unit)?,
) {
    val resolved = StoryTextGradientSettings.normalizedStops(gradientStops, textColor)
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        resolved.forEachIndexed { index, color ->
            Box {
                StoryTextGradientStopChip(
                    color = color,
                    selected = index == selectedIndex,
                    onClick = {
                        onSelectedIndexChange(index)
                        onTextColorChange(color)
                        HapticManager.shared.lightImpact()
                    },
                    onLongClick = {
                        if (resolved.size > StoryTextGradientSettings.minStops) {
                            menuIndex = index
                        }
                    },
                )
                DropdownMenu(
                    expanded = menuIndex == index,
                    onDismissRequest = { menuIndex = null },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.story_text_gradient_remove_stop)) },
                        onClick = {
                            val next = resolved.toMutableList().also { it.removeAt(index) }
                            onStopsChange(next)
                            onSelectedIndexChange(selectedIndex.coerceAtMost(next.lastIndex).coerceAtLeast(0))
                            menuIndex = null
                        },
                    )
                }
            }
        }
        if (resolved.size < StoryTextGradientSettings.maxStops) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .18f))
                    .clickable {
                        onStopsChange(resolved + textColor)
                        onSelectedIndexChange(resolved.size)
                        HapticManager.shared.lightImpact()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        // ≡ ColorPicker nativo iOS (edita el stop seleccionado vía callback del padre).
        StoryTextColorChip(
            color = resolved.getOrNull(selectedIndex) ?: textColor,
            selected = false,
            size = 26.dp,
            onClick = onOpenColorPicker,
        )
        Box(
            Modifier
                .width(30.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .14f))
                .clickable {
                    onAngleChange(StoryTextGradientSettings.cycleAngle(gradientAngle))
                    HapticManager.shared.lightImpact()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                StoryTextGradientSettings.angleSymbol(gradientAngle),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        StoryTextGradientPreset(
            title = stringResource(R.string.story_text_gradient_preset_moments),
            colors = StoryTextGradientSettings.presetMoments,
            onStopsChange = onStopsChange,
            onSelectedIndexChange = onSelectedIndexChange,
            onTextColorChange = onTextColorChange,
        )
        StoryTextGradientPreset(
            title = stringResource(R.string.story_text_gradient_preset_sunset),
            colors = StoryTextGradientSettings.presetSunset,
            onStopsChange = onStopsChange,
            onSelectedIndexChange = onSelectedIndexChange,
            onTextColorChange = onTextColorChange,
        )
        StoryTextGradientPreset(
            title = stringResource(R.string.story_text_gradient_preset_ocean),
            colors = StoryTextGradientSettings.presetOcean,
            onStopsChange = onStopsChange,
            onSelectedIndexChange = onSelectedIndexChange,
            onTextColorChange = onTextColorChange,
        )
        onPickFromCanvas?.let { pick ->
            Icon(
                Icons.Filled.Colorize,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .clickable(onClick = pick)
                    .padding(4.dp),
            )
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
        title,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .14f))
            .clickable {
                val next = colors.take(StoryTextGradientSettings.maxStops)
                onStopsChange(next)
                onSelectedIndexChange(0)
                onTextColorChange(next.firstOrNull() ?: Color.White)
                HapticManager.shared.lightImpact()
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
    val light = parseStoryColorHex("FAF9F6")
    val dark = parseStoryColorHex("0B1215")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTextColorChip(textColor, selected = false, onClick = onOpenColorPicker)
        StoryTextColorDivider()
        StoryTextColorChip(light, light.toArgb() == textColor.toArgb()) {
            onTextColorChange(light)
            HapticManager.shared.lightImpact()
        }
        StoryTextColorChip(dark, dark.toArgb() == textColor.toArgb()) {
            onTextColorChange(dark)
            HapticManager.shared.lightImpact()
        }
        StoryTextColorDivider()
        suggestedColors.forEach { color ->
            StoryTextColorChip(color, color.toArgb() == textColor.toArgb()) {
                onTextColorChange(color)
                HapticManager.shared.lightImpact()
            }
        }
        swatchColors.forEach { color ->
            StoryTextColorChip(color, color.toArgb() == textColor.toArgb()) {
                onTextColorChange(color)
                HapticManager.shared.lightImpact()
            }
        }
        onPickFromCanvas?.let { pick ->
            Icon(
                Icons.Filled.Colorize,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = pick)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun StoryTextColorDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = .3f)),
    )
}

/** ≡ `ColorOption` (StoryTextEditor.swift). */
@Composable
private fun StoryTextColorChip(
    color: Color,
    selected: Boolean,
    size: Dp = 24.dp,
    onClick: (() -> Unit)?,
) {
    val stroke = when {
        selected -> Color.White
        color.toArgb() == Color.White.toArgb() -> Color.Gray.copy(alpha = .9f)
        else -> Color.White.copy(alpha = .92f)
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, stroke, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

@Composable
private fun StoryTextGradientStopChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = .25f),
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    .clickable {
                        onSelect(raw)
                        HapticManager.shared.lightImpact()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
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
    fun selectContext(context: StoryTextEditorContext) {
        onActiveContextChange(
            if (activeContext == context && context != StoryTextEditorContext.FONTS) {
                StoryTextEditorContext.FONTS
            } else {
                context
            },
        )
        HapticManager.shared.lightImpact()
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(StoryTextEditorChrome.toolbarHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(StoryTextEditorChrome.toolbarFill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTextToolbarItem(
            label = if (forcesAllCaps || styleUsesCaps) "AA" else "Aa",
            active = activeContext == StoryTextEditorContext.FONTS,
            onTap = { selectContext(StoryTextEditorContext.FONTS) },
            onLongPress = {
                onForcesAllCapsChange(!forcesAllCaps)
                HapticManager.shared.mediumImpact()
            },
        )
        StoryTextToolbarDivider()
        StoryTextToolbarAccessory(
            active = activeContext == StoryTextEditorContext.COLORS,
            onTap = { selectContext(StoryTextEditorContext.COLORS) },
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Red),
                        ),
                    )
                    .border(1.2.dp, Color.White.copy(alpha = .9f), CircleShape),
            )
        }
        StoryTextToolbarDivider()
        StoryTextToolbarIconItem(
            // SF Symbol `text.line.first.and.arrowtriangle.forward` ≈ play/motion cue.
            icon = Icons.Filled.PlayArrow,
            active = activeContext == StoryTextEditorContext.MOTION,
            onTap = { selectContext(StoryTextEditorContext.MOTION) },
        )
        StoryTextToolbarDivider()
        StoryTextToolbarVisualItem(
            active = activeContext == StoryTextEditorContext.VISUAL,
            onTap = { selectContext(StoryTextEditorContext.VISUAL) },
        )
        StoryTextToolbarDivider()
        val alignIcon = when (textAlignmentRaw.lowercase()) {
            "leading", "left" -> Icons.AutoMirrored.Filled.FormatAlignLeft
            "trailing", "right" -> Icons.AutoMirrored.Filled.FormatAlignRight
            else -> Icons.Filled.FormatAlignCenter
        }
        StoryTextToolbarIconItem(icon = alignIcon, active = true, onTap = onCycleAlignment)
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
    Box(
        Modifier
            .weight(1f)
            .height(StoryTextEditorChrome.toolbarHeight)
            .pointerInput(onTap, onLongPress) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = onLongPress?.let { { it() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = .55f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RowScope.StoryTextToolbarIconItem(
    icon: ImageVector,
    active: Boolean,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(StoryTextEditorChrome.toolbarHeight)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color.White else Color.White.copy(alpha = .55f),
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun RowScope.StoryTextToolbarAccessory(
    active: Boolean,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredActive = active
    Box(
        Modifier
            .weight(1f)
            .height(StoryTextEditorChrome.toolbarHeight)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun RowScope.StoryTextToolbarVisualItem(
    active: Boolean,
    onTap: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .height(StoryTextEditorChrome.toolbarHeight)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Text(
                "A",
                color = if (active) Color.White else Color.White.copy(alpha = .55f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "✦",
                color = if (active) Color(0xFFFFD60A) else Color.White.copy(alpha = .7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
private fun StoryTextToolbarDivider() {
    Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color.White.copy(alpha = .12f)))
}

@Composable
private fun RowScope.StoryTextBackgroundToolbarItem(
    fillRaw: String,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    val normalized = fillRaw.lowercase()
    val previewFill = when (normalized) {
        "solid" -> selectedColor
        "semitransparent" -> selectedColor.copy(alpha = .70f)
        "inverted" -> if (StoryTextAttributesBuilder.contrastColor(selectedColor) == Color.Black) {
            Color.White
        } else {
            Color.Black
        }
        else -> Color.Transparent
    }
    val textForeground = when (normalized) {
        "none" -> Color.White
        "solid", "semitransparent" -> StoryTextAttributesBuilder.contrastColor(selectedColor)
        "inverted" -> selectedColor
        else -> Color.White
    }
    Box(
        Modifier
            .weight(1f)
            .height(StoryTextEditorChrome.toolbarHeight)
            .clickable {
                onClick()
                HapticManager.shared.lightImpact()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 22.dp, height = 18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(previewFill)
                .then(
                    if (normalized == "none") {
                        Modifier.border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(5.dp))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = textForeground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            onActiveContextChange = onActiveContextChange,
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
