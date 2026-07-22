package com.moments.android.views.creator.creatorscreens

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FilterService

/**
 * Port de `StoryFilterSelectorView.swift` / `FilterSelectorView`.
 * Reutiliza `FilterOption` (mismo FilterService que MediaEditing).
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
    val labelColor = if (isDark) Color.White else Color.Black.copy(0.82f)

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            filters.forEach { filter ->
                if (baseUri != null) {
                    FilterOption(
                        sourceUri = baseUri,
                        filter = filter,
                        isSelected = selectedFilter == filter,
                        onTap = { onFilterChange(filter) },
                    )
                }
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
