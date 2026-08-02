package com.moments.android.views.permission.camera.helpers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.permissionMockOverflowSize

/** Port de `CameraPermissionsview.swift`. */
@Composable
fun CameraPermissionsView(
    title: String,
    description: String,
    primaryActionTitle: String,
    secondaryActionTitle: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF00A896),
    showsShutterUI: Boolean = false,
    isDenied: Boolean = false,
    primaryAction: () -> Unit,
    secondaryAction: () -> Unit,
    panorama: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (dark) Color.White else Color.Black.copy(alpha = 0.88f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(canvas)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // weight(1f): el mock cabe en el espacio restante; botones siempre visibles abajo
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            CameraPermissionAnimation(
                isDenied = isDenied,
                showsShutterUI = showsShutterUI,
                panorama = panorama,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.padding(bottom = 4.dp)) {
                Box(modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Default.CameraAlt,
                        null,
                        tint = content.copy(alpha = if (isDenied) .72f else 1f),
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isDenied) {
                        CameraDeniedSlash(content, Modifier.fillMaxSize())
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = content,
                            modifier = Modifier.align(Alignment.TopStart).size(12.dp),
                        )
                    } else {
                        val transition = rememberInfiniteTransition(label = "camera-chevron")
                        val offset = transition.animateFloat(
                            -5f,
                            0f,
                            infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
                            label = "camera-chevron-offset",
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            null,
                            tint = content,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(12.dp)
                                .graphicsLayer { translationY = offset.value },
                        )
                    }
                }
            }
            Text(
                title,
                color = content,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                description,
                color = content.copy(alpha = .62f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                lineHeight = 20.sp,
            )
            PermissionActionButton(
                primaryActionTitle,
                tint,
                Color.White,
                primaryAction,
                Modifier.padding(top = 8.dp),
            )
            Text(
                secondaryActionTitle,
                color = content.copy(alpha = .62f),
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = secondaryAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CameraPermissionAnimation(
    isDenied: Boolean,
    showsShutterUI: Boolean,
    panorama: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PermissionPhoneFrame(
            screenBackground = Color.Black,
            animated = !isDenied,
            showsIslandIndicators = !isDenied,
            appliesDeniedChrome = isDenied,
            screen = { phoneSize, motion ->
                val density = LocalDensity.current
                val panRangePx = with(density) { phoneSize.width.toPx() }
                // ≡ iOS: panorama width*3, offset x: -progress * width (overflow real)
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Box(
                        Modifier
                            .permissionMockOverflowSize(
                                width = phoneSize.width * 3f,
                                height = phoneSize.height,
                                alignment = Alignment.Center,
                            )
                            .graphicsLayer {
                                translationX = if (isDenied) 0f else -motion.progress * panRangePx
                            },
                    ) {
                        Box(Modifier.fillMaxSize()) { panorama() }
                    }
                    if (showsShutterUI) {
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(phoneSize.height * 0.17f)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(
                                Modifier
                                    .size(phoneSize.height * 0.05f)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.65f)),
                            )
                            Spacer(
                                Modifier
                                    .size(
                                        width = phoneSize.height * 0.2f,
                                        height = phoneSize.height * 0.1f,
                                    )
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Spacer(
                                Modifier
                                    .size(phoneSize.height * 0.05f)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.65f)),
                            )
                        }
                    }
                }
            },
            island = { ratio, _ ->
                // privacy cam green dot (≡ iOS island green)
                Box(
                    Modifier
                        .size((10f * ratio).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759))
                        .offset(x = (6f * ratio).dp),
                )
            },
        )
    }
}

@Composable
private fun CameraDeniedSlash(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val inset = size.minDimension * .12f
    drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), strokeWidth = 3.5.dp.toPx())
}

@Composable
private fun PermissionActionButton(text: String, tint: Color, textColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(100)).background(tint).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
