package com.moments.android.views.messaging.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay

private val TrashCircleSize = 44.dp
private val TrashExpandedCircleSize = 62.dp
private val TrashAnimationCanvasSize = 78.dp
private const val TrashAnimationContentScale = 1.55f

/** Círculo de vidrio separado (fuera del input): solo papelera al cancelar. ≡ iOS. */
@Composable
fun VoiceRecordingTrashIndicator(
    morphProgress: Float,
    morphOffsetX: Dp,
    onLottieFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = MotionPolicy.reduceMotion
    var didFinishLottie by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) isExpanded = true
    }

    val animatedMorphProgress by animateFloatAsState(
        targetValue = morphProgress,
        animationSpec = if (reduceMotion) tween(0) else tween(200),
        label = "voiceTrashMorphProgress",
    )
    val density = LocalDensity.current
    val morphOffsetPx = with(density) { morphOffsetX.toPx() * animatedMorphProgress }
    val morphScale = maxOf(0.001f, 1f - animatedMorphProgress * 0.999f)
    val morphAlpha = 1f - animatedMorphProgress
    val animatedCircleSize by animateDpAsState(
        targetValue = if (isExpanded) TrashExpandedCircleSize else TrashCircleSize,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 0.76f, stiffness = 500f)
        },
        label = "voiceTrashCircleExpansion",
    )

    Box(
        modifier
            .size(TrashCircleSize)
            .graphicsLayer {
                translationX = morphOffsetPx
                scaleX = morphScale
                scaleY = morphScale
                alpha = morphAlpha
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(animatedCircleSize)
                .zIndex(0f)
                .momentsChromeGlass(CircleShape, interactive = false),
        )

        if (reduceMotion) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier
                    .size(22.dp)
                    .zIndex(1f),
            )
            LaunchedEffect(Unit) {
                if (!didFinishLottie) {
                    didFinishLottie = true
                    onLottieFinished()
                }
            }
        } else {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(R.raw.chat_voice_record_trash),
            )
            val lottieProgress by animateLottieCompositionAsState(
                composition = composition,
                iterations = 1,
                isPlaying = composition != null,
                speed = 2f,
            )
            LottieAnimation(
                composition = composition,
                progress = { lottieProgress },
                modifier = Modifier
                    .requiredSize(TrashAnimationCanvasSize)
                    .graphicsLayer {
                        scaleX = TrashAnimationContentScale
                        scaleY = TrashAnimationContentScale
                    }
                    .zIndex(1f),
            )
            LaunchedEffect(lottieProgress, composition) {
                if (composition != null && lottieProgress >= 0.99f && !didFinishLottie) {
                    didFinishLottie = true
                    onLottieFinished()
                }
            }
        }
    }
}
