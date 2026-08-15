package com.moments.android.views.creator

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.HapticManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GRID_COLUMNS = 6
private const val MAX_RECENT_COUNT = 12

private val SKIN_TONES = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")

private data class EmojiCategory(
    @StringRes val titleRes: Int,
    val emojis: List<String>,
)

/**
 * Port de `EmojiPickerView` + `SkinToneBubble` (storyeditor.swift L2925–3239).
 * Sheet de emojis premium: recientes, categorías 6 cols, long-press tonos de piel.
 */
@Composable
fun EmojiPickerView(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val tracker = remember { EmojiUsageTracker() }
    val revision by tracker.revision.collectAsState()
    val recentEmojis = remember(revision) { tracker.recentlyUsed(limit = MAX_RECENT_COUNT) }
    val categories = EmojiPickerCategories

    var selectedBaseEmoji by remember { mutableStateOf<String?>(null) }
    val emojiFrames = remember { mutableStateMapOf<String, Rect>() }

    val secondaryText = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
    val closeTextColor = if (isDark) Color.White else Color.Black

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val totalHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.story_editor_emoji_picker_title),
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = closeTextColor,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(
                        text = stringResource(R.string.story_editor_emoji_picker_close),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = closeTextColor,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (recentEmojis.isNotEmpty()) {
                    item(key = "recent-header") {
                        Text(
                            text = stringResource(R.string.chat_giphy_recents),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryText,
                        )
                    }
                    items(
                        items = recentEmojis.chunked(GRID_COLUMNS),
                        key = { row -> "recent-${row.firstOrNull().orEmpty()}" },
                    ) { row ->
                        EmojiGridRow(
                            emojis = row,
                            emojiFrames = emojiFrames,
                            selectedBaseEmoji = selectedBaseEmoji,
                            onDismissSkinTone = { selectedBaseEmoji = null },
                            onSelect = { emoji ->
                                onSelect(emoji)
                                onDismiss()
                            },
                            onShowSkinTone = { emoji ->
                                selectedBaseEmoji = emoji
                                HapticManager.shared.mediumImpact()
                            },
                        )
                    }
                }

                categories.forEach { category ->
                    if (category.emojis.isEmpty()) return@forEach
                    item(key = "header-${category.titleRes}") {
                        Text(
                            text = stringResource(category.titleRes),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryText,
                        )
                    }
                    items(
                        items = category.emojis.chunked(GRID_COLUMNS),
                        key = { row -> "${category.titleRes}-${row.firstOrNull().orEmpty()}" },
                    ) { row ->
                        EmojiGridRow(
                            emojis = row,
                            emojiFrames = emojiFrames,
                            selectedBaseEmoji = selectedBaseEmoji,
                            onDismissSkinTone = { selectedBaseEmoji = null },
                            onSelect = { emoji ->
                                onSelect(emoji)
                                onDismiss()
                            },
                            onShowSkinTone = { emoji ->
                                selectedBaseEmoji = emoji
                                HapticManager.shared.mediumImpact()
                            },
                        )
                    }
                }

                item(key = "bottom-spacer") {
                    Box(Modifier.padding(bottom = 16.dp))
                }
            }
        }

        val baseEmoji = selectedBaseEmoji
        AnimatedVisibility(
            visible = baseEmoji != null,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                scaleIn(
                    initialScale = 0.6f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                scaleOut(
                    targetScale = 0.6f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                ),
        ) {
            if (baseEmoji != null) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { selectedBaseEmoji = null },
                    )

                    val frame = emojiFrames[baseEmoji]
                    val anchor = popoverOffset(
                        frame = frame,
                        totalWidthPx = totalWidthPx,
                        totalHeightPx = totalHeightPx,
                        skinToneCount = SKIN_TONES.size,
                        density = density,
                    )
                    val bubbleWidthPx = with(density) { (SKIN_TONES.size * 52 + 24).dp.toPx() }
                    val bubbleHeightPx = with(density) { 72.dp.toPx() }

                    SkinToneBubble(
                        base = baseEmoji,
                        onSelect = { variant ->
                            onSelect(variant)
                            selectedBaseEmoji = null
                            onDismiss()
                        },
                        modifier = Modifier.offset {
                            IntOffset(
                                (anchor.x - bubbleWidthPx / 2f).roundToInt(),
                                (anchor.y - bubbleHeightPx / 2f).roundToInt(),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiGridRow(
    emojis: List<String>,
    emojiFrames: MutableMap<String, Rect>,
    selectedBaseEmoji: String?,
    onDismissSkinTone: () -> Unit,
    onSelect: (String) -> Unit,
    onShowSkinTone: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(GRID_COLUMNS) { index ->
            val emoji = emojis.getOrNull(index)
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (emoji != null) {
                    EmojiPickerCell(
                        emoji = emoji,
                        onFrameChange = { rect -> emojiFrames[emoji] = rect },
                        onTap = {
                            if (selectedBaseEmoji != null) {
                                onDismissSkinTone()
                            } else {
                                onSelect(emoji)
                            }
                        },
                        onLongPress = {
                            if (isSkinToneSupported(emoji)) {
                                onShowSkinTone(emoji)
                            } else {
                                onSelect(emoji)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerCell(
    emoji: String,
    onFrameChange: (Rect) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Text(
        text = emoji,
        fontSize = 36.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .onGloballyPositioned { coordinates ->
                onFrameChange(coordinates.boundsInRoot())
            }
            .pointerInput(emoji) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
    )
}

@Composable
private fun SkinToneBubble(
    base: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toneDescription = stringResource(R.string.story_editor_emoji_picker_select_tone)
    Row(
        modifier
            .semantics { contentDescription = toneDescription }
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SKIN_TONES.forEachIndexed { index, modifierSuffix ->
            val variant = base + modifierSuffix
            val interactionSource = remember(variant) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 1.18f else 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                label = "skinToneScale",
            )
            Text(
                text = variant,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(46.dp)
                    .scale(scale)
                    .momentEmojiScaleClickable(
                        interactionSource = interactionSource,
                        onClick = { onSelect(variant) },
                    ),
            )
        }
    }
}

@Composable
private fun Modifier.momentEmojiScaleClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)

private fun popoverOffset(
    frame: Rect?,
    totalWidthPx: Float,
    totalHeightPx: Float,
    skinToneCount: Int,
    density: androidx.compose.ui.unit.Density,
): Offset {
    if (frame == null) {
        return Offset(totalWidthPx / 2f, totalHeightPx / 2f)
    }
    val bubbleWidthPx = with(density) { (skinToneCount * 52 + 24).dp.toPx() }
    val bubbleHeightPx = with(density) { 72.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }
    val topGuardPx = with(density) { 80.dp.toPx() }
    val verticalGapPx = with(density) { 12.dp.toPx() }

    var x = frame.center.x
    x = max(
        bubbleWidthPx / 2f + marginPx,
        min(totalWidthPx - bubbleWidthPx / 2f - marginPx, x),
    )

    var y = frame.top - bubbleHeightPx / 2f - verticalGapPx
    if (y < topGuardPx) {
        y = frame.bottom + bubbleHeightPx / 2f + verticalGapPx
    }
    return Offset(x, y)
}

private fun isSkinToneSupported(emoji: String): Boolean {
    val firstCodePoint = emoji.codePointAt(0)
    return when (firstCodePoint) {
        in 0x1F442..0x1F44F,
        0x1F450,
        in 0x1F466..0x1F487,
        in 0x1F48F..0x1F490,
        in 0x1F645..0x1F64F,
        0x1F6A3,
        in 0x1F6B4..0x1F6B6,
        0x1F90C, 0x1F90F,
        in 0x1F918..0x1F91F,
        0x1F926,
        in 0x1F930..0x1F93E,
        0x1F977,
        in 0x1F9B5..0x1F9B6,
        in 0x1F9C1..0x1F9C2,
        in 0x1F9D1..0x1F9FF,
        in 0x270A..0x270D,
        -> true
        else -> false
    }
}

private val EmojiPickerCategories: List<EmojiCategory> by lazy { buildEmojiCategories() }

/** Catálogo compartido por el sheet y el selector inline del menú contextual. */
internal fun emojiPickerCatalog(): List<String> = EmojiPickerCategories
    .flatMap(EmojiCategory::emojis)
    .distinct()

internal fun emojiSupportsSkinTone(emoji: String): Boolean = isSkinToneSupported(emoji)

internal fun emojiWithoutSkinTone(emoji: String): String = buildString {
    emoji.codePoints().forEach { codePoint ->
        if (codePoint !in 0x1F3FB..0x1F3FF) appendCodePoint(codePoint)
    }
}

private fun buildEmojiCategories(): List<EmojiCategory> {
    val reactionEmojis = listOf(
        "😍", "🔥", "😂", "🥹", "❤️", "👏", "🙌", "🎉", "🤔", "💯", "✨", "👀",
        "🚀", "💀", "😭", "🥳", "😎", "🥺", "🥰", "🧁", "🙄", "😴", "😮‍💨", "🫠",
        "🤐", "🤯", "💔", "🌟", "🎈",
    )

    val categoryMap = linkedMapOf(
        "reactions" to reactionEmojis.toMutableList(),
        "faces" to mutableListOf(),
        "nature" to mutableListOf(),
        "food" to mutableListOf(),
        "activities" to mutableListOf(),
        "travel" to mutableListOf(),
        "objects" to mutableListOf(),
        "symbols" to mutableListOf(),
    )

    fun getCategoryKey(code: Int): String? = when (code) {
        in 0x1F600..0x1F64F -> "faces"
        in 0x1F440..0x1F487, in 0x1F90C..0x1F93F, in 0x1F970..0x1F97F -> "faces"
        in 0x1F400..0x1F43F, in 0x1F980..0x1F9AE, in 0x1F330..0x1F353 -> "nature"
        in 0x1F354..0x1F37F, in 0x1F9C0..0x1F9CF -> "food"
        in 0x1F3A0..0x1F3C4, in 0x1F940..0x1F94F -> "activities"
        in 0x1F680..0x1F6C5, in 0x1F300..0x1F32F, in 0x1F3E0..0x1F3F0 -> "travel"
        in 0x1F4A0..0x1F4FF, in 0x1F500..0x1F5FF, in 0x1F9E0..0x1F9FF, in 0x1FA90..0x1FAAF -> "objects"
        in 0x2700..0x27BF, in 0x1F490..0x1F49F, in 0x2600..0x26FF -> "symbols"
        else -> null
    }

    for (code in 0x2600..0x1FAFF) {
        val key = getCategoryKey(code) ?: continue
        if (UCharacter.hasBinaryProperty(code, UProperty.EMOJI_PRESENTATION)) {
            categoryMap.getValue(key).add(String(Character.toChars(code)))
        }
    }

    return listOf(
        EmojiCategory(R.string.story_editor_emoji_picker_reactions, categoryMap["reactions"] ?: reactionEmojis),
        EmojiCategory(R.string.story_editor_emoji_picker_faces, categoryMap["faces"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_nature, categoryMap["nature"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_food, categoryMap["food"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_activities, categoryMap["activities"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_travel, categoryMap["travel"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_objects, categoryMap["objects"].orEmpty()),
        EmojiCategory(R.string.story_editor_emoji_picker_symbols, categoryMap["symbols"].orEmpty()),
    )
}
