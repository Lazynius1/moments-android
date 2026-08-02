package com.moments.android.views.permission.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import com.moments.android.R
import kotlinx.coroutines.launch
import kotlin.math.min as minFloat

data class PermissionPhoneMotion(
    val progress: Float = 0f,
    val reveal: Float = 0f,
)

/**
 * Dónde van los indicadores de privacidad en el mock Android
 * (equivalente semántico a Dynamic Island iOS).
 * - [INSIDE]: junto al punch-hole (cámara/mic).
 * - [LEADING]: a la izquierda del notch, junto al reloj (ubicación).
 */
enum class PermissionPhoneIslandPlacement { INSIDE, LEADING }

@Composable
fun PermissionPhoneStatusBarTime(
    ratio: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Text(
        text = stringResource(R.string.permission_phone_time),
        color = color,
        fontSize = (15f * ratio).sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/**
 * Port de `PermissionPhoneFrame.swift` adaptado a **chasis Android**
 * (punch-hole + gesture pill), conservando la secuencia de motion iOS:
 * hold → scale/reveal → sway −1/+1/0 → reset.
 *
 * Sway ≡ SpringKeyframe `.smooth(duration: 1)` → tween 1500ms ease suave
 * (sin spring rebotón que se lee brusco en Compose).
 */
@Composable
fun PermissionPhoneFrame(
    screenBackground: Color = Color.Black,
    animated: Boolean = true,
    islandPlacement: PermissionPhoneIslandPlacement = PermissionPhoneIslandPlacement.INSIDE,
    showsStatusBarTime: Boolean = true,
    showsIslandIndicators: Boolean = true,
    appliesDeniedChrome: Boolean = false,
    screen: @Composable BoxScope.(size: DpSize, motion: PermissionPhoneMotion) -> Unit,
    island: @Composable (ratio: Float, motion: PermissionPhoneMotion) -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val reveal = remember { Animatable(if (animated) 0f else 1f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(animated) {
        if (!animated) {
            scale.snapTo(1f)
            reveal.snapTo(1f)
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        // ≈ CubicBezier de KeyframeAnimator iOS (scale/reveal)
        val cubic = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        // ≈ .smooth(duration: 1, extraBounce: 0) dentro de slot 1.5s
        val smooth = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
        while (true) {
            scale.snapTo(1f)
            reveal.snapTo(0f)
            progress.snapTo(0f)
            // hold 0.5s
            kotlinx.coroutines.delay(500)
            // scale 0.95 + reveal 1 in 0.5s
            launch { scale.animateTo(0.95f, tween(500, easing = cubic)) }
            reveal.animateTo(1f, tween(500, easing = cubic))
            // hold 0.5s
            kotlinx.coroutines.delay(500)
            // sway −1 → +1 → 0 (1.5s cada tramo, smooth sin bounce)
            progress.animateTo(-1f, tween(1_500, easing = smooth))
            progress.animateTo(1f, tween(1_500, easing = smooth))
            progress.animateTo(0f, tween(1_500, easing = smooth))
            // reset (≡ CubicKeyframe duration 0) + hold 0.5s
            scale.snapTo(1f)
            reveal.snapTo(0f)
            progress.snapTo(0f)
            kotlinx.coroutines.delay(500)
        }
    }

    val motion = PermissionPhoneMotion(progress = progress.value, reveal = reveal.value)
    // Pixel / iPhone portrait — ≡ iOS 390/870 contentMode .fit
    val phoneRatio = 390f / 870f
    val corner = 36.dp
    val bezel = Color(0xFF1A1A1A)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Fit dentro del espacio disponible (no forzar ancho completo → altura infinita).
        val fittedWidth = min(maxWidth, maxHeight * phoneRatio)
        val fittedHeight = fittedWidth / phoneRatio
        val ratio = minFloat(
            (fittedWidth / 390.dp).coerceAtLeast(0.01f),
            (fittedHeight / 870.dp).coerceAtLeast(0.01f),
        )
        val density = LocalDensity.current
        val hole = 14.dp * ratio.coerceAtLeast(0.7f)
        val statusH = 28.dp * ratio.coerceAtLeast(0.7f)
        // Perspectiva más suave: cameraDistance bajo = rotación brusca
        val camDistance = 32f * density.density

        Box(
            modifier = Modifier
                .size(fittedWidth * 0.92f, fittedHeight * 0.92f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    // ≡ rotation3DEffect degrees(progress*15), axis y/z
                    rotationY = progress.value * 15f
                    rotationZ = progress.value * 3.75f
                    translationX = progress.value * with(density) { 80.dp.toPx() }
                    transformOrigin = TransformOrigin.Center
                    cameraDistance = camDistance
                    clip = true
                }
                .clip(RoundedCornerShape(corner))
                .background(bezel)
                .border(2.dp, Color(0xFF2C2C2C), RoundedCornerShape(corner)),
        ) {
            // Pantalla — size real del área útil
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .clip(RoundedCornerShape(corner - 2.dp))
                    .background(screenBackground),
            ) {
                val screenSize = DpSize(maxWidth, maxHeight)
                // Reveal desde abajo ≡ iOS offset y: height - height*reveal
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = size.height * (1f - reveal.value)
                            clip = true
                        }
                        .clip(RoundedCornerShape(corner - 2.dp))
                        .permissionMockDeniedChrome(appliesDeniedChrome),
                ) {
                    screen(screenSize, motion)
                }

                // Status bar Android
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusH)
                        .padding(horizontal = 14.dp * ratio.coerceAtLeast(0.7f))
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showsStatusBarTime) {
                        PermissionPhoneStatusBarTime(ratio = ratio.coerceAtLeast(0.85f))
                    }
                    if (showsIslandIndicators && islandPlacement == PermissionPhoneIslandPlacement.LEADING) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(14.dp * ratio.coerceAtLeast(0.7f))
                                .graphicsLayer { alpha = motion.reveal },
                        ) {
                            island(ratio.coerceAtLeast(0.7f), motion)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // punch-hole centrado visualmente (espacio reservado)
                    Spacer(Modifier.width(hole + 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (showsIslandIndicators && islandPlacement == PermissionPhoneIslandPlacement.INSIDE) {
                        Box(modifier = Modifier.size(hole)) {
                            island(ratio.coerceAtLeast(0.7f), motion)
                        }
                    } else {
                        Spacer(Modifier.width(hole))
                    }
                }

                // Punch-hole (cámara frontal)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp * ratio.coerceAtLeast(0.7f))
                        .size(hole)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF333333), CircleShape),
                )

                // Gesture pill Android
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .width(108.dp * ratio.coerceAtLeast(0.7f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}
