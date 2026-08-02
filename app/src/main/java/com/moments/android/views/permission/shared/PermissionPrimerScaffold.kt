package com.moments.android.views.permission.shared

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.MomentsChromeGlass

enum class PermissionPrimerStage { PRIMER, DENIED }

/** Port de `PermissionPrimerScaffold.swift`. */
@Composable
fun PermissionPrimerScaffold(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    icon: @Composable (Color) -> Unit,
    title: String,
    description: String,
    primaryActionTitle: String,
    secondaryActionTitle: String? = null,
    modifier: Modifier = Modifier,
    /** ≡ `.accentColor` */
    tint: Color = Color(0xFF007AFF),
    accent: (@Composable () -> Unit)? = null,
    primaryAction: () -> Unit,
    secondaryAction: (() -> Unit)? = null,
    phone: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val contentColor = MomentsChromeGlass.contentColor(dark)
    // ≈ SwiftUI `.secondary`
    val secondaryColor = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)
    val denied = stage == PermissionPrimerStage.DENIED
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    // ≡ iOS fullScreenCover + safe area: canvas edge-to-edge, content dentro de safeDrawing
    Box(modifier.fillMaxSize().background(canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // iOS VStack: contentBlock intrínseco abajo; phone .fit en el resto.
            // Column mide hijos sin weight primero → ContentBlock no se aplasta.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                phone()
            }

            ContentBlock(
                denied = denied,
                contentColor = contentColor,
                secondaryColor = secondaryColor,
                tint = tint,
                icon = icon,
                title = title,
                description = description,
                primaryActionTitle = primaryActionTitle,
                secondaryActionTitle = secondaryActionTitle,
                accent = accent,
                primaryAction = primaryAction,
                secondaryAction = secondaryAction,
            )
        }
    }
}

@Composable
private fun ContentBlock(
    denied: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    tint: Color,
    icon: @Composable (Color) -> Unit,
    title: String,
    description: String,
    primaryActionTitle: String,
    secondaryActionTitle: String?,
    accent: (@Composable () -> Unit)?,
    primaryAction: () -> Unit,
    secondaryAction: (() -> Unit)?,
) {
    // Compact vs iOS 80pt: Material icons leen más grandes → 52dp deja aire a los botones.
    Column(
        Modifier
            .widthIn(max = 330.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.size(52.dp)) {
                icon(contentColor.copy(alpha = if (denied) 0.72f else 1f))
                IconBadge(
                    denied = denied,
                    contentColor = contentColor,
                    accent = accent,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }

        Text(
            text = title,
            color = contentColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
        )

        Text(
            text = description,
            color = secondaryColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            lineHeight = 20.sp,
        )

        Button(
            onClick = primaryAction,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = tint,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = primaryActionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
        }

        if (secondaryActionTitle != null && secondaryAction != null) {
            Text(
                text = secondaryActionTitle,
                color = secondaryColor,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = secondaryAction,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun IconBadge(
    denied: Boolean,
    contentColor: Color,
    accent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        denied -> {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = contentColor,
                modifier = modifier.size(12.dp),
            )
        }
        accent != null -> {
            // ≡ keyframeAnimator: −5 → 0 (1s) → −5 (1s) → hold −5 (0.5s)
            val offsetY by rememberInfiniteTransition(label = "permission-accent")
                .animateFloat(
                    initialValue = -5f,
                    targetValue = -5f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 2_500
                            -5f at 0 using FastOutSlowInEasing
                            0f at 1_000 using FastOutSlowInEasing
                            -5f at 2_000 using FastOutSlowInEasing
                            -5f at 2_500
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "permission-accent-offset",
                )
            Box(
                modifier
                    .size(12.dp)
                    .graphicsLayer { translationY = offsetY },
            ) {
                accent()
            }
        }
    }
}

/**
 * ≡ `permissionMockDeniedChrome` — saturation(0.35) + brightness(−0.05) + overlay black 0.18.
 */
fun Modifier.permissionMockDeniedChrome(denied: Boolean): Modifier {
    if (!denied) return this
    return this.drawWithCache {
        val matrix = ColorMatrix().apply { setToSaturation(0.35f) }
        matrix.timesAssign(
            ColorMatrix(
                floatArrayOf(
                    0.95f, 0f, 0f, 0f, 0f,
                    0f, 0.95f, 0f, 0f, 0f,
                    0f, 0f, 0.95f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(matrix)
        }
        onDrawWithContent {
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
            drawRect(Color.Black.copy(alpha = 0.18f))
        }
    }
}
