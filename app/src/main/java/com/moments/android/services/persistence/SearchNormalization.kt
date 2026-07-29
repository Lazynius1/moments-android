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

    /**
     * Índice en [haystack] del primer match diacritic+case insensitive de [needle].
     * ≡ iOS `range(of:options: [.caseInsensitive, .diacriticInsensitive])`.
     */
    fun indexOfDiacriticInsensitive(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        val (folded, indexMap) = foldWithIndexMap(haystack)
        val foldedNeedle = foldCodePoint(needle)
        if (foldedNeedle.isEmpty()) return -1
        val idx = folded.indexOf(foldedNeedle)
        if (idx < 0 || idx >= indexMap.size) return -1
        return indexMap[idx]
    }

    private fun foldCodePoint(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.getDefault())

    private fun foldWithIndexMap(value: String): Pair<String, IntArray> {
        val folded = StringBuilder()
        val map = ArrayList<Int>(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val base = foldCodePoint(ch)
            for (ignored in base) {
                folded.append(ignored)
                map.add(i)
            }
            i += Character.charCount(cp)
        }
        return folded.toString() to map.toIntArray()
    }
}
