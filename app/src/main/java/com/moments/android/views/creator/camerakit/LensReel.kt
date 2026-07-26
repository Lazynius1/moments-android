package com.moments.android.views.creator.camerakit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.views.creator.components.CaptureButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** ≡ `LensCarouselLayout.itemSize` / `itemPitch` */
private val ItemSize = 48.dp
private val ItemPitch = 84.dp

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
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pitchPx = with(density) { ItemPitch.toPx() }
    var centeredKey by remember { mutableStateOf(passthroughKey) }
    val centeredLens = remember(lenses, centeredKey) { lenses.firstOrNull { it.id == centeredKey } }

    LaunchedEffect(items, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - center)
            }?.index
        }
            .filterNotNull()
            .map { items.getOrNull(it)?.id }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { key ->
                centeredKey = key
                onSelect(
                    if (key == passthroughKey) null
                    else lenses.firstOrNull { it.id == key },
                )
            }
    }

    BoxWithConstraints(modifier.height(100.dp)) {
        val horizontalInset = (maxWidth / 2 - ItemSize / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = horizontalInset),
            horizontalArrangement = Arrangement.spacedBy(ItemPitch - ItemSize),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val appearance by remember(index, pitchPx) {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val itemInfo = info.visibleItemsInfo.firstOrNull { it.index == index }
                            ?: return@derivedStateOf 1f to 1f
                        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val itemCenter = itemInfo.offset + itemInfo.size / 2f
                        val progress = min(abs(itemCenter - center) / pitchPx, 1f)
                        // ≡ updateVisibleCellAppearance: centrada α=0 / scale=0.82 (bajo shutter)
                        progress to (0.82f + 0.18f * progress)
                    }
                }
                LensCarouselCell(
                    item = item,
                    alpha = appearance.first,
                    scale = appearance.second,
                    onClick = {
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }
        }
        CaptureButton(
            isRecording = isRecording,
            onTap = onCapturePhoto,
            onLongPressStart = onStartVideo,
            onLongPressEnd = onStopVideo,
            modifier = Modifier.align(Alignment.Center),
            lensIconURL = centeredLens?.iconUrl,
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
private fun LensCarouselCell(
    item: LensCarouselItem,
    alpha: Float,
    scale: Float,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(ItemSize)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(0.15f))
            .border(1.2.dp, Color.White.copy(0.45f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
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
