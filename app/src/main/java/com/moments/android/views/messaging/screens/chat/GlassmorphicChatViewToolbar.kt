package com.moments.android.views.messaging.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.PresenceDisplay
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.components.ChatSearchNavigationBar

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
    hasStory: Boolean,
    hasTypingUsers: Boolean,
    presence: PresenceDisplay?,
    callbacks: ChatToolbarCallbacks,
    modifier: Modifier = Modifier,
) {
    val subtitleRes = when {
        isBlockedByMe -> R.string.chat_blocked_by_me_subtitle
        isUnavailable -> R.string.chat_profile_unavailable
        hasTypingUsers -> R.string.chat_typing
        else -> null
    }
    Row(
        modifier.fillMaxWidth().background(adaptiveColors.chatBackground.first()).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = callbacks.onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_toolbar_back), tint = adaptiveColors.primary)
        }
        AsyncImage(
            model = profileImagePath,
            contentDescription = stringResource(R.string.chat_toolbar_profile),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(adaptiveColors.secondary.copy(.2f)).clickable {
                if (!isUnavailable && !isBlockedByMe && hasStory) callbacks.onStory() else callbacks.onProfile()
            },
        )
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
                )
                Icon(Icons.Default.ChevronRight, null, tint = adaptiveColors.secondary.copy(.6f), modifier = Modifier.size(14.dp))
            }
            when {
                subtitleRes != null -> Text(stringResource(subtitleRes), color = adaptiveColors.secondary, fontSize = 11.sp, maxLines = 1)
                presence != null -> Text(listOfNotNull(presence.statusText, presence.supplementalText).joinToString(" • "), color = adaptiveColors.secondary, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun GlassmorphicChatSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    adaptiveColors: AdaptiveColors,
    callbacks: ChatToolbarCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        ChatSearchNavigationBar(
            text = query,
            onTextChange = onQueryChange,
            adaptiveColors = adaptiveColors,
            onClear = callbacks.onSearchClear,
            onClose = callbacks.onSearchClose,
            onSubmit = callbacks.onSearchSubmit,
        )
    }
}
