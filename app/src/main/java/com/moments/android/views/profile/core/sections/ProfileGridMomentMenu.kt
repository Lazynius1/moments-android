package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.feed.reactions.ReactionType
import com.moments.android.views.feed.video.LiveVideoTimeDisplayMode
import com.moments.android.views.feed.video.LiveVideoTimeLabel
import com.moments.android.views.feed.video.ModernVideoPlayer
import com.moments.android.views.feed.video.VideoPlaybackActivationMode
import com.moments.android.views.feed.video.VideoPlaybackChromeStyle
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * Port de `ProfileGridMomentMenu.swift`:
 * gesture overlay, ProfileGridHeroCard, ProfileGridVisitorActionBar.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.profileMomentThumbnailGesture(onTap: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier =
    combinedClickable(
        onClick = onTap,
        onLongClick = onLongPress?.let { press ->
            {
                // ≡ iOS mediumImpact al began del long-press
                HapticManager.shared.mediumImpact()
                press()
            }
        },
    )

@Composable
fun ProfileGridHeroCard(
    moment: Moment,
    width: Dp,
    showsChrome: Boolean = true,
    showsAudience: Boolean = true,
    chromeOpacity: Float = 1f,
    onOpenMoment: () -> Unit,
) {
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val media = moment.primaryVisibleMediaItem
    val widthPx = with(density) { width.toPx() }
    val mediaHeight = with(density) {
        ProfileGridHeroLayout.mediaHeight(widthPx, moment.aspectRatio).toDp()
    }
    val footerHeight = ProfileGridHeroLayout.peekFooterHeightDp.dp
    val corner = ProfileGridHeroLayout.peekCornerRadius.dp
    val primaryText = if (dark) Color.White else Color.Black
    val secondaryText = if (dark) Color.White.copy(0.72f) else Color.Black.copy(0.58f)
    val footerBg = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val audience = run {
        val base = ContentAudience.fromAudienceValue(moment.audience)
        if (moment.customListId != null && base == ContentAudience.CUSTOM) {
            ContentAudience.CUSTOM_LIST
        } else {
            base
        }
    }
    val heroAspect = ProfileGridHeroLayout.clampedPeekWidthOverHeight(moment.aspectRatio)

    Column(
        Modifier
            .width(width)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(corner),
                ambientColor = Color.Black.copy(if (dark) 0.32f else 0.14f),
                spotColor = Color.Black.copy(if (dark) 0.32f else 0.14f),
            )
            .clip(RoundedCornerShape(corner))
            .profileMomentThumbnailGesture(onOpenMoment),
    ) {
        Box(Modifier.fillMaxWidth().height(mediaHeight)) {
            ProfileGridHeroMedia(
                moment = moment,
                media = media,
                aspectRatio = heroAspect,
                secondaryText = secondaryText,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(
                        with(density) {
                            min(mediaHeight.toPx() * 0.12f, 36.dp.toPx()).toDp()
                        },
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(if (dark) 0.18f else 0.08f),
                            ),
                        ),
                    ),
            )
            if (media?.type == MediaItem.MediaType.VIDEO) {
                ProfileGridHeroVideoDurationBadge(
                    moment = moment,
                    media = media,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp),
                )
            }
        }
        if (showsChrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
                    .background(footerBg)
                    .graphicsLayer { alpha = chromeOpacity }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncProfileImageView(
                    userId = moment.authorId,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        moment.username,
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    moment.location?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            color = secondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (showsAudience) {
                    AudienceIconView(
                        audience = audience,
                        size = AudienceIconMetrics.activityGridThumbnail,
                        isDark = dark,
                    )
                }
            }
        }
    }
}

/** ≡ `heroMedia` / videoPlaceholder / textFallback. */
@Composable
private fun ProfileGridHeroMedia(
    moment: Moment,
    media: MediaItem?,
    aspectRatio: Float,
    secondaryText: Color,
) {
    when {
        media != null && media.url.isNotEmpty() && media.type == MediaItem.MediaType.VIDEO -> {
            ModernVideoPlayer(
                url = media.url,
                videoId = GlobalVideoManager.profileVideoConsumerId(moment),
                modifier = Modifier.fillMaxSize(),
                aspectRatio = aspectRatio,
                hideMuteButton = true,
                chromeStyle = VideoPlaybackChromeStyle.SocialReels,
                allowsPauseInteraction = false,
                posterUrl = media.thumbnailUrl?.takeIf { it.isNotEmpty() }
                    ?: moment.previewImageURLString,
                mediaItem = media,
                moment = moment,
                activationMode = VideoPlaybackActivationMode.AlwaysWhenVisible,
                consumesDetailHandoff = false,
            )
        }
        media != null && media.url.isNotEmpty() -> {
            AsyncImage(
                model = media.url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.imagePath.isNullOrBlank() -> {
            AsyncImage(
                model = moment.imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.previewImageURLString.isNullOrBlank() -> {
            AsyncImage(
                model = moment.previewImageURLString,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        media?.type == MediaItem.MediaType.VIDEO -> {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(36.dp))
            }
        }
        else -> {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.06f)).padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    moment.content,
                    color = secondaryText,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    maxLines = 6,
                )
            }
        }
    }
}

/** ≡ `videoDurationBadge` — play.fill + LiveVideoTimeLabel.inline en cápsula. */
@Composable
private fun ProfileGridHeroVideoDurationBadge(
    moment: Moment,
    media: MediaItem,
    modifier: Modifier = Modifier,
) {
    val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
    val duration = media.videoDuration ?: moment.videoDuration
    Row(
        modifier
            .background(Color.Black.copy(0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp),
        )
        LiveVideoTimeLabel(
            consumerId = consumerId,
            totalDuration = duration,
            displayMode = LiveVideoTimeDisplayMode.Inline,
        )
    }
}

@Composable
fun ProfileGridVisitorActionBar(
    moment: Moment,
    canShare: Boolean,
    onComment: () -> Unit,
    onShare: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    // Reacciones fijas de momento (orden enum), ScrollView L→R — sin usage tracker UI.
    val reactions = ReactionType.allCases
    val primary = if (dark) Color.White else Color.Black
    val muted = if (dark) Color.White.copy(0.35f) else Color.Black.copy(0.28f)
    val firestore = remember { FirestoreService() }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = true)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reactions.forEach { reaction ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable {
                            HapticManager.shared.lightImpact()
                            val momentId = moment.id ?: return@clickable
                            val userId = uid.takeIf { it.isNotEmpty() } ?: return@clickable
                            scope.launch {
                                runCatching {
                                    firestore.addReaction(
                                        momentId = momentId,
                                        reaction = reaction.rawValue,
                                        userId = userId,
                                        authorId = moment.authorId,
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(reaction.icon, fontSize = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }

        if (!moment.disableComments) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = stringResource(R.string.comments_title),
                tint = primary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onComment)
                    .padding(8.dp),
            )
        }
        Icon(
            Icons.Default.Send,
            contentDescription = stringResource(R.string.context_menu_share_moment),
            tint = if (canShare) primary else muted,
            modifier = Modifier
                .size(36.dp)
                .clickable(enabled = canShare, onClick = onShare)
                .padding(8.dp),
        )
    }
}
