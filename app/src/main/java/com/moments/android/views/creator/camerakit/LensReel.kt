package com.moments.android.views.creator.camerakit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.views.creator.components.CaptureButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Port de `LensReel.swift`: el carrusel se mueve bajo un `CaptureButton` fijo.
 * La lente más próxima al centro es la activa; la primera celda es passthrough.
 */
@Composable
fun LensReel(
    lenses: List<CameraKitLens>,
    isRecording: Boolean,
    onSelect: (CameraKitLens?) -> Unit,
    onCapturePhoto: () -> Unit,
    onStartVideo: () -> Unit,
    onStopVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(lenses) { listOf(LensCarouselItem.passthrough) + lenses.map(LensCarouselItem::from) }
    val listState = rememberLazyListState()
    var centeredKey by remember { mutableStateOf(passthroughKey) }

    LaunchedEffect(items, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item -> abs((item.offset + item.size / 2) - center) }?.index
        }
            .filterNotNull()
            .map { items.getOrNull(it)?.id }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { key ->
                centeredKey = key
                onSelect(lenses.firstOrNull { it.id == key })
            }
    }

    BoxWithConstraints(modifier.height(100.dp)) {
        val horizontalInset = (maxWidth / 2 - 24.dp).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalInset),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(36.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.id }) { item ->
                LensCarouselCell(
                    item = item,
                    isCentered = item.id == centeredKey,
                    onClick = { /* el gesto de scroll decidirá la celda centrada */ },
                )
            }
        }
        CaptureButton(
            isRecording = isRecording,
            onTap = onCapturePhoto,
            onLongPressStart = onStartVideo,
            onLongPressEnd = onStopVideo,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private const val passthroughKey = "__passthrough__"

private data class LensCarouselItem(val id: String, val iconUrl: String?) {
    companion object {
        val passthrough = LensCarouselItem(passthroughKey, null)
        fun from(lens: CameraKitLens) = LensCarouselItem(lens.id, lens.iconUrl)
    }
}

@Composable
private fun LensCarouselCell(item: LensCarouselItem, isCentered: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(if (isCentered) .26f else .15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.iconUrl != null) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.Block, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
