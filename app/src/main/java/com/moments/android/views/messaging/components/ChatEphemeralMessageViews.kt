package com.moments.android.views.messaging.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.markEphemeralAsViewed
import java.util.Date
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * Port de `Views/Messaging/Components/ChatEphemeralMessageViews.swift`.
 * Diseño ámbar/dorado propio (sin anillo story ni view-once).
 */
enum class ChatEphemeralLayout(
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
    val iconPreset: AttachmentIconPreset,
) {
    COMPACT(76.dp, 118.dp, 14.dp, AttachmentIconPreset.STORY_EPHEMERAL),
    STANDARD(188.dp, 240.dp, 18.dp, AttachmentIconPreset.CHAT_EPHEMERAL_PLACEHOLDER),
}

object ChatEphemeralTimeFormatting {
    fun remainingSeconds(expirationDate: Date, now: Date = Date()): Long =
        max(0L, (expirationDate.time - now.time) / 1_000L)

    /** ≡ iOS `shortLabel(for:)` — literales `"1d"`, `"2h 3m"`, `"5m"`, `"<1m"`. */
    fun shortLabel(remainingSeconds: Long): String {
        val total = max(0L, remainingSeconds)
        val hours = total / 3_600
        val minutes = (total % 3_600) / 60
        return when {
            hours >= 24 -> "${hours / 24}d"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun shortLabel(expirationDate: Date, now: Date = Date()): String =
        shortLabel(remainingSeconds(expirationDate, now))
}

private val ephemeralAccent = Color(0xFFFFCC33)
private val ephemeralSecondary = Color(0xFFFF9500)
private val ephemeralCardGradient = Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF2A2418)))
private val ephemeralAccentBorder = Brush.linearGradient(
    listOf(ephemeralAccent, ephemeralSecondary.copy(alpha = 0.85f)),
)
private val ephemeralSaturationFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0.65f) },
)

@Composable
fun ChatEphemeralMessageContent(
    message: EnhancedMessage,
    layout: ChatEphemeralLayout,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    onOpenMedia: ((EnhancedMessage) -> Unit)? = null,
    onMarkViewed: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showContent by remember(message.id) { mutableStateOf(message.isViewed) }
    val scope = rememberCoroutineScope()
    val valid = message.expirationDate?.after(Date()) ?: true
    val preview = message.thumbnailUrl ?: message.mediaUrl
    val resolvedMediaUrl = message.mediaUrl?.takeIf { it.isNotBlank() }
        ?: message.thumbnailUrl?.takeIf { it.isNotBlank() }

    // ≡ onAppear: showContent = isViewed + hydrate
    LaunchedEffect(message.id, message.isViewed) {
        showContent = message.isViewed
        onHydrateMedia?.invoke(message)
    }

    val branch = when {
        message.isDeleted || !valid -> EphemeralBranch.EXPIRED
        !showContent && !message.isViewed -> EphemeralBranch.TAP
        resolvedMediaUrl != null -> EphemeralBranch.IMAGE
        message.isMediaPendingResolution -> EphemeralBranch.RESOLVING
        else -> EphemeralBranch.EXPIRED
    }

    // ≡ MotionPolicy.withOptionalAnimation(Spring.toggle) al revelar
    val reveal: @Composable (EphemeralBranch) -> Unit = { current ->
        when (current) {
            EphemeralBranch.EXPIRED -> ChatEphemeralExpiredCard(layout, modifier)
            EphemeralBranch.TAP -> ChatEphemeralTapCard(
                layout = layout,
                previewImageUrl = preview,
                expirationDate = message.expirationDate,
                modifier = modifier,
            ) {
                // ≡ iOS MotionPolicy.withOptionalAnimation(Spring.toggle) { showContent = true }
                showContent = true
                onHydrateMedia?.invoke(message)
                if (!message.isViewed) {
                    if (onMarkViewed != null) {
                        onMarkViewed(message)
                    } else {
                        // ≡ iOS `ChatService().markEphemeralAsViewed` en el componente
                        scope.launch {
                            ChatService.markEphemeralAsViewed(message.conversationId, message.id)
                        }
                    }
                }
            }
            EphemeralBranch.IMAGE -> ChatEphemeralImageCard(
                layout = layout,
                imageUrl = checkNotNull(resolvedMediaUrl),
                expirationDate = message.expirationDate,
                modifier = modifier,
            ) { onOpenMedia?.invoke(message) }
            EphemeralBranch.RESOLVING -> ChatEphemeralResolvingCard(layout, modifier)
        }
    }

    if (MotionPolicy.reduceMotion) {
        reveal(branch)
    } else {
        AnimatedContent(
            targetState = branch,
            transitionSpec = {
                fadeIn(
                    spring(
                        dampingRatio = MotionPolicy.Spring.TOGGLE_DAMPING.toFloat(),
                        stiffness = 400f,
                    ),
                ) togetherWith fadeOut(tween(160))
            },
            label = "ephemeralReveal",
        ) { current ->
            reveal(current)
        }
    }
}

private enum class EphemeralBranch { EXPIRED, TAP, IMAGE, RESOLVING }

@Composable
fun ChatEphemeralTapCard(
    layout: ChatEphemeralLayout,
    previewImageUrl: String?,
    expirationDate: Date?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    val isDark = isSystemInDarkTheme()
    val scrimAlpha = if (isDark) 0.42f else 0.32f
    val a11yMedia = stringResource(R.string.chat_view_once_media)
    val a11yHint = stringResource(R.string.chat_view_once_tap_to_view)
    val canUsePreview = remember(previewImageUrl) { isUsableEphemeralPreviewUrl(previewImageUrl) }
    Box(
        modifier
            .size(layout.width, layout.height)
            .clip(shape)
            .border(1.5.dp, ephemeralAccentBorder, shape)
            .semantics { contentDescription = "$a11yMedia. $a11yHint" }
            .clickable(onClick = onTap),
    ) {
        if (canUsePreview) {
            AsyncImage(
                model = previewImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ephemeralSaturationFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (layout == ChatEphemeralLayout.COMPACT) 18.dp else 24.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(ephemeralCardGradient))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            AttachmentIconView(
                icon = AttachmentIcon.EPHEMERAL,
                preset = layout.iconPreset,
                tintColor = ephemeralAccent.copy(alpha = 0.95f),
                modifier = Modifier.shadow(
                    elevation = 4.dp,
                    ambientColor = Color.Black.copy(0.35f),
                    spotColor = Color.Black.copy(0.35f),
                ),
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = if (layout == ChatEphemeralLayout.COMPACT) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.chat_tap_to_view),
                    color = Color.White,
                    fontSize = if (layout == ChatEphemeralLayout.COMPACT) 11.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.chat_ephemeral_title),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = if (layout == ChatEphemeralLayout.COMPACT) 10.sp else 11.sp,
                )
                expirationDate?.takeIf { it.after(Date()) }?.let {
                    Text(
                        stringResource(R.string.stories_expires_in, ChatEphemeralTimeFormatting.shortLabel(it)),
                        color = ephemeralAccent.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun ChatEphemeralImageCard(
    layout: ChatEphemeralLayout,
    imageUrl: String,
    expirationDate: Date?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    val a11yPhoto = stringResource(R.string.chat_view_once_photo)
    val a11yHint = stringResource(R.string.chat_view_once_tap_to_view)
    Box(
        modifier
            .size(layout.width, layout.height)
            .clip(shape)
            .border(1.dp, ephemeralAccent.copy(alpha = 0.45f), shape)
            .semantics { contentDescription = "$a11yPhoto. $a11yHint" }
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        expirationDate?.takeIf { it.after(Date()) }?.let {
            // ≡ iOS Capsule fill + stroke accent (topTrailing)
            Text(
                ChatEphemeralTimeFormatting.shortLabel(it),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(0.5.dp, ephemeralAccent.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ChatEphemeralResolvingCard(layout: ChatEphemeralLayout, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    Box(
        modifier
            .size(layout.width, layout.height)
            .clip(shape)
            .background(ephemeralCardGradient)
            .border(1.dp, ephemeralAccent.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = ephemeralAccent,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
            Text(
                stringResource(R.string.common_loading),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun ChatEphemeralExpiredCard(layout: ChatEphemeralLayout, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    Box(
        modifier
            .size(layout.width, layout.height)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(if (layout == ChatEphemeralLayout.COMPACT) 18.dp else 22.dp),
            )
            Text(
                stringResource(R.string.stories_ephemeral_expired),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = if (layout == ChatEphemeralLayout.COMPACT) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** ≡ iOS KF backdrop: file:// o http(s). */
private fun isUsableEphemeralPreviewUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return lower.startsWith("file:") ||
        lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("content:")
}
