package com.moments.android.debugscreenshots

import org.json.JSONArray
import org.json.JSONObject

/**
 * Textos de demo de `assets/screenshots/ScreenshotCopy.json` (solo debug) para el idioma
 * actual de la app; cae a inglés si falta algo. Android usa `in` (indonesio) y
 * `zh-rTW` (chino tradicional): se traducen a las claves del JSON.
 */
object ScreenshotCopy {
    private val table: JSONObject by lazy {
        runCatching {
            ScreenshotMode.appContext.assets.open("screenshots/ScreenshotCopy.json")
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
        }.getOrDefault(JSONObject())
    }

    /** Clave del JSON para el idioma con el que se resolvieron los recursos. */
    val lang: String by lazy {
        val locale = runCatching { ScreenshotMode.appContext.resources.configuration.locales[0] }.getOrNull()
        val language = locale?.language.orEmpty()
        when (language) {
            "" -> "en"
            "in", "id" -> "id"
            "zh" -> "zh-Hant"
            "pt" -> if (locale?.country.equals("PT", ignoreCase = true)) "pt-PT" else "pt-BR"
            else -> language
        }
    }

    private val languageChain: List<String> by lazy {
        listOf(lang, lang.substringBefore('-'), "en").distinct()
    }

    private fun lookup(path: String, language: String): Any? {
        var node: Any? = table.opt(language)
        for (part in path.split('.')) {
            node = (node as? JSONObject)?.opt(part)
        }
        return node
    }

    private fun resolve(path: String): Any? =
        languageChain.firstNotNullOfOrNull { lookup(path, it) }

    fun text(path: String): String = (resolve(path) as? String).orEmpty()

    fun list(path: String): List<String> {
        val array = resolve(path) as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.opt(it) as? String }
    }

    fun entries(path: String): List<Map<String, String>> {
        val array = resolve(path) as? JSONArray ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.opt(index) as? JSONObject ?: return@mapNotNull null
            item.keys().asSequence().associateWith { key -> item.optString(key) }
        }
    }
}
