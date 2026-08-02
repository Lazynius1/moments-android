package com.moments.android.views.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.revealContrastingEffectColor
import com.moments.android.extensions.toHex
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryColorPickerPanel
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.story.RevealSurfaceView
import kotlin.math.roundToInt

/**
 * Port del MARK `REVEAL STICKER EDITOR` en `CreatorView.swift`
 * (`RevealStickerEditorView` + `RevealStickerBottomControlsInset` + controls + presets).
 */

data class RevealPreset(
    val id: String,
    val name: String,
    val type: String,
    val pattern: String,
    val primary: String,
    val secondary: String,
    val effect: String,
)

val revealPresets: List<RevealPreset> = listOf(
    RevealPreset("classic", "Classic", "solid", "dots", "#000000", "#000000", "#FFFFFF"),
    RevealPreset("midnight", "Midnight", "solid", "grid", "#0B1215", "#0B1215", "#7EC8FF"),
    RevealPreset("golden", "Golden", "gradient", "noise", "#BF953F", "#8E6E2D", "#FFF4D6"),
    RevealPreset("neon", "Neon Glow", "gradient", "lines", "#430089", "#82009F", "#FF8AF8"),
    RevealPreset("silver", "Silver", "gradient", "dots", "#C0C0C0", "#708090", "#FFFFFF"),
    RevealPreset("retro", "Old TV", "solid", "static", "#FFFFFF", "#FFFFFF", "#2B2B2B"),
    RevealPreset("matrix", "Matrix", "solid", "matrix", "#000000", "#000000", "#00FF41"),
    RevealPreset("blueprint", "Blueprint", "solid", "grid", "#003366", "#003366", "#8FD3FF"),
    RevealPreset("magic", "Magic", "solid", "holographic", "#C8C8C8", "#C8C8C8", "#FF6AD5"),
)

private val revealPatternIds = listOf(
    "none", "dots", "noise", "static", "scanlines", "grid", "lines", "waves", "matrix", "holographic",
)

@Composable
fun RevealStickerEditorView(
    stickers: List<StoryStickerDraft>,
    editingId: String?,
    onEditingIdChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sticker = stickers.firstOrNull { it.id == editingId }
    val corner = storyViewerCanvasCornerRadius
    // X / Listo van sobre chrome glass → contentColor (claro/oscuro). Título sobre la superficie reveal.
    val isDark = isSystemInDarkTheme()
    val chromeFg = MomentsChromeGlass.contentColor(isDark)
    val surfaceColor = Color.fromHex(sticker?.revealPrimaryColor ?: "#000000")
    val titleFg = if (surfaceColor.luminance() > 0.55f) Color.Black else Color.White
    Box(modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
        if (sticker != null) {
            RevealSurfaceView(
                type = sticker.revealType,
                pattern = sticker.revealPattern,
                primaryColor = sticker.revealPrimaryColor,
                secondaryColor = sticker.revealSecondaryColor,
                effectColor = sticker.revealEffectColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable { onEditingIdChange(null) }
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = chromeFg)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.reveal_editor_title),
                color = titleFg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.common_done),
                color = chromeFg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable { onEditingIdChange(null) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun RevealStickerBottomControlsInset(
    stickers: List<StoryStickerDraft>,
    onStickersChange: (List<StoryStickerDraft>) -> Unit,
    editingId: String?,
    onEditingIdChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White.copy(0.96f) else Color.Black.copy(0.86f)
    Column(
        modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(26.dp), interactive = false)
            .padding(horizontal = 16.dp)
            .padding(top = 15.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.reveal_editor_title),
            color = primaryText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RevealStickerControlsContent(
            stickers = stickers,
            onStickersChange = onStickersChange,
            editingId = editingId,
            presetPreviewWidth = 86.dp,
            presetPreviewHeight = 126.dp,
            presetsHeight = 156.dp,
        )
    }
    // onEditingIdChange reserved for parity with iOS Binding; dismiss via editor header.
    @Suppress("UNUSED_VARIABLE")
    val keep = onEditingIdChange
}

@Composable
private fun RevealStickerControlsContent(
    stickers: List<StoryStickerDraft>,
    onStickersChange: (List<StoryStickerDraft>) -> Unit,
    editingId: String?,
    presetPreviewWidth: androidx.compose.ui.unit.Dp,
    presetPreviewHeight: androidx.compose.ui.unit.Dp,
    presetsHeight: androidx.compose.ui.unit.Dp,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black
    val secondaryText = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.62f)
    val tertiaryText = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.48f)
    val tabInactive = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.54f)
    val tabActive = if (isDark) Color.White.copy(0.96f) else Color.Black.copy(0.86f)
    val chipBg = if (isDark) Color.White else Color.Black
    val chipInactiveBg = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f)
    val chipActiveText = if (isDark) Color.Black else Color.White
    val circleBtnBg = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f)

    var selectedTab by remember { mutableStateOf(RevealEditorTab.PRESETS) }
    var selectedPresetId by remember { mutableStateOf("classic") }
    var tabTransientOffset by remember { mutableFloatStateOf(0f) }
    var customType by remember { mutableStateOf("solid") }
    var customPattern by remember { mutableStateOf("dots") }
    var customPrimary by remember { mutableStateOf(Color.Black) }
    var customSecondary by remember { mutableStateOf(Color.Black) }
    var customEffect by remember { mutableStateOf(Color.White) }
    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    fun currentIndex(): Int? = stickers.indexOfFirst { it.id == editingId }.takeIf { it >= 0 }

    fun updateSticker(type: String, pattern: String, primary: String, secondary: String, effect: String?) {
        val index = currentIndex() ?: return
        onStickersChange(
            stickers.mapIndexed { i, item ->
                if (i != index) item
                else item.copy(
                    revealType = type,
                    revealPattern = pattern,
                    revealPrimaryColor = primary,
                    revealSecondaryColor = secondary,
                    revealEffectColor = effect,
                )
            },
        )
    }

    fun ensureContrastingEffectColorIfNeeded() {
        if (customPattern == "none") return
        if (customPrimary.toHex().equals(customEffect.toHex(), ignoreCase = true)) {
            customEffect = customPrimary.revealContrastingEffectColor()
        }
    }

    fun updateCustomColors() {
        // iOS `Color.toHex()` sin `#`; presets sí llevan `#` — RevealSurface acepta ambos.
        updateSticker(
            type = customType,
            pattern = customPattern,
            primary = customPrimary.toHex(),
            secondary = customSecondary.toHex(),
            effect = if (customPattern == "none") null else customEffect.toHex(),
        )
    }

    fun resolvedLegacyEffectColor(sticker: StoryStickerDraft): Color {
        val secondary = sticker.revealSecondaryColor
        val primary = sticker.revealPrimaryColor
        if (!secondary.isNullOrEmpty() && primary != null &&
            !secondary.equals(primary, ignoreCase = true)
        ) {
            return Color.fromHex(secondary)
        }
        return customPrimary.revealContrastingEffectColor()
    }

    fun normalizeHex(raw: String?): String =
        raw?.trim()?.removePrefix("#")?.uppercase().orEmpty()

    fun loadCurrentState() {
        val index = currentIndex() ?: return
        val sticker = stickers[index]
        customType = sticker.revealType ?: "solid"
        customPattern = sticker.revealPattern ?: "dots"
        customPrimary = Color.fromHex(sticker.revealPrimaryColor ?: "#000000")
        customSecondary = Color.fromHex(sticker.revealSecondaryColor ?: "#000000")
        customEffect = if (!sticker.revealEffectColor.isNullOrEmpty()) {
            Color.fromHex(sticker.revealEffectColor!!)
        } else {
            resolvedLegacyEffectColor(sticker)
        }
        ensureContrastingEffectColorIfNeeded()
        val match = revealPresets.firstOrNull {
            it.type == customType &&
                it.pattern == customPattern &&
                normalizeHex(it.primary) == normalizeHex(sticker.revealPrimaryColor)
        }
        if (match != null) {
            selectedPresetId = match.id
            selectedTab = RevealEditorTab.PRESETS
        } else {
            selectedTab = RevealEditorTab.CUSTOM
        }
        tabTransientOffset = 0f
    }

    fun applyPreset(preset: RevealPreset) {
        selectedPresetId = preset.id
        customPrimary = Color.fromHex(preset.primary)
        customSecondary = Color.fromHex(preset.secondary)
        customEffect = Color.fromHex(preset.effect)
        customType = preset.type
        customPattern = preset.pattern
        updateSticker(preset.type, preset.pattern, preset.primary, preset.secondary, preset.effect)
        HapticManager.shared.lightImpact()
    }

    LaunchedEffect(editingId) { loadCurrentState() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ≡ tabSelector
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .momentsChromeGlass(RoundedCornerShape(50), interactive = false),
        ) {
            val totalWidth = constraints.maxWidth.toFloat()
            val segment = (totalWidth - 6f) / RevealEditorTab.entries.size
            val start = -((RevealEditorTab.entries.size - 1) * segment) / 2f
            val currentIndex = RevealEditorTab.entries.indexOf(selectedTab).coerceAtLeast(0)
            val pillOffset = start + currentIndex * segment + tabTransientOffset
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(pillOffset.roundToInt(), 0) }
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { segment.toDp() })
                    .height(34.dp)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .background(Color.White.copy(0.055f)),
            )
            Row(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                RevealEditorTab.entries.forEach { tab ->
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable {
                                if (tab != selectedTab) HapticManager.shared.selection()
                                selectedTab = tab
                                tabTransientOffset = 0f
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (tab == RevealEditorTab.PRESETS) Icons.Filled.AutoAwesome else Icons.Filled.Tune,
                            null,
                            tint = if (tab == selectedTab) tabActive else tabInactive,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (tab == RevealEditorTab.PRESETS) R.string.reveal_editor_tab_presets
                                else R.string.reveal_editor_tab_custom,
                            ),
                            color = if (tab == selectedTab) tabActive else tabInactive,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (selectedTab == RevealEditorTab.PRESETS) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(presetsHeight)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                revealPresets.forEach { preset ->
                    Column(
                        Modifier.clickable { applyPreset(preset) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RevealSurfaceView(
                            type = preset.type,
                            pattern = preset.pattern,
                            primaryColor = preset.primary,
                            secondaryColor = preset.secondary,
                            effectColor = preset.effect,
                            modifier = Modifier
                                .size(presetPreviewWidth, presetPreviewHeight)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    2.dp,
                                    if (selectedPresetId == preset.id) Color.White else Color.White.copy(0.2f),
                                    RoundedCornerShape(16.dp),
                                ),
                        )
                        Text(
                            presetTitle(preset.id),
                            color = primaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    revealPatternIds.forEach { pattern ->
                        val selected = customPattern == pattern
                        Text(
                            patternTitle(pattern),
                            color = if (selected) chipActiveText else primaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) chipBg else chipInactiveBg)
                                .clickable {
                                    customPattern = pattern
                                    ensureContrastingEffectColorIfNeeded()
                                    updateCustomColors()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RevealColorSwatch(
                        color = customPrimary,
                        label = stringResource(
                            if (customType == "solid") R.string.reveal_editor_color_background
                            else R.string.reveal_editor_color1,
                        ),
                        labelColor = secondaryText,
                        onClick = { colorPickerTarget = "primary" },
                    )
                    if (customType == "gradient") {
                        RevealColorSwatch(
                            color = customSecondary,
                            label = stringResource(R.string.reveal_editor_color2),
                            labelColor = secondaryText,
                            onClick = { colorPickerTarget = "secondary" },
                        )
                    }
                    if (customPattern != "none") {
                        RevealColorSwatch(
                            color = customEffect,
                            label = stringResource(R.string.reveal_editor_color_effect),
                            labelColor = secondaryText,
                            onClick = { colorPickerTarget = "effect" },
                        )
                    }
                    Column(
                        Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(circleBtnBg)
                            .clickable {
                                customType = if (customType == "solid") "gradient" else "solid"
                                ensureContrastingEffectColorIfNeeded()
                                updateCustomColors()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            if (customType == "solid") Icons.Filled.Add else Icons.Filled.Remove,
                            null,
                            tint = primaryText,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (customType == "solid") "2" else "1",
                            color = primaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                    Column {
                        Text(
                            stringResource(
                                if (customType == "solid") R.string.reveal_editor_color_background
                                else R.string.reveal_editor_color1,
                            ),
                            color = primaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                        Text(
                            if (customType == "solid") {
                                stringResource(R.string.reveal_editor_tab_custom)
                            } else {
                                stringResource(R.string.reveal_editor_color2)
                            },
                            color = tertiaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        colorPickerTarget?.let { target ->
            val selected = when (target) {
                "secondary" -> customSecondary
                "effect" -> customEffect
                else -> customPrimary
            }
            StoryColorPickerPanel(
                selectedColor = selected,
                onSelectedColorChange = { next ->
                    when (target) {
                        "secondary" -> {
                            customSecondary = next
                            updateCustomColors()
                        }
                        "effect" -> {
                            customEffect = next
                            updateCustomColors()
                        }
                        else -> {
                            customPrimary = next
                            ensureContrastingEffectColorIfNeeded()
                            updateCustomColors()
                        }
                    }
                },
                swatchColors = listOf(
                    Color.Black, Color.White, Color(0xFFBF953F), Color(0xFF430089),
                    Color(0xFF00FF41), Color(0xFF7EC8FF), Color(0xFFFF6AD5),
                ),
                suggestedColors = emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = false)
                    .padding(12.dp)
                    .clickable { /* keep open */ },
            )
            Text(
                stringResource(R.string.common_done),
                color = primaryText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { colorPickerTarget = null }
                    .padding(8.dp),
            )
        }
    }
}

private enum class RevealEditorTab { PRESETS, CUSTOM }

@Composable
private fun RevealColorSwatch(
    color: Color,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White, CircleShape),
        )
        Text(label, color = labelColor, fontSize = 10.sp)
    }
}

@Composable
private fun presetTitle(id: String): String = stringResource(
    when (id) {
        "midnight" -> R.string.reveal_editor_preset_midnight
        "golden" -> R.string.reveal_editor_preset_golden
        "neon" -> R.string.reveal_editor_preset_neon
        "silver" -> R.string.reveal_editor_preset_silver
        "retro" -> R.string.reveal_editor_preset_retro
        "matrix" -> R.string.reveal_editor_preset_matrix
        "blueprint" -> R.string.reveal_editor_preset_blueprint
        "magic" -> R.string.reveal_editor_preset_magic
        else -> R.string.reveal_editor_preset_classic
    },
)

@Composable
private fun patternTitle(id: String): String = stringResource(
    when (id) {
        "none" -> R.string.reveal_editor_pattern_none
        "noise" -> R.string.reveal_editor_pattern_noise
        "static" -> R.string.reveal_editor_pattern_static
        "scanlines" -> R.string.reveal_editor_pattern_scanlines
        "grid" -> R.string.reveal_editor_pattern_grid
        "lines" -> R.string.reveal_editor_pattern_lines
        "waves" -> R.string.reveal_editor_pattern_waves
        "matrix" -> R.string.reveal_editor_pattern_matrix
        "holographic" -> R.string.reveal_editor_pattern_holographic
        else -> R.string.reveal_editor_pattern_dots
    },
)
