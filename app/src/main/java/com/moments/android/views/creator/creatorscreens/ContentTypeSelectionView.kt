package com.moments.android.views.creator.creatorscreens

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassButtonTint
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorContentType
import com.moments.android.views.creator.CreatorFlow
import kotlin.math.roundToInt

/**
 * Port de `ContentTypeSelectionView.swift` (chunk UI principal).
 * BackgroundCameraView / PermissionPrimerGate fotos: pendientes de sus archivos iOS.
 */
@Composable
fun ContentTypeSelectionView(
    contentType: CreatorContentType,
    onContentTypeChange: (CreatorContentType) -> Unit,
    currentFlow: CreatorFlow,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember { mutableStateOf(contentType) }
    var recentImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var shutterScale by remember { mutableFloatStateOf(1f) }
    var dialTransientOffset by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val density = LocalDensity.current

    val dialControlWidth = 170.dp
    val dialInnerPadding = 4.dp
    val dialPillWidth = 84.dp
    val dialTravelPx = with(density) {
        (((dialControlWidth - dialInnerPadding * 2) - dialPillWidth) / 2).toPx()
    }
    val dialBaseOffset = if (selectedMode == CreatorContentType.MOMENT) -dialTravelPx else dialTravelPx
    val dialPillOffset = dialBaseOffset + dialTransientOffset
    val dialVisualMode =
        if (dialPillOffset <= 0f) CreatorContentType.MOMENT else CreatorContentType.STORY

    val infinite = rememberInfiniteTransition(label = "creatorBreath")
    val breathScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(5000), RepeatMode.Reverse),
        label = "breath",
    )
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )

    LaunchedEffect(Unit) {
        recentImageUris = loadRecentImageUris(context, limit = 4)
    }

    // currentFlow se mantiene en la firma por paridad iOS (@Binding); el shell lo actualiza vía onCurrentFlowChange.
    @Suppress("UNUSED_PARAMETER")
    val unusedFlow = currentFlow

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (selectedMode == CreatorContentType.MOMENT) {
                if (recentImageUris.isNotEmpty()) {
                    recentImageUris.take(4).forEachIndexed { index, uri ->
                        FloatingRecentImage(uri = uri, index = index)
                    }
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.15f)))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(0.8f)),
                                ),
                            ),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Gray.copy(0.1f)))
                }
            } else {
                // BackgroundCameraView.swift aún no portado — fallback iOS sin permiso
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black, Color(0xFF4A148C).copy(0.2f), Color(0xFFE91E63).copy(0.1f)),
                            ),
                        ),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize(),
        ) {
            // iOS topToolbar: leading 16 + top 10, close 44
            // Safe area la aporta el Dialog (safeDrawingPadding).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            // iOS: shutter.padding(.bottom, 28)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 28.dp)
                    .scale(shutterScale)
                    .pointerInput(selectedMode) {
                        detectTapGestures(
                            onPress = {
                                shutterScale = 0.9f
                                tryAwaitRelease()
                                shutterScale = 1f
                            },
                            onTap = {
                                HapticManager.shared.mediumImpact()
                                onContentTypeChange(selectedMode)
                                when (selectedMode) {
                                    CreatorContentType.MOMENT ->
                                        onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION)
                                    CreatorContentType.STORY ->
                                        onCurrentFlowChange(CreatorFlow.STORY_CAMERA)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedMode == CreatorContentType.MOMENT) {
                    MomentShutter(recentUri = recentImageUris.firstOrNull())
                } else {
                    StoryShutter(breathScale = breathScale, ringRotation = ringRotation)
                }
            }

            // iOS: dialSelector.padding(.bottom, 30)
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 30.dp)
                    .width(dialControlWidth)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(0.3f))
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(50))
                    .pointerInput(selectedMode, dialTravelPx, dialBaseOffset) {
                        detectDragGestures(
                            onDragEnd = {
                                val threshold = dialTravelPx * 0.7f
                                val currentOffset = dialBaseOffset + dialTransientOffset
                                val target = when {
                                    dialTransientOffset < -threshold -> CreatorContentType.MOMENT
                                    dialTransientOffset > threshold -> CreatorContentType.STORY
                                    else -> if (currentOffset <= 0f) {
                                        CreatorContentType.MOMENT
                                    } else {
                                        CreatorContentType.STORY
                                    }
                                }
                                if (target != selectedMode) HapticManager.shared.selection()
                                selectedMode = target
                                dialTransientOffset = 0f
                            },
                            onDrag = { _, drag ->
                                val proposed = dialBaseOffset + dialTransientOffset + drag.x
                                val clamped = proposed.coerceIn(-dialTravelPx, dialTravelPx)
                                dialTransientOffset = clamped - dialBaseOffset
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(dialPillOffset.roundToInt(), 0) }
                        .width(dialPillWidth)
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MomentsGlassButtonTint.canvas(true))
                        .border(0.5.dp, Color.Black.copy(0.14f), RoundedCornerShape(50)),
                )
                Row(Modifier.fillMaxSize().padding(horizontal = dialInnerPadding)) {
                    DialLabel(
                        text = stringResource(R.string.creator_moment_title),
                        active = dialVisualMode == CreatorContentType.MOMENT,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onClick = {
                            if (selectedMode != CreatorContentType.MOMENT) HapticManager.shared.selection()
                            selectedMode = CreatorContentType.MOMENT
                            dialTransientOffset = 0f
                        },
                    )
                    DialLabel(
                        text = stringResource(R.string.creator_story_title),
                        active = dialVisualMode == CreatorContentType.STORY,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onClick = {
                            if (selectedMode != CreatorContentType.STORY) HapticManager.shared.selection()
                            selectedMode = CreatorContentType.STORY
                            dialTransientOffset = 0f
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialLabel(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (active) Color.White.copy(0.96f) else Color.White.copy(0.58f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MomentShutter(recentUri: Uri?) {
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(x = (-5).dp)
                .rotate(-10f)
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.8f)),
        )
        Box(
            Modifier
                .offset(x = 5.dp, y = (-2).dp)
                .rotate(5f)
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(0.9f)),
        )
        if (recentUri != null) {
            AsyncImage(
                model = recentUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(65.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(65.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.Black)
            }
        }
    }
}

@Composable
private fun StoryShutter(breathScale: Float, ringRotation: Float) {
    // iOS: frame 88, ring 81 stroke 7, chrome glass on the circle
    Box(
        Modifier
            .size(88.dp)
            .scale(breathScale)
            .momentsChromeGlass(CircleShape, interactive = true),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(81.dp)
                .rotate(ringRotation)
                .border(
                    width = 7.dp,
                    brush = Brush.sweepGradient(
                        listOf(Color.Blue, Color.Magenta, Color(0xFFE91E63), Color.Magenta, Color.Blue),
                    ),
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun FloatingRecentImage(uri: Uri, index: Int) {
    val infinite = rememberInfiniteTransition(label = "float$index")
    val dx by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (index % 2 == 0) 40f else -40f,
        animationSpec = infiniteRepeatable(tween(18000), RepeatMode.Reverse),
        label = "dx",
    )
    val dy by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (index < 2) 30f else -30f,
        animationSpec = infiniteRepeatable(tween(20000), RepeatMode.Reverse),
        label = "dy",
    )
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = when (index) {
            0 -> Alignment.TopStart
            1 -> Alignment.TopEnd
            2 -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        },
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(280.dp)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .scale(1.15f)
                .alpha(0.8f),
        )
    }
}

private fun loadRecentImageUris(context: android.content.Context, limit: Int): List<Uri> {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val uris = mutableListOf<Uri>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        var count = 0
        while (cursor.moveToNext() && count < limit) {
            val id = cursor.getLong(idCol)
            uris += ContentUris.withAppendedId(collection, id)
            count++
        }
    }
    return uris
}
