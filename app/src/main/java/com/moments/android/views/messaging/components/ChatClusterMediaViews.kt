package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import kotlin.math.abs
import java.net.URI

@Composable
fun GlassmorphicClusterRow(
    messages: List<EnhancedMessage>,
    isCurrentUser: Boolean,
    uploadProgress: Map<String, Double>,
    onOpenCluster: (List<EnhancedMessage>) -> Unit,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start) {
        MediaGridBubble(
            messages = messages,
            isCurrentUser = isCurrentUser,
            uploadProgress = uploadProgress,
            onOpenCluster = onOpenCluster,
            onHydrateMedia = onHydrateMedia,
        )
    }
}

/** Timestamp callers use this aggregate while the rest of the row remains media-only. */
fun clusterAggregateStatus(messages: List<EnhancedMessage>): MessageStatus =
    ClusterMessageStatusAggregator.aggregate(messages)

/** Port de `Views/Messaging/Components/ChatClusterMediaViews.swift`. */
object ClusterMessageStatusAggregator {
    private val priority = mapOf(
        MessageStatus.FAILED to -2,
        MessageStatus.PENDING to -1,
        MessageStatus.SENDING to 0,
        MessageStatus.SENT to 1,
        MessageStatus.DELIVERED to 2,
        MessageStatus.READ to 3,
    )

    fun aggregate(messages: List<EnhancedMessage>): MessageStatus =
        messages.minByOrNull { priority[it.status] ?: 0 }?.status ?: MessageStatus.SENT
}

sealed interface ClusterMessageItem {
    data class Single(val message: EnhancedMessage) : ClusterMessageItem
    data class MediaCluster(val messages: List<EnhancedMessage>) : ClusterMessageItem
}

object ClusterMessageGrouper {
    private const val burstWindowMillis = 60_000L

    fun shouldAppendToCluster(message: EnhancedMessage, cluster: List<EnhancedMessage>): Boolean {
        val last = cluster.lastOrNull() ?: return true
        if (message.senderId != last.senderId) return false
        val batch = message.mediaBatchId?.takeIf { it.isNotBlank() }
        val lastBatch = last.mediaBatchId?.takeIf { it.isNotBlank() }
        if (batch != null && lastBatch != null) return batch == lastBatch
        if (batch != null || lastBatch != null) return false
        val delta = message.timestamp.time - last.timestamp.time
        return delta in 0..burstWindowMillis
    }

    fun group(input: List<EnhancedMessage>): List<ClusterMessageItem> {
        val result = mutableListOf<ClusterMessageItem>()
        val current = mutableListOf<EnhancedMessage>()
        fun flush() {
            if (current.isEmpty()) return
            result += if (current.size == 1) ClusterMessageItem.Single(current.first()) else ClusterMessageItem.MediaCluster(current.toList())
            current.clear()
        }
        input.forEach { message ->
            if (message.isDeleted) { flush(); result += ClusterMessageItem.Single(message) }
            else if (message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) {
                if (current.isEmpty() || shouldAppendToCluster(message, current)) current += message else { flush(); current += message }
            } else { flush(); result += ClusterMessageItem.Single(message) }
        }
        flush()
        return result
    }
}

object ClusterMediaLayout {
    val frontWidth = 196.dp
    val frontHeight = 244.dp
    val cornerRadius = 12.dp
    val fanBottomPadding = 10.dp
    const val maxVisible = 5
    val rotations = listOf(-4f, 3f, -2.5f, 4f, -3f)
    val offsets = listOf(0.dp to 0.dp, 10.dp to -10.dp, -8.dp to -19.dp, 14.dp to -27.dp, -6.dp to -34.dp)
    fun fanTopPadding(count: Int): Dp = offsets.take(count.coerceAtLeast(1)).maxOfOrNull { -it.second }?.plus(10.dp) ?: 10.dp
    fun fanSidePadding(count: Int): Dp = offsets.take(count.coerceAtLeast(1)).maxOfOrNull { abs(it.first.value).dp }?.plus(12.dp) ?: 12.dp
}

@Composable
fun MediaGridBubble(
    messages: List<EnhancedMessage>,
    isCurrentUser: Boolean,
    uploadProgress: Map<String, Double>,
    onOpenCluster: (List<EnhancedMessage>) -> Unit,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val active = remember(messages) { messages.filterNot { it.isDeleted } }
    if (active.isEmpty()) return
    val visible = active.take(ClusterMediaLayout.maxVisible)
    val hasVideo = active.any { it.type == MessageType.VIDEO }
    Column(modifier = modifier, horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
        Text(
            stringResource(
                when {
                    isCurrentUser && hasVideo -> R.string.chat_cluster_sent_items
                    isCurrentUser -> R.string.chat_cluster_sent_photos
                    hasVideo -> R.string.chat_cluster_received_items
                    else -> R.string.chat_cluster_received_photos
                },
                active.size,
            ),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = ClusterMediaLayout.fanTopPadding(visible.size), start = ClusterMediaLayout.fanSidePadding(visible.size), end = ClusterMediaLayout.fanSidePadding(visible.size), bottom = ClusterMediaLayout.fanBottomPadding)
                .size(ClusterMediaLayout.frontWidth, ClusterMediaLayout.frontHeight)
                .clickable { onOpenCluster(active) },
        ) {
            visible.asReversed().forEachIndexed { reversedIndex, message ->
                val index = visible.lastIndex - reversedIndex
                val (x, y) = ClusterMediaLayout.offsets[index]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MediaGridTileView(message, uploadProgress[message.id], modifier = Modifier
                        .size(ClusterMediaLayout.frontWidth, ClusterMediaLayout.frontHeight)
                        .rotate(ClusterMediaLayout.rotations[index])
                        .padding(start = x, top = y))
                }
                onHydrateMedia?.invoke(message)
            }
            ClusterCountBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

@Composable
fun MediaGridTileView(
    message: EnhancedMessage,
    progress: Double?,
    isDownloadingMedia: Boolean = false,
    downloadProgress: Double? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val mediaUrl = if (message.type == MessageType.VIDEO) message.thumbnailUrl else message.mediaUrl
    Box(modifier = modifier.clip(RoundedCornerShape(ClusterMediaLayout.cornerRadius)).background(if (isDark) Color.White.copy(.06f) else Color.Black.copy(.06f))) {
        if (mediaUrl.isNullOrBlank()) {
            Icon(if (message.type == MessageType.VIDEO) Icons.Default.VideoFile else Icons.Default.Photo, null, tint = Color.White.copy(.5f), modifier = Modifier.align(Alignment.Center).size(22.dp))
        } else {
            AsyncImage(mediaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (message.type == MessageType.VIDEO) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(14.dp))
        }
        if (isDownloadingMedia || message.status == MessageStatus.SENDING) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B1215).copy(.38f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = (downloadProgress ?: progress ?: .03).toFloat().coerceIn(.03f, 1f), color = Color.White, modifier = Modifier.size(42.dp))
            }
        }
    }
}

@Composable
private fun ClusterCountBadge(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(painterResource(R.drawable.carousel_post_icon), null, contentScale = ContentScale.Fit, modifier = modifier.size(20.dp))
}

data class ClusterWrapper(val messages: List<EnhancedMessage>) {
    val id: String get() = messages.firstOrNull()?.id ?: "empty-cluster"
}

data class ClusterGallerySelection(val anchorMessageId: String, val messageIds: List<String>) {
    val id: String get() = anchorMessageId
}

enum class ClusterGalleryPresentation { MODAL, PUSHED }
enum class ClusterGalleryScope { CLUSTER, CONVERSATION_SHARED }
enum class ClusterGalleryTab { MEDIA, LINKS }

/** Fullscreen/inline Android gallery counterpart to `ClusterGalleryView`. */
@Composable
fun ClusterGalleryView(
    messages: List<EnhancedMessage>,
    currentUserId: String,
    scope: ClusterGalleryScope = ClusterGalleryScope.CLUSTER,
    initialTab: ClusterGalleryTab = ClusterGalleryTab.MEDIA,
    onClose: () -> Unit,
    onOpenMedia: (EnhancedMessage) -> Unit,
    onDeleteForMe: ((List<EnhancedMessage>) -> Unit)? = null,
    onDeleteForEveryone: ((List<EnhancedMessage>) -> Unit)? = null,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val available = remember(messages) { messages.filterNot { it.isDeleted } }
    val visible = remember(available, tab, scope) {
        when {
            scope == ClusterGalleryScope.CLUSTER || tab == ClusterGalleryTab.MEDIA -> available.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
            else -> available.filter { it.type == MessageType.TEXT && firstUrl(it.content) != null }
        }
    }
    LaunchedEffect(visible) {
        selectedIds = selectedIds.intersect(visible.map { it.id }.toSet())
        if (selectedIds.isEmpty()) selectionMode = false
        visible.forEach { onHydrateMedia?.invoke(it) }
    }
    val background = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    Column(modifier.fillMaxSize().background(background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onClose))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.chat_gallery_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(stringResource(if (selectionMode) R.string.common_cancel else R.string.chat_gallery_select), color = if (selectionMode) Color.Red else Color(0xFF007AFF), modifier = Modifier.clickable { selectionMode = !selectionMode; if (!selectionMode) selectedIds = emptySet() })
        }
        if (scope == ClusterGalleryScope.CONVERSATION_SHARED) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GalleryTabButton(R.string.chat_gallery_tab_media, tab == ClusterGalleryTab.MEDIA) { tab = ClusterGalleryTab.MEDIA }
                GalleryTabButton(R.string.chat_gallery_tab_links, tab == ClusterGalleryTab.LINKS) { tab = ClusterGalleryTab.LINKS }
            }
        }
        if (tab == ClusterGalleryTab.LINKS && scope == ClusterGalleryScope.CONVERSATION_SHARED) {
            LazyColumn(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                columnItems(visible, key = { it.id }) { message ->
                    GalleryLinkCard(message, selectionMode, message.id in selectedIds) {
                        selectedIds = if (message.id in selectedIds) selectedIds - message.id else selectedIds + message.id
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(2), modifier = Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(visible, key = { it.id }) { message ->
                    GalleryMediaCard(message, selectionMode, message.id in selectedIds, onClick = {
                        if (selectionMode) selectedIds = if (message.id in selectedIds) selectedIds - message.id else selectedIds + message.id else onOpenMedia(message)
                    })
                }
            }
        }
        if (selectionMode) {
            val selected = visible.filter { it.id in selectedIds }
            val deletable = selected.filter { it.senderId == currentUserId && !it.isRead && System.currentTimeMillis() - it.timestamp.time < 7_200_000L }
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chat_gallery_selected_count, selected.size), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Delete, stringResource(R.string.feed_actions_delete), tint = Color.Red, modifier = Modifier.size(24.dp).clickable(enabled = selected.isNotEmpty()) { onDeleteForMe?.invoke(selected); selectedIds = emptySet(); selectionMode = false })
                if (deletable.isNotEmpty()) Text(stringResource(R.string.chat_action_delete_for_everyone), color = Color.Red, modifier = Modifier.padding(start = 14.dp).clickable { onDeleteForEveryone?.invoke(deletable); selectedIds = emptySet(); selectionMode = false })
            }
        }
    }
}

@Composable private fun GalleryTabButton(@androidx.annotation.StringRes title: Int, selected: Boolean, onClick: () -> Unit) {
    Text(stringResource(title), color = if (selected) Color(0xFF007AFF) else Color.Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.clickable(onClick = onClick).padding(10.dp))
}

@Composable private fun GalleryMediaCard(message: EnhancedMessage, selectionMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(galleryAspectRatio(message)).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        MediaGridTileView(message, null, modifier = Modifier.fillMaxSize())
        if (selectionMode) Icon(Icons.Default.CheckCircle, null, tint = if (selected) Color.White else Color.White.copy(.92f), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp))
        if (selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(.38f)))
    }
}

@Composable private fun GalleryLinkCard(message: EnhancedMessage, selectionMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    val raw = message.content.orEmpty(); val host = firstUrl(raw)?.host.orEmpty()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(if (androidx.compose.foundation.isSystemInDarkTheme()) .08f else .6f)).clickable(onClick = onClick).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Link, null, tint = Color(0xFF007AFF)); Column(Modifier.weight(1f)) { Text(host, color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(raw, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }
        if (selectionMode) Icon(Icons.Default.CheckCircle, null, tint = if (selected) Color.White else Color.White.copy(.92f))
    }
}

private fun galleryAspectRatio(message: EnhancedMessage): Float = message.mediaWidth?.let { width -> message.mediaHeight?.takeIf { it > 0 }?.let { height -> (width.toFloat() / height).coerceIn(.5f, 1.9f) } } ?: .8f
private fun firstUrl(text: String?): URI? = text?.let { Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(it)?.value }?.let { runCatching { URI(it) }.getOrNull() }

@Composable
fun GlassmorphicMediaSelectionSheet(messages: List<EnhancedMessage>, onSelect: (EnhancedMessage) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6))) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chat_reply_select_item), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable(onClick = onCancel))
        }
        LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
            items(messages, key = { it.id }) { message -> MediaGridTileView(message, null, modifier = Modifier.aspectRatio(1f).clickable { onSelect(message) }) }
        }
    }
}
