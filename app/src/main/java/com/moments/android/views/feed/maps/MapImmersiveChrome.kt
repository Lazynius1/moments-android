package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.feed.rememberAdaptiveColors

/** Compose chrome shared by the two native map entry points. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapImmersiveChrome(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onRecenter: () -> Unit,
    onOpenContent: () -> Unit,
    modifier: Modifier = Modifier,
    showsSearchArea: Boolean = false,
    onSearchArea: () -> Unit = {},
    contentFilter: MapDiscoverContentFilter? = null,
    onFilter: ((MapDiscoverContentFilter) -> Unit)? = null,
    showsDock: Boolean = true,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = rememberAdaptiveColors()
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp).background(colors.controlSurface, CircleShape)) {
                Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = colors.primary)
            }
            Row(
                Modifier.weight(1f).background(colors.controlSurface, RoundedCornerShape(50)).padding(horizontal = 14.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) Text(stringResource(R.string.maps_search_placeholder), color = colors.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onSearchTextChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = colors.secondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { keyboard?.hide(); onRecenter() }, modifier = Modifier.size(48.dp).background(colors.controlSurface, CircleShape)) {
                Icon(Icons.Filled.MyLocation, stringResource(R.string.maps_chrome_recenter), tint = colors.primary)
            }
        }
        if (contentFilter != null && onFilter != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapDiscoverContentFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = contentFilter == filter,
                        onClick = { keyboard?.hide(); onFilter(filter) },
                        label = { Text(stringResource(filter.titleKeyRes)) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.controlSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }
        if (showsSearchArea) {
            Button(
                onClick = { keyboard?.hide(); onSearchArea() },
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.controlSurface, contentColor = colors.primary),
            ) {
                Text(stringResource(R.string.maps_search_this_area))
            }
        }
        if (showsDock) Row(
            Modifier.fillMaxWidth().background(colors.controlSurface, RoundedCornerShape(26.dp))
                .clickable { keyboard?.hide(); onOpenContent() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Map, null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = colors.secondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
            else Icon(Icons.Filled.KeyboardArrowUp, null, tint = colors.primary)
        }
    }
}
