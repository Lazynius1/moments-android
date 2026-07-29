package com.moments.android.views.profile.core.sections

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.maps.toFeedMomentForMap
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.sharing.ModernShareBottomSheet
import com.moments.android.views.profile.core.canAdjustGridPreview
import com.moments.android.views.settings.hasVideoMedia
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port de `ProfileGridHeroTransition.swift`.
 * Flujo vivo: idle ↔ menuPeek (flying hero + menú). Expand/retract latentes como en Swift.
 */

val LocalProfileGridHeroCoordinator =
    staticCompositionLocalOf<ProfileGridHeroTransitionCoordinator?> { null }

enum class ProfileMomentDetailEntryKind { DIRECT, HERO }

data class ProfileMomentDetailRoute(
    val moments: List<Moment>,
    val initialIndex: Int,
    val initialMomentId: String?,
    val entryKind: ProfileMomentDetailEntryKind = ProfileMomentDetailEntryKind.DIRECT,
)

data class ProfileGridMomentMenuSelection(val moment: Moment, val index: Int)

enum class ProfileGridHeroMenuKind { OWNER, VISITOR }

sealed interface ProfileGridHeroPhase {
    data object Idle : ProfileGridHeroPhase
    data class MenuPeek(val selection: ProfileGridMomentMenuSelection) : ProfileGridHeroPhase
    data class Expanding(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase
    data class Retracting(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase
    data class Detail(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase
}

data class ProfileGridHeroPresentation(
    val frame: Rect,
    val cornerRadius: Float,
    val scale: Float,
    val opacity: Float,
    val shadowRadius: Float,
    val shadowOpacity: Float,
)

object ProfileGridHeroMotion {
    fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    fun easeOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    fun remap(value: Float, start: Float, end: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        return ((value - start) / (end - start)).coerceIn(0f, 1f)
    }
}

object ProfileGridHeroLayout {
    const val maxCardWidthDp = 350f
    val peekCornerRadius = FeedMomentCardLayout.mediaCornerRadius.value
    val detailCornerRadius = FeedMomentCardLayout.mediaCornerRadius.value
    val thumbnailCornerRadius = FeedMomentCardLayout.mediaCornerRadius.value
    const val horizontalPaddingDp = 16f
    const val detailHeaderBlockHeightDp = 80f
    const val menuSpacingDp = 14f
    const val peekFooterHeightDp = 56f
    const val menuWidthDp = 240f
    const val menuRowHeightDp = 46f
    const val pinConfirmHeightDp = 220f
    const val peekMinWidthOverHeight = 3f / 4f
    const val peekMaxWidthOverHeight = 16f / 9f
    const val peekLiftMs = 460
    const val peekDismissMs = 310
    const val retractPeekSplit = 0.34f
    const val retractFadeStart = 0.74f

    /** ≡ Animation.smooth — aproximación Compose. */
    val smoothEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    fun liftedSourceThumbnailOpacity(peekProgress: Float): Float =
        ProfileGridHeroMotion.smoothstep(1f - min(1f, peekProgress / 0.14f))

    fun peekHeroFrame(origin: Rect, destination: Rect, progress: Float): Rect {
        val growT = ProfileGridHeroMotion.smoothstep(min(1f, progress / 0.50f))
        val moveT = ProfileGridHeroMotion.smoothstep(
            ProfileGridHeroMotion.remap(progress, start = 0.20f, end = 0.92f),
        )
        val grownWidth = origin.width + (destination.width - origin.width) * growT
        val grownHeight = origin.height + (destination.height - origin.height) * growT
        val grownX = origin.center.x - grownWidth / 2f
        val grownY = origin.center.y - grownHeight / 2f
        return Rect(
            offset = Offset(
                grownX + (destination.left - grownX) * moveT,
                grownY + (destination.top - grownY) * moveT,
            ),
            size = Size(
                grownWidth + (destination.width - grownWidth) * moveT,
                grownHeight + (destination.height - grownHeight) * moveT,
            ),
        )
    }

    fun peekHeroCornerRadius(progress: Float): Float {
        val t = ProfileGridHeroMotion.smoothstep(
            ProfileGridHeroMotion.remap(progress, start = 0.08f, end = 0.62f),
        )
        return lerp(thumbnailCornerRadius, peekCornerRadius, t)
    }

    fun sourceKey(moment: Moment, index: Int): String = moment.id ?: "profile-grid-$index"

    fun parsedAspectRatio(value: String?): Float {
        val parts = value?.split(':', '/') ?: return 1f
        val lhs = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
        val rhs = parts.getOrNull(1)?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f
        return lhs / rhs
    }

    fun clampedPeekWidthOverHeight(aspectRatio: String?): Float =
        parsedAspectRatio(aspectRatio).coerceIn(peekMinWidthOverHeight, peekMaxWidthOverHeight)

    fun mediaHeight(widthPx: Float, aspectRatio: String?): Float =
        widthPx / clampedPeekWidthOverHeight(aspectRatio)

    fun peekCardHeight(widthPx: Float, aspectRatio: String?, density: Float): Float =
        mediaHeight(widthPx, aspectRatio) + peekFooterHeightDp * density

    /** iOS points → px: `min(container - 32, 350)`. */
    fun cardWidth(containerWidthPx: Float, density: Float): Float =
        min(containerWidthPx - 32f * density, maxCardWidthDp * density)

    fun aspect(value: String?): Float = clampedPeekWidthOverHeight(value)

    fun peekCardFrame(
        containerSize: Size,
        safeTop: Float,
        safeBottom: Float,
        moment: Moment,
        showPinConfirm: Boolean,
        menuBlockHeightPx: Float,
        density: Float,
    ): Rect {
        val width = cardWidth(containerSize.width, density)
        val height = peekCardHeight(width, moment.aspectRatio, density)
        val x = (containerSize.width - width) / 2f
        val menuHeight = if (showPinConfirm) pinConfirmHeightDp * density else menuBlockHeightPx
        val stackHeight = height + menuSpacingDp * density + menuHeight
        val minCenter = safeTop + 20f * density + stackHeight / 2f
        val maxCenter = containerSize.height - safeBottom - 20f * density - stackHeight / 2f
        val preferred = containerSize.height / 2f
        val centerY = when {
            minCenter > maxCenter -> containerSize.height / 2f
            else -> preferred.coerceIn(minCenter, maxCenter)
        }
        val cardTopY = centerY - stackHeight / 2f
        return Rect(x, cardTopY, x + width, cardTopY + height)
    }

    fun detailMediaFrame(
        screenSize: Size,
        safeTop: Float,
        moment: Moment,
        density: Float,
    ): Rect {
        val width = screenSize.width - horizontalPaddingDp * density * 2f
        val ratio = parsedAspectRatio(moment.aspectRatio)
        val calculatedHeight = width / max(ratio, 0.55f)
        val maxHeight = when {
            ratio < 0.85f -> 550f * density
            ratio > 1.2f -> 300f * density
            else -> 450f * density
        }
        val height = min(calculatedHeight, maxHeight)
        val x = horizontalPaddingDp * density
        val y = safeTop + detailHeaderBlockHeightDp * density
        return Rect(x, y, x + width, y + height)
    }

    fun lerp(a: Float, b: Float, t: Float): Float {
        val clamped = ProfileGridHeroMotion.smoothstep(t.coerceIn(0f, 1f))
        return a + (b - a) * clamped
    }

    fun lerp(a: Rect, b: Rect, t: Float): Rect {
        val x = ProfileGridHeroMotion.smoothstep(t.coerceIn(0f, 1f))
        return Rect(
            a.left + (b.left - a.left) * x,
            a.top + (b.top - a.top) * x,
            a.right + (b.right - a.right) * x,
            a.bottom + (b.bottom - a.bottom) * x,
        )
    }

    fun fallbackThumbnailFrame(containerSize: Size, density: Float): Rect {
        val side = min(containerSize.width / 3f - 8f * density, 140f * density)
        val left = (containerSize.width - side) / 2f
        val top = containerSize.height * 0.42f
        return Rect(left, top, left + side, top + side)
    }
}

class ProfileGridHeroTransitionCoordinator {
    var phase by mutableStateOf<ProfileGridHeroPhase>(ProfileGridHeroPhase.Idle); private set
    var sourceFrame by mutableStateOf(Rect.Zero)
    var peekProgress by mutableStateOf(0f)
    var peekProgressTarget by mutableStateOf(0f)
    var expandProgress by mutableStateOf(0f)
    var collapseProgress by mutableStateOf(0f)
    var menuOpacity by mutableStateOf(0f)
    var menuOpacityTarget by mutableStateOf(0f)
    var detailContentOpacity by mutableStateOf(0f)
    var scrimOpacity by mutableStateOf(0f)
    var scrimOpacityTarget by mutableStateOf(0f)
    var showPinConfirm by mutableStateOf(false)
    var toastMessage by mutableStateOf<String?>(null)
    var isDismissingInteractively by mutableStateOf(false)
    var menuKind by mutableStateOf(ProfileGridHeroMenuKind.OWNER); private set
    /** true = lift 460ms, false = dismiss 310ms. */
    var peekAnimatingOpen by mutableStateOf(true); private set

    var onEdit: ((Moment) -> Unit)? = null
    var onDelete: ((Moment) -> Unit)? = null
    var onArchive: ((Moment) -> Unit)? = null
    var onAdjustPreview: ((Moment) -> Unit)? = null
    var onPin: ((Moment, Boolean, Boolean) -> Unit)? = null
    var openZoomDetail: ((ProfileMomentZoomDestination) -> Unit)? = null
    var clearZoomNavigation: (() -> Unit)? = null
    var zoomMomentsSnapshot: List<Moment> = emptyList()

    private val thumbnailFrames = mutableMapOf<String, Rect>()

    val menuSelection: ProfileGridMomentMenuSelection?
        get() = (phase as? ProfileGridHeroPhase.MenuPeek)?.selection

    val activeMoment: Moment?
        get() = menuSelection?.moment ?: when (val p = phase) {
            is ProfileGridHeroPhase.Expanding -> p.route.moments.getOrNull(p.route.initialIndex)
            is ProfileGridHeroPhase.Retracting -> p.route.moments.getOrNull(p.route.initialIndex)
            is ProfileGridHeroPhase.Detail -> p.route.moments.getOrNull(p.route.initialIndex)
            else -> null
        }

    val isInteractive: Boolean get() = phase !is ProfileGridHeroPhase.Idle

    fun ingestThumbnailFrames(frames: Map<String, Rect>) {
        thumbnailFrames.putAll(frames)
    }

    fun isLiftedGridSource(moment: Moment, gridIndex: Int): Boolean {
        val selection = menuSelection ?: return false
        return ProfileGridHeroLayout.sourceKey(moment, gridIndex) ==
            ProfileGridHeroLayout.sourceKey(selection.moment, selection.index)
    }

    fun liftedGridSourceContentOpacity(moment: Moment, gridIndex: Int): Float {
        if (!isLiftedGridSource(moment, gridIndex)) return 1f
        return ProfileGridHeroLayout.liftedSourceThumbnailOpacity(peekProgress)
    }

    /** Alturas iOS en points → px vía [density]. */
    fun menuBlockHeight(moment: Moment, density: Float): Float {
        if (showPinConfirm) return ProfileGridHeroLayout.pinConfirmHeightDp * density
        return when (menuKind) {
            ProfileGridHeroMenuKind.VISITOR -> 52f * density
            ProfileGridHeroMenuKind.OWNER -> {
                val rows = if (moment.canAdjustGridPreview) 5 else 4
                ProfileGridHeroLayout.menuRowHeightDp * rows * density
            }
        }
    }

    fun openMenu(moment: Moment, index: Int, kind: ProfileGridHeroMenuKind = ProfileGridHeroMenuKind.OWNER) {
        showPinConfirm = false
        toastMessage = null
        menuKind = kind
        val key = ProfileGridHeroLayout.sourceKey(moment, index)
        sourceFrame = thumbnailFrames[key] ?: Rect.Zero
        expandProgress = 0f
        collapseProgress = 0f
        detailContentOpacity = 0f
        phase = ProfileGridHeroPhase.MenuPeek(ProfileGridMomentMenuSelection(moment, index))
        GlobalVideoManager.pauseAllVideos()
        GlobalVideoManager.clearProfilePlaybackHandoffState()
        peekAnimatingOpen = true
        peekProgressTarget = 1f
        scrimOpacityTarget = 1f
        menuOpacityTarget = 1f
    }

    fun dismissMenu() {
        if (phase !is ProfileGridHeroPhase.MenuPeek) return
        val selection = menuSelection
        showPinConfirm = false
        selection?.moment?.let { pauseProfileHeroVideo(it) }
        peekAnimatingOpen = false
        peekProgressTarget = 0f
        scrimOpacityTarget = 0f
        menuOpacityTarget = 0f
    }

    fun finishDismissIfNeeded() {
        if (phase is ProfileGridHeroPhase.MenuPeek && peekProgressTarget == 0f && peekProgress < 0.02f) {
            phase = ProfileGridHeroPhase.Idle
            GlobalVideoManager.clearProfilePlaybackHandoffState()
        }
    }

    fun openDirectDetail(
        moments: List<Moment>,
        initialIndex: Int,
        feedKind: ProfileMomentZoomFeedKind,
    ) {
        val moment = moments.getOrNull(initialIndex) ?: return
        zoomMomentsSnapshot = moments
        GlobalVideoManager.pauseAllVideos()
        GlobalVideoManager.clearProfilePlaybackHandoffState()
        if (moment.hasVideoMedia) {
            val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
            GlobalVideoManager.resetPlaybackPosition(forMomentId = consumerId)
            GlobalVideoManager.releasePreservedPlayer(consumerId = consumerId)
        }
        openZoomDetail?.invoke(
            ProfileMomentZoomDestination(
                zoomSourceID = ProfileGridHeroLayout.sourceKey(moment, initialIndex),
                initialIndex = initialIndex,
                initialMomentId = moment.id,
                feedKind = feedKind,
                restrictPlaybackToInitialIndex = false,
            ),
        )
    }

    fun expandToDetail(
        moments: List<Moment>,
        initialIndex: Int,
        feedKind: ProfileMomentZoomFeedKind,
        openCommentsOnAppear: Boolean = false,
    ) {
        val selection = menuSelection ?: return
        openZoomDetailFromMenu(moments, selection, feedKind, openCommentsOnAppear)
    }

    fun openZoomDetailFromMenu(
        moments: List<Moment>,
        selection: ProfileGridMomentMenuSelection,
        feedKind: ProfileMomentZoomFeedKind,
        openCommentsOnAppear: Boolean = false,
    ) {
        if (phase !is ProfileGridHeroPhase.MenuPeek) return
        val resolvedIndex = resolvedDetailIndex(selection, moments)
        val moment = moments.getOrNull(resolvedIndex) ?: selection.moment
        val sourceID = ProfileGridHeroLayout.sourceKey(selection.moment, selection.index)
        if (moment.hasVideoMedia) {
            val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
            GlobalVideoManager.markProfileHeroHandoff(forMomentId = consumerId)
            FeedVisibilityCoordinator.pinActiveVideo(momentId = consumerId)
        }
        HapticManager.shared.lightImpact()
        peekAnimatingOpen = false
        peekProgressTarget = 0f
        scrimOpacityTarget = 0f
        menuOpacityTarget = 0f
        phase = ProfileGridHeroPhase.Idle
        openZoomDetail?.invoke(
            ProfileMomentZoomDestination(
                zoomSourceID = sourceID,
                initialIndex = resolvedIndex,
                initialMomentId = moment.id,
                feedKind = feedKind,
                restrictPlaybackToInitialIndex = true,
                openCommentsOnAppear = openCommentsOnAppear,
            ),
        )
    }

    fun dismissDetail() {
        clearZoomNavigation?.invoke()
        resetToIdle()
    }

    private fun resolvedDetailIndex(selection: ProfileGridMomentMenuSelection, moments: List<Moment>): Int {
        val momentId = selection.moment.id
        if (momentId != null) {
            val matched = moments.indexOfFirst { it.id == momentId }
            if (matched >= 0) return matched
        }
        return selection.index.coerceIn(0, (moments.size - 1).coerceAtLeast(0))
    }

    fun resetToIdle() {
        phase = ProfileGridHeroPhase.Idle
        peekProgress = 0f
        peekProgressTarget = 0f
        expandProgress = 0f
        collapseProgress = 0f
        menuOpacity = 0f
        menuOpacityTarget = 0f
        detailContentOpacity = 0f
        scrimOpacity = 0f
        scrimOpacityTarget = 0f
        showPinConfirm = false
        isDismissingInteractively = false
        toastMessage = null
        menuKind = ProfileGridHeroMenuKind.OWNER
        GlobalVideoManager.clearProfilePlaybackHandoffState()
    }

    private fun pauseProfileHeroVideo(moment: Moment) {
        if (!moment.hasVideoMedia) return
        val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
        GlobalVideoManager.pauseVideo(consumerId)
        GlobalVideoManager.releasePreservedPlayer(consumerId = consumerId)
    }

    val flyingHeroOpacity: Float
        get() = when (phase) {
            is ProfileGridHeroPhase.MenuPeek -> 1f
            is ProfileGridHeroPhase.Expanding -> max(0f, 1f - detailContentOpacity)
            is ProfileGridHeroPhase.Retracting -> {
                val fadeStart = ProfileGridHeroLayout.retractFadeStart
                if (collapseProgress <= fadeStart) 1f
                else {
                    val fadeT = ProfileGridHeroMotion.easeOut(
                        (collapseProgress - fadeStart) / max(1f - fadeStart, 0.01f),
                    )
                    max(0f, 1f - fadeT)
                }
            }
            else -> 0f
        }

    fun menuPresentationOffset(density: Float): Float {
        val reveal = ProfileGridHeroMotion.smoothstep(
            ProfileGridHeroMotion.remap(menuOpacity, start = 0.38f, end = 1f),
        )
        return (1f - reveal) * 12f * density
    }

    val menuPresentationScale: Float
        get() {
            val reveal = ProfileGridHeroMotion.smoothstep(
                ProfileGridHeroMotion.remap(menuOpacity, start = 0.38f, end = 1f),
            )
            return 0.97f + reveal * 0.03f
        }

    val scrimPresentationOpacity: Float
        get() = when (phase) {
            is ProfileGridHeroPhase.MenuPeek -> 0.30f * ProfileGridHeroMotion.smoothstep(
                ProfileGridHeroMotion.remap(peekProgress, start = 0.05f, end = 0.70f),
            )
            else -> 0.28f * ProfileGridHeroMotion.smoothstep(scrimOpacity)
        }

    fun menuRowRevealProgress(index: Int): Float {
        val start = index * 0.07f
        val end = start + 0.52f
        return ProfileGridHeroMotion.easeOut(
            ProfileGridHeroMotion.remap(menuOpacity, start = start, end = end),
        )
    }

    val showsFlyingHeroChrome: Boolean
        get() = when (phase) {
            is ProfileGridHeroPhase.Expanding -> detailContentOpacity < 0.08f
            else -> true
        }

    val heroChromeRevealOpacity: Float
        get() = when (phase) {
            is ProfileGridHeroPhase.MenuPeek -> ProfileGridHeroMotion.smoothstep(
                ProfileGridHeroMotion.remap(peekProgress, start = 0.50f, end = 0.88f),
            )
            is ProfileGridHeroPhase.Expanding -> if (detailContentOpacity < 0.08f) 1f else 0f
            else -> 1f
        }

    val shouldRenderFlyingHero: Boolean
        get() = phase is ProfileGridHeroPhase.MenuPeek && flyingHeroOpacity > 0.02f

    fun heroPresentation(
        containerSize: Size,
        safeTop: Float,
        safeBottom: Float,
        moment: Moment,
        density: Float,
    ): ProfileGridHeroPresentation {
        val peekFrame = ProfileGridHeroLayout.peekCardFrame(
            containerSize = containerSize,
            safeTop = safeTop,
            safeBottom = safeBottom,
            moment = moment,
            showPinConfirm = showPinConfirm,
            menuBlockHeightPx = menuBlockHeight(moment, density),
            density = density,
        )
        val detailFrame = ProfileGridHeroLayout.detailMediaFrame(containerSize, safeTop, moment, density)
        val origin = if (sourceFrame.width > 1f) {
            sourceFrame
        } else {
            ProfileGridHeroLayout.fallbackThumbnailFrame(containerSize, density)
        }

        val frame: Rect
        val cornerRadius: Float
        val scale: Float
        val shadowRadius: Float
        val shadowOpacity: Float

        when (phase) {
            is ProfileGridHeroPhase.MenuPeek -> {
                frame = ProfileGridHeroLayout.peekHeroFrame(origin, peekFrame, peekProgress)
                cornerRadius = ProfileGridHeroLayout.peekHeroCornerRadius(peekProgress)
                scale = 1f
                val shadowT = ProfileGridHeroMotion.smoothstep(
                    ProfileGridHeroMotion.remap(peekProgress, start = 0.18f, end = 0.85f),
                )
                shadowRadius = ProfileGridHeroLayout.lerp(6f, 18f, shadowT)
                shadowOpacity = ProfileGridHeroLayout.lerp(0.12f, 0.24f, shadowT)
            }
            is ProfileGridHeroPhase.Expanding, is ProfileGridHeroPhase.Detail -> {
                frame = ProfileGridHeroLayout.lerp(peekFrame, detailFrame, expandProgress)
                cornerRadius = ProfileGridHeroLayout.lerp(
                    ProfileGridHeroLayout.peekCornerRadius,
                    ProfileGridHeroLayout.detailCornerRadius,
                    expandProgress,
                )
                scale = 1f
                shadowRadius = ProfileGridHeroLayout.lerp(18f, 8f, expandProgress)
                shadowOpacity = ProfileGridHeroLayout.lerp(0.24f, 0.1f, expandProgress)
            }
            is ProfileGridHeroPhase.Retracting -> {
                val split = ProfileGridHeroLayout.retractPeekSplit
                val t = collapseProgress.coerceIn(0f, 1f)
                if (t <= split) {
                    val local = t / max(split, 0.01f)
                    frame = ProfileGridHeroLayout.lerp(detailFrame, peekFrame, ProfileGridHeroMotion.easeOut(local))
                    cornerRadius = ProfileGridHeroLayout.lerp(
                        ProfileGridHeroLayout.detailCornerRadius,
                        ProfileGridHeroLayout.peekCornerRadius,
                        ProfileGridHeroMotion.easeOut(local),
                    )
                    scale = 1f
                    shadowRadius = ProfileGridHeroLayout.lerp(8f, 16f, local)
                    shadowOpacity = ProfileGridHeroLayout.lerp(0.1f, 0.2f, local)
                } else {
                    val local = (t - split) / max(1f - split, 0.01f)
                    frame = ProfileGridHeroLayout.lerp(peekFrame, origin, ProfileGridHeroMotion.easeOut(local))
                    cornerRadius = ProfileGridHeroLayout.lerp(
                        ProfileGridHeroLayout.peekCornerRadius,
                        ProfileGridHeroLayout.thumbnailCornerRadius,
                        ProfileGridHeroMotion.easeOut(local),
                    )
                    scale = 0.98f + (1f - local) * 0.02f
                    shadowRadius = ProfileGridHeroLayout.lerp(16f, 4f, local)
                    shadowOpacity = ProfileGridHeroLayout.lerp(0.2f, 0.08f, local)
                }
            }
            else -> {
                frame = origin
                cornerRadius = ProfileGridHeroLayout.thumbnailCornerRadius
                scale = 1f
                shadowRadius = 4f
                shadowOpacity = 0.08f
            }
        }

        return ProfileGridHeroPresentation(
            frame = frame,
            cornerRadius = cornerRadius,
            scale = scale,
            opacity = flyingHeroOpacity,
            shadowRadius = shadowRadius,
            shadowOpacity = shadowOpacity,
        )
    }
}

@Composable
fun ProfileGridFlyingHeroShell(
    presentation: ProfileGridHeroPresentation,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    presentation.frame.left.roundToInt(),
                    presentation.frame.top.roundToInt(),
                )
            }
            .size(
                with(density) { presentation.frame.width.toDp() },
                with(density) { presentation.frame.height.toDp() },
            )
            .graphicsLayer {
                scaleX = presentation.scale
                scaleY = presentation.scale
                alpha = presentation.opacity
                shadowElevation = presentation.shadowRadius
            }
            .shadow(
                elevation = with(density) { presentation.shadowRadius.toDp() },
                shape = RoundedCornerShape(presentation.cornerRadius.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = presentation.shadowOpacity),
                spotColor = Color.Black.copy(alpha = presentation.shadowOpacity),
            )
            .clip(RoundedCornerShape(presentation.cornerRadius.dp)),
    ) {
        content()
    }
}

@Composable
fun ProfileGridHeroDetailLayer(
    coordinator: ProfileGridHeroTransitionCoordinator,
    moments: List<Moment>,
    zoomFeedKind: ProfileMomentZoomFeedKind = ProfileMomentZoomFeedKind.OWN_MOMENTS,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var showShareSheet by remember { mutableStateOf(false) }
    var shareMoment by remember { mutableStateOf<Moment?>(null) }
    val dark = isSystemInDarkTheme()
    val pinnedToast = stringResource(R.string.context_menu_pin_moment)

    val peekDuration = if (coordinator.peekAnimatingOpen) {
        ProfileGridHeroLayout.peekLiftMs
    } else {
        ProfileGridHeroLayout.peekDismissMs
    }
    val peekAnimated by animateFloatAsState(
        targetValue = coordinator.peekProgressTarget,
        animationSpec = tween(peekDuration, easing = ProfileGridHeroLayout.smoothEasing),
        label = "heroPeek",
    )
    val menuAnimated by animateFloatAsState(
        targetValue = coordinator.menuOpacityTarget,
        animationSpec = tween(peekDuration, easing = ProfileGridHeroLayout.smoothEasing),
        label = "heroMenu",
    )
    val scrimAnimated by animateFloatAsState(
        targetValue = coordinator.scrimOpacityTarget,
        animationSpec = tween(peekDuration, easing = ProfileGridHeroLayout.smoothEasing),
        label = "heroScrim",
    )
    LaunchedEffect(peekAnimated, menuAnimated, scrimAnimated) {
        coordinator.peekProgress = peekAnimated
        coordinator.menuOpacity = menuAnimated
        coordinator.scrimOpacity = scrimAnimated
        coordinator.finishDismissIfNeeded()
    }

    coordinator.toastMessage?.let { message ->
        LaunchedEffect(message) {
            delay(2000)
            if (coordinator.toastMessage == message) coordinator.toastMessage = null
        }
    }

    if (!coordinator.isInteractive && !showShareSheet) return

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            .zIndex(40f),
    ) {
        val containerSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val safeTop = with(density) { statusTop.toPx() }
        val safeBottom = with(density) { navBottom.toPx() }

        fun toLocal(frame: Rect): Rect = Rect(
            frame.left - overlayOrigin.x,
            frame.top - overlayOrigin.y,
            frame.right - overlayOrigin.x,
            frame.bottom - overlayOrigin.y,
        )

        if (coordinator.isInteractive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = coordinator.scrimPresentationOpacity))
                    .clickable { coordinator.dismissMenu() },
            )
        }

        val densityPx = density.density

        if (coordinator.shouldRenderFlyingHero) {
            coordinator.activeMoment?.let { moment ->
                val raw = coordinator.heroPresentation(containerSize, safeTop, safeBottom, moment, densityPx)
                // sourceFrame está en root; peek destination ya es local al overlay
                val originLocal = if (coordinator.sourceFrame.width > 1f) {
                    toLocal(coordinator.sourceFrame)
                } else {
                    ProfileGridHeroLayout.fallbackThumbnailFrame(containerSize, densityPx)
                }
                val peekDest = ProfileGridHeroLayout.peekCardFrame(
                    containerSize, safeTop, safeBottom, moment,
                    coordinator.showPinConfirm, coordinator.menuBlockHeight(moment, densityPx),
                    densityPx,
                )
                val localPresentation = raw.copy(
                    frame = if (coordinator.phase is ProfileGridHeroPhase.MenuPeek) {
                        ProfileGridHeroLayout.peekHeroFrame(originLocal, peekDest, coordinator.peekProgress)
                    } else {
                        raw.frame
                    },
                )
                ProfileGridFlyingHeroShell(localPresentation) {
                    ProfileGridHeroCard(
                        moment = moment,
                        width = with(density) { localPresentation.frame.width.toDp() },
                        showsChrome = coordinator.showsFlyingHeroChrome,
                        showsAudience = coordinator.menuKind == ProfileGridHeroMenuKind.OWNER,
                        chromeOpacity = coordinator.heroChromeRevealOpacity,
                        onOpenMoment = {
                            coordinator.menuSelection?.let { selection ->
                                coordinator.expandToDetail(
                                    moments = moments,
                                    initialIndex = selection.index,
                                    feedKind = zoomFeedKind,
                                )
                            }
                        },
                    )
                }
            }
        }

        coordinator.menuSelection?.let { selection ->
            val heroFrame = run {
                val originLocal = if (coordinator.sourceFrame.width > 1f) {
                    toLocal(coordinator.sourceFrame)
                } else {
                    ProfileGridHeroLayout.fallbackThumbnailFrame(containerSize, densityPx)
                }
                val peekDest = ProfileGridHeroLayout.peekCardFrame(
                    containerSize, safeTop, safeBottom, selection.moment,
                    coordinator.showPinConfirm, coordinator.menuBlockHeight(selection.moment, densityPx),
                    densityPx,
                )
                ProfileGridHeroLayout.peekHeroFrame(originLocal, peekDest, coordinator.peekProgress)
            }
            val cardWidth = ProfileGridHeroLayout.cardWidth(containerSize.width, densityPx)
            val menuHeight = coordinator.menuBlockHeight(selection.moment, densityPx)
            val menuCenterY = heroFrame.bottom +
                ProfileGridHeroLayout.menuSpacingDp * densityPx + menuHeight / 2f
            // iOS: column == 2 → leading, else trailing (menu 240pt dentro de cardWidth)
            val menuContentAlignment = if (selection.index % 3 == 2) {
                Alignment.TopStart
            } else {
                Alignment.TopEnd
            }

            Box(
                Modifier
                    .offset {
                        IntOffset(
                            ((containerSize.width - cardWidth) / 2f).roundToInt(),
                            (menuCenterY - menuHeight / 2f).roundToInt(),
                        )
                    }
                    .width(with(density) { cardWidth.toDp() })
                    .graphicsLayer {
                        alpha = coordinator.menuOpacity
                        translationY = coordinator.menuPresentationOffset(densityPx)
                        scaleX = coordinator.menuPresentationScale
                        scaleY = coordinator.menuPresentationScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    },
                contentAlignment = menuContentAlignment,
            ) {
                when {
                    coordinator.showPinConfirm -> PinConfirmPanel(
                        dark = dark,
                        onConfirm = {
                            coordinator.onPin?.invoke(selection.moment, true, true)
                            coordinator.toastMessage = pinnedToast
                            coordinator.dismissMenu()
                        },
                        onCancel = { coordinator.showPinConfirm = false },
                    )
                    coordinator.menuKind == ProfileGridHeroMenuKind.VISITOR -> {
                        ProfileGridVisitorActionBar(
                            moment = selection.moment,
                            canShare = PrivacyService.canShareMoment(selection.moment),
                            onComment = {
                                coordinator.expandToDetail(
                                    moments = moments,
                                    initialIndex = selection.index,
                                    feedKind = zoomFeedKind,
                                    openCommentsOnAppear = true,
                                )
                            },
                            onShare = {
                                shareMoment = selection.moment
                                showShareSheet = true
                            },
                        )
                    }
                    else -> OwnerActionsMenu(
                        moment = selection.moment,
                        moments = moments,
                        coordinator = coordinator,
                    )
                }
            }
        }

        coordinator.toastMessage?.let { message ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    message,
                    modifier = Modifier
                        .padding(bottom = navBottom + 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(0.78f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }

    if (showShareSheet) {
        shareMoment?.let { moment ->
            ModernShareBottomSheet(
                moment = moment.toFeedMomentForMap(),
                onDismiss = {
                    showShareSheet = false
                    shareMoment = null
                },
            )
        }
    }
}

@Composable
private fun OwnerActionsMenu(
    moment: Moment,
    moments: List<Moment>,
    coordinator: ProfileGridHeroTransitionCoordinator,
) {
    val pinnedCount = moments.count { it.isPinned == true }
    val rows = buildList {
        add(
            HeroRow(
                Icons.Default.PushPin,
                stringResource(
                    if (moment.isPinned == true) R.string.context_menu_unpin_moment
                    else R.string.context_menu_pin_moment,
                ),
                false,
            ) {
                if (moment.isPinned != true && pinnedCount >= 3) {
                    coordinator.showPinConfirm = true
                } else {
                    coordinator.onPin?.invoke(moment, moment.isPinned != true, false)
                    coordinator.dismissMenu()
                }
            },
        )
        if (moment.canAdjustGridPreview) {
            add(
                HeroRow(Icons.Default.CropFree, stringResource(R.string.context_menu_adjust_preview), false) {
                    coordinator.dismissMenu()
                    coordinator.onAdjustPreview?.invoke(moment)
                },
            )
        }
        add(
            HeroRow(Icons.Default.Archive, stringResource(R.string.context_menu_archive_moment), false) {
                coordinator.dismissMenu()
                coordinator.onArchive?.invoke(moment)
            },
        )
        add(
            HeroRow(Icons.Default.Edit, stringResource(R.string.context_menu_edit_moment), false) {
                coordinator.dismissMenu()
                coordinator.onEdit?.invoke(moment)
            },
        )
        add(
            HeroRow(Icons.Default.Delete, stringResource(R.string.context_menu_delete_moment), true) {
                coordinator.dismissMenu()
                coordinator.onDelete?.invoke(moment)
            },
        )
    }

    val density = LocalDensity.current
    Column(
        Modifier
            .width(ProfileGridHeroLayout.menuWidthDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = true),
    ) {
        rows.forEachIndexed { index, row ->
            val reveal = coordinator.menuRowRevealProgress(index)
            ProfileGridMenuRow(
                icon = row.icon,
                title = row.title,
                destructive = row.destructive,
                action = row.action,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal
                    translationY = (1f - reveal) * 10f * density.density
                },
            )
        }
    }
}

private data class HeroRow(
    val icon: ImageVector?,
    val title: String,
    val destructive: Boolean,
    val action: () -> Unit,
)

@Composable
private fun PinConfirmPanel(
    dark: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = true)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.context_menu_pin_limit_confirm_title),
            Modifier.padding(horizontal = 18.dp).padding(top = 18.dp),
            color = if (dark) Color.White else Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.context_menu_pin_limit_confirm_message),
            Modifier.padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 14.dp),
            color = if (dark) Color.White.copy(0.78f) else Color.Black.copy(0.68f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.12f)))
        Text(
            stringResource(R.string.context_menu_pin_limit_confirm),
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onConfirm)
                .padding(vertical = 14.dp),
            color = Color(0xFF007AFF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(0.12f)))
        Text(
            stringResource(R.string.context_menu_pin_limit_cancel),
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCancel)
                .padding(vertical = 14.dp),
            color = if (dark) Color.White else Color.Black,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ProfileGridMenuRow(
    icon: ImageVector?,
    title: String,
    destructive: Boolean,
    action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (destructive) Color.Red else if (isSystemInDarkTheme()) Color.White else Color.Black
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = action)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Reporter de frames ≡ `profileGridThumbnailFrameReporter`. */
fun Modifier.profileGridThumbnailFrameReporter(
    moment: Moment,
    gridIndex: Int,
    coordinator: ProfileGridHeroTransitionCoordinator?,
): Modifier {
    if (coordinator == null) return this
    val key = ProfileGridHeroLayout.sourceKey(moment, gridIndex)
    return onGloballyPositioned { coords ->
        val pos = coords.positionInRoot()
        val size = coords.size
        coordinator.ingestThumbnailFrames(
            mapOf(
                key to Rect(
                    pos.x,
                    pos.y,
                    pos.x + size.width,
                    pos.y + size.height,
                ),
            ),
        )
    }
}

/** Hueco lifted ≡ `profileGridLiftedSource`. */
@Composable
fun Modifier.profileGridLiftedSource(
    moment: Moment,
    gridIndex: Int,
): Modifier {
    val coordinator = LocalProfileGridHeroCoordinator.current
    val opacity = coordinator?.liftedGridSourceContentOpacity(moment, gridIndex) ?: 1f
    val dark = isSystemInDarkTheme()
    val hole = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    return this
        .background(hole.copy(alpha = 1f - opacity))
        .graphicsLayer { alpha = opacity }
}
