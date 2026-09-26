package com.moments.android.views.comments

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/**
 * Overlay de comentarios con detents propios, equivalente a
 * `ModernCommentsSheetOverlay.swift`.
 *
 * Material recalcula sus anchors contra el viewport del IME. Aquí la superficie
 * siempre nace del borde inferior y el teclado sólo modifica la altura objetivo
 * y el inset del footer.
 */
@Composable
fun ModernCommentsSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    keepBackgroundVisible: Boolean = false,
    locksToMedium: Boolean = false,
    onSheetOffsetChanged: ((Float) -> Unit)? = null,
    onOpenStory: (userId: String) -> Unit = { userId ->
        NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(userId))
    },
    onOpenProfile: (userId: String) -> Unit = { userId ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && uid == userId) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
        } else {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(userId))
        }
    },
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat().coerceAtLeast(1f)
    var imeVisible by remember { mutableStateOf(false) }

    // Los cinco estados de referencia de Instagram: detalle medium/large,
    // detalle+IME y Reels sin/con IME.
    val mediumFraction = if (locksToMedium) 0.64f else 0.72f
    val largeFraction = 0.96f
    val keyboardFraction = if (locksToMedium) 0.72f else largeFraction

    var selectedFraction by remember(moment.id) { mutableFloatStateOf(mediumFraction) }
    var isDragging by remember { mutableStateOf(false) }
    var dragHeightPx by remember { mutableStateOf<Float?>(null) }
    var dragStartHeightPx by remember { mutableFloatStateOf(0f) }
    var dragTravelPx by remember { mutableFloatStateOf(0f) }
    var isClosing by remember { mutableStateOf(false) }
    val animatedHeightPx = remember(moment.id) { Animatable(0f) }
    val renderedHeightPx = (dragHeightPx ?: animatedHeightPx.value).coerceAtLeast(0f)

    LaunchedEffect(windowHeightPx) {
        if (animatedHeightPx.value == 0f) {
            animatedHeightPx.animateTo(windowHeightPx * mediumFraction, sheetSpring())
        }
    }

    LaunchedEffect(imeVisible, windowHeightPx, selectedFraction, isDragging, isClosing) {
        if (isDragging || isClosing) return@LaunchedEffect
        if (imeVisible && !locksToMedium) selectedFraction = largeFraction
        if (!imeVisible && locksToMedium) selectedFraction = mediumFraction
        animatedHeightPx.animateTo(
            windowHeightPx * if (imeVisible) keyboardFraction else selectedFraction,
            sheetSpring(),
        )
    }

    LaunchedEffect(onSheetOffsetChanged, windowHeightPx) {
        val observer = onSheetOffsetChanged ?: return@LaunchedEffect
        snapshotFlow {
            (windowHeightPx - (dragHeightPx ?: animatedHeightPx.value)).coerceAtLeast(0f)
        }.collect(observer)
    }

    fun dismissAnimated() {
        if (isClosing) return
        isClosing = true
        focusManager.clearFocus(force = true)
        scope.launch {
            dragHeightPx = null
            animatedHeightPx.animateTo(0f, sheetSpring())
            onDismiss()
        }
    }

    val dragModifier = Modifier.pointerInput(moment.id, locksToMedium, windowHeightPx, imeVisible) {
        detectVerticalDragGestures(
            onDragStart = {
                isDragging = true
                dragTravelPx = 0f
                dragStartHeightPx = dragHeightPx ?: animatedHeightPx.value
                scope.launch { animatedHeightPx.stop() }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragTravelPx += dragAmount
                if (imeVisible && dragTravelPx > 4f) focusManager.clearFocus(force = true)
                val proposed = dragStartHeightPx - dragTravelPx
                val floor = windowHeightPx * mediumFraction * 0.30f
                val ceiling = windowHeightPx * if (locksToMedium) {
                    if (imeVisible) keyboardFraction else mediumFraction
                } else {
                    largeFraction
                }
                dragHeightPx = rubberBand(proposed, floor, ceiling, windowHeightPx)
            },
            onDragCancel = {
                val current = dragHeightPx ?: animatedHeightPx.value
                dragHeightPx = null
                isDragging = false
                scope.launch {
                    animatedHeightPx.snapTo(current)
                    animatedHeightPx.animateTo(windowHeightPx * selectedFraction, sheetSpring())
                }
            },
            onDragEnd = {
                val current = dragHeightPx ?: animatedHeightPx.value
                dragHeightPx = null
                isDragging = false
                scope.launch {
                    animatedHeightPx.snapTo(current)
                    val mediumHeight = windowHeightPx * mediumFraction
                    if (locksToMedium) {
                        if (current < mediumHeight * 0.62f) {
                            dismissAnimated()
                        } else {
                            selectedFraction = mediumFraction
                            animatedHeightPx.animateTo(mediumHeight, sheetSpring())
                        }
                    } else {
                        val largeHeight = windowHeightPx * largeFraction
                        if (current < mediumHeight * 0.55f) {
                            dismissAnimated()
                        } else if (current >= (mediumHeight + largeHeight) / 2f) {
                            selectedFraction = largeFraction
                            animatedHeightPx.animateTo(largeHeight, sheetSpring())
                        } else {
                            selectedFraction = mediumFraction
                            animatedHeightPx.animateTo(mediumHeight, sheetSpring())
                        }
                    }
                }
            },
        )
    }

    Dialog(
        onDismissRequest = ::dismissAnimated,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        val dialogImeVisible = WindowInsets.ime.getBottom(density) > 0
        LaunchedEffect(dialogImeVisible) { imeVisible = dialogImeVisible }
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window == null) return@DisposableEffect onDispose { }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setDimAmount(0f)
            val previousSoftInputMode = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose { window.setSoftInputMode(previousSoftInputMode) }
        }

        BackHandler(onBack = ::dismissAnimated)

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = if (keepBackgroundVisible) {
                                if (isDark) 0.45f else 0.28f
                            } else {
                                if (isDark) 0.52f else 0.34f
                            },
                        ),
                    )
                    .clickable(onClick = ::dismissAnimated),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { renderedHeightPx.toDp() })
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                        clip = false,
                    ),
                color = colors.surfaceBackground,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
            ) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    CommentsSheetDragHandle(dragModifier)
                    ModernCommentsView(
                        moment = moment,
                        onDismiss = ::dismissAnimated,
                        onOpenStory = onOpenStory,
                        onOpenProfile = onOpenProfile,
                        headerDragModifier = dragModifier,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onSheetOffsetChanged?.invoke(windowHeightPx) }
    }
}

@Composable
private fun CommentsSheetDragHandle(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    Box(
        modifier
            .fillMaxWidth()
            .height(25.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 5.dp)
                .background(
                    color = colors.primary.copy(alpha = if (isDark) 0.34f else 0.25f),
                    shape = CircleShape,
                ),
        )
    }
}

private fun sheetSpring() = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = Spring.StiffnessMediumLow,
)

private fun rubberBand(
    proposed: Float,
    lowerBound: Float,
    upperBound: Float,
    dimension: Float,
): Float = when {
    proposed < lowerBound -> lowerBound - rubberBandDistance(lowerBound - proposed, dimension)
    proposed > upperBound -> upperBound + rubberBandDistance(proposed - upperBound, dimension)
    else -> proposed
}

private fun rubberBandDistance(distance: Float, dimension: Float): Float {
    val coefficient = 0.34f
    return (distance * coefficient * dimension) / (dimension + coefficient * distance)
}
