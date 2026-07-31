package com.moments.android.views.nova.novasections

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.nova.novacore.NovaBrandIcon
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaColors
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.nova.novacore.NovaGroundingSource
import kotlinx.coroutines.delay

/**
 * Port de `Views/Nova/NovaSections/NovaChatSection.swift`.
 * Bubbles, typewriter, grounding footer y texto formateado.
 */

@Composable
fun EnhancedChatBubble(
    message: NovaChatMessage,
    @Suppress("UNUSED_PARAMETER") username: String,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    var displayedText by remember(message.id) { mutableStateOf("") }
    var isTyping by remember(message.id) { mutableStateOf(false) }
    var isInitialized by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id, message.text, message.isHistorical, message.isUser, message.isSystem) {
        if (message.isSystem) return@LaunchedEffect

        // Streaming: texto crece vía ViewModel → mostrar directo (≡ onChange iOS).
        if (!message.isHistorical && !message.isUser && isInitialized) {
            displayedText = message.text
            isTyping = false
            return@LaunchedEffect
        }

        if (isInitialized) return@LaunchedEffect

        when {
            message.isUser || message.isHistorical -> {
                displayedText = message.text
                isTyping = false
                isInitialized = true
            }
            else -> {
                displayedText = ""
                isTyping = true
                val full = message.text
                if (shouldShowInstantly(full)) {
                    delay(300)
                    displayedText = full
                    isTyping = false
                    isInitialized = true
                } else {
                    delay(200)
                    for (chunk in naturalChunks(full)) {
                        displayedText += chunk
                        delay(
                            if (chunk.contains('.') || chunk.contains(',') || chunk.contains('\n')) 150L
                            else 80L,
                        )
                    }
                    isTyping = false
                    isInitialized = true
                }
            }
        }
    }

    // Si el texto llega vacío al inicio y luego se rellena (stream), el efecto de arriba
    // con isInitialized=false arranca animación; si ya había init por stream parcial:
    LaunchedEffect(message.text) {
        if (!message.isHistorical && !message.isUser && !message.isSystem && message.text.isNotEmpty()) {
            if (isInitialized || displayedText.isNotEmpty()) {
                displayedText = message.text
                isTyping = false
                isInitialized = true
            }
        }
    }

    when {
        message.isSystem -> NovaSystemBubble(message.text)
        message.isUser -> NovaUserBubble(message, onEdit)
        else -> NovaAssistantBubble(message, displayedText, isTyping, onRegenerate)
    }
}

private fun shouldShowInstantly(text: String): Boolean =
    text.length < 100 || text.contains("##") || text.contains("•") || text.contains("**")

@Composable
private fun NovaSystemBubble(text: String) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(SettingsProfileColors.surfaceContainer(isDark))
                .border(1.dp, NovaColors.primary.copy(alpha = 0.18f), shape)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = NovaColors.primary,
                modifier = Modifier.size(16.dp).padding(top = 1.dp),
            )
            Text(
                text = text,
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NovaUserBubble(message: NovaChatMessage, onEdit: (() -> Unit)?) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    fun copyText() {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("", message.text))
        HapticManager.shared.lightImpact()
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.widthIn(min = 50.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            message.image?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            if (message.text.isNotEmpty()) {
                Box {
                    Text(
                        text = message.text,
                        color = NovaColors.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(shape)
                            .background(NovaColors.secondaryBackground)
                            .border(1.dp, NovaColors.borderColor, shape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { menuOpen = true },
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        onEdit?.let { edit ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nova_message_edit)) },
                                onClick = {
                                    menuOpen = false
                                    edit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_action_copy)) },
                            onClick = {
                                menuOpen = false
                                copyText()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.nova_you),
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun NovaAssistantBubble(
    message: NovaChatMessage,
    displayedText: String,
    typing: Boolean,
    onRegenerate: (() -> Unit)?,
) {
    val context = LocalContext.current
    val bubbleShape = RoundedCornerShape(20.dp)

    fun copyText() {
        context.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("", message.text))
        HapticManager.shared.lightImpact()
    }

    fun shareText() {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message.text),
                null,
            ),
        )
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 330.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(NovaColors.materialBackground)
                        .border(1.dp, NovaColors.borderColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    NovaBrandIcon(size = 14.dp)
                }
                Text(
                    text = stringResource(R.string.nova_name),
                    color = NovaColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (typing && !message.isHistorical) {
                    TypingDots()
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        onRegenerate?.let { regen ->
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.nova_regenerate),
                                tint = NovaColors.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        HapticManager.shared.lightImpact()
                                        regen()
                                    },
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.chat_action_copy),
                            tint = NovaColors.textSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { copyText() },
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { shareText() },
                            contentAlignment = Alignment.Center,
                        ) {
                            AttachmentIconView(
                                icon = AttachmentIcon.SHARE,
                                preset = AttachmentIconPreset.NOVA_SHARE_INLINE,
                                tintColor = NovaColors.textSecondary,
                            )
                        }
                    }
                }
            }

            EnhancedFormattedText(
                text = displayedText,
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(NovaColors.cardBackground)
                    .border(1.dp, NovaColors.borderColor, bubbleShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )

            if (message.groundingSources.isNotEmpty() || !message.searchSuggestionsHtml.isNullOrEmpty()) {
                NovaGroundingFooter(
                    sources = message.groundingSources,
                    searchSuggestionsHtml = message.searchSuggestionsHtml,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Spacer(Modifier.widthIn(min = 50.dp))
    }
}

@Composable
private fun TypingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val scale = remember { Animatable(0.5f) }
            LaunchedEffect(Unit) {
                if (MotionPolicy.reduceMotion) {
                    scale.snapTo(1f)
                    return@LaunchedEffect
                }
                delay(index * 200L)
                while (true) {
                    scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                    scale.animateTo(0.5f, tween(600, easing = FastOutSlowInEasing))
                }
            }
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .background(NovaColors.accent.copy(alpha = 0.6f)),
            )
        }
    }
}

// MARK: - Grounding

@Composable
fun NovaGroundingFooter(
    sources: List<NovaGroundingSource>,
    searchSuggestionsHtml: String?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val sourceHint = stringResource(R.string.nova_search_source_hint)
    val suggestionsLabel = stringResource(R.string.nova_search_suggestions)
    var suggestionsHeight by remember { mutableFloatStateOf(36f) }

    Column(
        modifier = modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nova_search_sources),
                color = NovaColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(NovaColors.secondaryBackground)
                            .semantics { contentDescription = "${source.title}. $sourceHint" }
                            .clickable { uriHandler.openUri(source.url) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = NovaColors.textPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = source.title,
                            color = NovaColors.textPrimary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        searchSuggestionsHtml?.takeIf { it.isNotBlank() }?.let { html ->
            GoogleSearchSuggestionsView(
                html = html,
                onContentHeightChange = { suggestionsHeight = it.coerceIn(30f, 52f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(suggestionsHeight.dp.coerceIn(30.dp, 52.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = suggestionsLabel },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSearchSuggestionsView(
    html: String,
    onContentHeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loadedHtml by remember { mutableStateOf<String?>(null) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (request.isForMainFrame && request.hasGesture()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            return true
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url ?: return false
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)",
                        ) { result ->
                            val height = result?.trim('"')?.toDoubleOrNull() ?: return@evaluateJavascript
                            onContentHeightChange(height.toFloat())
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (loadedHtml != html) {
                loadedHtml = html
                val document = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                      <style>
                        html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
                      </style>
                    </head>
                    <body>$html</body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        modifier = modifier,
    )
}

// MARK: - Parsing models

enum class NovaTextSectionType { HEADER, BULLET, NUMBERED, LINK, CODE, QUOTE, REGULAR }

data class NovaTextSection(
    val type: NovaTextSectionType,
    val content: String,
    val url: String? = null,
    val number: Int? = null,
)

fun parseNovaText(text: String): List<NovaTextSection> {
    val sections = mutableListOf<NovaTextSection>()
    val code = mutableListOf<String>()
    var language = ""
    var inCode = false

    fun flushCode() {
        sections += NovaTextSection(
            NovaTextSectionType.CODE,
            code.joinToString("\n"),
            language.ifBlank { null },
        )
        code.clear()
        language = ""
        inCode = false
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCode) flushCode()
            else {
                inCode = true
                language = trimmed.drop(3).trim().lowercase()
            }
            return@forEach
        }
        if (inCode) {
            code += line
            return@forEach
        }
        when {
            trimmed.isBlank() -> Unit
            trimmed.startsWith("#") -> {
                val content = trimmed
                    .replace("##", "")
                    .replace("#", "")
                    .trim()
                sections += NovaTextSection(NovaTextSectionType.HEADER, content)
            }
            trimmed.startsWith("•") || trimmed.startsWith("-") ||
                (trimmed.startsWith("*") && !trimmed.startsWith("**")) -> {
                val cleaned = trimmed
                    .replace("•", "")
                    .replaceFirst(Regex("^-\\s*"), "")
                    .replaceFirst(Regex("^\\*\\s*"), "")
                    .trim()
                sections += NovaTextSection(NovaTextSectionType.BULLET, cleaned)
            }
            Regex("^\\d+[.)]\\s").containsMatchIn(trimmed) -> {
                val match = Regex("^(\\d+)[.)]\\s+(.*)").find(trimmed)
                if (match != null) {
                    sections += NovaTextSection(
                        NovaTextSectionType.NUMBERED,
                        match.groupValues[2],
                        number = match.groupValues[1].toInt(),
                    )
                }
            }
            trimmed.contains("[") && trimmed.contains("](") -> sections += parseNovaLinks(trimmed)
            trimmed.startsWith(">") ->
                sections += NovaTextSection(NovaTextSectionType.QUOTE, trimmed.drop(1).trim())
            else -> sections += NovaTextSection(NovaTextSectionType.REGULAR, trimmed)
        }
    }
    if (inCode && code.isNotEmpty()) flushCode()
    return sections
}

private fun parseNovaLinks(line: String): List<NovaTextSection> {
    val result = mutableListOf<NovaTextSection>()
    val regex = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    var index = 0
    regex.findAll(line).forEach { match ->
        if (match.range.first > index) {
            result += NovaTextSection(NovaTextSectionType.REGULAR, line.substring(index, match.range.first))
        }
        result += NovaTextSection(NovaTextSectionType.LINK, match.groupValues[1], match.groupValues[2])
        index = match.range.last + 1
    }
    if (index < line.length) {
        result += NovaTextSection(NovaTextSectionType.REGULAR, line.substring(index))
    }
    return result
}

// MARK: - Formatted text views

@Composable
fun EnhancedFormattedText(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        parseNovaText(text).forEach { section ->
            when (section.type) {
                NovaTextSectionType.HEADER -> HeaderView(section.content)
                NovaTextSectionType.BULLET -> BulletPointView(section.content)
                NovaTextSectionType.NUMBERED -> NumberedListView(section.content, section.number ?: 1)
                NovaTextSectionType.LINK -> LinkView(section.content, section.url.orEmpty())
                NovaTextSectionType.CODE -> CodeBlockView(section.content, section.url)
                NovaTextSectionType.QUOTE -> QuoteView(section.content)
                NovaTextSectionType.REGULAR -> RegularTextView(section.content)
            }
        }
    }
}

@Composable
private fun HeaderView(text: String) {
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                brush = Brush.horizontalGradient(listOf(NovaColors.primary, NovaColors.accent)),
            ),
        )
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NovaColors.primary.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun BulletPointView(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(NovaColors.primary),
        )
        Text(text, color = NovaColors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NumberedListView(text: String, number: Int) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(NovaColors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(text, color = NovaColors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LinkView(text: String, url: String) {
    val handler = LocalUriHandler.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(NovaColors.primary.copy(alpha = 0.1f))
            .border(1.dp, NovaColors.primary.copy(alpha = 0.3f), shape)
            .clickable { handler.openUri(url.ifBlank { "https://google.com" }) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Link, null, tint = NovaColors.primary, modifier = Modifier.size(14.dp))
        Text(
            text = text,
            color = NovaColors.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        )
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = NovaColors.primary, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun CodeBlockView(text: String, language: String?) {
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    val clean = text.trim()
    val label = if (!language.isNullOrBlank()) language else stringResource(R.string.nova_code_language)
    val shape = RoundedCornerShape(12.dp)

    if (copied) {
        LaunchedEffect(Unit) {
            delay(2000)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(NovaColors.secondaryBackground)
            .border(1.dp, NovaColors.borderColor, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NovaColors.secondaryBackground.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                color = NovaColors.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("", clean))
                    copied = true
                    HapticManager.shared.success()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) Color.Green else NovaColors.primary,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = stringResource(if (copied) R.string.nova_code_copied else R.string.chat_action_copy),
                    color = if (copied) Color.Green else NovaColors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        HorizontalDivider(color = NovaColors.borderColor)
        Text(
            text = clean,
            color = NovaColors.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

@Composable
private fun QuoteView(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .background(NovaColors.accent),
        )
        Text(
            text = text,
            color = NovaColors.textSecondary,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RegularTextView(text: String) {
    SelectionContainer {
        Text(
            text = inlineNovaFormatting(text),
            color = NovaColors.textPrimary,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
    }
}
