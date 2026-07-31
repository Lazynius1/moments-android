package com.moments.android.views.settings

import androidx.activity.compose.BackHandler
import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.performance.toVideoMoments
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.echoes.EchoViewerUI
import com.moments.android.views.feed.video.ReelsViewer
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.story.ArchiveDayStoriesViewer
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

class ActivitySelectionController {
    var isSelectionMode by mutableStateOf(false)
    var canSelect by mutableStateOf(false)
}

/**
 * Port de `UserActivityDetailView.swift`.
 *
 * Paridad funcional vs iOS: carga/error (+ Retry)/vacío rico, grids/listas (Column chunked 3),
 * filtros orden/fecha/autor (TAGS incluido) + custom date range, `filteredEventItems`,
 * echoes summary chips, `ActivityCollapsibleFilterScroll` + PTR ≡ `performActivityRefresh`,
 * `MomentsModalSheet` author, selection bars (restore/delete/select-all), processing banner,
 * success banner con checkmark, drag-select + auto-scroll en papelera, long-press solo
 * ARCHIVED/RECENTLY_DELETED, presentaciones internas (MomentZoom Single / Reels /
 * ArchiveDayStories / StoriesView / EchoViewerUI / perfil), canvas AdaptiveColors,
 * `SettingsToolbarBackButton`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityInteractionDetailView(
    category: ActivityInteractionCategory,
    recentlyDeletedKind: RecentlyDeletedContentKind = RecentlyDeletedContentKind.MOMENTS,
    suppressInlineNavigationTitle: Boolean = false,
    selectionController: ActivitySelectionController? = null,
    onBack: () -> Unit = {},
    onOpenMoment: (Moment, List<Moment>) -> Unit = { _, _ -> },
    onOpenProfile: (String) -> Unit = {},
    onOpenReels: (Moment, List<Moment>) -> Unit = { _, _ -> },
    onOpenDeletedStory: (ActivityDeletedStoryItem, List<ActivityDeletedStoryItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(category, recentlyDeletedKind) {
        ActivityInteractionDetailViewModel(category, recentlyDeletedKind)
    }
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val ink = if (isDark) Color.White else Color.Black
    val inkMuted = ink.copy(alpha = 0.55f)
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val accent = Color(0xFF0A84FF)
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scrollState = rememberScrollState()

    var sort by remember { mutableStateOf(ReactionsSortOption.NEWEST) }
    var dateFilter by remember { mutableStateOf(ReactionsDateFilter.ALL) }
    var customDateFrom by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time)
    }
    var customDateTo by remember { mutableStateOf(Date()) }
    var selectedAuthorId by remember { mutableStateOf<String?>(null) }
    var showAuthorSheet by remember { mutableStateOf(false) }
    var localSelectionMode by remember { mutableStateOf(false) }
    val isSelectionMode = selectionController?.isSelectionMode ?: localSelectionMode
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingConfirmation by remember { mutableStateOf<ActivitySelectionConfirmationAction?>(null) }
    var isMutating by remember { mutableStateOf(false) }
    var mutatingAction by remember { mutableStateOf<ActivitySelectionConfirmationAction?>(null) }
    var actionBanner by remember { mutableStateOf<ActivityActionBanner?>(null) }
    var selectedEchoId by remember { mutableStateOf<String?>(null) }
    var longPressActivatedItemId by remember { mutableStateOf<String?>(null) }

    // Presentaciones internas ≡ fullScreenCover / navigationDestination
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var zoomMomentsPool by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var reelsPresentation by remember { mutableStateOf<ActivityReelsPresentation?>(null) }
    var deletedStoryPresentation by remember { mutableStateOf<DeletedStoriesPresentation?>(null) }
    var storyUserId by remember { mutableStateOf<String?>(null) }

    // Drag-select ≡ Swift ~1599-1810
    var gridSelectionDragMode by remember { mutableStateOf<SelectionDragMode?>(null) }
    var recentlyDeletedDragCurrentId by remember { mutableStateOf<String?>(null) }
    var recentlyDeletedAutoScrollDirection by remember { mutableStateOf<RecentlyDeletedAutoScrollDirection?>(null) }
    var recentlyDeletedAutoScrollJob by remember { mutableStateOf<Job?>(null) }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    val titleRes = if (category == ActivityInteractionCategory.ARCHIVED) {
        R.string.user_activity_archived_header_title
    } else {
        category.titleRes
    }
    val chromeTitle = stringResource(titleRes)

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }
    LaunchedEffect(viewModel.isLoading) {
        if (!viewModel.isLoading) hasLoadedOnce = true
    }

    val supportsAuthorFilter = category in setOf(
        ActivityInteractionCategory.REACTIONS,
        ActivityInteractionCategory.COMMENTS,
        ActivityInteractionCategory.TAGS,
        ActivityInteractionCategory.STICKER_REPLIES,
    )
    val supportsSelection = category in setOf(
        ActivityInteractionCategory.REACTIONS,
        ActivityInteractionCategory.TAGS,
        ActivityInteractionCategory.COMMENTS,
        ActivityInteractionCategory.STICKER_REPLIES,
        ActivityInteractionCategory.ARCHIVED,
        ActivityInteractionCategory.RECENTLY_DELETED,
    )

    fun clearSelection() {
        if (selectionController != null) {
            selectionController.isSelectionMode = false
        } else {
            localSelectionMode = false
        }
        selectedIds = emptySet()
    }

    fun showBanner(res: Int, isError: Boolean = false) {
        val banner = ActivityActionBanner(res = res, isError = isError)
        actionBanner = banner
        scope.launch {
            delay(2000)
            if (actionBanner == banner) actionBanner = null
        }
    }

    fun stopRecentlyDeletedAutoScroll(resetSelectionState: Boolean = true) {
        recentlyDeletedAutoScrollJob?.cancel()
        recentlyDeletedAutoScrollJob = null
        recentlyDeletedAutoScrollDirection = null
        if (resetSelectionState) {
            gridSelectionDragMode = null
            recentlyDeletedDragCurrentId = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopRecentlyDeletedAutoScroll() }
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedIds = emptySet()
            stopRecentlyDeletedAutoScroll()
        }
    }

    // ≡ onChange(of: selectedReactionIds) — solo al vaciar selección, no al entrar en Select
    LaunchedEffect(selectedIds) {
        if ((category == ActivityInteractionCategory.ARCHIVED ||
                category == ActivityInteractionCategory.RECENTLY_DELETED) &&
            isSelectionMode &&
            selectedIds.isEmpty()
        ) {
            if (selectionController != null) {
                selectionController.isSelectionMode = false
            } else {
                localSelectionMode = false
            }
        }
    }

    val reactionItems = remember(
        viewModel.reactionItems, sort, dateFilter, customDateFrom, customDateTo, selectedAuthorId,
    ) {
        viewModel.reactionItems
            .filterByDate(dateFilter, customDateFrom, customDateTo) { it.reactedAt }
            .let { list ->
                if (supportsAuthorFilter) {
                    list.filter { selectedAuthorId == null || it.authorId == selectedAuthorId }
                } else {
                    list
                }
            }
            .sortedByOrder(sort) { it.reactedAt }
    }
    val deletedStories = remember(
        viewModel.deletedStoryItems, sort, dateFilter, customDateFrom, customDateTo,
    ) {
        viewModel.deletedStoryItems
            .filterByDate(dateFilter, customDateFrom, customDateTo) { it.deletedAt }
            .sortedByOrder(sort) { it.deletedAt }
    }
    val commentItems = remember(
        viewModel.commentItems, sort, dateFilter, customDateFrom, customDateTo, selectedAuthorId,
    ) {
        viewModel.commentItems
            .filterByDate(dateFilter, customDateFrom, customDateTo) { it.commentedAt }
            .filter { selectedAuthorId == null || it.authorId == selectedAuthorId }
            .sortedByOrder(sort) { it.commentedAt }
    }
    val moments = remember(viewModel.moments, sort, dateFilter, customDateFrom, customDateTo) {
        viewModel.moments
            .filterByDate(dateFilter, customDateFrom, customDateTo) { it.timestamp }
            .sortedByOrder(sort) { it.timestamp }
    }
    val eventItems = remember(
        viewModel.events, sort, dateFilter, customDateFrom, customDateTo, selectedAuthorId,
    ) {
        viewModel.events
            .filterByDate(dateFilter, customDateFrom, customDateTo) { it.timestamp }
            .filter { selectedAuthorId == null || it.targetAuthorId == selectedAuthorId }
            .sortedByOrder(sort) { it.timestamp }
    }

    val authorUsernameMap = remember(viewModel.reactionItems, viewModel.commentItems, viewModel.events, category) {
        buildAuthorUsernameMap(category, viewModel)
    }
    val availableAuthorIds = remember(authorUsernameMap) {
        authorUsernameMap.keys.sortedBy { authorUsernameMap[it]?.lowercase() }
    }

    val visibleSelectableIds: Set<String> = when {
        category == ActivityInteractionCategory.RECENTLY_DELETED &&
            recentlyDeletedKind == RecentlyDeletedContentKind.STORIES -> deletedStories.map { it.id }.toSet()
        category == ActivityInteractionCategory.RECENTLY_DELETED ||
            category == ActivityInteractionCategory.REACTIONS ||
            category == ActivityInteractionCategory.TAGS ||
            category == ActivityInteractionCategory.ARCHIVED -> reactionItems.map { it.id }.toSet()
        category == ActivityInteractionCategory.COMMENTS -> commentItems.map { it.id }.toSet()
        category == ActivityInteractionCategory.STICKER_REPLIES -> eventItems.map { it.id }.toSet()
        else -> emptySet()
    }
    val allVisibleSelected = visibleSelectableIds.isNotEmpty() && selectedIds.containsAll(visibleSelectableIds)

    LaunchedEffect(visibleSelectableIds) {
        selectionController?.canSelect = visibleSelectableIds.isNotEmpty()
        if (supportsSelection) {
            selectedIds = selectedIds.filter { it in visibleSelectableIds }.toSet()
        }
        if (visibleSelectableIds.isEmpty() && selectionController?.isSelectionMode == true) {
            selectionController.isSelectionMode = false
        }
    }

    fun openAuthor(authorId: String, hasStory: Boolean) {
        if (authorId.isEmpty()) return
        if (hasStory) {
            storyUserId = authorId
        } else {
            onOpenProfile(authorId)
        }
    }

    fun openActivityMomentZoom(moment: Moment) {
        zoomMomentsPool = listOf(moment)
        MomentZoomOpener.open(
            moment = moment,
            moments = listOf(moment),
            initialIndex = 0,
            presentation = MomentZoomPresentationKind.Single,
            setDestination = { zoomDestination = it },
            zoomIDPrefix = "activity",
            chromeTitle = chromeTitle,
        )
        onOpenMoment(moment, listOf(moment))
    }

    fun openActivityReels(moment: Moment, pool: List<Moment>) {
        val videos = pool.toVideoMoments()
        if (videos.isEmpty()) return
        val startIndex = videos.indexOfFirst { it.moment.id == moment.id }.coerceAtLeast(0)
        reelsPresentation = ActivityReelsPresentation(videos = videos, startIndex = startIndex)
        onOpenReels(moment, pool)
    }

    fun openRecentlyDeletedStory(item: ActivityDeletedStoryItem) {
        val index = deletedStories.indexOfFirst { it.id == item.id }
        if (index < 0) return
        deletedStoryPresentation = DeletedStoriesPresentation(
            stories = deletedStories.map { it.story },
            initialIndex = index,
        )
        onOpenDeletedStory(item, deletedStories)
    }

    fun handleEventTap(item: ActivityEventItem) {
        when (item.kind) {
            "echo" -> item.sourceId?.let { selectedEchoId = it }
            "follower", "visit", "sticker_reply", "poll", "question" ->
                item.actorId?.let { openAuthor(it, hasStory = false) }
            else -> Unit
        }
    }

    fun applyRecentlyDeletedDragSelection(id: String) {
        when (gridSelectionDragMode) {
            SelectionDragMode.SELECTING -> selectedIds = selectedIds + id
            SelectionDragMode.DESELECTING -> selectedIds = selectedIds - id
            null -> Unit
        }
    }

    fun advanceRecentlyDeletedAutoScroll(
        direction: RecentlyDeletedAutoScrollDirection,
        items: List<String>,
        sidePx: Float,
        spacingPx: Float,
        usesPortrait: Boolean,
    ) {
        if (category != ActivityInteractionCategory.RECENTLY_DELETED || !isSelectionMode) {
            stopRecentlyDeletedAutoScroll()
            return
        }
        if (items.isEmpty()) return
        val currentId = recentlyDeletedDragCurrentId ?: return
        val currentIndex = items.indexOf(currentId).takeIf { it >= 0 } ?: return
        val proposed = if (direction == RecentlyDeletedAutoScrollDirection.DOWN) {
            currentIndex + 1
        } else {
            currentIndex - 1
        }
        val targetIndex = proposed.coerceIn(0, items.lastIndex)
        if (targetIndex == currentIndex) return
        recentlyDeletedGridIndicesBetween(currentIndex, targetIndex, items.size).forEach { index ->
            applyRecentlyDeletedDragSelection(items[index])
        }
        recentlyDeletedDragCurrentId = items[targetIndex]
        val cellH = if (usesPortrait) sidePx * 16f / 9f else sidePx
        val delta = cellH + spacingPx
        scope.launch {
            scrollState.scrollBy(if (direction == RecentlyDeletedAutoScrollDirection.DOWN) delta else -delta)
        }
    }

    fun startRecentlyDeletedAutoScroll(
        direction: RecentlyDeletedAutoScrollDirection,
        items: List<String>,
        sidePx: Float,
        spacingPx: Float,
        usesPortrait: Boolean,
    ) {
        stopRecentlyDeletedAutoScroll(resetSelectionState = false)
        recentlyDeletedAutoScrollDirection = direction
        recentlyDeletedAutoScrollJob = scope.launch {
            while (isActive) {
                delay(90)
                advanceRecentlyDeletedAutoScroll(direction, items, sidePx, spacingPx, usesPortrait)
            }
        }
    }

    fun updateRecentlyDeletedAutoScroll(
        locationY: Float,
        viewportHeight: Float,
        items: List<String>,
        sidePx: Float,
        spacingPx: Float,
        usesPortrait: Boolean,
    ) {
        val edgeThreshold = with(density) { 96.dp.toPx() }
        val direction = when {
            locationY <= edgeThreshold -> RecentlyDeletedAutoScrollDirection.UP
            locationY >= (viewportHeight - edgeThreshold) -> RecentlyDeletedAutoScrollDirection.DOWN
            else -> null
        }
        if (direction == recentlyDeletedAutoScrollDirection) return
        if (direction != null) {
            startRecentlyDeletedAutoScroll(direction, items, sidePx, spacingPx, usesPortrait)
        } else {
            stopRecentlyDeletedAutoScroll(resetSelectionState = false)
        }
    }

    fun handleRecentlyDeletedDrag(
        location: Offset,
        items: List<String>,
        sidePx: Float,
        spacingPx: Float,
        viewportHeightPx: Float,
        usesPortrait: Boolean,
        horizontalInsetPx: Float = 0f,
    ) {
        if (category != ActivityInteractionCategory.RECENTLY_DELETED || !isSelectionMode) return
        updateRecentlyDeletedAutoScroll(
            location.y, viewportHeightPx, items, sidePx, spacingPx, usesPortrait,
        )
        val id = recentlyDeletedItemId(
            location = location,
            items = items,
            side = sidePx,
            spacing = spacingPx,
            horizontalInset = horizontalInsetPx,
            // padding(top=8) está fuera de pointerInput → y=0 es el inicio del grid
            topPad = 0f,
            usesPortraitStoryCells = usesPortrait,
        ) ?: return
        val currentIndex = items.indexOf(id).takeIf { it >= 0 } ?: return
        if (gridSelectionDragMode == null) {
            gridSelectionDragMode =
                if (id in selectedIds) SelectionDragMode.DESELECTING else SelectionDragMode.SELECTING
            applyRecentlyDeletedDragSelection(id)
            recentlyDeletedDragCurrentId = id
            return
        }
        val lastId = recentlyDeletedDragCurrentId
        val lastIndex = lastId?.let { items.indexOf(it) }?.takeIf { it >= 0 }
        if (lastIndex != null && lastIndex != currentIndex) {
            recentlyDeletedGridIndicesBetween(lastIndex, currentIndex, items.size).forEach { index ->
                applyRecentlyDeletedDragSelection(items[index])
            }
        } else {
            applyRecentlyDeletedDragSelection(id)
        }
        recentlyDeletedDragCurrentId = id
    }

    suspend fun performActivityRefresh() {
        viewModel.reload()
        while (viewModel.isLoading) delay(100)
    }

    fun enterSelectionWith(id: String) {
        longPressActivatedItemId = id
        if (!isSelectionMode) {
            if (selectionController != null) {
                selectionController.isSelectionMode = true
            } else {
                localSelectionMode = true
            }
        }
        selectedIds = selectedIds + id
    }

    val systemInsetModifier =
        if (suppressInlineNavigationTitle) Modifier else Modifier.safeDrawingPadding()
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .then(systemInsetModifier),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!suppressInlineNavigationTitle) {
                DetailTopBar(
                    titleRes = titleRes,
                    suppressTitle = false,
                    selectionActionLabel = when {
                        !supportsSelection -> null
                        isSelectionMode -> R.string.user_activity_cancel
                        category == ActivityInteractionCategory.ARCHIVED -> null
                        else -> R.string.user_activity_select
                    },
                    ink = ink,
                    onBack = onBack,
                    onSelectionAction = {
                        if (isSelectionMode) {
                            clearSelection()
                        } else if (selectionController != null) {
                            selectionController.isSelectionMode = true
                        } else {
                            localSelectionMode = true
                        }
                    },
                )
            }

            when {
                viewModel.isLoading && !hasLoadedOnce -> LoadingState(ink = ink, inkMuted = inkMuted)
                viewModel.errorMessage != null && !viewModel.isLoading -> ErrorState(
                    message = viewModel.errorMessage!!,
                    ink = ink,
                    inkMuted = inkMuted,
                    onRetry = { viewModel.reload() },
                )
                else -> ActivityCollapsibleFilterScroll(
                    onRefresh = { performActivityRefresh() },
                    scrollState = scrollState,
                    header = {
                        Column {
                            FiltersHeader(
                                sort = sort, onSort = { sort = it },
                                dateFilter = dateFilter, onDateFilter = { dateFilter = it },
                                showAuthor = supportsAuthorFilter && availableAuthorIds.isNotEmpty(),
                                selectedAuthorId = selectedAuthorId,
                                authorUsernameMap = authorUsernameMap,
                                onOpenAuthorSheet = { showAuthorSheet = true },
                                ink = ink,
                                inkMuted = inkMuted,
                            )
                            if (dateFilter == ReactionsDateFilter.CUSTOM) {
                                CustomDateRangeControls(
                                    from = customDateFrom,
                                    to = customDateTo,
                                    onFrom = { customDateFrom = it },
                                    onTo = { customDateTo = it },
                                    isDark = isDark,
                                    ink = ink,
                                )
                            }
                            if (category == ActivityInteractionCategory.ECHOES) {
                                EchoesSummaryHeader(
                                    total = viewModel.events.size,
                                    active = viewModel.events.count {
                                        it.echoStatusRaw?.equals("active", ignoreCase = true) == true
                                    },
                                    inkMuted = inkMuted,
                                    chipBg = ink.copy(alpha = 0.06f),
                                )
                            }
                        }
                    },
                    content = {
                        val emptyRes = category.emptyRes
                        val emptySubtitleRes = emptySubtitleRes(category)
                        val empty: @Composable () -> Unit = {
                            EmptyState(
                                category = category,
                                emptyRes = emptyRes,
                                subtitleRes = emptySubtitleRes,
                                ink = ink,
                                inkMuted = inkMuted,
                            )
                        }
                        when (category) {
                            ActivityInteractionCategory.RECENTLY_DELETED ->
                                if (recentlyDeletedKind == RecentlyDeletedContentKind.STORIES) {
                                    if (deletedStories.isEmpty()) empty()
                                    else DeletedStoriesGrid(
                                        items = deletedStories,
                                        isSelectionMode = isSelectionMode,
                                        selectedIds = selectedIds,
                                        onToggle = { toggle(it, selectedIds) { s -> selectedIds = s } },
                                        onOpen = ::openRecentlyDeletedStory,
                                        onLongPress = ::enterSelectionWith,
                                        dragEnabled = isSelectionMode,
                                        onDragLocation = { loc, sidePx, spacingPx, viewportH ->
                                            handleRecentlyDeletedDrag(
                                                location = loc,
                                                items = deletedStories.map { it.id },
                                                sidePx = sidePx,
                                                spacingPx = spacingPx,
                                                viewportHeightPx = viewportH,
                                                usesPortrait = true,
                                            )
                                        },
                                        onDragEnd = { stopRecentlyDeletedAutoScroll() },
                                        viewportHeightFraction = configuration.screenHeightDp * 0.62f,
                                    )
                                } else {
                                    if (reactionItems.isEmpty()) empty()
                                    else ReactionsGrid(
                                        items = reactionItems,
                                        category = category,
                                        isSelectionMode = isSelectionMode,
                                        selectedIds = selectedIds,
                                        onToggle = { toggle(it, selectedIds) { s -> selectedIds = s } },
                                        onOpen = ::openActivityMomentZoom,
                                        onLongPress = ::enterSelectionWith,
                                        allowLongPress = true,
                                        longPressActivatedItemId = longPressActivatedItemId,
                                        onClearLongPress = { longPressActivatedItemId = null },
                                        dragEnabled = isSelectionMode,
                                        onDragLocation = { loc, sidePx, spacingPx, viewportH ->
                                            handleRecentlyDeletedDrag(
                                                location = loc,
                                                items = reactionItems.map { it.id },
                                                sidePx = sidePx,
                                                spacingPx = spacingPx,
                                                viewportHeightPx = viewportH,
                                                usesPortrait = false,
                                            )
                                        },
                                        onDragEnd = { stopRecentlyDeletedAutoScroll() },
                                        viewportHeightFraction = configuration.screenHeightDp * 0.62f,
                                    )
                                }
                            ActivityInteractionCategory.REACTIONS,
                            ActivityInteractionCategory.TAGS,
                            ActivityInteractionCategory.ARCHIVED,
                            -> if (reactionItems.isEmpty()) empty()
                            else ReactionsGrid(
                                items = reactionItems,
                                category = category,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                onToggle = { toggle(it, selectedIds) { s -> selectedIds = s } },
                                onOpen = ::openActivityMomentZoom,
                                onLongPress = ::enterSelectionWith,
                                allowLongPress = category == ActivityInteractionCategory.ARCHIVED,
                                longPressActivatedItemId = longPressActivatedItemId,
                                onClearLongPress = { longPressActivatedItemId = null },
                                dragEnabled = false,
                                onDragLocation = { _, _, _, _ -> },
                                onDragEnd = {},
                                viewportHeightFraction = configuration.screenHeightDp * 0.62f,
                            )
                            ActivityInteractionCategory.COMMENTS -> if (commentItems.isEmpty()) empty()
                            else CommentsList(
                                items = commentItems,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                onToggle = { toggle(it, selectedIds) { s -> selectedIds = s } },
                                onOpenMoment = ::openActivityMomentZoom,
                                onOpenAuthor = ::openAuthor,
                            )
                            ActivityInteractionCategory.MOMENTS, ActivityInteractionCategory.REELS ->
                                if (moments.isEmpty()) empty()
                                else MomentsGrid(
                                    moments = moments,
                                    isReels = category == ActivityInteractionCategory.REELS,
                                    onOpen = { m ->
                                        if (category == ActivityInteractionCategory.REELS) {
                                            openActivityReels(m, moments)
                                        } else {
                                            openActivityMomentZoom(m)
                                        }
                                    },
                                )
                            else -> if (eventItems.isEmpty()) empty()
                            else EventsList(
                                items = eventItems,
                                isSelectionMode = isSelectionMode,
                                selectedIds = selectedIds,
                                onToggle = { toggle(it, selectedIds) { s -> selectedIds = s } },
                                onOpenProfile = { openAuthor(it, hasStory = false) },
                                onEventTap = ::handleEventTap,
                            )
                        }
                    },
                )
            }
        }

        if (isSelectionMode && supportsSelection) {
            SelectionBar(
                category = category,
                count = selectedIds.size,
                countLabelRes = selectionCountRes(category),
                isBusy = isMutating,
                allVisibleSelected = allVisibleSelected,
                showSelectAll = category == ActivityInteractionCategory.RECENTLY_DELETED,
                ink = ink,
                accent = accent,
                modifier = Modifier.align(Alignment.BottomCenter),
                onSelectAll = {
                    selectedIds = if (allVisibleSelected) emptySet() else visibleSelectableIds
                },
                onRestore = {
                    pendingConfirmation = when (category) {
                        ActivityInteractionCategory.ARCHIVED ->
                            ActivitySelectionConfirmationAction.ArchivedRestore(selectedIds)
                        ActivityInteractionCategory.RECENTLY_DELETED ->
                            ActivitySelectionConfirmationAction.RecentlyDeletedRestore
                        else -> null
                    }
                },
                onDelete = {
                    pendingConfirmation = when (category) {
                        ActivityInteractionCategory.TAGS -> ActivitySelectionConfirmationAction.TagsRemove
                        ActivityInteractionCategory.COMMENTS -> ActivitySelectionConfirmationAction.CommentsDelete
                        ActivityInteractionCategory.STICKER_REPLIES -> ActivitySelectionConfirmationAction.StickerRepliesDelete
                        ActivityInteractionCategory.RECENTLY_DELETED -> ActivitySelectionConfirmationAction.RecentlyDeletedDelete
                        else -> ActivitySelectionConfirmationAction.ReactionsDelete
                    }
                },
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp)
                .zIndex(20f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = actionBanner != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
            ) {
                actionBanner?.let {
                    ActionBanner(
                        res = it.res,
                        isError = it.isError,
                        isDark = isDark,
                        ink = ink,
                    )
                }
            }
            if (isMutating) {
                ProcessingBanner(
                    titleRes = processingTitleRes(mutatingAction),
                    ink = ink,
                    isDark = isDark,
                )
            }
        }

        selectedEchoId?.let { echoId ->
            EchoViewerUI(
                echoId = echoId,
                onDismiss = { selectedEchoId = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        zoomDestination?.let { destination ->
            MomentZoomDetailDestination(
                destination = destination,
                moments = MomentZoomOpener.resolvedMoments(destination, zoomMomentsPool),
                onDismiss = {
                    zoomDestination = null
                    zoomMomentsPool = emptyList()
                },
            )
        }

        reelsPresentation?.let { presentation ->
            ReelsViewer(
                videos = presentation.videos,
                onClose = { reelsPresentation = null },
                startIndex = presentation.startIndex,
                modifier = Modifier.fillMaxSize(),
            )
        }

        deletedStoryPresentation?.let { presentation ->
            ArchiveDayStoriesViewer(
                stories = presentation.stories,
                initialIndex = presentation.initialIndex,
                onDismiss = { deletedStoryPresentation = null },
            )
        }

        storyUserId?.let { uid ->
            StoriesView(
                startWithUserId = uid,
                onDismiss = { storyUserId = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showAuthorSheet) {
        MomentsModalSheet(
            onDismissRequest = { showAuthorSheet = false },
            largeOnly = false,
        ) { dismiss ->
            AuthorFilterSheet(
                selectedAuthorId = selectedAuthorId,
                availableAuthorIds = availableAuthorIds,
                authorUsernameMap = authorUsernameMap,
                onSelect = {
                    selectedAuthorId = it
                    dismiss()
                },
                onClose = dismiss,
            )
        }
    }

    pendingConfirmation?.let { action ->
        ConfirmationDialog(
            action = action,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                pendingConfirmation = null
                scope.launch {
                    isMutating = true
                    mutatingAction = action
                    val ids = selectedIds
                    val result = when (action) {
                        is ActivitySelectionConfirmationAction.ReactionsDelete -> viewModel.removeReactions(ids)
                        is ActivitySelectionConfirmationAction.TagsRemove -> viewModel.removeTags(ids)
                        is ActivitySelectionConfirmationAction.CommentsDelete -> viewModel.removeComments(ids)
                        is ActivitySelectionConfirmationAction.StickerRepliesDelete -> viewModel.removeStickerReplies(ids)
                        is ActivitySelectionConfirmationAction.ArchivedRestore -> viewModel.unarchiveSelection(action.ids)
                        is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> viewModel.restoreSelection(ids)
                        is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> viewModel.permanentlyDeleteSelection(ids)
                    }
                    isMutating = false
                    mutatingAction = null
                    if (result.isSuccess) {
                        clearSelection()
                        showBanner(successResFor(action))
                    } else {
                        Log.e(
                            "UserActivity",
                            "Selection action ${action.id} failed",
                            result.exceptionOrNull(),
                        )
                        showBanner(R.string.story_context_menu_action_failed, isError = true)
                    }
                }
            },
        )
    }
}

private data class ActivityReelsPresentation(
    val videos: List<VideoMoment>,
    val startIndex: Int,
)

private data class DeletedStoriesPresentation(
    val stories: List<Story>,
    val initialIndex: Int,
)

private fun emptySubtitleRes(category: ActivityInteractionCategory): Int? = when (category) {
    ActivityInteractionCategory.REACTIONS -> R.string.user_activity_empty_reactions_subtitle
    ActivityInteractionCategory.COMMENTS -> R.string.user_activity_empty_comments_subtitle
    ActivityInteractionCategory.TAGS -> R.string.user_activity_empty_tags_subtitle
    ActivityInteractionCategory.STICKER_REPLIES -> R.string.user_activity_empty_stickers_subtitle
    ActivityInteractionCategory.ARCHIVED -> R.string.user_activity_empty_archived_subtitle
    ActivityInteractionCategory.RECENTLY_DELETED -> R.string.user_activity_empty_recently_deleted_subtitle
    ActivityInteractionCategory.ECHOES -> R.string.user_activity_empty_echoes_subtitle
    ActivityInteractionCategory.FOLLOWERS -> R.string.user_activity_empty_followers_subtitle
    ActivityInteractionCategory.VISITS -> R.string.user_activity_empty_visits_subtitle
    ActivityInteractionCategory.MOMENTS -> R.string.user_activity_empty_moments_subtitle
    ActivityInteractionCategory.REELS -> R.string.user_activity_empty_reels_subtitle
    else -> null
}

private fun selectionCountRes(category: ActivityInteractionCategory): Int = when (category) {
    ActivityInteractionCategory.TAGS -> R.string.user_activity_tags_selected_count
    ActivityInteractionCategory.COMMENTS -> R.string.user_activity_comments_selected_count
    ActivityInteractionCategory.STICKER_REPLIES -> R.string.user_activity_stickers_selected_count
    else -> R.string.user_activity_reactions_selected_count
}

private fun processingTitleRes(action: ActivitySelectionConfirmationAction?): Int = when (action) {
    is ActivitySelectionConfirmationAction.ArchivedRestore -> R.string.user_activity_archived_processing_restore
    is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> R.string.user_activity_recently_deleted_processing_restore
    is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> R.string.user_activity_recently_deleted_processing_delete
    else -> R.string.user_activity_recently_deleted_processing_subtitle
}

private fun successResFor(action: ActivitySelectionConfirmationAction): Int = when (action) {
    is ActivitySelectionConfirmationAction.ReactionsDelete -> R.string.user_activity_reactions_success_delete
    is ActivitySelectionConfirmationAction.TagsRemove -> R.string.user_activity_tags_success_remove
    is ActivitySelectionConfirmationAction.CommentsDelete -> R.string.user_activity_comments_success_delete
    is ActivitySelectionConfirmationAction.StickerRepliesDelete -> R.string.user_activity_stickers_success_delete
    is ActivitySelectionConfirmationAction.ArchivedRestore -> R.string.user_activity_archived_success_restore
    is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> R.string.user_activity_recently_deleted_success_restore
    is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> R.string.user_activity_recently_deleted_success_delete
}

private fun toggle(id: String, current: Set<String>, set: (Set<String>) -> Unit) {
    set(if (id in current) current - id else current + id)
}

private inline fun <T> List<T>.filterByDate(
    filter: ReactionsDateFilter,
    customFrom: Date,
    customTo: Date,
    crossinline dateOf: (T) -> Date,
): List<T> {
    if (filter == ReactionsDateFilter.ALL) return this
    val calendar = Calendar.getInstance()
    return when (filter) {
        ReactionsDateFilter.WEEK -> {
            val from = calendar.apply { add(Calendar.DAY_OF_YEAR, -7) }.time
            filter { dateOf(it) >= from }
        }
        ReactionsDateFilter.MONTH -> {
            val from = calendar.apply { add(Calendar.MONTH, -1) }.time
            filter { dateOf(it) >= from }
        }
        ReactionsDateFilter.YEAR -> {
            val from = calendar.apply { add(Calendar.YEAR, -1) }.time
            filter { dateOf(it) >= from }
        }
        ReactionsDateFilter.CUSTOM -> {
            val startCal = Calendar.getInstance().apply {
                time = minOf(customFrom, customTo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                time = maxOf(customFrom, customTo)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val start = startCal.time
            val end = endCal.time
            filter { val d = dateOf(it); d >= start && d <= end }
        }
        ReactionsDateFilter.ALL -> this
    }
}

private inline fun <T> List<T>.sortedByOrder(sort: ReactionsSortOption, crossinline dateOf: (T) -> Date): List<T> =
    if (sort == ReactionsSortOption.NEWEST) sortedByDescending { dateOf(it) } else sortedBy { dateOf(it) }

private fun recentlyDeletedGridIndicesBetween(
    from: Int,
    to: Int,
    itemCount: Int,
    columns: Int = 3,
): List<Int> {
    val startRow = from / columns
    val startCol = from % columns
    val endRow = to / columns
    val endCol = to % columns
    val minRow = min(startRow, endRow)
    val maxRow = max(startRow, endRow)
    val minCol = min(startCol, endCol)
    val maxCol = max(startCol, endCol)
    val indices = mutableListOf<Int>()
    for (row in minRow..maxRow) {
        for (col in minCol..maxCol) {
            val index = row * columns + col
            if (index < itemCount) indices.add(index)
        }
    }
    return indices
}

private fun recentlyDeletedItemId(
    location: Offset,
    items: List<String>,
    side: Float,
    spacing: Float,
    horizontalInset: Float,
    topPad: Float,
    usesPortraitStoryCells: Boolean = false,
): String? {
    val x = location.x - horizontalInset
    val y = location.y - topPad
    if (x < 0 || y < 0) return null
    val columnWidth = side + spacing
    val cellHeight = if (usesPortraitStoryCells) side * 16f / 9f else side
    val rowHeight = cellHeight + spacing
    if (columnWidth <= 0f || rowHeight <= 0f) return null
    val column = (x / columnWidth).toInt()
    val row = (y / rowHeight).toInt()
    if (column !in 0..2) return null
    val columnRemainder = x % columnWidth
    val rowRemainder = y % rowHeight
    if (columnRemainder > side || rowRemainder > cellHeight) return null
    val index = row * 3 + column
    return items.getOrNull(index)
}

private fun buildAuthorUsernameMap(
    category: ActivityInteractionCategory,
    viewModel: ActivityInteractionDetailViewModel,
): Map<String, String> {
    val map = linkedMapOf<String, String>()
    when (category) {
        ActivityInteractionCategory.REACTIONS, ActivityInteractionCategory.TAGS ->
            viewModel.reactionItems.forEach { item ->
                val name = item.moment?.username?.takeIf { it.isNotBlank() } ?: return@forEach
                map.putIfAbsent(item.authorId, name)
            }
        ActivityInteractionCategory.COMMENTS ->
            viewModel.commentItems.forEach { item ->
                val name = item.moment?.username?.takeIf { it.isNotBlank() } ?: return@forEach
                map.putIfAbsent(item.authorId, name)
            }
        ActivityInteractionCategory.STICKER_REPLIES ->
            viewModel.events.forEach { item ->
                val authorId = item.targetAuthorId?.takeIf { it.isNotEmpty() } ?: return@forEach
                val name = item.targetUsername?.takeIf { it.isNotBlank() } ?: return@forEach
                map.putIfAbsent(authorId, name)
            }
        else -> {}
    }
    return map
}

// MARK: - Subcomponentes

@Composable
private fun DetailTopBar(
    titleRes: Int,
    suppressTitle: Boolean,
    selectionActionLabel: Int?,
    ink: Color,
    onBack: () -> Unit,
    onSelectionAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!suppressTitle) {
            SettingsToolbarBackButton(onNavigateBack = onBack)
            Text(
                stringResource(titleRes),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectionActionLabel != null) {
            TextButton(onClick = onSelectionAction) {
                Text(
                    stringResource(selectionActionLabel),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(ink: Color, inkMuted: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ink)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.user_activity_loading), fontSize = 13.sp, color = inkMuted)
        }
    }
}

@Composable
private fun ErrorState(message: String, ink: Color, inkMuted: Color, onRetry: () -> Unit) {
    val offline = listOf("offline", "internet", "network", "connection").any { message.contains(it, ignoreCase = true) }
    Box(Modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (offline) "📡" else "⚠️", fontSize = 48.sp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(if (offline) R.string.user_activity_error_offline_title else R.string.user_activity_error_generic_title),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (offline) R.string.user_activity_error_offline_subtitle else R.string.user_activity_error_generic_subtitle),
                    fontSize = 13.sp, color = inkMuted, textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.user_activity_retry),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(
    category: ActivityInteractionCategory,
    emptyRes: Int?,
    subtitleRes: Int?,
    ink: Color,
    inkMuted: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        when (category) {
            ActivityInteractionCategory.ECHOES -> EchoesIconView(
                size = EchoesIconMetrics.emptyState,
                gradient = EchoesIconGradients.brandDiagonal,
            )
            ActivityInteractionCategory.RECENTLY_DELETED -> Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = inkMuted.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp),
            )
            else -> {
                val accent = category.accentColor
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(86.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.12f), accent.copy(alpha = 0.04f)),
                                ),
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                                ),
                                shape = CircleShape,
                            ),
                    )
                    when (category) {
                        ActivityInteractionCategory.REACTIONS ->
                            AnimatedReactionIcon(modifier = Modifier.size(36.dp))
                        ActivityInteractionCategory.COMMENTS ->
                            AnimatedCommentIcon(modifier = Modifier.size(36.dp))
                        ActivityInteractionCategory.TAGS ->
                            AttachmentIconView(
                                icon = AttachmentIcon.TAGGED,
                                preset = AttachmentIconPreset.ACTIVITY_EMPTY_STATE,
                                tintColor = accent,
                            )
                        else -> Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = emptyRes?.let { stringResource(it) }.orEmpty(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (subtitleRes != null) {
                Text(
                    text = stringResource(subtitleRes),
                    fontSize = 13.sp,
                    color = inkMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CustomDateRangeControls(
    from: Date,
    to: Date,
    onFrom: (Date) -> Unit,
    onTo: (Date) -> Unit,
    isDark: Boolean,
    ink: Color,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    val chipBg = (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateChip(
            label = dateFormat.format(from),
            chipBg = chipBg,
            ink = ink,
            onClick = { pickActivityDate(context, from, onFrom) },
        )
        DateChip(
            label = dateFormat.format(to),
            chipBg = chipBg,
            ink = ink,
            onClick = { pickActivityDate(context, to, onTo) },
        )
    }
}

@Composable
private fun DateChip(label: String, chipBg: Color, ink: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(chipBg, CircleShape)
            .border(1.dp, Color.Gray.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = ink,
    )
}

private fun pickActivityDate(
    context: android.content.Context,
    initial: Date,
    onChosen: (Date) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { time = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onChosen(
                Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time,
            )
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

@Composable
private fun EchoesSummaryHeader(total: Int, active: Int, inkMuted: Color, chipBg: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EchoesInfoChip(text = stringResource(R.string.user_activity_echoes_count, total), inkMuted = inkMuted, chipBg = chipBg)
        EchoesInfoChip(text = stringResource(R.string.user_activity_echoes_active_count, active), inkMuted = inkMuted, chipBg = chipBg)
    }
}

@Composable
private fun EchoesInfoChip(text: String, inkMuted: Color, chipBg: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = inkMuted,
        modifier = Modifier
            .clip(CircleShape)
            .background(chipBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun FiltersHeader(
    sort: ReactionsSortOption,
    onSort: (ReactionsSortOption) -> Unit,
    dateFilter: ReactionsDateFilter,
    onDateFilter: (ReactionsDateFilter) -> Unit,
    showAuthor: Boolean,
    selectedAuthorId: String?,
    authorUsernameMap: Map<String, String>,
    onOpenAuthorSheet: () -> Unit,
    ink: Color,
    inkMuted: Color,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipMenu(
            label = stringResource(R.string.user_activity_filters_sort),
            value = stringResource(sort.titleRes),
            options = ReactionsSortOption.entries.map { it to stringResource(it.titleRes) },
            selected = sort,
            onSelect = onSort,
            ink = ink,
            inkMuted = inkMuted,
        )
        FilterChipMenu(
            label = stringResource(R.string.user_activity_filters_date),
            value = stringResource(dateFilter.titleRes),
            options = ReactionsDateFilter.entries.map { it to stringResource(it.titleRes) },
            selected = dateFilter,
            onSelect = onDateFilter,
            ink = ink,
            inkMuted = inkMuted,
        )
        if (showAuthor) {
            val authorLabel = selectedAuthorId?.let { authorUsernameMap[it] }
                ?: stringResource(R.string.user_activity_filters_author)
            FilterChip(label = authorLabel, onClick = onOpenAuthorSheet, ink = ink, inkMuted = inkMuted)
        }
    }
}

@Composable
private fun <T> FilterChipMenu(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    ink: Color,
    inkMuted: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(label = "$label: $value", onClick = { expanded = true }, ink = ink, inkMuted = inkMuted)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, onClick: () -> Unit, ink: Color, inkMuted: Color) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = ink, maxLines = 1)
        Icon(Icons.Filled.ArrowDropDown, null, tint = inkMuted, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReactionsGrid(
    items: List<ActivityReactionItem>,
    category: ActivityInteractionCategory,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (Moment) -> Unit,
    onLongPress: (String) -> Unit,
    allowLongPress: Boolean,
    longPressActivatedItemId: String?,
    onClearLongPress: () -> Unit,
    dragEnabled: Boolean,
    onDragLocation: (Offset, Float, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    viewportHeightFraction: Float,
) {
    val overlay = when (category) {
        ActivityInteractionCategory.REACTIONS, ActivityInteractionCategory.TAGS -> ActivityOverlayBadgeStyle.REACTION_DISCREET
        ActivityInteractionCategory.ARCHIVED -> ActivityOverlayBadgeStyle.AUDIENCE
        else -> ActivityOverlayBadgeStyle.NONE
    }
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { viewportHeightFraction.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - 2.dp) / 3
        val sidePx = with(density) { side.toPx() }
        val spacingPx = with(density) { 1.dp.toPx() }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = if (isSelectionMode) 88.dp else 12.dp)
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(items.map { it.id }, sidePx, spacingPx, viewportHeightPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    onDragLocation(offset, sidePx, spacingPx, viewportHeightPx)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onDragLocation(change.position, sidePx, spacingPx, viewportHeightPx)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    row.forEach { item ->
                        Box(
                            Modifier
                                .width(side)
                                .combinedClickable(
                                    onClick = {
                                        if (longPressActivatedItemId == item.id) {
                                            onClearLongPress()
                                            return@combinedClickable
                                        }
                                        if (isSelectionMode) onToggle(item.id)
                                        else item.moment?.takeIf { item.canView }?.let(onOpen)
                                    },
                                    onLongClick = {
                                        if (allowLongPress) onLongPress(item.id)
                                    },
                                ),
                        ) {
                            ActivityReactionMomentCard(
                                item = item,
                                size = side,
                                isSelectionMode = isSelectionMode,
                                isSelected = item.id in selectedIds,
                                overlayBadge = overlay,
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.width(side).height(side))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeletedStoriesGrid(
    items: List<ActivityDeletedStoryItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (ActivityDeletedStoryItem) -> Unit,
    onLongPress: (String) -> Unit,
    dragEnabled: Boolean,
    onDragLocation: (Offset, Float, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    viewportHeightFraction: Float,
) {
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { viewportHeightFraction.dp.toPx() }
    var longPressId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - 2.dp) / 3
        val sidePx = with(density) { side.toPx() }
        val spacingPx = with(density) { 1.dp.toPx() }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = if (isSelectionMode) 88.dp else 12.dp)
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(items.map { it.id }, sidePx, spacingPx, viewportHeightPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    onDragLocation(offset, sidePx, spacingPx, viewportHeightPx)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    onDragLocation(change.position, sidePx, spacingPx, viewportHeightPx)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    row.forEach { item ->
                        Box(
                            Modifier
                                .width(side)
                                .combinedClickable(
                                    onClick = {
                                        if (longPressId == item.id) {
                                            longPressId = null
                                            return@combinedClickable
                                        }
                                        if (isSelectionMode) onToggle(item.id) else onOpen(item)
                                    },
                                    onLongClick = {
                                        longPressId = item.id
                                        onLongPress(item.id)
                                    },
                                ),
                        ) {
                            ActivityDeletedStoryCard(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = item.id in selectedIds,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.width(side).aspectRatio(9f / 16f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentsGrid(moments: List<Moment>, isReels: Boolean, onOpen: (Moment) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - 2.dp) / 3
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            moments.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    row.forEach { moment ->
                        Box(
                            Modifier
                                .width(side)
                                .clickable { onOpen(moment) },
                        ) {
                            if (isReels) {
                                ActivityPortraitMomentCard(moment, Modifier.fillMaxWidth())
                            } else {
                                ActivityReactionMomentCard(
                                    item = ActivityReactionItem(
                                        moment.id.orEmpty(),
                                        moment.authorId,
                                        moment.id.orEmpty(),
                                        "moment",
                                        moment.timestamp,
                                        moment,
                                        true,
                                    ),
                                    size = side,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    overlayBadge = ActivityOverlayBadgeStyle.AUDIENCE,
                                )
                            }
                        }
                    }
                    repeat(3 - row.size) {
                        if (isReels) Spacer(Modifier.width(side).aspectRatio(9f / 16f))
                        else Spacer(Modifier.width(side).height(side))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsList(
    items: List<ActivityCommentItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpenMoment: (Moment) -> Unit,
    onOpenAuthor: (String, Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 10.dp, bottom = if (isSelectionMode) 88.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            ActivityCommentItemRow(
                item = item,
                isSelectionMode = isSelectionMode,
                isSelected = item.id in selectedIds,
                onOpenMoment = { item.moment?.takeIf { item.canView }?.let(onOpenMoment) },
                onOpenAuthorAvatar = { hasStory -> onOpenAuthor(item.authorId, hasStory) },
                onOpenAuthorProfile = { onOpenAuthor(item.authorId, false) },
                onToggleSelection = { onToggle(item.id) },
            )
        }
    }
}

@Composable
private fun EventsList(
    items: List<ActivityEventItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onEventTap: (ActivityEventItem) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 10.dp, bottom = if (isSelectionMode) 88.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            ActivityEventRow(
                item = item,
                isSelectionMode = isSelectionMode,
                isSelected = item.id in selectedIds,
                onOpenTargetProfile = { (item.targetAuthorId ?: item.actorId)?.let(onOpenProfile) },
                onRowTap = {
                    if (isSelectionMode) onToggle(item.id) else onEventTap(item)
                },
            )
        }
    }
}

@Composable
private fun SelectionBar(
    category: ActivityInteractionCategory,
    count: Int,
    countLabelRes: Int,
    isBusy: Boolean,
    allVisibleSelected: Boolean,
    showSelectAll: Boolean,
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val isArchived = category == ActivityInteractionCategory.ARCHIVED
    val isRecentlyDeleted = category == ActivityInteractionCategory.RECENTLY_DELETED
    if (isRecentlyDeleted) {
        Row(
            modifier
                .fillMaxWidth()
                .background(
                    if (isSystemInDarkTheme()) {
                        Color(0xFF151D21)
                    } else {
                        Color.White
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(countLabelRes, count),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSelectAll, enabled = !isBusy) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = stringResource(
                        if (allVisibleSelected) R.string.common_clear
                        else R.string.user_activity_select_all,
                    ),
                    tint = ink.copy(alpha = if (isBusy) 0.38f else 0.82f),
                )
            }
            IconButton(onClick = onRestore, enabled = count > 0 && !isBusy) {
                Icon(
                    Icons.Default.Restore,
                    contentDescription = stringResource(
                        R.string.user_activity_recently_deleted_restore_single,
                    ),
                    tint = ink.copy(alpha = if (count > 0 && !isBusy) 1f else 0.38f),
                )
            }
            IconButton(onClick = onDelete, enabled = count > 0 && !isBusy) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.user_activity_recently_deleted_delete_single,
                    ),
                    tint = Color(0xFFFF453A).copy(
                        alpha = if (count > 0 && !isBusy) 1f else 0.38f,
                    ),
                )
            }
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (isSystemInDarkTheme()) Color(0xFF0B1215).copy(alpha = 0.96f) else Color(0xFFFAF9F6).copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$count",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            modifier = Modifier
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            stringResource(countLabelRes, count),
            fontSize = 12.sp,
            color = ink.copy(alpha = 0.45f),
        )
        Spacer(Modifier.weight(1f))
        if (showSelectAll) {
            TextButton(onClick = onSelectAll, enabled = !isBusy) {
                Text(
                    stringResource(if (allVisibleSelected) R.string.common_clear else R.string.user_activity_select_all),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ink.copy(alpha = 0.82f),
                )
            }
        }
        if (isArchived || isRecentlyDeleted) {
            TextButton(onClick = onRestore, enabled = count > 0 && !isBusy) {
                Text(
                    stringResource(
                        if (isArchived) R.string.user_activity_archived_action_restore
                        else R.string.user_activity_recently_deleted_restore_single,
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isArchived) accent else ink,
                )
            }
        }
        if (!isArchived) {
            val actionColor = Color.Red
            TextButton(onClick = onDelete, enabled = count > 0 && !isBusy) {
                Text(
                    stringResource(
                        when (category) {
                            ActivityInteractionCategory.TAGS ->
                                if (count == 1) R.string.user_activity_tags_remove_single else R.string.user_activity_tags_remove_multiple
                            ActivityInteractionCategory.COMMENTS ->
                                if (count == 1) R.string.user_activity_comments_delete_single else R.string.user_activity_comments_delete_multiple
                            ActivityInteractionCategory.STICKER_REPLIES ->
                                if (count == 1) R.string.user_activity_stickers_delete_single else R.string.user_activity_stickers_delete_multiple
                            ActivityInteractionCategory.RECENTLY_DELETED ->
                                R.string.user_activity_recently_deleted_delete_single
                            else ->
                                if (count == 1) R.string.user_activity_reactions_delete_single else R.string.user_activity_reactions_delete_multiple
                        },
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = actionColor,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    action: ActivitySelectionConfirmationAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val titleRes = when (action) {
        is ActivitySelectionConfirmationAction.ReactionsDelete -> R.string.user_activity_reactions_confirm_delete_title
        is ActivitySelectionConfirmationAction.TagsRemove -> R.string.user_activity_tags_confirm_remove_title
        is ActivitySelectionConfirmationAction.CommentsDelete -> R.string.user_activity_comments_confirm_delete_title
        is ActivitySelectionConfirmationAction.StickerRepliesDelete -> R.string.user_activity_stickers_confirm_delete_title
        is ActivitySelectionConfirmationAction.ArchivedRestore -> R.string.user_activity_archived_confirm_restore_title
        is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> R.string.user_activity_recently_deleted_confirm_restore_title
        is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> R.string.user_activity_recently_deleted_confirm_delete_title
    }
    val messageRes = when (action) {
        is ActivitySelectionConfirmationAction.ReactionsDelete -> R.string.user_activity_reactions_confirm_delete_message
        is ActivitySelectionConfirmationAction.TagsRemove -> R.string.user_activity_tags_confirm_remove_message
        is ActivitySelectionConfirmationAction.CommentsDelete -> R.string.user_activity_comments_confirm_delete_message
        is ActivitySelectionConfirmationAction.StickerRepliesDelete -> R.string.user_activity_stickers_confirm_delete_message
        is ActivitySelectionConfirmationAction.ArchivedRestore -> R.string.user_activity_archived_confirm_restore_message
        is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> R.string.user_activity_recently_deleted_confirm_restore_message
        is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> R.string.user_activity_recently_deleted_confirm_delete_message
    }
    val confirmRes = when (action) {
        is ActivitySelectionConfirmationAction.ArchivedRestore -> R.string.user_activity_archived_action_restore
        is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> R.string.user_activity_recently_deleted_restore_single
        is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> R.string.user_activity_recently_deleted_delete_single
        is ActivitySelectionConfirmationAction.TagsRemove -> R.string.user_activity_tags_remove_single
        else -> R.string.user_activity_reactions_delete_single
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.user_activity_common_cancel)) } },
    )
}

private data class ActivityActionBanner(val res: Int, val isError: Boolean)

@Composable
private fun ActionBanner(res: Int, isError: Boolean, isDark: Boolean, ink: Color) {
    val bannerSurface = if (isDark) Color(0xFF20282C) else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.16f))
            .clip(CircleShape)
            .background(bannerSurface)
            .border(0.8.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.35f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (isError) Color(0xFFEF4444) else Color(0xFF22C55E),
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(res),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

@Composable
private fun ProcessingBanner(titleRes: Int, ink: Color, isDark: Boolean) {
    val bannerSurface = if (isDark) Color(0xFF20282C) else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.16f))
            .clip(CircleShape)
            .background(bannerSurface)
            .border(
                0.8.dp,
                Color.White.copy(alpha = if (isDark) 0.10f else 0.35f),
                CircleShape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        CircularProgressIndicator(
            color = ink,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(titleRes),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}
