package com.moments.android.views.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentMentionNavigation
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Port de `MomentCaptionPresentationStyle` (MomentCaptionView.swift). */
enum class MomentCaptionPresentationStyle {
    Feed,
    Reels,
    Detail,
}

/** Port de `MomentCaptionText` — normalización para cards feed/reels. */
object MomentCaptionText {
    /** Colapsa saltos a espacios para que hashtags fluyan. */
    fun flowing(content: String): String =
        content
            .replace(Regex("""\s*\n+\s*"""), " ")
            .replace(Regex(""" {2,}"""), " ")
            .trim()
}

private data class CaptionMediaPreviewContext(
    val authorId: String,
    val username: String,
    val mediaUrl: String?,
    val isVideo: Boolean,
    val audience: String?,
)

private fun resolveCaptionMediaPreviewContext(
    moment: Moment?,
    authorId: String?,
    username: String?,
    previewImageUrl: String?,
    thumbnailUrl: String?,
    previewVideoUrl: String?,
    audience: String?,
    isVideo: Boolean?,
): CaptionMediaPreviewContext? {
    moment?.let {
        val mediaUrl = it.previewImageURLString?.trim()?.takeIf { url -> url.isNotEmpty() }
            ?: it.thumbnailUrl?.trim()?.takeIf { url -> url.isNotEmpty() }
        val video = it.primaryVisibleMediaItem?.type == MediaItem.MediaType.VIDEO ||
            !it.previewVideoURLString.isNullOrBlank()
        return CaptionMediaPreviewContext(
            authorId = it.authorId,
            username = it.username,
            mediaUrl = mediaUrl,
            isVideo = video,
            audience = it.audience,
        )
    }

    val resolvedAuthorId = authorId?.trim().orEmpty()
    val resolvedUsername = username?.trim().orEmpty()
    if (resolvedAuthorId.isEmpty() && resolvedUsername.isEmpty() &&
        previewImageUrl.isNullOrBlank() && thumbnailUrl.isNullOrBlank()
    ) {
        return null
    }

    val mediaUrl = previewImageUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }
    val video = isVideo == true || !previewVideoUrl.isNullOrBlank()
    return CaptionMediaPreviewContext(
        authorId = resolvedAuthorId,
        username = resolvedUsername,
        mediaUrl = mediaUrl,
        isVideo = video,
        audience = audience,
    )
}

private fun captionBaseTextColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.84f)

private fun captionSecondaryTextColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.58f)

private fun captionHashtagTextColor(isDark: Boolean): Color =
    if (isDark) Color.White else Color(0xFF007AFF)

private val captionMentionTextColor = Color(0xFF007AFF)

private fun sheetBaseTextColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(alpha = 0.94f) else Color.Black.copy(alpha = 0.86f)

private fun reelsNeedsMore(text: String): Boolean {
    val lines = text.split('\n').filter { it.isNotEmpty() }
    if (lines.size > 2) return true
    if (lines.size == 2) return true
    return text.length > 72
}

private fun truncateString(str: String, limit: Int): String {
    if (str.length <= limit) return str
    val prefix = str.take(limit)
    val lastSpace = prefix.lastIndexOf(' ')
    return if (lastSpace >= 0) {
        prefix.take(lastSpace).trim()
    } else {
        prefix.trim()
    }
}

private fun truncatedCollapsedText(content: String, needsMore: Boolean): String {
    if (!needsMore) return content

    val lines = content.split('\n').filter { it.isNotEmpty() }
    if (lines.size >= 2) {
        val line1 = lines[0]
        val line2 = lines[1]

        if (line1.length > 60) {
            return truncateString(line1, 60)
        }

        val combined = "$line1\n$line2"
        if (combined.length > 75) {
            return line1 + "\n" + truncateString(line2, maxOf(15, 75 - line1.length))
        }
        return combined
    }
    return truncateString(content, 75)
}

/**
 * Port de `MomentCaptionView.swift` — feed/detail con morph contextual y reels inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentCaptionView(
    content: String,
    onHashtagTap: (String) -> Unit,
    style: MomentCaptionPresentationStyle = MomentCaptionPresentationStyle.Feed,
    modifier: Modifier = Modifier,
    moment: Moment? = null,
    authorId: String? = null,
    username: String? = null,
    previewImageUrl: String? = null,
    thumbnailUrl: String? = null,
    previewVideoUrl: String? = null,
    audience: String? = null,
    isVideo: Boolean? = null,
    isReelsCaptionExpanded: Boolean? = null,
    onReelsCaptionExpandedChange: ((Boolean) -> Unit)? = null,
    onMentionTap: (String) -> Unit = MomentMentionNavigation::openProfile,
) {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val isMediaOverlay = style == MomentCaptionPresentationStyle.Reels
    val baseColor = if (isMediaOverlay) Color.White.copy(alpha = 0.92f) else captionBaseTextColor(isDark)
    val secondaryColor = if (isMediaOverlay) Color.White.copy(alpha = 0.72f) else captionSecondaryTextColor(isDark)
    val hashtagColor = if (isMediaOverlay) Color.White else captionHashtagTextColor(isDark)
    val mentionColor = captionMentionTextColor

    // Feed/Reels: flujo continuo. Detail: respeta saltos del autor.
    val cardContent = when (style) {
        MomentCaptionPresentationStyle.Feed,
        MomentCaptionPresentationStyle.Reels,
        -> MomentCaptionText.flowing(trimmed)
        MomentCaptionPresentationStyle.Detail -> trimmed
    }

    var needsExpansion by remember(cardContent, style) { mutableStateOf(false) }
    val previewContent = cardContent

    val mediaPreviewContext = remember(moment, authorId, username, previewImageUrl, thumbnailUrl, previewVideoUrl, audience, isVideo) {
        resolveCaptionMediaPreviewContext(
            moment = moment,
            authorId = authorId,
            username = username,
            previewImageUrl = previewImageUrl,
            thumbnailUrl = thumbnailUrl,
            previewVideoUrl = previewVideoUrl,
            audience = audience,
            isVideo = isVideo,
        )
    }

    var showFullCaption by remember { mutableStateOf(false) }
    var captionBounds by remember { mutableStateOf(Rect.Zero) }

    if (style == MomentCaptionPresentationStyle.Reels) {
        ReelsCaptionBody(
            content = cardContent,
            needsMore = reelsNeedsMore(cardContent),
            baseTextColor = baseColor,
            hashtagTextColor = hashtagColor,
            mentionTextColor = mentionColor,
            onHashtagTap = onHashtagTap,
            onMentionTap = onMentionTap,
            isExpandedExternal = isReelsCaptionExpanded,
            onExpandedChange = onReelsCaptionExpandedChange,
            modifier = modifier,
        )
        return
    }

    val bodyFontSize = if (style == MomentCaptionPresentationStyle.Detail) 15.sp else 14.sp
    val lineLimit = if (style == MomentCaptionPresentationStyle.Detail) 4 else 3

    Column(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { captionBounds = it.boundsInWindow() }
            .padding(horizontal = FeedMomentCardLayout.captionHorizontalPadding)
            .padding(top = if (style == MomentCaptionPresentationStyle.Detail) 0.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MomentHashtagText(
            content = previewContent,
            onHashtagTap = onHashtagTap,
            onMentionTap = onMentionTap,
            baseColor = baseColor,
            hashtagColor = hashtagColor,
            mentionColor = mentionColor,
            fontSize = bodyFontSize,
            lineLimit = lineLimit,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { result ->
                val overflows = result.hasVisualOverflow
                if (needsExpansion != overflows) needsExpansion = overflows
            },
        )

        if (needsExpansion) {
            val context = LocalContext.current
            val density = LocalDensity.current
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        HapticManager.shared.lightImpact()
                        showFullCaption = true
                    }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.feed_see_more),
                    color = secondaryColor,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }

    if (showFullCaption) {
        Dialog(
            onDismissRequest = { /* El cierre inverso lo controla el morph. */ },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            MomentCaptionContextDestination(
                sourceBounds = captionBounds,
                onDismiss = { showFullCaption = false },
                source = {
                    CaptionMorphSource(
                        content = previewContent,
                        needsExpansion = needsExpansion,
                        baseColor = baseColor,
                        secondaryColor = secondaryColor,
                        hashtagColor = hashtagColor,
                        mentionColor = mentionColor,
                        bodyFontSize = bodyFontSize,
                        lineLimit = lineLimit,
                    )
                },
                destination = { close, reportContentHeight ->
                    MomentCaptionReaderCard(
                        content = trimmed,
                        mediaPreviewContext = mediaPreviewContext,
                        onHashtagTap = onHashtagTap,
                        onMentionTap = onMentionTap,
                        onClose = close,
                        onContentHeightChange = reportContentHeight,
                    )
                },
            )
        }
    }
}

@Composable
private fun CaptionMorphSource(
    content: String,
    needsExpansion: Boolean,
    baseColor: Color,
    secondaryColor: Color,
    hashtagColor: Color,
    mentionColor: Color,
    bodyFontSize: androidx.compose.ui.unit.TextUnit,
    lineLimit: Int,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = FeedMomentCardLayout.captionHorizontalPadding)
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MomentHashtagText(
            content = content,
            onHashtagTap = {},
            onMentionTap = {},
            baseColor = baseColor,
            hashtagColor = hashtagColor,
            mentionColor = mentionColor,
            fontSize = bodyFontSize,
            lineLimit = lineLimit,
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsExpansion) {
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .momentsChromeGlass(CircleShape, interactive = false)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(R.string.feed_see_more),
                    color = secondaryColor,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    contentDescription = null,
                    tint = secondaryColor,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/** M3 container morph equivalente al lector contextual de iOS, sin giro 3D. */
@Composable
private fun MomentCaptionContextDestination(
    sourceBounds: Rect,
    onDismiss: () -> Unit,
    source: @Composable () -> Unit,
    destination: @Composable (close: () -> Unit, onContentHeightChange: (Int) -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    var destinationContentHeightPx by remember { mutableIntStateOf(0) }
    val isDark = isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    fun close() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            progress.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    BackHandler(onBack = ::close)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val horizontalInsetPx = with(density) { 12.dp.toPx() }
        val verticalInsetPx = with(density) { 16.dp.toPx() }
        val maxCardHeightPx = minOf(with(density) { 560.dp.toPx() }, heightPx * 0.50f)
        val minCardHeightPx = with(density) { 220.dp.toPx() }
        val fallbackCardHeightPx = with(density) { 260.dp.toPx() }
        val measuredCardHeightPx = destinationContentHeightPx
            .takeIf { it > 0 }
            ?.toFloat()
            ?: fallbackCardHeightPx
        val effectiveMinHeightPx = minOf(minCardHeightPx, maxCardHeightPx)
        val cardHeightPx = measuredCardHeightPx.coerceIn(effectiveMinHeightPx, maxCardHeightPx)
        val destinationBounds = Rect(
            left = horizontalInsetPx,
            top = (heightPx - cardHeightPx) / 2f,
            right = widthPx - horizontalInsetPx,
            bottom = (heightPx + cardHeightPx) / 2f,
        )
        val safeSource = sourceBounds.takeIf { it.width > 0f && it.height > 0f }
            ?: Rect(
                left = horizontalInsetPx,
                top = heightPx / 2f - verticalInsetPx,
                right = widthPx - horizontalInsetPx,
                bottom = heightPx / 2f + verticalInsetPx,
            )
        val frame = captionLerpRect(safeSource, destinationBounds, progress.value)
        val corner = with(density) { captionLerp(16.dp.toPx(), 28.dp.toPx(), progress.value).toDp() }
        val sourceAlpha = (1f - progress.value / 0.44f).coerceIn(0f, 1f)
        val destinationAlpha = ((progress.value - 0.16f) / 0.42f).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * progress.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::close,
                ),
        )

        Box(
            Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(
                    width = with(density) { frame.width.toDp() },
                    height = with(density) { frame.height.toDp() },
                )
                .graphicsLayer {
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                    clip = true
                    shadowElevation = 24f * density.density * progress.value
                }
                .background(surface)
                .zIndex(1f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sourceAlpha },
            ) {
                source()
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = destinationAlpha },
            ) {
                destination(::close) { measuredHeight ->
                    if (measuredHeight > 0 && measuredHeight != destinationContentHeightPx) {
                        destinationContentHeightPx = measuredHeight
                    }
                }
            }
        }
    }
}

private fun captionLerpRect(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = captionLerp(start.left, end.left, progress),
    top = captionLerp(start.top, end.top, progress),
    right = captionLerp(start.right, end.right, progress),
    bottom = captionLerp(start.bottom, end.bottom, progress),
)

private fun captionLerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

@Composable
private fun ReelsCaptionBody(
    content: String,
    needsMore: Boolean,
    baseTextColor: Color,
    hashtagTextColor: Color,
    mentionTextColor: Color,
    onHashtagTap: (String) -> Unit,
    onMentionTap: (String) -> Unit,
    isExpandedExternal: Boolean?,
    onExpandedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expandedInternal by remember(content) { mutableStateOf(false) }
    val isExpanded = isExpandedExternal ?: expandedInternal
    val setExpanded: (Boolean) -> Unit = { value ->
        if (isExpandedExternal == null) {
            expandedInternal = value
        }
        onExpandedChange?.invoke(value)
    }

    val springSpec = spring<IntSize>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val bodyFontSize = 14.sp
    val seeMore = stringResource(R.string.feed_see_more)
    val seeLess = stringResource(R.string.feed_see_less)
    val density = LocalDensity.current
    val expandedMaxHeightPx = with(density) { 220.dp.roundToPx() }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = springSpec),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isExpanded) {
            val scrollHeightPx = minOf(contentHeightPx, expandedMaxHeightPx).coerceAtLeast(0)
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (scrollHeightPx > 0) {
                            Modifier.height(with(density) { scrollHeightPx.toDp() })
                        } else {
                            Modifier
                        },
                    )
                    .verticalScroll(rememberScrollState()),
            ) {
                MomentHashtagText(
                    content = content,
                    onHashtagTap = onHashtagTap,
                    onMentionTap = onMentionTap,
                    baseColor = baseTextColor,
                    hashtagColor = hashtagTextColor,
                    mentionColor = mentionTextColor,
                    fontSize = bodyFontSize,
                    actionText = " $seeLess",
                    actionColor = baseTextColor,
                    onActionTap = {
                        HapticManager.shared.lightImpact()
                        setExpanded(false)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .onSizeChanged { contentHeightPx = it.height },
                )
            }
        } else {
            MomentHashtagText(
                content = truncatedCollapsedText(content, needsMore),
                onHashtagTap = onHashtagTap,
                onMentionTap = onMentionTap,
                baseColor = baseTextColor,
                hashtagColor = hashtagTextColor,
                mentionColor = mentionTextColor,
                fontSize = bodyFontSize,
                lineLimit = 2,
                actionText = if (needsMore) " … $seeMore" else null,
                actionColor = baseTextColor,
                onActionTap = if (needsMore) {
                    {
                        HapticManager.shared.lightImpact()
                        setExpanded(true)
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (needsMore) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                HapticManager.shared.lightImpact()
                                setExpanded(true)
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/** Port de `MomentCaptionReaderCard` (MomentCaptionView.swift). */
@Composable
private fun MomentCaptionReaderCard(
    content: String,
    mediaPreviewContext: CaptionMediaPreviewContext?,
    onHashtagTap: (String) -> Unit,
    onMentionTap: (String) -> Unit,
    onClose: () -> Unit,
    onContentHeightChange: (Int) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val baseColor = sheetBaseTextColor(isDark)
    val hashtagColor = captionHashtagTextColor(isDark)
    val context = LocalContext.current
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .padding(bottom = 14.dp)
                .onSizeChanged { onContentHeightChange(it.height) },
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                mediaPreviewContext?.let { preview ->
                    MomentCaptionReaderThumbnail(
                        context = preview,
                        isDark = isDark,
                    )
                }

                mediaPreviewContext?.let { preview ->
                    LiveUsernameText(
                        userId = preview.authorId,
                        fallbackUsername = preview.username,
                        color = baseColor,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .size(38.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = baseColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.edit_moment_description),
                    color = baseColor,
                    fontSize = with(density) { legacyPoppinsSize(context, 17).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )

                MomentHashtagText(
                    content = content,
                    onHashtagTap = onHashtagTap,
                    onMentionTap = onMentionTap,
                    baseColor = baseColor,
                    hashtagColor = hashtagColor,
                    mentionColor = captionMentionTextColor,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MomentCaptionReaderThumbnail(
    context: CaptionMediaPreviewContext,
    isDark: Boolean,
) {
    val isProtected = (context.audience?.lowercase() ?: "") != "everyone"

    ScreenshotProtectedView(isProtected = isProtected) {
        Box(
            Modifier
                .size(58.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!context.mediaUrl.isNullOrBlank()) {
                AsyncImage(
                    model = context.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = if (isDark) {
                                    listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.04f),
                                    )
                                } else {
                                    listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Black.copy(alpha = 0.03f),
                                    )
                                },
                            ),
                        ),
                )
            }

            if (context.isVideo) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(14.dp)
                        .shadow(3.dp, CircleShape),
                )
            }
        }
    }
}
