package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.MomentGridPreviewSettings

/** Port de `ProfileGridPreviewEditorView.swift`. */
@Composable fun ProfileGridPreviewEditorView(imageUrl: String, initialSettings: MomentGridPreviewSettings, onDismiss: () -> Unit, onSave: (MomentGridPreviewSettings) -> Unit) {
    var scale by remember { mutableFloatStateOf(initialSettings.scale.toFloat()) }; var offset by remember { mutableStateOf(Offset(initialSettings.offsetX.toFloat(), initialSettings.offsetY.toFloat())) }; var fit by remember { mutableStateOf(initialSettings.fitMode) }; var background by remember { mutableStateOf(initialSettings.background) }
    val transform = rememberTransformableState { zoom, pan, _ -> scale = (scale * zoom).coerceIn(if (fit == MomentGridPreviewSettings.FitMode.FILL) 1f else .5f, 4f); offset += pan / 320f }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B1215)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.common_cancel), Modifier.profileMomentThumbnailGesture(onDismiss), color = Color.White); Spacer(Modifier.weight(1f)); Text(stringResource(R.string.profile_grid_preview_title), color = Color.White); Spacer(Modifier.weight(1f)); Text(stringResource(R.string.common_save), Modifier.profileMomentThumbnailGesture(onTap = { onSave(MomentGridPreviewSettings(scale.toDouble(), offset.x.toDouble(), offset.y.toDouble(), fit, background)); onDismiss() }), color = Color.White) }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Box(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().background(if (background == MomentGridPreviewSettings.Background.BLACK) Color.Black else Color.White).transformable(transform).pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) }) { AsyncImage(imageUrl, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x * size.width; translationY = offset.y * size.height }, contentScale = if (fit == MomentGridPreviewSettings.FitMode.FILL) ContentScale.Crop else ContentScale.Fit) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { PreviewControl(stringResource(if (fit == MomentGridPreviewSettings.FitMode.FILL) R.string.profile_grid_preview_mode_fill else R.string.profile_grid_preview_mode_fit), modifier = Modifier.weight(1f)) { fit = if (fit == MomentGridPreviewSettings.FitMode.FILL) MomentGridPreviewSettings.FitMode.FIT else MomentGridPreviewSettings.FitMode.FILL }; PreviewControl(stringResource(R.string.profile_grid_preview_background), fit == MomentGridPreviewSettings.FitMode.FIT, modifier = Modifier.weight(1f)) { background = if (background == MomentGridPreviewSettings.Background.BLACK) MomentGridPreviewSettings.Background.WHITE else MomentGridPreviewSettings.Background.BLACK } }
        Text(stringResource(R.string.profile_grid_preview_hint), Modifier.fillMaxWidth().padding(12.dp), color = Color.White.copy(alpha = .65f), textAlign = TextAlign.Center)
    }
}
@Composable private fun PreviewControl(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, action: () -> Unit) = Text(label, modifier.profileMomentThumbnailGesture(onTap = { if (enabled) action() }).padding(14.dp), color = if (enabled) Color.White else Color.Gray, textAlign = TextAlign.Center)
