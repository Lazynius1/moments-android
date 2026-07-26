package com.moments.android.models

import com.google.firebase.Timestamp
import com.moments.android.utilities.MomentsFormat
import java.util.Date

/**
 * Port de `Models/User.swift`.
 * Sin UserBadge / PlusSubscription / ProfileTheme (🚫 fuera de alcance Android).
 * Sí se porta el flag Firestore [isPlusSubscriber] (ads), no el subsistema de chapas.
 */

// MARK: - OnlineStatus

enum class OnlineStatus(val raw: String) {
    ONLINE("online"),
    AWAY("away"),
    BUSY("busy"),
    OFFLINE("offline"),
    INVISIBLE("invisible");

    /** ≡ iOS `OnlineStatus.icon` (SF Symbol; UI mapea a Material). */
    val iconName: String
        get() = when (this) {
            ONLINE -> "circle.fill"
            AWAY -> "moon.fill"
            BUSY -> "exclamationmark.circle.fill"
            OFFLINE -> "circle"
            INVISIBLE -> "eye.slash.fill"
        }

    /** ≡ iOS `OnlineStatus.color` (ARGB; UI aplica a Compose/View). */
    val colorArgb: Int
        get() = when (this) {
            ONLINE -> 0xFF4CAF50.toInt()
            AWAY -> 0xFFFF9800.toInt()
            BUSY -> 0xFFF44336.toInt()
            OFFLINE, INVISIBLE -> 0xFF9E9E9E.toInt()
        }

    companion object {
        fun from(raw: String?): OnlineStatus = entries.firstOrNull { it.raw == raw } ?: OFFLINE
    }
}

// MARK: - MessageRequestPolicy

enum class MessageRequestPolicy(val raw: String) {
    EVERYONE("everyone"),
    FOLLOWING("following"),
    NOBODY("nobody");

    companion object {
        fun from(raw: String?): MessageRequestPolicy =
            entries.firstOrNull { it.raw == raw } ?: EVERYONE
    }
}

// MARK: - PrivacyLevel

enum class PrivacyLevel {
    PUBLIC,
    RESTRICTED,
    PRIVATE,
    DEACTIVATED;

    /** ≡ iOS `PrivacyLevel.displayName` (textos fijos del Swift). */
    val displayName: String
        get() = when (this) {
            PUBLIC -> "Público"
            RESTRICTED -> "Restringido"
            PRIVATE -> "Privado"
            DEACTIVATED -> "Desactivado"
        }

    /** ≡ iOS `PrivacyLevel.description`. */
    val description: String
        get() = when (this) {
            PUBLIC -> "Cualquiera puede ver tu perfil y contenido"
            RESTRICTED -> "Tu perfil es público pero algunas opciones están restringidas"
            PRIVATE -> "Solo tus seguidores pueden ver tu contenido"
            DEACTIVATED -> "La cuenta está temporalmente desactivada"
        }
}

/**
 * Usuario de la app — ≡ [AppUser] de iOS.
 * Igualdad por `id`.
 */
data class AppUser(
    val id: String,
    val username: String = "Usuario Desconocido",
    val email: String = "",
    val interests: List<String> = emptyList(),
    /** Flag Firestore; no implica portar PlusSubscription / badges 🚫. */
    val isPlusSubscriber: Boolean = false,
    val profileImagePath: String? = null,
    val bio: String? = null,
    val blockedUsers: List<String> = emptyList(),
    val isPrivate: Boolean = false,
    val showMutuals: Boolean = true,
    val showFollowing: Boolean = true,
    val showFollowers: Boolean = true,
    val activeHoursStart: String? = null,
    val activeHoursEnd: String? = null,
    val notificationPreferences: Map<String, Boolean>? = null,
    val bestFriends: List<String> = emptyList(),
    val websiteUrl: String? = null,
    val profileNote: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val momentsCount: Int = 0,
    val isActive: Boolean = true,
    val deactivatedAt: Date? = null,
    val deactivatedBy: String? = null,
    val selectedProfileTheme: String? = null,
    val isVerified: Boolean = false,
    val onlineStatus: OnlineStatus = OnlineStatus.OFFLINE,
    val lastSeen: Date? = null,
    val isOnline: Boolean = false,
    val showReadReceipts: Boolean = true,
    val messageRequestPolicy: MessageRequestPolicy = MessageRequestPolicy.EVERYONE,
    val lastUsernameChange: Date? = null,
) {
    override fun equals(other: Any?): Boolean = other is AppUser && other.id == id
    override fun hashCode(): Int = id.hashCode()

    // MARK: - Seguimiento / privacidad (extensiones AppUser en iOS)

    val isPrivateAccount: Boolean get() = isPrivate

    val canLogin: Boolean get() = isActive

    /** ≡ iOS `deactivationInfo`. */
    fun deactivationInfo(): String? {
        if (isActive) return null
        val at = deactivatedAt ?: return null
        val formatted = MomentsFormat.smartDate(from = at, context = MomentsFormat.DateContext.MEDIUM_DATE_TIME)
        return "Cuenta desactivada el $formatted"
    }

    val daysSinceDeactivation: Int?
        get() = deactivatedAt?.let {
            ((System.currentTimeMillis() - it.time) / (24L * 3600 * 1000)).toInt()
        }

    /** Placeholder ≡ iOS (siempre false hasta cablear historias). */
    val hasActiveStory: Boolean get() = false

    fun hasMutualConnection(userId: String): Boolean = false

    fun canReceiveDirectMessages(from: String): Boolean {
        if (!isActive) return false
        if (blockedUsers.contains(from)) return false
        if (isPrivate) return hasMutualConnection(from)
        return true
    }

    fun canViewContent(from: String): Boolean {
        if (!isActive) return false
        if (blockedUsers.contains(from)) return false
        if (isPrivate) return hasMutualConnection(from)
        return true
    }

    fun canShowConnections(to: String): Boolean {
        if (!isActive) return false
        if (blockedUsers.contains(to)) return false
        if (isPrivate && !hasMutualConnection(to)) return false
        return showFollowing || showFollowers
    }

    val privacyLevel: PrivacyLevel
        get() = when {
            !isActive -> PrivacyLevel.DEACTIVATED
            isPrivate -> PrivacyLevel.PRIVATE
            !showFollowing || !showFollowers -> PrivacyLevel.RESTRICTED
            else -> PrivacyLevel.PUBLIC
        }

    val requiresFollowApproval: Boolean get() = isPrivate && isActive

    /** ≡ iOS `shouldHideAds` (flag básico; sin PlusSubscription 🚫). */
    val shouldHideAds: Boolean get() = isPlusSubscriber

    companion object {
        /** ≡ iOS `init(from decoder:)` / mapa Firestore. */
        fun from(id: String, data: Map<String, Any?>): AppUser = AppUser(
            id = id,
            username = data["username"] as? String ?: "Usuario Desconocido",
            email = data["email"] as? String ?: "",
            interests = (data["interests"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isPlusSubscriber = data["isPlusSubscriber"] as? Boolean ?: false,
            profileImagePath = data["profileImagePath"] as? String,
            bio = data["bio"] as? String,
            blockedUsers = (data["blockedUsers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            isPrivate = data["isPrivate"] as? Boolean ?: false,
            showMutuals = data["showMutuals"] as? Boolean ?: true,
            showFollowing = data["showFollowing"] as? Boolean ?: true,
            showFollowers = data["showFollowers"] as? Boolean ?: true,
            activeHoursStart = data["activeHoursStart"] as? String,
            activeHoursEnd = data["activeHoursEnd"] as? String,
            notificationPreferences = (data["notificationPreferences"] as? Map<*, *>)
                ?.entries?.mapNotNull { (k, v) -> if (k is String && v is Boolean) k to v else null }?.toMap(),
            bestFriends = (data["bestFriends"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            websiteUrl = data["websiteUrl"] as? String,
            profileNote = data["profileNote"] as? String,
            followersCount = (data["followersCount"] as? Number)?.toInt() ?: 0,
            followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
            momentsCount = (data["momentsCount"] as? Number)?.toInt() ?: 0,
            isActive = data["isActive"] as? Boolean ?: true,
            deactivatedAt = anyToDate(data["deactivatedAt"]),
            deactivatedBy = data["deactivatedBy"] as? String,
            selectedProfileTheme = data["selectedProfileTheme"] as? String,
            isVerified = data["isVerified"] as? Boolean ?: false,
            onlineStatus = OnlineStatus.from(data["onlineStatus"] as? String),
            lastSeen = anyToDate(data["lastSeen"]),
            isOnline = data["isOnline"] as? Boolean ?: false,
            showReadReceipts = data["showReadReceipts"] as? Boolean ?: true,
            messageRequestPolicy = MessageRequestPolicy.from(data["messageRequestPolicy"] as? String),
            lastUsernameChange = anyToDate(data["lastUsernameChange"]),
        )

        /** Timestamp | Date | Double epoch s (como Codable iOS en deactivatedAt). */
        private fun anyToDate(value: Any?): Date? = when (value) {
            is Timestamp -> value.toDate()
            is Date -> value
            is Number -> Date((value.toDouble() * 1000).toLong())
            else -> null
        }
    }
}

// MARK: - Serialización Firestore (sin ownedBadges / plusSubscription 🚫)

fun AppUser.toMap(): Map<String, Any> = buildMap {
    put("id", id)
    put("username", username)
    put("email", email)
    put("interests", interests)
    put("isPlusSubscriber", isPlusSubscriber)
    profileImagePath?.let { put("profileImagePath", it) }
    bio?.let { put("bio", it) }
    put("blockedUsers", blockedUsers)
    put("isPrivate", isPrivate)
    put("showMutuals", showMutuals)
    put("showFollowing", showFollowing)
    put("showFollowers", showFollowers)
    activeHoursStart?.let { put("activeHoursStart", it) }
    activeHoursEnd?.let { put("activeHoursEnd", it) }
    notificationPreferences?.let { put("notificationPreferences", it) }
    put("bestFriends", bestFriends)
    websiteUrl?.let { put("websiteUrl", it) }
    profileNote?.let { put("profileNote", it) }
    put("followersCount", followersCount)
    put("followingCount", followingCount)
    put("momentsCount", momentsCount)
    put("isActive", isActive)
    deactivatedAt?.let { put("deactivatedAt", Timestamp(it)) }
    deactivatedBy?.let { put("deactivatedBy", it) }
    selectedProfileTheme?.let { put("selectedProfileTheme", it) }
    put("isVerified", isVerified)
    put("onlineStatus", onlineStatus.raw)
    lastSeen?.let { put("lastSeen", Timestamp(it)) }
    put("isOnline", isOnline)
    put("showReadReceipts", showReadReceipts)
    put("messageRequestPolicy", messageRequestPolicy.raw)
    lastUsernameChange?.let { put("lastUsernameChange", Timestamp(it)) }
}
