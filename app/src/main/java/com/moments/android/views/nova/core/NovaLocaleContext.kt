package com.moments.android.views.nova.core

import java.util.Locale

/**
 * Port de `Views/Nova/Core/NovaLocaleContext.swift`.
 * Locale de la app para el contexto de sesión de Nova — no se usa en prompts LLM.
 */
object NovaLocaleContext {
    val appLocaleIdentifier: String
        get() = Locale.getDefault().toLanguageTag()

    val appLanguageCode: String
        get() = Locale.getDefault().language.ifBlank { "en" }
}
