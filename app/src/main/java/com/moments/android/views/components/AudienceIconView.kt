package com.moments.android.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.privacy.ContentAudience

/** Medidas ópticas — port 1:1 de `AudienceIconMetrics` (iOS points → dp). */
object AudienceIconMetrics {
    /** Filas (Settings, visibilidad). SF Symbol ~19pt en slot 28pt. */
    val row = 22.dp
    /** Creator / caption. */
    val creatorRow = 22.dp
    /** Grid del sheet (Only Me, etc.). */
    val gridCard = 30.dp
    /** Grid: Everyone / Mutuals / BFF / Personalizado / Listas. */
    val gridCardEmphasis = 34.dp
    /** Cápsula en editor de historia. */
    val storyCapsule = 20.dp
    /** Barra inferior de historia propia (alineado con StoryActivityEmptyIcon 36×36). */
    val storyBottomBar = 34.dp
    /** Resumen en actividad de historia. */
    val storyActivity = 17.dp
    /** Miniatura en grids de actividad / perfil (solo icono, sin cápsula). */
    val activityGridThumbnail = 15.dp
}

/**
 * Port de `AudienceIconView.swift`.
 * Assets: `Audience*Icon` → `R.drawable.audience_*_icon` (template + tint).
 *
 * Tint (mismo orden que iOS):
 * 1. `tintColor` explícito
 * 2. Best Friends → `#34C759`
 * 3. `isDark` / sistema → blanco / negro (equiv. `colorScheme` / `.primary`)
 */
@Composable
fun AudienceIconView(
    audience: ContentAudience,
    size: Dp,
    tintColor: Color? = null,
    /** Equiv. iOS `colorScheme: ColorScheme?` — si null, usa el tema del sistema. */
    isDark: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isDark ?: isSystemInDarkTheme()
    val tint = tintColor ?: when {
        audience == ContentAudience.BEST_FRIENDS -> Color(0xFF34C759)
        dark -> Color.White
        else -> Color.Black
    }
    Image(
        painter = painterResource(audienceIconResource(audience)),
        contentDescription = null, // iOS: accessibilityHidden(true)
        contentScale = ContentScale.Fit, // iOS: scaledToFit
        colorFilter = ColorFilter.tint(tint), // iOS: renderingMode(.template) + foregroundStyle
        modifier = modifier.size(size),
    )
}

/**
 * Icono de audiencia discreto para overlays en grids (sin texto ni cápsula).
 * Port de `ActivityGridAudienceIcon`.
 */
@Composable
fun ActivityGridAudienceIcon(
    audience: ContentAudience,
    size: Dp = AudienceIconMetrics.activityGridThumbnail,
    modifier: Modifier = Modifier,
) {
    AudienceIconView(
        audience = audience,
        size = size,
        tintColor = if (audience == ContentAudience.BEST_FRIENDS) {
            Color(0xFF34C759)
        } else {
            Color.White
        },
        // iOS: .shadow(color: .black.opacity(0.55), radius: 2, y: 1)
        // + .allowsHitTesting(false) → clearAndSetSemantics (decorativo; click en el padre)
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.55f),
            )
            .clearAndSetSemantics { },
    )
}

/** Mapa assetName iOS → drawable Android. */
private fun audienceIconResource(audience: ContentAudience): Int = when (audience) {
    ContentAudience.EVERYONE -> R.drawable.audience_everyone_icon
    ContentAudience.MUTUALS -> R.drawable.audience_mutuals_icon
    ContentAudience.BEST_FRIENDS -> R.drawable.audience_best_friends_icon
    ContentAudience.CUSTOM -> R.drawable.audience_custom_icon
    ContentAudience.CUSTOM_LIST -> R.drawable.audience_custom_list_icon
    ContentAudience.ONLY_ME -> R.drawable.audience_only_me_icon
}
