package com.moments.android.services.performance

import android.os.Trace

/**
 * Port de `PerformanceSignposts.swift`.
 * `OSSignpost` → `android.os.Trace` (systrace / Android Studio Profiler).
 */
object PerformanceSignposts {
    fun begin(name: String) {
        Trace.beginSection(name.take(127))
    }

    fun end(@Suppress("UNUSED_PARAMETER") name: String) {
        Trace.endSection()
    }

    fun event(name: String) {
        Trace.beginSection(name.take(127))
        Trace.endSection()
    }

    /** Port de `makeID()` — Trace es LIFO por hilo; el id es reservado para callers iOS. */
    fun makeID(): Long = System.nanoTime()
}
