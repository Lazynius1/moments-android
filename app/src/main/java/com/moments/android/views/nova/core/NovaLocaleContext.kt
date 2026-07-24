package com.moments.android.views.nova.novacore

import java.util.Locale

/** App locale metadata for Nova session context — not used for LLM prompts. */
object NovaLocaleContext {
    val appLocaleIdentifier: String
        get() = Locale.getDefault().toLanguageTag()

    val appLanguageCode: String
        get() = Locale.getDefault().language.ifBlank { "en" }
}
