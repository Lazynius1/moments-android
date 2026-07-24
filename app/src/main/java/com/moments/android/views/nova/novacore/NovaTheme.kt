package com.moments.android.views.nova.novacore

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.BuildConfig
import com.moments.android.R

object NovaColors {
    val primary = Color(0xFF00A896)
    val secondary = Color(0xFF6B73FF)
    val accent = Color(0xFF9B59B6)

    val background: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val secondaryBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    val cardBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
    val materialBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val textPrimary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White else Color.Black
    val textSecondary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.60f)
    val textTertiary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.30f)
    val borderColor: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f)
    val shadowColor: Color
        @Composable get() = textPrimary.copy(alpha = 0.10f)
}

@Composable
fun NovaBrandIcon(size: Dp = 22.dp, color: Color = NovaColors.textPrimary, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.nova_tab_icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size),
    )
}

object LogConfig {
    private const val TAG = "Moments"
    val isVerboseLogging: Boolean get() = BuildConfig.DEBUG

    fun log(message: String, category: String = "Nova") {
        if (isVerboseLogging || category == "Feed" || category == "BackendFeed") Log.d(TAG, "[$category] $message")
    }
}
