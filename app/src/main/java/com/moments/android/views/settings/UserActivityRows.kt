package com.moments.android.views.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.Moment
import com.moments.android.services.cache.VideoThumbnailCache
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.feed.reactions.ReactionType
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Port de `UserActivityRows.swift`. Acento adaptativo local (iOS `SettingsProfileColors.accent`). */
private val ContentColorAccentDark = Color.White
private val ContentColorAccentLight = Color.Black

private fun isProtectedMoment(moment: Moment?): Boolean {
    val audience = moment?.audience?.lowercase() ?: return false
    return audience != "everyone"
}

private fun isVideoMoment(moment: Moment?): Boolean {
    if (moment == null) return false
    moment.primaryVisibleMediaItem?.let { return it.type.raw != "image" }
    return !moment.previewVideoURLString.isNullOrEmpty()
}

// MARK: - Fila de comentario

@Composable
fun ActivityCommentItemRow(
    item: ActivityCommentItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenMoment: () -> Unit,
    onOpenAuthorAvatar: (Boolean) -> Unit,
    onOpenAuthorProfile: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = Color(0xFF2563EB)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = 0.05f))
            .then(if (isSelectionMode) Modifier.clickable { onToggleSelection() } else Modifier)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.then(
                if (isSelectionMode) Modifier else Modifier.clickable { onOpenMoment() },
            ),
        ) {
            ActivityCommentMomentPreview(moment = item.moment, canView = item.canView, size = 84.dp)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.moment?.username
                        ?: stringResource(R.string.user_activity_status_unknown),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isSelectionMode) Modifier else Modifier.clickable { onOpenAuthorProfile() },
                )

                if (!item.canView) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(10.dp),
                    )
                }

                Box(Modifier.weight(1f))

                StoryRingAvatarView(
                    userId = item.authorId,
                    size = 30.dp,
                    lineWidth = 2.2.dp,
                    onTap = if (isSelectionMode) null else { hasStory -> onOpenAuthorAvatar(hasStory) },
                )
            }

            Text(
                text = item.moment?.content?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.user_activity_comments_moment_no_content),
                fontSize = 12.sp,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(R.string.user_activity_comments_your_comment),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary.copy(alpha = 0.82f),
            )

            Text(
                text = item.commentText.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.user_activity_comments_empty_comment),
                fontSize = 12.sp,
                color = primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = item.commentedAt.timeAgoDisplay(),
                fontSize = 11.sp,
                color = secondary.copy(alpha = 0.85f),
            )
        }

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) selectionColor else secondary.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .clickable { onToggleSelection() },
            )
        }
    }
}

// MARK: - Preview de moment en la fila de comentario

@Composable
fun ActivityCommentMomentPreview(
    moment: Moment?,
    canView: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    ScreenshotProtectedView(isProtected = isProtectedMoment(moment)) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MomentPreviewContent(moment = moment, size = size)

            if (moment?.isCarouselMoment == true && canView) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(R.drawable.carousel_post_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(15.dp),
                    )
                }
            }

            if (!canView) RestrictedOverlay(compact = true)
        }
    }
}

// MARK: - Fila de evento (follow / visita / echo / sticker)

@Composable
fun ActivityEventRow(
    item: ActivityEventItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenTargetProfile: () -> Unit,
    onRowTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val kind = item.kind?.lowercase().orEmpty()
    when (kind) {
        "echo" -> EchoEventCard(item, modifier)
        "visit", "follower" -> VisitFollowerEventCard(item, onOpenTargetProfile, modifier)
        else -> StandardEventRow(item, isSelectionMode, isSelected, onOpenTargetProfile, onRowTap, modifier)
    }
}

@Composable
private fun StandardEventRow(
    item: ActivityEventItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenTargetProfile: () -> Unit,
    onRowTap: (() -> Unit)?,
    modifier: Modifier,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val hasContext = !item.targetUsername.isNullOrBlank() || !item.contextText.isNullOrEmpty()
    val showThumbnail = !item.thumbnailUrl.isNullOrEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onRowTap != null) Modifier.clickable { onRowTap() } else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventAvatar(item)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val actionText = item.actionText
            if (!actionText.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(actionText, fontSize = 12.sp, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (item.subtitle.isNotEmpty()) {
                Text(item.subtitle, fontSize = 13.sp, color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.timestamp.timeAgoDisplay(), fontSize = 11.sp, color = secondary.copy(alpha = 0.85f))
                if (hasContext) {
                    Text("•", fontSize = 10.sp, color = secondary.copy(alpha = 0.7f))
                    val username = item.targetUsername?.takeIf { it.isNotBlank() }
                    if (username != null) {
                        Text(eventContextPrefix(kind = item.kind), fontSize = 11.sp, color = secondary.copy(alpha = 0.85f))
                        Text(
                            username,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            maxLines = 1,
                            modifier = Modifier.clickable { onOpenTargetProfile() },
                        )
                    } else {
                        item.contextText?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, fontSize = 11.sp, color = secondary.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                }
            }
        }

        if (showThumbnail) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(end = 4.dp).size(44.dp).clip(RoundedCornerShape(8.dp)),
            )
        }

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF2563EB) else secondary.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun VisitFollowerEventCard(
    item: ActivityEventItem,
    onOpenTargetProfile: () -> Unit,
    modifier: Modifier,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val kind = item.kind?.lowercase().orEmpty()

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventAvatar(item)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("•", fontSize = 10.sp, color = secondary.copy(alpha = 0.7f))
                Text(item.timestamp.timeAgoDisplay(), fontSize = 11.sp, color = secondary)
            }
            Text(
                text = when (kind) {
                    "visit" -> stringResource(R.string.user_activity_event_visit_clean)
                    "follower" -> stringResource(R.string.user_activity_event_follow_clean)
                    else -> item.subtitle
                },
                fontSize = 13.sp,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.actionText?.takeIf { it.isNotEmpty() }?.let { actionText ->
            Text(
                text = actionText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.06f))
                    .clickable { onOpenTargetProfile() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EchoEventCard(item: ActivityEventItem, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val statusColor = when (item.echoStatusRaw?.lowercase()) {
        "pending" -> Color(0xFFFF9500)
        "active" -> Color(0xFF34C759)
        "completed" -> Color(0xFFAF52DE)
        else -> Color.Gray
    }
    val statusText = when (item.echoStatusRaw?.lowercase()) {
        "pending" -> stringResource(R.string.echo_status_pending)
        "active" -> stringResource(R.string.echo_status_active)
        "completed" -> stringResource(R.string.echo_status_completed)
        else -> stringResource(R.string.echo_status_expired)
    }
    val count = (item.echoParticipantsCount ?: 0).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumbUrl = item.thumbnailUrl
            if (!thumbUrl.isNullOrEmpty()) {
                AsyncImage(thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                EchoesIconView(
                    size = EchoesIconMetrics.rowThumbnail,
                    gradient = Brush.horizontalGradient(listOf(Color(0xFFFF9500), Color(0xFFAF52DE))),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val participantsText = if (count == 1) {
                    stringResource(R.string.echo_participants_singular, count)
                } else {
                    stringResource(R.string.echo_participants_plural, count)
                }
                Text(participantsText, fontSize = 12.sp, color = secondary)
                Text("•", color = secondary)
                Text(item.timestamp.timeAgoDisplay(), fontSize = 12.sp, color = secondary)
            }
        }

        Text(
            text = statusText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            modifier = Modifier.clip(CircleShape).background(statusColor.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EventAvatar(item: ActivityEventItem) {
    val path = item.actorProfileImagePath
    val userId = item.actorId
    when {
        !path.isNullOrEmpty() -> AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(CircleShape),
        )
        !userId.isNullOrEmpty() -> AsyncProfileImageView(
            userId = userId,
            modifier = Modifier.size(34.dp).clip(CircleShape),
        )
        else -> Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)),
        )
    }
}

private fun eventContextPrefix(kind: String?): String = ""

// MARK: - Tarjeta de reacción / tag / archivado (cuadrada)

@Composable
fun ActivityReactionMomentCard(
    item: ActivityReactionItem,
    size: Dp,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    overlayBadge: ActivityOverlayBadgeStyle = ActivityOverlayBadgeStyle.NONE,
    modifier: Modifier = Modifier,
) {
    ScreenshotProtectedView(isProtected = isProtectedMoment(item.moment)) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (item.canView) Modifier else Modifier.blur(16.dp)),
            ) {
                MomentPreviewContent(moment = item.moment, size = size)
            }

            if (!item.canView) RestrictedOverlay(compact = false)

            when (overlayBadge) {
                ActivityOverlayBadgeStyle.REACTION_DISCREET -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    DiscreetReactionBadge(item.reactionType)
                }
                ActivityOverlayBadgeStyle.AUDIENCE -> item.moment?.let { moment ->
                    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopStart) {
                        AudienceIconView(
                            audience = resolvedAudience(moment),
                            size = 18.dp,
                        )
                    }
                }
                ActivityOverlayBadgeStyle.NONE -> if (isVideoMoment(item.moment) && item.canView) {
                    ActivityThumbnailVideoPlayIndicator()
                }
            }

            if (isSelectionMode) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.padding(6.dp).size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscreetReactionBadge(reactionType: String) {
    Box(Modifier.padding(6.dp)) {
        if (reactionType.lowercase() == "tagged") {
            AttachmentIconView(icon = AttachmentIcon.TAGGED, size = 14.dp, tintColor = Color.White)
        } else {
            Text(reactionStyleIcon(reactionType), fontSize = 14.sp)
        }
    }
}

private fun reactionStyleIcon(rawValue: String): String {
    if (rawValue.lowercase() == "tagged") return "🏷️"
    ReactionType.fromRaw(rawValue)?.let { return it.icon }
    return "✨"
}

private fun resolvedAudience(moment: Moment): ContentAudience {
    val audience = ContentAudience.fromAudienceValue(moment.audience)
    return if (moment.customListId != null && audience == ContentAudience.CUSTOM) {
        ContentAudience.CUSTOM_LIST
    } else {
        audience
    }
}

// MARK: - Tarjeta portrait 9:16 (reels)

@Composable
fun ActivityPortraitMomentCard(moment: Moment, modifier: Modifier = Modifier) {
    ScreenshotProtectedView(isProtected = isProtectedMoment(moment)) {
        Box(
            modifier = modifier.aspectRatio(9f / 16f),
            contentAlignment = Alignment.Center,
        ) {
            MomentPreviewContent(moment = moment, size = null)
            if (isVideoMoment(moment)) ActivityThumbnailVideoPlayIndicator()
        }
    }
}

// MARK: - Tarjeta de story borrada (9:16)

@Composable
fun ActivityDeletedStoryCard(
    item: ActivityDeletedStoryItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val story = item.story
    val isVideo = story.mediaItem.type.raw == "video"
    val previewUrl = (if (isVideo) story.mediaItem.thumbnailUrl else null)
        ?.trim()?.takeIf { it.isNotEmpty() }
        ?: story.mediaItem.url.trim().takeIf { it.isNotEmpty() }

    Box(
        modifier = modifier.aspectRatio(9f / 16f),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (previewUrl != null) {
            AsyncImage(previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            StoryPlaceholder(isVideo)
        }

        Box(Modifier.fillMaxSize().padding(7.dp), contentAlignment = Alignment.TopStart) {
            StoryDateBadge(story.timestamp)
        }

        if (isVideo && story.duration > 0) {
            Box(Modifier.fillMaxSize().padding(7.dp), contentAlignment = Alignment.BottomEnd) {
                Text(
                    text = formatStoryDuration(story.duration),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun ActivityThumbnailVideoPlayIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.BottomEnd) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(5.dp),
        )
    }
}

// MARK: - Helpers de preview compartidos

/**
 * Resuelve la preview de un moment igual que iOS: MediaItem primario visible → previewImageURLString
 * → previewVideoURLString (con thumbnail generado y cacheado) → placeholder. `size == null` deja que
 * el contenedor imponga el tamaño (tarjetas con aspect ratio).
 */
@Composable
private fun MomentPreviewContent(moment: Moment?, size: Dp?) {
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fillModifier = if (size != null) Modifier.size(size) else Modifier.fillMaxSize()

    if (moment == null) {
        Box(fillModifier.background(placeholderColor), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Photo, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
        return
    }

    val media = moment.primaryVisibleMediaItem
    val imageUrl: String?
    val videoUrl: String?
    val thumbnailUrl: String?
    when {
        media != null && media.type.raw == "image" -> {
            imageUrl = media.url; videoUrl = null; thumbnailUrl = null
        }
        media != null -> {
            imageUrl = null; videoUrl = media.url; thumbnailUrl = media.thumbnailUrl ?: moment.thumbnailUrl
        }
        !moment.previewImageURLString.isNullOrEmpty() && moment.previewVideoURLString.isNullOrEmpty() -> {
            imageUrl = moment.previewImageURLString; videoUrl = null; thumbnailUrl = null
        }
        !moment.previewVideoURLString.isNullOrEmpty() -> {
            imageUrl = null; videoUrl = moment.previewVideoURLString; thumbnailUrl = moment.previewImageURLString ?: moment.thumbnailUrl
        }
        else -> {
            imageUrl = null; videoUrl = null; thumbnailUrl = null
        }
    }

    when {
        imageUrl != null -> AsyncImage(imageUrl, null, fillModifier, contentScale = ContentScale.Crop)
        videoUrl != null -> VideoThumb(videoUrl, thumbnailUrl, fillModifier, placeholderColor)
        else -> Box(fillModifier.background(placeholderColor), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Videocam, null, tint = Color.Gray, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun VideoThumb(videoUrl: String, thumbnailUrl: String?, modifier: Modifier, placeholderColor: Color) {
    if (!thumbnailUrl.isNullOrEmpty()) {
        AsyncImage(thumbnailUrl, null, modifier, contentScale = ContentScale.Crop)
        return
    }
    var generated by remember(videoUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(videoUrl) {
        if (generated == null) generated = VideoThumbnailCache.thumbnail(videoUrl)
    }
    generated?.let {
        Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } ?: Box(modifier.background(placeholderColor))
}

@Composable
private fun RestrictedOverlay(compact: Boolean) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
            Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(if (compact) 12.dp else 13.dp))
            Text(
                text = stringResource(R.string.profile_saved_restricted_title),
                fontSize = (if (compact) 8 else 10).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = stringResource(R.string.profile_saved_restricted_subtitle),
                fontSize = (if (compact) 7 else 9).sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun StoryPlaceholder(isVideo: Boolean) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (isVideo) Icons.Filled.PlayCircleFilled else Icons.Filled.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun StoryDateBadge(date: Date) {
    Column(
        Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White).padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(SimpleDateFormat("d", Locale.getDefault()).format(date), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(SimpleDateFormat("MMM", Locale.getDefault()).format(date).lowercase(), color = Color.Black.copy(alpha = 0.75f), fontSize = 10.sp)
    }
}

private fun formatStoryDuration(duration: Double): String {
    val total = duration.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
