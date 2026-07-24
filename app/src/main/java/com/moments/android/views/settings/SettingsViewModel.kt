package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.MessageRequestPolicy
import com.moments.android.models.NotificationType
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.privacy.PrivacyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port 1:1 de `SettingsViewModel.swift`.
 */
class SettingsViewModel {
    var notificationPreferences by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    private val firestoreService = FirestoreService()
    private val scope = CoroutineScope(Dispatchers.IO)

    val dateFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun fetchUserSettings(onResult: (Result<AppUser>) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onResult(Result.failure(Exception("settings.error.notAuthenticated")))
            return
        }

        scope.launch {
            try {
                val user = firestoreService.fetchUser(userId)
                val defaultPreferences: Map<String, Boolean> = mapOf(
                    NotificationType.LIKE.raw to true,
                    NotificationType.NEW_FOLLOWER.raw to true,
                    NotificationType.FOLLOW_REQUEST.raw to true,
                    NotificationType.MUTUAL_CONNECTION.raw to true,
                    NotificationType.COMMENT.raw to true,
                    NotificationType.STORY_REACTION.raw to true,
                    "gentleReminders" to true,
                    "commentsMutualsOnly" to false,
                    "muteOldPostReactions" to false
                )
                val userPrefs = user.notificationPreferences ?: emptyMap()
                notificationPreferences = defaultPreferences + userPrefs
                onResult(Result.success(user))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun updatePrivacySettings(
        isPrivate: Boolean? = null,
        showMutuals: Boolean? = null,
        showFollowing: Boolean? = null,
        showFollowers: Boolean? = null
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            PrivacyService.updatePrivacySettings(
                userId = userId,
                isPrivate = isPrivate,
                showMutuals = showMutuals,
                showFollowing = showFollowing,
                showFollowers = showFollowers
            )
        }
    }

    fun updateReadReceiptsPrivacy(enabled: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestoreService.db.collection("users").document(userId).update(
            mapOf("showReadReceipts" to enabled)
        )
    }

    fun updateMessageRequestPolicy(policy: MessageRequestPolicy) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestoreService.db.collection("users").document(userId).update(
            mapOf("messageRequestPolicy" to policy.raw)
        )
    }

    fun updateActiveHours(startTime: Date, endTime: Date, onComplete: ((Throwable?) -> Unit)? = null) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val startHour = dateFormatter.format(startTime)
        val endHour = dateFormatter.format(endTime)
        scope.launch {
            try {
                firestoreService.updateActiveHours(userId, startHour, endHour)
                onComplete?.invoke(null)
            } catch (e: Exception) {
                onComplete?.invoke(e)
            }
        }
    }

    fun clearActiveHours() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            firestoreService.clearActiveHours(userId)
        }
    }

    fun updateNotificationPreference(type: String, isEnabled: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val updated = notificationPreferences.toMutableMap().apply { put(type, isEnabled) }
        notificationPreferences = updated
        scope.launch {
            firestoreService.updateNotificationPreferences(userId, updated)
        }
    }
}
