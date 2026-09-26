package com.moments.android.views.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.Gravity
import android.view.WindowManager
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.MomentsGlassStyle
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationBannerCopy
import com.moments.android.notifications.services.NotificationCopyResolver
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.reactions.ReactionType
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.views.profile.incognito.IncognitoBannerPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BannerCapsule = RoundedCornerShape(percent = 50)

/**
 * Port de `InAppBannerView.swift` + `InAppBannerWindowPresenter`.
 *
 * Usa [Dialog] (ventana propia) encima de Nav3 `fullScreenDialog` (notificaciones,
 * mensajes, stories…) ≡ iOS `UIWindow` `.alert + 1`.
 *
 * Mientras Incognito está activo el Dialog **permanece abierto** con
 * [IncognitoBannerPill] (misma cápsula glass + tap → panel Pausar).
 */
private enum class IncognitoChromePhase { Message, Timer, TimerIcon }

@Composable
fun InAppBannerView(modifier: Modifier = Modifier) {
    val visible by InAppNotificationService.showBanner.collectAsState()
    val notification by InAppNotificationService.currentNotification.collectAsState()
    val actionToast by InAppNotificationService.actionToast.collectAsState()
    val hostGeneration by InAppNotificationService.hostGeneration.collectAsState()
    val incognitoActive by IncognitoModeService.isActive.collectAsState()
    var isQuickReplyExpanded by remember { mutableStateOf(false) }

    val incognitoBridgeToast = actionToast?.takeIf {
        it.bridgesToIncognitoPill || it.isIncognitoPaused
    }
    val incognitoPhase: IncognitoChromePhase? = when {
        incognitoBridgeToast != null -> IncognitoChromePhase.Message
        !incognitoActive -> null
        visible -> IncognitoChromePhase.TimerIcon
        else -> IncognitoChromePhase.Timer
    }
    val showsStandardBanner = visible && !incognitoActive && incognitoBridgeToast == null
    val showHost = showsStandardBanner || incognitoPhase != null

    LaunchedEffect(visible) {
        if (!visible) isQuickReplyExpanded = false
    }

    if (!showHost) return

    key(hostGeneration) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        // Banner tapeable; alrededor pasa (WRAP_CONTENT + NOT_TOUCH_MODAL).
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.setDimAmount(0f)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            window.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            window.attributes = window.attributes.apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                flags = (flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            }
        }

        Column(
            modifier
                .wrapContentWidth()
                .wrapContentHeight()
                .statusBarsPadding()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val phase = incognitoPhase
            if (phase != null) {
                Row(
                    modifier = Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IncognitoBannerPill(
                        horizontalPadding = if (phase == IncognitoChromePhase.TimerIcon) 0.dp else 20.dp,
                        showsCompactOnly = phase != IncognitoChromePhase.Timer,
                        compact = {
                            AnimatedContent(
                                targetState = phase,
                                transitionSpec = {
                                    (
                                        fadeIn(tween(140, easing = LinearOutSlowInEasing)) +
                                            scaleIn(
                                                initialScale = 0.96f,
                                                animationSpec = tween(140, easing = LinearOutSlowInEasing),
                                            )
                                        ) togetherWith (
                                        fadeOut(tween(90, easing = FastOutLinearInEasing)) +
                                            scaleOut(
                                                targetScale = 0.98f,
                                                animationSpec = tween(90, easing = FastOutLinearInEasing),
                                            )
                                        )
                                },
                                label = "incognitoChromeMorph",
                            ) { targetPhase ->
                                UnifiedIncognitoChrome(
                                    phase = targetPhase,
                                    bridgeToast = incognitoBridgeToast,
                                )
                            }
                        },
                    )

                    if (phase == IncognitoChromePhase.TimerIcon) {
                        when {
                            actionToast != null && incognitoBridgeToast == null -> {
                                ActionToastBanner(actionToast!!, clustered = true)
                            }

                            notification != null -> {
                                val current = notification!!
                                if (isQuickReplyExpanded && current.type == NotificationType.MESSAGE) {
                                    InAppMessageQuickReplyPanel(
                                        notification = current,
                                        onDismiss = { isQuickReplyExpanded = false },
                                    )
                                } else {
                                    CompactInAppBanner(
                                        notification = current,
                                        clustered = true,
                                        onExpandQuickReply = { isQuickReplyExpanded = true },
                                        onCollapseQuickReply = { isQuickReplyExpanded = false },
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (showsStandardBanner) {
                when {
                    actionToast != null -> ActionToastBanner(actionToast!!)
                    notification != null -> {
                        val current = notification!!
                        if (isQuickReplyExpanded && current.type == NotificationType.MESSAGE) {
                            InAppMessageQuickReplyPanel(
                                notification = current,
                                onDismiss = { isQuickReplyExpanded = false },
                            )
                        } else {
                            CompactInAppBanner(
                                notification = current,
                                onExpandQuickReply = { isQuickReplyExpanded = true },
                                onCollapseQuickReply = { isQuickReplyExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun UnifiedIncognitoChrome(
    phase: IncognitoChromePhase,
    bridgeToast: InAppActionToast? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val titleSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val subtitleSp = with(density) { legacyPoppinsSize(context, 12).toSp() }
    val remainingSeconds by IncognitoModeService.remainingSeconds.collectAsState()
    val formattedTime = remember(remainingSeconds) {
        val remaining = maxOf(remainingSeconds, 0)
        "%02d:%02d".format(remaining / 60, remaining % 60)
    }

    CompositionLocalProvider(
        LocalContentColor provides MomentsChromeGlass.contentColor(isDark),
    ) {
        Row(
            modifier = Modifier
                .then(
                    when (phase) {
                        IncognitoChromePhase.Timer -> Modifier.width(108.dp)
                        IncognitoChromePhase.TimerIcon -> Modifier.width(40.dp)
                        IncognitoChromePhase.Message -> Modifier.widthIn(max = 340.dp)
                    },
                )
                .heightIn(min = 40.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = BannerCapsule,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .momentsChromeGlass(
                    shape = BannerCapsule,
                    interactive = phase != IncognitoChromePhase.Message,
                    style = MomentsGlassStyle.NATIVE,
                )
                .padding(
                    horizontal = when (phase) {
                        IncognitoChromePhase.TimerIcon -> 11.dp
                        IncognitoChromePhase.Message -> 16.dp
                        IncognitoChromePhase.Timer -> 14.dp
                    },
                    vertical = if (phase == IncognitoChromePhase.Message) 10.dp else 9.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (phase) {
                IncognitoChromePhase.Message -> {
                    val toast = bridgeToast
                    if (toast != null) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                        append(toast.prefix)
                                    }
                                    val emphasis = toast.emphasis
                                    if (!emphasis.isNullOrEmpty()) {
                                        if (toast.prefix.lastOrNull()?.isLetterOrDigit() == true) {
                                            append(" ")
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(emphasis)
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                            append(toast.suffix)
                                        }
                                    }
                                },
                                color = LocalContentColor.current,
                                fontSize = titleSp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            toast.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.let { sub ->
                                Text(
                                    sub,
                                    color = LocalContentColor.current.copy(alpha = 0.62f),
                                    fontSize = subtitleSp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LocalContentColor.current,
                        )
                    }
                }
                IncognitoChromePhase.Timer -> {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        formattedTime,
                        color = LocalContentColor.current,
                        fontSize = titleSp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                IncognitoChromePhase.TimerIcon -> {
                    Icon(
                        Icons.Filled.HourglassFull,
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionToastBanner(toast: InAppActionToast, clustered: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val titleSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val subtitleSp = with(density) { legacyPoppinsSize(context, 12).toSp() }
    val undoSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    var dragOffsetY by remember(toast.id) { mutableFloatStateOf(0f) }
    val subtitle = toast.subtitle?.trim().orEmpty()
    val hasUndo = toast.undo != null
    val maxBannerWidth = if (clustered) 280.dp else 340.dp
    val undoProgress = remember(toast.id) { Animatable(1f) }

    LaunchedEffect(toast.id) {
        if (!toast.showsProgress) HapticManager.shared.success()
        if (hasUndo) {
            undoProgress.snapTo(1f)
            undoProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = toast.durationMs.toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides MomentsChromeGlass.contentColor(isDark),
    ) {
    Box(
        modifier = Modifier
            .widthIn(max = maxBannerWidth)
            .then(if (clustered) Modifier else Modifier.padding(horizontal = 20.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .widthIn(max = maxBannerWidth)
                .width(IntrinsicSize.Max)
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                .shadow(
                    elevation = 8.dp,
                    shape = BannerCapsule,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .momentsChromeGlass(
                    shape = BannerCapsule,
                    interactive = false,
                    style = MomentsGlassStyle.NATIVE,
                )
                .then(
                    if (hasUndo) {
                        Modifier.drawWithContent {
                            drawContent()
                            val strokeWidth = 2.dp.toPx()
                            val inset = strokeWidth / 2f
                            val capsule = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = inset,
                                        top = inset,
                                        right = size.width - inset,
                                        bottom = size.height - inset,
                                        cornerRadius = CornerRadius(size.height / 2f),
                                    ),
                                )
                            }
                            val measure = PathMeasure()
                            measure.setPath(capsule, forceClosed = false)
                            val segment = Path()
                            measure.getSegment(
                                startDistance = 0f,
                                stopDistance = measure.length * undoProgress.value,
                                destination = segment,
                                startWithMoveTo = true,
                            )
                            drawPath(
                                path = segment,
                                color = Color.Red.copy(alpha = 0.85f),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .pointerInput(toast.id) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0f) dragOffsetY += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffsetY < -20f) InAppNotificationService.dismissManually()
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                    )
                }
                .heightIn(min = if (clustered) 56.dp else 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { InAppNotificationService.dismissManually() },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(toast.prefix)
                        }
                        val emphasis = toast.emphasis
                        if (!emphasis.isNullOrEmpty()) {
                            if (toast.prefix.lastOrNull()?.isLetterOrDigit() == true) {
                                append(" ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(emphasis)
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append(toast.suffix)
                            }
                        }
                    },
                    color = LocalContentColor.current,
                    fontSize = titleSp,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = titleSp,
                        stepSize = 0.5.sp,
                    ),
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = LocalContentColor.current.copy(alpha = 0.62f),
                        fontSize = subtitleSp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (toast.showsProgress) {
                MomentsCircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (toast.bridgesToIncognitoPill || toast.isIncognitoPaused) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.CheckCircle
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { InAppNotificationService.dismissManually() },
                    tint = LocalContentColor.current,
                )
            }

            toast.undo?.let {
                Text(
                    text = stringResource(R.string.notifications_deleted_undo),
                    color = Color.Red,
                    fontSize = undoSp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { InAppNotificationService.performUndoFromActionToast() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun CompactInAppBanner(
    notification: MomentsNotification,
    clustered: Boolean = false,
    onExpandQuickReply: () -> Unit,
    onCollapseQuickReply: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val copy = remember(notification) { NotificationCopyResolver.resolve(notification) }
    val isSystem = isSystemBanner(notification)
    val accent = if (isSystem) Color(0xFFFF9500) else colorFor(notification.type)
    val lines = bannerTextLines(copy, notification)
    // iOS: legacyPoppinsSize(13/12)
    val headlineSp = with(density) { legacyPoppinsSize(context, 13).toSp() }
    val detailSp = with(density) { legacyPoppinsSize(context, 12).toSp() }

    var contentPreviewImage by remember(notification.id) { mutableStateOf<String?>(null) }
    var contentPreviewFeedCrop by remember(notification.id) { mutableStateOf<com.moments.android.models.MediaItemFeedCrop?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var suppressTapUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(notification.id) {
        contentPreviewImage = null
        contentPreviewFeedCrop = null
        HapticManager.shared.success()
        val preview = loadPreviewImage(notification)
        contentPreviewImage = preview.first
        contentPreviewFeedCrop = preview.second
    }

    // iOS: wash sutil (sin borde gradient gordo)
    val accentWash = Brush.linearGradient(
        colors = listOf(
            accent.copy(alpha = 0.18f),
            accent.copy(alpha = 0.08f),
            Color.Transparent,
        ),
    )

    // Mismo gestos de siempre; tamaño + look tipo iOS SToasts.
    Box(
        modifier = Modifier
            .widthIn(max = if (clustered) 280.dp else 310.dp)
            .then(if (clustered) Modifier else Modifier.padding(horizontal = 20.dp)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                .shadow(
                    elevation = 8.dp,
                    shape = BannerCapsule,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .momentsChromeGlass(
                    shape = BannerCapsule,
                    interactive = false,
                    style = MomentsGlassStyle.NATIVE,
                )
                .pointerInput(notification.id) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0f) dragOffsetY += dragAmount
                        },
                        onDragEnd = {
                            if (dragOffsetY < -20f) {
                                onCollapseQuickReply()
                                InAppNotificationService.dismissManually()
                            }
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                    )
                }
                .pointerInput(notification.id, notification.conversationId) {
                    detectTapGestures(
                        onLongPress = {
                            if (notification.type == NotificationType.MESSAGE &&
                                !notification.conversationId.isNullOrBlank()
                            ) {
                                suppressTapUntilMs = System.currentTimeMillis() + 600L
                                onExpandQuickReply()
                                HapticManager.shared.mediumImpact()
                            }
                        },
                        onTap = {
                            if (System.currentTimeMillis() < suppressTapUntilMs) return@detectTapGestures
                            onCollapseQuickReply()
                            InAppNotificationService.dismissManually()
                            scope.launch {
                                routeBannerTap(notification, context)
                            }
                        },
                    )
                },
        ) {
            // Wash encima del fill AdaptiveColors (paridad iOS).
            Box(
                Modifier
                    .matchParentSize()
                    .clip(BannerCapsule)
                    .background(accentWash),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // iOS: .primary/.secondary sobre glass — Android fuerza contentColor del chrome.
                CompositionLocalProvider(
                    LocalContentColor provides MomentsChromeGlass.contentColor(isDark),
                ) {
                    MaskedBannerAvatar(notification = notification, isSystem = isSystem, isDark = isDark)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        lines.headline?.let { headline ->
                            Text(
                                text = headline,
                                color = LocalContentColor.current,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = headlineSp,
                                maxLines = 1,
                            )
                        }
                        when {
                            lines.detail != null -> Text(
                                text = lines.detail,
                                color = LocalContentColor.current.copy(alpha = 0.72f),
                                fontWeight = FontWeight.Medium,
                                fontSize = detailSp,
                                maxLines = 2,
                            )
                            isSystemModerationBanner(notification) -> Text(
                                text = moderationBannerText(notification),
                                color = LocalContentColor.current.copy(alpha = 0.92f * 0.72f),
                                fontWeight = FontWeight.Medium,
                                fontSize = detailSp,
                                maxLines = 2,
                            )
                        }
                    }

                    // iOS: solo preview de media a la derecha; el tipo va en cutout del avatar.
                    if (!isSystem && !contentPreviewImage.isNullOrBlank()) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(contentPreviewImage)
                                .transformations(
                                    com.moments.android.views.creator.creatoruikit.feedCropTransformations(
                                        contentPreviewFeedCrop,
                                    ),
                                )
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp)),
                        )
                    }
                }
            }
        }
    }
}

private data class BannerTextLines(val headline: String?, val detail: String?)

private fun bannerTextLines(
    copy: NotificationBannerCopy,
    notification: MomentsNotification,
): BannerTextLines {
    if (com.moments.android.services.messaging.GroupChatScope.isGroup(notification.conversationId)) {
        return BannerTextLines(copy.title, copy.body)
    }
    val name = notification.senderUsername
    if (isSystemTimeLimitBanner(notification)) {
        return BannerTextLines(copy.title, copy.body)
    }
    if (notification.type == NotificationType.GENTLE_REMINDER) {
        return BannerTextLines(copy.title, copy.body)
    }
    // Título (frase) + subtitle (CTA en notification_mutual_connection_body).
    if (notification.type == NotificationType.MUTUAL_CONNECTION) {
        return BannerTextLines(copy.title, copy.body)
    }
    if (notification.type == NotificationType.ECHO_SUGGESTION) {
        val sentence = copy.body?.trim().orEmpty()
        return BannerTextLines(null, sentence.ifEmpty { copy.title })
    }
    val body = copy.body?.trim().orEmpty()
    if (body.isNotEmpty()) {
        return if (body.startsWith(name)) {
            BannerTextLines(null, body)
        } else {
            BannerTextLines(name, body)
        }
    }
    if (copy.title != name) {
        return BannerTextLines(name, copy.title)
    }
    return BannerTextLines(name, null)
}

@Composable
private fun MaskedBannerAvatar(
    notification: MomentsNotification,
    isSystem: Boolean,
    isDark: Boolean,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // ≡ iOS reversedMask: Circle 18×18 offset(+2,+2) bottomTrailing
                    val cut = with(density) { 18.dp.toPx() }
                    val shift = with(density) { 2.dp.toPx() }
                    drawCircle(
                        color = Color.Black,
                        radius = cut / 2f,
                        center = Offset(size.width - cut / 2f + shift, size.height - cut / 2f + shift),
                        blendMode = BlendMode.Clear,
                    )
                },
        ) {
            BannerAvatarCore(notification = notification, isSystem = isSystem, isDark = isDark)
        }
        Box(
            modifier = Modifier
                .size(14.dp)
                .offset(x = 2.dp, y = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            BannerAvatarCutoutGlyph(notification)
        }
    }
}

@Composable
private fun BannerAvatarCore(
    notification: MomentsNotification,
    isSystem: Boolean,
    isDark: Boolean,
) {
    if (isSystem) {
        SystemBannerAvatar(notification = notification, isDark = isDark)
    } else if (com.moments.android.services.messaging.GroupChatScope.isGroup(notification.conversationId)) {
        com.moments.android.views.messaging.groups.GroupChatAvatar(
            image = notification.groupImage.orEmpty(),
            size = 34.dp,
        )
    } else {
        AsyncProfileImageView(
            userId = notification.senderId,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
        )
    }
}

@Composable
private fun BannerAvatarCutoutGlyph(notification: MomentsNotification) {
    val reactionGlyph = momentReactionGlyph(notification)
    when {
        reactionGlyph != null -> Text(
            text = reactionGlyph,
            fontSize = with(LocalDensity.current) { 13.dp.toSp() },
            maxLines = 1,
        )
        usesCustomCutout(notification) -> BannerCustomCutoutIcon(notification)
        else -> Icon(
            imageVector = trailingSystemIcon(notification),
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.72f),
            modifier = Modifier.size(11.dp),
        )
    }
}

private fun usesCustomCutout(notification: MomentsNotification): Boolean {
    if (isSystemTimeLimitBanner(notification) || isSystemModerationBanner(notification)) return false
    return when (notification.type) {
        NotificationType.PHOTO_TAG,
        NotificationType.MUTUAL_CONNECTION,
        NotificationType.CHAT_BUZZ,
        NotificationType.ECHO_SUGGESTION,
        -> true
        else -> false
    }
}

@Composable
private fun BannerCustomCutoutIcon(notification: MomentsNotification) {
    val tint = LocalContentColor.current.copy(alpha = 0.72f)
    when (notification.type) {
        NotificationType.PHOTO_TAG -> AttachmentIconView(
            icon = AttachmentIcon.TAGGED,
            preset = AttachmentIconPreset.IN_APP_BANNER,
            tintColor = tint,
        )
        NotificationType.MUTUAL_CONNECTION -> AttachmentIconView(
            icon = AttachmentIcon.MUTUALS,
            preset = AttachmentIconPreset.IN_APP_BANNER,
            tintColor = tint,
        )
        NotificationType.CHAT_BUZZ -> AttachmentIconView(
            icon = AttachmentIcon.BUZZ,
            preset = AttachmentIconPreset.IN_APP_BANNER,
            tintColor = tint,
        )
        NotificationType.ECHO_SUGGESTION -> EchoesIconView(size = 15.dp, tintColor = tint)
        else -> Unit
    }
}

private fun momentReactionGlyph(notification: MomentsNotification): String? {
    if (notification.type != NotificationType.REACTION &&
        notification.type != NotificationType.STORY_REACTION
    ) {
        return null
    }
    val raw = notification.reaction?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return ReactionType.fromRaw(raw)?.icon ?: raw
}

@Composable
private fun SystemBannerAvatar(notification: MomentsNotification, isDark: Boolean) {
    if (isSystemModerationBanner(notification)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(0.14f) else Color.Black.copy(0.1f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    if (isDark) R.drawable.splash_logo_light else R.drawable.splash_logo_dark,
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9500).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassFull,
                contentDescription = null,
                tint = Color(0xFFFF9500),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun trailingSystemIcon(notification: MomentsNotification): ImageVector = when {
    isSystemTimeLimitBanner(notification) -> Icons.Filled.HourglassFull
    isSystemModerationBanner(notification) -> Icons.Filled.Security
    else -> when (notification.type) {
        NotificationType.LIKE, NotificationType.REACTION -> Icons.Filled.Favorite
        NotificationType.COMMENT -> Icons.Filled.Comment
        NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ -> Icons.Filled.Chat
        NotificationType.NEW_FOLLOWER, NotificationType.FOLLOW_REQUEST, NotificationType.REQUEST_ACCEPTED -> Icons.Filled.Person
        NotificationType.ECHO_SUGGESTION -> Icons.Filled.Star
        else -> Icons.Filled.Notifications
    }
}

private fun isSystemTimeLimitBanner(notification: MomentsNotification): Boolean =
    notification.senderId == "system_time_limit"

private fun isSystemModerationBanner(notification: MomentsNotification): Boolean =
    notification.type == NotificationType.MEDIA_MODERATION

private fun isSystemBanner(notification: MomentsNotification): Boolean =
    isSystemTimeLimitBanner(notification) || isSystemModerationBanner(notification)

private suspend fun loadPreviewImage(notification: MomentsNotification): Pair<String?, com.moments.android.models.MediaItemFeedCrop?> {
    if (isSystemBanner(notification)) return null to null
    return withContext(Dispatchers.IO) {
        runCatching {
            when {
                notification.type == NotificationType.MENTION && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, storyAuthorId(notification)) to null
                notification.type in setOf(
                    NotificationType.LIKE,
                    NotificationType.COMMENT,
                    NotificationType.REACTION,
                    NotificationType.MENTION,
                ) && notification.momentId != null -> {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching null to null
                    val moment = FirestoreService().fetchMoment(notification.momentId, uid)
                    moment.previewImageURLString to moment.primaryVisibleMediaItem?.feedCrop
                }
                notification.type == NotificationType.STORY_REACTION && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, notification.storyAuthorId) to null
                notification.type == NotificationType.STORY_CHAIN_CONTINUED && notification.storyId != null ->
                    fetchStoryPreview(notification.storyId, notification.senderId) to null
                else -> null to null
            }
        }.getOrNull() ?: (null to null)
    }
}

private suspend fun fetchStoryPreview(storyId: String, authorId: String?): String? {
    val userId = authorId ?: return null
    val snap = FirebaseFirestore.getInstance()
        .collection("users").document(userId)
        .collection("stories").document(storyId)
        .get().await()
    @Suppress("UNCHECKED_CAST")
    val mediaItem = snap.data?.get("mediaItem") as? Map<String, Any?> ?: return null
    val thumbnail = mediaItem["thumbnailUrl"] as? String
    if (!thumbnail.isNullOrBlank()) return thumbnail
    return mediaItem["url"] as? String
}

private fun storyAuthorId(notification: MomentsNotification): String? {
    if (notification.type == NotificationType.STORY_REACTION) {
        return notification.storyAuthorId
            ?: notification.targetAuthorId
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: notification.senderId
    }
    return notification.storyAuthorId ?: notification.targetAuthorId ?: notification.senderId
}

private fun routeBannerTap(notification: MomentsNotification, context: android.content.Context) {
    when (notification.type) {
        NotificationType.MESSAGE ->
            notification.conversationId?.let(NotificationNavigationService::navigateToConversation)
        NotificationType.MESSAGE_REACTION -> notification.conversationId?.let { conversationId ->
            notification.messageId?.let { ChatNavigationIntentStore.enqueueHighlight(conversationId, it) }
            NotificationNavigationService.navigateToConversation(conversationId)
        }
        NotificationType.CHAT_BUZZ -> notification.conversationId?.let { conversationId ->
            ChatNavigationIntentStore.enqueueBuzz(conversationId, notification.buzzEventId)
            NotificationNavigationService.navigateToConversation(conversationId)
        }
        NotificationType.DATA_EXPORT_READY -> notification.downloadURL?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        else -> NotificationNavigationService.navigateToNotifications(notificationsFilter(notification.type))
    }
}

private fun notificationsFilter(type: NotificationType): String? = when (type) {
    NotificationType.FOLLOW_REQUEST, NotificationType.REQUEST_ACCEPTED -> "requests"
    NotificationType.REACTION -> "reactions"
    NotificationType.COMMENT -> "comments"
    NotificationType.STORY_REACTION -> "stories"
    NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION -> "follows"
    else -> null
}

private fun colorFor(type: NotificationType): Color = when (type) {
    NotificationType.LIKE -> Color(0xFFFF3B30) // .red
    NotificationType.REACTION, NotificationType.MESSAGE_REACTION -> Color(0xFFAF52DE) // .purple
    NotificationType.COMMENT -> Color(0xFF007AFF) // .blue
    NotificationType.NEW_FOLLOWER -> Color(0xFF34C759) // .green
    NotificationType.STORY_CHAIN_CONTINUED -> Color(0xFF5856D6) // .indigo
    NotificationType.ECHO_SUGGESTION, NotificationType.MEDIA_MODERATION -> Color(0xFFFF9500) // .orange
    NotificationType.CHAT_BUZZ -> Color(0xFF32ADE6) // .cyan
    NotificationType.GENTLE_REMINDER -> Color(0xFF00C7BE) // .mint
    else -> Color.Gray
}

@Composable
private fun moderationBannerText(notification: MomentsNotification): String {
    notification.message?.takeIf { it.isNotEmpty() }?.let { return it }
    val moderationType = notification.reaction ?: "partial"
    val scope = notification.moderationScope ?: "post"
    return when (scope) {
        "storySticker" -> stringResource(R.string.banner_verb_media_moderation_story_sticker_partial)
        "postHiddenLayer" -> stringResource(R.string.banner_verb_media_moderation_post_hidden_layer_partial)
        "story" -> if (moderationType == "full") {
            stringResource(R.string.banner_verb_media_moderation_story_full)
        } else {
            stringResource(R.string.banner_verb_media_moderation_story_partial)
        }
        else -> stringResource(R.string.banner_verb_media_moderation_partial)
    }
}
