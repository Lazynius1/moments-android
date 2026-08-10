package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.Moment
import com.moments.android.services.performance.toVideoMoments
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.views.components.LiveUsernameText
import com.moments.android.views.components.MomentCaptionPresentationStyle
import com.moments.android.views.components.MomentCaptionView
import com.moments.android.views.components.ModernActionButtons
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.maps.mapssections.MomentUnavailableOverlay
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.moments.HiddenLayersOverlayView
import com.moments.android.views.feed.moments.MomentCarouselPageIndicators
import com.moments.android.views.feed.moments.MomentMediaCarousel
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.story.StoryRingAvatarView
import kotlin.math.max
import kotlin.math.min

/** ≡ iOS `LocationMomentCardLayout`. */
enum class LocationMomentCardLayout { Standalone, Feed }

/**
 * MARK: Tarjeta de momento de ubicación — `LocationMomentCard` en LocationMomentDetailView.swift.
 * Path activo del detalle usa `ModernPostCardView`; esta card queda para paridad de archivo / layouts legacy.
 */
@Composable
fun LocationMomentCard(
    moment: Moment,
    isAvailable: Boolean,
    availableHeight: Dp,
    commentCount: Int,
    isSaved: Boolean,
    isSaveLoading: Boolean,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onContextMenu: () -> Unit,
    onHashtagTap: (String) -> Unit,
    onAvatarTap: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    layoutMode: LocationMomentCardLayout = LocationMomentCardLayout.Standalone,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val feedMoment = remember(moment) { moment.toFeedMomentForMap() }
    val reelsVideos = remember(moment) { listOf(moment).toVideoMoments() }
    var detectedAspectRatio by remember(moment.id) {
        mutableFloatStateOf(moment.resolvedAspectRatioValue?.takeIf { it > 0f } ?: 1f)
    }
    var showTags by remember { mutableStateOf(false) }
    var isImmersive by remember { mutableStateOf(false) }
    var currentImageIndex by remember { mutableIntStateOf(0) }

    val aspectType = remember(detectedAspectRatio) { classifyLocationAspect(detectedAspectRatio) }
    val cardHeight = remember(detectedAspectRatio, availableHeight, aspectType, screenWidthDp) {
        val maxWidth = with(density) {
            FeedMomentCardLayout.mediaContentWidth(screenWidthDp).dp.toPx()
        }
        val ratio = detectedAspectRatio.takeIf { it > 0f && it.isFinite() } ?: aspectType.exactRatio
        val calculated = maxWidth / ratio
        val availPx = with(density) { availableHeight.toPx() }
        val dynamicMax = when (aspectType) {
            LocationAspectType.Square -> min(availPx * 0.82f, 680f)
            LocationAspectType.Portrait -> min(availPx * 0.92f, 820f)
            LocationAspectType.Landscape -> min(availPx * 0.68f, 440f)
            LocationAspectType.Reels -> availPx * 1.02f
        }
        val heightPx = if (aspectType == LocationAspectType.Reels) {
            min(max(calculated, availPx * 0.96f), dynamicMax)
        } else {
            min(calculated, dynamicMax)
        }
        with(density) { heightPx.toDp() }
    }

    val mediaItems = remember(moment) {
        val visible = moment.visibleMediaItems
        when {
            visible.isNotEmpty() -> visible
            moment.shouldUseLegacyMediaFallback -> buildList {
                moment.imagePath?.takeIf { it.isNotBlank() }?.let {
                    add(com.moments.android.models.MediaItem(type = com.moments.android.models.MediaItem.MediaType.IMAGE, url = it))
                }
                moment.videoUrl?.takeIf { it.isNotBlank() }?.let {
                    add(com.moments.android.models.MediaItem(type = com.moments.android.models.MediaItem.MediaType.VIDEO, url = it))
                }
            }
            else -> emptyList()
        }
    }

    val cardContent = @Composable {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    MomentMediaCarousel(
                        moment = feedMoment,
                        consumerId = GlobalVideoManager.profileVideoConsumerId(feedMoment),
                        fixedHeight = cardHeight,
                        applyOwnChrome = false,
                        showTags = showTags,
                        onToggleTags = { showTags = !showTags },
                        isImmersive = isImmersive,
                        onImmersiveChange = { isImmersive = it },
                        onPageChange = { currentImageIndex = it },
                        reelsVideos = reelsVideos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight)
                            .clip(RoundedCornerShape(20.dp)),
                    )

                    if (moment.hasHiddenLayers &&
                        moment.hiddenLayerCount > 0 &&
                        mediaItems.size == 1 &&
                        mediaItems.firstOrNull()?.type == com.moments.android.models.MediaItem.MediaType.IMAGE &&
                        currentImageIndex == 0
                    ) {
                        HiddenLayersOverlayView(
                            momentId = moment.id.orEmpty(),
                            authorId = moment.authorId,
                            hasHiddenLayers = moment.hasHiddenLayers,
                            hiddenLayerCount = moment.hiddenLayerCount,
                            isImmersive = isImmersive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    }

                    if (mediaItems.size > 1) {
                        MomentCarouselPageIndicators(
                            count = mediaItems.size,
                            currentIndex = currentImageIndex,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 20.dp),
                        )
                    }

                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 12.dp, start = 12.dp),
                    ) {
                        LocationAuthorCompactHeader(
                            moment = moment,
                            onAvatarTap = onAvatarTap,
                        )
                    }

                    ModernActionButtons(
                        moment = feedMoment,
                        isSaved = isSaved,
                        isSaveLoading = isSaveLoading,
                        commentCount = commentCount,
                        onComment = onComment,
                        onSave = onSave,
                        onContextMenu = onContextMenu,
                        isImmersive = isImmersive,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }

            MomentCaptionView(
                content = moment.content,
                onHashtagTap = onHashtagTap,
                style = MomentCaptionPresentationStyle.Detail,
                moment = moment,
                authorId = moment.authorId,
                username = moment.username,
                modifier = Modifier.padding(horizontal = FeedMomentCardLayout.captionHorizontalPadding),
            )

            if (!moment.disableComments) {
                LocationInlineCommentsSection(
                    commentCount = commentCount,
                    onComment = onComment,
                )
            }
        }
    }

    // ≡ iOS: card.blur(isAvailable ? 0 : 20).overlay { MomentUnavailableOverlay }
    Box(modifier) {
        Box(Modifier.then(if (!isAvailable) Modifier.blur(20.dp) else Modifier)) {
            if (layoutMode == LocationMomentCardLayout.Feed) {
                Box(Modifier.padding(horizontal = 15.dp)) { cardContent() }
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 15.dp)
                        .fillMaxWidth()
                        .height(availableHeight),
                ) {
                    cardContent()
                }
            }
        }
        if (!isAvailable) {
            MomentUnavailableOverlay(
                compact = false,
                cornerRadius = 24.dp,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun LocationAuthorCompactHeader(
    moment: Moment,
    onAvatarTap: (String, Boolean) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    Row(
        Modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryRingAvatarView(
            userId = moment.authorId,
            size = 32.dp,
            lineWidth = 2.2.dp,
            showBaseStroke = true,
            baseStrokeColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f),
            baseStrokeWidth = 0.9.dp,
            onTap = { hasStory -> onAvatarTap(moment.authorId, hasStory) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onAvatarTap(moment.authorId, false) },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveUsernameText(
                    userId = moment.authorId,
                    fallbackUsername = moment.username,
                    color = colors.primary,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                VerifiedBadgeView(userId = moment.authorId, size = 12.dp)
            }
            Text(
                moment.timestamp.timeAgoDisplay(),
                color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.75f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun LocationInlineCommentsSection(
    commentCount: Int,
    onComment: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val blue = Color(0xFF007AFF)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.COMMENTS,
                preset = AttachmentIconPreset.INLINE_COMMENTS_HEADER,
                tintColor = blue.copy(alpha = 0.9f),
            )
            Text(
                stringResource(R.string.location_moment_detail_comments),
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (commentCount > 0) {
                Text(
                    "($commentCount)",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(blue, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.location_moment_detail_view_all),
                color = blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(blue.copy(alpha = 0.08f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onComment,
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        if (commentCount == 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = false)
                    .border(
                        width = 0.8.dp,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.14f), blue.copy(alpha = 0.22f)),
                        ),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AttachmentIconView(
                    icon = AttachmentIcon.COMMENTS,
                    preset = AttachmentIconPreset.COMMENTS_EMPTY_STATE,
                    tintColor = blue,
                )
                Text(
                    stringResource(R.string.location_moment_detail_no_comments_title),
                    color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Text(
                    stringResource(R.string.location_moment_detail_no_comments_description),
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
                Text(
                    stringResource(R.string.location_moment_detail_comment),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(blue, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onComment,
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private enum class LocationAspectType(val exactRatio: Float) {
    Square(1f),
    Portrait(0.8f),
    Landscape(16f / 9f),
    Reels(9f / 16f),
}

private fun classifyLocationAspect(ratio: Float): LocationAspectType = when {
    ratio < 0.7f -> LocationAspectType.Reels
    ratio < 0.95f -> LocationAspectType.Portrait
    ratio < 1.2f -> LocationAspectType.Square
    else -> LocationAspectType.Landscape
}
