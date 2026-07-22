package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.momentdetail.FeedPinnedTopChrome

/**
 * Port MVP de `LocationMomentDetailView.swift`:
 * LazyColumn de ModernPostCardView + chrome de lugar + availability blur.
 * Sheets edit/stories/explore quedan en stubs del card callbacks vía Single-like handlers
 * (comments/profile vía NavigationEventBus desde el card).
 */
@Composable
fun LocationMomentDetailView(
    moments: List<Moment>,
    initialIndex: Int,
    locationName: String,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val cardHeight = (configuration.screenHeightDp * 0.58f).dp
    val listState = rememberLazyListState()
    var feedMoments by remember(moments) {
        mutableStateOf(moments.map { it.toFeedMomentForMap() })
    }
    val safeIndex = initialIndex.coerceIn(0, (feedMoments.size - 1).coerceAtLeast(0))

    LaunchedEffect(safeIndex, feedMoments.size) {
        if (feedMoments.isNotEmpty()) {
            listState.scrollToItem(safeIndex)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 28.dp, start = 0.dp, end = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(feedMoments, key = { _, m -> "${m.authorId}_${m.id}" }) { _, moment ->
                val available = momentAvailability[moment.id] != false
                Box(Modifier.fillMaxWidth()) {
                    ScreenshotProtectedView(
                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                    ) {
                        ModernPostCardView(
                            moment = moment,
                            availableHeight = with(density) { cardHeight.toPx() },
                            onOpenProfile = { },
                            onOpenHashtag = { },
                            onOpenLocation = { _, _ -> },
                            onOpenComments = { },
                            onShare = { },
                            onContextMenu = { },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                                .then(if (!available) Modifier.blur(12.dp) else Modifier),
                        )
                    }
                    if (!available) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Overlay mínimo — paridad MomentUnavailableOverlay (texto en lote siguiente si hace falta)
                        }
                    }
                }
            }
        }

        FeedPinnedTopChrome(
            title = locationName.ifBlank { "Location" },
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Compat firma antigua (spinner call sites). */
@Composable
fun LocationMomentDetailView(
    locationName: String,
    momentIds: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sin pool de Moment: chrome + lista vacía (mapa aún no hidrata moments aquí).
    LocationMomentDetailView(
        moments = emptyList(),
        initialIndex = 0,
        locationName = locationName,
        momentAvailability = momentIds.associateWith { true },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

private fun Moment.toFeedMomentForMap(): FeedMoment {
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
