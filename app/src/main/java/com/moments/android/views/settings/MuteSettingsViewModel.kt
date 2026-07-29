package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Port de `MuteSettingsViewModel` en `MuteSettingsView.swift`.
 * Persistencia: `users/{uid}.muteSettings`.
 */
class MuteSettingsViewModel {
    var mutedUsers by mutableStateOf<List<AppUser>>(emptyList())
    var mutedWords by mutableStateOf<List<String>>(emptyList())
    var muteNotifications by mutableStateOf(false)
    var hideFromSearch by mutableStateOf(false)

    private val firestoreService = FirestoreService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun loadSettings(completion: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            completion()
            return
        }

        scope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    firestoreService.db.collection("users").document(userId).get().await()
                }
                @Suppress("UNCHECKED_CAST")
                val rawSettings = snapshot.data?.get("muteSettings") as? Map<String, Any?> ?: emptyMap()
                val mutedUserIds = (rawSettings["mutedUsers"] as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
                val words = (rawSettings["mutedWords"] as? List<*>)?.filterIsInstance<String>()
                    ?: emptyList()
                muteNotifications = rawSettings["muteNotifications"] as? Boolean ?: false
                hideFromSearch = rawSettings["hideFromSearch"] as? Boolean ?: false
                mutedWords = words
                loadMutedUsers(mutedUserIds)
            } catch (_: Exception) {
                mutedUsers = emptyList()
                mutedWords = emptyList()
            } finally {
                completion()
            }
        }
    }

    private suspend fun loadMutedUsers(userIds: List<String>) {
        if (userIds.isEmpty()) {
            mutedUsers = emptyList()
            return
        }
        val loadedById = withContext(Dispatchers.IO) {
            userIds.map { id ->
                async {
                    runCatching { firestoreService.fetchUserProfile(id) }.getOrNull()?.let { id to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        mutedUsers = userIds.mapNotNull { loadedById[it] }
    }

    fun muteUser(user: AppUser) {
        if (mutedUsers.none { it.id == user.id }) {
            mutedUsers = mutedUsers + user
            saveSettings()
        }
    }

    fun unmuteUser(userId: String) {
        mutedUsers = mutedUsers.filterNot { it.id == userId }
        saveSettings()
    }

    fun addMutedWord(word: String) {
        val normalized = word.lowercase()
        if (mutedWords.none { it == normalized }) {
            mutedWords = mutedWords + normalized
            saveSettings()
        }
    }

    fun removeMutedWord(word: String) {
        mutedWords = mutedWords.filterNot { it == word }
        saveSettings()
    }

    fun saveSettings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val settings = mapOf(
            "mutedUsers" to mutedUsers.map { it.id },
            "mutedWords" to mutedWords,
            "muteNotifications" to muteNotifications,
            "hideFromSearch" to hideFromSearch,
        )
        firestoreService.db.collection("users").document(userId)
            .update("muteSettings", settings)
    }
}
