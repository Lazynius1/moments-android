package com.moments.android.views.nova.novasections

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.moments.android.views.nova.novacore.NovaColors

/** Divide texto en trozos "naturales" para animar la escritura de Nova. */
internal fun naturalChunks(text: String) = text.split(" ").flatMap { word -> if ('\n' in word) word.split('\n').mapIndexed { i, part -> if (i == 0) part else "\n$part" } else listOf("$word ") }.filter(String::isNotEmpty)

/** Aplica negrita/cursiva/monospace inline a partir de marcado markdown-lite. */
@Composable
internal fun inlineNovaFormatting(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val regex = Regex("\\*\\*([^*]+)\\*\\*|(?<!\\*)\\*([^*]+)\\*(?!\\*)|`([^`]+)`")
    regex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val style = if (match.groupValues[1].isNotEmpty()) {
            SpanStyle(fontWeight = FontWeight.Bold)
        } else if (match.groupValues[2].isNotEmpty()) {
            SpanStyle(fontStyle = FontStyle.Italic)
        } else {
            SpanStyle(fontFamily = FontFamily.Monospace, background = NovaColors.secondaryBackground)
        }
        pushStyle(style)
        append(match.groupValues.drop(1).first { it.isNotEmpty() })
        pop()
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
