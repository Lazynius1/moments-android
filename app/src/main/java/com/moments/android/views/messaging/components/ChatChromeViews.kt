package com.moments.android.views.messaging.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MessageType
import com.moments.android.models.PendingChatContext
import com.moments.android.models.PendingChatTimelineMessage
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadge
import java.util.Calendar
import java.util.Date

/** Port de `Views/Messaging/Components/ChatChromeViews.swift`. */
object ChatComposerChromeMetrics {
    val panelHomeGap = 16.dp
    val messageListGap = 11.dp
    val fadeExtendAbovePanel = 20.dp
    val fadeEdgeSize = 60.dp
    const val fadeAlphaSolid = .82f
    val estimatedComposerChromeHeight = 68.dp

    fun listBottomInset(composerChromeHeight: Dp): Dp =
        maxOf(composerChromeHeight, estimatedComposerChromeHeight) + messageListGap

    fun floatingControlBottomInset(composerChromeHeight: Dp): Dp =
        maxOf(composerChromeHeight, estimatedComposerChromeHeight) + 20.dp
}

/** Android's system back dispatcher is already gesture-enabled, unlike the iOS controller bridge. */
fun Modifier.chatInteractivePopEnabled(): Modifier = this
fun Modifier.navigationInteractivePopEnabled(): Modifier = chatInteractivePopEnabled()
fun Modifier.messagingListEdgeToEdge(): Modifier = this
fun Modifier.chatBottomScrollEdgeHidden(): Modifier = this
fun Modifier.chatScrollEdgeEffect(hardBottomEdge: Boolean = false): Modifier = this
fun Modifier.momentsScrollEdgeChrome(hardBottomEdge: Boolean = false): Modifier = chatScrollEdgeEffect(hardBottomEdge)

@Composable
fun ChatBottomWallpaperEdgeFade(
    color: Color,
    composerChromeHeight: Dp,
    extendAbovePanel: Dp = ChatComposerChromeMetrics.fadeExtendAbovePanel,
    edgeSize: Dp = ChatComposerChromeMetrics.fadeEdgeSize,
    alpha: Float = ChatComposerChromeMetrics.fadeAlphaSolid,
    modifier: Modifier = Modifier,
) {
    val chrome = maxOf(composerChromeHeight, ChatComposerChromeMetrics.estimatedComposerChromeHeight)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chrome + extendAbovePanel + edgeSize)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .55f to color.copy(alpha = alpha * .4f),
                        1f to color.copy(alpha = alpha),
                    ),
                ),
            ),
    )
}

@Composable
fun ChatHistoryLoadingIndicator(
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    @StringRes textRes: Int = R.string.chat_loading_older_messages,
    showsProgress: Boolean = true,
    @StringRes retryTextRes: Int? = null,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .momentsChromeGlass(shape, interactive = false)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = if (isDark) .22f else .12f), spotColor = Color.Black.copy(alpha = if (isDark) .22f else .12f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsProgress) CircularProgressIndicator(
            color = if (isDark) Color.White else adaptiveColors.primary,
            strokeWidth = 1.5.dp,
            modifier = Modifier.size(14.dp),
        )
        Text(stringResource(textRes), color = if (isDark) Color.White.copy(alpha = .92f) else Color.Black.copy(alpha = .82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        retryTextRes?.let { Text(stringResource(it), color = adaptiveColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ChatHistoryStartHeader(
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.chat_history_start),
        color = adaptiveColors.secondary.copy(alpha = .85f),
        fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
fun ChatConversationIntroRow(
    context: PendingChatContext?,
    fallbackName: String,
    fallbackUserId: String,
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    val displayName = context?.otherUsername ?: fallbackName
    val userId = context?.otherUserId ?: fallbackUserId
    val stats = context?.let {
        val followers = it.otherFollowersCount ?: 0
        val moments = it.otherMomentsCount ?: 0
        if (followers > 0 || moments > 0) stringResource(
            R.string.chat_intro_profile_stats,
            MomentsFormat.count(followers, MomentsFormat.CountStyle.PROFILE_STAT),
            MomentsFormat.count(moments, MomentsFormat.CountStyle.PROFILE_STAT),
        ) else null
    }
    val subtitle: Int? = if (context == null) {
        R.string.chat_intro_normal
    } else {
        when (context.status) {
            PendingChatContext.Status.NORMAL_CONVERSATION -> R.string.chat_intro_normal
            PendingChatContext.Status.INCOMING_REQUEST_PENDING -> R.string.chat_intro_request_incoming
            PendingChatContext.Status.OUTGOING_REQUEST_DRAFT -> null
            PendingChatContext.Status.OUTGOING_REQUEST_SENT -> R.string.chat_intro_request_sent
            PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED -> R.string.chat_intro_request_blocked
        }
    }
    val relationship = context?.let { relationshipText(it) }

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp).padding(top = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncProfileImageView(userId, Modifier.size(96.dp).clip(CircleShape))
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, color = adaptiveColors.primary, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (context?.otherIsVerified == true) VerifiedBadge(size = 20.dp)
            }
            stats?.let { Text(it, color = adaptiveColors.secondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
            if (subtitle != null) Text(stringResource(subtitle), color = adaptiveColors.secondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3)
            relationship?.let { Text(it, color = adaptiveColors.secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2) }
        }
    }
}

@Composable
private fun relationshipText(context: PendingChatContext): String {
    val username = context.otherUsername
    val mutualDate = listOfNotNull(context.viewerFollowedAt, context.otherFollowedViewerAt).maxOrNull()
    return when {
        context.viewerFollowsOther == true && context.otherFollowsViewer == true && mutualDate != null ->
            stringResource(R.string.chat_intro_relationship_mutual_since, username, yearString(mutualDate))
        context.viewerFollowsOther == true && context.otherFollowsViewer == true ->
            stringResource(R.string.chat_intro_relationship_mutual, username)
        context.viewerFollowsOther == true && context.viewerFollowedAt != null ->
            stringResource(R.string.chat_intro_relationship_viewer_follows_since, username, yearString(context.viewerFollowedAt))
        context.viewerFollowsOther == true -> stringResource(R.string.chat_intro_relationship_viewer_follows, username)
        context.otherFollowsViewer == true && context.otherFollowedViewerAt != null ->
            stringResource(R.string.chat_intro_relationship_other_follows_since, username, yearString(context.otherFollowedViewerAt))
        context.otherFollowsViewer == true -> stringResource(R.string.chat_intro_relationship_other_follows, username)
        else -> stringResource(R.string.chat_intro_relationship_not_mutual)
    }
}

private fun yearString(date: Date): String = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR).toString()

@Composable
fun ChatRequestInviteNotice(
    displayName: String,
    username: String,
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().border(.5.dp, adaptiveColors.secondary.copy(alpha = .12f), RoundedCornerShape(0.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).momentsChromeGlass(CircleShape, interactive = false), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Send, null, tint = adaptiveColors.secondary, modifier = Modifier.size(21.dp))
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.chat_request_invite_title, displayName, username), color = adaptiveColors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.chat_request_invite_body), color = adaptiveColors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ChatRequestDisclaimerRow(
    @StringRes textRes: Int,
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false).padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Info, null, tint = adaptiveColors.secondary, modifier = Modifier.size(13.dp))
        Text(stringResource(textRes), color = adaptiveColors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PendingRequestMessageRow(
    message: PendingChatTimelineMessage,
    adaptiveColors: com.moments.android.views.feed.AdaptiveColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start) {
        val background = if (message.isOutgoing) Color.Blue.copy(alpha = .92f) else adaptiveColors.messageBubbleBackground
        val contentColor = if (message.isOutgoing) Color.White else adaptiveColors.primary
        Row(
            modifier = Modifier.widthIn(max = LocalConfiguration.current.screenWidthDp.dp - 96.dp).clip(RoundedCornerShape(18.dp)).background(background).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (message.messageType) {
                MessageType.IMAGE -> { Icon(Icons.Default.Photo, null, tint = contentColor); Text(stringResource(R.string.message_requests_image), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                MessageType.VIDEO -> { Icon(Icons.Default.PlayCircle, null, tint = contentColor); Text(stringResource(R.string.message_requests_video), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                else -> Text(message.text, color = contentColor, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun MessagingSectionHeader(@StringRes titleRes: Int, adaptiveColors: com.moments.android.views.feed.AdaptiveColors, modifier: Modifier = Modifier) {
    Text(stringResource(titleRes), color = adaptiveColors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(top = 10.dp))
}

@Composable
fun ChatTintedGlassCircleButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    foregroundColor: Color,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size).clip(CircleShape).momentsChromeGlass(CircleShape, interactive = true, tint = tint).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(imageVector, null, tint = foregroundColor, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ChatGlassmorphicBackground(adaptiveColors: com.moments.android.views.feed.AdaptiveColors, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(adaptiveColors.chatBackground.first()))
}

fun Modifier.glassmorphicChat(): Modifier = composed { glassmorphicChrome(circle = false) }
fun Modifier.glassmorphicChatCircle(): Modifier = composed { glassmorphicChrome(circle = true) }

@Composable
private fun Modifier.glassmorphicChrome(circle: Boolean): Modifier {
    val isDark = isSystemInDarkTheme()
    val shape = if (circle) CircleShape else RoundedCornerShape(0.dp)
    return this.clip(shape).background(if (isDark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .70f), shape).border(.5.dp, if (isDark) Color.White.copy(alpha = .2f) else Color.Black.copy(alpha = .1f), shape)
}

@Composable
fun GlassmorphicDateHeader(date: Date, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    Text(MomentsFormat.smartDate(date, MomentsFormat.DateContext.CHAT_SEPARATOR), color = colors.dateHeaderColor, fontSize = 12.sp, modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp).glassmorphicChat().clip(RoundedCornerShape(50)))
}

@Composable
fun GlassmorphicUnreadDivider(unreadCount: Int = 0, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.secondary.copy(alpha = .25f)))
        Row(Modifier.clip(RoundedCornerShape(50)).background(colors.chatInputBackground).border(.5.dp, colors.messageBubbleStroke.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Circle, null, tint = colors.primary.copy(alpha = .9f), modifier = Modifier.size(5.dp))
            Text(if (unreadCount > 1) stringResource(R.string.chat_unread_count_preview, unreadCount) else stringResource(R.string.chat_new_messages), color = colors.primary.copy(alpha = .9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f).height(1.dp).background(colors.secondary.copy(alpha = .25f)))
    }
}

@Composable
fun GlassmorphicAvatar(userId: String, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    AsyncProfileImageView(userId, modifier.shadow(4.dp, CircleShape, ambientColor = colors.primary.copy(alpha = .1f), spotColor = colors.primary.copy(alpha = .1f)))
}

@Composable
fun GlassmorphicTypingIndicator(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    val transition = rememberInfiniteTransition(label = "chatTyping")
    Row(modifier = modifier.clip(RoundedCornerShape(20.dp)).glassmorphicChat().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val amount by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600, delayMillis = index * 200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "typing$index")
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.typingIndicatorColor.copy(alpha = if (reduceMotion) .85f else amount)).then(if (reduceMotion) Modifier else Modifier))
        }
    }
}

@Composable
fun MessagingActionToast(text: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(14.dp)
    Text(text, color = if (isDark) Color.White else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.fillMaxWidth().clip(shape).momentsChromeGlass(shape, interactive = false).shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = if (isDark) .22f else .12f), spotColor = Color.Black.copy(alpha = if (isDark) .22f else .12f)).padding(horizontal = 18.dp, vertical = 14.dp))
}

@Composable
fun ChatScrollDownButton(pendingCount: Int, accentColor: Color, badgeTextColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(46.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_scroll_to_bottom_accessibility), tint = accentColor, modifier = Modifier.size(17.dp)) }
        if (pendingCount > 0) Text(if (pendingCount > 99) stringResource(R.string.chat_scroll_badge_max) else pendingCount.toString(), color = badgeTextColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(accentColor).padding(horizontal = 5.dp).height(18.dp))
    }
}

@Composable
fun ChatInThreadSearchField(text: String, onTextChange: (String) -> Unit, adaptiveColors: com.moments.android.views.feed.AdaptiveColors, onClear: () -> Unit, onSubmit: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .06f)).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = adaptiveColors.secondary.copy(alpha = .75f), modifier = Modifier.size(15.dp))
        BasicTextField(value = text, onValueChange = onTextChange, textStyle = TextStyle(color = adaptiveColors.primary, fontSize = 15.sp), cursorBrush = SolidColor(adaptiveColors.primary), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }), modifier = Modifier.weight(1f), decorationBox = { inner -> if (text.isEmpty()) Text(stringResource(R.string.chat_search_placeholder), color = adaptiveColors.secondary, fontSize = 15.sp); inner() })
        if (text.isNotEmpty()) Icon(Icons.Default.Close, stringResource(R.string.chat_attachment_clear_accessibility), tint = adaptiveColors.secondary.copy(alpha = .65f), modifier = Modifier.size(16.dp).clickable(onClick = onClear))
    }
}
