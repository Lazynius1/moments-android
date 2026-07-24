package com.moments.android.views.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.moments.android.utilities.MomentMentionParser

/** Parser Unicode equivalente a `MomentHashtagParser`. */
object MomentHashtagParser {
    val hashtagColor = Color(0xFF667EEA)
    private val pattern = Regex("""(?<![\p{L}\p{M}\p{N}_])#([\p{L}\p{M}\p{N}_]+)""")
    data class Match(val range: IntRange, val term: String)
    fun matchesIn(content: String): List<Match> = pattern.findAll(content).map { Match(it.range, it.groupValues[1]) }.toList()
    fun extractHashtags(content: String): List<String> = matchesIn(content).map { it.term }.filter { it.length > 1 }
}

/** Port de `MomentHashtagText.swift`, incluido hashtag, mención y acción inline. */
@Composable
fun MomentHashtagText(
    content: String,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified,
    hashtagColor: Color = MomentHashtagParser.hashtagColor,
    mentionColor: Color = MomentMentionParser.mentionColor,
    fontSize: TextUnit = 14.sp,
    textAlignment: TextAlign = TextAlign.Start,
    lineLimit: Int? = null,
    shadow: Shadow? = null,
    actionText: String? = null,
    actionColor: Color = hashtagColor,
    onMentionTap: ((String) -> Unit)? = null,
    onActionTap: (() -> Unit)? = null,
) {
    data class Token(val range: IntRange, val tag: String, val value: String)
    val tokens = (MomentHashtagParser.matchesIn(content).map { Token(it.range, "hashtag", it.term) } +
        MomentMentionParser.matchesIn(content).map { Token(it.range, "mention", it.username) }).sortedBy { it.range.first }
    val annotated = buildAnnotatedString {
        var cursor = 0
        tokens.forEach { token ->
            if (token.range.first < cursor) return@forEach
            append(content.substring(cursor, token.range.first))
            val color = if (token.tag == "hashtag") hashtagColor else mentionColor
            pushStringAnnotation(token.tag, token.value)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(content.substring(token.range)) }
            pop()
            cursor = token.range.last + 1
        }
        if (cursor < content.length) append(content.substring(cursor))
        if (!actionText.isNullOrEmpty()) {
            pushStringAnnotation("action", "inline")
            withStyle(SpanStyle(color = actionColor, fontWeight = FontWeight.Bold)) { append(actionText) }
            pop()
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(color = baseColor, fontSize = fontSize, textAlign = textAlignment, shadow = shadow),
        maxLines = lineLimit ?: Int.MAX_VALUE,
        onClick = { offset ->
            annotated.getStringAnnotations("action", offset, offset).firstOrNull()?.let { onActionTap?.invoke(); return@ClickableText }
            annotated.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let { onHashtagTap(it.item); return@ClickableText }
            annotated.getStringAnnotations("mention", offset, offset).firstOrNull()?.let { onMentionTap?.invoke(it.item) }
        },
    )
}
