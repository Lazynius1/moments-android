package com.moments.android.views.messaging.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.profile.userprofile.sections.ProfileUnavailableAvatar

/**
 * Port de `ChatSpeechBubbleViews.swift` — forma agrupada, spoilers/links/markdown inline,
 * highlight de búsqueda (diacríticos) y gutter de avatar entrante.
 */

enum class ChatBubbleSide { LEADING, TRAILING }

enum class ChatMessageGroupPosition { SINGLE, FIRST, MIDDLE, LAST }

object ChatTextBubbleMetrics {
    val horizontalPadding = 15.dp
    val verticalPadding = 10.dp
    val lineSpacing = 2.dp
    val cornerRadius = 20.dp
    val joinedRadius = 4.dp
    /** ≡ iOS `maxWidthScreenFraction`. */
    const val maxWidthScreenFraction = 0.78f
    const val maxWidthFraction = maxWidthScreenFraction
}

/** ≡ iOS `ChatMessageFont.bubble` (~15pt escalado con tamaño de texto del sistema). */
object ChatMessageFont {
    @Composable
    fun bubbleSizeSp(): Float {
        val scale = LocalConfiguration.current.fontScale.coerceIn(0.85f, 1.6f)
        return 15f * scale
    }

    @Composable
    fun bubbleLineHeightSp(): Float = bubbleSizeSp() + 4f
}

/** ≡ iOS `EnvironmentValues.chatSearchHighlightTerm`. */
val LocalChatSearchHighlightTerm = compositionLocalOf { "" }

/** ≡ iOS `EnvironmentValues.chatSearchActiveMessageId`. */
val LocalChatSearchActiveMessageId = compositionLocalOf<String?> { null }

data class TextSegment(val text: String, val isSpoiler: Boolean)

fun chatTextSegments(text: String): List<TextSegment> =
    text.split("||").mapIndexedNotNull { index, part ->
        part.takeIf { it.isNotEmpty() }?.let { TextSegment(it, isSpoiler = index % 2 != 0) }
    }

/**
 * Port de `ChatBubbleShape`.
 * Compose: `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)`.
 */
fun chatBubbleShape(
    side: ChatBubbleSide,
    position: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
    cornerRadius: Dp = ChatTextBubbleMetrics.cornerRadius,
    joinedRadius: Dp = ChatTextBubbleMetrics.joinedRadius,
): RoundedCornerShape {
    val r = cornerRadius
    val j = joinedRadius
    val topPinned = position != ChatMessageGroupPosition.FIRST && position != ChatMessageGroupPosition.SINGLE
    val bottomPinned = position != ChatMessageGroupPosition.LAST && position != ChatMessageGroupPosition.SINGLE
    return when (side) {
        ChatBubbleSide.LEADING -> RoundedCornerShape(
            topStart = if (topPinned) j else r,
            topEnd = r,
            bottomEnd = r,
            bottomStart = if (bottomPinned) j else r,
        )
        ChatBubbleSide.TRAILING -> RoundedCornerShape(
            topStart = r,
            topEnd = if (topPinned) j else r,
            bottomEnd = if (bottomPinned) j else r,
            bottomStart = r,
        )
    }
}

@Composable
fun ChatTextBubbleView(
    text: String,
    isOutgoing: Boolean,
    messageId: String? = null,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
    reactions: Map<String, List<String>>? = null,
    isStarred: Boolean = false,
    repliedMessage: EnhancedMessage? = null,
    otherParticipantName: String = "",
    onReplyTap: (() -> Unit)? = null,
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val outgoingFill = LocalChatOutgoingBubbleColor.current
    val searchTerm = LocalChatSearchHighlightTerm.current
    val activeSearchId = LocalChatSearchActiveMessageId.current
    var revealSpoilers by remember(messageId) { mutableStateOf(false) }

    val segments = remember(text) { chatTextSegments(text) }
    val hasSpoilers = segments.any { it.isSpoiler }
    val hasReactions = !reactions.isNullOrEmpty()
    val hasReply = repliedMessage != null
    val linkUrl = remember(text) { ChatLinkOpener.firstUrl(text) }
    val hasLink = linkUrl != null

    val maxBubbleWidth = LocalConfiguration.current.screenWidthDp.dp * ChatTextBubbleMetrics.maxWidthScreenFraction
    val shape = chatBubbleShape(
        side = if (isOutgoing) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING,
        position = groupPosition,
    )
    val bubbleFill = if (isOutgoing) outgoingFill else colors.messageBubbleBackground
    val textColor = if (isOutgoing) Color.White else colors.messageTextColor
    val linkColor = if (isOutgoing) Color.White.copy(alpha = 0.92f) else Color.Blue
    val isActiveSearchMatch = messageId != null && messageId == activeSearchId
    val highlightBg = Color(1f, 0.82f, 0.25f).copy(alpha = if (isActiveSearchMatch) 0.92f else 0.45f)
    val fontSizeSp = ChatMessageFont.bubbleSizeSp()
    val lineHeightSp = ChatMessageFont.bubbleLineHeightSp()

    val annotated = remember(text, revealSpoilers, textColor, linkColor, searchTerm, isActiveSearchMatch) {
        buildBubbleAnnotatedString(
            segments = segments,
            revealSpoilers = revealSpoilers,
            textColor = textColor,
            linkColor = linkColor,
            searchTerm = searchTerm,
            highlightBackground = highlightBg,
            activeSearchMatch = isActiveSearchMatch,
            suppressSearch = hasSpoilers && !revealSpoilers,
        )
    }

    val spoilerHint = stringResource(R.string.chat_a11y_spoiler_hint)
    val uriHandler = LocalUriHandler.current
    val reduceMotion = MotionPolicy.reduceMotion

    MessageReactionOverlayBox(
        isOutgoing = isOutgoing,
        reactions = reactions,
        isStarred = isStarred,
        compact = true,
        onTap = onReaction,
    ) {
        Column(
            modifier
                .widthIn(max = maxBubbleWidth)
                .clip(shape)
                .background(bubbleFill)
                .border(
                    width = 0.5.dp,
                    color = if (isOutgoing) Color.Transparent else colors.messageBubbleStroke,
                    shape = shape,
                )
                .then(
                    if (hasSpoilers) {
                        Modifier
                            .clickable {
                                // ≡ iOS reduceMotion vs easeInOut 0.22
                                revealSpoilers = !revealSpoilers
                            }
                            .semantics { contentDescription = spoilerHint }
                    } else {
                        Modifier
                    },
                )
                .padding(
                    MessageReactionMetrics.bubbleContentInsets(
                        isOutgoing = isOutgoing,
                        compact = true,
                        hasReactions = hasReactions,
                        hasStar = isStarred,
                    ),
                )
                .padding(
                    start = if (hasReply) 6.dp else ChatTextBubbleMetrics.horizontalPadding,
                    top = if (hasReply) 6.dp else ChatTextBubbleMetrics.verticalPadding,
                    end = if (hasReply) 6.dp else ChatTextBubbleMetrics.horizontalPadding,
                    bottom = if (hasReply) 8.dp else ChatTextBubbleMetrics.verticalPadding,
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repliedMessage?.let {
                EmbeddedReplyView(it, isOutgoing, otherParticipantName, onReplyTap)
            }
            linkUrl?.let { url ->
                LinkPreviewCard(url = url, outgoing = isOutgoing, embedded = true)
            }
            val messageText: @Composable () -> Unit = {
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start,
                        lineHeight = lineHeightSp.sp,
                    ),
                    modifier = Modifier
                        .then(if (hasReply || hasLink) Modifier.fillMaxWidth() else Modifier)
                        .padding(horizontal = if (hasReply) 4.dp else 0.dp),
                    onClick = { offset ->
                        annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
                            uriHandler.openUri(it.item)
                        }
                    },
                )
            }
            if (hasSpoilers && !reduceMotion) {
                AnimatedContent(
                    targetState = revealSpoilers,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                    label = "spoilerReveal",
                ) { _ ->
                    messageText()
                }
            } else {
                messageText()
            }
        }
    }
}

private fun buildBubbleAnnotatedString(
    segments: List<TextSegment>,
    revealSpoilers: Boolean,
    textColor: Color,
    linkColor: Color,
    searchTerm: String,
    highlightBackground: Color,
    activeSearchMatch: Boolean,
    suppressSearch: Boolean,
): AnnotatedString {
    val built = buildAnnotatedString {
        segments.forEach { segment ->
            val hiddenSpoiler = segment.isSpoiler && !revealSpoilers
            val fg = if (hiddenSpoiler) Color.Transparent else textColor
            val bg = when {
                hiddenSpoiler -> textColor.copy(alpha = 0.85f)
                segment.isSpoiler -> textColor.copy(alpha = 0.12f)
                else -> Color.Unspecified
            }
            // ≡ AttributedString(markdown: inlineOnlyPreservingWhitespace) + applyDetectedLinks
            appendInlineMarkdownAndLinks(
                raw = segment.text,
                color = fg,
                background = bg,
                linkColor = linkColor,
                detectLinks = !hiddenSpoiler,
            )
        }
    }
    val term = searchTerm.trim()
    if (term.isEmpty() || suppressSearch) return built
    return buildAnnotatedString {
        append(built)
        val plain = built.text
        for ((start, end) in findSearchHighlightRanges(plain, term)) {
            addStyle(
                SpanStyle(
                    background = highlightBackground,
                    color = if (activeSearchMatch) Color.Black else Color.Unspecified,
                ),
                start,
                end,
            )
        }
    }
}

/**
 * Markdown inline lite ≡ iOS `AttributedString(markdown:)` inlineOnly:
 * `**bold**`, `*italic*`, `` `code` ``, `~~strike~~`, luego URLs con underline.
 */
private fun AnnotatedString.Builder.appendInlineMarkdownAndLinks(
    raw: String,
    color: Color,
    background: Color,
    linkColor: Color,
    detectLinks: Boolean,
) {
    val mdRegex = Regex(
        """\*\*([^*]+)\*\*|(?<!\*)\*([^*]+)\*(?!\*)|`([^`]+)`|~~([^~]+)~~""",
    )
    val plain = StringBuilder()
    val rangeStart = length
    var cursor = 0
    fun appendStyled(text: String, style: SpanStyle) {
        if (text.isEmpty()) return
        pushStyle(style)
        append(text)
        pop()
        plain.append(text)
    }
    mdRegex.findAll(raw).forEach { match ->
        val before = raw.substring(cursor, match.range.first)
        appendStyled(before, SpanStyle(color = color, background = background))
        val style = when {
            match.groupValues[1].isNotEmpty() ->
                SpanStyle(color = color, background = background, fontWeight = FontWeight.Bold)
            match.groupValues[2].isNotEmpty() ->
                SpanStyle(color = color, background = background, fontStyle = FontStyle.Italic)
            match.groupValues[3].isNotEmpty() ->
                SpanStyle(
                    color = color,
                    background = if (background == Color.Unspecified) {
                        color.copy(alpha = 0.12f)
                    } else {
                        background
                    },
                    fontFamily = FontFamily.Monospace,
                )
            else -> SpanStyle(
                color = color,
                background = background,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        val content = match.groupValues.drop(1).first { it.isNotEmpty() }
        appendStyled(content, style)
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        appendStyled(raw.substring(cursor), SpanStyle(color = color, background = background))
    }
    if (!detectLinks) return
    val linkRegex = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<]+)")
    linkRegex.findAll(plain).forEach { match ->
        val url = if (match.value.startsWith("www.")) "https://${match.value}" else match.value
        val start = rangeStart + match.range.first
        val end = rangeStart + match.range.last + 1
        addStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            start,
            end,
        )
        addStringAnnotation("url", url, start, end)
    }
}

/** ≡ iOS `.caseInsensitive` + `.diacriticInsensitive` sobre el texto de la burbuja. */
private fun findSearchHighlightRanges(text: String, term: String): List<Pair<Int, Int>> {
    val needle = foldForSearch(term)
    if (needle.isEmpty()) return emptyList()
    val (folded, indexMap) = foldForSearchWithMap(text)
    val ranges = mutableListOf<Pair<Int, Int>>()
    var from = 0
    while (from <= folded.length - needle.length) {
        val idx = folded.indexOf(needle, from)
        if (idx < 0) break
        val endFolded = idx + needle.length
        val startOrig = indexMap[idx]
        val endOrig = if (endFolded < indexMap.size) {
            indexMap[endFolded]
        } else {
            text.length
        }
        ranges += startOrig to endOrig.coerceAtLeast(startOrig + 1)
        from = endFolded.coerceAtLeast(from + 1)
    }
    return ranges
}

private fun foldForSearch(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

private fun foldForSearchWithMap(value: String): Pair<String, IntArray> {
    val folded = StringBuilder()
    val map = ArrayList<Int>(value.length)
    var i = 0
    while (i < value.length) {
        val cp = value.codePointAt(i)
        val ch = String(Character.toChars(cp))
        val base = foldForSearch(ch)
        for (ignored in base) {
            folded.append(ignored)
            map.add(i)
        }
        i += Character.charCount(cp)
    }
    return folded.toString() to map.toIntArray()
}

object ChatIncomingMessageLayout {
    val gutterAvatarSize = 26.dp
    val gutterGap = 6.dp
    val gutterInset: Dp get() = gutterAvatarSize + gutterGap
}

/** Port de `ChatIncomingAvatarButton`. */
@Composable
fun ChatIncomingAvatarButton(
    otherUserId: String?,
    isUnavailable: Boolean,
    size: Dp,
    expandTapTarget: Boolean = true,
    onTap: () -> Unit,
) {
    val hit = if (expandTapTarget) maxOf(size, 44.dp) else size
    Box(
        Modifier
            .size(hit)
            .clip(CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (isUnavailable) {
            ProfileUnavailableAvatar(size = size)
        } else {
            // Mismo patrón que el avatar circular del toolbar del chat.
            GlassmorphicAvatar(
                userId = otherUserId.orEmpty(),
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * Port de `ChatIncomingAvatarGutter`: avatar solo en el último de la ráfaga;
 * hueco del mismo ancho en el resto.
 */
@Composable
fun ChatIncomingAvatarGutter(
    showAvatar: Boolean,
    otherUserId: String?,
    isUnavailable: Boolean,
    onTap: () -> Unit,
) {
    // ≡ iOS: frame(width: avatarSize).padding(.trailing, gutterGap)
    // padding fuera del width para no comprimir el avatar a un no-cuadrado.
    Box(
        Modifier
            .padding(end = ChatIncomingMessageLayout.gutterGap)
            .width(ChatIncomingMessageLayout.gutterAvatarSize),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (showAvatar) {
            ChatIncomingAvatarButton(
                otherUserId = otherUserId,
                isUnavailable = isUnavailable,
                size = ChatIncomingMessageLayout.gutterAvatarSize,
                expandTapTarget = false,
                onTap = onTap,
            )
        } else {
            // ≡ iOS Color.clear.frame(width: avatarSize, height: 1)
            Box(Modifier.width(ChatIncomingMessageLayout.gutterAvatarSize).height(1.dp))
        }
    }
}
