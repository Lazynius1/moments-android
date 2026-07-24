package com.moments.android.views.creator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CachedHiddenLayerDraft
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorMedia
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

/** Espejo de iOS `HiddenLayerDraft` (chunk texto). */
data class HiddenLayerDraft(
    val id: String = UUID.randomUUID().toString(),
    val type: MomentHiddenLayer.LayerType = MomentHiddenLayer.LayerType.TEXT,
    val anchorX: Double = 0.5,
    val anchorY: Double = 0.5,
    val width: Double = 0.28,
    val height: Double = 0.16,
    val shape: MomentHiddenLayer.LayerShape = MomentHiddenLayer.LayerShape.ROUNDED_RECT,
    val zIndex: Int = 0,
    val text: String = "",
    val caption: String = "",
    val textStyle: HiddenLayerTextStyle = HiddenLayerTextStyle.CLEAN,
    val presentationStyle: HiddenLayerPresentationStyle = HiddenLayerPresentationStyle.GLASS_CARD,
    val unlockMode: MomentHiddenLayer.UnlockMode = MomentHiddenLayer.UnlockMode.IMMEDIATE,
    val authorTimezoneIdentifier: String? = TimeZone.getDefault().id,
) {
    val isReadyToPublish: Boolean
        get() = when (type) {
            MomentHiddenLayer.LayerType.TEXT -> text.trim().isNotEmpty()
            else -> false
        }

    fun toCached(): CachedHiddenLayerDraft = CachedHiddenLayerDraft(
        id = id,
        type = type.raw,
        anchorX = anchorX,
        anchorY = anchorY,
        width = width,
        height = height,
        shape = shape.raw,
        zIndex = zIndex,
        text = text,
        caption = caption,
        imageOffsetX = 0.0,
        imageOffsetY = 0.0,
        imageScale = 1.0,
        imageFrameStyle = "classic",
        localImageFileName = null,
        localAudioFileName = null,
        duration = null,
        textStyle = textStyle.raw,
        presentationStyle = presentationStyle.raw,
        unlockMode = unlockMode.raw,
        unlockAt = null,
        authorTimezoneIdentifier = authorTimezoneIdentifier,
    )
}

/**
 * Port chunk 1 de `HiddenLayersEditorView.swift` — solo capas de texto (máx 3).
 * Image/audio/schedule: chunks siguientes.
 */
@Composable
fun HiddenLayersEditorView(
    mediaItem: CreatorMedia,
    layers: List<HiddenLayerDraft>,
    onLayersChange: (List<HiddenLayerDraft>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val fg = if (isDark) Color.White else Color.Black
    val density = LocalDensity.current

    var selectedId by remember { mutableStateOf(layers.firstOrNull()?.id) }
    val selected = layers.firstOrNull { it.id == selectedId }

    fun updateSelected(transform: (HiddenLayerDraft) -> HiddenLayerDraft) {
        val id = selectedId ?: return
        onLayersChange(layers.map { if (it.id == id) transform(it) else it })
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.creator_hidden_layers),
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                AsyncImage(
                    model = mediaItem.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )

                layers.forEach { layer ->
                    val wPx = (layer.width * boxW).toFloat()
                    val hPx = (layer.height * boxH).toFloat()
                    val xPx = (layer.anchorX * boxW - wPx / 2).toFloat()
                    val yPx = (layer.anchorY * boxH - hPx / 2).toFloat()
                    val selectedLayer = layer.id == selectedId
                    Box(
                        Modifier
                            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                            .size(
                                width = with(density) { wPx.toDp() },
                                height = with(density) { hPx.toDp() },
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(0.45f))
                            .border(
                                1.5.dp,
                                if (selectedLayer) Color(0xFFE91E63) else Color.White.copy(0.35f),
                                RoundedCornerShape(10.dp),
                            )
                            .pointerInput(layer.id, boxW, boxH) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val nx = (layer.anchorX + dragAmount.x / boxW).coerceIn(0.08, 0.92)
                                    val ny = (layer.anchorY + dragAmount.y / boxH).coerceIn(0.08, 0.92)
                                    onLayersChange(
                                        layers.map {
                                            if (it.id == layer.id) it.copy(anchorX = nx, anchorY = ny) else it
                                        },
                                    )
                                }
                            }
                            .clickable { selectedId = layer.id }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            layer.text.ifBlank { stringResource(R.string.creator_hidden_layer_text_placeholder) },
                            color = Color.White.copy(if (layer.text.isBlank()) 0.45f else 1f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 4,
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable(enabled = layers.size < 3) {
                            if (layers.size >= 3) return@clickable
                            val draft = HiddenLayerDraft(zIndex = layers.size)
                            onLayersChange(layers + draft)
                            selectedId = draft.id
                            HapticManager.shared.success()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = fg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.creator_hidden_layer_add_text),
                            color = fg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (selected != null) {
                    Box(
                        Modifier
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable {
                                onLayersChange(layers.filterNot { it.id == selected.id })
                                selectedId = layers.firstOrNull { it.id != selected.id }?.id
                                HapticManager.shared.warning()
                            }
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Filled.Delete, null, tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_hidden_layer_count, layers.count { it.isReadyToPublish }, 3),
                    color = fg.copy(0.55f),
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (selected != null) {
                BasicTextField(
                    value = selected.text,
                    onValueChange = { newText -> updateSelected { layer -> layer.copy(text = newText) } },
                    textStyle = TextStyle(color = fg, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFFE91E63)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = true)
                        .padding(14.dp),
                    decorationBox = { inner ->
                        if (selected.text.isEmpty()) {
                            Text(
                                stringResource(R.string.creator_hidden_layer_text_placeholder),
                                color = fg.copy(0.4f),
                            )
                        }
                        inner()
                    },
                )
            } else {
                Text(
                    stringResource(R.string.creator_hidden_layer_empty_hint),
                    color = fg.copy(0.5f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
