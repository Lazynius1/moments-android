package com.moments.android.views.messaging.attachments

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.AnimatedGIFView
import com.moments.android.views.creator.components.GiphyGif
import com.moments.android.views.messaging.models.ChatStickerAsset
import com.moments.android.views.messaging.services.ChatGiphyService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de `Views/Messaging/Attachments/ChatGiphyPickerSheet.swift`. */
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
    val primaryText = if (isDark) Color.White else Color.Black
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
        val job = scope.launch {
            runCatching {
                ChatGiphyService.fetch(
                    function = kind.function,
                    mode = activeMode,
                    query = activeQuery.takeIf { activeMode == ChatGiphyService.Mode.SEARCH },
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
        LazyColumn(
            contentPadding = PaddingValues(top = SEARCH_OVERLAY_HEIGHT, bottom = 12.dp),
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
                item(key = "brand") {
                    Text(
                        text = stringResource(R.string.chat_giphy_brand),
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                    )
                }
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
                kind == ChatGiphyPickerKind.STICKER -> {
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
                                    onReachEnd = {
                                        if (gif.id == results.lastOrNull()?.id) fetchPage(nextOffset, append = true)
                                    },
                                )
                            }
                            repeat(STICKER_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                else -> {
                    items(results.chunked(GIF_COLUMNS), key = { row -> row.first().id }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            row.forEach { gif ->
                                ChatGiphyGifCell(
                                    gif = gif,
                                    modifier = Modifier.weight(1f),
                                    onSelect = onSelect,
                                    onReachEnd = {
                                        if (gif.id == results.lastOrNull()?.id) fetchPage(nextOffset, append = true)
                                    },
                                )
                            }
                            if (row.size < GIF_COLUMNS) Spacer(Modifier.weight(1f))
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
        ChatGiphySearchField(
            value = searchText,
            placeholder = stringResource(kind.searchPlaceholderRes),
            primaryText = primaryText,
            secondaryText = secondaryText,
            onValueChange = ::scheduleSearch,
            onClear = { scheduleSearch("") },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
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
    onReachEnd: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(gif.id) { onReachEnd() }
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
    onReachEnd: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(gif.id) { onReachEnd() }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    )
}

@Composable
private fun ChatGiphySearchField(
    value: String,
    placeholder: String,
    primaryText: Color,
    secondaryText: Color,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primaryText == Color.White) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = secondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = secondaryText, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = primaryText, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = secondaryText.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp).clickable(onClick = onClear),
            )
        }
    }
}

private const val PAGE_SIZE = 24
private const val MAX_RECENT_STICKERS = 8
private const val STICKER_COLUMNS = 4
private const val GIF_COLUMNS = 2
private const val SEARCH_DEBOUNCE_MILLIS = 350L
private val GRID_SPACING = 6.dp
private val STICKER_INSET = 8.dp
private val SEARCH_OVERLAY_HEIGHT = 60.dp
