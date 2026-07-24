package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.Moment
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Port MVP de `UserActivityDetailView.swift`. Cubre: estados carga/error/vacío, grid (reacciones/
 * tags/archivados/papelera/moments/reels) y listas (comentarios/eventos), filtros (orden/fecha/
 * autor), modo selección y borrado/restauración por lotes cableados al ViewModel, con diálogo de
 * confirmación y banner de éxito.
 *
 * Diferido a pulido (sin perder funcionalidad de datos): drag-select por arrastre, auto-scroll en
 * selección y la transición de zoom compartida — esta última se delega al host vía [onOpenMoment]
 * (el zoom real existe en `ProfileMomentZoomNavigation`/`MomentZoomOpener`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityInteractionDetailView(
    category: ActivityInteractionCategory,
    recentlyDeletedKind: RecentlyDeletedContentKind = RecentlyDeletedContentKind.MOMENTS,
    suppressInlineNavigationTitle: Boolean = false,
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

    var sort by remember { mutableStateOf(ReactionsSortOption.NEWEST) }
    var dateFilter by remember { mutableStateOf(ReactionsDateFilter.ALL) }
    var selectedAuthorId by remember { mutableStateOf<String?>(null) }
    var showAuthorSheet by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingConfirmation by remember { mutableStateOf<ActivitySelectionConfirmationAction?>(null) }
    var isMutating by remember { mutableStateOf(false) }
    var successBannerRes by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    val supportsAuthorFilter = category in setOf(
        ActivityInteractionCategory.REACTIONS,
        ActivityInteractionCategory.COMMENTS,
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
        isSelectionMode = false
        selectedIds = emptySet()
    }

    fun showBanner(res: Int) {
        successBannerRes = res
        scope.launch {
            kotlinx.coroutines.delay(2000)
            if (successBannerRes == res) successBannerRes = null
        }
    }

    val reactionItems = remember(viewModel.reactionItems, sort, dateFilter, selectedAuthorId) {
        viewModel.reactionItems
            .filterByDate(dateFilter) { it.reactedAt }
            .let { list -> if (supportsAuthorFilter) list.filter { selectedAuthorId == null || it.authorId == selectedAuthorId } else list }
            .sortedByOrder(sort) { it.reactedAt }
    }
    val deletedStories = remember(viewModel.deletedStoryItems, sort, dateFilter) {
        viewModel.deletedStoryItems.filterByDate(dateFilter) { it.deletedAt }.sortedByOrder(sort) { it.deletedAt }
    }
    val commentItems = remember(viewModel.commentItems, sort, dateFilter, selectedAuthorId) {
        viewModel.commentItems
            .filterByDate(dateFilter) { it.commentedAt }
            .filter { selectedAuthorId == null || it.authorId == selectedAuthorId }
            .sortedByOrder(sort) { it.commentedAt }
    }
    val moments = remember(viewModel.moments, sort, dateFilter) {
        viewModel.moments.filterByDate(dateFilter) { it.timestamp }.sortedByOrder(sort) { it.timestamp }
    }
    val eventItems = viewModel.events

    val authorUsernameMap = remember(viewModel.reactionItems, viewModel.commentItems, viewModel.events, category) {
        buildAuthorUsernameMap(category, viewModel)
    }
    val availableAuthorIds = remember(authorUsernameMap) {
        authorUsernameMap.keys.sortedBy { authorUsernameMap[it]?.lowercase() }
    }

    val background = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    Box(modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize()) {
            DetailTopBar(
                titleRes = if (category == ActivityInteractionCategory.ARCHIVED) {
                    R.string.user_activity_cat_stories_archive_title
                } else {
                    category.titleRes
                },
                suppressTitle = suppressInlineNavigationTitle,
                selectionActionLabel = when {
                    !supportsSelection -> null
                    isSelectionMode -> R.string.user_activity_cancel
                    category == ActivityInteractionCategory.ARCHIVED -> null
                    else -> R.string.user_activity_select
                },
                onBack = onBack,
                onSelectionAction = { if (isSelectionMode) clearSelection() else isSelectionMode = true },
            )

            when {
                viewModel.isLoading -> LoadingState()
                viewModel.errorMessage != null -> ErrorState(viewModel.errorMessage!!)
                else -> Column(Modifier.fillMaxSize()) {
                    FiltersHeader(
                        sort = sort, onSort = { sort = it },
                        dateFilter = dateFilter, onDateFilter = { dateFilter = it },
                        showAuthor = supportsAuthorFilter && availableAuthorIds.isNotEmpty(),
                        selectedAuthorId = selectedAuthorId,
                        authorUsernameMap = authorUsernameMap,
                        onOpenAuthorSheet = { showAuthorSheet = true },
                    )

                    val emptyRes = category.emptyRes
                    when (category) {
                        ActivityInteractionCategory.RECENTLY_DELETED -> if (recentlyDeletedKind == RecentlyDeletedContentKind.STORIES) {
                            if (deletedStories.isEmpty()) EmptyState(emptyRes) else DeletedStoriesGrid(deletedStories, isSelectionMode, selectedIds, { toggle(it, selectedIds) { s -> selectedIds = s } }, { onOpenDeletedStory(it, deletedStories) })
                        } else {
                            if (reactionItems.isEmpty()) EmptyState(emptyRes) else ReactionsGrid(reactionItems, category, isSelectionMode, selectedIds, { toggle(it, selectedIds) { s -> selectedIds = s } }, { m -> onOpenMoment(m, reactionItems.mapNotNull { it.moment }) }) { id -> if (!isSelectionMode) { isSelectionMode = true }; selectedIds = selectedIds + id }
                        }
                        ActivityInteractionCategory.REACTIONS, ActivityInteractionCategory.TAGS, ActivityInteractionCategory.ARCHIVED ->
                            if (reactionItems.isEmpty()) EmptyState(emptyRes) else ReactionsGrid(reactionItems, category, isSelectionMode, selectedIds, { toggle(it, selectedIds) { s -> selectedIds = s } }, { m -> onOpenMoment(m, reactionItems.mapNotNull { it.moment }) }) { id -> if (category == ActivityInteractionCategory.ARCHIVED) { if (!isSelectionMode) isSelectionMode = true; selectedIds = selectedIds + id } }
                        ActivityInteractionCategory.COMMENTS ->
                            if (commentItems.isEmpty()) EmptyState(emptyRes) else CommentsList(commentItems, isSelectionMode, selectedIds, { toggle(it, selectedIds) { s -> selectedIds = s } }, { m -> onOpenMoment(m, commentItems.mapNotNull { it.moment }) }, onOpenProfile)
                        ActivityInteractionCategory.MOMENTS, ActivityInteractionCategory.REELS ->
                            if (moments.isEmpty()) EmptyState(emptyRes) else MomentsGrid(moments, category == ActivityInteractionCategory.REELS, { m -> if (category == ActivityInteractionCategory.REELS) onOpenReels(m, moments) else onOpenMoment(m, moments) })
                        else ->
                            if (eventItems.isEmpty()) EmptyState(emptyRes) else EventsList(eventItems, isSelectionMode, selectedIds, { toggle(it, selectedIds) { s -> selectedIds = s } }, onOpenProfile)
                    }
                }
            }
        }

        if (isSelectionMode && supportsSelection) {
            SelectionBar(
                category = category,
                count = selectedIds.size,
                isBusy = isMutating,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                pendingConfirmation = when (category) {
                    ActivityInteractionCategory.TAGS -> ActivitySelectionConfirmationAction.TagsRemove
                    ActivityInteractionCategory.COMMENTS -> ActivitySelectionConfirmationAction.CommentsDelete
                    ActivityInteractionCategory.STICKER_REPLIES -> ActivitySelectionConfirmationAction.StickerRepliesDelete
                    ActivityInteractionCategory.RECENTLY_DELETED -> ActivitySelectionConfirmationAction.RecentlyDeletedDelete
                    ActivityInteractionCategory.ARCHIVED -> ActivitySelectionConfirmationAction.ArchivedRestore(selectedIds)
                    else -> ActivitySelectionConfirmationAction.ReactionsDelete
                }
            }
        }

        successBannerRes?.let { res ->
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
                SuccessBanner(res)
            }
        }
    }

    if (showAuthorSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showAuthorSheet = false }, sheetState = sheetState) {
            AuthorFilterSheet(
                selectedAuthorId = selectedAuthorId,
                availableAuthorIds = availableAuthorIds,
                authorUsernameMap = authorUsernameMap,
                onSelect = { selectedAuthorId = it; showAuthorSheet = false },
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
                    if (result.isSuccess) {
                        clearSelection()
                        showBanner(successResFor(action))
                    }
                }
            },
        )
    }
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

private inline fun <T> List<T>.filterByDate(filter: ReactionsDateFilter, crossinline dateOf: (T) -> Date): List<T> {
    if (filter == ReactionsDateFilter.ALL) return this
    val cal = Calendar.getInstance()
    val from = when (filter) {
        ReactionsDateFilter.WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        ReactionsDateFilter.MONTH -> cal.apply { add(Calendar.MONTH, -1) }.time
        ReactionsDateFilter.YEAR -> cal.apply { add(Calendar.YEAR, -1) }.time
        else -> return this // CUSTOM: rango de fechas diferido a pulido, se comporta como ALL
    }
    return filter { dateOf(it) >= from }
}

private inline fun <T> List<T>.sortedByOrder(sort: ReactionsSortOption, crossinline dateOf: (T) -> Date): List<T> =
    if (sort == ReactionsSortOption.NEWEST) sortedByDescending { dateOf(it) } else sortedBy { dateOf(it) }

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
    onBack: () -> Unit,
    onSelectionAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!suppressTitle) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(titleRes),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectionActionLabel != null) {
            TextButton(onClick = onSelectionAction) {
                Text(stringResource(selectionActionLabel), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.user_activity_loading), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    val offline = listOf("offline", "internet", "network", "connection").any { message.contains(it, ignoreCase = true) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (offline) "📡" else "⚠️", fontSize = 48.sp)
            Text(
                stringResource(if (offline) R.string.user_activity_error_offline_title else R.string.user_activity_error_generic_title),
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (offline) R.string.user_activity_error_offline_subtitle else R.string.user_activity_error_generic_subtitle),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyState(emptyRes: Int?) {
    Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
        Text(
            text = emptyRes?.let { stringResource(it) }.orEmpty(),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
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
        )
        FilterChipMenu(
            label = stringResource(R.string.user_activity_filters_date),
            value = stringResource(dateFilter.titleRes),
            options = ReactionsDateFilter.entries.map { it to stringResource(it.titleRes) },
            selected = dateFilter,
            onSelect = onDateFilter,
        )
        if (showAuthor) {
            val authorLabel = selectedAuthorId?.let { authorUsernameMap[it] }
                ?: stringResource(R.string.user_activity_filters_author)
            FilterChip(label = authorLabel, onClick = onOpenAuthorSheet)
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
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(label = "$label: $value", onClick = { expanded = true })
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
private fun FilterChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ReactionsGrid(
    items: List<ActivityReactionItem>,
    category: ActivityInteractionCategory,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (Moment) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val overlay = when (category) {
        ActivityInteractionCategory.REACTIONS, ActivityInteractionCategory.TAGS -> ActivityOverlayBadgeStyle.REACTION_DISCREET
        ActivityInteractionCategory.ARCHIVED -> ActivityOverlayBadgeStyle.AUDIENCE
        else -> ActivityOverlayBadgeStyle.NONE
    }
    val side = gridCellSide()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(top = 8.dp, bottom = if (isSelectionMode) 88.dp else 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                Modifier.clickable {
                    if (isSelectionMode) onToggle(item.id)
                    else item.moment?.takeIf { item.canView }?.let(onOpen)
                },
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
    }
}

@Composable
private fun gridCellSide(): androidx.compose.ui.unit.Dp {
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return ((widthDp - 2) / 3).dp
}

@Composable
private fun DeletedStoriesGrid(
    items: List<ActivityDeletedStoryItem>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (ActivityDeletedStoryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(top = 8.dp, bottom = if (isSelectionMode) 88.dp else 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                Modifier.clickable { if (isSelectionMode) onToggle(item.id) else onOpen(item) },
            ) {
                ActivityDeletedStoryCard(
                    item = item,
                    isSelectionMode = isSelectionMode,
                    isSelected = item.id in selectedIds,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MomentsGrid(moments: List<Moment>, isReels: Boolean, onOpen: (Moment) -> Unit) {
    val side = gridCellSide()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(moments, key = { it.id ?: it.hashCode().toString() }) { moment ->
            Box(Modifier.clickable { onOpen(moment) }) {
                if (isReels) {
                    ActivityPortraitMomentCard(moment, Modifier.fillMaxWidth())
                } else {
                    ActivityReactionMomentCard(
                        item = ActivityReactionItem(moment.id.orEmpty(), moment.authorId, moment.id.orEmpty(), "moment", moment.timestamp, moment, true),
                        size = side,
                        isSelectionMode = false,
                        isSelected = false,
                        overlayBadge = ActivityOverlayBadgeStyle.AUDIENCE,
                    )
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
    onOpenProfile: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        lazyColumnItems(items, key = { it.id }) { item ->
            ActivityCommentItemRow(
                item = item,
                isSelectionMode = isSelectionMode,
                isSelected = item.id in selectedIds,
                onOpenMoment = { item.moment?.takeIf { item.canView }?.let(onOpenMoment) },
                onOpenAuthorAvatar = { onOpenProfile(item.authorId) },
                onOpenAuthorProfile = { onOpenProfile(item.authorId) },
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
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        lazyColumnItems(items, key = { it.id }) { item ->
            ActivityEventRow(
                item = item,
                isSelectionMode = isSelectionMode,
                isSelected = item.id in selectedIds,
                onOpenTargetProfile = { (item.targetAuthorId ?: item.actorId)?.let(onOpenProfile) },
                onRowTap = { if (isSelectionMode) onToggle(item.id) },
            )
        }
    }
}

@Composable
private fun SelectionBar(
    category: ActivityInteractionCategory,
    count: Int,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    val isRestore = category == ActivityInteractionCategory.ARCHIVED
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$count",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        val actionColor = if (isRestore) MaterialTheme.colorScheme.primary else Color.Red
        Row(
            Modifier
                .clip(CircleShape)
                .background(actionColor.copy(alpha = if (count > 0) 0.9f else 0.45f))
                .clickable(enabled = count > 0 && !isBusy, onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(selectionActionLabelRes(category, count)),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

private fun selectionActionLabelRes(category: ActivityInteractionCategory, count: Int): Int = when (category) {
    ActivityInteractionCategory.TAGS -> if (count == 1) R.string.user_activity_tags_remove_single else R.string.user_activity_tags_remove_multiple
    ActivityInteractionCategory.COMMENTS -> if (count == 1) R.string.user_activity_comments_delete_single else R.string.user_activity_comments_delete_multiple
    ActivityInteractionCategory.STICKER_REPLIES -> if (count == 1) R.string.user_activity_stickers_delete_single else R.string.user_activity_stickers_delete_multiple
    ActivityInteractionCategory.ARCHIVED -> R.string.user_activity_archived_action_restore
    ActivityInteractionCategory.RECENTLY_DELETED -> R.string.user_activity_recently_deleted_delete_single
    else -> if (count == 1) R.string.user_activity_reactions_delete_single else R.string.user_activity_reactions_delete_multiple
}

@Composable
private fun ConfirmationDialog(
    action: ActivitySelectionConfirmationAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (titleRes, messageRes, confirmRes) = when (action) {
        is ActivitySelectionConfirmationAction.ReactionsDelete -> Triple(R.string.user_activity_reactions_confirm_delete_title, R.string.user_activity_reactions_confirm_delete_message, R.string.user_activity_reactions_delete_single)
        is ActivitySelectionConfirmationAction.TagsRemove -> Triple(R.string.user_activity_tags_confirm_remove_title, R.string.user_activity_tags_confirm_remove_message, R.string.user_activity_tags_remove_single)
        is ActivitySelectionConfirmationAction.CommentsDelete -> Triple(R.string.user_activity_comments_confirm_delete_title, R.string.user_activity_comments_confirm_delete_message, R.string.user_activity_comments_delete_single)
        is ActivitySelectionConfirmationAction.StickerRepliesDelete -> Triple(R.string.user_activity_stickers_confirm_delete_title, R.string.user_activity_stickers_confirm_delete_message, R.string.user_activity_stickers_delete_single)
        is ActivitySelectionConfirmationAction.ArchivedRestore -> Triple(R.string.user_activity_archived_confirm_restore_title, R.string.user_activity_archived_confirm_restore_message, R.string.user_activity_archived_action_restore)
        is ActivitySelectionConfirmationAction.RecentlyDeletedRestore -> Triple(R.string.user_activity_recently_deleted_confirm_restore_title, R.string.user_activity_recently_deleted_confirm_restore_message, R.string.user_activity_recently_deleted_restore_single)
        is ActivitySelectionConfirmationAction.RecentlyDeletedDelete -> Triple(R.string.user_activity_recently_deleted_confirm_delete_title, R.string.user_activity_recently_deleted_confirm_delete_message, R.string.user_activity_recently_deleted_delete_single)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.user_activity_common_cancel)) } },
    )
}

@Composable
private fun SuccessBanner(res: Int) {
    Text(
        stringResource(res),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16A34A))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

