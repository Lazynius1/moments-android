package com.moments.android.views.settings.savedmoments

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.ChatVideoPlayBadge
import com.moments.android.views.messaging.components.momentsScrollEdgeChrome
import com.moments.android.views.settings.SettingsSearchField
import com.moments.android.views.profile.core.sections.MomentCarouselIndicatorIcon
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.profile.core.sections.momentZoomNavigationSurface
import com.moments.android.views.profile.core.sections.profileGridNavigationChrome
import com.moments.android.views.profile.core.sections.profileMomentZoomSource
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsToolbarBackButton
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private data class IdentifiedSavedMoment(
    val index: Int,
    val moment: Moment,
) {
    val id: String
        get() = moment.id ?: listOf(
            moment.authorId,
            moment.timestamp.time.toString(),
            moment.imagePath.orEmpty(),
            moment.videoUrl.orEmpty(),
        ).joinToString("|")
}

/**
 * Port de `SavedMomentsView.swift` (struct principal + `SavedMomentGridCard`).
 * Detalle (`ModernSavedMomentsDetailView`+) se abre vía [MomentZoomDetailDestination].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMomentsView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel = remember { SavedMomentsViewModel() }

    var searchText by remember { mutableStateOf("") }
    var mediaFilter by remember { mutableStateOf(SavedMediaFilter.ALL) }
    var collectionFilter by remember { mutableStateOf(SavedCollectionFilter.ALL) }
    var sortMode by remember { mutableStateOf(SavedSortMode.NEWEST) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMomentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveSelectionAlert by remember { mutableStateOf(false) }
    var restrictedMomentToRemove by remember { mutableStateOf<Moment?>(null) }
    var showingRestrictedRemoveAlert by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    BackHandler {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedMomentIds = emptySet()
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.moments.isEmpty() && !viewModel.isLoading) {
            viewModel.loadSavedMoments()
        }
    }

    val filteredMoments = remember(
        viewModel.moments,
        searchText,
        mediaFilter,
        collectionFilter,
        sortMode,
    ) {
        var list = viewModel.moments
        val query = searchText.trim()
        if (query.isNotEmpty()) {
            val q = query.lowercase()
            list = list.filter {
                it.username.lowercase().contains(q) ||
                    it.content.lowercase().contains(q) ||
                    (it.location?.lowercase()?.contains(q) == true)
            }
        }
        list = when (mediaFilter) {
            SavedMediaFilter.ALL -> list
            SavedMediaFilter.PHOTOS -> list.filter { hasImage(it) }
            SavedMediaFilter.VIDEOS -> list.filter { hasVideo(it) }
        }
        list = when (collectionFilter) {
            SavedCollectionFilter.ALL -> list
            SavedCollectionFilter.LOCATION -> list.filter { !it.location.isNullOrEmpty() }
            SavedCollectionFilter.TEXT -> list.filter { it.content.trim().isNotEmpty() }
            SavedCollectionFilter.MULTIPLE -> list.filter { (it.mediaItems?.size ?: 0) > 1 }
        }
        when (sortMode) {
            SavedSortMode.NEWEST -> list.sortedByDescending { it.timestamp }
            SavedSortMode.OLDEST -> list.sortedBy { it.timestamp }
            SavedSortMode.AUTHOR -> list.sortedBy { it.username.lowercase() }
        }
    }

    val identifiedFilteredMoments = remember(filteredMoments) {
        filteredMoments.mapIndexed { index, moment -> IdentifiedSavedMoment(index, moment) }
    }

    LaunchedEffect(filteredMoments.map { it.id.orEmpty() }) {
        val valid = filteredMoments.mapNotNull { it.id }.toSet()
        selectedMomentIds = selectedMomentIds.filter { it in valid }.toSet()
    }

    fun accessibleMomentsPool(): List<Moment> =
        filteredMoments.filter { candidate ->
            val id = candidate.id ?: return@filter false
            viewModel.visibilityByMomentId[id] ?: true
        }

    fun openDetailForAccessibleMoments(momentId: String, currentList: List<Moment>) {
        val accessible = currentList.filter { candidate ->
            val id = candidate.id ?: return@filter false
            viewModel.visibilityByMomentId[id] ?: true
        }
        val resolvedIndex = accessible.indexOfFirst { it.id == momentId }
        if (resolvedIndex < 0) return
        val moment = accessible.getOrNull(resolvedIndex) ?: return
        MomentZoomOpener.open(
            moment = moment,
            moments = accessible,
            initialIndex = resolvedIndex,
            presentation = MomentZoomPresentationKind.Saved,
            setDestination = { zoomDestination = it },
            zoomIDPrefix = "saved",
        )
    }

    fun toggleSelection(moment: Moment) {
        val momentId = moment.id ?: return
        val canView = viewModel.visibilityByMomentId[momentId]
        if (canView == false) {
            HapticManager.shared.warning()
            return
        }
        selectedMomentIds = if (momentId in selectedMomentIds) {
            selectedMomentIds - momentId
        } else {
            selectedMomentIds + momentId
        }
    }

    fun handleTap(moment: Moment, currentList: List<Moment>) {
        if (isSelectionMode) {
            toggleSelection(moment)
            return
        }
        val momentId = moment.id ?: return
        val canView = viewModel.visibilityByMomentId[momentId]
        if (canView == false) {
            restrictedMomentToRemove = moment
            showingRestrictedRemoveAlert = true
            return
        }
        if (canView == null) {
            viewModel.refreshVisibilityForMoment(moment) { visible ->
                if (!visible) {
                    HapticManager.shared.warning()
                    return@refreshVisibilityForMoment
                }
                openDetailForAccessibleMoments(momentId, currentList)
            }
            return
        }
        openDetailForAccessibleMoments(momentId, currentList)
    }

    fun removeSelected() {
        selectedMomentIds.forEach { viewModel.removeMoment(it) }
        selectedMomentIds = emptySet()
        isSelectionMode = false
    }

    fun shareSelectedLinks() {
        val selected = viewModel.moments.filter { moment ->
            val id = moment.id ?: return@filter false
            id in selectedMomentIds
        }
        val urls = selected.mapNotNull { moment ->
            val momentId = moment.id ?: return@mapNotNull null
            buildString {
                append("https://momentsapp.app/moment/$momentId")
                if (moment.authorId.isNotEmpty()) append("?a=${moment.authorId}")
            }
        }
        if (urls.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, urls.joinToString("\n"))
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    MomentsSharedTransitionLayout(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .momentZoomNavigationSurface(isDark)
            .momentsScrollEdgeChrome()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            SavedMomentsToolbar(
                title = stringResource(R.string.profile_tab_saved),
                isSelectionMode = isSelectionMode,
                textColor = textColor,
                onNavigateBack = onNavigateBack,
                onToggleSelection = {
                    isSelectionMode = !isSelectionMode
                    if (!isSelectionMode) selectedMomentIds = emptySet()
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    viewModel.isLoading && viewModel.moments.isEmpty() -> {
                        SavedMomentsLoading(secondaryColor)
                    }
                    viewModel.error != null && viewModel.moments.isEmpty() -> {
                        SavedMomentsError(
                            message = viewModel.error?.localizedMessage
                                ?: viewModel.error.toString(),
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            onRetry = { viewModel.loadSavedMoments() },
                        )
                    }
                    viewModel.moments.isEmpty() -> {
                        SavedMomentsEmpty(textColor, secondaryColor)
                    }
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    suspendCancellableCoroutine { cont ->
                                        viewModel.loadSavedMoments {
                                            cont.resume(Unit)
                                        }
                                    }
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                SavedMomentsSearchBar(
                                    searchText = searchText,
                                    onSearchTextChange = { searchText = it },
                                    textColor = textColor,
                                    secondaryColor = secondaryColor,
                                )
                                SavedMomentsFilterPanel(
                                    mediaFilter = mediaFilter,
                                    onMediaFilterChange = { mediaFilter = it },
                                    collectionFilter = collectionFilter,
                                    onCollectionFilterChange = { collectionFilter = it },
                                    sortMode = sortMode,
                                    onSortModeChange = { sortMode = it },
                                    sortMenuExpanded = sortMenuExpanded,
                                    onSortMenuExpandedChange = { sortMenuExpanded = it },
                                    textColor = textColor,
                                    isDark = isDark,
                                )
                                if (filteredMoments.isEmpty()) {
                                    SavedMomentsFilteredEmpty(
                                        textColor = textColor,
                                        secondaryColor = secondaryColor,
                                        onClear = {
                                            searchText = ""
                                            mediaFilter = SavedMediaFilter.ALL
                                            collectionFilter = SavedCollectionFilter.ALL
                                        },
                                    )
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier
                                            .weight(1f)
                                            .profileGridNavigationChrome()
                                            .padding(horizontal = 10.dp),
                                        contentPadding = PaddingValues(
                                            bottom = if (isSelectionMode) 90.dp else 20.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        itemsIndexed(
                                            identifiedFilteredMoments,
                                            key = { _, item -> item.id },
                                        ) { _, identified ->
                                            val moment = identified.moment
                                            val momentId = identified.id
                                            val isRestricted =
                                                !(viewModel.visibilityByMomentId[momentId] ?: true)
                                            val isMutedRestriction =
                                                isRestricted && viewModel.isMomentFromMutedUser(moment)
                                            ScreenshotProtectedView(
                                                isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                                            ) {
                                                SavedMomentGridCard(
                                                    moment = moment,
                                                    isRestricted = isRestricted,
                                                    isMutedRestriction = isMutedRestriction,
                                                    isSelectionMode = isSelectionMode,
                                                    isSelected = momentId in selectedMomentIds,
                                                    zoomSourceID = ProfileMomentZoomNavigation.sourceID(
                                                        moment,
                                                        identified.index,
                                                        "saved-manager",
                                                    ),
                                                    onTap = {
                                                        handleTap(moment, filteredMoments)
                                                    },
                                                    onLongPress = {
                                                        if (!isSelectionMode) isSelectionMode = true
                                                        toggleSelection(moment)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSelectionMode) {
                    SavedMomentsSelectionBar(
                        count = selectedMomentIds.size,
                        textColor = textColor,
                        isDark = isDark,
                        enabled = selectedMomentIds.isNotEmpty(),
                        onShare = { shareSelectedLinks() },
                        onRemove = { showRemoveSelectionAlert = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp),
                    )
                }
            }
        }

        MomentsContainerTransformOverlay(visible = zoomDestination != null) {
            val destination = zoomDestination
            if (destination != null) {
                MomentZoomDetailDestination(
                    destination = destination,
                    moments = MomentZoomOpener.resolvedMoments(destination, accessibleMomentsPool()),
                    onDismiss = { zoomDestination = null },
                    onRemoveSavedMoment = { moment ->
                        moment.id?.let { viewModel.removeMoment(it) }
                    },
                )
            }
        }
    }
    } // MomentsSharedTransitionLayout

    if (showRemoveSelectionAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveSelectionAlert = false },
            title = { Text(stringResource(R.string.saved_moments_selection_remove_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.saved_moments_selection_remove_message,
                        selectedMomentIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeSelected()
                        showRemoveSelectionAlert = false
                    },
                ) {
                    Text(stringResource(R.string.saved_moments_remove_confirm), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveSelectionAlert = false }) {
                    Text(stringResource(R.string.saved_moments_cancel))
                }
            },
        )
    }

    if (showingRestrictedRemoveAlert) {
        val restricted = restrictedMomentToRemove
        val muted = restricted != null && viewModel.isMomentFromMutedUser(restricted)
        AlertDialog(
            onDismissRequest = {
                showingRestrictedRemoveAlert = false
                restrictedMomentToRemove = null
            },
            title = { Text(stringResource(R.string.saved_moments_remove_title)) },
            text = {
                Text(
                    stringResource(
                        if (muted) R.string.saved_moments_remove_message_muted
                        else R.string.saved_moments_remove_message_restricted,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restricted?.id?.let { viewModel.removeMoment(it) }
                        restrictedMomentToRemove = null
                        showingRestrictedRemoveAlert = false
                    },
                ) {
                    Text(stringResource(R.string.saved_moments_remove_confirm), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showingRestrictedRemoveAlert = false
                        restrictedMomentToRemove = null
                    },
                ) {
                    Text(stringResource(R.string.saved_moments_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavedMomentsToolbar(
    title: String,
    isSelectionMode: Boolean,
    textColor: Color,
    onNavigateBack: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val actionSlotWidth = 96.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(actionSlotWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            SettingsToolbarBackButton(onNavigateBack = onNavigateBack)
        }
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .width(actionSlotWidth)
                .widthIn(min = 48.dp)
                .clickable(onClick = onToggleSelection),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (isSelectionMode) R.string.saved_moments_cancel
                    else R.string.saved_moments_select,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelectionMode) Color(0xFFFF3B30) else textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SavedMomentsSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    textColor: Color,
    secondaryColor: Color,
) {
    SettingsSearchField(
        value = searchText,
        onValueChange = onSearchTextChange,
        placeholder = stringResource(R.string.saved_moments_search_placeholder),
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

@Composable
private fun SavedMomentsFilterPanel(
    mediaFilter: SavedMediaFilter,
    onMediaFilterChange: (SavedMediaFilter) -> Unit,
    collectionFilter: SavedCollectionFilter,
    onCollectionFilterChange: (SavedCollectionFilter) -> Unit,
    sortMode: SavedSortMode,
    onSortModeChange: (SavedSortMode) -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    textColor: Color,
    isDark: Boolean,
) {
    val stroke = Color.White.copy(alpha = if (isDark) 0.06f else 0.16f)
    Column(
        Modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SavedMediaFilter.entries.forEach { filter ->
                    val selected = mediaFilter == filter
                    Text(
                        text = stringResource(filter.titleRes),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (selected) {
                                    Modifier.momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                } else {
                                    Modifier
                                        .background(Color.Transparent)
                                        .border(1.dp, stroke, RoundedCornerShape(50))
                                },
                            )
                            .clickable { onMediaFilterChange(filter) }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                }
            }
            Box {
                Row(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable { onSortMenuExpandedChange(true) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.SwapVert, null, tint = textColor, modifier = Modifier.size(13.dp))
                    Text(
                        stringResource(sortMode.titleRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { onSortMenuExpandedChange(false) },
                ) {
                    SavedSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.titleRes)) },
                            onClick = {
                                onSortModeChange(mode)
                                onSortMenuExpandedChange(false)
                            },
                        )
                    }
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SavedCollectionFilter.entries) { filter ->
                val selected = collectionFilter == filter
                Text(
                    text = stringResource(filter.titleRes),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (selected) {
                                Modifier.momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            } else {
                                Modifier.border(1.dp, stroke, RoundedCornerShape(50))
                            },
                        )
                        .clickable { onCollectionFilterChange(filter) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun SavedMomentsSelectionBar(
    count: Int,
    textColor: Color,
    isDark: Boolean,
    enabled: Boolean,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (isDark) 0.14f else 0.3f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (count == 1) {
                stringResource(R.string.saved_moments_selection_count_single)
            } else {
                stringResource(R.string.saved_moments_selection_count, count)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onShare,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.SHARE,
                preset = AttachmentIconPreset.SHARE_INLINE,
                tintColor = if (enabled) textColor else textColor.copy(alpha = 0.38f),
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        ) {
            Icon(
                Icons.Default.BookmarkRemove,
                contentDescription = stringResource(R.string.saved_moments_remove),
                tint = if (enabled) Color(0xFFFF453A) else Color(0xFFFF453A).copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SavedMomentsLoading(secondaryColor: Color) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(36.dp), color = secondaryColor)
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.saved_moments_loading),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
        )
    }
}

@Composable
private fun SavedMomentsError(
    message: String,
    textColor: Color,
    secondaryColor: Color,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.WifiOff, null, tint = secondaryColor, modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(14.dp))
        Text(
            stringResource(R.string.saved_moments_error_title),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
        Spacer(Modifier.size(8.dp))
        Text(message, fontSize = 13.sp, color = secondaryColor, textAlign = TextAlign.Center)
        Spacer(Modifier.size(14.dp))
        Text(
            stringResource(R.string.saved_moments_retry),
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun SavedMomentsEmpty(textColor: Color, secondaryColor: Color) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.BookmarkBorder, null, tint = secondaryColor, modifier = Modifier.size(64.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.saved_moments_empty_title),
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.saved_moments_empty_description),
            fontSize = 15.sp,
            color = secondaryColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.saved_moments_empty_tip),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SavedMomentsFilteredEmpty(
    textColor: Color,
    secondaryColor: Color,
    onClear: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.FilterList, null, tint = secondaryColor, modifier = Modifier.size(36.dp))
        Text(
            stringResource(R.string.saved_moments_empty_filtered_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
        Text(
            stringResource(R.string.saved_moments_empty_filtered_description),
            fontSize = 13.sp,
            color = secondaryColor,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.saved_moments_clear_filters),
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .clickable(onClick = onClear)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

/** Port de `SavedMomentGridCard`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedMomentGridCard(
    moment: Moment,
    isRestricted: Boolean,
    isMutedRestriction: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    zoomSourceID: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .profileMomentZoomSource(zoomSourceID, cornerRadius = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (isRestricted) Modifier.blur(16.dp) else Modifier),
        ) {
            SavedMomentPreview(moment = moment, isRestricted = isRestricted, isDark = isDark)
        }

        if (isRestricted) {
            SavedRestrictedOverlay(isMutedRestriction = isMutedRestriction)
        } else if (moment.isCarouselMoment) {
            MomentCarouselIndicatorIcon(
                size = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }

        if (isSelectionMode && !isRestricted) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun SavedMomentPreview(moment: Moment, isRestricted: Boolean, isDark: Boolean) {
    val media = moment.primaryVisibleMediaItem
    when {
        media != null && media.type == MediaItem.MediaType.VIDEO -> {
            SavedVideoPreview(
                url = media.url,
                thumbnail = media.thumbnailUrl,
                isRestricted = isRestricted,
            )
        }
        media != null && media.url.isNotBlank() -> {
            AsyncImage(
                model = profileThumbnailUrl(media.url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.previewImageURLString.isNullOrBlank() -> {
            AsyncImage(
                model = profileThumbnailUrl(moment.previewImageURLString!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        !moment.previewVideoURLString.isNullOrBlank() -> {
            SavedVideoPreview(
                url = moment.previewVideoURLString!!,
                thumbnail = moment.previewImageURLString ?: moment.thumbnailUrl,
                isRestricted = isRestricted,
            )
        }
        else -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF007AFF), SettingsProfileColors.accent(isDark)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (moment.content.isNotEmpty()) {
                    Text(
                        moment.content,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    Icon(Icons.Default.BookmarkBorder, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedVideoPreview(url: String, thumbnail: String?, isRestricted: Boolean) {
    var generated by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, thumbnail) {
        if (!thumbnail.isNullOrBlank()) return@LaunchedEffect
        generated = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(profileThumbnailUrl(url))
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            !thumbnail.isNullOrBlank() -> {
                AsyncImage(
                    model = profileThumbnailUrl(thumbnail),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            generated != null -> {
                Image(
                    bitmap = generated!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                Box(
                    Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(22.dp))
                }
            }
        }
        if (!isRestricted) {
            ChatVideoPlayBadge(
                size = 10.dp,
                padding = 6.dp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun SavedRestrictedOverlay(isMutedRestriction: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.95f), modifier = Modifier.size(13.dp))
            Text(
                stringResource(
                    if (isMutedRestriction) R.string.saved_moments_restricted_muted_title
                    else R.string.saved_moments_restricted_title,
                ),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                stringResource(
                    if (isMutedRestriction) R.string.saved_moments_restricted_muted_subtitle
                    else R.string.saved_moments_restricted_subtitle,
                ),
                color = Color.White.copy(0.84f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun hasVideo(moment: Moment): Boolean {
    val first = moment.primaryVisibleMediaItem
    if (first != null) return first.type == MediaItem.MediaType.VIDEO
    return moment.previewVideoURLString != null
}

private fun hasImage(moment: Moment): Boolean {
    val first = moment.primaryVisibleMediaItem
    if (first != null) return first.type == MediaItem.MediaType.IMAGE
    return moment.previewImageURLString != null
}
