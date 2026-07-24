package com.moments.android.views.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.Echo
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchArchivedMoments
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMoments
import com.moments.android.services.firestore.fetchVisitsWithUsers
import com.moments.android.services.firestore.permanentlyDeleteRecentlyDeleted
import com.moments.android.services.firestore.restoreMoment
import com.moments.android.services.firestore.unarchiveMoment
import com.moments.android.services.network.CloudFunctionsClient
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.story.StoryRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

// MARK: - Activity Summary (contadores + previews de la pantalla principal)

data class ThumbInfo(
    /** URL del thumbnail (o del vídeo) usada como id. */
    val id: String,
    /** Thumbnail estático (imagen o frame de vídeo). */
    val url: String,
    /** Solo para momentos de vídeo sin thumbnail estático. */
    val videoUrl: String?,
    /** `audience != "everyone"` → vista protegida contra capturas. */
    val isProtected: Boolean,
    /** `false` → blur + candado. */
    val canView: Boolean,
)

data class ActivityCategorySummary(
    val count: Int,
    val thumbnails: List<ThumbInfo>,
)

/** Port de `ActivityInteractionDetailViewModel` (`UserActivityDetailViewModel.swift`). */
class ActivityInteractionDetailViewModel(
    private val category: ActivityInteractionCategory,
    private val recentlyDeletedKind: RecentlyDeletedContentKind = RecentlyDeletedContentKind.MOMENTS,
    private val firestoreService: FirestoreService = FirestoreService(),
) : ViewModel() {

    var isLoading by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var reactionItems by mutableStateOf<List<ActivityReactionItem>>(emptyList()); private set
    var commentItems by mutableStateOf<List<ActivityCommentItem>>(emptyList()); private set
    var events by mutableStateOf<List<ActivityEventItem>>(emptyList()); private set
    var deletedStoryItems by mutableStateOf<List<ActivityDeletedStoryItem>>(emptyList()); private set

    /** Moments y Reels (estilo ProfileView). */
    var moments by mutableStateOf<List<Moment>>(emptyList()); private set

    /** Para resolver audiencias custom. */
    var customListNamesById by mutableStateOf<Map<String, String>>(emptyMap()); private set

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private var didLoadOnce = false
    private var reactionsNextCursor: BackendReactionsCursor? = null
    private var commentsNextCursor: BackendCommentsCursor? = null

    private fun string(@StringRes res: Int): String =
        MomentsApplication.instance?.getString(res).orEmpty()

    private fun string(@StringRes res: Int, vararg args: Any): String =
        MomentsApplication.instance?.getString(res, *args).orEmpty()

    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    fun loadIfNeeded() {
        if (didLoadOnce) return
        didLoadOnce = true
        reload()
    }

    fun reload() {
        val userId = currentUserId
        if (userId == null) {
            errorMessage = string(R.string.user_activity_not_authenticated)
            return
        }

        isLoading = true
        errorMessage = null

        when (category) {
            ActivityInteractionCategory.REACTIONS -> loadReactions(userId)
            ActivityInteractionCategory.COMMENTS -> loadComments(userId)
            ActivityInteractionCategory.TAGS -> loadTags(userId)
            ActivityInteractionCategory.STICKER_REPLIES -> loadStickerReplies()
            ActivityInteractionCategory.ARCHIVED -> loadArchived(userId)
            ActivityInteractionCategory.STORIES_ARCHIVE -> {
                reactionItems = emptyList()
                commentItems = emptyList()
                events = emptyList()
                isLoading = false
            }
            ActivityInteractionCategory.RECENTLY_DELETED -> loadRecentlyDeleted(userId)
            ActivityInteractionCategory.MOMENTS -> fetchCustomAudienceListNames(userId) { loadMoments(userId) }
            ActivityInteractionCategory.REELS -> fetchCustomAudienceListNames(userId) { loadReels(userId) }
            ActivityInteractionCategory.ECHOES -> loadEchoes(userId)
            ActivityInteractionCategory.FOLLOWERS -> loadFollowers(userId)
            ActivityInteractionCategory.VISITS -> loadVisits(userId)
            ActivityInteractionCategory.TIME_SPENT,
            ActivityInteractionCategory.SEARCHES,
            ActivityInteractionCategory.ACCOUNT_HISTORY -> isLoading = false
        }
    }

    // MARK: - Mutaciones por selección

    suspend fun removeReactions(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val userId = currentUserId?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))

        val targets = reactionItems.filter { it.id in ids }
        if (targets.isEmpty()) return Result.success(Unit)

        return runCatching {
            val batch = db.batch()
            targets.forEach { item ->
                val ref = db.collection("users")
                    .document(item.authorId)
                    .collection("moments")
                    .document(item.momentId)
                    .collection("reactions")
                    .document(userId)
                batch.delete(ref)
            }
            batch.commit().await()
            reactionItems = reactionItems.filterNot { it.id in ids }
        }
    }

    suspend fun removeComments(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        if (currentUserId == null) {
            return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))
        }

        val targets = commentItems.filter { it.id in ids }
        if (targets.isEmpty()) return Result.success(Unit)

        return runCatching {
            deleteCommentsBatch(targets)
            commentItems = commentItems.filterNot { it.id in ids }
        }
    }

    suspend fun removeTags(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        if (currentUserId == null) {
            return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))
        }

        val targets = reactionItems.filter { it.id in ids }
        if (targets.isEmpty()) return Result.success(Unit)

        return runCatching {
            val moments = JSONArray(
                targets.map { JSONObject().put("authorId", it.authorId).put("momentId", it.momentId) },
            )
            CloudFunctionsClient.postVoid("removeMyTagsBatch", JSONObject().put("moments", moments))
            reactionItems = reactionItems.filterNot { it.id in ids }
        }
    }

    suspend fun removeStickerReplies(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        if (currentUserId.isNullOrEmpty()) {
            return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))
        }

        val targets = events.filter { it.id in ids }
        if (targets.isEmpty()) return Result.success(Unit)

        val payload = targets.mapNotNull { item ->
            val kind = item.kind?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val authorId = item.targetAuthorId?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val storyId = item.storyId?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            JSONObject().apply {
                put("kind", kind)
                put("authorId", authorId)
                put("storyId", storyId)
                item.sourceId?.takeIf { it.isNotEmpty() }?.let { put("sourceId", it) }
            }
        }
        if (payload.isEmpty()) return Result.success(Unit)

        return runCatching {
            CloudFunctionsClient.postVoid(
                "removeMyStickerRepliesBatch",
                JSONObject().put("replies", JSONArray(payload)),
            )
            events = events.filterNot { it.id in ids }
        }
    }

    suspend fun restoreSelection(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))

        return runCatching {
            if (category == ActivityInteractionCategory.RECENTLY_DELETED &&
                recentlyDeletedKind == RecentlyDeletedContentKind.STORIES
            ) {
                val storyRepository = StoryRepository(firestoreService)
                ids.forEach { storyRepository.restoreStory(userId, it) }
            } else {
                ids.forEach { firestoreService.restoreMoment(userId, it) }
            }
            reactionItems = reactionItems.filterNot { it.id in ids }
            deletedStoryItems = deletedStoryItems.filterNot { it.id in ids }
            ActivityCache.saveRecentlyDeletedCount(
                reactionItems.size + deletedStoryItems.size,
                userId,
            )
        }
    }

    suspend fun permanentlyDeleteSelection(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))

        return runCatching {
            firestoreService.permanentlyDeleteRecentlyDeleted(ids.toList())
            reactionItems = reactionItems.filterNot { it.id in ids }
            deletedStoryItems = deletedStoryItems.filterNot { it.id in ids }
            if (category == ActivityInteractionCategory.RECENTLY_DELETED) {
                ActivityCache.saveRecentlyDeletedCount(
                    reactionItems.size + deletedStoryItems.size,
                    userId,
                )
            }
        }
    }

    suspend fun unarchiveSelection(ids: Set<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException(string(R.string.user_activity_not_authenticated)))

        return runCatching {
            ids.forEach { id ->
                val ownerId = reactionItems.firstOrNull { it.id == id }?.authorId ?: userId
                firestoreService.unarchiveMoment(ownerId, id)
            }
            reactionItems = reactionItems.filterNot { it.id in ids }
        }
    }

    // MARK: - Cargadores por categoría

    private fun loadReactions(userId: String) {
        viewModelScope.launch {
            runCatching { fetchReactedMomentsPage(limit = 36, cursor = null) }
                .onSuccess { page ->
                    val sorted = page.first.sortedByDescending { it.reactedAt }
                    ActivityCache.saveReactions(sorted, userId)
                    reactionItems = sorted
                    reactionsNextCursor = page.second
                    commentItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
                .onFailure { error ->
                    val cached = ActivityCache.loadReactions(userId)
                        .filter { it.moment?.isArchived != true }
                    if (cached.isNotEmpty()) {
                        reactionItems = cached
                        commentItems = emptyList()
                        events = emptyList()
                    } else {
                        errorMessage = error.message
                    }
                    isLoading = false
                }
        }
    }

    private fun loadComments(userId: String) {
        viewModelScope.launch {
            runCatching { fetchCommentedMomentsPage(limit = 36, cursor = null) }
                .onSuccess { page ->
                    val sorted = page.first.sortedByDescending { it.commentedAt }
                    ActivityCache.saveComments(sorted, userId)
                    commentItems = sorted
                    commentsNextCursor = page.second
                    reactionItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
                .onFailure { error ->
                    val cached = ActivityCache.loadComments(userId)
                        .filter { it.moment?.isArchived != true }
                    if (cached.isNotEmpty()) {
                        commentItems = cached
                        reactionItems = emptyList()
                        events = emptyList()
                    } else {
                        errorMessage = error.message
                    }
                    isLoading = false
                }
        }
    }

    private fun loadTags(userId: String) {
        viewModelScope.launch {
            runCatching { fetchTaggedMomentsPage(limit = 60, cursor = null) }
                .onSuccess { page ->
                    val mapped = page.first.mapNotNull { item ->
                        val moment = item.moment ?: return@mapNotNull null
                        if (moment.isArchived == true) return@mapNotNull null
                        val timestamp = item.taggedAt?.let { Date(it.toLong()) } ?: moment.timestamp
                        val authorId = item.authorId ?: moment.authorId
                        val momentId = item.momentId ?: moment.id
                        if (authorId.isEmpty() || momentId.isNullOrEmpty()) return@mapNotNull null
                        ActivityReactionItem(
                            id = "${authorId}_$momentId",
                            authorId = authorId,
                            momentId = momentId,
                            reactionType = "tagged",
                            reactedAt = timestamp,
                            moment = moment,
                            canView = item.canView ?: false,
                        )
                    }
                    val sorted = mapped.sortedByDescending { it.reactedAt }
                    currentUserId?.let { ActivityCache.saveTagged(sorted, it) }
                    reactionItems = sorted
                    commentItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
                .onFailure { error ->
                    val cached = ActivityCache.loadTagged(userId)
                    if (cached.isNotEmpty()) {
                        reactionItems = cached
                    } else {
                        reactionItems = emptyList()
                        errorMessage = error.message
                    }
                    commentItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
        }
    }

    private fun loadRecentlyDeleted(userId: String) {
        viewModelScope.launch {
            runCatching {
                db.collection("users").document(userId).collection("recentlyDeleted")
                    .orderBy("deletedAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()
            }.onSuccess { snapshot ->
                val momentItems = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val type = (data["type"] as? String)?.lowercase() ?: "moment"
                    if (type == "story") return@mapNotNull null
                    val timestamp = (data["deletedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                    val moment = Moment.from(doc.id, data)
                    ActivityReactionItem(
                        id = doc.id,
                        authorId = userId,
                        momentId = doc.id,
                        reactionType = "moment",
                        reactedAt = timestamp,
                        moment = moment,
                        canView = true,
                    )
                }

                val storyItems = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val type = (data["type"] as? String)?.lowercase().orEmpty()
                    if (type != "story") return@mapNotNull null
                    val timestamp = (data["deletedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                    val story: Story = Story.from(doc.id, data) ?: return@mapNotNull null
                    ActivityDeletedStoryItem(id = doc.id, story = story, deletedAt = timestamp)
                }

                reactionItems = if (recentlyDeletedKind == RecentlyDeletedContentKind.MOMENTS) momentItems else emptyList()
                deletedStoryItems = if (recentlyDeletedKind == RecentlyDeletedContentKind.STORIES) storyItems else emptyList()
                commentItems = emptyList()
                events = emptyList()
                isLoading = false
            }.onFailure { error ->
                isLoading = false
                errorMessage = error.message
            }
        }
    }

    private fun loadArchived(userId: String) {
        viewModelScope.launch {
            runCatching { firestoreService.fetchArchivedMoments(userId) }
                .onSuccess { archived ->
                    reactionItems = archived.mapNotNull { moment ->
                        val id = moment.id ?: return@mapNotNull null
                        ActivityReactionItem(
                            id = id,
                            authorId = moment.authorId,
                            momentId = id,
                            reactionType = "archived",
                            reactedAt = moment.archivedAt ?: moment.timestamp,
                            moment = moment,
                            canView = true,
                        )
                    }
                    commentItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
                .onFailure { error ->
                    isLoading = false
                    errorMessage = if (moments.isEmpty()) error.message else null
                }
        }
    }

    private fun fetchCustomAudienceListNames(userId: String, completion: (() -> Unit)? = null) {
        viewModelScope.launch {
            runCatching { firestoreService.fetchCustomLists(userId) }
                .onSuccess { lists ->
                    customListNamesById = lists.mapNotNull { list ->
                        list.id?.let { it to list.name }
                    }.toMap()
                }
            completion?.invoke()
        }
    }

    private fun loadMoments(userId: String) = loadProfileMoments(userId, wantReels = false)

    private fun loadReels(userId: String) = loadProfileMoments(userId, wantReels = true)

    /**
     * `loadMoments`/`loadReels` de iOS: mismo cuerpo salvo el signo del filtro `isReelCandidate`.
     * 1) caché local para pintar ya, 2) Firestore como fuente de verdad.
     */
    private fun loadProfileMoments(userId: String, wantReels: Boolean) {
        val viewerId = currentUserId

        viewModelScope.launch {
            val cached = LocalPersistenceService.loadProfileMoments(userId, viewerId)
                .filter { moment ->
                    val isArchived = moment.isArchived ?: false
                    !isArchived && moment.isReelCandidate == wantReels
                }
            if (cached.isNotEmpty()) {
                moments = cached
                isLoading = false
            }

            runCatching { firestoreService.fetchMoments(userId) }
                .onSuccess { fetched ->
                    LocalPersistenceService.saveProfileMoments(fetched, userId, viewerId, sync = true)
                    moments = fetched.filter { moment ->
                        val isArchived = moment.isArchived ?: false
                        !isArchived && moment.isReelCandidate == wantReels
                    }
                    reactionItems = emptyList()
                    commentItems = emptyList()
                    events = emptyList()
                    isLoading = false
                }
                .onFailure { error ->
                    isLoading = false
                    // Paridad iOS: en Moments se conserva lo cacheado; en Reels el error siempre se muestra.
                    errorMessage = if (wantReels || moments.isEmpty()) error.message else null
                }
        }
    }

    private fun loadEchoes(userId: String) {
        com.moments.android.services.social.EchoService.fetchEchoHistory(userId) { echoes ->
            events = echoes.mapNotNull { echo: Echo ->
                val id = echo.id ?: return@mapNotNull null
                val locationName = echo.locationName
                    ?: string(R.string.user_activity_echo_unknown_location)
                val thumbnailUrl = echo.moments.lastOrNull()?.thumbnailUrl
                    ?: echo.moments.lastOrNull()?.mediaUrl

                ActivityEventItem(
                    id = id,
                    title = locationName,
                    subtitle = "",
                    timestamp = echo.createdAt,
                    icon = "waveform.and.mic",
                    kind = "echo",
                    sourceId = id,
                    thumbnailUrl = thumbnailUrl,
                    echoStatusRaw = echo.status.raw,
                    echoParticipantsCount = echo.participants.size,
                    echoExpiresAt = echo.expiresAt,
                )
            }.sortedByDescending { it.timestamp }
            isLoading = false
        }
    }

    private fun loadFollowers(userId: String) {
        viewModelScope.launch {
            runCatching { firestoreService.fetchFollowersWithTimestamps(userId) }
                .onSuccess { items ->
                    events = items.map { (user, timestamp) ->
                        val dateString = MomentsFormat.smartDate(
                            from = timestamp,
                            context = MomentsFormat.DateContext.MEDIUM_DATE_TIME,
                        )
                        ActivityEventItem(
                            id = user.id,
                            title = user.username,
                            subtitle = string(R.string.user_activity_event_follow_subtitle, dateString),
                            timestamp = timestamp,
                            icon = "person.badge.plus",
                            actorId = user.id,
                            actorUsername = user.username,
                            actorProfileImagePath = user.profileImagePath,
                            actionText = string(R.string.user_activity_event_action_view_profile),
                            kind = "follower",
                        )
                    }
                    isLoading = false
                }
                .onFailure { isLoading = false }
        }
    }

    private fun loadVisits(userId: String) {
        viewModelScope.launch {
            runCatching { firestoreService.fetchVisitsWithUsers(userId) }
                .onSuccess { items ->
                    // Deduplicar por visitante, quedándose con la visita más reciente.
                    val latestVisits = mutableMapOf<String, ActivityEventItem>()
                    items.forEach { (user, visit) ->
                        val dateString = MomentsFormat.smartDate(
                            from = visit.timestamp,
                            context = MomentsFormat.DateContext.MEDIUM_DATE_TIME,
                        )
                        val event = ActivityEventItem(
                            id = visit.id ?: java.util.UUID.randomUUID().toString(),
                            title = user.username,
                            subtitle = string(R.string.user_activity_event_visit_subtitle, dateString),
                            timestamp = visit.timestamp,
                            icon = "eye",
                            actorId = user.id,
                            actorUsername = user.username,
                            actorProfileImagePath = user.profileImagePath,
                            actionText = string(R.string.user_activity_event_action_view_profile),
                            kind = "visit",
                        )
                        val existing = latestVisits[user.id]
                        if (existing == null || event.timestamp.after(existing.timestamp)) {
                            latestVisits[user.id] = event
                        }
                    }
                    events = latestVisits.values.sortedByDescending { it.timestamp }
                    isLoading = false
                }
                .onFailure { isLoading = false }
        }
    }

    private fun loadStickerReplies() {
        viewModelScope.launch {
            runCatching { fetchStickerRepliesPage(limit = 80, cursor = null) }
                .onSuccess { page ->
                    val mapped = page.first.mapNotNull { item -> item.toEventItem() }
                    val sorted = mapped.sortedByDescending { it.timestamp }
                    currentUserId?.let { ActivityCache.saveStickerReplyCount(sorted.size, it) }
                    events = sorted
                    reactionItems = emptyList()
                    commentItems = emptyList()
                    isLoading = false
                }
                .onFailure {
                    currentUserId?.let { uid -> ActivityCache.saveStickerReplyCount(0, uid) }
                    events = emptyList()
                    reactionItems = emptyList()
                    commentItems = emptyList()
                    isLoading = false
                    errorMessage = string(R.string.user_activity_empty_stickers)
                }
        }
    }

    private fun BackendStickerReplyItem.toEventItem(): ActivityEventItem? {
        val time = timestamp?.let { Date(it.toLong()) } ?: Date()
        val actorName = actorUsername?.trim().orEmpty()
        val displayName = actorName.ifEmpty { string(R.string.user_activity_stickers_actor_fallback) }
        val targetName = targetUsername?.takeIf { it.isNotEmpty() }
            ?: string(R.string.user_activity_status_unknown)

        return when (kind.lowercase()) {
            "poll" -> {
                val optionText = pollOptionText?.trim().orEmpty()
                val optionFallback = pollOption
                    ?.let { string(R.string.user_activity_stickers_poll_option_fallback, it + 1) }
                    .orEmpty()
                val resolvedOptionText = optionText.ifEmpty { optionFallback }
                val subtitle = if (resolvedOptionText.isEmpty()) {
                    string(R.string.user_activity_stickers_poll_subtitle_fallback)
                } else {
                    string(R.string.user_activity_stickers_poll_subtitle, resolvedOptionText)
                }
                ActivityEventItem(
                    id = "event_poll_$id",
                    title = displayName,
                    subtitle = subtitle,
                    timestamp = time,
                    icon = "checkmark.circle.fill",
                    actorId = actorId,
                    actorUsername = actorUsername,
                    actorProfileImagePath = actorProfileImagePath,
                    actionText = string(R.string.user_activity_stickers_poll_action),
                    kind = "poll",
                    targetAuthorId = authorId,
                    targetUsername = targetUsername,
                    storyId = storyId,
                    sourceId = sourceId,
                    contextText = string(R.string.user_activity_stickers_poll_context, targetName),
                )
            }

            "question" -> {
                val question = questionText?.trim().orEmpty()
                val response = responseText?.trim().orEmpty()
                val subtitle = when {
                    response.isNotEmpty() -> response
                    question.isNotEmpty() -> question
                    else -> string(R.string.user_activity_stickers_question_subtitle_fallback)
                }
                ActivityEventItem(
                    id = "event_question_$id",
                    title = displayName,
                    subtitle = subtitle,
                    timestamp = time,
                    icon = "questionmark.bubble.fill",
                    actorId = actorId,
                    actorUsername = actorUsername,
                    actorProfileImagePath = actorProfileImagePath,
                    actionText = string(R.string.user_activity_stickers_question_action),
                    kind = "question",
                    targetAuthorId = authorId,
                    targetUsername = targetUsername,
                    storyId = storyId,
                    sourceId = sourceId,
                    contextText = string(R.string.user_activity_stickers_question_context, targetName),
                )
            }

            else -> null
        }
    }

    // MARK: - Cloud Functions

    private suspend fun fetchReactedMomentsPage(
        limit: Int,
        cursor: BackendReactionsCursor?,
    ): Pair<List<ActivityReactionItem>, BackendReactionsCursor?> {
        val payload = JSONObject().put("limit", limit)
        cursor?.let { payload.put("cursor", JSONObject().put("timestamp", it.timestamp)) }

        val decoded = BackendReactionsResponse.from(
            CloudFunctionsClient.postJson("getReactedMomentsPage", payload, timeoutMs = 15_000),
        )

        val mapped = decoded.items.mapNotNull { item ->
            val moment = item.moment ?: return@mapNotNull null
            if (moment.isArchived == true) return@mapNotNull null
            val resolvedAuthorId = item.authorId ?: moment.authorId
            val resolvedMomentId = (item.momentId ?: moment.id)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val reactedAt = item.reactedAt?.let { Date(it.toLong()) } ?: moment.timestamp

            ActivityReactionItem(
                id = "${resolvedAuthorId}_$resolvedMomentId",
                authorId = resolvedAuthorId,
                momentId = resolvedMomentId,
                reactionType = item.reactionType,
                reactedAt = reactedAt,
                moment = moment,
                canView = item.canView ?: true,
            )
        }

        return mapped to decoded.nextCursor
    }

    private suspend fun fetchCommentedMomentsPage(
        limit: Int,
        cursor: BackendCommentsCursor?,
    ): Pair<List<ActivityCommentItem>, BackendCommentsCursor?> {
        val payload = JSONObject().put("limit", limit)
        cursor?.let { payload.put("cursor", JSONObject().put("timestamp", it.timestamp)) }

        val decoded = BackendCommentsResponse.from(
            CloudFunctionsClient.postJson("getCommentedMomentsPage", payload, timeoutMs = 15_000),
        )

        val mapped = decoded.items.mapNotNull { item ->
            val moment = item.moment ?: return@mapNotNull null
            if (moment.isArchived == true) return@mapNotNull null
            val resolvedAuthorId = item.authorId ?: moment.authorId
            val resolvedMomentId = (item.momentId ?: moment.id)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val resolvedCommentId = (item.commentId ?: item.comment?.id)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val commentText = item.comment?.content?.trim().orEmpty()
            val commentedAt = item.commentedAt?.let { Date(it.toLong()) }
                ?: item.comment?.timestamp?.let { Date(it.toLong()) }
                ?: moment.timestamp

            ActivityCommentItem(
                id = "${resolvedAuthorId}_${resolvedMomentId}_$resolvedCommentId",
                authorId = resolvedAuthorId,
                momentId = resolvedMomentId,
                commentId = resolvedCommentId,
                commentText = commentText,
                commentedAt = commentedAt,
                moment = moment,
                canView = item.canView ?: true,
            )
        }

        return mapped to decoded.nextCursor
    }

    private suspend fun fetchTaggedMomentsPage(
        limit: Int,
        cursor: com.moments.android.services.content.BackendTagsCursor?,
    ): Pair<List<BackendTaggedItem>, com.moments.android.services.content.BackendTagsCursor?> {
        val payload = JSONObject().put("limit", limit)
        cursor?.let { payload.put("cursor", JSONObject().put("timestamp", it.timestamp)) }

        val decoded = BackendTagsResponse.from(
            CloudFunctionsClient.postJson("getTaggedMomentsPage", payload, timeoutMs = 15_000),
        )
        return decoded.items to decoded.nextCursor
    }

    private suspend fun fetchStickerRepliesPage(
        limit: Int,
        cursor: BackendStickerRepliesCursor?,
    ): Pair<List<BackendStickerReplyItem>, BackendStickerRepliesCursor?> {
        val payload = JSONObject().put("limit", limit)
        cursor?.let { payload.put("cursor", JSONObject().put("timestamp", it.timestamp)) }

        val decoded = BackendStickerRepliesResponse.from(
            CloudFunctionsClient.postJson("getStickerRepliesPage", payload, timeoutMs = 15_000),
        )
        return decoded.items to decoded.nextCursor
    }

    private suspend fun deleteCommentsBatch(items: List<ActivityCommentItem>) {
        val comments = JSONArray(
            items.map {
                DeleteCommentsTarget(
                    authorId = it.authorId,
                    momentId = it.momentId,
                    commentId = it.commentId,
                ).toJson()
            },
        )
        // iOS ignora el cuerpo de la respuesta (`_ = try? JSONDecoder()...`).
        CloudFunctionsClient.postVoid("deleteMyCommentsBatch", JSONObject().put("comments", comments))
    }

    @Suppress("unused") // Paridad con iOS: aún sin consumidor, igual que allí.
    private suspend fun fetchNotifications(userId: String): List<NotificationRecord> = runCatching {
        db.collection("users")
            .document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(300)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val type = data["type"] as? String ?: return@mapNotNull null
                val timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                    ?: return@mapNotNull null
                NotificationRecord(
                    id = doc.id,
                    type = type,
                    senderUsername = data["senderUsername"] as? String,
                    reaction = data["reaction"] as? String,
                    timestamp = timestamp,
                )
            }
    }.getOrDefault(emptyList())
}
