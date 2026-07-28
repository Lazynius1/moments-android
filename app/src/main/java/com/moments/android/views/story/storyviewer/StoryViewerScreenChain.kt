package com.moments.android.views.story.storyviewer

import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.services.privacy.PrivacyService
import kotlinx.coroutines.tasks.await

/**
 * Port de la lógica de continuación de cadena en `StoryViewerScreen.swift`
 * (`checkCanContinueChain` → `checkContinuationAudience`).
 */
internal object StoryViewerChainLogic {

    suspend fun canContinueChain(
        chainId: String,
        storyAuthorId: String,
        currentUserId: String,
        db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): Boolean {
        if (currentUserId.isEmpty() || chainId.isEmpty()) return false

        val global = runCatching {
            db.collection("storyChains").document(chainId).get().await()
        }.getOrNull()
        if (global != null && global.exists()) {
            return processChainMetadata(global.data.orEmpty(), currentUserId)
        }

        return fallbackToCheckFirstPart(chainId, storyAuthorId, currentUserId, db)
    }

    private suspend fun fallbackToCheckFirstPart(
        chainId: String,
        storyAuthorId: String,
        currentUserId: String,
        db: FirebaseFirestore,
    ): Boolean {
        val firstPart = runCatching {
            db.collection("users").document(storyAuthorId).collection("stories")
                .whereEqualTo("chainId", chainId)
                .whereEqualTo("chainPosition", 1)
                .limit(1)
                .get().await()
                .documents.firstOrNull()
        }.getOrNull()
        if (firstPart != null) {
            return processChainMetadata(firstPart.data.orEmpty(), currentUserId)
        }
        return ultimateFallbackSearch(chainId, currentUserId, db)
    }

    private suspend fun ultimateFallbackSearch(
        chainId: String,
        currentUserId: String,
        db: FirebaseFirestore,
    ): Boolean {
        val ultimate = runCatching {
            db.collectionGroup("stories")
                .whereEqualTo("chainId", chainId)
                .whereEqualTo("chainPosition", 1)
                .limit(1)
                .get().await()
                .documents.firstOrNull()
        }.getOrNull() ?: return false
        return processChainMetadata(ultimate.data.orEmpty(), currentUserId)
    }

    private suspend fun processChainMetadata(data: Map<String, Any?>, currentUserId: String): Boolean {
        val authorId = data["authorId"] as? String ?: ""
        if (authorId == currentUserId) return true
        val allowOthers = data["allowOthersToContinue"] as? Boolean ?: true
        if (!allowOthers) return false
        val audience = data["continuationAudience"] as? String ?: "everyone"
        return checkContinuationAudience(audience, data, currentUserId)
    }

    private suspend fun checkContinuationAudience(
        continuationAudience: String,
        data: Map<String, Any?>,
        currentUserId: String,
    ): Boolean {
        val authorId = data["authorId"] as? String ?: ""
        return when (continuationAudience) {
            "everyone" -> true
            "mutuals" -> PrivacyService.checkMutualConnection(currentUserId, authorId)
            "bestFriends" -> PrivacyService.checkIfBestFriend(authorId, currentUserId)
            "custom" -> {
                @Suppress("UNCHECKED_CAST")
                val viewers = data["continuationCustomViewers"] as? List<String> ?: emptyList()
                currentUserId in viewers
            }
            "customList" -> {
                val listId = data["continuationCustomListId"] as? String ?: return false
                val members = PrivacyService.getCustomListViewers(listId, authorId)
                currentUserId in members
            }
            else -> false
        }
    }
}
