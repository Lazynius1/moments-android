package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.FilterSettings
import com.moments.android.services.content.FilterService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Port de `MediaEditingView.swift`.
 * Crop + modo filtros (FilterOption / FilterService).
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
    var currentMediaIndex by remember { mutableIntStateOf(0) }
    var showingCrop by remember { mutableStateOf(false) }
    var showingFilterToolbar by remember { mutableStateOf(false) }
    var appliedFilters by remember { mutableStateOf<Map<String, FilterSettings>>(emptyMap()) }
    var tempFilterType by remember { mutableStateOf(FilterService.FilterType.NORMAL) }
    var tempFilterIntensity by remember { mutableDoubleStateOf(1.0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filterJob by remember { mutableStateOf<Job?>(null) }
    val pagerState = rememberPagerState(pageCount = { selectedMediaItems.size })

    LaunchedEffect(pagerState.currentPage) {
        currentMediaIndex = pagerState.currentPage
    }

    val current = selectedMediaItems.getOrNull(currentMediaIndex) ?: selectedMediaItems.first()
    val recommended = current.recommendedAspectRatio ?: current.aspectRatio

    fun updatePreview() {
        filterJob?.cancel()
        if (tempFilterType == FilterService.FilterType.NORMAL) {
            previewBitmap = null
            return
        }
        val uri = current.uri
        val type = tempFilterType
        val intensity = tempFilterIntensity
        filterJob = scope.launch {
            delay(45)
            val filtered = withContext(Dispatchers.Default) {
                val base = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext null
                FilterService.applyFilter(type, base, intensity)
            }
            previewBitmap = filtered
        }
    }

    fun enterFilterMode() {
        val settings = appliedFilters[current.id]
        if (settings != null) {
            tempFilterType = FilterService.FilterType.from(settings.name)
            tempFilterIntensity = settings.intensity
        } else {
            tempFilterType = FilterService.FilterType.NORMAL
            tempFilterIntensity = 1.0
        }
        showingFilterToolbar = true
        updatePreview()
    }

    fun cancelFilter() {
        filterJob?.cancel()
        showingFilterToolbar = false
        previewBitmap = null
    }

    fun applyFilterPermanent() {
        filterJob?.cancel()
        val preview = previewBitmap
        appliedFilters = appliedFilters + (current.id to FilterSettings(tempFilterType.raw, tempFilterIntensity))
        if (preview != null && tempFilterType != FilterService.FilterType.NORMAL) {
            scope.launch {
                val outUri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "creator_filters").also { it.mkdirs() }
                    val file = File(dir, "filter_${UUID.randomUUID()}.jpg")
                    FileOutputStream(file).use { preview.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    Uri.fromFile(file)
                }
                val updated = selectedMediaItems.toMutableList()
                updated[currentMediaIndex] = current.copy(uri = outUri, hasEdits = true)
                onSelectedMediaItemsChange(updated)
                showingFilterToolbar = false
                previewBitmap = null
            }
        } else {
            showingFilterToolbar = false
            previewBitmap = null
        }
    }

    if (showingCrop) {
        CropViewWrapper(
            imageUri = current.uri,
            aspectRatio = current.aspectRatio,
            allowFreeCrop = true,
            onComplete = { uri, newRatio ->
                val updated = selectedMediaItems.toMutableList()
                updated[currentMediaIndex] = current.copy(uri = uri, aspectRatio = newRatio, hasEdits = true)
                onSelectedMediaItemsChange(updated)
                showingCrop = false
            },
            onCancel = { showingCrop = false },
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = current.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.35f),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)))

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent)))
                    .padding(16.dp),
            ) {
                if (showingFilterToolbar) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(0.1f))
                            .clickable { cancelFilter() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.creator_edit),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Text(
                        stringResource(R.string.common_done),
                        color = Color(0xFFE91E63),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFE91E63).copy(0.1f))
                            .clickable { applyFilterPermanent() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .align(Alignment.CenterStart)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { onCurrentFlowChange(CreatorFlow.MEDIA_SELECTION) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        stringResource(R.string.creator_edit),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Row(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFFFF9800))))
                            .clickable { onCurrentFlowChange(CreatorFlow.CAPTION_AND_DETAILS) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.creator_next), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f),
                contentAlignment = Alignment.Center,
            ) {
                if (showingFilterToolbar && previewBitmap != null && pagerState.currentPage == currentMediaIndex) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        userScrollEnabled = !showingFilterToolbar,
                    ) { page ->
                        AsyncImage(
                            model = selectedMediaItems[page].uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                if (!showingFilterToolbar &&
                    (recommended != CreatorAspectRatio.SQUARE || current.aspectRatio != recommended)
                ) {
                    Text(
                        stringResource(R.string.creator_recommended_dimensions),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .background(Color.Black.copy(0.45f), RoundedCornerShape(50))
                            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))),
            ) {
                if (showingFilterToolbar) {
                    Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (tempFilterType != FilterService.FilterType.NORMAL) {
                            Column(Modifier.padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row {
                                    Text(
                                        stringResource(R.string.creator_intensity),
                                        color = Color.White.copy(0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${(tempFilterIntensity * 100).toInt()}%",
                                        color = Color(0xFFE91E63),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Slider(
                                    value = tempFilterIntensity.toFloat(),
                                    onValueChange = {
                                        tempFilterIntensity = it.toDouble()
                                        updatePreview()
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFE91E63),
                                        activeTrackColor = Color(0xFFE91E63),
                                    ),
                                )
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 25.dp),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            modifier = Modifier.height(140.dp),
                        ) {
                            items(FilterService.FilterType.entries, key = { it.raw }) { filter ->
                                FilterOption(
                                    sourceUri = current.uri,
                                    filter = filter,
                                    isSelected = tempFilterType == filter,
                                    onTap = {
                                        HapticManager.shared.lightImpact()
                                        tempFilterType = filter
                                        if (filter == FilterService.FilterType.NORMAL) {
                                            tempFilterIntensity = 1.0
                                        }
                                        updatePreview()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(bottom = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(selectedMediaItems, key = { _, m -> m.id }) { index, item ->
                            val selected = index == currentMediaIndex
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(55.dp)
                                    .scale(if (selected) 1.05f else 0.95f)
                                    .alpha(if (selected) 1f else 0.6f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                2.dp,
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFFFF9800)),
                                                ),
                                                RoundedCornerShape(12.dp),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        currentMediaIndex = index
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.padding(start = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            listOf(
                                CreatorAspectRatio.SQUARE,
                                CreatorAspectRatio.PORTRAIT,
                                CreatorAspectRatio.LANDSCAPE,
                            ).forEach { ratio ->
                                AspectRatioChip(
                                    ratio = ratio,
                                    selected = current.aspectRatio == ratio,
                                    recommended = ratio == recommended,
                                    onClick = {
                                        HapticManager.shared.lightImpact()
                                        val updated = selectedMediaItems.toMutableList()
                                        updated[currentMediaIndex] = current.copy(aspectRatio = ratio)
                                        onSelectedMediaItemsChange(updated)
                                        showingCrop = true
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.padding(end = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ToolIconButton(Icons.Filled.Crop) { showingCrop = true }
                            ToolIconButton(Icons.Filled.Filter) { enterFilterMode() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatioChip(
    ratio: CreatorAspectRatio,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    val stroke = when {
        selected -> Color(0xFFE91E63)
        recommended -> Color(0xFF4CAF50).copy(0.6f)
        else -> Color.White.copy(0.3f)
    }
    val w = when (ratio) {
        CreatorAspectRatio.LANDSCAPE -> 35.dp
        CreatorAspectRatio.SQUARE -> 25.dp
        else -> 20.dp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(w)
                .height(25.dp)
                .border(if (selected) 2.dp else 1.dp, stroke, RoundedCornerShape(4.dp)),
        )
        Text(
            ratio.displayName,
            color = if (selected) Color(0xFFE91E63) else Color.White.copy(0.6f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ToolIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable {
                HapticManager.shared.lightImpact()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
