package com.moments.android.extensions

import com.moments.android.utilities.MomentsFormat
import java.util.Date

/**
 * Port de `Date+Extensions.swift`.
 * Delega en [MomentsFormat] para no duplicar la lógica de tiempos relativos.
 */
fun Date.timeAgoDisplay(): String = MomentsFormat.relativeTime(from = this)
