package com.moments.android.views.permission.shared

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.moments.android.R

data class PermissionPhoneMotion(val progress: Float = 0f, val reveal: Float = 0f)
enum class PermissionPhoneIslandPlacement { INSIDE, LEADING }

@Composable fun PermissionPhoneStatusBarTime(ratio: Float, modifier: Modifier = Modifier, color: Color = Color.White) = Text(stringResource(R.string.permission_phone_time), color = color, fontSize = (15 * ratio).sp, modifier = modifier)

/** Port de `PermissionPhoneFrame.swift`. */
@Composable
fun PermissionPhoneFrame(
    screenBackground: Color = Color.Black, animated: Boolean = true,
    islandPlacement: PermissionPhoneIslandPlacement = PermissionPhoneIslandPlacement.INSIDE,
    showsStatusBarTime: Boolean = true, showsIslandIndicators: Boolean = true, appliesDeniedChrome: Boolean = false,
    screen: @Composable BoxScope.(PermissionPhoneMotion) -> Unit,
    island: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "permission-phone")
    val progress = if (animated) transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Reverse), label = "permission-phone-pan").value else 0f
    Box(Modifier.aspectRatio(390f / 870f), contentAlignment = Alignment.Center) {
        val motion = PermissionPhoneMotion(progress, if (animated) 1f else 1f)
        Box(Modifier.fillMaxSize(.82f).graphicsLayer { scaleX = if (animated) .95f else 1f; scaleY = scaleX; rotationY = progress * 15f; translationX = progress * 40f }.clip(RoundedCornerShape(47.dp)).background(screenBackground)) {
            Box(Modifier.fillMaxSize().permissionMockDeniedChrome(appliesDeniedChrome)) { screen(motion) }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 11.dp).size(120.dp, 36.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) { if (showsIslandIndicators && islandPlacement == PermissionPhoneIslandPlacement.INSIDE) island() }
            if (showsStatusBarTime) PermissionPhoneStatusBarTime(1f, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp))
            if (showsIslandIndicators && islandPlacement == PermissionPhoneIslandPlacement.LEADING) Box(Modifier.align(Alignment.TopStart).padding(start = 70.dp, top = 20.dp).size(14.dp)) { island() }
        }
    }
}
