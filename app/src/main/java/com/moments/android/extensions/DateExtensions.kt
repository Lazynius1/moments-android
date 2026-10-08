package com.moments.android.extensions

import com.moments.android.utilities.MomentsFormat
import java.util.Date

/**
 * Port de `Date+Extensions.swift`.
 * Delega en [MomentsFormat] para no duplicar la lógica de tiempos relativos.
 */
fun Date.timeAgoDisplay(): String = MomentsFormat.relativeTime(from = this)

/**
 * Relativo largo del sistema con una sola unidad: «hace 3 horas» (≡ iOS).
 * Sesiones de inicio, Nova y pantalla de actividad.
 */
fun Date.timeAgoLongDisplay(): String =
    MomentsFormat.relativeTime(from = this, style = MomentsFormat.RelativeTimeStyle.CONVERSATIONAL)
