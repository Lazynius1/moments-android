package com.moments.android.views.shared.tabbar

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Orden del deslizamiento entre pestañas (➕ fuera); el creador queda a la izquierda de Inicio. */
object MomentsTabPages {
    const val CREATOR = -1
    val order = listOf(0, 1, 3, 4)
}

enum class MomentsTabSwipeKind { None, Next, Previous, Creator }

private enum class MomentsTabSwipeDirection {
    /** Dedo hacia la izquierda. */
    Next,

    /** Dedo hacia la derecha. */
    Previous,
}

@Stable
class MomentsTabSwipeState {
    var kind by mutableStateOf(MomentsTabSwipeKind.None)
        internal set
    var dragX by mutableFloatStateOf(0f)
        internal set
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        internal set
    var isCreatorOpen by mutableStateOf(false)
        internal set

    /** El creador está en su pantalla inicial (o la cámara de historias): se puede volver con el gesto. */
    var creatorAtRoot by mutableStateOf(true)

    /** Durante el gesto la pestaña cambia sin la animación de la navegación. */
    val changesTabInstantly: Boolean get() = kind == MomentsTabSwipeKind.Next || kind == MomentsTabSwipeKind.Previous

    internal var width by mutableFloatStateOf(1f)
    internal var fromTab = 0
    internal var pendingEnd: Pair<Float, Float>? = null

    val isCreatorMounted: Boolean get() = isCreatorOpen || kind == MomentsTabSwipeKind.Creator

    /** 0 Inicio a la vista, 1 creador a la vista. */
    val creatorReveal: Float by derivedStateOf {
        if (!isCreatorMounted) return@derivedStateOf 0f
        val base = if (isCreatorOpen) 1f else 0f
        if (kind != MomentsTabSwipeKind.Creator) base else (base + dragX / width).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberMomentsTabSwipeState(): MomentsTabSwipeState = remember { MomentsTabSwipeState() }

/** El dock se desvanece al asomar el creador y desaparece con él abierto. */
fun Modifier.momentsTabBarSwipeFade(state: MomentsTabSwipeState): Modifier =
    graphicsLayer { alpha = 1f - (state.creatorReveal * 1.6f).coerceAtMost(1f) }

private const val PARALLAX = 1f / 3f
private const val DIMMING = 0.22f
private const val COMMIT_FRACTION = 0.3f
private const val HORIZONTAL_DOMINANCE = 1.3f
private val releaseSpring = spring<Float>(dampingRatio = 1f, stiffness = 380f)

/**
 * Deslizar entre pestañas y hacia el creador, apilado como la navegación del sistema:
 * la página nueva entra por encima con las esquinas del dispositivo y sombra en el borde,
 * y la de debajo se desplaza más despacio y se oscurece.
 *
 * Solo desde la raíz de cada pestaña ([canSwipeTabs]). Las listas horizontales de dentro
 * tienen prioridad: si consumen el gesto, no se cambia de pestaña.
 */
@Composable
fun MomentsTabSwipeContainer(
    state: MomentsTabSwipeState,
    selectedTab: Int,
    canSwipeTabs: Boolean,
    onSelectTabInstantly: (Int) -> Unit,
    creator: @Composable (close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val contentLayer = rememberGraphicsLayer()
    val view = LocalView.current
    val density = LocalDensity.current
    val shadowWidth = with(density) { 14.dp.toPx() }
    val deviceCorners: Shape = remember(view) {
        val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        } else {
            0
        }
        if (radius > 0) RoundedCornerShape(radius.toFloat()) else RectangleShape
    }

    val currentSelectedTab by rememberUpdatedState(selectedTab)
    val currentCanSwipeTabs by rememberUpdatedState(canSwipeTabs)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSelect by rememberUpdatedState(onSelectTabInstantly)

    fun neighbor(direction: MomentsTabSwipeDirection): Int? {
        val index = MomentsTabPages.order.indexOf(currentSelectedTab)
        if (index < 0) return null
        val target = index + if (direction == MomentsTabSwipeDirection.Next) 1 else -1
        return MomentsTabPages.order.getOrNull(target)
    }

    fun canBegin(direction: MomentsTabSwipeDirection): Boolean {
        if (!currentEnabled || state.kind != MomentsTabSwipeKind.None) return false
        if (state.isCreatorOpen) return direction == MomentsTabSwipeDirection.Next && state.creatorAtRoot
        if (!currentCanSwipeTabs) return false
        if (direction == MomentsTabSwipeDirection.Previous && currentSelectedTab == 0) return true
        return neighbor(direction) != null
    }

    fun finish() {
        state.kind = MomentsTabSwipeKind.None
        state.dragX = 0f
        state.snapshot = null
        state.pendingEnd = null
    }

    fun changed(translation: Float) {
        state.dragX = when (state.kind) {
            MomentsTabSwipeKind.Next -> translation.coerceAtMost(0f)
            MomentsTabSwipeKind.Previous -> translation.coerceAtLeast(0f)
            MomentsTabSwipeKind.Creator ->
                if (state.isCreatorOpen) translation.coerceAtMost(0f) else translation.coerceAtLeast(0f)
            MomentsTabSwipeKind.None -> 0f
        }
    }

    fun ended(translation: Float, velocity: Float) {
        changed(translation)
        val projected = state.dragX + velocity * 0.2f
        val towardsGesture = when (state.kind) {
            MomentsTabSwipeKind.Next -> projected < 0
            MomentsTabSwipeKind.Creator -> if (state.isCreatorOpen) projected < 0 else projected > 0
            else -> projected > 0
        }
        val commit = towardsGesture && abs(projected) > state.width * COMMIT_FRACTION
        val width = state.width

        when (state.kind) {
            MomentsTabSwipeKind.None -> Unit
            MomentsTabSwipeKind.Next, MomentsTabSwipeKind.Previous -> {
                // Aún sin foto: se termina en cuanto llegue.
                if (state.snapshot == null) {
                    state.pendingEnd = translation to velocity
                    return
                }
                val goal = if (!commit) 0f else if (state.kind == MomentsTabSwipeKind.Next) -width else width
                scope.launch {
                    animate(state.dragX, goal, velocity, releaseSpring) { value, _ -> state.dragX = value }
                    if (commit) {
                        HapticManager.shared.selection(view)
                    } else {
                        currentOnSelect(state.fromTab)
                        // La pestaña de origen se pinta bajo la foto antes de quitarla.
                        withFrameNanos { }
                        withFrameNanos { }
                    }
                    finish()
                }
            }
            MomentsTabSwipeKind.Creator -> {
                val opening = !state.isCreatorOpen
                val goal = if (!commit) 0f else if (opening) width else -width
                scope.launch {
                    animate(state.dragX, goal, velocity, releaseSpring) { value, _ -> state.dragX = value }
                    if (commit) {
                        if (opening) HapticManager.shared.mediumImpact(view)
                        state.isCreatorOpen = opening
                    }
                    finish()
                }
            }
        }
    }

    fun began(direction: MomentsTabSwipeDirection) {
        state.dragX = 0f
        state.pendingEnd = null
        if (state.isCreatorOpen || (direction == MomentsTabSwipeDirection.Previous && currentSelectedTab == 0)) {
            state.kind = MomentsTabSwipeKind.Creator
            return
        }
        val target = neighbor(direction) ?: return
        state.fromTab = currentSelectedTab
        state.kind = if (direction == MomentsTabSwipeDirection.Next) {
            MomentsTabSwipeKind.Next
        } else {
            MomentsTabSwipeKind.Previous
        }
        scope.launch {
            state.snapshot = contentLayer.toImageBitmap()
            currentOnSelect(target)
            state.pendingEnd?.let { (translation, velocity) ->
                state.pendingEnd = null
                ended(translation, velocity)
            }
        }
    }

    /** La ✕ del creador: Inicio vuelve por encima, como al deslizar. */
    fun closeCreator() {
        if (!state.isCreatorOpen || state.kind != MomentsTabSwipeKind.None) return
        state.kind = MomentsTabSwipeKind.Creator
        state.dragX = 0f
        scope.launch {
            animate(0f, -state.width, animationSpec = releaseSpring) { value, _ -> state.dragX = value }
            state.isCreatorOpen = false
            finish()
        }
    }

    BackHandler(enabled = state.isCreatorOpen && state.kind == MomentsTabSwipeKind.None) {
        closeCreator()
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.width = it.width.toFloat().coerceAtLeast(1f) }
            .drawBehind { drawRect(Color.Black) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (state.kind != MomentsTabSwipeKind.None) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    var total = Offset.Zero
                    var direction: MomentsTabSwipeDirection? = null
                    // Las listas de dentro deciden primero: si se quedan el gesto, no es nuestro.
                    while (direction == null) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        total += change.positionChange()
                        if (abs(total.y) > slop && abs(total.y) * HORIZONTAL_DOMINANCE >= abs(total.x)) {
                            return@awaitEachGesture
                        }
                        if (abs(total.x) > slop && abs(total.x) > abs(total.y) * HORIZONTAL_DOMINANCE) {
                            direction = if (total.x < 0) MomentsTabSwipeDirection.Next else MomentsTabSwipeDirection.Previous
                        }
                    }
                    if (!canBegin(direction)) return@awaitEachGesture
                    began(direction)

                    val tracker = VelocityTracker()
                    var translation = 0f
                    // Desde aquí el gesto es nuestro: se consume antes que las listas de dentro.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        translation += change.positionChange().x
                        change.consume()
                        changed(translation)
                    }
                    ended(translation, tracker.calculateVelocity().x)
                }
            },
    ) {
        val width = state.width
        val kind = state.kind
        val progress = (abs(state.dragX) / width).coerceIn(0f, 1f)
        val reveal = state.creatorReveal
        val snapshot = state.snapshot

        if (state.isCreatorMounted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -width * PARALLAX * (1f - reveal) }
                    .dimmed(DIMMING * (1f - reveal)),
            ) {
                creator(::closeCreator)
                if (!state.isCreatorOpen || kind != MomentsTabSwipeKind.None) TouchBlocker()
            }
        }

        if (kind == MomentsTabSwipeKind.Next && snapshot != null) {
            SnapshotImage(
                snapshot,
                Modifier
                    .graphicsLayer { translationX = state.dragX * PARALLAX }
                    .dimmed(DIMMING * progress),
            )
        }

        val contentIsOnTop = when (kind) {
            MomentsTabSwipeKind.Next -> snapshot != null
            MomentsTabSwipeKind.Creator -> true
            MomentsTabSwipeKind.None -> state.isCreatorOpen
            MomentsTabSwipeKind.Previous -> false
        }
        val contentOffset = when (kind) {
            MomentsTabSwipeKind.None -> if (state.isCreatorOpen) width else 0f
            MomentsTabSwipeKind.Creator -> reveal * width
            // La pestaña nueva entra desde la derecha (aún sin foto: quieta).
            MomentsTabSwipeKind.Next -> if (snapshot == null) 0f else (width + state.dragX).coerceAtLeast(0f)
            // La pestaña anterior está debajo, más lenta.
            MomentsTabSwipeKind.Previous -> if (snapshot == null) 0f else -width * PARALLAX * (1f - progress)
        }
        val contentDimming = if (kind == MomentsTabSwipeKind.Previous && snapshot != null) {
            DIMMING * (1f - progress)
        } else {
            0f
        }
        Box(
            Modifier
                .fillMaxSize()
                .movingLayer(contentOffset, contentIsOnTop, deviceCorners, shadowWidth)
                .dimmed(contentDimming)
                // Lo que se pinta aquí es la foto que se toma al empezar a deslizar.
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                },
        ) {
            content()
            if (kind != MomentsTabSwipeKind.None || state.isCreatorOpen) TouchBlocker()
        }

        if (kind == MomentsTabSwipeKind.Previous && snapshot != null) {
            SnapshotImage(
                snapshot,
                Modifier.movingLayer(state.dragX, true, deviceCorners, shadowWidth),
            )
        }
    }
}

@Composable
private fun SnapshotImage(bitmap: ImageBitmap, modifier: Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.fillMaxSize(),
    )
}

/** Mientras algo se mueve, los toques no llegan a lo de debajo. */
@Composable
private fun TouchBlocker() {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    )
}

/** La capa que se mueve por encima lleva las esquinas del dispositivo y sombra en su borde izquierdo. */
private fun Modifier.movingLayer(offsetX: Float, isMoving: Boolean, corners: Shape, shadowWidth: Float): Modifier =
    graphicsLayer { translationX = offsetX }
        .drawBehind {
            if (!isMoving) return@drawBehind
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                    startX = -shadowWidth,
                    endX = 0f,
                ),
                topLeft = Offset(-shadowWidth, 0f),
                size = Size(shadowWidth, size.height),
            )
        }
        .graphicsLayer {
            clip = isMoving
            shape = if (isMoving) corners else RectangleShape
        }

private fun Modifier.dimmed(amount: Float): Modifier =
    drawWithContent {
        drawContent()
        if (amount > 0f) drawRect(Color.Black.copy(alpha = amount))
    }
