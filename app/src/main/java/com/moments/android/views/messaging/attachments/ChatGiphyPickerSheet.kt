package com.moments.android.views.messaging.attachments

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.AnimatedGIFView
import com.moments.android.views.creator.components.GiphyGif
import com.moments.android.views.messaging.components.ChatAttachmentSearchField
import com.moments.android.views.messaging.components.ChatAttachmentSheetMetrics
import com.moments.android.views.messaging.models.ChatStickerAsset
import com.moments.android.views.messaging.services.ChatGiphyService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `Views/Messaging/Attachments/ChatGiphyPickerSheet.swift` (`ChatGiphyPickerContent`). */
enum class ChatGiphyPickerKind(
    val function: ChatGiphyService.FunctionName,
    @StringRes val searchPlaceholderRes: Int,
) {
    GIF(ChatGiphyService.FunctionName.GIFS, R.string.chat_giphy_search_gif),
    STICKER(ChatGiphyService.FunctionName.STICKERS, R.string.chat_giphy_search_sticker),
}

@Composable
fun ChatGiphyPickerContent(
    kind: ChatGiphyPickerKind,
    accentColor: Color,
    onSelect: (GiphyGif) -> Unit,
    recents: List<ChatStickerAsset> = emptyList(),
    onSelectRecent: ((ChatStickerAsset) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<GiphyGif>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var activeMode by remember { mutableStateOf(ChatGiphyService.Mode.TRENDING) }
    var activeQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var loadMoreJob by remember { mutableStateOf<Job?>(null) }
    var requestVersion by remember { mutableIntStateOf(0) }

    val isDark = isSystemInDarkTheme()
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
    val displayedRecents = remember(recents) { recents.take(MAX_RECENT_STICKERS) }
    val showsPinnedRecents = kind == ChatGiphyPickerKind.STICKER &&
        displayedRecents.isNotEmpty() && onSelectRecent != null && searchText.trim().isEmpty()

    fun resetPagination() {
        loadMoreJob?.cancel()
        nextOffset = 0
        hasMorePages = true
        isLoadingMore = false
    }

    fun fetchPage(offset: Int, append: Boolean) {
        if (append) {
            if (isLoadingMore || isLoading || !hasMorePages) return
            isLoadingMore = true
            loadMoreJob?.cancel()
        } else {
            searchJob?.cancel()
            loadMoreJob?.cancel()
            isLoading = true
            isLoadingMore = false
            loadError = false
            results = emptyList()
        }
        val version = ++requestVersion
        val mode = activeMode
        val query = activeQuery
        val job = scope.launch {
            runCatching {
                ChatGiphyService.fetch(
                    function = kind.function,
                    mode = mode,
                    query = query.takeIf { mode == ChatGiphyService.Mode.SEARCH },
                    offset = offset,
                    limit = PAGE_SIZE,
                )
            }.onSuccess { page ->
                if (version != requestVersion) return@onSuccess
                results = if (append) {
                    val existingIds = results.asSequence().map { it.id }.toHashSet()
                    results + page.items.filter { it.id !in existingIds }
                } else {
                    page.items
                }
                nextOffset = page.nextOffset
                hasMorePages = page.hasMore
                loadError = false
            }.onFailure {
                if (version == requestVersion && !append) loadError = true
            }
            if (version == requestVersion) {
                isLoading = false
                isLoadingMore = false
            }
        }
        if (append) loadMoreJob = job else searchJob = job
    }

    // ≡ iOS `loadMoreIfNeeded` + `triggerLoadMoreIfNeeded(for:)` (solo `results.last`)
    fun loadMoreIfNeeded(gif: GiphyGif) {
        if (gif.id != results.lastOrNull()?.id) return
        if (!hasMorePages || isLoading || isLoadingMore) return
        fetchPage(offset = nextOffset, append = true)
    }

    fun loadTrending() {
        resetPagination()
        activeMode = ChatGiphyService.Mode.TRENDING
        activeQuery = ""
        fetchPage(offset = 0, append = false)
    }

    fun scheduleSearch(query: String) {
        searchText = query
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            loadTrending()
            return
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            if (searchText.trim() != trimmed) return@launch
            resetPagination()
            activeMode = ChatGiphyService.Mode.SEARCH
            activeQuery = trimmed
            fetchPage(offset = 0, append = false)
        }
    }

    DisposableEffect(Unit) {
        loadTrending()
        onDispose {
            searchJob?.cancel()
            loadMoreJob?.cancel()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (kind) {
            // ≡ iOS LazyVGrid 4 columnas
            ChatGiphyPickerKind.STICKER -> StickerPickerScroll(
                showsPinnedRecents = showsPinnedRecents,
                displayedRecents = displayedRecents,
                onSelectRecent = onSelectRecent,
                loadError = loadError,
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                results = results,
                accentColor = accentColor,
                secondaryText = secondaryText,
                onSelect = onSelect,
                onLoadMore = ::loadMoreIfNeeded,
            )
            // ≡ iOS gifMasonryGrid (HStack + 2 LazyVStack alternando)
            ChatGiphyPickerKind.GIF -> GifMasonryScroll(
                loadError = loadError,
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                results = results,
                accentColor = accentColor,
                secondaryText = secondaryText,
                onSelect = onSelect,
                onLoadMore = ::loadMoreIfNeeded,
            )
        }

        ChatAttachmentSearchField(
            placeholderRes = kind.searchPlaceholderRes,
            text = searchText,
            onTextChange = ::scheduleSearch,
            onClear = { loadTrending(); searchText = "" },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun StickerPickerScroll(
    showsPinnedRecents: Boolean,
    displayedRecents: List<ChatStickerAsset>,
    onSelectRecent: ((ChatStickerAsset) -> Unit)?,
    loadError: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    results: List<GiphyGif>,
    accentColor: Color,
    secondaryText: Color,
    onSelect: (GiphyGif) -> Unit,
    onLoadMore: (GiphyGif) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = ChatAttachmentSheetMetrics.searchOverlayHeight, bottom = 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showsPinnedRecents) {
            item(key = "recents") {
                ChatGiphyRecentsSection(
                    stickers = displayedRecents,
                    secondaryText = secondaryText,
                    onSelect = { sticker ->
                        HapticManager.shared.lightImpact()
                        onSelectRecent?.invoke(sticker)
                    },
                )
            }
        }
        if (!loadError) {
            item(key = "brand") { GiphyBrandHeader(secondaryText) }
        }
        when {
            isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
            loadError -> item(key = "error") {
                ChatGiphyStateMessage(R.string.chat_giphy_error, secondaryText)
            }
            results.isEmpty() -> item(key = "empty") {
                ChatGiphyStateMessage(R.string.chat_giphy_empty, secondaryText)
            }
            else -> {
                items(results.chunked(STICKER_COLUMNS), key = { row -> row.first().id }) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    ) {
                        row.forEach { gif ->
                            ChatGiphyStickerCell(
                                gif = gif,
                                modifier = Modifier.weight(1f),
                                onSelect = onSelect,
                                onAppear = { onLoadMore(gif) },
                            )
                        }
                        repeat(STICKER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        }
    }
}

@Composable
private fun GifMasonryScroll(
    loadError: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    results: List<GiphyGif>,
    accentColor: Color,
    secondaryText: Color,
    onSelect: (GiphyGif) -> Unit,
    onLoadMore: (GiphyGif) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(GIF_COLUMNS),
        contentPadding = PaddingValues(
            top = ChatAttachmentSheetMetrics.searchOverlayHeight,
            bottom = 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalItemSpacing = GRID_SPACING,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
    ) {
        if (!loadError) {
            item(span = StaggeredGridItemSpan.FullLine, key = "brand") {
                GiphyBrandHeader(secondaryText, horizontalPadding = 4.dp)
            }
        }
        when {
            isLoading -> item(span = StaggeredGridItemSpan.FullLine, key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
            loadError -> item(span = StaggeredGridItemSpan.FullLine, key = "error") {
                ChatGiphyStateMessage(R.string.chat_giphy_error, secondaryText)
            }
            results.isEmpty() -> item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                ChatGiphyStateMessage(R.string.chat_giphy_empty, secondaryText)
            }
            else -> {
                items(results, key = { it.id }) { gif ->
                    ChatGiphyGifCell(
                        gif = gif,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = onSelect,
                        onAppear = { onLoadMore(gif) },
                    )
                }
            }
        }
        if (isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine, key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            }
        }
    }
}

@Composable
private fun GiphyBrandHeader(secondaryText: Color, horizontalPadding: Dp = 16.dp) {
    Text(
        text = stringResource(R.string.chat_giphy_brand).uppercase(),
        color = secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun ChatGiphyRecentsSection(
    stickers: List<ChatStickerAsset>,
    secondaryText: Color,
    onSelect: (ChatStickerAsset) -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.chat_giphy_recents),
            color = secondaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        stickers.chunked(STICKER_COLUMNS).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            ) {
                row.forEach { sticker ->
                    AnimatedGIFView(
                        url = sticker.url,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable { onSelect(sticker) }
                            .padding(STICKER_INSET),
                    )
                }
                repeat(STICKER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChatGiphyStickerCell(
    gif: GiphyGif,
    modifier: Modifier,
    onSelect: (GiphyGif) -> Unit,
    onAppear: () -> Unit,
) {
    // ≡ iOS `.onAppear { triggerLoadMoreIfNeeded(for: gif) }`
    LaunchedEffect(gif.id) { onAppear() }
    AnimatedGIFView(
        url = gif.images.fixedHeight.url,
        modifier = modifier
            .aspectRatio(1f)
            .clickable {
                HapticManager.shared.lightImpact()
                onSelect(gif)
            }
            .padding(STICKER_INSET),
    )
}

@Composable
private fun ChatGiphyGifCell(
    gif: GiphyGif,
    modifier: Modifier,
    onSelect: (GiphyGif) -> Unit,
    onAppear: () -> Unit,
) {
    // ≡ iOS `.onAppear { triggerLoadMoreIfNeeded(for: gif) }` — el guard de last está en loadMoreIfNeeded
    LaunchedEffect(gif.id) { onAppear() }
    AnimatedGIFView(
        url = gif.images.fixedHeight.url,
        modifier = modifier
            .aspectRatio(gif.previewAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                HapticManager.shared.lightImpact()
                onSelect(gif)
            },
    )
}

@Composable
private fun ChatGiphyStateMessage(@StringRes textRes: Int, secondaryText: Color) {
    Text(
        text = stringResource(textRes),
        color = secondaryText,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    )
}

private const val PAGE_SIZE = 24
private const val MAX_RECENT_STICKERS = 8
private const val STICKER_COLUMNS = 4
private const val GIF_COLUMNS = 2
private const val SEARCH_DEBOUNCE_MILLIS = 350L
private val GRID_SPACING = 6.dp
private val STICKER_INSET = 8.dp
