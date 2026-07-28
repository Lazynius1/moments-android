package com.moments.android.views.messaging.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.messaging.core.PresenceDisplay
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.profile.userprofile.sections.ProfileUnavailableAvatar
import com.moments.android.views.story.StorySegmentedRing

/** Port de `GlassmorphicChatView+Toolbar.swift`. */
data class ChatToolbarCallbacks(
    val onBack: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onStory: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onSearchClose: () -> Unit = {},
    val onSearchClear: () -> Unit = {},
    val onSearchSubmit: () -> Unit = {},
)

@Composable
fun GlassmorphicChatToolbar(
    displayName: String,
    userId: String,
    profileImagePath: String?,
    adaptiveColors: AdaptiveColors,
    isUnavailable: Boolean,
    isBlockedByMe: Boolean,
    storyRing: StoryRingSnapshot,
    hasTypingUsers: Boolean,
    presence: PresenceDisplay?,
    callbacks: ChatToolbarCallbacks,
    modifier: Modifier = Modifier,
) {
    val hasStory = storyRing.hasStory
    Row(
        modifier
            .fillMaxWidth()
            .background(adaptiveColors.chatBackground.first())
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = callbacks.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_toolbar_back), tint = adaptiveColors.primary)
        }
        Box(
            Modifier
                .size(40.dp)
                .clickable {
                    if (isUnavailable && !isBlockedByMe) callbacks.onProfile()
                    else if (hasStory && !isBlockedByMe) callbacks.onStory()
                    else callbacks.onProfile()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isUnavailable && !isBlockedByMe) {
                ProfileUnavailableAvatar(size = 40.dp)
            } else {
                AsyncImage(
                    model = profileImagePath,
                    contentDescription = stringResource(R.string.chat_toolbar_profile),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(adaptiveColors.secondary.copy(.2f)),
                )
                StorySegmentedRing(
                    storyCount = storyRing.storyCount,
                    hasStory = storyRing.hasStory,
                    hasUnseenStory = storyRing.hasUnseenStory,
                    storyViewedStatus = storyRing.storyViewedStatus,
                    storyAudiences = storyRing.storyAudiences,
                    isOwnStory = false,
                    ringSize = 40.dp,
                    lineWidth = 2.7.dp,
                )
            }
        }
        Column(Modifier.weight(1f).clickable(onClick = callbacks.onSettings)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    displayName,
                    color = adaptiveColors.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isUnavailable && !isBlockedByMe) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!isUnavailable) {
                    VerifiedBadgeView(userId = userId, size = 14.dp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = adaptiveColors.secondary.copy(.6f), modifier = Modifier.size(14.dp))
            }
            ChatToolbarSubtitle(
                isBlockedByMe = isBlockedByMe,
                isUnavailable = isUnavailable,
                hasTypingUsers = hasTypingUsers,
                presence = presence,
                adaptiveColors = adaptiveColors,
            )
        }
    }
}

@Composable
private fun ChatToolbarSubtitle(
    isBlockedByMe: Boolean,
    isUnavailable: Boolean,
    hasTypingUsers: Boolean,
    presence: PresenceDisplay?,
    adaptiveColors: AdaptiveColors,
) {
    when {
        isBlockedByMe -> Text(
            stringResource(R.string.chat_blocked_by_me_subtitle),
            color = adaptiveColors.secondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
        isUnavailable -> Text(
            stringResource(R.string.chat_profile_unavailable),
            color = adaptiveColors.secondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
        hasTypingUsers -> Text(
            stringResource(R.string.chat_typing),
            color = adaptiveColors.secondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
        presence != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = Color(presence.status.colorArgb),
                modifier = Modifier.size(7.dp),
            )
            Text(presence.statusText, color = adaptiveColors.secondary, fontSize = 11.sp, maxLines = 1)
            presence.supplementalText?.let { lastSeen ->
                Text("• $lastSeen", color = adaptiveColors.secondary.copy(.7f), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

/** ≡ `chatHeaderSearchBar` — pill glass + X circular; ProgressView mientras busca historial. */
@Composable
fun GlassmorphicChatSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    adaptiveColors: AdaptiveColors,
    callbacks: ChatToolbarCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(adaptiveColors.chatBackground.first())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(50))
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = adaptiveColors.secondary,
                    )
                } else {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = adaptiveColors.secondary.copy(.85f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = adaptiveColors.primary, fontSize = 17.sp),
                cursorBrush = SolidColor(adaptiveColors.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { callbacks.onSearchSubmit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.chat_search_placeholder),
                            color = adaptiveColors.secondary,
                            fontSize = 17.sp,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.chat_attachment_clear_accessibility),
                    tint = adaptiveColors.secondary.copy(.75f),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                        .clickable(onClick = callbacks.onSearchClear),
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(onClick = callbacks.onSearchClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = adaptiveColors.primary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
