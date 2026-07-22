package com.moments.android.views.feed.sharing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moments.android.R

/** Port de helpers de `StoryShare.swift`. */
enum class SharedStoryAccessDenialReason {
    Expired,
    NotFound,
    Blocked,
    PrivateAccount,
    Restricted,
}

fun storyPreviewUrl(
    backgroundFrameUrl: String?,
    backgroundBlurredFrameUrl: String?,
    mediaUrl: String,
): String {
    backgroundFrameUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    backgroundBlurredFrameUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return mediaUrl
}

fun storyMediaTypeString(isVideo: Boolean): String = if (isVideo) "video" else "image"

fun buildStoryShareUrl(authorId: String, storyId: String): String =
    "https://momentsapp.app/story/$storyId?a=$authorId"

/** Port de flujo UI de `StoryShare.swift` — placeholder. */
@Composable
fun StoryShareFlowPlaceholder(
    momentId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.feed_share_story))
    }
}
