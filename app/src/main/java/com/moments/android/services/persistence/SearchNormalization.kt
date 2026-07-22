package com.moments.android.services.persistence

import java.text.Normalizer
import java.util.Locale

/**
 * Paridad con iOS `String.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)`
 * y `localizedStandardContains` en búsqueda de mensajes cacheados.
 */
object SearchNormalization {

    fun normalizeForSearch(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return decomposed.lowercase(Locale.getDefault())
    }

    fun containsNormalized(haystack: String, normalizedNeedle: String): Boolean {
        if (normalizedNeedle.isEmpty()) return false
        return normalizeForSearch(haystack).contains(normalizedNeedle)
    }
}
