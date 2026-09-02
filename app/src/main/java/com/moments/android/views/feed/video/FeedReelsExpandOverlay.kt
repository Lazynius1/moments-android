package com.moments.android.views.feed.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.services.performance.VideoMoment
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Sesión Reels abierta desde un post del feed.
 * iOS: `fullScreenCover` + `matchedTransitionSource` / `.navigationTransition(.zoom)`.
 */
data class FeedReelsPresentation(
    val videos: List<VideoMoment>,
    val startIndex: Int,
    val startSeconds: Double,
    val sourceRectInWindow: Rect,
    val handoffConsumerId: String? = null,
    val onClosed: () -> Unit,
)

class FeedReelsHostState {
    var presentation by mutableStateOf<FeedReelsPresentation?>(null)
        private set

    fun present(presentation: FeedReelsPresentation) {
        this.presentation = presentation
    }

    fun clear() {
        presentation = null
    }
}

val LocalFeedReelsHost = staticCompositionLocalOf<FeedReelsHostState?> { null }

@Composable
fun FeedReelsHostOverlay() {
    val host = LocalFeedReelsHost.current ?: return
    val presentation = host.presentation
    FeedReelsExpandOverlay(
        visible = presentation != null,
        sourceRectInWindow = presentation?.sourceRectInWindow ?: Rect.Zero,
        onDismissed = {
            val closed = host.presentation?.onClosed
            host.clear()
            closed?.invoke()
        },
    ) { collapse ->
        val session = presentation ?: return@FeedReelsExpandOverlay
        ReelsViewer(
            videos = session.videos,
            startIndex = session.startIndex,
            initialStartSeconds = session.startSeconds,
            handoffConsumerId = session.handoffConsumerId,
            onClose = collapse,
        )
    }
}

/**
 * Feed → Reels en la misma ventana: Reels se escala desde el post (sin relayout).
 * No cubre la barra de estado (como IG). No usa `isImmersive` (eso es el peek).
 */
@Composable
fun FeedReelsExpandOverlay(
    visible: Boolean,
    sourceRectInWindow: Rect,
    onDismissed: () -> Unit,
    content: @Composable (collapse: () -> Unit) -> Unit,
) {
    if (!visible) return

    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }
    val capturedSource = remember(visible) { sourceRectInWindow }
    val from = remember(overlayOrigin) {
        val origin = overlayOrigin ?: return@remember null
        val local = Rect(
            capturedSource.left - origin.x,
            capturedSource.top - origin.y,
            capturedSource.right - origin.x,
            capturedSource.bottom - origin.y,
        )
        if (local.width > 8f && local.height > 8f) local else null
    }

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(from) {
        if (from == null) return@LaunchedEffect
        progress.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val collapse: () -> Unit = {
        scope.launch {
            progress.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            onDismissed()
        }
    }

    BackHandler(onBack = collapse)

    val t = progress.value
    val originRect = from
    val corner = lerp(20f, 0f, t)

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(24f)
            .statusBarsPadding()
            .onGloballyPositioned { coords ->
                if (overlayOrigin == null) {
                    overlayOrigin = coords.positionInWindow()
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * t)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (originRect != null && size.width > 0f && size.height > 0f) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        // Ventana: de la card al fullscreen (puede ser no uniforme).
                        scaleX = lerp(originRect.width / size.width, 1f, t)
                        scaleY = lerp(originRect.height / size.height, 1f, t)
                        translationX = lerp(originRect.left, 0f, t)
                        translationY = lerp(originRect.top, 0f, t)
                        clip = true
                        shape = RoundedCornerShape(corner.dp)
                    } else {
                        alpha = 0f
                    }
                }
                .background(Color.Black),
        ) {
            // Contenido a escala uniforme (cover): el vídeo no se aplasta.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (originRect != null && size.width > 0f && size.height > 0f) {
                            val sx = lerp(originRect.width / size.width, 1f, t).coerceAtLeast(0.001f)
                            val sy = lerp(originRect.height / size.height, 1f, t).coerceAtLeast(0.001f)
                            val cover = max(sx, sy)
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            scaleX = cover / sx
                            scaleY = cover / sy
                        }
                    },
            ) {
                content(collapse)
            }
        }
    }
}
