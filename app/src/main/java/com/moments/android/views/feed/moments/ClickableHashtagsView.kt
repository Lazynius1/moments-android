package com.moments.android.views.feed.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.moments.android.utilities.legacyPoppinsSize

private val HashtagColor = Color(0xFF667EEA)

enum class ContentPartType { Text, Hashtag }

data class ContentPart(
    val content: String,
    val type: ContentPartType,
)

data class WordItem(val content: String)

data class WordLine(val words: List<WordItem>)

/** Port de `parseContentForHashtags` (ClickableHashtagsView.swift). */
fun parseContentForHashtags(content: String): List<ContentPart> {
    val parts = mutableListOf<ContentPart>()
    val words = content.split(Regex("[\\s\\n]+")).filter { it.isNotEmpty() }
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

/** Port de `ClickableHashtagsView.swift`. */
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
    val lines = content.split('\n').ifEmpty { listOf(content) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            val annotated = buildAnnotatedString {
                parseContentForHashtags(line).forEach { part ->
                    when (part.type) {
                        ContentPartType.Hashtag -> {
                            pushStringAnnotation(tag = "hashtag", annotation = part.content.removePrefix("#"))
                            withStyle(
                                SpanStyle(
                                    color = HashtagColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = bodySize,
                                ),
                            ) {
                                append(part.content)
                            }
                            pop()
                        }
                        ContentPartType.Text -> append(part.content)
                    }
                }
            }
            ClickableText(
                text = annotated,
                style = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = bodySize),
                onClick = { offset ->
                    annotated.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                        onHashtagTap(it.item)
                    }
                },
            )
        }
    }
}

/** Port de `ClickableHashtagsHStackView` — hashtags con cápsula. */
@OptIn(ExperimentalLayoutApi::class)
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

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        parseContentForHashtags(content.replace("\n", " ")).forEach { part ->
            when (part.type) {
                ContentPartType.Hashtag -> {
                    Text(
                        text = part.content,
                        color = HashtagColor,
                        fontSize = bodySize,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(HashtagColor.copy(alpha = 0.1f), chipShape)
                            .border(1.dp, HashtagColor.copy(alpha = 0.3f), chipShape)
                            .clickable { onHashtagTap(part.content.removePrefix("#")) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                ContentPartType.Text -> {
                    if (part.content.isNotBlank()) {
                        Text(text = part.content, color = textColor, fontSize = bodySize)
                    }
                }
            }
        }
    }
}
