package com.moments.android.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.privacy.ContentAudience

/** Medidas ópticas compartidas del port de `AudienceIconMetrics`. */
object AudienceIconMetrics {
    val row = 22.dp
    val creatorRow = 22.dp
    val gridCard = 30.dp
    val gridCardEmphasis = 34.dp
    val storyCapsule = 20.dp
    val storyBottomBar = 34.dp
    val storyActivity = 17.dp
    val activityGridThumbnail = 15.dp
}

/** Port de `AudienceIconView.swift`, con los assets de audiencia Android como fuente visual. */
@Composable
fun AudienceIconView(
    audience: ContentAudience,
    size: Dp,
    tintColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val tint = tintColor ?: when {
        audience == ContentAudience.BEST_FRIENDS -> Color(0xFF34C759)
        isSystemInDarkTheme() -> Color.White
        else -> Color.Black
    }
    Image(
        painter = painterResource(audienceIconResource(audience)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/** Icono discreto para overlays de grids, equivalente a `ActivityGridAudienceIcon`. */
@Composable
fun ActivityGridAudienceIcon(
    audience: ContentAudience,
    size: Dp = AudienceIconMetrics.activityGridThumbnail,
    modifier: Modifier = Modifier,
) {
    AudienceIconView(
        audience = audience,
        size = size,
        tintColor = if (audience == ContentAudience.BEST_FRIENDS) Color(0xFF34C759) else Color.White,
        modifier = modifier.shadow(2.dp, ambientColor = Color.Black.copy(.55f), spotColor = Color.Black.copy(.55f)),
    )
}

private fun audienceIconResource(audience: ContentAudience): Int = when (audience) {
    ContentAudience.EVERYONE -> R.drawable.audience_everyone_icon
    ContentAudience.MUTUALS -> R.drawable.audience_mutuals_icon
    ContentAudience.BEST_FRIENDS -> R.drawable.audience_best_friends_icon
    ContentAudience.CUSTOM -> R.drawable.audience_custom_icon
    ContentAudience.CUSTOM_LIST -> R.drawable.audience_custom_list_icon
    ContentAudience.ONLY_ME -> R.drawable.audience_only_me_icon
}
