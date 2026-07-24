package com.moments.android.views.permission.tracking

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `TrackingPermissionView.swift`. */
@Composable fun TrackingPermissionView(stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER, primaryAction: () -> Unit) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(stage, icon = { tint -> Icon(if (denied) Icons.Default.PanToolAlt else Icons.Default.PanTool, null, tint = tint, modifier = Modifier.fillMaxSize()) },
        title = stringResource(if (denied) R.string.permission_tracking_denied_title else R.string.att_pre_alert_title), description = stringResource(if (denied) R.string.permission_tracking_denied_subtitle else R.string.att_pre_alert_description),
        primaryActionTitle = stringResource(if (denied) R.string.permission_tracking_denied_open_settings else R.string.att_pre_alert_continue), primaryAction = primaryAction) {
        PermissionPhoneFrame(screenBackground = Color(0xFF111318), animated = false, appliesDeniedChrome = denied, screen = { TrackingFeedScreen(!denied) }, island = {})
    }
}

@Composable private fun TrackingFeedScreen(active: Boolean) {
    val progress = if (active) rememberInfiniteTransition(label = "tracking-feed").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "tracking-scroll").value else .4f
    Column(Modifier.fillMaxSize().graphicsLayer { translationY = -progress * size.height * 1.3f }.then(if (active) Modifier else Modifier.blur(2.5.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(if (active) 10 else 5) { FeedCard(it % 5 == 1) } }
}

@Composable private fun FeedCard(highlighted: Boolean) {
    val accent = Color(0xFF6C5CE7)
    Column(Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .05f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(30.dp).clip(CircleShape).background(if (highlighted) accent else Color.White.copy(alpha = .18f))); Spacer(Modifier.width(8.dp)); Column { Box(Modifier.width(90.dp).height(6.dp).background(Color.White.copy(alpha = .5f), CircleShape)); Spacer(Modifier.height(5.dp)); Box(Modifier.width(55.dp).height(5.dp).background(Color.White.copy(alpha = .28f), CircleShape)) }; Spacer(Modifier.weight(1f)); if (highlighted) Row(Modifier.clip(CircleShape).background(accent).padding(horizontal = 8.dp, vertical = 3.dp)) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(12.dp)); Text(stringResource(R.string.permission_tracking_mock_ad), color = Color.White) } }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(if (highlighted) Brush.linearGradient(listOf(accent.copy(alpha = .85f), Color(0xFF4C8DFF).copy(alpha = .7f))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = .1f), Color.White.copy(alpha = .05f)))))
    }
}
