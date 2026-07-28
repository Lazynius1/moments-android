package com.moments.android.views.feed.core.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.uploads.UploadStatus
import com.moments.android.utilities.momentsPress
import com.moments.android.views.story.StoryRingLayout
import com.moments.android.views.story.StorySegmentedRing
import kotlin.math.min

data class FeedStoryUserState(
    val userId: String,
    val username: String,
    val profileImageUrl: String? = null,
    val hasStory: Boolean = false,
    val hasUnseenStory: Boolean = false,
    val storyCount: Int = 0,
    val storyViewedStatus: List<Boolean> = emptyList(),
    val storyAudiences: List<String?> = emptyList(),
)

@Composable
fun YourStoryRing(
    onClick: () -> Unit,
    profileImageUrl: String? = null,
    hasStory: Boolean = false,
    storyCount: Int = 0,
    storyAudiences: List<String?> = emptyList(),
) {
    // ≡ iOS: storyUploadService.uploadingStory (no StoryUploadProgressManager.activeUploads)
    val uploadingStory = BackgroundStoryUploadService.uploadingStory
    val status = uploadingStory?.status
    val label = when {
        status == UploadStatus.Initializing -> stringResource(R.string.feed_uploading_initializing)
        status == UploadStatus.Uploading || status == UploadStatus.Processing ->
            stringResource(R.string.feed_uploading_uploading)
        status == UploadStatus.Completed || status == UploadStatus.Moderated ->
            stringResource(R.string.feed_uploading_published)
        status == UploadStatus.Failed -> stringResource(R.string.feed_uploading_retry)
        else -> stringResource(R.string.stories_your_story)
    }
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    // iOS YourStoryCircleWithProgress: scale 0.95 si failed + Spring.row
    val failedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (status == UploadStatus.Failed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f),
        label = "yourStoryFailedScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(64.dp)
            .graphicsLayer { scaleX = failedScale; scaleY = failedScale }
            // iOS: .momentsPress(scale: 0.94, haptic: .none)
            .momentsPress(scale = 0.94f)
            .clickable {
                if (status == UploadStatus.Failed && uploadingStory != null) {
                    BackgroundStoryUploadService.retryUpload(uploadingStory.tempId)
                } else {
                    onClick()
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            FeedStoryRingAvatar(
                avatarSize = StoryRingLayout.feedHeaderAvatarSize,
                lineWidth = StoryRingLayout.feedHeaderLineWidth,
                imageUrl = profileImageUrl,
                hasStory = hasStory,
                hasUnseenStory = false,
                storyCount = storyCount,
                viewedStatuses = List(storyCount) { true },
                storyAudiences = storyAudiences,
                isOwnStory = true,
                hapticsEnabled = true,
                placeholder = {
                    com.moments.android.coordinators.AsyncProfileImageView(
                        userId = currentUserId,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
            if (uploadingStory != null) {
                StoryUploadCircleOverlay(
                    progress = uploadingStory.uploadProgress,
                    status = uploadingStory.status,
                )
            }
        }
        Text(
            label,
            color = if (isSystemInDarkTheme()) Color.White.copy(0.76f) else Color.Black.copy(0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
fun UserStoryRing(
    userId: String,
    username: String,
    imageUrl: String?,
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    storyCount: Int,
    viewedStatuses: List<Boolean>,
    storyAudiences: List<String?> = emptyList(),
    onClick: () -> Unit,
) {
    // iOS RealStoryCircle: AsyncProfileImageView(userId:) + LiveUsernameContent
    val labelColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.76f)
    } else {
        Color.Black.copy(alpha = 0.76f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .width(64.dp)
            .momentsPress(scale = 0.94f)
            .clickable(onClick = onClick),
    ) {
        FeedStoryRingAvatar(
            avatarSize = StoryRingLayout.feedHeaderAvatarSize,
            lineWidth = StoryRingLayout.feedHeaderLineWidth,
            imageUrl = null,
            hasStory = hasStory,
            hasUnseenStory = hasUnseenStory,
            storyCount = storyCount,
            viewedStatuses = viewedStatuses,
            storyAudiences = storyAudiences,
            isOwnStory = false,
            hapticsEnabled = true,
            placeholder = {
                com.moments.android.coordinators.AsyncProfileImageView(
                    userId = userId,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
        com.moments.android.views.components.LiveUsernameContent(
            userId = userId,
            fallbackUsername = username,
        ) { live ->
            Text(
                live,
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

/** Compat: call sites sin userId (usa imageUrl / inicial). */
@Composable
fun UserStoryRing(
    username: String,
    imageUrl: String?,
    hasUnseenStory: Boolean,
    storyCount: Int,
    viewedStatuses: List<Boolean>,
    onClick: () -> Unit,
) {
    StoryRingItem(
        label = username,
        imageUrl = imageUrl,
        hasStory = storyCount > 0,
        hasUnseenStory = hasUnseenStory,
        storyCount = storyCount,
        viewedStatuses = viewedStatuses,
        isOwnStory = false,
        onClick = onClick,
    ) {
        Text(username.take(1).uppercase(), color = FeedInk, fontWeight = FontWeight.SemiBold)
    }
}

/** Port de `FeedStoryRingAvatar` (FeedStoryRingComponents.swift). */
@Composable
fun FeedStoryRingAvatar(
    avatarSize: Dp,
    lineWidth: Dp? = null,
    imageUrl: String?,
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    storyCount: Int,
    viewedStatuses: List<Boolean>,
    storyAudiences: List<String?> = emptyList(),
    isOwnStory: Boolean = false,
    hapticsEnabled: Boolean = false,
    placeholder: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedLineWidth = lineWidth ?: StoryRingLayout.defaultLineWidth(avatarSize)
    val outerSize = StoryRingLayout.outerFrameSize(avatarSize, resolvedLineWidth)

    Box(modifier.size(outerSize), contentAlignment = Alignment.Center) {
        // iOS siempre monta StorySegmentedRing; el Canvas no dibuja si !hasStory
        StorySegmentedRing(
            storyCount = storyCount,
            hasStory = hasStory,
            hasUnseenStory = hasUnseenStory,
            storyViewedStatus = viewedStatuses,
            storyAudiences = storyAudiences,
            isOwnStory = isOwnStory,
            ringSize = StoryRingLayout.ringStrokeDiameter(avatarSize, resolvedLineWidth),
            lineWidth = resolvedLineWidth,
            hapticsEnabled = hapticsEnabled,
        )

        Box(
            Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(FeedInk.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                placeholder()
            }
        }
    }
}

@Composable
private fun StoryRingItem(
    label: String,
    imageUrl: String?,
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    storyCount: Int,
    viewedStatuses: List<Boolean>,
    storyAudiences: List<String?> = emptyList(),
    isOwnStory: Boolean,
    onClick: () -> Unit,
    overlay: @Composable () -> Unit = {},
    placeholder: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(64.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            FeedStoryRingAvatar(
                avatarSize = StoryRingLayout.feedHeaderAvatarSize,
                lineWidth = StoryRingLayout.feedHeaderLineWidth,
                imageUrl = imageUrl,
                hasStory = hasStory,
                hasUnseenStory = hasUnseenStory,
                storyCount = storyCount,
                viewedStatuses = viewedStatuses,
                storyAudiences = storyAudiences,
                isOwnStory = isOwnStory,
                placeholder = placeholder,
            )
            overlay()
        }

        // iOS: Color.primary.opacity(0.76)
        val labelColor = if (isSystemInDarkTheme()) {
            Color.White.copy(alpha = 0.76f)
        } else {
            Color.Black.copy(alpha = 0.76f)
        }
        Text(
            label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** Port de `StoryUploadCircleOverlay` (FeedStoryRingComponents.swift). */
@Composable
fun StoryUploadCircleOverlay(
    progress: Double,
    status: UploadStatus,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val avatarSize = StoryRingLayout.feedHeaderAvatarSize
    val lineWidth = StoryRingLayout.feedHeaderLineWidth
    val ringSize = StoryRingLayout.ringStrokeDiameter(avatarSize, lineWidth)
    val track = if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.10f)
    val target = progress.coerceIn(0.0, 1.0).toFloat()
    val rendered = remember { Animatable(target) }
    LaunchedEffect(target) {
        val delta = kotlin.math.abs(target - rendered.value)
        val durationMs = (min(0.5, maxOf(0.14, delta * 1.05)) * 1000).toInt()
        rendered.animateTo(target, tween(durationMs, easing = LinearEasing))
    }

    val infinite = rememberInfiniteTransition(label = "storyUpload")
    val pulseScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse",
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseA",
    )
    // iOS arrow bounce solo en uploading/processing
    val arrowBounce by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "arrow",
    )
    val auraOffset by infinite.animateFloat(
        initialValue = 10f,
        targetValue = -18f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "auraY",
    )
    val auraOpacity by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "auraA",
    )
    val auraScale by infinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "auraS",
    )

    var checkmarkOpacity by remember { mutableFloatStateOf(0f) }
    var checkmarkScale by remember { mutableFloatStateOf(0f) }
    var checkmarkRotation by remember { mutableFloatStateOf(-15f) }
    var completionPulse by remember { mutableStateOf(false) }
    var rippleScale by remember { mutableFloatStateOf(0.2f) }
    var rippleOpacity by remember { mutableFloatStateOf(0f) }
    var completionScheduled by remember { mutableStateOf(false) }
    var arrowFlyOffset by remember { mutableFloatStateOf(0f) }
    var arrowFlyOpacity by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(status) {
        if (status == UploadStatus.Completed || status == UploadStatus.Moderated) {
            if (completionScheduled) return@LaunchedEffect
            completionScheduled = true
            com.moments.android.utilities.HapticManager.shared.success()
            rendered.animateTo(1f, tween(450, easing = LinearEasing))
            arrowFlyOffset = 0f
            arrowFlyOpacity = 1f
            arrowFlyOffset = -26f
            arrowFlyOpacity = 0f
            rippleScale = 0.2f
            rippleOpacity = 0.8f
            // ripple expand
            rippleScale = 1.6f
            rippleOpacity = 0f
            kotlinx.coroutines.delay(220)
            checkmarkScale = 0f
            checkmarkRotation = -15f
            checkmarkOpacity = 0f
            checkmarkScale = 1f
            checkmarkRotation = 0f
            checkmarkOpacity = 1f
            completionPulse = true
            kotlinx.coroutines.delay(250)
            completionPulse = false
        } else {
            completionScheduled = false
            checkmarkOpacity = 0f
            checkmarkScale = 0f
            checkmarkRotation = -15f
            completionPulse = false
            rippleScale = 0.2f
            rippleOpacity = 0f
            arrowFlyOffset = 0f
            arrowFlyOpacity = 1f
        }
    }

    val checkScaleAnim by androidx.compose.animation.core.animateFloatAsState(
        checkmarkScale,
        androidx.compose.animation.core.spring(dampingRatio = 0.52f),
        label = "chkS",
    )
    val checkRotAnim by androidx.compose.animation.core.animateFloatAsState(
        checkmarkRotation,
        androidx.compose.animation.core.spring(dampingRatio = 0.52f),
        label = "chkR",
    )
    val checkOpAnim by androidx.compose.animation.core.animateFloatAsState(
        checkmarkOpacity,
        androidx.compose.animation.core.spring(dampingRatio = 0.52f),
        label = "chkO",
    )
    val rippleScaleAnim by androidx.compose.animation.core.animateFloatAsState(
        rippleScale,
        tween(550),
        label = "ripS",
    )
    val rippleOpAnim by androidx.compose.animation.core.animateFloatAsState(
        rippleOpacity,
        tween(550),
        label = "ripO",
    )
    val arrowFlyY by androidx.compose.animation.core.animateFloatAsState(
        arrowFlyOffset,
        tween(280),
        label = "arrY",
    )
    val arrowFlyA by androidx.compose.animation.core.animateFloatAsState(
        arrowFlyOpacity,
        tween(280),
        label = "arrA",
    )
    val pulseScaleAnim by androidx.compose.animation.core.animateFloatAsState(
        if (completionPulse) 1.06f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.72f),
        label = "pulseDone",
    )

    val p = rendered.value
    val progressBrush = when (status) {
        UploadStatus.Failed -> Brush.linearGradient(listOf(Color(0xFFFF453A), Color(0xFFFF8A3D)))
        else -> {
            fun lerp(a: Long, b: Long, f: Float): Color {
                val ar = ((a shr 16) and 0xFF) / 255f
                val ag = ((a shr 8) and 0xFF) / 255f
                val ab = (a and 0xFF) / 255f
                val br = ((b shr 16) and 0xFF) / 255f
                val bg = ((b shr 8) and 0xFF) / 255f
                val bb = (b and 0xFF) / 255f
                return Color(ar + (br - ar) * f, ag + (bg - ag) * f, ab + (bb - ab) * f)
            }
            Brush.linearGradient(
                listOf(lerp(0x6A11CB, 0x34C759, p), lerp(0x007AFF, 0x1EA84C, p)),
            )
        }
    }

    val isUploadingPhase =
        status == UploadStatus.Uploading || status == UploadStatus.Processing
    val isCompleting =
        status == UploadStatus.Completed || status == UploadStatus.Moderated

    Box(
        modifier
            .size(StoryRingLayout.outerFrameSize(avatarSize, lineWidth))
            .graphicsLayer {
                scaleX = pulseScaleAnim
                scaleY = pulseScaleAnim
            },
        contentAlignment = Alignment.Center,
    ) {
        if (status == UploadStatus.Initializing) {
            Canvas(
                Modifier
                    .size(ringSize)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    },
            ) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val dia = size.minDimension
                val topLeft = Offset((size.width - dia) / 2f, (size.height - dia) / 2f)
                drawArc(
                    brush = Brush.linearGradient(listOf(Color(0xFF6A11CB), Color(0xFF007AFF))),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(dia, dia),
                    style = stroke,
                )
            }
        } else {
            Canvas(Modifier.size(ringSize)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val dia = size.minDimension
                val topLeft = Offset((size.width - dia) / 2f, (size.height - dia) / 2f)
                val arc = Size(dia, dia)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = stroke,
                )
                drawArc(
                    brush = progressBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * maxOf(0.04f, p),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = stroke,
                )
            }
        }

        Box(
            Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background((if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)).copy(alpha = 0.42f)),
        )

        // iOS ripple stroke white 0.8
        Canvas(
            Modifier
                .size(ringSize)
                .graphicsLayer {
                    scaleX = rippleScaleAnim
                    scaleY = rippleScaleAnim
                    alpha = rippleOpAnim
                },
        ) {
            drawArc(
                color = Color.White.copy(alpha = 0.8f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // iOS statusGlyph — iconos @ 18pt bold
        when (status) {
            UploadStatus.Failed -> Icon(
                Icons.Filled.PriorityHigh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            UploadStatus.Completed, UploadStatus.Moderated -> {
                if (checkOpAnim > 0f) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = checkScaleAnim
                                scaleY = checkScaleAnim
                                rotationZ = checkRotAnim
                                alpha = checkOpAnim
                            },
                    )
                } else {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                translationY = arrowFlyY
                                alpha = arrowFlyA
                            },
                    )
                }
            }
            UploadStatus.Initializing -> Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            else -> {
                // uploading/processing: aura + arrow
                Box(contentAlignment = Alignment.Center) {
                    if (isUploadingPhase) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    translationY = auraOffset
                                    scaleX = auraScale
                                    scaleY = auraScale
                                    alpha = auraOpacity
                                },
                        )
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                translationY = if (isCompleting) arrowFlyY else arrowBounce
                                alpha = if (isCompleting) arrowFlyA else 1f
                            },
                    )
                }
            }
        }
    }
}
