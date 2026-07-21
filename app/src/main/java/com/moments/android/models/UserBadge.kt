package com.moments.android.models

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Date
import java.util.UUID

// Colores de sistema de iOS (SwiftUI .red/.blue/…), para fidelidad de badges/niveles.
internal object SystemColors {
    val red = Color(0xFFFF3B30)
    val pink = Color(0xFFFF2D55)
    val blue = Color(0xFF007AFF)
    val purple = Color(0xFFAF52DE)
    val yellow = Color(0xFFFFCC00)
    val orange = Color(0xFFFF9500)
    val indigo = Color(0xFF5856D6)
    val gray = Color(0xFF8E8E93)
}

internal fun colorFromHex(hex: String): Color? = runCatching {
    val clean = hex.removePrefix("#")
    val value = clean.toLong(16)
    when (clean.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}.getOrNull()

internal fun Color.toHexString(): String {
    fun comp(c: Float) = (c * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${comp(red)}${comp(green)}${comp(blue)}".uppercase()
}

// MARK: - Badge del usuario (equivalente a UserBadge de iOS)
data class UserBadge(
    val id: String = UUID.randomUUID().toString(),
    val badgeId: String,
    val name: String,
    val emoji: String,
    val colors: List<String>, // hex para serialización
    val purchaseDate: Date = Date(),
    val isVisible: Boolean = true,
    val price: String,
) {
    val composeColors: List<Color> get() = colors.mapNotNull { colorFromHex(it) }

    companion object {
        /** Constructor a partir de Colors (equivalente al init(colors: [Color]) de iOS). */
        fun of(
            badgeId: String,
            name: String,
            emoji: String,
            colors: List<Color>,
            purchaseDate: Date = Date(),
            isVisible: Boolean = true,
            price: String,
            id: String = UUID.randomUUID().toString(),
        ) = UserBadge(id, badgeId, name, emoji, colors.map { it.toHexString() }, purchaseDate, isVisible, price)
    }
}

// MARK: - Suscripción Plus (equivalente a PlusSubscription de iOS)
data class PlusSubscription(
    val isActive: Boolean = false,
    val startDate: Date? = null,
    val expiryDate: Date? = null,
    val autoRenew: Boolean = false,
    val plan: String = "", // "monthly", "yearly"
) {
    val isExpired: Boolean get() = expiryDate?.let { Date().after(it) } ?: false

    val daysRemaining: Int? get() = expiryDate?.let {
        ((it.time - System.currentTimeMillis()) / (24L * 3600 * 1000)).toInt()
    }
}

// MARK: - Niveles de supporter (equivalente a SupporterLevel de iOS)
enum class SupporterLevel(val raw: String) {
    NONE("none"),
    SUPPORTER("supporter"),
    EARLY_ADOPTER("early_adopter"),
    CHAMPION("champion"),
    VIP("vip");

    // Nombres de marca (no traducidos); "Usuario" para none se localiza en UI.
    val displayName: String
        get() = when (this) {
            NONE -> "Usuario"
            SUPPORTER -> "Supporter"
            EARLY_ADOPTER -> "Early Adopter"
            CHAMPION -> "Champion"
            VIP -> "VIP"
        }

    val emoji: String
        get() = when (this) {
            NONE -> "👤"
            SUPPORTER -> "❤️"
            EARLY_ADOPTER -> "🚀"
            CHAMPION -> "🏆"
            VIP -> "💎"
        }

    val colors: List<Color>
        get() = when (this) {
            NONE -> listOf(SystemColors.gray)
            SUPPORTER -> listOf(SystemColors.red, SystemColors.pink)
            EARLY_ADOPTER -> listOf(SystemColors.blue, SystemColors.purple)
            CHAMPION -> listOf(SystemColors.yellow, SystemColors.orange)
            VIP -> listOf(SystemColors.purple, SystemColors.indigo)
        }

    companion object {
        fun from(raw: String?): SupporterLevel = entries.firstOrNull { it.raw == raw } ?: NONE
    }

    // NOTA: `profileTheme` (SupporterLevel → ProfileTheme) se añadirá al portar
    // Views/Profile/Theme/ProfileTheme.swift, del que depende AppUser.currentProfileTheme.
}

// MARK: - Badge de tienda (equivalente a Badge de iOS)
data class Badge(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val price: String,
    val colors: List<Color>,
    val productId: String,
) {
    companion object {
        val supportBadges: List<Badge> = listOf(
            Badge("supporter", "Supporter", "❤️", "Apoya el proyecto", "€2.99", listOf(SystemColors.red, SystemColors.pink), "com.moments.badge.supporter"),
            Badge("early_adopter", "Early Adopter", "🚀", "Usuario pionero", "€4.99", listOf(SystemColors.blue, SystemColors.purple), "com.moments.badge.early_adopter"),
            Badge("champion", "Champion", "🏆", "Campeón de la comunidad", "€7.99", listOf(SystemColors.yellow, SystemColors.orange), "com.moments.badge.champion"),
            Badge("vip", "VIP", "💎", "Badge premium", "€9.99", listOf(SystemColors.purple, SystemColors.indigo), "com.moments.badge.vip"),
        )
    }
}
