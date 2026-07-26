package com.moments.android.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.notifications.core.NotificationRowMetrics

/**
 * Port de NotificationRowComponents.swift —
 * [NotificationLeadingAvatarView] + [NotificationStoryThumbnailView].
 *
 * [NotificationMomentThumbnail] es helper Android usado por Previews/Trailing
 * (en iOS el thumb de momento va inline en EnhancedNotificationRow+Trailing).
 */

/** ≡ NotificationLeadingAvatarView — uno grande o dos solapados (atrás izq, delante der). */
@Composable
fun NotificationLeadingAvatarView(
    senderIds: List<String>,
    isDark: Boolean,
    onPrimaryTap: () -> Unit,
    onSecondaryTap: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER")
    profilePaths: Map<String, String?> = emptyMap(),
) {
    val ringStroke = if (isDark) Color.Black else Color.White
    val frontId = senderIds.firstOrNull() ?: return
    val backId = senderIds.getOrNull(1)

    if (backId != null) {
        val size = NotificationRowMetrics.STACKED_AVATAR_SIZE_DP.dp
        val overlap = NotificationRowMetrics.stackedOverlapDp.dp
        Box(
            modifier = Modifier.size(
                NotificationRowMetrics.stackedRowWidthDp.dp,
                NotificationRowMetrics.STACKED_AVATAR_SIZE_DP.dp,
            ),
        ) {
            // Atrás (izquierda) — tap secundario
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(size)
                    .zIndex(0f)
                    .clip(CircleShape)
                    .clickable { onSecondaryTap?.invoke() },
            ) {
                AsyncProfileImageView(userId = backId, modifier = Modifier.matchParentSize())
            }
            // Delante (derecha) — overlap ≡ HStack(spacing: -stackedOverlap)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = size - overlap)
                    .size(size)
                    .zIndex(1f)
                    .clip(CircleShape)
                    .border(2.dp, ringStroke, CircleShape)
                    .clickable(onClick = onPrimaryTap),
            ) {
                AsyncProfileImageView(userId = frontId, modifier = Modifier.matchParentSize())
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(NotificationRowMetrics.AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
                .border(2.dp, ringStroke, CircleShape)
                .clickable(onClick = onPrimaryTap),
        ) {
            AsyncProfileImageView(userId = frontId, modifier = Modifier.matchParentSize())
        }
    }
}

/** ≡ NotificationStoryThumbnailView */
@Composable
fun NotificationStoryThumbnailView(
    imagePath: String?,
    reaction: String?,
    isDark: Boolean,
    loadFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val corner = RoundedCornerShape(NotificationRowMetrics.STORY_THUMB_CORNER_RADIUS_DP.dp)
    val stroke = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.1f)
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(
                    NotificationRowMetrics.STORY_THUMB_WIDTH_DP.dp,
                    NotificationRowMetrics.STORY_THUMB_HEIGHT_DP.dp,
                )
                .clip(corner)
                .border(0.5.dp, stroke, corner),
            contentAlignment = Alignment.Center,
        ) {
            val showImage = !imagePath.isNullOrBlank() && !loadFailed
            if (showImage) {
                AsyncImage(
                    model = imagePath,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                StoryThumbPlaceholder(isDark = isDark, corner = corner)
            }
        }
        if (!reaction.isNullOrBlank()) {
            Text(
                text = reaction,
                fontSize = 15.sp,
                modifier = Modifier
                    .offset(x = 3.dp, y = 3.dp)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                        RoundedCornerShape(5.dp),
                    )
                    .padding(3.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.StoryThumbPlaceholder(isDark: Boolean, corner: RoundedCornerShape) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f),
                corner,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Photo,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f),
        )
    }
}

/** Alias usado por Previews — ≡ NotificationStoryThumbnailView sin reacción. */
@Composable
fun NotificationStoryThumbnail(
    imageUrl: String?,
    @Suppress("UNUSED_PARAMETER") isLoading: Boolean,
    isDark: Boolean,
    reaction: String? = null,
    modifier: Modifier = Modifier,
) {
    NotificationStoryThumbnailView(
        imagePath = imageUrl,
        reaction = reaction,
        isDark = isDark,
        loadFailed = false,
        modifier = modifier,
    )
}

/**
 * Thumb cuadrado 44×44 para momentos (inline en iOS Trailing; helper aquí para Previews).
 */
@Composable
fun NotificationMomentThumbnail(
    imageUrl: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val corner = RoundedCornerShape(8.dp)
    val stroke = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(corner)
            .border(1.dp, stroke, corner)
            .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Photo,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
            )
        }
    }
}
