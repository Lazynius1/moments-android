package com.moments.android.views.settings

import android.content.Context
import com.moments.android.MomentsApplication
import com.moments.android.models.Moment
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Port de `UserActivityCache.swift`.
 *
 * iOS persiste con `UserDefaults` + `Codable`; aquí se usa `SharedPreferences` + JSON, con las
 * MISMAS claves (`activityCache_*_{userId}`) y los mismos campos del payload, para que el
 * comportamiento y el ciclo de invalidación sean idénticos.
 */

data class CachedReactionPayload(
    val id: String,
    val authorId: String,
    val momentId: String,
    val reactionType: String,
    val reactedAt: Double,
    val canView: Boolean,
    val momentImagePath: String? = null,
    val momentVideoUrl: String? = null,
    val momentThumbnailUrl: String? = null,
    val momentContent: String? = null,
    val momentUsername: String? = null,
    val momentAuthorId: String? = null,
    val momentAudience: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorId", authorId)
        put("momentId", momentId)
        put("reactionType", reactionType)
        put("reactedAt", reactedAt)
        put("canView", canView)
        putOpt("momentImagePath", momentImagePath)
        putOpt("momentVideoUrl", momentVideoUrl)
        putOpt("momentThumbnailUrl", momentThumbnailUrl)
        putOpt("momentContent", momentContent)
        putOpt("momentUsername", momentUsername)
        putOpt("momentAuthorId", momentAuthorId)
        putOpt("momentAudience", momentAudience)
    }

    companion object {
        fun from(json: JSONObject): CachedReactionPayload = CachedReactionPayload(
            id = json.optString("id"),
            authorId = json.optString("authorId"),
            momentId = json.optString("momentId"),
            reactionType = json.optString("reactionType"),
            reactedAt = json.optDouble("reactedAt", 0.0),
            canView = json.optBoolean("canView", true),
            momentImagePath = json.optStringOrNull("momentImagePath"),
            momentVideoUrl = json.optStringOrNull("momentVideoUrl"),
            momentThumbnailUrl = json.optStringOrNull("momentThumbnailUrl"),
            momentContent = json.optStringOrNull("momentContent"),
            momentUsername = json.optStringOrNull("momentUsername"),
            momentAuthorId = json.optStringOrNull("momentAuthorId"),
            momentAudience = json.optStringOrNull("momentAudience"),
        )
    }
}

data class CachedCommentPayload(
    val id: String,
    val authorId: String,
    val momentId: String,
    val commentId: String,
    val commentText: String,
    val commentedAt: Double,
    val canView: Boolean,
    val momentImagePath: String? = null,
    val momentVideoUrl: String? = null,
    val momentThumbnailUrl: String? = null,
    val momentContent: String? = null,
    val momentUsername: String? = null,
    val momentAuthorId: String? = null,
    val momentAudience: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorId", authorId)
        put("momentId", momentId)
        put("commentId", commentId)
        put("commentText", commentText)
        put("commentedAt", commentedAt)
        put("canView", canView)
        putOpt("momentImagePath", momentImagePath)
        putOpt("momentVideoUrl", momentVideoUrl)
        putOpt("momentThumbnailUrl", momentThumbnailUrl)
        putOpt("momentContent", momentContent)
        putOpt("momentUsername", momentUsername)
        putOpt("momentAuthorId", momentAuthorId)
        putOpt("momentAudience", momentAudience)
    }

    companion object {
        fun from(json: JSONObject): CachedCommentPayload = CachedCommentPayload(
            id = json.optString("id"),
            authorId = json.optString("authorId"),
            momentId = json.optString("momentId"),
            commentId = json.optString("commentId"),
            commentText = json.optString("commentText"),
            commentedAt = json.optDouble("commentedAt", 0.0),
            canView = json.optBoolean("canView", true),
            momentImagePath = json.optStringOrNull("momentImagePath"),
            momentVideoUrl = json.optStringOrNull("momentVideoUrl"),
            momentThumbnailUrl = json.optStringOrNull("momentThumbnailUrl"),
            momentContent = json.optStringOrNull("momentContent"),
            momentUsername = json.optStringOrNull("momentUsername"),
            momentAuthorId = json.optStringOrNull("momentAuthorId"),
            momentAudience = json.optStringOrNull("momentAudience"),
        )
    }
}

object ActivityCache {
    private const val PREFS_NAME = "activity_cache"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() = (appContext ?: MomentsApplication.instance?.applicationContext)
        ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun minimalMoment(
        imagePath: String?,
        videoUrl: String?,
        thumbnailUrl: String?,
        content: String?,
        username: String?,
        authorId: String?,
        id: String,
        audience: String?,
    ): Moment = Moment(
        id = id,
        authorId = authorId.orEmpty(),
        username = username.orEmpty(),
        content = content.orEmpty(),
        imagePath = imagePath,
        videoUrl = videoUrl,
        timestamp = Date(),
        reactions = emptyMap(),
        commentCount = 0,
        profileImagePath = null,
        taggedUsers = null,
        location = null,
        audience = audience,
        mediaItems = null,
        aspectRatio = null,
        customListId = null,
        thumbnailUrl = thumbnailUrl,
        videoDuration = null,
        videoFileSize = null,
        videoResolution = null,
        disableComments = false,
        hideLikeCounts = false,
        allowSharing = true,
    )

    private fun writeArray(key: String, items: List<JSONObject>) {
        val array = JSONArray().apply { items.forEach { put(it) } }
        prefs()?.edit()?.putString(key, array.toString())?.apply()
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs()?.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    fun saveReactions(items: List<ActivityReactionItem>, userId: String) {
        writeArray(
            "activityCache_reactions_$userId",
            items.map { item ->
                CachedReactionPayload(
                    id = item.id,
                    authorId = item.authorId,
                    momentId = item.momentId,
                    reactionType = item.reactionType,
                    reactedAt = item.reactedAt.time / 1000.0,
                    canView = item.canView,
                    momentImagePath = item.moment?.imagePath,
                    momentVideoUrl = item.moment?.videoUrl,
                    momentThumbnailUrl = item.moment?.thumbnailUrl,
                    momentContent = item.moment?.content,
                    momentUsername = item.moment?.username,
                    momentAuthorId = item.moment?.authorId,
                    momentAudience = item.moment?.audience,
                ).toJson()
            },
        )
    }

    fun loadReactions(userId: String): List<ActivityReactionItem> =
        readArray("activityCache_reactions_$userId")
            .map(CachedReactionPayload::from)
            .map { payload -> payload.toReactionItem() }

    fun saveComments(items: List<ActivityCommentItem>, userId: String) {
        writeArray(
            "activityCache_comments_$userId",
            items.map { item ->
                CachedCommentPayload(
                    id = item.id,
                    authorId = item.authorId,
                    momentId = item.momentId,
                    commentId = item.commentId,
                    commentText = item.commentText,
                    commentedAt = item.commentedAt.time / 1000.0,
                    canView = item.canView,
                    momentImagePath = item.moment?.imagePath,
                    momentVideoUrl = item.moment?.videoUrl,
                    momentThumbnailUrl = item.moment?.thumbnailUrl,
                    momentContent = item.moment?.content,
                    momentUsername = item.moment?.username,
                    momentAuthorId = item.moment?.authorId,
                    momentAudience = item.moment?.audience,
                ).toJson()
            },
        )
    }

    fun loadComments(userId: String): List<ActivityCommentItem> =
        readArray("activityCache_comments_$userId")
            .map(CachedCommentPayload::from)
            .map { payload ->
                ActivityCommentItem(
                    id = payload.id,
                    authorId = payload.authorId,
                    momentId = payload.momentId,
                    commentId = payload.commentId,
                    commentText = payload.commentText,
                    commentedAt = Date((payload.commentedAt * 1000).toLong()),
                    moment = minimalMoment(
                        payload.momentImagePath,
                        payload.momentVideoUrl,
                        payload.momentThumbnailUrl,
                        payload.momentContent,
                        payload.momentUsername,
                        payload.momentAuthorId,
                        payload.momentId,
                        payload.momentAudience,
                    ),
                    canView = payload.canView,
                )
            }

    fun saveRecentlyDeletedCount(count: Int, userId: String) {
        prefs()?.edit()
            ?.putInt("activityCache_recentlyDeletedCount_$userId", maxOf(0, count))
            ?.apply()
    }

    fun loadRecentlyDeletedCount(userId: String): Int =
        prefs()?.getInt("activityCache_recentlyDeletedCount_$userId", 0) ?: 0

    fun saveTagged(items: List<ActivityReactionItem>, userId: String) {
        writeArray(
            "activityCache_tags_$userId",
            items.map { item ->
                CachedReactionPayload(
                    id = item.id,
                    authorId = item.authorId,
                    momentId = item.momentId,
                    reactionType = item.reactionType,
                    reactedAt = item.reactedAt.time / 1000.0,
                    canView = item.canView,
                    momentImagePath = item.moment?.imagePath,
                    momentVideoUrl = item.moment?.videoUrl,
                    momentThumbnailUrl = item.moment?.thumbnailUrl,
                    momentContent = item.moment?.content,
                    momentUsername = item.moment?.username,
                    momentAuthorId = item.moment?.authorId,
                    momentAudience = item.moment?.audience,
                ).toJson()
            },
        )
    }

    fun loadTagged(userId: String): List<ActivityReactionItem> =
        readArray("activityCache_tags_$userId")
            .map(CachedReactionPayload::from)
            .map { payload -> payload.toReactionItem() }

    fun saveStickerReplyCount(count: Int, userId: String) {
        prefs()?.edit()
            ?.putInt("activityCache_stickerCount_$userId", maxOf(0, count))
            ?.apply()
    }

    fun loadStickerReplyCount(userId: String): Int =
        prefs()?.getInt("activityCache_stickerCount_$userId", 0) ?: 0

    private fun CachedReactionPayload.toReactionItem(): ActivityReactionItem = ActivityReactionItem(
        id = id,
        authorId = authorId,
        momentId = momentId,
        reactionType = reactionType,
        reactedAt = Date((reactedAt * 1000).toLong()),
        moment = minimalMoment(
            momentImagePath,
            momentVideoUrl,
            momentThumbnailUrl,
            momentContent,
            momentUsername,
            momentAuthorId,
            momentId,
            momentAudience,
        ),
        canView = canView,
    )
}
