package com.moments.android.debugscreenshots

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.AppUser
import java.util.Date

/**
 * Siembra la caché en memoria de Firestore con la red apagada: las escrituras quedan
 * como pendientes locales (nunca se envían) y las lecturas posteriores las ven al
 * instante. Cubre las vistas que leen `users/{id}` y comentarios sin pasar por los
 * ganchos del modo capturas. Solo ids de demo.
 */
object ScreenshotFirestoreSeeder {
    fun seed() {
        if (!ScreenshotMode.isActive) return
        val db = FirebaseFirestore.getInstance()

        for (user in ScreenshotFixtures.allUsers) {
            db.collection("users").document(user.id).set(userDocument(user))
        }

        for ((moment, comments) in ScreenshotFixtures.commentThreads) {
            val momentId = moment.id ?: continue
            val collection = db.collection("users")
                .document(moment.authorId)
                .collection("moments")
                .document(momentId)
                .collection("comments")
            for (comment in comments) {
                collection.document(comment.id).set(
                    mapOf(
                        "authorId" to comment.author.id,
                        "username" to comment.author.username,
                        "content" to comment.text,
                        "timestamp" to Timestamp(comment.timestamp),
                        "profileImagePath" to comment.author.profileImagePath.orEmpty(),
                        "reactions" to emptyMap<String, List<String>>(),
                        "mentions" to emptyList<Map<String, Any>>(),
                    ),
                )
            }
        }
    }

    /** Mismos campos que lee `AppUser.from` (el `id` va también como campo). */
    private fun userDocument(user: AppUser): Map<String, Any> = mapOf(
        "id" to user.id,
        "username" to user.username,
        "email" to "",
        "interests" to emptyList<String>(),
        "isPlusSubscriber" to false,
        "profileImagePath" to user.profileImagePath.orEmpty(),
        "blockedUsers" to emptyList<String>(),
        "isPrivate" to false,
        "showMutuals" to true,
        "showFollowing" to true,
        "showFollowers" to true,
        "bestFriends" to user.bestFriends,
        "followersCount" to user.followersCount,
        "followingCount" to user.followingCount,
        "momentsCount" to user.momentsCount,
        "mutualsCount" to 0,
        "profileVisitorsCount" to 0,
        "isActive" to true,
        "isSuspended" to false,
        "ownedBadges" to emptyList<Map<String, Any>>(),
        "showBadge" to true,
        "showPlusBadge" to true,
        "isVerified" to user.isVerified,
        // En línea en las capturas: chat y bandeja sin el «Desconectado».
        "onlineStatus" to "online",
        "isOnline" to true,
        "lastSeen" to Timestamp(Date()),
        "showReadReceipts" to true,
        "messageRequestPolicy" to "everyone",
    )
}
