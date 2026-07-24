package com.moments.android.views.permission.shared

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    tint: Color = Color(0xFF00A896),
    accent: (@Composable () -> Unit)? = null,
    primaryAction: () -> Unit,
    secondaryAction: (() -> Unit)? = null,
    phone: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val text = if (dark) Color.White else Color.Black.copy(alpha = .88f)
    val denied = stage == PermissionPrimerStage.DENIED
    Column(modifier.fillMaxSize().background(if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 15.dp, bottom = 25.dp).weight(1f, false), contentAlignment = Alignment.Center) { phone() }
        Column(Modifier.widthIn(max = 330.dp).padding(horizontal = 24.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(80.dp).padding(bottom = 10.dp)) {
                icon(text.copy(alpha = if (denied) .72f else 1f))
                if (denied) Icon(Icons.Default.Lock, null, tint = text, modifier = Modifier.align(Alignment.TopStart).size(16.dp))
                else accent?.let { content -> val motion = rememberInfiniteTransition(label = "permission-accent").animateFloat(-5f, 0f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "permission-accent-offset"); Box(Modifier.align(Alignment.TopStart).graphicsLayer { translationY = motion.value }) { content() } }
            }
            Text(title, color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(description, color = text.copy(alpha = .62f), fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4)
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(100)).background(tint).clickable(onClick = primaryAction), contentAlignment = Alignment.Center) { Text(primaryActionTitle, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1) }
            if (secondaryActionTitle != null && secondaryAction != null) Text(secondaryActionTitle, color = text.copy(alpha = .62f), modifier = Modifier.clickable(onClick = secondaryAction).padding(top = 5.dp, bottom = 8.dp))
        }
    }
}

fun Modifier.permissionMockDeniedChrome(denied: Boolean): Modifier = if (denied) background(Color.Black.copy(alpha = .18f)).graphicsLayer { alpha = .82f } else this
