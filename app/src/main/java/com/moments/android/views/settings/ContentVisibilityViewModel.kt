package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.creator.audienceselector.ContentAudience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Port de `ContentVisibilityViewModel` en `ContentVisibilityView.swift`.
 */
class ContentVisibilityViewModel {
    var storyAudience by mutableStateOf(ContentAudience.EVERYONE)
    var storyCustomListId by mutableStateOf<String?>(null)
    var storyCustomListName by mutableStateOf<String?>(null)
    var storyCustomUsers by mutableStateOf<List<String>>(emptyList())
    var allowStoryMessages by mutableStateOf(true)
    var allowStoryReactions by mutableStateOf(true)
    var allowStoryEphemeralPhotos by mutableStateOf(true)

    var postAudience by mutableStateOf(ContentAudience.EVERYONE)
    var postCustomListId by mutableStateOf<String?>(null)
    var postCustomListName by mutableStateOf<String?>(null)
    var postCustomUsers by mutableStateOf<List<String>>(emptyList())

    var hiddenFromUsers by mutableStateOf<List<AppUser>>(emptyList())

    private val firestoreService = FirestoreService()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun loadSettings(completion: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            completion()
            return
        }
        scope.launch {
            runCatching {
                val document = firestoreService.db.collection("users").document(userId).get().await()
                val data = document.data
                @Suppress("UNCHECKED_CAST")
                val visibility = data?.get("contentVisibilitySettings") as? Map<String, Any?>
                if (visibility != null) {
                    withContext(Dispatchers.Main) {
                        (visibility["storyAudience"] as? String)?.let {
                            storyAudience = ContentAudience.from(it)
                        }
                        storyCustomListId = visibility["storyCustomListId"] as? String
                        storyCustomListName = visibility["storyCustomListName"] as? String
                        @Suppress("UNCHECKED_CAST")
                        storyCustomUsers = visibility["storyCustomUsers"] as? List<String> ?: emptyList()
                        allowStoryMessages = visibility["allowStoryMessages"] as? Boolean ?: true
                        allowStoryReactions = visibility["allowStoryReactions"] as? Boolean ?: true
                        allowStoryEphemeralPhotos =
                            visibility["allowStoryEphemeralPhotos"] as? Boolean ?: true

                        (visibility["postAudience"] as? String)?.let {
                            postAudience = ContentAudience.from(it)
                        }
                        postCustomListId = visibility["postCustomListId"] as? String
                        postCustomListName = visibility["postCustomListName"] as? String
                        @Suppress("UNCHECKED_CAST")
                        postCustomUsers = visibility["postCustomUsers"] as? List<String> ?: emptyList()
                    }
                    @Suppress("UNCHECKED_CAST")
                    val hiddenIds = visibility["hiddenFromUsers"] as? List<String> ?: emptyList()
                    if (hiddenIds.isNotEmpty()) {
                        val users = runCatching { firestoreService.fetchUsers(hiddenIds) }.getOrDefault(emptyList())
                        withContext(Dispatchers.Main) { hiddenFromUsers = users }
                    }
                }
            }
            withContext(Dispatchers.Main) { completion() }
        }
    }

    fun saveStorySettings() {
        saveSettings(
            audienceKey = "storyAudience",
            audience = storyAudience,
            listIdKey = "storyCustomListId",
            listId = storyCustomListId,
            listNameKey = "storyCustomListName",
            listName = storyCustomListName,
            customUsersKey = "storyCustomUsers",
            customUsers = storyCustomUsers,
        )
    }

    fun savePostSettings() {
        saveSettings(
            audienceKey = "postAudience",
            audience = postAudience,
            listIdKey = "postCustomListId",
            listId = postCustomListId,
            listNameKey = "postCustomListName",
            listName = postCustomListName,
            customUsersKey = "postCustomUsers",
            customUsers = postCustomUsers,
        )
    }

    fun saveStoryInteractionSettings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                firestoreService.db.collection("users").document(userId).update(
                    mapOf(
                        "contentVisibilitySettings.allowStoryMessages" to allowStoryMessages,
                        "contentVisibilitySettings.allowStoryReactions" to allowStoryReactions,
                        "contentVisibilitySettings.allowStoryEphemeralPhotos" to allowStoryEphemeralPhotos,
                    ),
                ).await()
            }
        }
    }

    private fun saveSettings(
        audienceKey: String,
        audience: ContentAudience,
        listIdKey: String,
        listId: String?,
        listNameKey: String,
        listName: String?,
        customUsersKey: String,
        customUsers: List<String>,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>(
            "contentVisibilitySettings.$audienceKey" to audience.raw,
            "contentVisibilitySettings.$customUsersKey" to customUsers,
            "contentVisibilitySettings.hiddenFromUsers" to hiddenFromUsers.map { it.id },
        )
        updates["contentVisibilitySettings.$listIdKey"] =
            if (!listId.isNullOrEmpty()) listId else FieldValue.delete()
        updates["contentVisibilitySettings.$listNameKey"] =
            if (!listName.isNullOrEmpty()) listName else FieldValue.delete()

        scope.launch {
            runCatching {
                firestoreService.db.collection("users").document(userId).update(updates).await()
            }
        }
    }
}
