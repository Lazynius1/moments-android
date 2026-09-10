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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsSearchField
import com.moments.android.views.settings.SettingsToolbarBackButton
import com.moments.android.views.profile.core.sections.MomentCarouselIndicatorIcon
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.profile.core.sections.momentZoomNavigationSurface
import com.moments.android.views.profile.core.sections.profileGridNavigationChrome
import com.moments.android.views.profile.core.sections.profileMomentZoomSource
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.tabbar.MomentsTabBarHidden
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
 * Detalle se abre vía [MomentZoomDetailDestination] con [MomentZoomPresentationKind.Carousel]
 * (= `ModernMomentDetailView`, misma superficie que el feed/perfil).
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
    var isSearchExpanded by remember { mutableStateOf(false) }
    var mediaFilter by remember { mutableStateOf(SavedMediaFilter.ALL) }
    var collectionFilter by remember { mutableStateOf(SavedCollectionFilter.ALL) }
    var sortMode by remember { mutableStateOf(SavedSortMode.NEWEST) }
    var filtersMenuExpanded by remember { mutableStateOf(false) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMomentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveSelectionAlert by remember { mutableStateOf(false) }
    var restrictedMomentToRemove by remember { mutableStateOf<Moment?>(null) }
    var showingRestrictedRemoveAlert by remember { mutableStateOf(false) }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }

    val hasSecondaryFiltersActive =
        collectionFilter != SavedCollectionFilter.ALL || sortMode != SavedSortMode.NEWEST

    // ≡ iOS SavedMomentsView `.momentsFloatingTabBarHidden`
    MomentsTabBarHidden()

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
            presentation = MomentZoomPresentationKind.Carousel,
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
                        SavedMomentsToolbarFilterScroll(
                            onRefresh = {
                                suspendCancellableCoroutine { cont ->
                                    viewModel.loadSavedMoments {
                                        cont.resume(Unit)
                                    }
                                }
                            },
                            chrome = {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SavedMomentsChromeRow(
                                        mediaFilter = mediaFilter,
                                        onMediaFilterChange = { mediaFilter = it },
                                        isSearchActive = isSearchExpanded || searchText.isNotEmpty(),
                                        onSearchToggle = { isSearchExpanded = !isSearchExpanded },
                                        hasSecondaryFilters = hasSecondaryFiltersActive,
                                        filtersMenuExpanded = filtersMenuExpanded,
                                        onFiltersMenuExpandedChange = { filtersMenuExpanded = it },
                                        sortMode = sortMode,
                                        onSortModeChange = { sortMode = it },
                                        collectionFilter = collectionFilter,
                                        onCollectionFilterChange = { collectionFilter = it },
                                        onResetFilters = {
                                            sortMode = SavedSortMode.NEWEST
                                            collectionFilter = SavedCollectionFilter.ALL
                                        },
                                        textColor = textColor,
                                        isDark = isDark,
                                    )
                                    if (isSearchExpanded || searchText.isNotEmpty()) {
                                        SavedMomentsSearchBar(
                                            searchText = searchText,
                                            onSearchTextChange = { searchText = it },
                                        )
                                    }
                                    if (hasSecondaryFiltersActive) {
                                        SavedMomentsActiveFilterChips(
                                            sortMode = sortMode,
                                            collectionFilter = collectionFilter,
                                            onClearSort = { sortMode = SavedSortMode.NEWEST },
                                            onClearCollection = {
                                                collectionFilter = SavedCollectionFilter.ALL
                                            },
                                            textColor = textColor,
                                        )
                                    }
                                }
                            },
                            content = {
                                if (filteredMoments.isEmpty()) {
                                    SavedMomentsFilteredEmpty(
                                        textColor = textColor,
                                        secondaryColor = secondaryColor,
                                        onClear = {
                                            searchText = ""
                                            isSearchExpanded = false
                                            mediaFilter = SavedMediaFilter.ALL
                                            collectionFilter = SavedCollectionFilter.ALL
                                            sortMode = SavedSortMode.NEWEST
                                        },
                                    )
                                } else {
                                    SavedMomentsScrollGrid(
                                        items = identifiedFilteredMoments,
                                        isSelectionMode = isSelectionMode,
                                        selectedMomentIds = selectedMomentIds,
                                        visibilityByMomentId = viewModel.visibilityByMomentId,
                                        isMuted = { viewModel.isMomentFromMutedUser(it) },
                                        onTap = { handleTap(it, filteredMoments) },
                                        onLongPress = { moment ->
                                            if (!isSelectionMode) isSelectionMode = true
                                            toggleSelection(moment)
                                        },
                                    )
                                }
                            },
                        )
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
/** ≡ header de `SharedActivityDetailView` / `DetailTopBar`: título a la izquierda, Seleccionar en cápsula. */
private fun SavedMomentsToolbar(
    title: String,
    isSelectionMode: Boolean,
    textColor: Color,
    onNavigateBack: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsToolbarBackButton(onNavigateBack = onNavigateBack)
        Text(
            text = title,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        TextButton(
            onClick = onToggleSelection,
            // Material3 TextButton default = CircleShape → óvalo al pulsar; cápsula como el chrome de actividad.
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = stringResource(
                    if (isSelectionMode) R.string.saved_moments_cancel
                    else R.string.saved_moments_select,
                ),
                color = if (isSelectionMode) Color.Red else textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Port de Guardados con chrome tipo toolbar (`safeAreaBar`):
 * barra fija arriba; el grid scrollea por debajo (sin bloque duro inline).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMomentsToolbarFilterScroll(
    onRefresh: suspend () -> Unit,
    chrome: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val chromeHeight = with(density) { chromeHeightPx.toDp() }
    val isDark = isSystemInDarkTheme()
    val canvas = ProfileMomentZoomNavigation.canvasBackground(isDark)

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    onRefresh()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(chromeHeight))
                content()
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onGloballyPositioned { chromeHeightPx = it.size.height },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to canvas.copy(alpha = 0.9f),
                                0.65f to canvas.copy(alpha = 0.45f),
                                1f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            chrome()
        }
    }
}

@Composable
private fun SavedMomentsScrollGrid(
    items: List<IdentifiedSavedMoment>,
    isSelectionMode: Boolean,
    selectedMomentIds: Set<String>,
    visibilityByMomentId: Map<String, Boolean>,
    isMuted: (Moment) -> Boolean,
    onTap: (Moment) -> Unit,
    onLongPress: (Moment) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .profileGridNavigationChrome()
            .padding(horizontal = 10.dp)
            .padding(bottom = if (isSelectionMode) 90.dp else 20.dp),
    ) {
        val side = (maxWidth - 8.dp) / 3
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { identified ->
                        val moment = identified.moment
                        val momentId = identified.id
                        val isRestricted = !(visibilityByMomentId[momentId] ?: true)
                        val isMutedRestriction = isRestricted && isMuted(moment)
                        Box(Modifier.width(side)) {
                            ScreenshotProtectedView(
                                isProtected = !isRestricted &&
                                    (moment.audience?.lowercase() ?: "") != "everyone",
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
                                    onTap = { onTap(moment) },
                                    onLongPress = { onLongPress(moment) },
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.width(side).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMomentsChromeRow(
    mediaFilter: SavedMediaFilter,
    onMediaFilterChange: (SavedMediaFilter) -> Unit,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    hasSecondaryFilters: Boolean,
    filtersMenuExpanded: Boolean,
    onFiltersMenuExpandedChange: (Boolean) -> Unit,
    sortMode: SavedSortMode,
    onSortModeChange: (SavedSortMode) -> Unit,
    collectionFilter: SavedCollectionFilter,
    onCollectionFilterChange: (SavedCollectionFilter) -> Unit,
    onResetFilters: () -> Unit,
    textColor: Color,
    isDark: Boolean,
) {
    val stroke = Color.White.copy(alpha = if (isDark) 0.06f else 0.16f)
    Row(
        Modifier.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .border(1.dp, stroke, RoundedCornerShape(50))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                                Modifier.background(
                                    if (isDark) Color.White.copy(alpha = 0.14f) else Color.White,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onMediaFilterChange(filter) }
                        .padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
        SavedMomentsChromeIconButton(
            icon = Icons.Default.Search,
            isActive = isSearchActive,
            textColor = textColor,
            isDark = isDark,
            onClick = onSearchToggle,
        )
        Box {
            SavedMomentsChromeIconButton(
                icon = Icons.Default.FilterList,
                isActive = hasSecondaryFilters,
                textColor = textColor,
                isDark = isDark,
                onClick = { onFiltersMenuExpandedChange(true) },
                contentDescription = stringResource(R.string.saved_moments_filters_button),
            )
            DropdownMenu(
                expanded = filtersMenuExpanded,
                onDismissRequest = { onFiltersMenuExpandedChange(false) },
            ) {
                Text(
                    text = stringResource(R.string.saved_moments_filters_sort),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.55f),
                )
                SavedSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.titleRes)) },
                        onClick = {
                            onSortModeChange(mode)
                            onFiltersMenuExpandedChange(false)
                        },
                        trailingIcon = {
                            if (sortMode == mode) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.saved_moments_filters_contains),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.55f),
                )
                SavedCollectionFilter.entries
                    .filter { it != SavedCollectionFilter.ALL }
                    .forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(stringResource(filter.titleRes)) },
                            onClick = {
                                onCollectionFilterChange(
                                    if (collectionFilter == filter) SavedCollectionFilter.ALL else filter,
                                )
                                onFiltersMenuExpandedChange(false)
                            },
                            trailingIcon = {
                                if (collectionFilter == filter) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                if (hasSecondaryFilters) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.saved_moments_filters_reset),
                                color = Color(0xFFFF3B30),
                            )
                        },
                        onClick = {
                            onResetFilters()
                            onFiltersMenuExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedMomentsChromeIconButton(
    icon: ImageVector,
    isActive: Boolean,
    textColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val stroke = Color.White.copy(alpha = if (isDark) 0.06f else 0.16f)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (isActive) {
                    Modifier.momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                } else {
                    Modifier.border(1.dp, stroke, RoundedCornerShape(50))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = textColor, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun SavedMomentsSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
) {
    SettingsSearchField(
        value = searchText,
        onValueChange = onSearchTextChange,
        placeholder = stringResource(R.string.saved_moments_search_placeholder),
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

@Composable
private fun SavedMomentsActiveFilterChips(
    sortMode: SavedSortMode,
    collectionFilter: SavedCollectionFilter,
    onClearSort: () -> Unit,
    onClearCollection: () -> Unit,
    textColor: Color,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sortMode != SavedSortMode.NEWEST) {
            item {
                SavedMomentsActiveChip(
                    title = stringResource(sortMode.titleRes),
                    textColor = textColor,
                    onClear = onClearSort,
                )
            }
        }
        if (collectionFilter != SavedCollectionFilter.ALL) {
            item {
                SavedMomentsActiveChip(
                    title = stringResource(collectionFilter.titleRes),
                    textColor = textColor,
                    onClear = onClearCollection,
                )
            }
        }
    }
}

@Composable
private fun SavedMomentsActiveChip(
    title: String,
    textColor: Color,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .clickable(onClick = onClear)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        Icon(Icons.Default.Close, null, tint = textColor, modifier = Modifier.size(10.dp))
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
