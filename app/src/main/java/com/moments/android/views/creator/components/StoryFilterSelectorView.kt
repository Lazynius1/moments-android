package com.moments.android.views.creator.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FilterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port de `StoryFilterSelectorView.swift` / `FilterSelectorView`.
 */
@Composable
fun StoryFilterSelectorView(
    selectedFilter: FilterService.FilterType,
    onFilterChange: (FilterService.FilterType) -> Unit,
    baseUri: Uri?,
    filters: List<FilterService.FilterType> = FilterService.FilterType.entries,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val labelColor = StoryEditorChromeColor.icon(isDark)

    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            filters.forEach { filter ->
                FilterItemView(
                    type = filter,
                    isSelected = selectedFilter == filter,
                    baseUri = baseUri,
                    onTap = { onFilterChange(filter) },
                )
            }
        }

        Text(
            selectedFilter.raw,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Port de `FilterItemView` en `StoryFilterSelectorView.swift`. */
@Composable
fun FilterItemView(
    type: FilterService.FilterType,
    isSelected: Boolean,
    baseUri: Uri?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var preview by remember(baseUri, type) { mutableStateOf<Bitmap?>(null) }
    val shape = RoundedCornerShape(10.dp)

    LaunchedEffect(baseUri, type) {
        val uri = baseUri ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            val base = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@withContext null
            val thumb = Bitmap.createScaledBitmap(base, 60, 80, true)
            if (base !== thumb) base.recycle()
            FilterService.applyFilterToThumbnail(type, thumb)
        }
    }

    Column(
        modifier.clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(60.dp, 80.dp)
                .then(
                    if (isSelected) {
                        Modifier.shadow(4.dp, shape)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, Color.White, shape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview!!.asImageBitmap(),
                    contentDescription = type.raw,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Gray.copy(alpha = 0.3f)),
                )
            }
        }

        Text(
            type.raw,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
