package com.moments.android.utilities

import android.util.Log
import com.moments.android.BuildConfig

/**
 * Logging ligero para hot-paths. En Release [debug] es no-op.
 * [error] se conserva también en Release vía [Log].
 */
object AppLog {
    private const val TAG = "Moments"

    /** Solo se emite en builds DEBUG. No-op en Release. */
    fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    /** Errores relevantes que se conservan también en Release. */
    fun error(message: String) {
        Log.e(TAG, message)
    }
}
