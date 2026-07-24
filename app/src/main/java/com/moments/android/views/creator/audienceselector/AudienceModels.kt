package com.moments.android.views.creator.audienceselector

import com.google.firebase.Timestamp
import com.moments.android.models.MediaItem
import java.util.Date

/**
 * Port de `AudienceModels.swift`.
 *
 * Los textos permanecen como claves para que la capa Compose los resuelva con
 * sus recursos localizados, igual que `NSLocalizedString` en iOS.
 */
enum class ContentAudience(val raw: String) {
    EVERYONE("everyone"),
    MUTUALS("mutuals"),
    BEST_FRIENDS("bestFriends"),
    CUSTOM("custom"),
    CUSTOM_LIST("customList"),
    ONLY_ME("onlyMe");

    val titleKey: String
        get() = when (this) {
            EVERYONE -> "audience.type.everyone"
            MUTUALS -> "audience.type.mutuals"
            BEST_FRIENDS -> "audience.type.bestFriends"
            CUSTOM -> "audience.type.custom"
            CUSTOM_LIST -> "audience.type.customList"
            ONLY_ME -> "audience.type.onlyMe"
        }

    /** Fallback en inglés; la pantalla usa recursos Android para la localización. */
    val title: String
        get() = when (this) {
            EVERYONE -> "Everyone"
            MUTUALS -> "Mutuals"
            BEST_FRIENDS -> "Best friends"
            CUSTOM -> "Custom"
            CUSTOM_LIST -> "Custom list"
            ONLY_ME -> "Only me"
        }

    val descriptionKey: String
        get() = when (this) {
            EVERYONE -> "audience.description.everyone"
            MUTUALS -> "audience.description.mutuals"
            BEST_FRIENDS -> "audience.description.bestFriends"
            CUSTOM -> "audience.description.custom"
            CUSTOM_LIST -> "audience.description.customList"
            ONLY_ME -> "audience.description.onlyMe"
        }

    /** Fallback en inglés equivalente a los valores de `Localizable.strings`. */
    val description: String
        get() = when (this) {
            EVERYONE -> "Anyone can see this content"
            MUTUALS -> "Only people you follow and follow you"
            BEST_FRIENDS -> "Only your best friends list"
            CUSTOM -> "Choose specific people"
            CUSTOM_LIST -> "Use a custom list"
            ONLY_ME -> "Private content, only you can see it"
        }

    val icon: String
        get() = when (this) {
            EVERYONE -> "globe"
            MUTUALS -> "person.2.fill"
            BEST_FRIENDS -> "star.fill"
            CUSTOM -> "person.crop.circle.badge.plus"
            CUSTOM_LIST -> "list.bullet.rectangle"
            ONLY_ME -> "lock.fill"
        }

    val assetName: String
        get() = when (this) {
            EVERYONE -> "AudienceEveryoneIcon"
            MUTUALS -> "AudienceMutualsIcon"
            BEST_FRIENDS -> "AudienceBestFriendsIcon"
            CUSTOM -> "AudienceCustomIcon"
            CUSTOM_LIST -> "AudienceCustomListIcon"
            ONLY_ME -> "AudienceOnlyMeIcon"
        }

    companion object {
        fun fromCaptionAudienceSetting(setting: ContentAudience, hasCustomList: Boolean): ContentAudience =
            if (setting == CUSTOM && hasCustomList) CUSTOM_LIST else setting

        fun fromAudienceValue(value: String?): ContentAudience {
            return when (value?.trim()?.lowercase() ?: EVERYONE.raw) {
                "mutuals", "mutual" -> MUTUALS
                "bestfriends", "best_friends", "best-friends" -> BEST_FRIENDS
                "customlist" -> CUSTOM_LIST
                "custom" -> CUSTOM
                "onlyme", "only_me", "only-me" -> ONLY_ME
                else -> EVERYONE
            }
        }

        /** Alias de compatibilidad con los consumidores Android previos. */
        fun from(raw: String?): ContentAudience = fromAudienceValue(raw)
    }
}

/** Modelo Firestore de una lista de audiencia; igualdad por id como en Swift. */
data class CustomAudienceList(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val members: List<String> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val color: String? = null,
    val icon: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is CustomAudienceList && id == other.id

    override fun hashCode(): Int = id?.hashCode() ?: 0

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "description" to description,
        "members" to members,
        "createdAt" to Timestamp(createdAt),
        "updatedAt" to Timestamp(updatedAt),
        "color" to color,
        "icon" to icon,
    )

    companion object {
        val predefinedColors = listOf(
            "FF6B6B", "4ECDC4", "45B7D1", "FFA07A",
            "98D8C8", "F7DC6F", "BB8FCE", "85C1E2",
        )
        val predefinedIcons = listOf(
            "person.3.fill", "briefcase.fill", "house.fill", "graduationcap.fill",
            "heart.fill", "star.fill", "flag.fill", "bolt.fill",
        )

        fun from(id: String?, data: Map<String, Any?>): CustomAudienceList? {
            val name = data["name"] as? String ?: return null
            return CustomAudienceList(
                id = id ?: data["id"] as? String,
                name = name,
                description = data["description"] as? String,
                members = (data["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = MediaItem.anyToDate(data["createdAt"]) ?: Date(),
                updatedAt = MediaItem.anyToDate(data["updatedAt"]) ?: Date(),
                color = data["color"] as? String,
                icon = data["icon"] as? String,
            )
        }
    }
}
