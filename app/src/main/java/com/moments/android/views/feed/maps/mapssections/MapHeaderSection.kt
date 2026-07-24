package com.moments.android.views.feed.maps.mapssections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.FeedTeal
import com.moments.android.views.feed.maps.MapDiscoverContentFilter

enum class MapHeaderCloseStyle {
    Discover,
    Location,
}

/** Header chrome compartido entre DiscoverMapView y LocationMapView (iOS header pills). */
@Composable
fun MapHeaderSection(
    title: String,
    subtitle: String,
    closeStyle: MapHeaderCloseStyle,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else FeedInk
    val tertiary = primary.copy(alpha = 0.55f)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (closeStyle) {
                        MapHeaderCloseStyle.Discover -> Icons.Filled.Close
                        MapHeaderCloseStyle.Location -> Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = title,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = tertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MapFilterChipsSection(
    selected: MapDiscoverContentFilter,
    onSelect: (MapDiscoverContentFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else FeedInk

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapDiscoverContentFilter.entries.forEach { filter ->
            val selectedChip = filter == selected
            Text(
                text = stringResource(filter.titleKeyRes),
                color = if (selectedChip) Color.White else primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .momentsChromeGlass(CircleShape, interactive = !selectedChip)
                    .background(
                        if (selectedChip) FeedTeal else Color.Transparent,
                        CircleShape,
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
