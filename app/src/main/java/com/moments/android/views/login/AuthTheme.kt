package com.moments.android.views.login

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.views.shared.Ink
import com.moments.android.views.shared.Surface

/**
 * Espejo de AuthColors.swift: texto siempre sobre el fondo claro paper ([Surface]).
 * Tokens compartidos con [com.moments.android.views.shared.MomentsTheme].
 */
object AuthColors {
    val canvas: Color = Surface
    val primary: Color = Ink
    fun secondary(alpha: Float = 0.68f): Color = Ink.copy(alpha = alpha)
    fun subtle(alpha: Float = 0.12f): Color = Ink.copy(alpha = alpha)
}

/** Espejo de AuthFormMetrics.swift. */
object AuthMetrics {
    val maxFormContentWidth = 340.dp
    val fieldHeight = 44.dp
    val buttonHeight = 46.dp
    val fieldCornerRadius = 14.dp
    val buttonCornerRadius = 14.dp
    val fieldHorizontalPadding = 16.dp
    val iconSlotWidth = 20.dp
    val fieldFontSize = 15.sp
    val buttonFontSize = 15.sp
}
