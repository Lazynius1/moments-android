package com.moments.android.extensions

import androidx.annotation.StringRes
import com.moments.android.R

/**
 * Port de `Extensions/InterestEmojiHelper.swift`.
 *
 * [firestoreRaw] en español = valor persistido en Firestore (igual que iOS).
 * [labelRes] = UI localizada (≡ NSLocalizedString interest.*).
 */
object InterestEmojiHelper {

    data class SupportedInterest(
        val firestoreRaw: String,
        @StringRes val labelRes: Int,
        val emoji: String,
    )

    /** ≡ emoji(for:) — fallback ✨ */
    fun emoji(interest: String): String = emojiFor(interest)

    fun emojiFor(interest: String): String {
        return when (interest.lowercase()) {
            // Arte & Cultura
            "escritura", "writing" -> "✍️"
            "cine", "movies" -> "🎬"
            "libros", "books", "lectura", "reading" -> "📚"
            "teatro", "theater" -> "🎭"
            "arte", "art" -> "🎨"
            "diseño", "design" -> "🎨"
            "baile", "dance" -> "💃"

            // Bienestar & Salud
            "meditación", "meditation" -> "🕯️"
            "yoga" -> "🧘"
            "fitness" -> "💪"
            "deportes", "sports" -> "⚽"

            // Naturaleza & Vida
            "naturaleza", "nature" -> "🌿"
            "fotografía", "photography" -> "📸"
            "mascotas", "pets" -> "🐾"
            "astronomía", "astronomy" -> "⭐"

            // Estilo & Lifestyle
            "moda", "fashion" -> "👗"
            "café", "coffee" -> "☕"
            "cocina", "cooking" -> "👨‍🍳"
            "viajar", "travel" -> "✈️"

            // Tecnología & Entretenimiento
            "gaming", "gamer" -> "🎮"
            "tecnología", "technology" -> "💻"
            "programación", "programming", "programacion" -> "💻"
            "podcasts" -> "🎧"
            "kpop", "k-pop" -> "🎤"

            // Negocios
            "emprendimiento", "entrepreneurship" -> "🚀"

            // Música
            "música", "music" -> "🎵"

            else -> "✨"
        }
    }

    /** ≡ supportedInterests — orden y emojis 1:1 con iOS */
    val supportedInterests: List<SupportedInterest> = listOf(
        SupportedInterest("Escritura", R.string.interest_writing, "✍️"),
        SupportedInterest("Cine", R.string.interest_movies, "🎬"),
        SupportedInterest("Libros", R.string.interest_books, "📚"),
        SupportedInterest("Teatro", R.string.interest_theater, "🎭"),
        SupportedInterest("Meditación", R.string.interest_meditation, "🕯️"),
        SupportedInterest("Naturaleza", R.string.interest_nature, "🌿"),
        SupportedInterest("Fotografía", R.string.interest_photography, "📸"),
        SupportedInterest("Moda", R.string.interest_fashion, "👗"),
        SupportedInterest("Emprendimiento", R.string.interest_entrepreneurship, "🚀"),
        SupportedInterest("Yoga", R.string.interest_yoga, "🧘"),
        SupportedInterest("Historia", R.string.interest_history, "📜"),
        SupportedInterest("Café", R.string.interest_coffee, "☕"),
        SupportedInterest("Fitness", R.string.interest_fitness, "💪"),
        SupportedInterest("Música", R.string.interest_music, "🎵"),
        SupportedInterest("Gaming", R.string.interest_gaming, "🎮"),
        SupportedInterest("Tecnología", R.string.interest_technology, "💻"),
        SupportedInterest("Viajar", R.string.interest_viajar, "✈️"),
        SupportedInterest("Astronomía", R.string.interest_astronomy, "⭐"),
        SupportedInterest("Podcasts", R.string.interest_podcasts, "🎧"),
        SupportedInterest("Deportes", R.string.interest_sports, "⚽"),
        SupportedInterest("Cocina", R.string.interest_cooking, "👨‍🍳"),
        SupportedInterest("Mascotas", R.string.interest_pets, "🐾"),
        SupportedInterest("Diseño", R.string.interest_design, "🎨"),
        SupportedInterest("Baile", R.string.interest_dance, "💃"),
        SupportedInterest("Programación", R.string.interest_programming, "💻"),
        SupportedInterest("Kpop", R.string.interest_kpop, "🎤"),
    )

    /** ≡ randomInterest() */
    fun randomInterest(): SupportedInterest =
        supportedInterests.randomOrNull()
            ?: SupportedInterest("Interés", R.string.interest_writing, "✨")
}
