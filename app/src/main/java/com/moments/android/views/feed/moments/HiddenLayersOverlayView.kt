package com.moments.android.views.feed.moments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchHiddenLayers
import com.moments.android.views.components.hiddenlayers.HiddenLayerLayout
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/**
 * Port de `HiddenLayersOverlayView.swift` — cacho 1:
 * carga, hotspots posicionados, hint de presencia, reveal texto/imagen, locked hint.
 * Audio reveal / burst / metrics discovery → siguientes cachos.
 */
@Composable
fun HiddenLayersOverlayView(
    momentId: String,
    authorId: String = "",
    hasHiddenLayers: Boolean = true,
    hiddenLayerCount: Int = 1,
    isImmersive: Boolean = false,
    requiresFocusForIntro: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onOpenLayers: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!hasHiddenLayers || isImmersive || hiddenLayerCount <= 0 || authorId.isBlank() || momentId.isBlank()) {
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val firestore = remember { FirestoreService() }
    var layers by remember(momentId) { mutableStateOf<List<MomentHiddenLayer>>(emptyList()) }
    var isLoading by remember(momentId) { mutableStateOf(false) }
    var revealedIds by remember(momentId) { mutableStateOf(setOf<String>()) }
    var showIntro by remember(momentId) { mutableStateOf(false) }
    var topHint by remember { mutableStateOf<String?>(null) }
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    val prefs = remember {
        context.getSharedPreferences("moments_hidden_layers_seen", android.content.Context.MODE_PRIVATE)
    }

    fun seenKey(layerId: String) = "hiddenLayerSeen:$viewerId:$momentId:$layerId"
    fun wasSeen(layerId: String) = prefs.getBoolean(seenKey(layerId), false)
    fun markSeen(layerId: String) {
        prefs.edit().putBoolean(seenKey(layerId), true).apply()
    }

    LaunchedEffect(momentId, authorId) {
        if (layers.isNotEmpty() || isLoading) return@LaunchedEffect
        isLoading = true
        val fetched = runCatching {
            firestore.fetchHiddenLayers(authorId, momentId)
        }.getOrDefault(emptyList())
        val visible = fetched
            .filter { it.isVisibleInViewer }
            .sortedBy { it.zIndex }
        layers = visible
        val now = Date()
        revealedIds = visible
            .filter { wasSeen(it.id) && it.isUnlocked(now) }
            .map { it.id }
            .toSet()
        isLoading = false
        if (visible.isNotEmpty() && !requiresFocusForIntro) {
            showIntro = true
            topHint = "Tap to discover"
            delay(2200)
            showIntro = false
            if (topHint == "Tap to discover") topHint = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val imageRect = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        if (isLoading && layers.isEmpty()) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                strokeWidth = 2.dp,
            )
        }

        layers.forEachIndexed { index, layer ->
            val frame = HiddenLayerLayout.frame(layer, imageRect)
            val revealed = layer.id in revealedIds
            val unlocked = layer.isUnlocked()

            Box(
                Modifier
                    .offset {
                        IntOffset(frame.left.roundToInt(), frame.top.roundToInt())
                    }
                    .size(
                        width = with(density) { frame.width.toDp() },
                        height = with(density) { frame.height.toDp() },
                    )
                    .clickable {
                        if (revealed) return@clickable
                        if (!unlocked) {
                            topHint = "Locked for now"
                            return@clickable
                        }
                        markSeen(layer.id)
                        revealedIds = revealedIds + layer.id
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (revealed) {
                    RevealedLayerContent(layer = layer)
                } else {
                    PresenceHint(
                        shape = layer.shape,
                        isIntro = showIntro,
                        delayMs = (index * 120L),
                        seen = wasSeen(layer.id),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = topHint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
        ) {
            Text(
                text = topHint.orEmpty(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .momentsChromeGlass(RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    LaunchedEffect(topHint) {
        val hint = topHint ?: return@LaunchedEffect
        if (hint == "Tap to discover") return@LaunchedEffect
        delay(1800)
        if (topHint == hint) topHint = null
    }
}

@Composable
private fun PresenceHint(
    shape: MomentHiddenLayer.LayerShape,
    isIntro: Boolean,
    delayMs: Long,
    seen: Boolean,
) {
    val infinite = rememberInfiniteTransition(label = "hlHint")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hlPulse",
    )
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    if (!visible && !isIntro) return

    val clip = if (shape == MomentHiddenLayer.LayerShape.CIRCLE) CircleShape else RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxSize()
            .alpha(if (seen) 0.25f else pulse)
            .clip(clip)
            .background(Color.White.copy(alpha = 0.18f)),
    )
}

@Composable
private fun RevealedLayerContent(layer: MomentHiddenLayer) {
    when (layer.type) {
        MomentHiddenLayer.LayerType.TEXT -> {
            Text(
                text = layer.text.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .momentsChromeGlass(RoundedCornerShape(12.dp))
                    .padding(8.dp),
            )
        }
        MomentHiddenLayer.LayerType.IMAGE -> {
            val url = layer.mediaURL ?: layer.thumbnailURL
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = layer.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        MomentHiddenLayer.LayerType.AUDIO -> {
            Text(
                text = "♪",
                color = Color(0xFF66CCFF),
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .momentsChromeGlass(CircleShape)
                    .padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
