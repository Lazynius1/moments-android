package com.moments.android.views.creator.creatorscreens

import com.moments.android.views.creator.creatoruikit.MomentFeedCrop
import com.moments.android.views.creator.creatoruikit.NormalizedMediaCropContainer
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import com.moments.android.views.creator.creatoruikit.PhotoAdjustAxis
import com.moments.android.views.creator.creatoruikit.PhotoEditTab
import com.moments.android.views.creator.creatoruikit.PhotoEditTool
import com.moments.android.views.creator.creatoruikit.PhotoEdits
import com.moments.android.views.creator.creatoruikit.PhotoTintColor
import com.moments.android.views.creator.creatoruikit.PhotoTintTarget
import com.moments.android.views.creator.creatoruikit.TiltShiftMode
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItemFeedCrop
import com.moments.android.services.content.FilterService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.GlowSharePill
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Port de `MediaEditingView.swift`.
 * PhotoEdits (filter + lux/adjust) + reframe no destructivo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaEditingView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedMediaItems.isEmpty()) {
        CreatorFlowPendingScreen(
            iosSource = "MediaEditingView.swift (sin media)",
            onBack = { onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION) },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val screenBackground = rememberAdaptiveColors().surfaceBackground
    val ink = if (isDark) Color.White else Color.Black
    val inkMuted = ink.copy(alpha = if (isDark) 0.8f else 0.62f)
    var currentMediaIndex by remember { mutableIntStateOf(0) }
    var bottomTab by remember { mutableStateOf<PhotoEditTab?>(null) }
    var selectedTool by remember { mutableStateOf<PhotoEditTool?>(null) }
    var adjustAxis by remember { mutableStateOf(PhotoAdjustAxis.STRAIGHTEN) }
    var photoEdits by remember { mutableStateOf<Map<String, PhotoEdits>>(emptyMap()) }
    var currentEdits by remember { mutableStateOf(PhotoEdits()) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var sourceImmersiveUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var sourceImmersiveBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var filterJob by remember { mutableStateOf<Job?>(null) }
    var reframeDragStart by remember { mutableStateOf<MediaItemFeedCrop?>(null) }
    var reframeZoomStart by remember { mutableStateOf<MediaItemFeedCrop?>(null) }
    var immersivePixelSizes by remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    var showingCarouselAspectMenu by remember { mutableStateOf(false) }
    var carouselUsesSquare by remember { mutableStateOf(false) }
    var carouselPortraitAspect by remember { mutableDoubleStateOf(MomentFeedCrop.portraitMax.toDouble()) }
    val pagerState = rememberPagerState(pageCount = { selectedMediaItems.size })
    val isCarousel = selectedMediaItems.size > 1
    val current = selectedMediaItems.getOrNull(currentMediaIndex) ?: selectedMediaItems.first()
    val currentItemIsImage = !current.isVideo

    fun persistEdits(index: Int) {
        val item = selectedMediaItems.getOrNull(index) ?: return
        if (item.isVideo) return
        photoEdits = photoEdits + (item.id to currentEdits)
    }

    fun loadEdits(index: Int) {
        selectedTool = null
        val item = selectedMediaItems.getOrNull(index)
        if (item == null || item.isVideo) {
            currentEdits = PhotoEdits()
            previewBitmap = null
            return
        }
        currentEdits = photoEdits[item.id] ?: PhotoEdits()
    }

    fun decodeSource(uri: Uri, maxSide: Int = 1600): Bitmap? {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, probe) }
        val longest = max(probe.outWidth, probe.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    fun updatePreview() {
        filterJob?.cancel()
        if (!currentItemIsImage || currentEdits.isIdentity) {
            previewBitmap = null
            return
        }
        val uri = sourceUris[current.id] ?: current.uri
        val edits = currentEdits
        filterJob = scope.launch {
            delay(30)
            val rendered = withContext(Dispatchers.Default) {
                val base = decodeSource(uri) ?: return@withContext null
                FilterService.applyPhotoEdits(edits, base)
            }
            previewBitmap = rendered
        }
    }

    fun writeJpeg(bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "creator_photo_edits").also { it.mkdirs() }
        val file = File(dir, "edit_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return Uri.fromFile(file)
    }

    fun bakeAllEdits(after: () -> Unit) {
        persistEdits(currentMediaIndex)
        scope.launch {
            val updated = selectedMediaItems.toMutableList()
            for (index in updated.indices) {
                val item = updated[index]
                if (item.isVideo) continue
                val edits = photoEdits[item.id] ?: continue
                if (edits.isIdentity) continue
                withContext(Dispatchers.Default) {
                    val sourceUri = sourceUris[item.id] ?: item.uri
                    val source = decodeSource(sourceUri, maxSide = 4096) ?: return@withContext
                    val baked = FilterService.applyPhotoEdits(edits, source)
                    val outUri = writeJpeg(baked)
                    val immersiveSourceUri = sourceImmersiveUris[item.id] ?: item.immersiveUri
                    val immersiveOut = if (immersiveSourceUri != null && immersiveSourceUri != sourceUri) {
                        decodeSource(immersiveSourceUri, maxSide = 4096)?.let { writeJpeg(FilterService.applyPhotoEdits(edits, it)) }
                    } else {
                        outUri
                    }
                    updated[index] = item.copy(
                        uri = outUri,
                        immersiveUri = immersiveOut ?: item.immersiveUri ?: outUri,
                        hasEdits = true,
                    )
                }
            }
            onSelectedMediaItemsChange(updated)
            after()
        }
    }

    fun finishReframe(index: Int) {
        reframeDragStart = null
        reframeZoomStart = null
        if (!selectedMediaItems.indices.contains(index)) return
        previewBitmap = null
        updatePreview()
    }

    fun applyFeedCrop(index: Int, feedCrop: MediaItemFeedCrop) {
        if (!selectedMediaItems.indices.contains(index)) return
        val item = selectedMediaItems[index]
        val updated = selectedMediaItems.toMutableList()
        updated[index] = item.copy(
            feedCrop = feedCrop,
            aspectRatio = CreatorAspectRatio.fromFeedPostRatio(feedCrop.cardAspectValue),
            recommendedAspectRatio = CreatorAspectRatio.fromFeedPostRatio(feedCrop.cardAspectValue),
            hasEdits = true,
        )
        onSelectedMediaItemsChange(updated)
    }

    fun applyCarouselAspect(square: Boolean) {
        if (square == carouselUsesSquare) return
        carouselUsesSquare = square
        persistEdits(currentMediaIndex)
        val target = if (square) MomentFeedCrop.squareAspect else carouselPortraitAspect.toFloat()
        val cardRatio = CreatorAspectRatio.fromFeedPostRatio(target)
        val updated = selectedMediaItems.map { item ->
            val size = immersivePixelSizes[item.id] ?: return@map item.copy(
                aspectRatio = cardRatio,
                recommendedAspectRatio = cardRatio,
                feedCrop = MediaItemFeedCrop.fullBounds(cardRatio.displayName),
            )
            val (imgW, imgH) = size
            val currentRect = item.feedCrop?.rect(imgW, imgH) ?: RectF(0f, 0f, imgW, imgH)
            val currentAspect = currentRect.width() / max(currentRect.height(), 1f)
            val rect = if (target < currentAspect) {
                MomentFeedCrop.expandRect(currentRect, target, imgW, imgH)
            } else {
                MomentFeedCrop.exactCropRect(currentRect, target, imgW, imgH)
            }
            item.copy(
                aspectRatio = cardRatio,
                recommendedAspectRatio = cardRatio,
                feedCrop = MomentFeedCrop.normalizedFeedCrop(rect, imgW, imgH, target),
            )
        }
        onSelectedMediaItemsChange(updated)
        reframeDragStart = null
        reframeZoomStart = null
        previewBitmap = null
        loadEdits(currentMediaIndex)
        updatePreview()
    }

    fun removeCurrentCarouselItem() {
        if (selectedMediaItems.size <= 1) return
        persistEdits(currentMediaIndex)
        val removed = selectedMediaItems[currentMediaIndex]
        val next = selectedMediaItems.toMutableList().also { it.removeAt(currentMediaIndex) }
        photoEdits = photoEdits - removed.id
        sourceUris = sourceUris - removed.id
        sourceImmersiveUris = sourceImmersiveUris - removed.id
        sourceImmersiveBitmaps = sourceImmersiveBitmaps - removed.id
        onSelectedMediaItemsChange(next)
        currentMediaIndex = currentMediaIndex.coerceAtMost(next.lastIndex)
        HapticManager.shared.lightImpact()
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentMediaIndex) {
            persistEdits(currentMediaIndex)
            bottomTab = null
            currentMediaIndex = pagerState.currentPage
            reframeDragStart = null
            reframeZoomStart = null
            loadEdits(currentMediaIndex)
            updatePreview()
        }
    }

    LaunchedEffect(selectedMediaItems.map { it.id }) {
        selectedMediaItems.forEach { item ->
            if (!sourceUris.containsKey(item.id) && !item.isVideo) {
                sourceUris = sourceUris + (item.id to item.uri)
            }
            val immersive = item.immersiveUri
            if (immersive != null && !sourceImmersiveUris.containsKey(item.id)) {
                sourceImmersiveUris = sourceImmersiveUris + (item.id to immersive)
            }
            if (sourceImmersiveBitmaps.containsKey(item.id)) return@forEach
            val uri = item.immersiveUri ?: item.uri
            val bitmap = withContext(Dispatchers.IO) {
                decodeSource(uri, maxSide = 4096)?.creatorNormalizedUp(context, uri)
            }
            if (bitmap != null) {
                sourceImmersiveBitmaps = sourceImmersiveBitmaps + (item.id to bitmap)
                immersivePixelSizes = immersivePixelSizes + (item.id to (bitmap.width.toFloat() to bitmap.height.toFloat()))
            }
        }
        val first = selectedMediaItems.firstOrNull() ?: return@LaunchedEffect
        val ratio = first.aspectRatio.value
        carouselUsesSquare = abs(ratio - 1f) < 0.03f
        if (!carouselUsesSquare) carouselPortraitAspect = ratio.toDouble()
        loadEdits(currentMediaIndex)
        updatePreview()
    }

    LaunchedEffect(currentEdits) { updatePreview() }

    val carouselNonSquareRes =
        if (carouselPortraitAspect >= 1.0) R.string.creator_carousel_landscape else R.string.creator_carousel_portrait

    Box(modifier.fillMaxSize().background(screenBackground)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(screenBackground)
                    .padding(16.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .align(Alignment.CenterStart)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        null,
                        tint = ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    stringResource(R.string.creator_edit),
                    color = ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                GlowSharePill(
                    titleRes = R.string.creator_next,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = {
                        bakeAllEdits { onCurrentFlowChange(CreatorFlow.CAPTION_AND_DETAILS) }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    isSmall = true,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val lockSwipe = selectedTool == PhotoEditTool.BLUR
                if (lockSwipe) {
                    MediaEditPage(
                        item = current,
                        page = currentMediaIndex,
                        currentMediaIndex = currentMediaIndex,
                        previewBitmap = previewBitmap,
                        isCarousel = isCarousel,
                        selectedTool = selectedTool,
                        bottomTab = bottomTab,
                        reframeDragStart = reframeDragStart,
                        reframeZoomStart = reframeZoomStart,
                        immersivePixelSizes = immersivePixelSizes,
                        immersiveBitmap = sourceImmersiveBitmaps[current.id],
                        onReframeDragStartChange = { reframeDragStart = it },
                        onReframeZoomStartChange = { reframeZoomStart = it },
                        onApplyFeedCrop = { applyFeedCrop(currentMediaIndex, it) },
                        onFinishReframe = { finishReframe(currentMediaIndex) },
                        onRemove = { removeCurrentCarouselItem() },
                        showingCarouselAspectMenu = showingCarouselAspectMenu,
                        carouselUsesSquare = carouselUsesSquare,
                        carouselNonSquareRes = carouselNonSquareRes,
                        onToggleAspectMenu = { showingCarouselAspectMenu = !showingCarouselAspectMenu },
                        onApplyCarouselAspect = { applyCarouselAspect(it); showingCarouselAspectMenu = false },
                        ink = ink,
                        currentEdits = currentEdits,
                        onCurrentEditsChange = { currentEdits = it },
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = bottomTab == null && selectedTool == null,
                    ) { page ->
                        val item = selectedMediaItems[page]
                        MediaEditPage(
                            item = item,
                            page = page,
                            currentMediaIndex = currentMediaIndex,
                            previewBitmap = previewBitmap,
                            isCarousel = isCarousel,
                            selectedTool = selectedTool,
                            bottomTab = bottomTab,
                            reframeDragStart = reframeDragStart,
                            reframeZoomStart = reframeZoomStart,
                            immersivePixelSizes = immersivePixelSizes,
                            immersiveBitmap = sourceImmersiveBitmaps[item.id],
                            onReframeDragStartChange = { reframeDragStart = it },
                            onReframeZoomStartChange = { reframeZoomStart = it },
                            onApplyFeedCrop = { applyFeedCrop(page, it) },
                            onFinishReframe = { finishReframe(page) },
                            onRemove = { removeCurrentCarouselItem() },
                            showingCarouselAspectMenu = showingCarouselAspectMenu,
                            carouselUsesSquare = carouselUsesSquare,
                            carouselNonSquareRes = carouselNonSquareRes,
                            onToggleAspectMenu = { showingCarouselAspectMenu = !showingCarouselAspectMenu },
                            onApplyCarouselAspect = { applyCarouselAspect(it); showingCarouselAspectMenu = false },
                            ink = ink,
                            currentEdits = currentEdits,
                            onCurrentEditsChange = { currentEdits = it },
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(screenBackground)
                    .padding(top = 10.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(6.dp)) {
                    if (isCarousel) {
                        selectedMediaItems.indices.forEach { index ->
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (index == currentMediaIndex) ink else ink.copy(alpha = 0.22f)),
                            )
                        }
                    }
                }
                if (bottomTab == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                        EditorMiniCard(
                            titleRes = R.string.creator_tools_filter,
                            icon = Icons.Filled.Filter,
                            enabled = currentItemIsImage,
                            ink = ink,
                            inkMuted = inkMuted,
                            isDark = isDark,
                            onClick = {
                                HapticManager.shared.lightImpact()
                                bottomTab = PhotoEditTab.FILTER
                                selectedTool = null
                            },
                        )
                        EditorMiniCard(
                            titleRes = R.string.creator_tools_edit,
                            icon = Icons.Filled.Tune,
                            enabled = currentItemIsImage,
                            ink = ink,
                            inkMuted = inkMuted,
                            isDark = isDark,
                            onClick = {
                                HapticManager.shared.lightImpact()
                                bottomTab = PhotoEditTab.EDIT
                            },
                        )
                    }
                } else {
                    Spacer(Modifier.height(74.dp))
                }
            }
        }

        val tab = bottomTab
        if (tab != null && currentItemIsImage) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(screenBackground)
                    .padding(top = 10.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    if (currentEdits.canReset(tab, selectedTool, adjustAxis)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.saved_moments_filters_reset),
                            tint = ink,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(32.dp)
                                .clickable {
                                    HapticManager.shared.lightImpact()
                                    currentEdits = currentEdits.reset(tab, selectedTool, adjustAxis)
                                },
                        )
                    }
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.common_done),
                        tint = ink,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp)
                            .clickable {
                                HapticManager.shared.lightImpact()
                                persistEdits(currentMediaIndex)
                                bottomTab = null
                                selectedTool = null
                            },
                    )
                }
                if (tab == PhotoEditTab.FILTER) {
                    if (currentEdits.filter != FilterService.FilterType.NORMAL) {
                        EditorValueSlider(
                            value = currentEdits.filterIntensity,
                            range = 0.0..1.0,
                            bipolar = false,
                            ink = ink,
                            onChange = { currentEdits = currentEdits.copy(filterIntensity = it) },
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        modifier = Modifier.height(140.dp),
                    ) {
                        items(FilterService.FilterType.entries, key = { it.raw }) { filter ->
                            FilterOption(
                                sourceUri = sourceUris[current.id] ?: current.uri,
                                filter = filter,
                                isSelected = currentEdits.filter == filter,
                                onTap = {
                                    HapticManager.shared.lightImpact()
                                    currentEdits = currentEdits.copy(
                                        filter = filter,
                                        filterIntensity = if (filter == FilterService.FilterType.NORMAL) 1.0 else currentEdits.filterIntensity,
                                    )
                                },
                            )
                        }
                    }
                } else {
                    EditPanel(
                        currentEdits = currentEdits,
                        selectedTool = selectedTool,
                        adjustAxis = adjustAxis,
                        ink = ink,
                        inkMuted = inkMuted,
                        canvasColor = screenBackground,
                        isDark = isDark,
                        onEditsChange = { currentEdits = it },
                        onToolChange = { selectedTool = it },
                        onAxisChange = { adjustAxis = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaEditPage(
    item: CreatorMedia,
    page: Int,
    currentMediaIndex: Int,
    previewBitmap: Bitmap?,
    isCarousel: Boolean,
    selectedTool: PhotoEditTool?,
    bottomTab: PhotoEditTab?,
    reframeDragStart: MediaItemFeedCrop?,
    reframeZoomStart: MediaItemFeedCrop?,
    immersivePixelSizes: Map<String, Pair<Float, Float>>,
    immersiveBitmap: Bitmap?,
    onReframeDragStartChange: (MediaItemFeedCrop?) -> Unit,
    onReframeZoomStartChange: (MediaItemFeedCrop?) -> Unit,
    onApplyFeedCrop: (MediaItemFeedCrop) -> Unit,
    onFinishReframe: () -> Unit,
    onRemove: () -> Unit,
    showingCarouselAspectMenu: Boolean,
    carouselUsesSquare: Boolean,
    carouselNonSquareRes: Int,
    onToggleAspectMenu: () -> Unit,
    onApplyCarouselAspect: (Boolean) -> Unit,
    ink: Color,
    currentEdits: PhotoEdits,
    onCurrentEditsChange: (PhotoEdits) -> Unit,
) {
    val cardAspect = item.feedCrop?.cardAspectValue ?: item.aspectRatio.value
    val feedCrop = item.feedCrop
    val canReframe = isCarousel &&
        page == currentMediaIndex &&
        selectedTool == null &&
        bottomTab == null &&
        feedCrop != null &&
        (item.isVideo || immersiveBitmap != null || item.immersiveUri != null)
    val liveCrop = rememberUpdatedState(feedCrop)
    val liveSizes = rememberUpdatedState(immersivePixelSizes)
    val liveApply = rememberUpdatedState(onApplyFeedCrop)
    val liveFinish = rememberUpdatedState(onFinishReframe)
    val shownPreview = if (page == currentMediaIndex && !canReframe) previewBitmap else null
    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .aspectRatio(cardAspect),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (canReframe && feedCrop != null) {
                            Modifier.pointerInput(item.id, cardAspect, immersiveBitmap?.width, immersiveBitmap?.height) {
                                val cardWidthPx = size.width.toFloat().coerceAtLeast(1f)
                                val cardHeightPx = size.height.toFloat().coerceAtLeast(1f)
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val crop0 = liveCrop.value ?: return@awaitEachGesture
                                    var dragBase = crop0
                                    var zoomBase: MediaItemFeedCrop? = null
                                    var accPanX = 0f
                                    var accPanY = 0f
                                    var accZoom = 1f
                                    var lastCentroid = down.position
                                    var lastSpread = 0f
                                    var moved = false
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.isEmpty()) break
                                            val centroid = androidx.compose.ui.geometry.Offset(
                                                pressed.map { it.position.x }.average().toFloat(),
                                                pressed.map { it.position.y }.average().toFloat(),
                                            )
                                            accPanX += centroid.x - lastCentroid.x
                                            accPanY += centroid.y - lastCentroid.y
                                            lastCentroid = centroid
                                            if (pressed.size >= 2) {
                                                val spread = hypot(
                                                    (pressed[0].position.x - pressed[1].position.x).toDouble(),
                                                    (pressed[0].position.y - pressed[1].position.y).toDouble(),
                                                ).toFloat()
                                                if (lastSpread > 1f && spread > 1f) {
                                                    if (zoomBase == null) zoomBase = dragBase
                                                    accZoom = (accZoom * (spread / lastSpread))
                                                        .coerceIn(0.25f, MomentFeedCrop.maxScale)
                                                    moved = true
                                                }
                                                lastSpread = spread
                                            } else {
                                                lastSpread = 0f
                                            }
                                            if (!moved && hypot(accPanX.toDouble(), accPanY.toDouble()) < 4.0) {
                                                continue
                                            }
                                            moved = true
                                            event.changes.forEach { it.consume() }
                                            val imgPx = liveSizes.value[item.id]
                                                ?: (immersiveBitmap?.let { it.width.toFloat() to it.height.toFloat() }
                                                    ?: (1080f to 1080f / max(cardAspect, 0.01f)))
                                            var next = zoomBase?.let {
                                                MomentFeedCrop.scaled(it, accZoom, imgPx.first, imgPx.second)
                                            } ?: dragBase
                                            next = MomentFeedCrop.translated(
                                                next,
                                                -accPanX * dragBase.width / cardWidthPx.toDouble(),
                                                -accPanY * dragBase.height / cardHeightPx.toDouble(),
                                            )
                                            liveApply.value(next)
                                        }
                                    } finally {
                                        if (moved) liveFinish.value()
                                    }
                                }
                            }
                        } else Modifier,
                    ),
            ) {
                if (shownPreview != null) {
                    Image(
                        bitmap = shownPreview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (canReframe && feedCrop != null) {
                    if (immersiveBitmap != null) {
                        val source = immersiveBitmap.asImageBitmap()
                        Canvas(Modifier.fillMaxSize()) {
                            val destinationWidth = size.width / feedCrop.width.toFloat()
                            val destinationHeight = size.height / feedCrop.height.toFloat()
                            drawImage(
                                image = source,
                                dstOffset = IntOffset(
                                    x = (-destinationWidth * feedCrop.x.toFloat()).roundToInt(),
                                    y = (-destinationHeight * feedCrop.y.toFloat()).roundToInt(),
                                ),
                                dstSize = IntSize(
                                    width = destinationWidth.roundToInt().coerceAtLeast(1),
                                    height = destinationHeight.roundToInt().coerceAtLeast(1),
                                ),
                            )
                        }
                    }
                } else if (item.isVideo && feedCrop != null) {
                    NormalizedMediaCropContainer(
                        feedCrop = feedCrop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AsyncImage(
                            model = item.thumbnailUri ?: item.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (page == currentMediaIndex && selectedTool == PhotoEditTool.ADJUST) {
                    AdjustGridOverlay(Modifier.fillMaxSize())
                }
                if (page == currentMediaIndex && selectedTool == PhotoEditTool.BLUR) {
                    BlurInteractionLayer(
                        edits = currentEdits,
                        onChange = onCurrentEditsChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (isCarousel && page == currentMediaIndex) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.creator_carousel_remove), tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Box(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    CarouselAspectButton(
                        showingMenu = showingCarouselAspectMenu,
                        usesSquare = carouselUsesSquare,
                        nonSquareRes = carouselNonSquareRes,
                        ink = ink,
                        onToggle = onToggleAspectMenu,
                        onPick = onApplyCarouselAspect,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustGridOverlay(modifier: Modifier = Modifier) {
    Box(modifier) {
        val line = Color.White.copy(alpha = 0.55f)
        Column(Modifier.fillMaxSize()) {
            repeat(2) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
            }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxSize()) {
            repeat(2) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxSize().width(1.dp).background(line))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BlurInteractionLayer(
    edits: PhotoEdits,
    onChange: (PhotoEdits) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.pointerInput(edits.tiltShiftMode) {
            detectDragGestures { change, _ ->
                val x = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                val y = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                onChange(edits.copy(tiltShiftCenterX = x.toDouble(), tiltShiftCenterY = y.toDouble()))
            }
        },
    )
}

@Composable
private fun CarouselAspectButton(
    showingMenu: Boolean,
    usesSquare: Boolean,
    nonSquareRes: Int,
    ink: Color,
    onToggle: () -> Unit,
    onPick: (Boolean) -> Unit,
) {
    Box {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (usesSquare) Icons.Filled.CropSquare else Icons.Filled.Crop,
                contentDescription = stringResource(if (usesSquare) R.string.creator_carousel_square else nonSquareRes),
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
        if (showingMenu) {
            Column(
                Modifier
                    .offset(y = (-44).dp)
                    .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = true)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CarouselAspectMenuRow(
                    titleRes = nonSquareRes,
                    selected = !usesSquare,
                    ink = ink,
                    onClick = { onPick(false) },
                )
                CarouselAspectMenuRow(
                    titleRes = R.string.creator_carousel_square,
                    selected = usesSquare,
                    ink = ink,
                    onClick = { onPick(true) },
                )
            }
        }
    }
}

@Composable
private fun CarouselAspectMenuRow(
    titleRes: Int,
    selected: Boolean,
    ink: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(titleRes), color = ink, fontSize = 17.sp)
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = ink, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
private fun EditorMiniCard(
    titleRes: Int,
    icon: ImageVector,
    enabled: Boolean,
    ink: Color,
    inkMuted: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier else Modifier),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ink.copy(alpha = if (isDark) 0.12f else 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ink.copy(alpha = if (enabled) 1f else 0.34f), modifier = Modifier.size(17.dp))
        }
        Text(stringResource(titleRes), color = inkMuted.copy(alpha = if (enabled) 1f else 0.34f), fontSize = 11.sp)
    }
}

@Composable
private fun EditPanel(
    currentEdits: PhotoEdits,
    selectedTool: PhotoEditTool?,
    adjustAxis: PhotoAdjustAxis,
    ink: Color,
    inkMuted: Color,
    canvasColor: Color,
    isDark: Boolean,
    onEditsChange: (PhotoEdits) -> Unit,
    onToolChange: (PhotoEditTool?) -> Unit,
    onAxisChange: (PhotoAdjustAxis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (selectedTool) {
            PhotoEditTool.ADJUST -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                ) {
                    PhotoAdjustAxis.entries.forEach { axis ->
                        val on = adjustAxis == axis
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                HapticManager.shared.lightImpact()
                                onAxisChange(axis)
                            },
                        ) {
                            Text(
                                stringResource(axis.titleRes),
                                color = if (on) ink else inkMuted,
                                fontSize = 10.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                val axisValue = when (adjustAxis) {
                    PhotoAdjustAxis.STRAIGHTEN -> currentEdits.straighten
                    PhotoAdjustAxis.VERTICAL -> currentEdits.verticalPerspective
                    PhotoAdjustAxis.HORIZONTAL -> currentEdits.horizontalPerspective
                }
                EditorValueSlider(
                    value = axisValue,
                    range = -1.0..1.0,
                    bipolar = true,
                    ink = ink,
                    onChange = {
                        onEditsChange(
                            when (adjustAxis) {
                                PhotoAdjustAxis.STRAIGHTEN -> currentEdits.copy(straighten = it)
                                PhotoAdjustAxis.VERTICAL -> currentEdits.copy(verticalPerspective = it)
                                PhotoAdjustAxis.HORIZONTAL -> currentEdits.copy(horizontalPerspective = it)
                            },
                        )
                    },
                )
            }
            PhotoEditTool.COLOR -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                ) {
                    listOf(PhotoTintTarget.SHADOWS to R.string.creator_adjust_shadows, PhotoTintTarget.HIGHLIGHTS to R.string.creator_adjust_highlights).forEach { (target, res) ->
                        val on = currentEdits.tintTarget == target
                        Text(
                            stringResource(res),
                            color = if (on) ink else inkMuted,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clickable { onEditsChange(currentEdits.copy(tintTarget = target)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    PhotoTintColor.entries.forEach { tint ->
                        val selected = (if (currentEdits.tintTarget == PhotoTintTarget.SHADOWS) currentEdits.shadowTint else currentEdits.highlightTint) == tint
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(tint.color)
                                .then(if (selected) Modifier.border(2.dp, ink, CircleShape) else Modifier)
                                .clickable {
                                    HapticManager.shared.lightImpact()
                                    onEditsChange(
                                        if (currentEdits.tintTarget == PhotoTintTarget.SHADOWS) {
                                            currentEdits.copy(shadowTint = if (currentEdits.shadowTint == tint) null else tint)
                                        } else {
                                            currentEdits.copy(highlightTint = if (currentEdits.highlightTint == tint) null else tint)
                                        },
                                    )
                                },
                        )
                    }
                }
                EditorValueSlider(
                    value = if (currentEdits.tintTarget == PhotoTintTarget.SHADOWS) currentEdits.shadowTintAmount else currentEdits.highlightTintAmount,
                    range = 0.0..1.0,
                    bipolar = false,
                    ink = ink,
                    onChange = {
                        onEditsChange(
                            if (currentEdits.tintTarget == PhotoTintTarget.SHADOWS) currentEdits.copy(shadowTintAmount = it)
                            else currentEdits.copy(highlightTintAmount = it),
                        )
                    },
                )
            }
            PhotoEditTool.BLUR -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                ) {
                    listOf(TiltShiftMode.RADIAL to R.string.creator_adjust_radial, TiltShiftMode.LINEAR to R.string.creator_adjust_linear).forEach { (mode, res) ->
                        val on = currentEdits.tiltShiftMode == mode
                        Text(
                            stringResource(res),
                            color = if (on) ink else inkMuted,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clickable {
                                HapticManager.shared.lightImpact()
                                onEditsChange(currentEdits.copy(tiltShiftMode = mode))
                            },
                        )
                    }
                }
                EditorValueSlider(
                    value = currentEdits.tiltShiftAmount,
                    range = 0.0..1.0,
                    bipolar = false,
                    ink = ink,
                    onChange = { onEditsChange(currentEdits.copy(tiltShiftAmount = it)) },
                )
            }
            null -> {}
            else -> {
                val value = sliderValue(currentEdits, selectedTool)
                EditorValueSlider(
                    value = value,
                    range = if (selectedTool.usesZeroToHundred) 0.0..1.0 else -1.0..1.0,
                    bipolar = !selectedTool.usesZeroToHundred,
                    ink = ink,
                    onChange = { onEditsChange(copySlider(currentEdits, selectedTool, it)) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(PhotoEditTool.entries, key = { it.name }) { tool ->
                val selected = selectedTool == tool
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .width(66.dp)
                        .clickable {
                            HapticManager.shared.lightImpact()
                            if (selectedTool == tool) {
                                onToolChange(null)
                                return@clickable
                            }
                            onToolChange(tool)
                            if (tool == PhotoEditTool.ADJUST) onAxisChange(PhotoAdjustAxis.STRAIGHTEN)
                            if (tool == PhotoEditTool.LUX && currentEdits.lux == 0.0) {
                                onEditsChange(currentEdits.copy(lux = 0.5))
                            }
                            if (tool == PhotoEditTool.BLUR && currentEdits.tiltShiftMode == null) {
                                onEditsChange(currentEdits.copy(tiltShiftMode = TiltShiftMode.RADIAL))
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (selected) ink else ink.copy(alpha = if (isDark) 0.12f else 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tool.icon(),
                                null,
                                tint = if (selected) canvasColor else ink,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        if (currentEdits.isApplied(tool) && !selected) {
                            Box(
                                Modifier
                                    .offset(y = 6.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(inkMuted),
                            )
                        }
                    }
                    Text(
                        stringResource(tool.titleRes),
                        color = if (selected) ink else inkMuted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorValueSlider(
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    bipolar: Boolean,
    ink: Color,
    onChange: (Double) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink),
        )
        Text(
            "${(value * 100).toInt()}",
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .width(36.dp)
                .clickable { onChange(if (bipolar) 0.0 else 0.0) },
        )
    }
}

private fun sliderValue(edits: PhotoEdits, tool: PhotoEditTool): Double = when (tool) {
    PhotoEditTool.LUX -> edits.lux
    PhotoEditTool.BRIGHTNESS -> edits.brightness
    PhotoEditTool.CONTRAST -> edits.contrast
    PhotoEditTool.TEXTURE -> edits.texture
    PhotoEditTool.WARMTH -> edits.warmth
    PhotoEditTool.SATURATION -> edits.saturation
    PhotoEditTool.FADE -> edits.fade
    PhotoEditTool.HIGHLIGHTS -> edits.highlights
    PhotoEditTool.SHADOWS -> edits.shadows
    PhotoEditTool.VIGNETTE -> edits.vignette
    PhotoEditTool.SHARPEN -> edits.sharpen
    else -> 0.0
}

private fun copySlider(edits: PhotoEdits, tool: PhotoEditTool, value: Double): PhotoEdits = when (tool) {
    PhotoEditTool.LUX -> edits.copy(lux = value)
    PhotoEditTool.BRIGHTNESS -> edits.copy(brightness = value)
    PhotoEditTool.CONTRAST -> edits.copy(contrast = value)
    PhotoEditTool.TEXTURE -> edits.copy(texture = value)
    PhotoEditTool.WARMTH -> edits.copy(warmth = value)
    PhotoEditTool.SATURATION -> edits.copy(saturation = value)
    PhotoEditTool.FADE -> edits.copy(fade = value)
    PhotoEditTool.HIGHLIGHTS -> edits.copy(highlights = value)
    PhotoEditTool.SHADOWS -> edits.copy(shadows = value)
    PhotoEditTool.VIGNETTE -> edits.copy(vignette = value)
    PhotoEditTool.SHARPEN -> edits.copy(sharpen = value)
    else -> edits
}

private fun PhotoEditTool.icon(): ImageVector = when (this) {
    PhotoEditTool.ADJUST -> Icons.Filled.Tune
    PhotoEditTool.LUX -> Icons.Filled.WbSunny
    PhotoEditTool.BRIGHTNESS -> Icons.Filled.WbSunny
    PhotoEditTool.CONTRAST -> Icons.Filled.Tune
    PhotoEditTool.TEXTURE -> Icons.Filled.AutoAwesome
    PhotoEditTool.WARMTH -> Icons.Filled.WbTwilight
    PhotoEditTool.SATURATION -> Icons.Filled.WaterDrop
    PhotoEditTool.COLOR -> Icons.Filled.Palette
    PhotoEditTool.FADE -> Icons.Filled.Brightness6
    PhotoEditTool.HIGHLIGHTS -> Icons.Filled.WbSunny
    PhotoEditTool.SHADOWS -> Icons.Filled.NightsStay
    PhotoEditTool.VIGNETTE -> Icons.Filled.FilterCenterFocus
    PhotoEditTool.BLUR -> Icons.Filled.BlurCircular
    PhotoEditTool.SHARPEN -> Icons.Filled.AutoAwesome
}
