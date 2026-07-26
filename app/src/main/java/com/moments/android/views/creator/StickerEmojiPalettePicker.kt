package com.moments.android.views.creator

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R

/** Espejo de `StickerEmojiOption` (StickerEmojiPalettePicker.swift). */
data class StickerEmojiOption(
    val emoji: String,
    val skinToneVariants: List<String>,
    val category: StickerEmojiCategory,
) {
    val id: String get() = emoji
}

/**
 * Port de `StickerEmojiCategory`.
 * Strings ≡ `stickerview.emojiPicker.category.*`.
 */
enum class StickerEmojiCategory {
    SMILEYS, PEOPLE, NATURE, FOOD, ACTIVITIES, TRAVEL, OBJECTS, SYMBOLS, FLAGS;

    val titleRes: Int
        get() = when (this) {
            SMILEYS -> R.string.sticker_emoji_cat_smileys
            PEOPLE -> R.string.sticker_emoji_cat_people
            NATURE -> R.string.sticker_emoji_cat_nature
            FOOD -> R.string.sticker_emoji_cat_food
            ACTIVITIES -> R.string.sticker_emoji_cat_activities
            TRAVEL -> R.string.sticker_emoji_cat_travel
            OBJECTS -> R.string.sticker_emoji_cat_objects
            SYMBOLS -> R.string.sticker_emoji_cat_symbols
            FLAGS -> R.string.sticker_emoji_cat_flags
        }

    /** ≡ SF Symbol `icon` (aproximación Material). */
    val icon: ImageVector
        get() = when (this) {
            SMILEYS -> Icons.Filled.EmojiEmotions
            PEOPLE -> Icons.Filled.People
            NATURE -> Icons.Filled.Park
            FOOD -> Icons.Filled.Restaurant
            ACTIVITIES -> Icons.Filled.SportsEsports
            TRAVEL -> Icons.Filled.DirectionsCar
            OBJECTS -> Icons.Filled.Lightbulb
            SYMBOLS -> Icons.Filled.Favorite
            FLAGS -> Icons.Filled.Flag
        }
}

/**
 * Port de `IOSSystemEmojiCatalog` — scan Unicode + extras + skin tones (ICU).
 * `emojis(category)` se mantiene para call sites (stickerview / inputs).
 */
object StickerEmojiCatalog {
    private val toneModifiers = intArrayOf(0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)

    val options: List<StickerEmojiOption> by lazy { build() }

    fun emojis(category: StickerEmojiCategory): List<String> =
        options.filter { it.category == category }.map { it.emoji }

    private fun build(): List<StickerEmojiOption> {
        val result = ArrayList<StickerEmojiOption>(2_500)
        val seen = HashSet<String>(2_500)

        fun append(emoji: String) {
            if (emoji.isEmpty() || !seen.add(emoji)) return
            result += StickerEmojiOption(
                emoji = emoji,
                skinToneVariants = skinToneVariants(emoji),
                category = category(emoji),
            )
        }

        val scalarRanges = listOf(
            0x1F1E6..0x1F1FF,
            0x1F300..0x1F5FF,
            0x1F600..0x1F64F,
            0x1F680..0x1F6FF,
            0x1F700..0x1F77F,
            0x1F780..0x1F7FF,
            0x1F800..0x1F8FF,
            0x1F900..0x1F9FF,
            0x1FA70..0x1FAFF,
            0x2600..0x26FF,
            0x2700..0x27BF,
        )

        for (range in scalarRanges) {
            for (value in range) {
                if (!isEmoji(value) || isEmojiModifier(value)) continue
                append(String(Character.toChars(value)))
            }
        }

        val extras = listOf(
            "❤️", "🩷", "🩵", "🩶", "🫶", "☠️", "☹️", "☺️", "✌️", "☝️",
            "✍️", "⭐", "✨", "⚡", "☄️", "☀️", "☁️", "⛅", "☔", "❄️",
            "☕", "⚽", "⚾", "⛳", "⌚", "☎️", "⌨️", "✈️", "⌛", "⏰",
            "⌛️", "⏳", "™️", "©️", "®️", "‼️", "⁉️", "〰️", "➕", "➖",
            "➗", "✖️", "♾️", "♻️", "⚠️", "❣️", "💟", "🗯️", "🫠", "🫨",
            "👨‍💻", "👩‍💻", "🧑‍💻", "👨‍🚀", "👩‍🚀", "🧑‍🚀", "👨‍🎤", "👩‍🎤",
            "🧑‍🎤", "👨‍🍳", "👩‍🍳", "🧑‍🍳", "👨‍🎨", "👩‍🎨", "🧑‍🎨", "👨‍⚕️",
            "👩‍⚕️", "🧑‍⚕️", "👨‍🏫", "👩‍🏫", "🧑‍🏫", "👨‍🌾", "👩‍🌾", "🧑‍🌾",
            "👨‍🔧", "👩‍🔧", "🧑‍🔧", "👨‍🚒", "👩‍🚒", "🧑‍🚒", "👨‍✈️", "👩‍✈️",
            "🧑‍✈️", "👨‍⚖️", "👩‍⚖️", "🧑‍⚖️", "👨‍🎓", "👩‍🎓", "🧑‍🎓", "👨‍🍼",
            "👩‍🍼", "🧑‍🍼", "👨‍🦽", "👩‍🦽", "🧑‍🦽", "👨‍🦯", "👩‍🦯", "🧑‍🦯",
            "👨‍🦼", "👩‍🦼", "🧑‍🦼", "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩",
        )
        extras.forEach(::append)
        return result
    }

    private fun skinToneVariants(emoji: String): List<String> {
        if (!supportsSkinTone(emoji)) return emptyList()
        return toneModifiers.map { applyingSkinTone(it, emoji) }.filterNotNull()
    }

    private fun category(emoji: String): StickerEmojiCategory {
        val scalars = emoji.codePoints().toArray()
        if (scalars.any { it in 0x1F1E6..0x1F1FF }) return StickerEmojiCategory.FLAGS
        if (supportsSkinTone(emoji) ||
            emoji.contains("🧑") || emoji.contains("👨") ||
            emoji.contains("👩") || emoji.contains("👶")
        ) {
            return StickerEmojiCategory.PEOPLE
        }
        val first = scalars.firstOrNull()
        if (first != null) {
            when (first) {
                in 0x1F600..0x1F64F -> return StickerEmojiCategory.SMILEYS
                in 0x1F300..0x1F32C, in 0x2600..0x26FF -> return StickerEmojiCategory.NATURE
                in 0x1F32D..0x1F37F, in 0x1F950..0x1F96F -> return StickerEmojiCategory.FOOD
                in 0x1F380..0x1F3CF, in 0x1F93C..0x1F945 -> return StickerEmojiCategory.ACTIVITIES
                in 0x1F680..0x1F6FF, in 0x1F30D..0x1F30F -> return StickerEmojiCategory.TRAVEL
                in 0x1F4A1..0x1F5FF, in 0x1F9F0..0x1F9FF -> return StickerEmojiCategory.OBJECTS
                0x2764, in 0x1F494..0x1F49F, in 0x1F500..0x1F53D -> return StickerEmojiCategory.SYMBOLS
            }
        }
        if (emoji in setOf(
                "❤️", "🩷", "🩵", "🩶", "💟", "❣️", "‼️", "⁉️",
                "〰️", "➕", "➖", "➗", "✖️", "♾️", "♻️", "⚠️",
            )
        ) {
            return StickerEmojiCategory.SYMBOLS
        }
        return StickerEmojiCategory.OBJECTS
    }

    private fun supportsSkinTone(emoji: String): Boolean =
        emoji.codePoints().anyMatch { isEmojiModifierBase(it) }

    private fun applyingSkinTone(modifier: Int, emoji: String): String? {
        val scalars = emoji.codePoints().toArray()
        val baseIndex = scalars.indexOfFirst { isEmojiModifierBase(it) }
        if (baseIndex < 0) return null

        val out = ArrayList<Int>(scalars.size + 1)
        var inserted = false
        for (index in scalars.indices) {
            out += scalars[index]
            if (index == baseIndex) {
                val nextIndex = index + 1
                if (nextIndex < scalars.size && scalars[nextIndex] == 0xFE0F) {
                    continue
                }
                out += modifier
                inserted = true
            } else if (inserted && scalars[index] == 0xFE0F) {
                continue
            }
        }
        if (!inserted) return null
        return buildString {
            for (cp in out) appendCodePoint(cp)
        }
    }

    private fun isEmoji(cp: Int): Boolean =
        UCharacter.hasBinaryProperty(cp, UProperty.EMOJI)

    private fun isEmojiModifier(cp: Int): Boolean =
        UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_MODIFIER)

    private fun isEmojiModifierBase(cp: Int): Boolean =
        UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_MODIFIER_BASE)
}

/**
 * Port de `StickerEmojiPalettePicker` (L206–373).
 * Categorías + grid 7 cols + long-press skin tones tray.
 */
@Composable
fun StickerEmojiPalettePicker(
    selectedEmoji: String,
    onSelectedEmojiChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    var activeVariantSourceId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(StickerEmojiCategory.SMILEYS) }

    val backgroundFill = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f)
    val variantTrayFill = if (isDark) {
        Color(11 / 255f, 18 / 255f, 21 / 255f)
    } else {
        Color(250 / 255f, 249 / 255f, 246 / 255f)
    }
    val borderColor = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.08f)
    val cellBorder = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    val selectedBorder = Color(0.99f, 0.56f, 0.21f)
    val categoryText = if (isDark) Color.White else Color.Black.copy(0.82f)

    val catalog = remember { StickerEmojiCatalog.options }
    val filtered = remember(selectedCategory, catalog) {
        catalog.filter { it.category == selectedCategory }
    }
    val activeVariantOption = remember(activeVariantSourceId, catalog) {
        catalog.firstOrNull { it.id == activeVariantSourceId }
    }
    val trayOpen = activeVariantOption != null &&
        !activeVariantOption.skinToneVariants.isEmpty()

    Column(
        modifier
            .height(if (trayOpen) 384.dp else 320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundFill)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(StickerEmojiCategory.entries, key = { it.name }) { category ->
                val selected = selectedCategory == category
                Row(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) selectedBorder else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) selectedBorder else cellBorder,
                            RoundedCornerShape(50),
                        )
                        .clickable {
                            activeVariantSourceId = null
                            selectedCategory = category
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = if (selected) Color.White else categoryText,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(category.titleRes),
                        color = if (selected) Color.White else categoryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = if (trayOpen) 0.dp else 14.dp),
        ) {
            items(filtered, key = { it.id }) { option ->
                val isSelected = selectedEmoji == option.emoji
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
                    label = "emojiScale",
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .scale(scale)
                        .then(
                            if (isSelected) {
                                Modifier.shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = selectedBorder.copy(0.25f))
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(option.id) {
                            detectTapGestures(
                                onTap = {
                                    activeVariantSourceId = null
                                    onSelectedEmojiChange(option.emoji)
                                    onSelect(option.emoji)
                                },
                                onLongPress = {
                                    if (option.skinToneVariants.isNotEmpty()) {
                                        activeVariantSourceId = option.id
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.emoji, fontSize = 28.sp, textAlign = TextAlign.Center)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 3.dp)
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) selectedBorder else Color.Transparent),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = trayOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val option = activeVariantOption!!
            Row(
                Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(variantTrayFill)
                    .border(1.dp, cellBorder, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(option.emoji, fontSize = 22.sp, modifier = Modifier.size(28.dp), textAlign = TextAlign.Center)
                option.skinToneVariants.forEach { variant ->
                    Text(
                        variant,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                onSelectedEmojiChange(variant)
                                activeVariantSourceId = null
                                onSelect(variant)
                            },
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .clickable { activeVariantSourceId = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = categoryText.copy(0.72f),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}
