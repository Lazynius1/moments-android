package com.moments.android.models.cache

import java.util.Date

/**
 * Usuario cacheado localmente (espejo ligero de AppUser). Sin campos de badges/Plus
 * (descartados en el proyecto). Room + conversiones AppUser↔CachedUser al montar la caché.
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
    val interestsData: ByteArray? = null,     // [String] JSON
    val blockedUsersData: ByteArray? = null,  // [String] JSON
    val bestFriendsData: ByteArray? = null,   // [String] JSON
    val lastSyncedAt: Date = Date(),
    val cacheSection: String = "profile", // "currentUser" | "profile" | "explore"
)
