package com.moments.android.views.nova.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

/**
 * Port de `Views/Nova/Tools/NovaSocialTools.swift`.
 * Listas de audiencia, create_moment (upload) y sugerencias de conexión.
 */
class NovaSocialTools(
    context: Context,
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    private val appContext = context.applicationContext

    suspend fun listAudienceLists(userId: String): Map<String, Any?> =
        runCatching {
            val lists = firestoreService.fetchCustomLists(userId)
            val payload = lists.mapNotNull { list ->
                val id = list.id ?: return@mapNotNull null
                mapOf(
                    "id" to id,
                    "name" to list.name,
                    "member_count" to NovaJSON.int(list.members.size),
                )
            }
            mapOf(
                "count" to NovaJSON.int(payload.size),
                "lists" to payload,
            )
        }.getOrElse { error ->
            mapOf(
                "count" to NovaJSON.int(0),
                "error" to (error.message ?: "Unknown error"),
                "lists" to emptyList<Any>(),
            )
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
        val trimmed = content.trim()
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
            val code = (error as? NovaMomentAudienceError)?.code
                ?: error.message
                ?: "audience_resolution_failed"
            return mapOf("success" to false, "error" to code)
        }

        return uploadMomentWithImage(
            content = trimmed,
            audience = audience,
            image = image,
        )
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

        // ≡ iOS: audienceSetting (.custom también para customList) + customListId aparte.
        val started = BackgroundMomentUploadService.uploadMoment(
            content = content,
            mediaItems = listOf(media),
            taggedUsers = null,
            mentionedUsers = captionMentionIds.takeIf { it.isNotEmpty() },
            location = null,
            audienceSetting = audience.audienceSettingRaw(),
            customViewers = audience.customViewers,
            customListId = audience.customListId,
            aspectRatio = media.aspectRatio.displayName,
        ) != null

        if (!started) {
            return mapOf("success" to false, "error" to "upload_start_failed")
        }

        return mapOf(
            "success" to true,
            "status" to "uploading",
            "audience" to audience.contentAudience.raw,
            "audience_label" to audience.displayLabel(appContext),
            "has_media" to true,
            "content_preview" to content.take(120),
            "mentioned_users_count" to NovaJSON.int(captionMentionIds.size),
        )
    }

    suspend fun connectionSuggestions(limit: Int = 5): Map<String, Any?> {
        val capped = limit.coerceIn(1, 10)
        return runCatching {
            val users = firestoreService.fetchSuggestedUsers()
            val suggestions = users.take(capped).map { user ->
                mapOf(
                    "user_id" to user.id,
                    "username" to user.username,
                    "bio_preview" to user.bio.orEmpty().take(80),
                )
            }
            mapOf(
                "count" to NovaJSON.int(suggestions.size),
                "suggestions" to suggestions,
            )
        }.getOrElse { error ->
            mapOf(
                "count" to NovaJSON.int(0),
                "error" to (error.message ?: "Unknown error"),
                "suggestions" to emptyList<Any>(),
            )
        }
    }

    private fun persistMomentImage(image: Bitmap): Uri? = runCatching {
        val output = File(appContext.cacheDir, "nova_moment_${UUID.randomUUID()}.jpg")
        FileOutputStream(output).use { stream ->
            check(image.compress(Bitmap.CompressFormat.JPEG, 95, stream))
        }
        Uri.fromFile(output)
    }.getOrNull()
}
