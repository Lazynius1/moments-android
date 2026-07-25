package com.moments.android.views.profile.userprofile.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.moments.android.models.Moment
import com.moments.android.views.profile.core.sections.ModernMomentThumbnail
import com.moments.android.views.profile.core.sections.ProfileBentoTileAssigner
import com.moments.android.views.profile.core.sections.ProfileGridTileDescriptor
import com.moments.android.views.profile.core.sections.ProfileMomentsGridMetrics

/**
 * Port de `UserProfileMomentsSection.swift`.
 *
 * `UserModernMomentThumbnail` de iOS es funcionalmente idéntico a `ModernMomentThumbnail` (ya portado
 * para el perfil propio): misma maquinaria de media/crop/chrome/gestos. Para respetar la regla de
 * NO reinventar infraestructura, aquí se delega en él fijando el estilo de visitante (sin badge de
 * audiencia). Se conserva la firma con `zoomNamespace`/`onTap`/`onLongPress`/`descriptor` de la struct
 * de iOS. Los helpers de altura de grid (`calculateBentoGridHeight`/`calculateTaggedGridHeight`)
 * ganan un parámetro `availableWidth` porque en Android la altura del bento depende del ancho de celda.
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

/** Port de `calculateBentoGridHeight(moments:)`. */
fun calculateBentoGridHeight(moments: List<Moment>, availableWidth: Dp): Dp {
    val descriptors = ProfileBentoTileAssigner.assign(moments)
    return ProfileMomentsGridMetrics.bentoHeight(descriptors.map { it.layoutKind }, availableWidth)
}

/** Port de `calculateTaggedGridHeight(moments:)`. */
fun calculateTaggedGridHeight(moments: List<Moment>, availableWidth: Dp): Dp {
    val descriptors = ProfileBentoTileAssigner.simple(moments)
    return ProfileMomentsGridMetrics.bentoHeight(descriptors.map { it.layoutKind }, availableWidth)
}
