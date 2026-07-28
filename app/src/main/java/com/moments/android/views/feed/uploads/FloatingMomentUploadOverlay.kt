package com.moments.android.views.feed.uploads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.UploadingMoment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OrbSize = 58.dp
private val PanelWidth = 238.dp
private val PanelHeight = 72.dp

/**
 * Port de `FloatingMomentUploadOverlay.swift`.
 * Fuente: `BackgroundMomentUploadService.uploadingMoments`.
 */
@Composable
fun FloatingMomentUploadOverlay(
    topInset: Float,
    modifier: Modifier = Modifier,
) {
    val uploadService = BackgroundMomentUploadService
    val moments = uploadService.uploadingMoments
    val isDark = isSystemInDarkTheme()

    var isVisible by remember { mutableStateOf(false) }
    var activeMoment by remember { mutableStateOf<UploadingMoment?>(null) }
    var isExpanded by remember { mutableStateOf(false) }

    // ≡ iOS onAppear + onChange(uploadingMoments.count)
    LaunchedEffect(moments.size, moments.firstOrNull()?.tempId) {
        val first = moments.firstOrNull()
        if (first != null) {
            activeMoment = first
            isVisible = true
        } else {
            isVisible = false
            delay(400)
            if (uploadService.uploadingMoments.isEmpty()) {
                activeMoment = null
                isExpanded = false
            }
        }
    }

    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        AnimatedVisibility(
            visible = isVisible && activeMoment != null,
            enter = slideInVertically { -it / 3 } + fadeIn(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            ) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)),
            exit = slideOutVertically { -it / 4 } + fadeOut(
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
            ) + scaleOut(targetScale = 0.92f, animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f)),
            modifier = Modifier.padding(top = topInset.dp, end = 16.dp),
        ) {
            val moment = activeMoment ?: return@AnimatedVisibility
            UploadCluster(
                moment = moment,
                extraUploadsCount = max(0, moments.size - 1),
                isExpanded = isExpanded,
                isDark = isDark,
                onToggleFromOrb = {
                    if (moment.status == UploadStatus.Completed || moment.status == UploadStatus.Moderated) return@UploadCluster
                    HapticManager.shared.lightImpact()
                    isExpanded = !isExpanded
                },
                onToggleFromPanel = {
                    if (moment.status == UploadStatus.Completed || moment.status == UploadStatus.Moderated) return@UploadCluster
                    HapticManager.shared.selection()
                    isExpanded = !isExpanded
                },
                onRetry = {
                    HapticManager.shared.mediumImpact()
                    uploadService.retryUpload(moment)
                },
                onCancel = {
                    HapticManager.shared.lightImpact()
                    uploadService.cancelUpload(moment)
                },
                onForceExpand = { isExpanded = true },
                onCollapse = { isExpanded = false },
            )
        }
    }
}

@Composable
private fun UploadCluster(
    moment: UploadingMoment,
    extraUploadsCount: Int,
    isExpanded: Boolean,
    isDark: Boolean,
    onToggleFromOrb: () -> Unit,
    onToggleFromPanel: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onForceExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val status = moment.status
    val progress = moment.uploadProgress
    val content = moment.content
    val mediaCount = moment.mediaCount
    val currentIndex = moment.currentMediaIndex
    val errorMessage = moment.errorMessage
    val thumb = moment.currentMediaThumbnailBitmap ?: moment.thumbnailBitmap

    var renderedProgress by remember(moment.tempId) { mutableDoubleStateOf(0.0) }
    var showsCompletionIcon by remember(moment.tempId) { mutableStateOf(false) }
    var completionAnimationScheduled by remember(moment.tempId) { mutableStateOf(false) }
    var completionPulse by remember(moment.tempId) { mutableStateOf(false) }
    var lastHandledStatus by remember(moment.tempId) { mutableStateOf<UploadStatus?>(null) }

    val arrowOffset = remember(moment.tempId) { Animatable(0f) }
    val arrowOpacity = remember(moment.tempId) { Animatable(1f) }
    val checkmarkScale = remember(moment.tempId) { Animatable(0f) }
    val checkmarkRotation = remember(moment.tempId) { Animatable(-15f) }
    val checkmarkOpacity = remember(moment.tempId) { Animatable(0f) }
    val rippleScale = remember(moment.tempId) { Animatable(0.2f) }
    val rippleOpacity = remember(moment.tempId) { Animatable(0f) }

    suspend fun resetAnimationStates() {
        arrowOffset.snapTo(0f)
        arrowOpacity.snapTo(1f)
        checkmarkScale.snapTo(0f)
        checkmarkRotation.snapTo(-15f)
        checkmarkOpacity.snapTo(0f)
        rippleScale.snapTo(0.2f)
        rippleOpacity.snapTo(0f)
        showsCompletionIcon = false
        completionAnimationScheduled = false
        completionPulse = false
    }

    // ≡ iOS handleStatusChange — solo al cambiar status
    LaunchedEffect(status, moment.tempId) {
        if (lastHandledStatus == status) return@LaunchedEffect
        lastHandledStatus = status

        when (status) {
            UploadStatus.Initializing -> resetAnimationStates()
            UploadStatus.Completed, UploadStatus.Moderated -> {
                if (completionAnimationScheduled) return@LaunchedEffect
                completionAnimationScheduled = true
                HapticManager.shared.notification(HapticManager.NotificationType.SUCCESS)

                // Progress → 1.0 en paralelo con rocket + ripple
                val progressStart = renderedProgress
                launch {
                    val anim = Animatable(0f)
                    anim.animateTo(1f, tween(650, easing = LinearEasing)) {
                        renderedProgress = progressStart + (1.0 - progressStart) * value.toDouble()
                    }
                    renderedProgress = 1.0
                }

                // 1. Rocket launch + 2. ripple (iOS en paralelo)
                arrowOffset.snapTo(0f)
                arrowOpacity.snapTo(1f)
                rippleScale.snapTo(0.2f)
                rippleOpacity.snapTo(0.8f)
                launch {
                    arrowOffset.animateTo(-35f, tween(350, easing = FastOutSlowInEasing))
                }
                launch {
                    arrowOpacity.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
                }
                launch {
                    rippleScale.animateTo(1.6f, tween(550, easing = FastOutSlowInEasing))
                }
                launch {
                    rippleOpacity.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
                }

                delay(300)
                showsCompletionIcon = true
                checkmarkScale.snapTo(0f)
                checkmarkRotation.snapTo(-15f)
                checkmarkOpacity.snapTo(0f)
                launch { checkmarkScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                launch { checkmarkRotation.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                launch { checkmarkOpacity.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                completionPulse = true
                onCollapse()
                delay(250)
                completionPulse = false
            }
            UploadStatus.Failed -> {
                HapticManager.shared.notification(HapticManager.NotificationType.ERROR)
                onForceExpand()
                resetAnimationStates()
            }
            UploadStatus.Uploading, UploadStatus.Processing -> resetAnimationStates()
        }
    }

    // ≡ iOS syncRenderedProgress
    LaunchedEffect(progress, status, moment.tempId) {
        if (status == UploadStatus.Completed || status == UploadStatus.Moderated) {
            if (!completionAnimationScheduled) {
                // handleStatusChange lo cubre el effect de status
                return@LaunchedEffect
            }
            return@LaunchedEffect
        }
        val target = min(1.0, max(0.0, progress))
        val delta = abs(target - renderedProgress)
        val durationMs = (min(0.55, max(0.14, delta * 1.1)) * 1000).toInt()
        val start = renderedProgress
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(durationMs, easing = LinearEasing)) {
            renderedProgress = start + (target - start) * value.toDouble()
        }
    }

    // ≡ iOS updateArrowAnimation bob + aura
    val infinite = rememberInfiniteTransition(label = "uploadOrbBob")
    val auraActive = status == UploadStatus.Uploading || status == UploadStatus.Processing
    val arrowBob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (auraActive) -3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrowBob",
    )
    val auraProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aura",
    )
    val auraOffset = if (auraActive) 10f + (auraProgress * -28f) else 0f
    val auraOpacity = if (auraActive) 0.6f * (1f - auraProgress) else 0f
    val auraScale = if (auraActive) 0.8f + auraProgress * 0.5f else 1f
    val auraBlur = if (auraActive) 1f + auraProgress * 2f else 0f

    val colors = OverlayColors(isDark)
    val gradient = progressBrush(status, renderedProgress)

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // ≡ iOS liquidGlassStretch (stretch desde trailing + blur dissolve)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.0f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
            ),
            exit = fadeOut(tween(160)) + scaleOut(
                targetScale = 0.0f,
                transformOrigin = TransformOrigin(1f, 0.5f),
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 380f),
            ),
        ) {
            ExpandedPanel(
                content = content,
                mediaCount = mediaCount,
                currentIndex = currentIndex,
                status = status,
                errorMessage = errorMessage,
                thumb = thumb,
                renderedProgress = renderedProgress,
                colors = colors,
                gradient = gradient,
                onToggle = onToggleFromPanel,
                onRetry = onRetry,
                onCancel = onCancel,
            )
        }

        CompactOrb(
            status = status,
            renderedProgress = renderedProgress,
            extraUploadsCount = extraUploadsCount,
            colors = colors,
            gradient = gradient,
            completionPulse = completionPulse,
            showsCompletionIcon = showsCompletionIcon,
            arrowBob = arrowBob,
            arrowOffset = arrowOffset.value,
            arrowOpacity = arrowOpacity.value,
            checkmarkScale = checkmarkScale.value,
            checkmarkRotation = checkmarkRotation.value,
            checkmarkOpacity = checkmarkOpacity.value,
            rippleScale = rippleScale.value,
            rippleOpacity = rippleOpacity.value,
            auraOffset = auraOffset,
            auraOpacity = auraOpacity,
            auraScale = auraScale,
            auraBlur = auraBlur,
            onToggle = onToggleFromOrb,
        )
    }
}

@Composable
private fun CompactOrb(
    status: UploadStatus,
    renderedProgress: Double,
    extraUploadsCount: Int,
    colors: OverlayColors,
    gradient: Brush,
    completionPulse: Boolean,
    showsCompletionIcon: Boolean,
    arrowBob: Float,
    arrowOffset: Float,
    arrowOpacity: Float,
    checkmarkScale: Float,
    checkmarkRotation: Float,
    checkmarkOpacity: Float,
    rippleScale: Float,
    rippleOpacity: Float,
    auraOffset: Float,
    auraOpacity: Float,
    auraScale: Float,
    auraBlur: Float,
    onToggle: () -> Unit,
) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(OrbSize)
                .scale(if (completionPulse) 1.06f else 1f)
                .shadow(12.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(OrbSize)) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2
                drawArc(
                    color = colors.track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = gradient,
                    startAngle = -90f,
                    sweepAngle = (360f * max(0.04, min(1.0, renderedProgress))).toFloat(),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Box(
                Modifier
                    .size(OrbSize)
                    .scale(rippleScale)
                    .alpha(rippleOpacity)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            )
            OrbIcon(
                status = status,
                colors = colors,
                showsCompletionIcon = showsCompletionIcon,
                arrowBob = arrowBob,
                arrowOffset = arrowOffset,
                arrowOpacity = arrowOpacity,
                checkmarkScale = checkmarkScale,
                checkmarkRotation = checkmarkRotation,
                checkmarkOpacity = checkmarkOpacity,
                auraOffset = auraOffset,
                auraOpacity = auraOpacity,
                auraScale = auraScale,
                auraBlur = auraBlur,
            )
        }
        if (extraUploadsCount > 0) {
            Text(
                "+$extraUploadsCount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .offset(x = 5.dp, y = (-3).dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun OrbIcon(
    status: UploadStatus,
    colors: OverlayColors,
    showsCompletionIcon: Boolean,
    arrowBob: Float,
    arrowOffset: Float,
    arrowOpacity: Float,
    checkmarkScale: Float,
    checkmarkRotation: Float,
    checkmarkOpacity: Float,
    auraOffset: Float,
    auraOpacity: Float,
    auraScale: Float,
    auraBlur: Float,
) {
    when (status) {
        UploadStatus.Initializing -> Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = colors.iconMuted,
            modifier = Modifier.size(22.dp),
        )
        UploadStatus.Completed, UploadStatus.Moderated -> {
            if (showsCompletionIcon) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier
                        .size(22.dp)
                        .scale(checkmarkScale)
                        .rotate(checkmarkRotation)
                        .alpha(checkmarkOpacity),
                )
            } else {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier
                        .size(22.dp)
                        .offset(y = arrowOffset.dp)
                        .alpha(arrowOpacity),
                )
            }
        }
        UploadStatus.Failed -> Icon(
            Icons.Filled.PriorityHigh,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(22.dp),
        )
        UploadStatus.Uploading, UploadStatus.Processing -> {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier
                        .size(22.dp)
                        .offset(y = auraOffset.dp)
                        .scale(auraScale)
                        .alpha(auraOpacity)
                        .then(if (auraBlur > 0f) Modifier.blur(auraBlur.dp) else Modifier),
                )
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier
                        .size(22.dp)
                        .offset(y = arrowBob.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandedPanel(
    content: String,
    mediaCount: Int,
    currentIndex: Int,
    status: UploadStatus,
    errorMessage: String?,
    thumb: android.graphics.Bitmap?,
    renderedProgress: Double,
    colors: OverlayColors,
    gradient: Brush,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = content.ifBlank { stringResource(R.string.feed_uploading_new_moment) }
    val detail = detailText(status, mediaCount, currentIndex, errorMessage)
    val label = statusLabel(status)

    Row(
        Modifier
            .width(PanelWidth)
            .height(PanelHeight)
            .shadow(
                16.dp,
                RoundedCornerShape(26.dp),
                ambientColor = colors.shadow.copy(alpha = 0.8f),
                spotColor = colors.shadow.copy(alpha = 0.8f),
            )
            .momentsChromeGlass(RoundedCornerShape(26.dp), interactive = true)
            .border(0.75.dp, colors.border, RoundedCornerShape(26.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThumbnailView(thumb, mediaCount, currentIndex, colors)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                color = colors.secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressCapsule(renderedProgress, label, colors, gradient)
        }
        if (status == UploadStatus.Failed) {
            FailedActions(colors = colors, onRetry = onRetry, onCancel = onCancel)
        }
    }
}

@Composable
private fun ThumbnailView(
    thumb: android.graphics.Bitmap?,
    mediaCount: Int,
    currentIndex: Int,
    colors: OverlayColors,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = colors.iconMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (mediaCount > 1) {
            Text(
                "${currentIndex + 1}/$mediaCount",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .offset(x = 4.dp, y = 4.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ProgressCapsule(
    renderedProgress: Double,
    label: String,
    colors: OverlayColors,
    gradient: Brush,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            val h = size.height
            drawRoundRect(color = colors.track, cornerRadius = CornerRadius(h / 2, h / 2))
            val w = max(18f, size.width * min(1f, renderedProgress.toFloat()))
            drawRoundRect(
                brush = gradient,
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2, h / 2),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = colors.secondaryText, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                "${(renderedProgress * 100).toInt()}%",
                color = colors.primaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FailedActions(
    colors: OverlayColors,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9800).copy(alpha = 0.75f))
                .clickable(onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.30f))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, null, tint = colors.primaryText, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun detailText(
    status: UploadStatus,
    mediaCount: Int,
    currentIndex: Int,
    errorMessage: String?,
): String {
    val fileDetail = stringResource(R.string.feed_uploading_files, mediaCount)
    return when (status) {
        UploadStatus.Initializing -> stringResource(R.string.feed_uploading_initializing)
        UploadStatus.Uploading -> {
            val statusTxt = stringResource(R.string.feed_uploading_uploading)
            if (mediaCount > 1) {
                "$statusTxt · ${currentIndex + 1}/$mediaCount · $fileDetail"
            } else {
                "$statusTxt · $fileDetail"
            }
        }
        UploadStatus.Processing -> stringResource(R.string.feed_uploading_creating)
        UploadStatus.Completed, UploadStatus.Moderated -> stringResource(R.string.feed_uploading_available)
        UploadStatus.Failed -> errorMessage ?: stringResource(R.string.feed_uploading_error)
    }
}

@Composable
private fun statusLabel(status: UploadStatus): String = when (status) {
    UploadStatus.Initializing -> stringResource(R.string.feed_uploading_initializing)
    UploadStatus.Uploading -> stringResource(R.string.feed_uploading_uploading)
    UploadStatus.Processing -> stringResource(R.string.feed_uploading_processing)
    UploadStatus.Completed, UploadStatus.Moderated -> stringResource(R.string.feed_uploading_published)
    UploadStatus.Failed -> stringResource(R.string.feed_uploading_retry)
}

private data class OverlayColors(
    val isDark: Boolean,
) {
    val primaryText = if (isDark) Color.White.copy(alpha = 0.94f) else Color.Black.copy(alpha = 0.84f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.62f)
    val icon = if (isDark) Color.White else Color.Black
    val iconMuted = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.45f)
    val track = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
    val border = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f)
    val shadow = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f)
}

private fun progressBrush(status: UploadStatus, renderedProgress: Double): Brush {
    if (status == UploadStatus.Failed) {
        return Brush.linearGradient(listOf(Color.Red, Color(0xFFFF9800)))
    }
    val p = min(1.0, max(0.0, renderedProgress))
    val start = interpolateColor(Color(0xFF6A11CB), Color(0xFF34C759), p)
    val end = interpolateColor(Color(0xFF007AFF), Color(0xFF1EA84C), p)
    return Brush.linearGradient(listOf(start, end))
}

/** Port de `Color.interpolate` (FloatingMomentUploadOverlay.swift). */
private fun interpolateColor(from: Color, to: Color, fraction: Double): Color {
    val f = min(1f, max(0f, fraction.toFloat()))
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}
