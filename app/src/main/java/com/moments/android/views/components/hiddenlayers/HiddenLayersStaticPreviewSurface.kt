package com.moments.android.views.components.hiddenlayers

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchHiddenLayers
import com.moments.android.views.feed.moments.HiddenLayerRevealedContent
import com.moments.android.views.profile.core.MomentGridPreviewFitMode
import kotlin.math.roundToInt

/**
 * Port de `HiddenLayersStaticPreviewSurface.swift`, con el mismo patrón de
 * `StoryStaticPreviewSurface`: canvas del media + overlay a tamaño de referencia
 * escalado dentro. El pan/zoom del grid lo aplica el padre
 * (`GridPreviewThumbnailFrame` / graphicsLayer del editor) — aquí solo caben
 * layers en las dimensiones del moment.
 */
@Composable
fun HiddenLayersStaticPreviewSurface(
    moment: Moment,
    settings: MomentGridPreviewSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val firestore = remember { FirestoreService() }
    var layers by remember(moment.id) { mutableStateOf<List<MomentHiddenLayer>>(emptyList()) }
    val prefs = remember {
        context.getSharedPreferences("moments_hidden_layers_seen", Context.MODE_PRIVATE)
    }
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    val momentId = moment.id.orEmpty()

    LaunchedEffect(momentId, moment.authorId, moment.hasHiddenLayers) {
        if (!moment.hasHiddenLayers || momentId.isBlank()) {
            layers = emptyList()
            return@LaunchedEffect
        }
        val fetched = runCatching {
            firestore.fetchHiddenLayers(moment.authorId, momentId)
        }.getOrDefault(emptyList())
        layers = fetched
            .filter { it.isVisibleInViewer }
            .sortedBy { it.zIndex }
    }

    if (layers.isEmpty()) return

    BoxWithConstraints(modifier.clipToBounds()) {
        val targetW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val targetH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val mediaCanvas = previewMediaCanvasSize(
            target = Size(targetW, targetH),
            moment = moment,
            fitMode = settings.fitMode,
        )
        val layerAspect = layerCanvasAspectRatio(moment)
        val referenceWidth = 393.dp
        val referenceHeight = (393f / layerAspect).dp
        val referenceWidthPx = with(density) { referenceWidth.toPx() }
        val referenceHeightPx = with(density) { referenceHeight.toPx() }
        val overlayScale = if (referenceWidthPx > 0f) {
            mediaCanvas.width / referenceWidthPx
        } else {
            1f
        }
        val canvasWidth = with(density) { mediaCanvas.width.toDp() }
        val canvasHeight = with(density) { mediaCanvas.height.toDp() }

        Box(
            Modifier
                .size(canvasWidth, canvasHeight)
                .align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .requiredSize(referenceWidth, referenceHeight)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = overlayScale
                        scaleY = overlayScale
                    },
            ) {
                layers.forEach { layer ->
                    val frame = HiddenLayerLayout.frame(
                        layer = layer,
                        imageRect = Rect(0f, 0f, referenceWidthPx, referenceHeightPx),
                        minimumSizePx = 1f,
                    )
                    val revealed = layer.isUnlocked() &&
                        prefs.getBoolean("hiddenLayerSeen:$viewerId:$momentId:${layer.id}", false)
                    Box(
                        Modifier
                            .offset {
                                IntOffset(frame.left.roundToInt(), frame.top.roundToInt())
                            }
                            .size(
                                width = with(density) { frame.width.toDp() },
                                height = with(density) { frame.height.toDp() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (revealed) {
                            HiddenLayerRevealedContent(
                                layer = layer,
                                frameWidthPx = frame.width,
                                frameHeightPx = frame.height,
                                shouldAutoplay = false,
                                isAnimated = false,
                            )
                        } else {
                            HiddenLayerHintAppearanceView(
                                type = layer.type,
                                shape = layer.shape,
                                style = layer.hintStyle ?: MomentHiddenLayer.HintStyle.ACTUAL,
                                isSeen = false,
                                delaySec = 0.0,
                                isIntro = false,
                                isAnimated = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun previewMediaCanvasSize(
    target: Size,
    moment: Moment,
    fitMode: MomentGridPreviewFitMode,
): Size {
    if (target.width <= 0f || target.height <= 0f) return Size.Zero
    val sourceAspect = layerCanvasAspectRatio(moment)
    val targetAspect = target.width / target.height
    val usesFit = fitMode == MomentGridPreviewFitMode.FIT
    return if ((usesFit && sourceAspect > targetAspect) || (!usesFit && sourceAspect < targetAspect)) {
        Size(target.width, target.width / sourceAspect)
    } else {
        Size(target.height * sourceAspect, target.height)
    }
}

private fun layerCanvasAspectRatio(moment: Moment): Float {
    val mediaItem = moment.primaryVisibleMediaItem
    val mediaAspect = mediaItem?.feedCrop
        ?.takeIf { !it.isFullBounds }
        ?.cardAspectValue
        ?: mediaItem?.resolvedAspectRatioValue
        ?: 1f
    return mediaAspect.coerceIn(
        HiddenLayerLayout.minimumPostAspectRatio,
        HiddenLayerLayout.maximumPostAspectRatio,
    )
}
