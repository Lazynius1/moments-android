package com.moments.android.views.feed.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.utilities.legacyPoppinsSize

private val HashtagColor = Color(0xFF667EEA)

/** Port de `ContentPart.ContentType` (ClickableHashtagsView.swift). */
enum class ContentPartType { Text, Hashtag }

/** Port de `ContentPart`. */
data class ContentPart(
    val content: String,
    val type: ContentPartType,
)

/** Port de `WordItem`. */
data class WordItem(val content: String)

/** Port de `WordLine`. */
data class WordLine(val words: List<WordItem>)

/**
 * Port de `parseContentForHashtags` (ClickableHashtagsView.swift).
 * iOS: `components(separatedBy: .whitespacesAndNewlines)` + espacio entre tokens.
 */
fun parseContentForHashtags(content: String): List<ContentPart> {
    val parts = mutableListOf<ContentPart>()
    val words = content.split(Regex("\\s+")).filter { it.isNotEmpty() }
    words.forEachIndexed { index, word ->
        if (word.startsWith("#") && word.length > 1) {
            parts += ContentPart(word, ContentPartType.Hashtag)
        } else {
            parts += ContentPart(word, ContentPartType.Text)
        }
        if (index < words.lastIndex) {
            parts += ContentPart(" ", ContentPartType.Text)
        }
    }
    return parts
}

/**
 * Port de `ClickableHashtagsView.groupWordsInLines`.
 * Nota iOS: `parseContentForHashtags` nunca emite `"\n"`, así que suele ser una sola línea.
 */
fun groupWordsInLines(content: String): List<WordLine> {
    val parts = parseContentForHashtags(content)
    val lines = mutableListOf<WordLine>()
    var currentWords = mutableListOf<WordItem>()

    for (part in parts) {
        if (part.content == "\n") {
            if (currentWords.isNotEmpty()) {
                lines += WordLine(currentWords)
                currentWords = mutableListOf()
            }
        } else {
            currentWords += WordItem(part.content)
        }
    }
    if (currentWords.isNotEmpty()) {
        lines += WordLine(currentWords)
    }
    return if (lines.isEmpty()) {
        listOf(WordLine(listOf(WordItem(content))))
    } else {
        lines
    }
}

/** Port de `ClickableHashtagsView` (ClickableHashtagsView.swift). */
@Composable
fun ClickableHashtagsView(
    content: String,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bodySize = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val textColor = if (isDarkTheme) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.9f)
    val lines = remember(content) { groupWordsInLines(content) }

    // iOS LazyVStack(alignment: .leading, spacing: 2)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { line ->
            // iOS HStack(spacing: 0) + Spacer
            Row(Modifier.fillMaxWidth()) {
                line.words.forEach { word ->
                    if (word.content.startsWith("#") && word.content.length > 1) {
                        val interaction = remember(word.content) { MutableInteractionSource() }
                        Text(
                            text = word.content,
                            color = HashtagColor,
                            fontSize = bodySize,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onHashtagTap(word.content.removePrefix("#")) },
                            ),
                        )
                    } else {
                        Text(
                            text = word.content,
                            color = textColor,
                            fontSize = bodySize,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Port de `ClickableHashtagsHStackView` + `FeedFlowLayout`.
 * iOS FeedFlowLayout = VStack (no wrap horizontal); spacing 4.
 */
@Composable
fun ClickableHashtagsHStackView(
    content: String,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val bodySize = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val textColor = if (isDarkTheme) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.9f)
    val chipShape = RoundedCornerShape(percent = 50)
    val parts = remember(content) { parseContentForHashtags(content) }

    FeedFlowLayout(modifier = modifier, spacing = 4.dp) {
        parts.forEach { part ->
            when (part.type) {
                ContentPartType.Text -> {
                    Text(
                        text = part.content,
                        color = textColor,
                        fontSize = bodySize,
                    )
                }
                ContentPartType.Hashtag -> {
                    val interaction = remember(part.content) { MutableInteractionSource() }
                    Text(
                        text = part.content,
                        color = HashtagColor,
                        fontSize = bodySize,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(HashtagColor.copy(alpha = 0.1f), chipShape)
                            .border(1.dp, HashtagColor.copy(alpha = 0.3f), chipShape)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onHashtagTap(part.content.removePrefix("#")) },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Port de `FeedFlowLayout` (ClickableHashtagsView.swift) — VStack, no FlowRow. */
@Composable
fun FeedFlowLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.Start,
    ) {
        content()
    }
}
