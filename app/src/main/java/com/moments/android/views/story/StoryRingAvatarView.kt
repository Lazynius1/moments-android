package com.moments.android.views.story

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.services.social.StoryRingCacheService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.utilities.momentsPressIcon

/**
 * Port de `StoryRingLayout` + `StoryRingAvatarView` (`Views/story/StoryRingAvatarView.swift`).
 */
object StoryRingLayout {
    val feedHeaderAvatarSize = 50.dp
    val feedHeaderLineWidth = 3.dp
    /** Espacio visible entre la foto y el aro (transparente). */
    val ringGap = 1.5.dp
    /**
     * Ancho de celda del tray (iOS hardcodea 64 en `StoryRingItem` del feed).
     * No está en el enum Swift; helper compartido para skeleton/tray.
     */
    val skeletonCellWidth = 64.dp

    fun defaultLineWidth(avatarSize: Dp): Dp {
        val scaled = avatarSize * (feedHeaderLineWidth / feedHeaderAvatarSize)
        return maxOf(2.8.dp, scaled)
    }

    fun ringStrokeDiameter(
        avatarSize: Dp = feedHeaderAvatarSize,
        lineWidth: Dp = feedHeaderLineWidth,
    ): Dp = avatarSize + ringGap * 2 + lineWidth

    fun outerFrameSize(
        avatarSize: Dp = feedHeaderAvatarSize,
        lineWidth: Dp = feedHeaderLineWidth,
    ): Dp = ringStrokeDiameter(avatarSize, lineWidth) + lineWidth + 2.dp
}

/**
 * Port 1:1 de `StoryRingAvatarView`.
 * Resuelve snapshot vía `StoryRingResolverService` (como iOS).
 *
 * Zoom: iOS aplica `.userProfileZoomSource(namespace:)` (no-op si namespace nil).
 * En Compose el stub actual siempre clippea; no se aplica aquí hasta haber Namespace real.
 */
@Composable
fun StoryRingAvatarView(
    userId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    lineWidth: Dp? = null,
    refreshTrigger: Int = 0,
    isOwnStory: Boolean? = null,
    allowOwnStories: Boolean = true,
    hapticsEnabled: Boolean = false,
    showBaseStroke: Boolean = false,
    baseStrokeColor: Color = Color.White.copy(alpha = 0.2f),
    baseStrokeWidth: Dp = 1.dp,
    onTap: ((hasStory: Boolean) -> Unit)? = null,
    onHasStoryChange: ((Boolean) -> Unit)? = null,
) {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val resolvedIsOwnStory = isOwnStory ?: (viewerId != null && viewerId == userId)
    val resolvedLineWidth = lineWidth ?: StoryRingLayout.defaultLineWidth(size)
    val ringStrokeDiameter = StoryRingLayout.ringStrokeDiameter(size, resolvedLineWidth)
    val outerSize = StoryRingLayout.outerFrameSize(size, resolvedLineWidth)

    var snapshot by remember(userId) {
        mutableStateOf(
            StoryRingSnapshot(
                hasStory = false,
                hasUnseenStory = false,
                storyCount = 0,
                storyViewedStatus = emptyList(),
                storyAudiences = emptyList(),
            ),
        )
    }

    // iOS: onAppear / onChange(userId) → resolveSnapshot() sin force
    LaunchedEffect(userId, viewerId, allowOwnStories) {
        snapshot = resolveSnapshot(
            userId = userId,
            viewerId = viewerId,
            allowOwnStories = allowOwnStories,
            forceRefresh = false,
        )
    }
    // iOS: onChange(refreshTrigger) → resolveSnapshot(forceRefresh: true)
    var skipFirstRefreshTrigger by remember { mutableStateOf(true) }
    LaunchedEffect(refreshTrigger) {
        if (skipFirstRefreshTrigger) {
            skipFirstRefreshTrigger = false
            return@LaunchedEffect
        }
        snapshot = resolveSnapshot(
            userId = userId,
            viewerId = viewerId,
            allowOwnStories = allowOwnStories,
            forceRefresh = true,
        )
    }

    LaunchedEffect(snapshot.hasStory) {
        onHasStoryChange?.invoke(snapshot.hasStory)
    }

    val interaction = remember { MutableInteractionSource() }
    // requiredSize: el frame iOS (outerFrameSize) es mayor que columnWidth 96 del perfil;
    // con size() Compose lo comprime y el gap-mask (elipse > canvas) borra el aro entero.
    var frameModifier = modifier.requiredSize(outerSize)
    if (onTap != null) {
        frameModifier = frameModifier
            .momentsPressIcon()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onTap(snapshot.hasStory) },
            )
    }

    Box(frameModifier, contentAlignment = Alignment.Center) {
        // iOS: StorySegmentedRing.mask(StoryRingLayout.ringGapMask)
        StorySegmentedRing(
            storyCount = snapshot.storyCount,
            hasStory = snapshot.hasStory,
            hasUnseenStory = snapshot.hasUnseenStory,
            storyViewedStatus = snapshot.storyViewedStatus,
            storyAudiences = snapshot.storyAudiences,
            isOwnStory = resolvedIsOwnStory,
            ringSize = ringStrokeDiameter,
            lineWidth = resolvedLineWidth,
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier.storyRingGapMask(avatarSize = size),
        )

        AsyncProfileImageView(
            userId = userId,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (showBaseStroke) {
                        Modifier.border(baseStrokeWidth, baseStrokeColor, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/**
 * ≡ iOS `StoryRingLayout.ringGapMask` / `StoryRingGapCutoutMask`.
 * Even-odd: rect − elipse → el aro no pinta el hueco transparente alrededor del avatar.
 */
fun Modifier.storyRingGapMask(avatarSize: Dp): Modifier =
    drawWithCache {
        val innerDiameter = (avatarSize + StoryRingLayout.ringGap * 2).toPx()
        val bounds = size
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, bounds))
            addOval(
                Rect(
                    offset = Offset(
                        (bounds.width - innerDiameter) / 2f,
                        (bounds.height - innerDiameter) / 2f,
                    ),
                    size = Size(innerDiameter, innerDiameter),
                ),
            )
        }
        onDrawWithContent {
            clipPath(path) {
                this@onDrawWithContent.drawContent()
            }
        }
    }

private suspend fun resolveSnapshot(
    userId: String,
    viewerId: String?,
    allowOwnStories: Boolean,
    forceRefresh: Boolean,
): StoryRingSnapshot {
    val empty = StoryRingSnapshot(
        hasStory = false,
        hasUnseenStory = false,
        storyCount = 0,
        storyViewedStatus = emptyList(),
        storyAudiences = emptyList(),
    )
    if (userId.isEmpty()) return empty
    if (viewerId.isNullOrEmpty()) return empty
    if (!allowOwnStories && viewerId == userId) return empty

    if (forceRefresh) {
        StoryRingCacheService.invalidate(viewerId, userId)
    }
    return StoryRingResolverService.resolve(
        viewerId = viewerId,
        authorId = userId,
        useCache = !forceRefresh,
    )
}
