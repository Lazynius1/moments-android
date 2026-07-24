package com.moments.android.views.nova.novasections

import android.content.ClipData
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.moments.android.R
import com.moments.android.views.nova.novacore.NovaBrandIcon
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaColors
import com.moments.android.views.nova.novacore.NovaGroundingSource
import kotlinx.coroutines.delay

@Composable
fun EnhancedChatBubble(message: NovaChatMessage, username: String, onRegenerate: (() -> Unit)? = null, onEdit: (() -> Unit)? = null) {
    var displayedText by remember(message.id) { mutableStateOf(if (message.isHistorical || message.isUser) message.text else "") }
    var isTyping by remember(message.id) { mutableStateOf(!message.isHistorical && !message.isUser) }
    LaunchedEffect(message.id, message.text, message.isHistorical, message.isUser) {
        if (message.isSystem || message.isHistorical || message.isUser) { displayedText = message.text; isTyping = false; return@LaunchedEffect }
        if (displayedText.isNotEmpty() || message.text.isEmpty()) { displayedText = message.text; isTyping = message.text.isEmpty(); return@LaunchedEffect }
        if (message.text.length < 100 || listOf("##", "•", "**").any(message.text::contains)) { delay(300); displayedText = message.text; isTyping = false }
        else { isTyping = true; naturalChunks(message.text).forEach { chunk -> displayedText += chunk; delay(if (chunk.contains('.') || chunk.contains(',') || chunk.contains('\n')) 230 else 80) }; isTyping = false }
    }
    when {
        message.isSystem -> NovaSystemBubble(message.text)
        message.isUser -> NovaUserBubble(message, onEdit)
        else -> NovaAssistantBubble(message, username, displayedText, isTyping, onRegenerate)
    }
}

@Composable private fun NovaSystemBubble(text: String) = Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) { Row(Modifier.clip(CircleShape).background(NovaColors.materialBackground).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = NovaColors.primary.copy(alpha = .7f), modifier = Modifier.size(10.dp)); Spacer(Modifier.width(6.dp)); Text(text, color = NovaColors.textSecondary, fontSize = 12.sp) } }
@Composable private fun NovaUserBubble(message: NovaChatMessage, onEdit: (() -> Unit)?) { val context = LocalContext.current; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Column(Modifier.widthIn(max = 250.dp), horizontalAlignment = Alignment.End) { message.image?.let { Image(it.asImageBitmap(), null, modifier = Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(16.dp))) }; if (message.text.isNotEmpty()) Text(message.text, color = NovaColors.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(NovaColors.secondaryBackground).clickable { onEdit?.invoke() }.padding(horizontal = 20.dp, vertical = 14.dp)); Text(stringResource(R.string.nova_you), color = NovaColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, end = 8.dp).clickable { context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", message.text)) }) } } }
@Composable private fun NovaAssistantBubble(message: NovaChatMessage, username: String, displayedText: String, typing: Boolean, onRegenerate: (() -> Unit)?) { val context = LocalContext.current; Row(Modifier.fillMaxWidth()) { Column(Modifier.widthIn(max = 330.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(24.dp).clip(CircleShape).background(NovaColors.materialBackground), Alignment.Center) { NovaBrandIcon(14.dp) }; Spacer(Modifier.width(8.dp)); Text(if (username.isBlank()) stringResource(R.string.nova_name) else username, color = NovaColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); if (typing && !message.isHistorical) TypingDots() else Row { onRegenerate?.let { Icon(Icons.Default.Refresh, stringResource(R.string.nova_regenerate), tint = NovaColors.textSecondary, modifier = Modifier.size(20.dp).clickable(onClick = it)) }; Icon(Icons.Default.ContentCopy, stringResource(R.string.chat_action_copy), tint = NovaColors.textSecondary, modifier = Modifier.size(20.dp).clickable { context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", message.text)) }); Icon(Icons.Default.Share, stringResource(R.string.nova_share), tint = NovaColors.textSecondary, modifier = Modifier.size(20.dp).clickable { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message.text), null)) }) } }; EnhancedFormattedText(displayedText, Modifier.padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(NovaColors.cardBackground).padding(horizontal = 20.dp, vertical = 16.dp)); if (message.groundingSources.isNotEmpty() || !message.searchSuggestionsHtml.isNullOrEmpty()) NovaGroundingFooter(message.groundingSources, message.searchSuggestionsHtml) } } }
@Composable private fun TypingDots() = Row { repeat(3) { Box(Modifier.padding(horizontal = 2.dp).size(4.dp).clip(CircleShape).background(NovaColors.accent.copy(alpha = .6f))) } }

@Composable fun NovaGroundingFooter(sources: List<NovaGroundingSource>, searchSuggestionsHtml: String?) { val uriHandler = LocalUriHandler.current; Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (sources.isNotEmpty()) { Text(stringResource(R.string.nova_search_sources), color = NovaColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { sources.forEach { source -> Text(source.title, color = NovaColors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clip(CircleShape).background(NovaColors.secondaryBackground).clickable { uriHandler.openUri(source.url) }.padding(horizontal = 10.dp, vertical = 7.dp)) } } }; searchSuggestionsHtml?.takeIf(String::isNotBlank)?.let { GoogleSearchSuggestionsView(it) } } }
@Composable fun GoogleSearchSuggestionsView(html: String) { val context = LocalContext.current; AndroidView(factory = { WebView(it).apply { setBackgroundColor(android.graphics.Color.TRANSPARENT); settings.javaScriptEnabled = false; webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it))); true } ?: false } } }, update = { it.loadDataWithBaseURL(null, "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}</style></head><body>$html</body></html>", "text/html", "utf-8", null) }, modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))) }

enum class NovaTextSectionType { HEADER, BULLET, NUMBERED, LINK, CODE, QUOTE, REGULAR }
data class NovaTextSection(val type: NovaTextSectionType, val content: String, val url: String? = null, val number: Int? = null)
fun parseNovaText(text: String): List<NovaTextSection> { val sections = mutableListOf<NovaTextSection>(); val code = mutableListOf<String>(); var language = ""; var inCode = false; text.lines().forEach { line -> val trimmed = line.trim(); if (trimmed.startsWith("```")) { if (inCode) { sections += NovaTextSection(NovaTextSectionType.CODE, code.joinToString("\n"), language.ifBlank { null }); code.clear(); language = ""; inCode = false } else { inCode = true; language = trimmed.drop(3).trim().lowercase() }; return@forEach }; if (inCode) { code += line; return@forEach }; when { trimmed.isBlank() -> Unit; trimmed.startsWith("#") -> sections += NovaTextSection(NovaTextSectionType.HEADER, trimmed.trimStart('#').trim()); trimmed.startsWith("•") || trimmed.startsWith("-") || (trimmed.startsWith("*") && !trimmed.startsWith("**")) -> sections += NovaTextSection(NovaTextSectionType.BULLET, trimmed.drop(1).trim()); Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> { val match = Regex("^(\\d+)[.)]\\s+(.*)").find(trimmed)!!; sections += NovaTextSection(NovaTextSectionType.NUMBERED, match.groupValues[2], number = match.groupValues[1].toInt()) }; trimmed.startsWith(">") -> sections += NovaTextSection(NovaTextSectionType.QUOTE, trimmed.drop(1).trim()); trimmed.contains("](") -> sections += parseNovaLinks(trimmed); else -> sections += NovaTextSection(NovaTextSectionType.REGULAR, trimmed) } }; if (inCode && code.isNotEmpty()) sections += NovaTextSection(NovaTextSectionType.CODE, code.joinToString("\n"), language.ifBlank { null }); return sections }
private fun parseNovaLinks(line: String): List<NovaTextSection> { val result = mutableListOf<NovaTextSection>(); val regex = Regex("\\[([^]]+)]\\(([^)]+)\\)"); var index = 0; regex.findAll(line).forEach { match -> if (match.range.first > index) result += NovaTextSection(NovaTextSectionType.REGULAR, line.substring(index, match.range.first)); result += NovaTextSection(NovaTextSectionType.LINK, match.groupValues[1], match.groupValues[2]); index = match.range.last + 1 }; if (index < line.length) result += NovaTextSection(NovaTextSectionType.REGULAR, line.substring(index)); return result }

@Composable fun EnhancedFormattedText(text: String, modifier: Modifier = Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) { parseNovaText(text).forEach { section -> when (section.type) { NovaTextSectionType.HEADER -> Column { Text(section.content, color = NovaColors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Box(Modifier.padding(top = 4.dp).width(40.dp).height(3.dp).background(NovaColors.primary.copy(alpha = .3f))) }; NovaTextSectionType.BULLET -> Row { Box(Modifier.padding(top = 8.dp, end = 12.dp).size(6.dp).clip(CircleShape).background(NovaColors.primary)); Text(section.content, color = NovaColors.textPrimary, fontSize = 16.sp) }; NovaTextSectionType.NUMBERED -> Row { Text((section.number ?: 1).toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.size(20.dp).clip(CircleShape).background(NovaColors.primary).padding(horizontal = 6.dp, vertical = 2.dp)); Spacer(Modifier.width(12.dp)); Text(section.content, color = NovaColors.textPrimary, fontSize = 16.sp) }; NovaTextSectionType.LINK -> NovaLinkSection(section); NovaTextSectionType.CODE -> NovaCodeBlock(section); NovaTextSectionType.QUOTE -> Row { Box(Modifier.width(4.dp).height(42.dp).background(NovaColors.accent)); Spacer(Modifier.width(12.dp)); Text(section.content, color = NovaColors.textSecondary, fontStyle = FontStyle.Italic, fontSize = 16.sp, fontWeight = FontWeight.Medium) }; NovaTextSectionType.REGULAR -> SelectionContainer { Text(inlineNovaFormatting(section.content), color = NovaColors.textPrimary, fontSize = 16.sp, lineHeight = 20.sp) } } } }
@Composable private fun NovaLinkSection(section: NovaTextSection) { val handler = LocalUriHandler.current; Row(Modifier.clip(RoundedCornerShape(8.dp)).background(NovaColors.primary.copy(alpha = .1f)).clickable { section.url?.let(handler::openUri) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = NovaColors.primary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)); Text(section.content, color = NovaColors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun NovaCodeBlock(section: NovaTextSection) { val context = LocalContext.current; var copied by remember(section.content) { mutableStateOf(false) }; Column(Modifier.clip(RoundedCornerShape(12.dp)).background(NovaColors.secondaryBackground)) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text((section.url ?: stringResource(R.string.nova_code_language)).uppercase(), color = NovaColors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text(stringResource(if (copied) R.string.nova_code_copied else R.string.chat_action_copy), color = if (copied) Color.Green else NovaColors.primary, fontSize = 12.sp, modifier = Modifier.clickable { context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", section.content)); copied = true }) }; Text(section.content.trim(), color = NovaColors.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)); if (copied) LaunchedEffect(Unit) { delay(2000); copied = false } } }
