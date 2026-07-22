package com.moments.android.services.content

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.HighlightedStory
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

// MARK: - Feed UI types (FeedView)

data class FeedMediaItem(
    val id: String,
    val type: String,
    val url: String,
    val thumbnailUrl: String?,
    val aspectRatio: String?,
    val isHiddenByModeration: Boolean = false,
    val tags: List<com.moments.android.models.PhotoTag>? = null,
    val videoDuration: Double? = null,
)

data class FeedMoment(
    val id: String,
    val authorId: String,
    val username: String,
    val content: String,
    val timestamp: Long,
    val profileImagePath: String?,
    val location: String?,
    val mediaItems: List<FeedMediaItem>,
    val aspectRatio: String?,
    val commentCount: Int,
    val reactionCount: Int,
    val hideLikeCounts: Boolean,
    val disableComments: Boolean,
    val hasHiddenLayers: Boolean = false,
    val hiddenLayerCount: Int = 0,
    /** Paridad iOS `Moment.audience` — != "everyone" → ScreenshotProtected. */
    val audience: String? = null,
    /** Paridad iOS `Moment.customListId` — audience customList. */
    val customListId: String? = null,
    /** Paridad iOS `Moment.isArchived` — filtrado en fetch/loadMore. */
    val isArchived: Boolean? = null,
    /** Paridad iOS `Moment.locationCoordinate` → mapa. */
    val locationCoordinate: Moment.LocationCoordinate? = null,
) {
    /** Paridad iOS `Moment.visibleMediaItems`. */
    val visibleMediaItems: List<FeedMediaItem>
        get() = mediaItems.filter { !it.isHiddenByModeration && it.url.isNotBlank() }
}

data class StoryUser(val id: String, val username: String)

// MARK: - Backend response models (Cloud Functions contract)

data class FeedCursor(
    val timestamp: Double,
    val momentId: String,
    val authorId: String? = null,
    val globalStreamTimestamp: Double? = null,
    val globalStreamMomentId: String? = null,
    val globalStreamAuthorId: String? = null,
)

data class BackendTagsCursor(val timestamp: Double)

data class StoryRingCursor(val offset: Int)

data class BackendFeedPageResult(
    val moments: List<FeedMoment>,
    val nextCursor: FeedCursor?,
    val source: String,
    val totalVisibleCount: Int? = null,
)

data class BackendTaggedPageResult(
    val moments: List<Moment>,
    val nextCursor: BackendTagsCursor?,
    val source: String,
)

data class BackendProfilePageResult(
    val moments: List<Moment>,
    val nextCursor: FeedCursor?,
    val source: String,
    val totalVisibleCount: Int?,
)

data class BackendHighlightsResult(
    val highlights: List<HighlightedStory>,
    val source: String,
)

data class BackendStoryTrayItem(
    val userId: String,
    val storyCount: Int,
    val hasUnseenStory: Boolean,
    val segments: List<BackendStoryTraySegment>,
    val latestStoryAt: Double?,
)

data class BackendStoryTraySegment(
    val storyId: String,
    val viewed: Boolean,
    val audience: String?,
    val timestamp: Double?,
)

data class BackendStoryTrayResponse(
    val items: List<BackendStoryTrayItem>,
    val nextCursor: StoryRingCursor?,
    val source: String,
    val totalCandidates: Int,
)

data class BackendStoryMediaItem(val type: String, val url: String)

data class BackendStoryDocument(
    val id: String,
    val authorId: String,
    val duration: Double?,
    val expirationHours: Int?,
    val expirationDate: Double?,
    val timestamp: Double?,
    val username: String?,
    val profileImagePath: String?,
    val audience: String?,
    val customListId: String?,
    val text: String?,
    val textStyle: String?,
    val textPositionX: Double?,
    val textPositionY: Double?,
    val textPositionNormX: Double?,
    val textPositionNormY: Double?,
    val textColorHex: String?,
    val textFontSize: Double?,
    val textAlignment: String?,
    val textBackgroundFill: String?,
    val textStroke: String?,
    val textVisualEffect: String?,
    val textMotion: String?,
    val forcesAllCaps: Boolean?,
    val textLayerOrder: Int?,
    val textOverlayLive: Boolean?,
    val textOverlays: List<StoryTextOverlayMetadata>?,
    val drawingData: String?,
    val stickers: List<StickerData>?,
    val aspectRatio: String?,
    val backgroundFrameURL: String?,
    val backgroundBlurredFrameURL: String?,
    val chainId: String?,
    val chainPosition: Int?,
    val chainTitle: String?,
    val mediaItem: BackendStoryMediaItem?,
    val imagePath: String?,
    val videoUrl: String?,
)

data class BackendAuthorStoryBundleResponse(
    val authorId: String,
    val stories: List<BackendStoryDocument>,
    val segments: List<BackendStoryTraySegment>,
    val source: String,
)

// MARK: - BackendFeedService

object BackendFeedService {
    private const val REGION = "europe-southwest1"
    private const val MAX_FAILS = 3
    private const val COOLDOWN_MS = 300_000L

    private val failCount = AtomicInteger(0)
    private val lastFailTime = AtomicLong(0L)
    private val legacyScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val isCircuitOpen: Boolean
        get() {
            if (failCount.get() < MAX_FAILS) return false
            if (System.currentTimeMillis() - lastFailTime.get() > COOLDOWN_MS) {
                failCount.set(0)
                return false
            }
            return true
        }

    /** Legacy callback API — FeedView. */
    fun fetch(feedType: String, onResult: (Result<List<FeedMoment>>) -> Unit) {
        legacyScope.launch {
            val result = runCatching {
                fetchFeedPage(feedType)?.moments ?: emptyList()
            }
            onResult(result)
        }
    }

    /** Legacy callback API — FeedView story ring. */
    fun fetchStoryUsers(onResult: (Result<List<StoryUser>>) -> Unit) {
        legacyScope.launch {
            onResult(runCatching { fetchStoryUsersSuspend() })
        }
    }

    suspend fun fetchFeedPage(
        feedType: String,
        cursor: FeedCursor? = null,
        limit: Int = 20,
    ): BackendFeedPageResult? {
        if (isCircuitOpen) return null
        val body = buildMap {
            put("feedType", feedType)
            put("limit", limit)
            cursor?.let { put("cursor", it.toJson()) }
        }
        return postFunction("getFeedPage", body, timeoutMs = 15_000)?.let { json ->
            recordSuccess()
            BackendFeedPageResult(
                moments = json.optJSONArray("moments").toFeedMoments(),
                nextCursor = json.optJSONObject("nextCursor")?.toFeedCursor(),
                source = json.optString("source", "unknown"),
                totalVisibleCount = json.optIntOrNull("totalVisibleCount"),
            )
        }
    }

    suspend fun fetchTaggedMoments(
        targetUserId: String? = null,
        cursor: BackendTagsCursor? = null,
        limit: Int = 50,
    ): BackendTaggedPageResult? {
        if (isCircuitOpen) return null
        val body = buildMap<String, Any> {
            put("limit", limit)
            cursor?.let { put("cursor", mapOf("timestamp" to it.timestamp)) }
            targetUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("targetUserId", it) }
        }
        return postFunction("getTaggedMomentsPage", body)?.let { json ->
            recordSuccess()
            val items = json.optJSONArray("items")
            val moments = (0 until (items?.length() ?: 0)).mapNotNull { i ->
                val item = items?.optJSONObject(i) ?: return@mapNotNull null
                if (item.optBoolean("canView", false)) {
                    item.optJSONObject("moment")?.toMoment()
                } else null
            }
            BackendTaggedPageResult(
                moments = moments,
                nextCursor = json.optJSONObject("nextCursor")?.let {
                    BackendTagsCursor(it.getDouble("timestamp"))
                },
                source = json.optString("source", "unknown"),
            )
        }
    }

    suspend fun fetchProfileMoments(
        targetUserId: String? = null,
        cursor: FeedCursor? = null,
        limit: Int = 50,
        includeTotalCount: Boolean = false,
    ): BackendProfilePageResult? {
        if (isCircuitOpen) return null
        val body = buildMap<String, Any> {
            put("limit", limit)
            cursor?.let { put("cursor", it.toProfileCursorJson()) }
            targetUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("targetUserId", it) }
            if (includeTotalCount) put("includeTotalCount", true)
        }
        return postFunction("getProfileMomentsPage", body)?.let { json ->
            recordSuccess()
            BackendProfilePageResult(
                moments = json.optJSONArray("moments").toMoments(),
                nextCursor = json.optJSONObject("nextCursor")?.toFeedCursor(),
                source = json.optString("source", "unknown"),
                totalVisibleCount = json.optIntOrNull("totalVisibleCount"),
            )
        }
    }

    suspend fun fetchVisibleHighlights(
        targetUserId: String? = null,
        limit: Int = 30,
    ): BackendHighlightsResult? {
        if (isCircuitOpen) return null
        val body = buildMap<String, Any> {
            put("limit", limit)
            targetUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("targetUserId", it) }
        }
        return postFunction("getVisibleHighlightsPage", body)?.let { json ->
            recordSuccess()
            val highlights = json.optJSONArray("highlights").toHighlightedStories()
            BackendHighlightsResult(highlights, json.optString("source", "unknown"))
        }
    }

    suspend fun fetchStoryUsersSuspend(limit: Int = 16): List<StoryUser> {
        val tray = StoryTrayService.fetchStoryRingPage(limit = limit, cursor = null) ?: return emptyList()
        val ids = tray.items.map { it.userId }.distinct()
        if (ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            ids.map { id ->
                val username = runCatching {
                    FirebaseFirestore.getInstance().collection("users").document(id).get().await()
                        .getString("username")
                }.getOrNull()
                StoryUser(id, username?.takeIf { it.isNotBlank() } ?: "moments")
            }
        }
    }

    private suspend fun postFunction(
        functionName: String,
        body: Map<String, Any>,
        timeoutMs: Int = 15_000,
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (isCircuitOpen) return@withContext null
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        try {
            val token = user.getIdToken(false).await().token ?: return@withContext null
            val projectId = FirebaseApp.getInstance().options.projectId ?: return@withContext null
            val url = URL("https://$REGION-$projectId.cloudfunctions.net/$functionName")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
            }
            try {
                connection.outputStream.use {
                    it.write(JSONObject(body).toString().toByteArray())
                }
                if (connection.responseCode != 200) {
                    recordFailure()
                    return@withContext null
                }
                JSONObject(connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            recordFailure()
            null
        }
    }

    private fun recordSuccess() {
        failCount.set(0)
    }

    private fun recordFailure() {
        failCount.incrementAndGet()
        lastFailTime.set(System.currentTimeMillis())
    }
}

// MARK: - StoryTrayService

object StoryTrayService {
    private const val REGION = "europe-southwest1"
    private const val MAX_FAILS = 3
    private const val COOLDOWN_MS = 180_000L
    private const val CACHE_TTL_MS = 20_000L

    private val failCount = AtomicInteger(0)
    private val lastFailTime = AtomicLong(0L)
    private val cacheByViewerId = mutableMapOf<String, Pair<BackendStoryTrayResponse, Long>>()

    val isCircuitOpen: Boolean
        get() {
            if (failCount.get() < MAX_FAILS) return false
            if (System.currentTimeMillis() - lastFailTime.get() > COOLDOWN_MS) {
                failCount.set(0)
                return false
            }
            return true
        }

    fun cachedTray(viewerId: String): BackendStoryTrayResponse? {
        val entry = cacheByViewerId[viewerId] ?: return null
        if (entry.second < System.currentTimeMillis()) {
            cacheByViewerId.remove(viewerId)
            return null
        }
        return entry.first
    }

    fun invalidate(viewerId: String? = null) {
        if (viewerId == null) cacheByViewerId.clear()
        else cacheByViewerId.remove(viewerId)
    }

    suspend fun fetchStoryTray(limit: Int = 80): BackendStoryTrayResponse? =
        fetchStoryRingPage(limit = limit, cursor = null)

    suspend fun fetchStoryRingPage(
        limit: Int = 16,
        cursor: StoryRingCursor?,
    ): BackendStoryTrayResponse? {
        val body = buildMap<String, Any> {
            put("limit", limit)
            cursor?.let { put("cursor", mapOf("offset" to it.offset)) }
        }
        return postStoryEndpoint("getStoryRingPage", body, cacheOnSuccess = cursor == null)
    }

    suspend fun fetchAuthorStoryBundle(authorId: String): BackendAuthorStoryBundleResponse? {
        if (authorId.isBlank()) return null
        return postStoryEndpoint("getAuthorStoryBundle", mapOf("authorId" to authorId), cacheOnSuccess = false)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> postStoryEndpoint(
        functionName: String,
        body: Map<String, Any>,
        cacheOnSuccess: Boolean,
    ): T? = withContext(Dispatchers.IO) {
        if (isCircuitOpen) return@withContext null
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        try {
            val token = user.getIdToken(false).await().token ?: return@withContext null
            val projectId = FirebaseApp.getInstance().options.projectId ?: return@withContext null
            val url = URL("https://$REGION-$projectId.cloudfunctions.net/$functionName")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
            }
            try {
                connection.outputStream.use {
                    it.write(JSONObject(body).toString().toByteArray())
                }
                if (connection.responseCode != 200) {
                    recordFailure()
                    return@withContext null
                }
                val json = JSONObject(connection.inputStream.bufferedReader().readText())
                recordSuccess()
                when (functionName) {
                    "getStoryRingPage" -> {
                        val tray = json.toStoryTrayResponse()
                        if (cacheOnSuccess) {
                            cacheByViewerId[user.uid] = tray to (System.currentTimeMillis() + CACHE_TTL_MS)
                        }
                        tray as T
                    }
                    "getAuthorStoryBundle" -> json.toAuthorStoryBundle() as T
                    else -> null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            recordFailure()
            null
        }
    }

    private fun recordSuccess() {
        failCount.set(0)
    }

    private fun recordFailure() {
        failCount.incrementAndGet()
        lastFailTime.set(System.currentTimeMillis())
    }
}

// MARK: - JSON parsing

private fun FeedCursor.toJson(): Map<String, Any> = buildMap {
    put("timestamp", timestamp)
    put("momentId", momentId)
    authorId?.takeIf { it.isNotEmpty() }?.let { put("authorId", it) }
    val gsMoment = globalStreamMomentId
    val gsAuthor = globalStreamAuthorId
    if (globalStreamTimestamp != null && gsMoment != null && gsAuthor != null &&
        gsMoment.isNotEmpty() && gsAuthor.isNotEmpty()
    ) {
        put("globalStreamTimestamp", globalStreamTimestamp)
        put("globalStreamMomentId", gsMoment)
        put("globalStreamAuthorId", gsAuthor)
    }
}

private fun FeedCursor.toProfileCursorJson(): Map<String, Any?> = mapOf(
    "timestamp" to timestamp,
    "momentId" to momentId,
    "authorId" to authorId,
)

private fun JSONObject.toFeedCursor(): FeedCursor = FeedCursor(
    timestamp = getDouble("timestamp"),
    momentId = getString("momentId"),
    authorId = stringOrNull("authorId"),
    globalStreamTimestamp = optDoubleOrNull("globalStreamTimestamp"),
    globalStreamMomentId = stringOrNull("globalStreamMomentId"),
    globalStreamAuthorId = stringOrNull("globalStreamAuthorId"),
)

private fun JSONArray?.toFeedMoments(): List<FeedMoment> =
    (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it)?.toFeedMoment() }

private fun JSONArray?.toMoments(): List<Moment> =
    (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it)?.toMoment() }

private fun JSONArray?.toHighlightedStories(): List<HighlightedStory> =
    (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it)?.toHighlightedStory() }

private fun JSONObject.toFeedMoment(): FeedMoment {
    val structuredMedia = optJSONArray("mediaItems")?.let { items ->
        (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { item ->
                item.optString("url").takeIf { it.isNotBlank() }?.let { url ->
                    FeedMediaItem(
                        id = item.optString("id", "$index-$url"),
                        type = item.optString("type", "image"),
                        url = url,
                        thumbnailUrl = item.stringOrNull("thumbnailUrl"),
                        aspectRatio = item.stringOrNull("aspectRatio"),
                        isHiddenByModeration = item.optString("moderationState") == "hidden",
                        tags = item.optJSONArray("tags")?.toPhotoTags(),
                        videoDuration = item.optDoubleOrNull("videoDuration"),
                    )
                }
            }
        }
    }.orEmpty()
    val fallbackMedia = listOfNotNull(
        stringOrNull("imageUrl")?.let {
            FeedMediaItem("legacy-image", "image", it, null, stringOrNull("aspectRatio"))
        },
        stringOrNull("videoUrl")?.let {
            FeedMediaItem("legacy-video", "video", it, stringOrNull("thumbnailUrl"), stringOrNull("aspectRatio"))
        },
    )
    val locationCoordinate = optJSONObject("locationCoordinate")?.let { coord ->
        val lat = coord.optDoubleOrNull("latitude")
        val lon = coord.optDoubleOrNull("longitude")
        if (lat != null && lon != null) Moment.LocationCoordinate(lat, lon) else null
    }
    return FeedMoment(
        id = getString("id"),
        authorId = getString("authorId"),
        username = optString("username", "moments"),
        content = optString("content"),
        timestamp = optLong("timestamp"),
        profileImagePath = stringOrNull("profileImagePath"),
        location = stringOrNull("location"),
        mediaItems = structuredMedia.ifEmpty { fallbackMedia },
        aspectRatio = stringOrNull("aspectRatio"),
        commentCount = optInt("commentCount"),
        reactionCount = optJSONObject("reactions")?.let { map ->
            map.keys().asSequence().sumOf { map.optJSONArray(it)?.length() ?: 0 }
        } ?: 0,
        hideLikeCounts = optBoolean("hideLikeCounts"),
        disableComments = optBoolean("disableComments"),
        hasHiddenLayers = optBoolean("hasHiddenLayers"),
        hiddenLayerCount = optInt("hiddenLayerCount"),
        audience = stringOrNull("audience"),
        customListId = stringOrNull("customListId"),
        isArchived = if (has("isArchived")) optBoolean("isArchived") else null,
        locationCoordinate = locationCoordinate,
    )
}

private fun JSONArray.toPhotoTags(): List<com.moments.android.models.PhotoTag> =
    (0 until length()).mapNotNull { i ->
        optJSONObject(i)?.let { t ->
            com.moments.android.models.PhotoTag(
                id = t.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                userId = t.optString("userId"),
                username = t.optString("username"),
                x = t.optDouble("x"),
                y = t.optDouble("y"),
            )
        }
    }

private fun JSONObject.toMoment(): Moment {
    val mediaItems = optJSONArray("mediaItems")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { item ->
                val typeRaw = item.optString("type", "image")
                val type = MediaItem.MediaType.entries.firstOrNull { it.raw == typeRaw }
                    ?: MediaItem.MediaType.IMAGE
                MediaItem(
                    id = item.optString("id"),
                    type = type,
                    url = item.optString("url"),
                    aspectRatio = item.stringOrNull("aspectRatio"),
                    thumbnailUrl = item.stringOrNull("thumbnailUrl"),
                    videoDuration = item.optDoubleOrNull("videoDuration"),
                    videoFileSize = item.optLongOrNull("videoFileSize"),
                    videoResolution = item.stringOrNull("videoResolution"),
                    tags = item.optJSONArray("tags")?.toPhotoTags(),
                )
            }
        }.takeIf { it.isNotEmpty() }
    }
    val reactions = optJSONObject("reactions")?.let { map ->
        map.keys().asSequence().associateWith { key ->
            map.optJSONArray(key)?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } ?: emptyList()
        }
    } ?: emptyMap()
    val locationCoordinate = optJSONObject("locationCoordinate")?.let { coord ->
        val lat = coord.optDoubleOrNull("latitude")
        val lon = coord.optDoubleOrNull("longitude")
        if (lat != null && lon != null) Moment.LocationCoordinate(lat, lon) else null
    }
    return Moment(
        id = getString("id"),
        authorId = getString("authorId"),
        username = optString("username", "moments"),
        content = optString("content"),
        imagePath = stringOrNull("imageUrl"),
        videoUrl = stringOrNull("videoUrl"),
        timestamp = Date(optLong("timestamp")),
        reactions = reactions,
        commentCount = optInt("commentCount"),
        profileImagePath = stringOrNull("profileImagePath"),
        taggedUsers = optJSONArray("taggedUsers")?.toStringList(),
        mentionedUsers = optJSONArray("mentionedUsers")?.toStringList(),
        location = stringOrNull("location"),
        locationCoordinate = locationCoordinate,
        audience = stringOrNull("audience"),
        mediaItems = mediaItems,
        aspectRatio = stringOrNull("aspectRatio"),
        customListId = stringOrNull("customListId"),
        thumbnailUrl = stringOrNull("thumbnailUrl"),
        videoDuration = optDoubleOrNull("videoDuration"),
        videoFileSize = optLongOrNull("videoFileSize"),
        videoResolution = stringOrNull("videoResolution"),
        disableComments = optBoolean("disableComments"),
        hideLikeCounts = optBoolean("hideLikeCounts"),
        allowSharing = optBoolean("allowSharing", true),
        scheduledDate = optLongOrNull("scheduledDate")?.let { Date(it) },
        hasHiddenLayers = optBoolean("hasHiddenLayers"),
        hiddenLayerCount = optInt("hiddenLayerCount"),
    )
}

private fun JSONObject.toHighlightedStory(): HighlightedStory = HighlightedStory(
    id = getString("id"),
    title = getString("title"),
    coverImageUrl = stringOrNull("coverImageUrl"),
    storiesCount = optInt("storiesCount"),
    createdAt = Date((optDoubleOrNull("createdAt") ?: 0.0).toLong()),
    storyIds = optJSONArray("storyIds")?.toStringList() ?: emptyList(),
    authorId = getString("authorId"),
)

private fun JSONObject.toStoryTrayResponse(): BackendStoryTrayResponse {
    val itemsArr = optJSONArray("items")
    val items = (0 until (itemsArr?.length() ?: 0)).mapNotNull { i ->
        itemsArr?.optJSONObject(i)?.toStoryTrayItem()
    }
    val nextCursor = optJSONObject("nextCursor")?.let { StoryRingCursor(it.getInt("offset")) }
    return BackendStoryTrayResponse(
        items = items,
        nextCursor = nextCursor,
        source = optString("source", "unknown"),
        totalCandidates = optInt("totalCandidates"),
    )
}

private fun JSONObject.toStoryTrayItem(): BackendStoryTrayItem {
    val segmentsArr = optJSONArray("segments")
    val segments = (0 until (segmentsArr?.length() ?: 0)).mapNotNull { i ->
        segmentsArr?.optJSONObject(i)?.toStoryTraySegment()
    }
    return BackendStoryTrayItem(
        userId = getString("userId"),
        storyCount = optInt("storyCount"),
        hasUnseenStory = optBoolean("hasUnseenStory"),
        segments = segments,
        latestStoryAt = optDoubleOrNull("latestStoryAt"),
    )
}

private fun JSONObject.toStoryTraySegment(): BackendStoryTraySegment = BackendStoryTraySegment(
    storyId = getString("storyId"),
    viewed = optBoolean("viewed"),
    audience = stringOrNull("audience"),
    timestamp = optDoubleOrNull("timestamp"),
)

private fun JSONObject.toAuthorStoryBundle(): BackendAuthorStoryBundleResponse {
    val storiesArr = optJSONArray("stories")
    val stories = (0 until (storiesArr?.length() ?: 0)).mapNotNull { i ->
        storiesArr?.optJSONObject(i)?.toBackendStoryDocument()
    }
    val segmentsArr = optJSONArray("segments")
    val segments = (0 until (segmentsArr?.length() ?: 0)).mapNotNull { i ->
        segmentsArr?.optJSONObject(i)?.toStoryTraySegment()
    }
    return BackendAuthorStoryBundleResponse(
        authorId = getString("authorId"),
        stories = stories,
        segments = segments,
        source = optString("source", "unknown"),
    )
}

private fun JSONObject.toBackendStoryDocument(): BackendStoryDocument = BackendStoryDocument(
    id = getString("id"),
    authorId = getString("authorId"),
    duration = optDoubleOrNull("duration"),
    expirationHours = optIntOrNull("expirationHours"),
    expirationDate = optDoubleOrNull("expirationDate"),
    timestamp = optDoubleOrNull("timestamp"),
    username = stringOrNull("username"),
    profileImagePath = stringOrNull("profileImagePath"),
    audience = stringOrNull("audience"),
    customListId = stringOrNull("customListId"),
    text = stringOrNull("text"),
    textStyle = stringOrNull("textStyle"),
    textPositionX = optDoubleOrNull("textPositionX"),
    textPositionY = optDoubleOrNull("textPositionY"),
    textPositionNormX = optDoubleOrNull("textPositionNormX"),
    textPositionNormY = optDoubleOrNull("textPositionNormY"),
    textColorHex = stringOrNull("textColorHex"),
    textFontSize = optDoubleOrNull("textFontSize"),
    textAlignment = stringOrNull("textAlignment"),
    textBackgroundFill = stringOrNull("textBackgroundFill"),
    textStroke = stringOrNull("textStroke"),
    textVisualEffect = stringOrNull("textVisualEffect"),
    textMotion = stringOrNull("textMotion"),
    forcesAllCaps = optBooleanOrNull("forcesAllCaps"),
    textLayerOrder = optIntOrNull("textLayerOrder"),
    textOverlayLive = optBooleanOrNull("textOverlayLive"),
    textOverlays = null, // [~] full overlay decode deferred
    drawingData = stringOrNull("drawingData"),
    stickers = null, // [~] full sticker decode deferred
    aspectRatio = stringOrNull("aspectRatio"),
    backgroundFrameURL = stringOrNull("backgroundFrameURL"),
    backgroundBlurredFrameURL = stringOrNull("backgroundBlurredFrameURL"),
    chainId = stringOrNull("chainId"),
    chainPosition = optIntOrNull("chainPosition"),
    chainTitle = stringOrNull("chainTitle"),
    mediaItem = optJSONObject("mediaItem")?.let {
        BackendStoryMediaItem(it.getString("type"), it.getString("url"))
    },
    imagePath = stringOrNull("imagePath"),
    videoUrl = stringOrNull("videoUrl"),
)

fun BackendStoryDocument.toStory(): Story? {
    val media = mediaItem?.let {
        val type = MediaItem.MediaType.entries.firstOrNull { e -> e.raw == it.type }
            ?: MediaItem.MediaType.IMAGE
        MediaItem(type = type, url = it.url)
    } ?: imagePath?.takeIf { it.isNotBlank() }?.let { MediaItem(type = MediaItem.MediaType.IMAGE, url = it) }
        ?: videoUrl?.takeIf { it.isNotBlank() }?.let { MediaItem(type = MediaItem.MediaType.VIDEO, url = it) }
        ?: return null
    val textPosition = if (textPositionX != null && textPositionY != null) {
        Point(textPositionX, textPositionY)
    } else null
    return Story(
        id = id,
        authorId = authorId,
        duration = duration ?: 0.0,
        expirationHours = expirationHours ?: if (chainId != null) 48 else 24,
        expirationDate = Date((expirationDate ?: 0.0).toLong()),
        mediaItem = media,
        profileImagePath = profileImagePath,
        timestamp = Date((timestamp ?: 0.0).toLong()),
        username = username ?: "",
        audience = audience,
        customListId = customListId,
        text = text,
        textPosition = textPosition,
        textStyle = textStyle,
        textPositionNormX = textPositionNormX,
        textPositionNormY = textPositionNormY,
        textColorHex = textColorHex,
        textFontSize = textFontSize,
        textAlignment = textAlignment,
        textBackgroundFill = textBackgroundFill,
        textStroke = textStroke,
        textVisualEffect = textVisualEffect,
        textMotion = textMotion,
        forcesAllCaps = forcesAllCaps,
        textLayerOrder = textLayerOrder,
        textOverlayLive = textOverlayLive,
        textOverlays = textOverlays,
        stickers = stickers,
        aspectRatio = aspectRatio,
        backgroundFrameURL = backgroundFrameURL,
        backgroundBlurredFrameURL = backgroundBlurredFrameURL,
        chainId = chainId,
        chainPosition = chainPosition,
        chainTitle = chainTitle,
    )
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

private fun JSONObject.stringOrNull(name: String): String? = when (val value = opt(name)) {
    null, JSONObject.NULL -> null
    is String -> value.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    else -> null
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null
