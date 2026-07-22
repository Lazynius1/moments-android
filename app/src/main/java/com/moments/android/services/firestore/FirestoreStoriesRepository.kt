package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.moments.android.models.HighlightedStory
import com.moments.android.models.MapVisibilityPolicy
import com.moments.android.models.MediaItem
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.toMap
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.services.privacy.ContentVisibilityType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/** Port de FirestoreStoriesRepository.swift. */
suspend fun FirestoreService.createStory(
    userId: String,
    mediaItem: MediaItem,
    audience: String? = null,
    text: String? = null,
    textPosition: Point? = null,
    textStyle: String? = null,
    textOverlays: List<StoryTextOverlayMetadata>? = null,
    stickers: List<StickerData>? = null,
    drawingData: ByteArray? = null,
    chainId: String? = null,
    chainPosition: Int? = null,
    chainTitle: String? = null,
    expirationHours: Int? = 24,
    duration: Double? = null,
) {
    createStoryDocument(
        userId, mediaItem, audience, null, null, text, textPosition, textStyle,
        null, textOverlays, stickers, drawingData, null, null, null,
        chainId, chainPosition, chainTitle, expirationHours, null, null, null, null, null, duration, null,
    )
}

suspend fun FirestoreService.createStoryWithVisibility(
    userId: String,
    mediaItem: MediaItem,
    audienceSetting: ContentAudience,
    customViewers: List<String>? = null,
    text: String? = null,
    textPosition: Point? = null,
    textStyle: String? = null,
    textOverlay: StoryTextOverlayMetadata? = null,
    textOverlays: List<StoryTextOverlayMetadata>? = null,
    stickers: List<StickerData>? = null,
    drawingData: ByteArray? = null,
    aspectRatio: String? = null,
    backgroundFrameURL: String? = null,
    backgroundBlurredFrameURL: String? = null,
    chainId: String? = null,
    chainPosition: Int? = null,
    chainTitle: String? = null,
    allowOthersToContinue: Boolean? = null,
    continuationAudience: ContentAudience? = null,
    continuationCustomViewers: List<String>? = null,
    continuationCustomListId: String? = null,
    continuationCustomListName: String? = null,
    expirationHours: Int? = 24,
    duration: Double? = null,
    storyId: String? = null,
): String = createStoryDocument(
    userId, mediaItem, audienceSetting.raw, null, customViewers, text, textPosition, textStyle,
    textOverlay, textOverlays, stickers, drawingData, aspectRatio, backgroundFrameURL,
    backgroundBlurredFrameURL, chainId, chainPosition, chainTitle, expirationHours,
    allowOthersToContinue, continuationAudience, continuationCustomViewers,
    continuationCustomListId, continuationCustomListName, duration, storyId,
)

suspend fun FirestoreService.createStoryWithCustomList(
    userId: String,
    mediaItem: MediaItem,
    customListId: String,
    text: String? = null,
    textPosition: Point? = null,
    textStyle: String? = null,
    textOverlay: StoryTextOverlayMetadata? = null,
    textOverlays: List<StoryTextOverlayMetadata>? = null,
    stickers: List<StickerData>? = null,
    drawingData: ByteArray? = null,
    aspectRatio: String? = null,
    backgroundFrameURL: String? = null,
    backgroundBlurredFrameURL: String? = null,
    chainId: String? = null,
    chainPosition: Int? = null,
    chainTitle: String? = null,
    allowOthersToContinue: Boolean? = null,
    continuationAudience: ContentAudience? = null,
    continuationCustomViewers: List<String>? = null,
    continuationCustomListId: String? = null,
    continuationCustomListName: String? = null,
    expirationHours: Int? = 24,
    duration: Double? = null,
    storyId: String? = null,
): String = createStoryDocument(
    userId, mediaItem, ContentAudience.CUSTOM_LIST.raw, customListId, null, text, textPosition,
    textStyle, textOverlay, textOverlays, stickers, drawingData, aspectRatio, backgroundFrameURL,
    backgroundBlurredFrameURL, chainId, chainPosition, chainTitle, expirationHours,
    allowOthersToContinue, continuationAudience, continuationCustomViewers,
    continuationCustomListId, continuationCustomListName, duration, storyId,
)

private suspend fun FirestoreService.createStoryDocument(
    userId: String,
    mediaItem: MediaItem,
    audience: String?,
    customListId: String?,
    customViewers: List<String>?,
    text: String?,
    textPosition: Point?,
    textStyle: String?,
    textOverlay: StoryTextOverlayMetadata?,
    textOverlays: List<StoryTextOverlayMetadata>?,
    stickers: List<StickerData>?,
    drawingData: ByteArray?,
    aspectRatio: String?,
    backgroundFrameURL: String?,
    backgroundBlurredFrameURL: String?,
    chainId: String?,
    chainPosition: Int?,
    chainTitle: String?,
    expirationHours: Int?,
    allowOthersToContinue: Boolean?,
    continuationAudience: ContentAudience?,
    continuationCustomViewers: List<String>?,
    continuationCustomListId: String?,
    continuationCustomListName: String?,
    duration: Double?,
    storyId: String?,
): String {
    val user = fetchUser(userId)
    val isChain = chainId != null
    val resolvedExpirationHours = if (isChain) 48 else if (expirationHours == 48) 48 else 24
    val expirationDate = calculateStoryExpirationDate(isChain, chainId, resolvedExpirationHours)
    val resolvedDuration = duration ?: if (mediaItem.type == MediaItem.MediaType.VIDEO) 60.0 else 15.0
    val resolvedStoryId = storyId ?: UUID.randomUUID().toString()
    val resolvedTextOverlays = textOverlays?.takeIf { it.isNotEmpty() }
        ?: textOverlay?.let { listOf(it) }
    val primaryTextOverlay = resolvedTextOverlays?.minByOrNull { it.layerOrder }

    val story = Story(
        id = resolvedStoryId,
        authorId = userId,
        username = user.username,
        mediaItem = mediaItem,
        duration = resolvedDuration,
        timestamp = Date(),
        expirationHours = resolvedExpirationHours,
        expirationDate = expirationDate,
        profileImagePath = user.profileImagePath,
        audience = audience,
        customListId = customListId,
        text = primaryTextOverlay?.text ?: text,
        textPosition = textPosition,
        textStyle = primaryTextOverlay?.styleRaw ?: textStyle,
        textPositionNormX = primaryTextOverlay?.normalizedPosition?.x,
        textPositionNormY = primaryTextOverlay?.normalizedPosition?.y,
        textColorHex = primaryTextOverlay?.colorHex,
        textFontSize = primaryTextOverlay?.fontSize,
        textAlignment = primaryTextOverlay?.alignmentRaw,
        textBackgroundFill = primaryTextOverlay?.backgroundFillRaw,
        textStroke = primaryTextOverlay?.strokeRaw,
        textVisualEffect = primaryTextOverlay?.visualEffectRaw,
        textMotion = primaryTextOverlay?.motionRaw,
        forcesAllCaps = primaryTextOverlay?.forcesAllCaps,
        textLayerOrder = primaryTextOverlay?.layerOrder,
        textOverlayLive = primaryTextOverlay?.isLiveOverlay,
        textOverlays = resolvedTextOverlays,
        stickers = stickers,
        drawingData = drawingData,
        aspectRatio = aspectRatio,
        backgroundFrameURL = backgroundFrameURL,
        backgroundBlurredFrameURL = backgroundBlurredFrameURL,
        chainId = chainId,
        chainPosition = chainPosition,
        chainTitle = chainTitle,
    )

    var storyData = makeStoryPayload(story, textPosition, stickers).toMutableMap()
    applyChainConfiguration(
        storyData, userId, chainId, chainPosition, chainTitle,
        allowOthersToContinue, continuationAudience, continuationCustomViewers,
        continuationCustomListId, continuationCustomListName,
    )
    if (audience == ContentAudience.CUSTOM.raw && !customViewers.isNullOrEmpty()) {
        saveCustomAudienceForContent("story", resolvedStoryId, userId, customViewers)
    }
    db.collection("users").document(userId).collection("stories")
        .document(resolvedStoryId).set(storyData).await()
    bumpStorySummaryOnCreate(userId, story.audience, story.timestamp, story.expirationDate)
    rebuildStorySummary(userId)
    return resolvedStoryId
}

private fun FirestoreService.makeStoryPayload(
    story: Story,
    textPosition: Point?,
    stickers: List<StickerData>?,
): Map<String, Any?> {
    val storyData = story.toMap().toMutableMap()
    storyData.remove("stickers")
    storyData.remove("textPosition")
    textPosition?.let {
        storyData["textPositionX"] = it.x
        storyData["textPositionY"] = it.y
    }
    story.textOverlays?.let { overlays ->
        storyData["textOverlays"] = overlays.map { overlay ->
            mapOf(
                "id" to overlay.id,
                "text" to overlay.text,
                "normalizedPosition" to mapOf("x" to overlay.normalizedPosition.x, "y" to overlay.normalizedPosition.y),
                "layerOrder" to overlay.layerOrder,
                "styleRaw" to overlay.styleRaw,
                "colorHex" to overlay.colorHex,
                "fontSize" to overlay.fontSize,
                "alignmentRaw" to overlay.alignmentRaw,
                "backgroundFillRaw" to overlay.backgroundFillRaw,
                "strokeRaw" to overlay.strokeRaw,
                "visualEffectRaw" to overlay.visualEffectRaw,
                "motionRaw" to overlay.motionRaw,
                "forcesAllCaps" to overlay.forcesAllCaps,
                "isLiveOverlay" to overlay.isLiveOverlay,
            )
        }
    }
    stickers?.let { storyData["stickers"] = it.map { s -> serializedStorySticker(s) } }
    val mapLocation = MapVisibilityPolicy.storyMapLocation(stickers)
    if (mapLocation != null) {
        storyData["mapLocation"] = mapOf(
            "latitude" to mapLocation.latitude,
            "longitude" to mapLocation.longitude,
            "locationName" to mapLocation.name,
        )
    } else {
        storyData.remove("mapLocation")
    }
    storyData["mapVisibility"] = MapVisibilityPolicy.resolvedVisibility(
        hasLocation = mapLocation != null,
        audience = story.audience,
    )
    return storyData
}

private fun serializedStorySticker(sticker: StickerData): Map<String, Any?> = sticker.toMap()

private suspend fun FirestoreService.applyChainConfiguration(
    storyData: MutableMap<String, Any?>,
    userId: String,
    chainId: String?,
    chainPosition: Int?,
    chainTitle: String?,
    allowOthersToContinue: Boolean?,
    continuationAudience: ContentAudience?,
    continuationCustomViewers: List<String>?,
    continuationCustomListId: String?,
    continuationCustomListName: String?,
) {
    if (chainId == null) return
    allowOthersToContinue?.let { storyData["allowOthersToContinue"] = it }
    continuationAudience?.let { storyData["continuationAudience"] = it.raw }
    continuationCustomViewers?.let { storyData["continuationCustomViewers"] = it }
    continuationCustomListId?.let { storyData["continuationCustomListId"] = it }
    continuationCustomListName?.let { storyData["continuationCustomListName"] = it }
    if (chainPosition != 1) return
    val chainMetadata = mapOf(
        "chainId" to chainId,
        "authorId" to userId,
        "title" to (chainTitle ?: ""),
        "createdAt" to FieldValue.serverTimestamp(),
        "allowOthersToContinue" to (allowOthersToContinue ?: true),
        "continuationAudience" to (continuationAudience?.raw ?: "everyone"),
        "continuationCustomViewers" to (continuationCustomViewers ?: emptyList<String>()),
        "continuationCustomListId" to (continuationCustomListId ?: ""),
        "continuationCustomListName" to (continuationCustomListName ?: ""),
        "isExpired" to false,
    )
    db.collection("storyChains").document(chainId)
        .set(chainMetadata, com.google.firebase.firestore.SetOptions.merge()).await()
}

suspend fun FirestoreService.fetchHighlights(userId: String): List<HighlightedStory> {
    val snap = db.collection("users").document(userId).collection("highlights")
        .orderBy("createdAt", Query.Direction.DESCENDING).get().await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { HighlightedStory.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
}

suspend fun FirestoreService.fetchAllStories(userId: String): List<Story> {
    val snap = db.collection("users").document(userId).collection("stories")
        .orderBy("timestamp", Query.Direction.DESCENDING).get().await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Story.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
}

data class StoriesPage(val stories: List<Story>, val lastDocument: DocumentSnapshot?)

suspend fun FirestoreService.fetchArchivedStoriesPaginated(
    userId: String,
    limit: Int,
    lastDocument: DocumentSnapshot? = null,
): StoriesPage {
    var query: Query = db.collection("users").document(userId).collection("stories")
        .whereLessThan("expirationDate", Date())
        .orderBy("expirationDate", Query.Direction.DESCENDING)
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(limit.toLong())
    if (lastDocument != null) query = query.startAfter(lastDocument)
    val snap = query.get().await()
    val stories = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Story.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
    return StoriesPage(stories, snap.documents.lastOrNull())
}

suspend fun FirestoreService.fetchStoriesPaginated(
    userId: String,
    limit: Int,
    lastDocument: DocumentSnapshot? = null,
): StoriesPage {
    var query: Query = db.collection("users").document(userId).collection("stories")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(limit.toLong())
    if (lastDocument != null) query = query.startAfter(lastDocument)
    val snap = query.get().await()
    val stories = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Story.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
    return StoriesPage(stories, snap.documents.lastOrNull())
}

suspend fun FirestoreService.fetchStoriesByIds(userId: String, storyIds: List<String>): List<Story> =
    coroutineScope {
        storyIds.map { storyId ->
            async {
                val snap = db.collection("users").document(userId).collection("stories")
                    .document(storyId).get().await()
                if (!snap.exists()) return@async null
                @Suppress("UNCHECKED_CAST")
                Story.from(snap.id, snap.data as Map<String, Any?>)
            }
        }.awaitAll().filterNotNull().let { stories ->
            storyIds.mapNotNull { id -> stories.firstOrNull { it.id == id } }
        }
    }

suspend fun FirestoreService.fetchActiveStoriesForUsers(userIds: List<String>): Map<String, List<Story>> {
    val normalized = userIds.filter { it.isNotEmpty() }.distinct()
    if (normalized.isEmpty()) return emptyMap()
    val aggregated = mutableMapOf<String, MutableList<Story>>()
    for (batch in normalized.chunked(10)) {
        val batchResult = runCatching { fetchActiveStoriesBatch(batch) }
            .getOrElse { fetchActiveStoriesLegacy(batch) }
        for ((authorId, stories) in batchResult) {
            aggregated.getOrPut(authorId) { mutableListOf() }.addAll(stories)
        }
    }
    return aggregated.mapValues { (_, stories) -> stories.sortedBy { it.timestamp.time } }
}

private suspend fun FirestoreService.fetchActiveStoriesBatch(userIds: List<String>): Map<String, List<Story>> {
    val snap = db.collectionGroup("stories")
        .whereIn("authorId", userIds)
        .whereGreaterThan("expirationDate", Timestamp(Date()))
        .limit(500)
        .get().await()
    val storiesByUser = mutableMapOf<String, MutableList<Story>>()
    for (doc in snap.documents) {
        @Suppress("UNCHECKED_CAST")
        val story = Story.from(doc.id, doc.data as Map<String, Any?>) ?: continue
        storiesByUser.getOrPut(story.authorId) { mutableListOf() }.add(story)
    }
    return storiesByUser
}

private suspend fun FirestoreService.fetchActiveStoriesLegacy(userIds: List<String>): Map<String, List<Story>> =
    coroutineScope {
        userIds.associateWith { userId ->
            async {
                val snap = db.collection("users").document(userId).collection("stories")
                    .whereGreaterThan("expirationDate", Timestamp(Date()))
                    .get().await()
                snap.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    Story.from(doc.id, doc.data as Map<String, Any?>)
                }.sortedBy { it.timestamp.time }
            }
        }.mapValues { it.value.await() }
    }

suspend fun FirestoreService.fetchStorySummariesForUsers(userIds: List<String>): Map<String, StoryAuthorSummary> {
    val normalized = userIds.filter { it.isNotEmpty() }.distinct()
    if (normalized.isEmpty()) return emptyMap()
    val merged = mutableMapOf<String, StoryAuthorSummary>()
    for (batch in normalized.chunked(10)) {
        val snap = db.collection("users")
            .whereIn(FieldPath.documentId(), batch)
            .get().await()
        for (doc in snap.documents) {
            @Suppress("UNCHECKED_CAST")
            val summary = parseStorySummary(doc.data as Map<String, Any?>) ?: continue
            val normalizedSummary = normalizedStorySummary(summary)
            merged[doc.id] = normalizedSummary
            if (summary.activeStoryCount > 0 && normalizedSummary.activeStoryCount == 0) {
                scheduleStorySummaryRebuildIfNeeded(doc.id)
            }
        }
    }
    return merged
}

suspend fun FirestoreService.rebuildStorySummary(userId: String) {
    require(userId.isNotEmpty()) { "userId vacío" }
    val snap = db.collection("users").document(userId).collection("stories")
        .whereGreaterThan("expirationDate", Timestamp(Date()))
        .get().await()
    val stories = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        Story.from(doc.id, doc.data as Map<String, Any?>)
    }
    val activeCount = stories.size
    val latestStoryAt = stories.maxOfOrNull { it.timestamp }
    val latestExpirationAt = stories.maxOfOrNull { it.expirationDate }
    val audiencesSummary = stories.groupingBy { it.audience?.takeIf { a -> a.isNotEmpty() } ?: "everyone" }
        .eachCount()
    val userRef = db.collection("users").document(userId)
    val summaryPayload = buildMap<String, Any> {
        put("storySummary.activeStoryCount", activeCount)
        put("storySummary.audiencesSummary", audiencesSummary)
        put("storySummary.updatedAt", FieldValue.serverTimestamp())
        if (latestStoryAt != null) put("storySummary.latestStoryAt", Timestamp(latestStoryAt))
        else put("storySummary.latestStoryAt", FieldValue.delete())
        if (latestExpirationAt != null) put("storySummary.latestExpirationAt", Timestamp(latestExpirationAt))
        else put("storySummary.latestExpirationAt", FieldValue.delete())
    }.plus(legacyStorySummaryCleanupPayload(audiencesSummary.keys.toList()))
    userRef.update(summaryPayload).await()
}

suspend fun FirestoreService.bumpStorySummaryOnCreate(
    userId: String,
    audience: String?,
    timestamp: Date,
    expirationDate: Date,
) {
    if (userId.isEmpty()) return
    val audienceKey = audience?.takeIf { it.isNotEmpty() } ?: "everyone"
    val payload = buildMap<String, Any> {
        put("storySummary.activeStoryCount", FieldValue.increment(1))
        put("storySummary.latestStoryAt", Timestamp(timestamp))
        put("storySummary.latestExpirationAt", Timestamp(expirationDate))
        put("storySummary.updatedAt", FieldValue.serverTimestamp())
        put("storySummary.audiencesSummary.$audienceKey", FieldValue.increment(1))
    }.plus(legacyStorySummaryCleanupPayload(listOf(audienceKey)))
    db.collection("users").document(userId).update(payload).await()
}

suspend fun FirestoreService.createHighlight(
    userId: String,
    title: String,
    storyIds: List<String>,
    coverImageUrl: String?,
) {
    val highlight = HighlightedStory(
        title = title,
        coverImageUrl = coverImageUrl,
        storiesCount = storyIds.size,
        createdAt = Date(),
        storyIds = storyIds,
        authorId = userId,
    )
    db.collection("users").document(userId).collection("highlights")
        .add(highlight.toMap()).await()
}

suspend fun FirestoreService.deleteHighlight(userId: String, highlightId: String) {
    db.collection("users").document(userId).collection("highlights")
        .document(highlightId).delete().await()
}

suspend fun FirestoreService.updateHighlight(
    userId: String,
    highlightId: String,
    title: String,
    storyIds: List<String>,
    coverImageUrl: String?,
) {
    db.collection("users").document(userId).collection("highlights").document(highlightId)
        .update(mapOf(
            "title" to title,
            "storyIds" to storyIds,
            "storiesCount" to storyIds.size,
            "coverImageUrl" to coverImageUrl,
        )).await()
}

suspend fun FirestoreService.prefetchStoriesForUser(userId: String) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val snap = db.collection("users").document(userId).collection("stories")
        .whereGreaterThan("expirationDate", Timestamp(Date()))
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(5)
        .get().await()
    if (snap.isEmpty) return
    val stories = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        Story.from(doc.id, doc.data as Map<String, Any?>)
    }
    val authorized = coroutineScope {
        stories.map { story ->
            async {
                val visibilityType = when (story.audience) {
                    "mutuals" -> ContentVisibilityType.MUTUALS
                    "bestFriends" -> ContentVisibilityType.BEST_FRIENDS
                    "custom", "customList" -> ContentVisibilityType.CUSTOM
                    "onlyMe" -> ContentVisibilityType.ONLY_ME
                    else -> ContentVisibilityType.EVERYONE
                }
                val canSee = ContentVisibilityService.canUserSeeContent(
                    story.authorId, currentUserId, visibilityType,
                    story.customListId?.let { listOf(it) },
                )
                if (canSee) story else null
            }
        }.awaitAll().filterNotNull()
    }
    prefetchStoriesToCache(authorized)
}

private fun prefetchStoriesToCache(stories: List<Story>) {
    val urls = stories.take(5).mapNotNull { story ->
        story.mediaItem.url.takeIf { it.isNotBlank() }
    }
    if (urls.isNotEmpty()) ImagePrefetchManager.prefetch(urls)
}
