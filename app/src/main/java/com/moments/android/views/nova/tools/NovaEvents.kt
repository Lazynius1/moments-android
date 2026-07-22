package com.moments.android.views.nova.tools

/**
 * Stub mínimo de NovaEvents para Echo sparks hasta portar Views/Nova.
 * Espejo de NovaEvents.triggerEchoSpark en NovaActivityTools.swift.
 */
object NovaEvents {
    fun triggerEchoSpark(echoId: String, userId: String) {
        // Nova UI / tools aún no portados; no-op seguro (iOS dispara spark proactivo).
        android.util.Log.d("NovaEvents", "triggerEchoSpark echoId=$echoId userId=$userId")
    }
}
