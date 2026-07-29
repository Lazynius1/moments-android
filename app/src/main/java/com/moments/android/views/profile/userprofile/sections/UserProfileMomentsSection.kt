package com.moments.android.views.profile.userprofile.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment
import com.moments.android.views.profile.core.sections.ModernMomentThumbnail
import com.moments.android.views.profile.core.sections.ProfileBentoTileAssigner
import com.moments.android.views.profile.core.sections.ProfileGridTileDescriptor
import com.moments.android.views.profile.core.sections.ProfileMomentsGridMetrics

/**
 * Port de `UserProfileMomentsSection.swift`.
 *
 * `UserModernMomentThumbnail` en iOS es la variante visitante de `ModernMomentThumbnail`
 * (perfil propio): misma media/crop/chrome/gestos, **sin** badge de audiencia. No se duplica
 * la maquinaria; se delega con `showsAudienceBadge = false`.
 *
 * Helpers de altura: iOS usa `defaultAvailableWidth` (≈393) si no hay ancho; aquí el default
 * es el mismo fallback.
 */
@Composable
fun UserModernMomentThumbnail(
    moment: Moment,
    size: Dp,
    zoomSourceID: String? = null,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    gridIndex: Int = 0,
    descriptor: ProfileGridTileDescriptor = ProfileGridTileDescriptor.standard(moment),
    modifier: Modifier = Modifier,
) {
    ModernMomentThumbnail(
        moment = moment,
        size = size,
        zoomSourceID = zoomSourceID,
        onTap = onTap,
        onLongPress = onLongPress,
        showsAudienceBadge = false,
        usesDiscreetAudienceIcon = false,
        gridIndex = gridIndex,
        descriptor = descriptor,
        modifier = modifier,
    )
}

/** Port de `calculateBentoGridHeight(moments:)` — `ProfileBentoTileAssigner.assign`. */
fun calculateBentoGridHeight(
    moments: List<Moment>,
    availableWidth: Dp = ProfileMomentsGridMetrics.defaultAvailableWidth,
): Dp {
    val descriptors = ProfileBentoTileAssigner.assign(moments)
    return ProfileMomentsGridMetrics.bentoHeight(descriptors.map { it.layoutKind }, availableWidth)
}

/** Port de `calculateTaggedGridHeight(moments:)` — `ProfileBentoTileAssigner.simple`. */
fun calculateTaggedGridHeight(
    moments: List<Moment>,
    availableWidth: Dp = ProfileMomentsGridMetrics.defaultAvailableWidth,
): Dp {
    val descriptors = ProfileBentoTileAssigner.simple(moments)
    return ProfileMomentsGridMetrics.bentoHeight(descriptors.map { it.layoutKind }, availableWidth)
}
