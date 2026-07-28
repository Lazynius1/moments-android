package com.moments.android.extensions

import org.json.JSONObject

/**
 * `JSONObject.optString(name)` de `org.json` devuelve el literal **"null"** cuando la clave
 * existe con valor JSON `null` (no "" ni null de Kotlin). Encadenar `.takeIf { it.isNotBlank() }`
 * no lo filtra, así que el campo acaba valiendo la cadena "null" y cualquier chequeo de
 * "¿tiene valor?" da true.
 *
 * Usar esto en vez de `optString(name).takeIf { it.isNotBlank() }`.
 */
fun JSONObject.optStringOrNull(name: String): String? {
    if (isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}
