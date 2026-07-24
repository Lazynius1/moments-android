package com.moments.android.views.permission.notifications

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPhoneWallpaper
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `NotificationsPermissionView.swift`. */
@Composable
fun NotificationsPermissionView(stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER, primaryAction: () -> Unit, secondaryAction: () -> Unit) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(
        stage, icon = { tint -> Icon(if (denied) Icons.Default.NotificationsOff else Icons.Default.Notifications, null, tint = tint, modifier = Modifier.fillMaxSize()) },
        title = stringResource(if (denied) R.string.permission_notifications_denied_title else R.string.permission_notifications_primer_title),
        description = stringResource(if (denied) R.string.permission_notifications_denied_subtitle else R.string.permission_notifications_primer_subtitle),
        primaryActionTitle = stringResource(if (denied) R.string.permission_notifications_denied_open_settings else R.string.permission_notifications_primer_allow),
        secondaryActionTitle = stringResource(R.string.permission_notifications_primer_not_now), primaryAction = primaryAction, secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(screenBackground = Color(0xFF0A0A0C), animated = false, showsStatusBarTime = false, appliesDeniedChrome = denied, screen = { NotificationBannerScreen(!denied) }, island = {})
    }
}

@Composable private fun NotificationBannerScreen(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "notification-banner")
    val progress = if (active) transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3800), RepeatMode.Restart), label = "notification-banner-progress").value else 0f
    Box(Modifier.fillMaxSize()) {
        PermissionPhoneWallpaper(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.permission_phone_time), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Thin)
            NotificationCard(active, progress)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun NotificationCard(active: Boolean, progress: Float) {
    val alpha = if (active) when { progress < .08f -> 0f; progress > .9f -> (1f - progress) / .1f; else -> 1f } else .45f
    Row(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = if (active) (1f - progress.coerceAtMost(.25f) * 4f) * -24f else 0f }.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .18f)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.mipmap.ic_launcher), null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row { Text(stringResource(R.string.app_name), color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text(stringResource(R.string.permission_notifications_mock_now), color = Color.White.copy(alpha = .55f), fontSize = 11.sp) }
            Text(stringResource(R.string.permission_notifications_mock_sender), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.permission_notifications_mock_body), color = Color.White.copy(alpha = .78f), fontSize = 14.sp, maxLines = 2)
        }
    }
}
