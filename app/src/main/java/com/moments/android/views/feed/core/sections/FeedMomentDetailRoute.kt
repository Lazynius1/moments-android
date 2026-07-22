package com.moments.android.views.feed.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.FeedTeal
import com.moments.android.views.shared.momentdetail.MomentDetailContainerView
import com.moments.android.views.shared.momentdetail.MomentDetailContext
import kotlinx.coroutines.CancellationException

/**
 * Port de `FeedMomentDetailRoute.swift` / `MomentDetailFromNotificationView`.
 */
@Composable
fun FeedMomentDetailRoute(
    momentId: String,
    authorId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moment by remember { mutableStateOf<FeedMoment?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val firestore = remember { FirestoreService() }
    val loadFailed = stringResource(R.string.errors_moment_load_failed)
    val notFound = stringResource(R.string.errors_moment_not_found)

    LaunchedEffect(momentId, authorId) {
        isLoading = true
        errorMessage = null
        try {
            val loaded: Moment = firestore.fetchMoment(momentId, authorId)
            moment = loaded.toFeedMoment()
            if (moment == null) errorMessage = notFound
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            errorMessage = loadFailed
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> LoadingMomentView(modifier)
        errorMessage != null -> ErrorMomentView(
            message = errorMessage!!,
            onClose = onDismiss,
            modifier = modifier,
        )
        moment != null -> {
            // Paridad iOS FeedMomentDetailRoute → MomentDetailContainerView(.single)
            MomentDetailContainerView(
                context = MomentDetailContext.Single(moment!!),
                onDismiss = onDismiss,
                modifier = modifier,
            )
        }
        else -> ErrorMomentView(message = notFound, onClose = onDismiss, modifier = modifier)
    }
}

/** Port de `LoadingMomentView`. */
@Composable
fun LoadingMomentView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Box(
        modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = FeedTeal)
            Text(
                text = stringResource(R.string.feed_loading_moment),
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Port de `ErrorMomentView`. */
@Composable
fun ErrorMomentView(
    message: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Box(
        modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.common_close),
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Red)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

private fun Moment.toFeedMoment(): FeedMoment {
    val media = (mediaItems ?: emptyList()).mapIndexed { index, item ->
        FeedMediaItem(
            id = item.id.ifBlank { "$index" },
            type = item.type.raw,
            url = item.url,
            thumbnailUrl = item.thumbnailUrl,
            aspectRatio = item.aspectRatio,
            isHiddenByModeration = item.isHiddenByModeration,
            tags = item.tags,
            videoDuration = item.videoDuration,
        )
    }.ifEmpty {
        buildList {
            imagePath?.takeIf { it.isNotBlank() }?.let {
                add(FeedMediaItem(id = "img", type = "image", url = it, thumbnailUrl = null, aspectRatio = aspectRatio))
            }
            videoUrl?.takeIf { it.isNotBlank() }?.let {
                add(FeedMediaItem(id = "vid", type = "video", url = it, thumbnailUrl = thumbnailUrl, aspectRatio = aspectRatio))
            }
        }
    }
    val reactionTotal = reactions.values.sumOf { it.size }
    return FeedMoment(
        id = id.orEmpty(),
        authorId = authorId,
        username = username,
        content = content,
        timestamp = timestamp.time,
        profileImagePath = profileImagePath,
        location = location,
        mediaItems = media,
        aspectRatio = aspectRatio,
        commentCount = commentCount,
        reactionCount = reactionTotal,
        hideLikeCounts = hideLikeCounts,
        disableComments = disableComments,
        hasHiddenLayers = hasHiddenLayers,
        hiddenLayerCount = hiddenLayerCount,
        audience = audience,
        customListId = customListId,
        isArchived = isArchived,
        locationCoordinate = locationCoordinate,
    )
}
