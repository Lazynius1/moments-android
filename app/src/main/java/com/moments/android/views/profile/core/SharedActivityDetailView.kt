package com.moments.android.views.profile.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.moments.android.views.settings.ReactionsDateFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.settings.ActivityCommentItem
import com.moments.android.views.settings.ActivityReactionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.Calendar
import android.app.DatePickerDialog

private enum class SharedActivityDirection(val raw: String) { VIEWER_ON_OTHER("viewer_on_other"), OTHER_ON_VIEWER("other_on_viewer") }

/** ViewModel 1:1 de la primera sección de `SharedActivityDetailView.swift`. */
class SharedActivityDetailViewModel(
    val category: SharedActivityCategory,
    val currentUser: AppUser?,
    val otherUser: AppUser,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var selectedTabState by mutableStateOf(0)
    var selectedTab: Int
        get() = selectedTabState
        set(value) { if (selectedTabState != value) { selectedTabState = value; reload() } }
    var isLoading by mutableStateOf(false); var errorMessage by mutableStateOf<String?>(null)
    var reactionItems by mutableStateOf<List<ActivityReactionItem>>(emptyList()); var commentItems by mutableStateOf<List<ActivityCommentItem>>(emptyList())
    var hasMore by mutableStateOf(true)
    private var didLoadOnce = false
    private var reactionsCursor: Double? = null; private var commentsCursor: Double? = null; private var tagsCursor: Double? = null

    fun loadIfNeeded() { if (!didLoadOnce) { didLoadOnce = true; reload() } }
    fun reload() { reactionItems = emptyList(); commentItems = emptyList(); reactionsCursor = null; commentsCursor = null; tagsCursor = null; hasMore = true; errorMessage = null; loadNextPage() }
    fun loadNextPage() {
        if (isLoading || !hasMore) return
        if (currentUser?.id.isNullOrBlank()) { errorMessage = SharedActivityFailure.NOT_AUTHENTICATED; hasMore = false; return }
        isLoading = true
        scope.launch {
            try {
                when (category) { SharedActivityCategory.REACTIONS -> fetchReactions(); SharedActivityCategory.COMMENTS -> fetchComments(); SharedActivityCategory.TAGS -> fetchTags() }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { errorMessage = error.message ?: SharedActivityFailure.GENERIC; hasMore = false
            } finally { isLoading = false }
        }
    }
    fun clear() { scope.coroutineContext.cancel() }
    private val direction get() = if (selectedTab == 0) SharedActivityDirection.VIEWER_ON_OTHER else SharedActivityDirection.OTHER_ON_VIEWER

    suspend fun removeReactions(ids: Set<String>): Result<Unit> = removeBatch(ids, reactionItems, "removeSharedReactionsBatch") { item -> JSONObject().put("authorId", item.authorId).put("momentId", item.momentId) }.also { if (it.isSuccess) reactionItems = reactionItems.filterNot { item -> item.id in ids } }
    suspend fun removeTags(ids: Set<String>): Result<Unit> = removeBatch(ids, reactionItems, "removeSharedTagsBatch") { item -> JSONObject().put("authorId", item.authorId).put("momentId", item.momentId) }.also { if (it.isSuccess) reactionItems = reactionItems.filterNot { item -> item.id in ids } }
    suspend fun removeComments(ids: Set<String>): Result<Unit> = runCatching {
        val selected = commentItems.filter { it.id in ids }; if (selected.isNotEmpty()) postVoid("deleteSharedCommentsBatch", JSONObject().put("otherUserId", otherUser.id).put("direction", direction.raw).put("comments", JSONArray(selected.map { JSONObject().put("authorId", it.authorId).put("momentId", it.momentId).put("commentId", it.commentId) })))
        commentItems = commentItems.filterNot { it.id in ids }
    }
    private suspend fun removeBatch(ids: Set<String>, items: List<ActivityReactionItem>, function: String, target: (ActivityReactionItem) -> JSONObject): Result<Unit> = runCatching {
        val selected = items.filter { it.id in ids }; if (selected.isNotEmpty()) postVoid(function, JSONObject().put("otherUserId", otherUser.id).put("direction", direction.raw).put(if (function == "removeSharedTagsBatch") "moments" else "reactions", JSONArray(selected.map(target))))
    }
    private suspend fun fetchReactions() { val response = post("getSharedReactedMomentsPage", reactionsCursor); reactionsCursor = response.optJSONObject("nextCursor")?.optDoubleOrNull("timestamp"); hasMore = reactionsCursor != null; reactionItems += response.optJSONArray("items").toReactionItems("reactedAt") }
    private suspend fun fetchTags() { val response = post("getSharedTaggedMomentsPage", tagsCursor); tagsCursor = response.optJSONObject("nextCursor")?.optDoubleOrNull("timestamp"); hasMore = tagsCursor != null; reactionItems += response.optJSONArray("items").toReactionItems("taggedAt", "tagged") }
    private suspend fun fetchComments() { val response = post("getSharedCommentedMomentsPage", commentsCursor); commentsCursor = response.optJSONObject("nextCursor")?.optDoubleOrNull("timestamp"); hasMore = commentsCursor != null; commentItems += response.optJSONArray("items").toCommentItems() }
    private suspend fun post(function: String, cursor: Double?): JSONObject { val payload = JSONObject().put("otherUserId", otherUser.id).put("direction", direction.raw).put("limit", 36); cursor?.let { payload.put("cursor", JSONObject().put("timestamp", it)) }; return postJson(function, payload) }
    private suspend fun postVoid(function: String, payload: JSONObject) { postResponse(function, payload) }
    private suspend fun postJson(function: String, payload: JSONObject): JSONObject = JSONObject(postResponse(function, payload))
    private suspend fun postResponse(function: String, payload: JSONObject): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException(SharedActivityFailure.NOT_AUTHENTICATED)
        val token = user.getIdToken(false).await().token ?: throw IllegalStateException(SharedActivityFailure.NOT_AUTHENTICATED)
        val project = FirebaseApp.getInstance().options.projectId ?: throw IllegalStateException(SharedActivityFailure.GENERIC)
        val connection = URL("https://europe-southwest1-$project.cloudfunctions.net/$function").openConnection() as HttpURLConnection
        try { connection.requestMethod = "POST"; connection.connectTimeout = 20_000; connection.readTimeout = 20_000; connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Authorization", "Bearer $token"); connection.doOutput = true; connection.outputStream.use { it.write(payload.toString().toByteArray()) }; val code = connection.responseCode; val text = (if (code == 200) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }; if (code != 200) throw IllegalStateException(SharedActivityFailure.GENERIC); text } finally { connection.disconnect() }
    }
}

private object SharedActivityFailure { const val NOT_AUTHENTICATED = "shared_activity_not_authenticated"; const val GENERIC = "shared_activity_generic_failure" }

private fun JSONArray?.toReactionItems(dateKey: String, fallbackReaction: String? = null): List<ActivityReactionItem> = buildList {
    if (this@toReactionItems == null) return@buildList
    for (index in 0 until this@toReactionItems.length()) { val item = this@toReactionItems.optJSONObject(index) ?: continue; val moment = item.optJSONObject("moment")?.toMoment(); if (moment?.isArchived == true) continue; val author = item.optString("authorId", moment?.authorId.orEmpty()); val momentId = item.optString("momentId", moment?.id.orEmpty()); if (momentId.isBlank()) continue; val time = item.optDoubleOrNull(dateKey)?.let { Date(it.toLong()) } ?: moment?.timestamp ?: Date(); add(ActivityReactionItem("${author}_${momentId}_${time.time}", author, momentId, item.optString("reactionType", fallbackReaction.orEmpty()), time, moment, item.optBoolean("canView", true))) }
}
private fun JSONArray?.toCommentItems(): List<ActivityCommentItem> = buildList {
    if (this@toCommentItems == null) return@buildList
    for (index in 0 until this@toCommentItems.length()) { val item = this@toCommentItems.optJSONObject(index) ?: continue; val moment = item.optJSONObject("moment")?.toMoment(); if (moment?.isArchived == true) continue; val author = item.optString("authorId", moment?.authorId.orEmpty()); val momentId = item.optString("momentId", moment?.id.orEmpty()); val comment = item.optJSONObject("comment"); val commentId = item.optString("commentId", comment?.optString("id").orEmpty()); if (momentId.isBlank() || commentId.isBlank()) continue; val time = item.optDoubleOrNull("commentedAt") ?: comment?.optDoubleOrNull("timestamp"); add(ActivityCommentItem("${author}_${momentId}_${commentId}", author, momentId, commentId, comment?.optString("content").orEmpty().trim(), time?.let { Date(it.toLong()) } ?: moment?.timestamp ?: Date(), moment, item.optBoolean("canView", true))) }
}
private fun JSONObject.toMoment(): Moment = Moment.from(optString("id").ifBlank { null }, toMap())
private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> when (val value = opt(key)) { is JSONObject -> value.toMap(); is JSONArray -> (0 until value.length()).map { i -> value.opt(i).let { if (it is JSONObject) it.toMap() else it } }; JSONObject.NULL -> null; else -> value } }
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null

enum class SharedActivityCategory(val titleRes: Int, val accentColor: Color) {
    REACTIONS(R.string.shared_activity_reactions, Color(0xFFF97316)), COMMENTS(R.string.shared_activity_comments, Color(0xFF3B82F6)), TAGS(R.string.shared_activity_tags, Color(0xFFEC4899))
}

/** Port Compose del cuerpo de `SharedActivityDetailView`, con refresh, filtros y borrado por lotes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedActivityDetailView(category: SharedActivityCategory, currentUser: AppUser?, otherUser: AppUser, onOpenMoment: (Moment) -> Unit, onOpenProfile: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = remember(category, currentUser?.id, otherUser.id) { SharedActivityDetailViewModel(category, currentUser, otherUser) }
    val scope = rememberCoroutineScope()
    var sortNewest by remember { mutableStateOf(true) }; var filter by remember { mutableStateOf(ReactionsDateFilter.ALL) }
    var customDateFrom by remember { mutableStateOf(oneMonthAgo()) }; var customDateTo by remember { mutableStateOf(Date()) }
    var selectionMode by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf<Set<String>>(emptySet()) }; var confirmation by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf(false) }; var success by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.loadIfNeeded() }
    DisposableEffect(viewModel) { onDispose(viewModel::clear) }
    val reactions = viewModel.reactionItems.filter { matchesSharedDate(it.reactedAt, filter, customDateFrom, customDateTo) }.let { if (sortNewest) it.sortedByDescending(ActivityReactionItem::reactedAt) else it.sortedBy(ActivityReactionItem::reactedAt) }
    val comments = viewModel.commentItems.filter { matchesSharedDate(it.commentedAt, filter, customDateFrom, customDateTo) }.let { if (sortNewest) it.sortedByDescending(ActivityCommentItem::commentedAt) else it.sortedBy(ActivityCommentItem::commentedAt) }
    Column(modifier.fillMaxSize().background(sharedCanvas()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = sharedPrimary(), modifier = Modifier.size(28.dp).clickable(onClick = onDismiss))
            Text(stringResource(category.titleRes), color = sharedPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
            Spacer(Modifier.weight(1f))
            TextButton({ selectionMode = !selectionMode; if (!selectionMode) selected = emptySet() }) { Text(stringResource(if (selectionMode) R.string.shared_activity_cancel else R.string.shared_activity_select), color = if (selectionMode) Color.Red else sharedPrimary()) }
        }
        SharedActivityUnderlineTabBar(listOf(stringResource(sharedYourTitle(category)), stringResource(R.string.shared_activity_from_user, otherUser.username)), viewModel.selectedTab, onSelect = { viewModel.selectedTab = it; selected = emptySet(); selectionMode = false })
        SharedActivityFilters(sortNewest, { sortNewest = it }, filter, { filter = it }, customDateFrom, customDateTo, { customDateFrom = it }, { customDateTo = it })
        when {
            viewModel.isLoading && reactions.isEmpty() && comments.isEmpty() -> SharedActivityLoading()
            viewModel.errorMessage != null && reactions.isEmpty() && comments.isEmpty() -> SharedActivityError(viewModel.errorMessage.orEmpty(), viewModel::reload)
            category == SharedActivityCategory.COMMENTS -> SharedCommentsList(comments, viewModel.selectedTab, otherUser.username, selectionMode, selected, viewModel.hasMore, viewModel.isLoading, { id -> selected = selected.toggle(id) }, { id -> selectionMode = true; selected = selected + id }, onOpenMoment, onOpenProfile, { viewModel.loadNextPage() })
            reactions.isEmpty() -> SharedActivityEmpty(category, viewModel.selectedTab, otherUser.username)
            else -> LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                items(reactions, key = ActivityReactionItem::id) { item -> SharedReactionTile(item, selectionMode, item.id in selected, { selected = selected.toggle(item.id) }, { selectionMode = true; selected = selected + item.id }, { item.moment?.takeIf { item.canView }?.let(onOpenMoment) }) }
                if (viewModel.hasMore) item { LaunchedEffect(Unit) { viewModel.loadNextPage() }; Box(Modifier.height(50.dp)) }
            }
        }
        if (selectionMode) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { TextButton({ if (selected.isNotEmpty()) confirmation = true }, enabled = selected.isNotEmpty() && !deleting) { if (deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Icons.Filled.Delete, null); Text(sharedRemoveLabel(category, selected.size)) } } }
        if (success) Text(stringResource(R.string.shared_activity_remove_success), color = Color(0xFF22C55E), fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
    }
    LaunchedEffect(success) { if (success) { kotlinx.coroutines.delay(2_000); success = false } }
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text(stringResource(sharedConfirmationTitle(category))) }, text = { Text(stringResource(sharedConfirmationMessage(category))) }, dismissButton = { TextButton({ confirmation = false }) { Text(stringResource(R.string.shared_activity_cancel)) } }, confirmButton = { TextButton({ confirmation = false; deleting = true; scope.launch { val result = when (category) { SharedActivityCategory.COMMENTS -> viewModel.removeComments(selected); SharedActivityCategory.TAGS -> viewModel.removeTags(selected); SharedActivityCategory.REACTIONS -> viewModel.removeReactions(selected) }; deleting = false; if (result.isSuccess) { selected = emptySet(); selectionMode = false; success = true } else { viewModel.errorMessage = result.exceptionOrNull()?.message ?: SharedActivityFailure.GENERIC } } }) { Text(sharedRemoveLabel(category, selected.size)) } })
}

@Composable fun SharedActivityUnderlineTabBar(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) = Column(modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth()) { titles.forEachIndexed { index, title -> Text(title, Modifier.weight(1f).clickable { onSelect(index) }.padding(12.dp), color = if (selected == index) sharedPrimary() else sharedSecondary(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) } }; Box(Modifier.fillMaxWidth().height(1.dp).background(sharedSecondary().copy(.18f))) { Box(Modifier.fillMaxWidth(1f / titles.size.coerceAtLeast(1)).height(1.dp).background(sharedPrimary()).align(Alignment.CenterStart).graphicsLayer { translationX = size.width * selected }) } }
@Composable
private fun SharedActivityFilters(
    newest: Boolean,
    setNewest: (Boolean) -> Unit,
    filter: ReactionsDateFilter,
    setFilter: (ReactionsDateFilter) -> Unit,
    customFrom: Date,
    customTo: Date,
    setCustomFrom: (Date) -> Unit,
    setCustomTo: (Date) -> Unit,
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SharedFilterMenu(R.string.shared_activity_filter_sort, if (newest) R.string.shared_activity_sort_newest else R.string.shared_activity_sort_oldest, listOf(true to R.string.shared_activity_sort_newest, false to R.string.shared_activity_sort_oldest), newest, setNewest)
        SharedFilterMenu(R.string.shared_activity_filter_date, sharedDateTitle(filter), ReactionsDateFilter.entries.map { it to sharedDateTitle(it) }, filter, setFilter)
    }
    if (filter == ReactionsDateFilter.CUSTOM) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SharedDateRangeButton(R.string.shared_activity_date_from, customFrom, Modifier.weight(1f)) { pickSharedDate(context, customFrom, setCustomFrom) }
            SharedDateRangeButton(R.string.shared_activity_date_to, customTo, Modifier.weight(1f)) { pickSharedDate(context, customTo, setCustomTo) }
        }
    }
}

@Composable
private fun <T> SharedFilterMenu(label: Int, selectedLabel: Int, choices: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(.05f)).clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(label), color = sharedSecondary(), fontSize = 11.sp)
            Text(stringResource(selectedLabel), color = sharedPrimary(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, title) -> androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = { expanded = false; onSelect(value) }, trailingIcon = { if (value == selected) Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp)) }) }
        }
    }
}

@Composable
private fun SharedDateRangeButton(label: Int, date: Date, modifier: Modifier, onPick: () -> Unit) = Row(modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(.05f)).clickable(onClick = onPick).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(stringResource(label), color = sharedSecondary(), fontSize = 11.sp); Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(date), color = sharedPrimary(), fontSize = 12.sp, maxLines = 1) }

private fun pickSharedDate(context: android.content.Context, initial: Date, onChosen: (Date) -> Unit) {
    val calendar = Calendar.getInstance().apply { time = initial }
    DatePickerDialog(context, { _, year, month, day -> onChosen(Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.time) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}
@Composable private fun SharedActivityLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
@Composable private fun SharedActivityError(message: String, retry: () -> Unit) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Filled.ErrorOutline, null, tint = Color.Red); Text(stringResource(R.string.shared_activity_error), color = sharedPrimary(), fontWeight = FontWeight.SemiBold); Text(sharedFailureText(message), color = sharedSecondary(), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)); TextButton(retry) { Text(stringResource(R.string.shared_activity_retry)) } }
@Composable private fun SharedActivityEmpty(category: SharedActivityCategory, tab: Int, username: String) = Column(Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.shared_activity_empty_title), color = sharedPrimary(), fontWeight = FontWeight.SemiBold); Text(stringResource(sharedEmptyText(category, tab), username), color = sharedSecondary(), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
@Composable private fun SharedReactionTile(item: ActivityReactionItem, selectionMode: Boolean, selected: Boolean, onToggle: () -> Unit, onEnterSelection: () -> Unit, onOpen: () -> Unit) = Box(Modifier.height(124.dp).combinedClickable(onClick = { if (selectionMode) onToggle() else onOpen() }, onLongClick = onEnterSelection)) { val url = item.moment?.previewImageURLString; if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize().background(Color.Black.copy(.12f))); if (item.moment?.isCarouselMoment == true && item.canView) Icon(Icons.Filled.Circle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp)); if (item.canView && item.reactionType.isNotBlank()) Text(sharedReactionIcon(item.reactionType), Modifier.align(Alignment.BottomEnd).padding(6.dp), fontSize = 16.sp); if (selectionMode) Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle, null, tint = if (selected) Color(0xFF2563EB) else Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) }
@Composable private fun SharedCommentsList(items: List<ActivityCommentItem>, selectedTab: Int, otherUsername: String, selection: Boolean, selected: Set<String>, hasMore: Boolean, loading: Boolean, onToggle: (String) -> Unit, onEnterSelection: (String) -> Unit, onMoment: (Moment) -> Unit, onProfile: (String) -> Unit, onEnd: () -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items, key = ActivityCommentItem::id) { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(.05f)).combinedClickable(onClick = { if (selection) onToggle(item.id) else item.moment?.takeIf { item.canView }?.let(onMoment) }, onLongClick = { if (!selection) onEnterSelection(item.id) }).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { SharedCommentPreview(item); Column(Modifier.weight(1f)) { Text(item.moment?.username?.takeIf(String::isNotBlank) ?: stringResource(R.string.shared_activity_unknown_user), color = sharedPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = !selection) { onProfile(item.authorId) }); Text(item.moment?.content?.takeIf { it.isNotBlank() } ?: stringResource(R.string.shared_activity_moment_no_content), color = sharedSecondary(), maxLines = 2, overflow = TextOverflow.Ellipsis); Text(stringResource(if (selectedTab == 0) R.string.shared_activity_comment_yours else R.string.shared_activity_comment_from_user, otherUsername), color = sharedSecondary(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(item.commentText.ifBlank { stringResource(R.string.shared_activity_empty_comment) }, color = sharedPrimary(), maxLines = 3, overflow = TextOverflow.Ellipsis); Text(item.commentedAt.timeAgoDisplay(), color = sharedSecondary(), fontSize = 11.sp) }; if (selection) Icon(if (item.id in selected) Icons.Filled.CheckCircle else Icons.Filled.Circle, null, tint = if (item.id in selected) Color(0xFF2563EB) else sharedSecondary()) else StoryRingAvatarView(item.authorId, 30.dp, lineWidth = 2.2.dp, onTap = { onProfile(item.authorId) }) } }; if (hasMore) item { if (loading) CircularProgressIndicator(Modifier.size(22.dp).padding(4.dp)) else LaunchedEffect(Unit) { onEnd() } } }
@Composable private fun SharedCommentPreview(item: ActivityCommentItem) = Box(Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(.10f))) { item.moment?.previewImageURLString?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; if (item.moment?.isCarouselMoment == true && item.canView) Icon(Icons.Filled.Circle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp)) }
private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
private fun sharedEmptyText(category: SharedActivityCategory, tab: Int): Int = when (category) {
    SharedActivityCategory.REACTIONS -> if (tab == 0) R.string.shared_activity_empty_your_reactions else R.string.shared_activity_empty_their_reactions
    SharedActivityCategory.COMMENTS -> if (tab == 0) R.string.shared_activity_empty_your_comments else R.string.shared_activity_empty_their_comments
    SharedActivityCategory.TAGS -> if (tab == 0) R.string.shared_activity_empty_your_tags else R.string.shared_activity_empty_their_tags
}
private fun sharedConfirmationTitle(category: SharedActivityCategory): Int = when (category) { SharedActivityCategory.REACTIONS -> R.string.shared_activity_confirm_reactions_title; SharedActivityCategory.COMMENTS -> R.string.shared_activity_confirm_comments_title; SharedActivityCategory.TAGS -> R.string.shared_activity_confirm_tags_title }
private fun sharedConfirmationMessage(category: SharedActivityCategory): Int = when (category) { SharedActivityCategory.REACTIONS -> R.string.shared_activity_confirm_reactions_message; SharedActivityCategory.COMMENTS -> R.string.shared_activity_confirm_comments_message; SharedActivityCategory.TAGS -> R.string.shared_activity_confirm_tags_message }
@Composable private fun sharedRemoveLabel(category: SharedActivityCategory, selectedCount: Int): String = when (category) { SharedActivityCategory.REACTIONS -> if (selectedCount == 1) stringResource(R.string.shared_activity_remove_reaction_single) else stringResource(R.string.shared_activity_remove_reactions_count, selectedCount); SharedActivityCategory.COMMENTS -> if (selectedCount == 1) stringResource(R.string.shared_activity_remove_comment_single) else stringResource(R.string.shared_activity_remove_comments_count, selectedCount); SharedActivityCategory.TAGS -> if (selectedCount == 1) stringResource(R.string.shared_activity_remove_tag_single) else stringResource(R.string.shared_activity_remove_tags_count, selectedCount) }
@Composable private fun sharedFailureText(message: String): String = stringResource(when (message) { SharedActivityFailure.NOT_AUTHENTICATED -> R.string.shared_activity_not_authenticated; SharedActivityFailure.GENERIC -> R.string.shared_activity_generic_failure; else -> R.string.shared_activity_generic_failure })
private fun matchesSharedDate(date: Date, filter: ReactionsDateFilter, customFrom: Date, customTo: Date): Boolean { val now = Date(); val day = 86_400_000L; return when (filter) { ReactionsDateFilter.ALL -> true; ReactionsDateFilter.WEEK -> date.time >= now.time - day * 7; ReactionsDateFilter.MONTH -> date.time >= now.time - day * 31; ReactionsDateFilter.YEAR -> date.time >= now.time - day * 366; ReactionsDateFilter.CUSTOM -> { val start = Calendar.getInstance().apply { time = minOf(customFrom, customTo); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time; val end = Calendar.getInstance().apply { time = maxOf(customFrom, customTo); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.time; date in start..end } } }
private fun oneMonthAgo(): Date = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time
private fun sharedYourTitle(category: SharedActivityCategory) = when (category) { SharedActivityCategory.REACTIONS -> R.string.shared_activity_your_reactions; SharedActivityCategory.COMMENTS -> R.string.shared_activity_your_comments; SharedActivityCategory.TAGS -> R.string.shared_activity_your_tags }
private fun sharedDateTitle(filter: ReactionsDateFilter) = when (filter) { ReactionsDateFilter.ALL -> R.string.shared_activity_date_all; ReactionsDateFilter.WEEK -> R.string.shared_activity_date_week; ReactionsDateFilter.MONTH -> R.string.shared_activity_date_month; ReactionsDateFilter.YEAR -> R.string.shared_activity_date_year; ReactionsDateFilter.CUSTOM -> R.string.shared_activity_date_custom }
private fun sharedReactionIcon(raw: String): String = when (raw.trim().lowercase()) { "heart", "like", "feel" -> "❤️"; "fire" -> "🔥"; "real" -> "✅"; "mood" -> "😊"; "glow" -> "✨"; "love" -> "💕"; "wow" -> "😮"; "laugh" -> "😂"; "cry" -> "😢"; "respect" -> "🙏🏻"; "power" -> "⚡"; "genius", "creative" -> "🧠"; "chill" -> "😎"; "hype" -> "🚀"; else -> "✨" }
@Composable internal fun sharedCanvas() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
@Composable internal fun sharedPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
@Composable internal fun sharedSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.55f) else Color.Black.copy(.55f)
