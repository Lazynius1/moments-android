package com.moments.android.utilities

import android.content.Context
import android.util.TypedValue

private const val LEGACY_POPPINS_SCALE = 0.94f

/**
 * Escala tipográfica legacy con font scale del sistema vía [TypedValue].
 */
fun legacyPoppinsSize(context: Context, sizeSp: Float): Float {
    val base = kotlin.math.round(sizeSp * LEGACY_POPPINS_SCALE)
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        base,
        context.resources.displayMetrics,
    )
}

fun legacyPoppinsSize(context: Context, sizeSp: Int): Float =
    legacyPoppinsSize(context, sizeSp.toFloat())
