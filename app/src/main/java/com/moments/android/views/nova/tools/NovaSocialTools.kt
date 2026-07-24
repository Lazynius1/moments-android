package com.moments.android.views.nova.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.moments.android.R
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchSuggestedUsers
import com.moments.android.utilities.MomentMentionResolver
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorMedia
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Port de `NovaSocialTools.swift`. */
class NovaSocialTools(
    context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    private val appContext = context.applicationContext

    suspend fun listAudienceLists(userId: String): Map<String, Any?> = runCatching {
        val lists = firestoreService.fetchCustomLists(userId)
        mapOf(
            "count" to lists.size,
            "lists" to lists.mapNotNull { list ->
                list.id?.let { id ->
                    mapOf(
                        "id" to id,
                        "name" to list.name,
                        "member_count" to list.members.size,
                    )
                }
            },
        )
    }.getOrElse { error ->
        mapOf("count" to 0, "error" to (error.message ?: "Unknown error"), "lists" to emptyList<Any>())
    }

    suspend fun createMoment(
        userId: String,
        content: String,
        audienceRaw: String,
        targetUsername: String?,
        customListName: String?,
        customListId: String?,
        attachedImage: Bitmap?,
    ): Map<String, Any?> {
        val image = attachedImage ?: return mapOf(
            "success" to false,
            "error" to "missing_media",
            "hint" to "Moments require a photo or video. The user must attach media in the chat.",
        )
        val audience = NovaMomentAudienceResolver.resolve(
            userId = userId,
            audienceRaw = audienceRaw,
            targetUsername = targetUsername,
            customListName = customListName,
            customListId = customListId,
            firestoreService = firestoreService,
        ).getOrElse { error ->
            val code = (error as? NovaMomentAudienceError)?.code ?: error.message ?: "audience_resolution_failed"
            return mapOf("success" to false, "error" to code)
        }
        return uploadMomentWithImage(content.trim(), audience, image)
    }

    private suspend fun uploadMomentWithImage(
        content: String,
        audience: NovaMomentAudience,
        image: Bitmap,
    ): Map<String, Any?> {
        val imageUri = persistMomentImage(image) ?: return mapOf(
            "success" to false,
            "error" to "upload_start_failed",
        )
        val aspectRatio = CreatorAspectRatio.fromRatio(
            image.width.toFloat() / image.height.coerceAtLeast(1).toFloat(),
        )
        val media = CreatorMedia(uri = imageUri, aspectRatio = aspectRatio)
        val captionMentionIds = MomentMentionResolver.resolveUserIds(content)
        val started = BackgroundMomentUploadService.uploadMoment(
            content = content,
            mediaItems = listOf(media),
            taggedUsers = null,
            mentionedUsers = captionMentionIds.takeIf { it.isNotEmpty() },
            location = null,
            audienceSetting = audience.contentAudience.raw,
            customViewers = audience.customViewers,
            customListId = audience.customListId,
            aspectRatio = media.aspectRatio.displayName,
        ) != null
        if (!started) return mapOf("success" to false, "error" to "upload_start_failed")

        return mapOf(
            "success" to true,
            "status" to "uploading",
            "audience" to audience.contentAudience.raw,
            "audience_label" to audience.displayLabel(),
            "has_media" to true,
            "content_preview" to content.take(120),
            "mentioned_users_count" to captionMentionIds.size,
        )
    }

    suspend fun connectionSuggestions(limit: Int = 5): Map<String, Any?> = runCatching {
        val users = firestoreService.fetchSuggestedUsers()
        val suggestions = users.take(limit.coerceIn(1, 10)).map { user ->
            mapOf(
                "user_id" to user.id,
                "username" to user.username,
                "bio_preview" to user.bio.orEmpty().take(80),
            )
        }
        mapOf("count" to suggestions.size, "suggestions" to suggestions)
    }.getOrElse { error ->
        mapOf("count" to 0, "error" to (error.message ?: "Unknown error"), "suggestions" to emptyList<Any>())
    }

    private fun persistMomentImage(image: Bitmap): Uri? = runCatching {
        val output = File(appContext.cacheDir, "nova_moment_${UUID.randomUUID()}.jpg")
        FileOutputStream(output).use { stream ->
            check(image.compress(Bitmap.CompressFormat.JPEG, 95, stream))
        }
        Uri.fromFile(output)
    }.getOrNull()

    private fun NovaMomentAudience.displayLabel(): String = when (this) {
        NovaMomentAudience.Everyone -> appContext.getString(R.string.audience_everyone)
        NovaMomentAudience.Mutuals -> appContext.getString(R.string.audience_mutuals)
        NovaMomentAudience.BestFriends -> appContext.getString(R.string.audience_best_friends)
        NovaMomentAudience.OnlyMe -> appContext.getString(R.string.audience_only_me)
        is NovaMomentAudience.Custom -> label
        is NovaMomentAudience.CustomList -> listName
    }
}
