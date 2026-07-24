package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Port de `UserProfileZoomNavigation.swift`. */
object UserProfileZoomNavigation {
    fun sourceID(userId: String): String = "user-profile-$userId"
}

/** Compose estable no expone el zoom navigationTransition de iOS; conserva source id + corner clipping. */
fun Modifier.userProfileZoomSource(userId: String, cornerRadius: Dp = 22.dp): Modifier =
    if (userId.isBlank()) this else clip(RoundedCornerShape(cornerRadius))

/** El host de navegación usa este source id al abrir UserProfileView. */
fun userProfileZoomDestinationSourceID(userId: String): String = UserProfileZoomNavigation.sourceID(userId)
