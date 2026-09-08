package com.moments.android.views.creator.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.story.CurrentUserVerifiedBadge
import kotlin.math.abs
import kotlin.math.roundToInt

/** ≡ `StoryAlignmentGuideMetrics`. */
object StoryAlignmentGuideMetrics {
    const val snapThreshold = 8f
    const val displayThreshold = 12f
    const val lineWidth = 1.5f
    val color = Color(0xFF5AC8FA)
    const val sideInset = 16f
    const val headerCenterY = 26f
    const val headerHeight = 40f
    val headerBottomY: Float get() = headerCenterY + headerHeight / 2f
    fun bottomEdgeY(canvasHeight: Float, density: Float) = canvasHeight - headerBottomY * density
    const val chromeOpacity = 0.62f
    const val topScrimHeight = 96f
    val dotDash = floatArrayOf(0.2f, 6.5f)
    const val markArm = 11f
    const val markGap = 3.5f
}

/** ≡ `StoryAlignmentGuideSnapshot`. */
data class StoryAlignmentGuideSnapshot(
    val showsVerticalCenter: Boolean = false,
    val showsHorizontalCenter: Boolean = false,
    val showsLeft: Boolean = false,
    val showsRight: Boolean = false,
    val showsTopEdge: Boolean = false,
    val showsBottomEdge: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !showsVerticalCenter && !showsHorizontalCenter && !showsLeft && !showsRight && !showsTopEdge && !showsBottomEdge

    val engagementTokens: Set<String>
        get() = buildSet {
            if (showsVerticalCenter) add("vc")
            if (showsHorizontalCenter) add("hc")
            if (showsLeft) add("left")
            if (showsRight) add("right")
            if (showsTopEdge) add("top")
            if (showsBottomEdge) add("bottom")
        }

    companion object {
        val Empty = StoryAlignmentGuideSnapshot()
    }
}

/** ≡ `StoryAlignmentGuideBroker`. Solo el overlay observa estado. */
class StoryAlignmentGuideBroker {
    var isActive by mutableStateOf(false)
        private set
    var snapshot by mutableStateOf(StoryAlignmentGuideSnapshot.Empty)
        private set

    fun publish(next: StoryAlignmentGuideSnapshot) {
        if (!isActive) isActive = true
        if (snapshot == next) return
        storyAlignmentHapticIfNeeded(snapshot, next)
        snapshot = next
    }

    fun clear() {
        if (!isActive && snapshot == StoryAlignmentGuideSnapshot.Empty) return
        isActive = false
        snapshot = StoryAlignmentGuideSnapshot.Empty
    }
}

fun storyAlignmentHapticIfNeeded(
    previous: StoryAlignmentGuideSnapshot,
    next: StoryAlignmentGuideSnapshot,
) {
    if (next.engagementTokens.subtract(previous.engagementTokens).isEmpty()) return
    HapticManager.shared.selection()
}

fun storySanitizedLayoutSize(width: Float, height: Float, fallback: Float = 120f): Size {
    val safeWidth = if (width.isFinite() && width > 1f) minOf(width, 2048f) else fallback
    val safeHeight = if (height.isFinite() && height > 1f) minOf(height, 2048f) else fallback
    return Size(safeWidth, safeHeight)
}

fun storyApplyAlignmentSnap(
    proposedX: Float,
    proposedY: Float,
    itemWidth: Float,
    itemHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
): Offset {
    if (canvasWidth <= 8f || canvasHeight <= 8f) return Offset(proposedX, proposedY)
    val threshold = StoryAlignmentGuideMetrics.snapThreshold * density
    val width = itemWidth.coerceAtLeast(1f)
    val height = itemHeight.coerceAtLeast(1f)
    val midX = canvasWidth / 2f
    val midY = canvasHeight / 2f
    val inset = StoryAlignmentGuideMetrics.sideInset * density
    val headerBottom = StoryAlignmentGuideMetrics.headerBottomY * density
    val bottomEdge = StoryAlignmentGuideMetrics.bottomEdgeY(canvasHeight, density)
    var x = proposedX
    var y = proposedY

    if (abs(proposedX - midX) <= threshold) {
        x = midX
    } else {
        val left = proposedX - width / 2f
        val right = proposedX + width / 2f
        when {
            abs(left - inset) <= threshold -> x = inset + width / 2f
            abs(right - (canvasWidth - inset)) <= threshold -> x = canvasWidth - inset - width / 2f
        }
    }

    if (abs(proposedY - midY) <= threshold) {
        y = midY
    } else {
        val top = proposedY - height / 2f
        val bottom = proposedY + height / 2f
        when {
            abs(top - headerBottom) <= threshold -> y = headerBottom + height / 2f
            abs(bottom - bottomEdge) <= threshold -> y = bottomEdge - height / 2f
        }
    }

    return Offset(x, y)
}

fun storyAlignmentGuides(
    centerX: Float,
    centerY: Float,
    itemWidth: Float,
    itemHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
): StoryAlignmentGuideSnapshot {
    if (canvasWidth <= 8f || canvasHeight <= 8f) return StoryAlignmentGuideSnapshot.Empty
    val threshold = StoryAlignmentGuideMetrics.displayThreshold * density
    val width = itemWidth.coerceAtLeast(1f)
    val height = itemHeight.coerceAtLeast(1f)
    val left = centerX - width / 2f
    val right = centerX + width / 2f
    val top = centerY - height / 2f
    val bottom = centerY + height / 2f
    val inset = StoryAlignmentGuideMetrics.sideInset * density
    val headerBottom = StoryAlignmentGuideMetrics.headerBottomY * density
    val bottomEdge = StoryAlignmentGuideMetrics.bottomEdgeY(canvasHeight, density)

    return StoryAlignmentGuideSnapshot(
        showsVerticalCenter = abs(centerX - canvasWidth / 2f) <= threshold,
        showsHorizontalCenter = abs(centerY - canvasHeight / 2f) <= threshold,
        showsLeft = abs(left - inset) <= threshold,
        showsRight = abs(right - (canvasWidth - inset)) <= threshold,
        showsTopEdge = abs(top - headerBottom) <= threshold,
        showsBottomEdge = abs(bottom - bottomEdge) <= threshold,
    )
}

fun storyAlignAndKeepVisible(
    proposedX: Float,
    proposedY: Float,
    itemWidth: Float,
    itemHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    overTrash: Boolean,
    broker: StoryAlignmentGuideBroker,
    density: Float,
): Offset {
    if (overTrash) {
        broker.clear()
        val kept = storyKeepOverlayVisibleCenter(
            x = proposedX,
            y = proposedY,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
        )
        return Offset(kept.first, kept.second)
    }
    val snapped = storyApplyAlignmentSnap(
        proposedX = proposedX,
        proposedY = proposedY,
        itemWidth = itemWidth,
        itemHeight = itemHeight,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        density = density,
    )
    val clamped = storyKeepOverlayVisibleCenter(
        x = snapped.x,
        y = snapped.y,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
    )
    broker.publish(
        storyAlignmentGuides(
            centerX = clamped.first,
            centerY = clamped.second,
            itemWidth = itemWidth,
            itemHeight = itemHeight,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            density = density,
        ),
    )
    return Offset(clamped.first, clamped.second)
}

/** ≡ `StoryAlignmentGuidesOverlay`. */
@Composable
fun StoryAlignmentGuidesOverlay(
    broker: StoryAlignmentGuideBroker,
    modifier: Modifier = Modifier,
) {
    val snapshot = broker.snapshot
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        if (snapshot.showsTopEdge) {
            val headerCenterPx = with(density) { StoryAlignmentGuideMetrics.headerCenterY.dp.toPx() }
            val headerHeightPx = with(density) { StoryAlignmentGuideMetrics.headerHeight.dp.toPx() }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(StoryAlignmentGuideMetrics.topScrimHeight.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.46f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            StoryAlignmentHeaderChromePreview(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(StoryAlignmentGuideMetrics.headerHeight.dp)
                    .offset {
                        IntOffset(0, (headerCenterPx - headerHeightPx / 2f).roundToInt())
                    }
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = StoryAlignmentGuideMetrics.chromeOpacity },
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            if (snapshot.isEmpty) return@Canvas
            val d = density.density
            val color = StoryAlignmentGuideMetrics.color
            val stroke = StoryAlignmentGuideMetrics.lineWidth * d
            val inset = StoryAlignmentGuideMetrics.sideInset * d
            val headerBottom = StoryAlignmentGuideMetrics.headerBottomY * d
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomEdge = StoryAlignmentGuideMetrics.bottomEdgeY(canvasHeight, d)
            val dotted = PathEffect.dashPathEffect(
                floatArrayOf(
                    StoryAlignmentGuideMetrics.dotDash[0] * d,
                    StoryAlignmentGuideMetrics.dotDash[1] * d,
                ),
                0f,
            )
            val xs = buildList {
                if (snapshot.showsLeft) add(inset)
                if (snapshot.showsVerticalCenter) add(canvasWidth / 2f)
                if (snapshot.showsRight) add(canvasWidth - inset)
            }
            val ys = buildList {
                if (snapshot.showsTopEdge) add(headerBottom)
                if (snapshot.showsHorizontalCenter) add(canvasHeight / 2f)
                if (snapshot.showsBottomEdge) add(bottomEdge)
            }
            val dottedColor = color.copy(alpha = 0.88f)
            xs.forEach { x ->
                drawLine(
                    dottedColor,
                    Offset(x, 0f),
                    Offset(x, canvasHeight),
                    stroke,
                    StrokeCap.Round,
                    pathEffect = dotted,
                )
            }
            ys.forEach { y ->
                drawLine(
                    dottedColor,
                    Offset(0f, y),
                    Offset(canvasWidth, y),
                    stroke,
                    StrokeCap.Round,
                    pathEffect = dotted,
                )
            }
            val arm = StoryAlignmentGuideMetrics.markArm * d
            val gap = StoryAlignmentGuideMetrics.markGap * d
            xs.forEach { x ->
                ys.forEach { y ->
                    val isSide = x == inset || x == canvasWidth - inset
                    val isTopCorner = isSide && y == headerBottom
                    val isBottomCorner = isSide && y == bottomEdge
                    if (isTopCorner || isBottomCorner) {
                        val h = if (x == inset) arm else -arm
                        val v = if (isTopCorner) arm else -arm
                        drawLine(
                            color,
                            Offset(x, y),
                            Offset(x + h, y),
                            stroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            color,
                            Offset(x, y),
                            Offset(x, y + v),
                            stroke,
                            StrokeCap.Round,
                        )
                    } else {
                        drawLine(
                            color,
                            Offset(x, y - gap - arm),
                            Offset(x, y - gap),
                            stroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            color,
                            Offset(x, y + gap),
                            Offset(x, y + gap + arm),
                            stroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            color,
                            Offset(x - gap - arm, y),
                            Offset(x - gap, y),
                            stroke,
                            StrokeCap.Round,
                        )
                        drawLine(
                            color,
                            Offset(x + gap, y),
                            Offset(x + gap + arm, y),
                            stroke,
                            StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/** Ghost fijo del `glassmorphicHeader`: avatar, usuario, ellipsis y X. */
@Composable
private fun StoryAlignmentHeaderChromePreview(
    modifier: Modifier = Modifier,
) {
    val user = remember {
        FirebaseAuth.getInstance().currentUser?.uid?.let(LocalPersistenceService::loadUser)
    }
    val username = user?.username.orEmpty()
    val profileImagePath = user?.profileImagePath

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Color.White.copy(0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(0.7f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        username,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    CurrentUserVerifiedBadge(size = 12.dp)
                }
                Text(
                    stringResource(R.string.time_now),
                    color = Color.White.copy(0.7f),
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = Color.White.copy(0.92f),
                modifier = Modifier.size(40.dp).padding(12.dp),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = Color.White.copy(0.92f),
                modifier = Modifier.size(40.dp).padding(12.dp),
            )
        }
    }
}
