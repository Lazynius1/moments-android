package com.moments.android.models.cache

import com.moments.android.models.AppUser
import org.json.JSONArray
import org.json.JSONObject
import java.util.Arrays
import java.util.Date

/**
 * Port de `Models/Cache/CachedUser.swift`.
 * Espejo ligero de [AppUser] para offline.
 * Sin Plus/badges (UserBadge 🚫 en Android).
 */
data class CachedUser(
    val userId: String,
    val username: String,
    val email: String = "",
    val bio: String? = null,
    val profileImagePath: String? = null,
    val websiteUrl: String? = null,
    val profileNote: String? = null,
    val followersCount: Int? = 0,
    val followingCount: Int? = 0,
    val momentsCount: Int? = 0,
    val isVerified: Boolean? = false,
    val isPrivate: Boolean? = false,
    val isActive: Boolean? = true,
    val showMutuals: Boolean? = true,
    val showFollowing: Boolean? = true,
    val showFollowers: Boolean? = true,
    val showReadReceipts: Boolean? = true,
    val selectedProfileTheme: String? = null,
    val interestsData: ByteArray? = null,
    val blockedUsersData: ByteArray? = null,
    val bestFriendsData: ByteArray? = null,
    val lastSyncedAt: Date = Date(),
    /** `"currentUser"` | `"profile"` | `"explore"` */
    val cacheSection: String = "profile",
) {
    /** ≡ iOS `toAppUser()`. */
    fun toAppUser(): AppUser = AppUser(
        id = userId,
        username = username,
        email = email,
        interests = decodeStringList(interestsData),
        profileImagePath = profileImagePath,
        bio = bio,
        blockedUsers = decodeStringList(blockedUsersData),
        isPrivate = isPrivate ?: false,
        showMutuals = showMutuals ?: true,
        showFollowing = showFollowing ?: true,
        showFollowers = showFollowers ?: true,
        activeHoursStart = null,
        activeHoursEnd = null,
        notificationPreferences = null,
        bestFriends = decodeStringList(bestFriendsData),
        websiteUrl = websiteUrl,
        profileNote = profileNote,
        followersCount = followersCount ?: 0,
        followingCount = followingCount ?: 0,
        momentsCount = momentsCount ?: 0,
        isActive = isActive ?: true,
        selectedProfileTheme = selectedProfileTheme,
        isVerified = isVerified ?: false,
        showReadReceipts = showReadReceipts ?: true,
    )

    /** Serialización prefs (clave `id` por compatibilidad con caché existente). */
    fun encodeToPrefsJson(): String = JSONObject().apply {
        put("id", userId)
        put("username", username)
        put("email", email)
        put("bio", bio)
        put("profileImagePath", profileImagePath)
        put("websiteUrl", websiteUrl)
        put("profileNote", profileNote)
        put("followersCount", followersCount)
        put("followingCount", followingCount)
        put("momentsCount", momentsCount)
        put("isVerified", isVerified)
        put("isPrivate", isPrivate)
        put("isActive", isActive)
        put("showMutuals", showMutuals)
        put("showFollowing", showFollowing)
        put("showFollowers", showFollowers)
        put("showReadReceipts", showReadReceipts)
        put("selectedProfileTheme", selectedProfileTheme)
        put("interests", JSONArray(decodeStringList(interestsData)))
        put("blockedUsers", JSONArray(decodeStringList(blockedUsersData)))
        put("bestFriends", JSONArray(decodeStringList(bestFriendsData)))
        put("cacheSection", cacheSection)
        put("lastSyncedAt", lastSyncedAt.time)
    }.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedUser) return false
        return userId == other.userId &&
            username == other.username &&
            email == other.email &&
            bio == other.bio &&
            profileImagePath == other.profileImagePath &&
            websiteUrl == other.websiteUrl &&
            profileNote == other.profileNote &&
            followersCount == other.followersCount &&
            followingCount == other.followingCount &&
            momentsCount == other.momentsCount &&
            isVerified == other.isVerified &&
            isPrivate == other.isPrivate &&
            isActive == other.isActive &&
            showMutuals == other.showMutuals &&
            showFollowing == other.showFollowing &&
            showFollowers == other.showFollowers &&
            showReadReceipts == other.showReadReceipts &&
            selectedProfileTheme == other.selectedProfileTheme &&
            Arrays.equals(interestsData, other.interestsData) &&
            Arrays.equals(blockedUsersData, other.blockedUsersData) &&
            Arrays.equals(bestFriendsData, other.bestFriendsData) &&
            lastSyncedAt == other.lastSyncedAt &&
            cacheSection == other.cacheSection
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + (bio?.hashCode() ?: 0)
        result = 31 * result + (profileImagePath?.hashCode() ?: 0)
        result = 31 * result + (websiteUrl?.hashCode() ?: 0)
        result = 31 * result + (profileNote?.hashCode() ?: 0)
        result = 31 * result + (followersCount ?: 0)
        result = 31 * result + (followingCount ?: 0)
        result = 31 * result + (momentsCount ?: 0)
        result = 31 * result + (isVerified?.hashCode() ?: 0)
        result = 31 * result + (isPrivate?.hashCode() ?: 0)
        result = 31 * result + (isActive?.hashCode() ?: 0)
        result = 31 * result + (showMutuals?.hashCode() ?: 0)
        result = 31 * result + (showFollowing?.hashCode() ?: 0)
        result = 31 * result + (showFollowers?.hashCode() ?: 0)
        result = 31 * result + (showReadReceipts?.hashCode() ?: 0)
        result = 31 * result + (selectedProfileTheme?.hashCode() ?: 0)
        result = 31 * result + (interestsData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (blockedUsersData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (bestFriendsData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + lastSyncedAt.hashCode()
        result = 31 * result + cacheSection.hashCode()
        return result
    }

    companion object {
        /** ≡ iOS `CachedUser.from(_:section:)`. */
        fun from(user: AppUser, section: String = "profile"): CachedUser = CachedUser(
            userId = user.id,
            username = user.username,
            email = user.email,
            bio = user.bio,
            profileImagePath = user.profileImagePath,
            websiteUrl = user.websiteUrl,
            profileNote = user.profileNote,
            isVerified = user.isVerified,
            isPrivate = user.isPrivate,
            isActive = user.isActive,
            showMutuals = user.showMutuals,
            showFollowing = user.showFollowing,
            showFollowers = user.showFollowers,
            showReadReceipts = user.showReadReceipts,
            selectedProfileTheme = user.selectedProfileTheme,
            followersCount = user.followersCount,
            followingCount = user.followingCount,
            momentsCount = user.momentsCount,
            interestsData = encodeStringList(user.interests),
            blockedUsersData = encodeStringList(user.blockedUsers),
            bestFriendsData = encodeStringList(user.bestFriends),
            lastSyncedAt = Date(),
            cacheSection = section,
        )

        fun decodeFromPrefsJson(raw: String): CachedUser? = runCatching {
            val json = JSONObject(raw)
            CachedUser(
                userId = json.getString("id"),
                username = json.optString("username", "Usuario Desconocido"),
                email = json.optString("email"),
                bio = json.stringOrNull("bio"),
                profileImagePath = json.stringOrNull("profileImagePath"),
                websiteUrl = json.stringOrNull("websiteUrl"),
                profileNote = json.stringOrNull("profileNote"),
                followersCount = json.optInt("followersCount"),
                followingCount = json.optInt("followingCount"),
                momentsCount = json.optInt("momentsCount"),
                isVerified = json.optBoolean("isVerified", false),
                isPrivate = json.optBoolean("isPrivate", false),
                isActive = json.optBoolean("isActive", true),
                showMutuals = json.optBoolean("showMutuals", true),
                showFollowing = json.optBoolean("showFollowing", true),
                showFollowers = json.optBoolean("showFollowers", true),
                showReadReceipts = json.optBoolean("showReadReceipts", true),
                selectedProfileTheme = json.stringOrNull("selectedProfileTheme"),
                interestsData = encodeStringList(json.optJSONArray("interests").toStringList()),
                blockedUsersData = encodeStringList(json.optJSONArray("blockedUsers").toStringList()),
                bestFriendsData = encodeStringList(json.optJSONArray("bestFriends").toStringList()),
                lastSyncedAt = Date(json.optLong("lastSyncedAt", System.currentTimeMillis())),
                cacheSection = json.optString("cacheSection", "profile"),
            )
        }.getOrNull()

        private fun encodeStringList(list: List<String>): ByteArray =
            JSONArray(list).toString().toByteArray()

        private fun decodeStringList(data: ByteArray?): List<String> {
            if (data == null) return emptyList()
            return runCatching {
                val arr = JSONArray(String(data))
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }.getOrDefault(emptyList())
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
        }

        private fun JSONObject.stringOrNull(name: String): String? = when (val value = opt(name)) {
            null, JSONObject.NULL -> null
            is String -> value.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
            else -> null
        }
    }
}
