package com.moments.android.models

import android.content.res.Resources
import androidx.annotation.StringRes
import com.moments.android.R

/**
 * Port de `Models/InterestModels.swift`.
 * Keys EXACTOS de Firestore (español); [labelRes] localiza. `UNKNOWN.raw` ≡ `"unknown"`.
 */
enum class InterestOption(
    val raw: String,
    /** `0` solo en [UNKNOWN] (localizedName vacío, como iOS). */
    @StringRes val labelRes: Int,
) {
    PHOTOGRAPHY("Fotografía", R.string.interest_photography),
    TRAVEL("Viajes", R.string.interest_travel),
    MUSIC("Música", R.string.interest_music),
    MOVIES("Cine", R.string.interest_movies),
    ART("Arte", R.string.interest_art),
    SPORTS("Deportes", R.string.interest_sports),
    BOOKS("Libros", R.string.interest_books),
    COOKING("Cocina", R.string.interest_cooking),
    TECHNOLOGY("Tecnología", R.string.interest_technology),
    FASHION("Moda", R.string.interest_fashion),
    GAMING("Gaming", R.string.interest_gaming),
    FITNESS("Fitness", R.string.interest_fitness),
    NATURE("Naturaleza", R.string.interest_nature),
    ANIMALS("Animales", R.string.interest_animals),
    FOOD("Comida", R.string.interest_food),
    SCIENCE("Ciencia", R.string.interest_science),
    HISTORY("Historia", R.string.interest_history),
    POLITICS("Política", R.string.interest_politics),
    BUSINESS("Negocios", R.string.interest_business),
    HEALTH("Salud", R.string.interest_health),
    STYLE("Estilo", R.string.interest_style),
    DANCE("Baile", R.string.interest_dance),
    WRITING("Escritura", R.string.interest_writing),
    DIY("DIY", R.string.interest_diy),
    CARS("Coches", R.string.interest_cars),
    UNKNOWN("unknown", 0);

    val id: String get() = raw

    companion object {
        /** ≡ `InterestOption(rawValue:)` — null si no hay caso (interés custom). */
        fun fromRaw(key: String): InterestOption? =
            entries.firstOrNull { it.raw == key }

        /**
         * ≡ iOS `InterestOption.localize(_:)`.
         * Match → localizedName (`unknown` → `""`); sin match → la key original.
         */
        fun localize(key: String, resources: Resources): String {
            val option = fromRaw(key) ?: return key
            if (option.labelRes == 0) return ""
            return resources.getString(option.labelRes)
        }

        /** CaseIterable sin `unknown` (onboarding / pickers). */
        val allKnown: List<InterestOption> = entries.filter { it != UNKNOWN }
    }
}
