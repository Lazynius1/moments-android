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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.views.permission.shared.PermissionPhoneStatusBarTime

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
        modifier = modifier.fillMaxSize().background(canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CameraPermissionAnimation(
            isDenied = isDenied,
            showsShutterUI = showsShutterUI,
            panorama = panorama,
            modifier = Modifier.padding(top = 15.dp, bottom = 25.dp).fillMaxWidth().weight(1f, fill = false),
        )
        Column(
            modifier = Modifier.widthIn(max = 330.dp).padding(horizontal = 24.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(80.dp).padding(bottom = 10.dp)) {
                Icon(Icons.Default.CameraAlt, null, tint = content.copy(alpha = if (isDenied) .72f else 1f), modifier = Modifier.fillMaxSize())
                if (isDenied) {
                    CameraDeniedSlash(content, Modifier.fillMaxSize())
                    Icon(Icons.Default.Lock, null, tint = content, modifier = Modifier.align(Alignment.TopStart).size(16.dp))
                } else {
                    val transition = rememberInfiniteTransition(label = "camera-chevron")
                    val offset = transition.animateFloat(-5f, 0f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse), label = "camera-chevron-offset")
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = content, modifier = Modifier.align(Alignment.TopStart).size(15.dp).graphicsLayer { translationY = offset.value })
                }
            }
            Text(title, color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(description, color = content.copy(alpha = .62f), fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4)
            PermissionActionButton(primaryActionTitle, tint, Color.White, primaryAction, Modifier.padding(top = 10.dp))
            Text(secondaryActionTitle, color = content.copy(alpha = .62f), fontSize = 14.sp, modifier = Modifier.clickable(onClick = secondaryAction).padding(top = 5.dp, bottom = 8.dp))
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
    val transition = rememberInfiniteTransition(label = "camera-permission-phone")
    val progress = if (isDenied) 0f else transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse), label = "camera-pan").value
    val scale = if (isDenied) 1f else .95f
    Box(modifier = modifier.aspectRatio(390f / 870f), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxSize(.82f).graphicsLayer { scaleX = scale; scaleY = scale; rotationY = if (isDenied) 0f else progress * 15f; translationX = if (isDenied) 0f else progress * 40f }.clip(RoundedCornerShape(47.dp)).background(Color.Black),
        ) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = if (isDenied) 0f else -progress * size.width }) { panorama() }
            if (showsShutterUI) {
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(68.dp).background(Color.Black.copy(alpha = .5f)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = .65f)))
                    Spacer(Modifier.size(width = 80.dp, height = 40.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = .65f)))
                }
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 11.dp).size(width = 120.dp, height = 36.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black))
            if (!isDenied) Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).size(10.dp).clip(CircleShape).background(Color.Green).graphicsLayer { translationX = 12.dp.toPx() })
            PermissionPhoneStatusBarTime(1f, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp))
        }
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
