package com.moments.android.views.shared

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

private val LocalMomentsSheetDragModifier = staticCompositionLocalOf<Modifier> { Modifier }

/**
 * Bottom sheet propio, con los mismos detents y arrastre que los comentarios.
 *
 * El panel nace siempre del borde inferior y aumenta su propia altura de medium a
 * large: no se convierte en una pantalla completa ni deja que Material recalcule
 * los detents al aparecer el teclado.
 */
@Composable
fun MomentsModalSheet(
    onDismissRequest: () -> Unit,
    largeOnly: Boolean = false,
    containerColor: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    showDragHandle: Boolean = true,
    scrimColor: Color? = null,
    /** false bloquea swipe-to-dismiss y tap en scrim. */
    dismissEnabled: Boolean = true,
    /** false mantiene el detent medium y rechaza el salto a large. */
    allowExpanded: Boolean = true,
    /** Promueve temporalmente al detent large cuando aparece el teclado. */
    expandOnIme: Boolean = false,
    /** Límite superior del sheet; siempre deja una franja de la vista de fondo visible. */
    expandedHeightFraction: Float? = null,
    /** Posición Y real del borde superior; permite coordinar la vista de fondo. */
    onSheetOffsetChanged: ((Float) -> Unit)? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val canvas = if (containerColor == Color.Unspecified) {
        rememberAdaptiveColors().surfaceBackground
    } else {
        containerColor
    }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat().coerceAtLeast(1f)
    var imeVisible by remember { mutableStateOf(false) }
    val mediumFraction = 0.72f
    // .96 coincide con large de comentarios: visible, pero nunca fullscreen.
    val requestedLargeFraction = expandedHeightFraction ?: 0.96f
    val largeFraction = requestedLargeFraction.coerceIn(mediumFraction, 0.96f)
    val initialFraction = if (largeOnly) largeFraction else mediumFraction
    var selectedFraction by remember { mutableFloatStateOf(initialFraction) }
    var isDragging by remember { mutableStateOf(false) }
    var dragHeightPx by remember { mutableStateOf<Float?>(null) }
    var dragStartHeightPx by remember { mutableFloatStateOf(0f) }
    var dragTravelPx by remember { mutableFloatStateOf(0f) }
    var isClosing by remember { mutableStateOf(false) }
    val animatedHeightPx = remember { Animatable(0f) }
    val renderedHeightPx = (dragHeightPx ?: animatedHeightPx.value).coerceAtLeast(0f)

    LaunchedEffect(windowHeightPx) {
        if (animatedHeightPx.value == 0f) {
            animatedHeightPx.animateTo(windowHeightPx * initialFraction, modalSheetSpring())
        }
    }
    LaunchedEffect(imeVisible, windowHeightPx, selectedFraction, isDragging, isClosing) {
        if (isDragging || isClosing) return@LaunchedEffect
        val targetFraction = when {
            imeVisible && expandOnIme && allowExpanded -> largeFraction
            else -> selectedFraction
        }
        animatedHeightPx.animateTo(windowHeightPx * targetFraction, modalSheetSpring())
    }
    LaunchedEffect(onSheetOffsetChanged, windowHeightPx) {
        val observer = onSheetOffsetChanged ?: return@LaunchedEffect
        snapshotFlow {
            (windowHeightPx - (dragHeightPx ?: animatedHeightPx.value)).coerceAtLeast(0f)
        }.collect(observer)
    }

    fun dismissAnimated() {
        if (!dismissEnabled || isClosing) return
        isClosing = true
        focusManager.clearFocus(force = true)
        scope.launch {
            dragHeightPx = null
            animatedHeightPx.animateTo(0f, modalSheetSpring())
            onDismissRequest()
        }
    }

    val dragModifier = Modifier.pointerInput(
        windowHeightPx, largeOnly, allowExpanded, largeFraction, imeVisible,
    ) {
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
                val floor = windowHeightPx * initialFraction * 0.30f
                val ceiling = windowHeightPx * when {
                    largeOnly -> largeFraction
                    allowExpanded -> largeFraction
                    else -> mediumFraction
                }
                dragHeightPx = modalSheetRubberBand(proposed, floor, ceiling, windowHeightPx)
            },
            onDragCancel = {
                val current = dragHeightPx ?: animatedHeightPx.value
                dragHeightPx = null
                isDragging = false
                scope.launch {
                    animatedHeightPx.snapTo(current)
                    animatedHeightPx.animateTo(windowHeightPx * selectedFraction, modalSheetSpring())
                }
            },
            onDragEnd = {
                val current = dragHeightPx ?: animatedHeightPx.value
                dragHeightPx = null
                isDragging = false
                scope.launch {
                    animatedHeightPx.snapTo(current)
                    val mediumHeight = windowHeightPx * initialFraction
                    if (current < mediumHeight * 0.55f && dismissEnabled) {
                        dismissAnimated()
                    } else if (!largeOnly && allowExpanded && current >=
                        (windowHeightPx * (mediumFraction + largeFraction) / 2f)
                    ) {
                        selectedFraction = largeFraction
                        animatedHeightPx.animateTo(windowHeightPx * largeFraction, modalSheetSpring())
                    } else {
                        selectedFraction = initialFraction
                        animatedHeightPx.animateTo(windowHeightPx * initialFraction, modalSheetSpring())
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
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(view) {
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
                    .background(scrimColor ?: Color.Black.copy(alpha = 0.32f))
                    .clickable(enabled = dismissEnabled, onClick = ::dismissAnimated),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { renderedHeightPx.toDp() })
                    .shadow(24.dp, shape, clip = false),
                color = canvas,
                shape = shape,
                tonalElevation = 0.dp,
            ) {
                // El canvas puede llegar al borde inferior, pero el contenido no
                // debe quedar por debajo de la barra/botones de navegación.
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    if (showDragHandle) ModalSheetDragHandle(dragModifier)
                    CompositionLocalProvider(LocalMomentsSheetDragModifier provides dragModifier) {
                        content(::dismissAnimated)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onSheetOffsetChanged?.invoke(windowHeightPx) }
    }
}

/**
 * Cabecera compacta de sheet: título centrado pegado al drag handle.
 * Sin botón chevron/cerrar — dismiss = swipe / scrim.
 */
@Composable
fun MomentsSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleSize: TextUnit = 16.sp,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = rememberAdaptiveColors()
    val dragModifier = LocalMomentsSheetDragModifier.current
    Box(
        modifier.then(dragModifier)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleSize,
                color = colors.primary,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                trailing()
            }
        }
    }
}

@Composable
private fun ModalSheetDragHandle(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .fillMaxWidth()
            // 18.dp de hit-area arriba; el pill va abajo para pegar el título.
            .height(18.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .padding(bottom = 2.dp)
                .size(width = 36.dp, height = 5.dp)
                .background(colors.primary.copy(alpha = 0.25f), CircleShape),
        )
    }
}

private fun modalSheetSpring() = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = Spring.StiffnessMediumLow,
)

private fun modalSheetRubberBand(
    proposed: Float,
    lowerBound: Float,
    upperBound: Float,
    dimension: Float,
): Float = when {
    proposed < lowerBound -> lowerBound - modalSheetRubberBandDistance(lowerBound - proposed, dimension)
    proposed > upperBound -> upperBound + modalSheetRubberBandDistance(proposed - upperBound, dimension)
    else -> proposed
}

private fun modalSheetRubberBandDistance(distance: Float, dimension: Float): Float {
    val coefficient = 0.34f
    return (distance * coefficient * dimension) / (dimension + coefficient * distance)
}
