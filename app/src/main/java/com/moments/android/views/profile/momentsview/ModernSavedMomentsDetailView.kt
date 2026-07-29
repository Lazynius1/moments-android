package com.moments.android.views.profile.momentsview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.models.Moment
import com.moments.android.views.settings.savedmoments.ModernSavedMomentsDetailView as SavedMomentsDetailImpl

/**
 * Re-export del port en `savedmoments/` (fuente: `SavedMomentsView.swift`).
 * Call sites de perfil / zoom siguen importando este package.
 */
@Composable
fun ModernSavedMomentsDetailView(
    moments: List<Moment>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRemoveMoment: ((Moment) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SavedMomentsDetailImpl(
        moments = moments,
        initialIndex = initialIndex,
        onDismiss = onDismiss,
        onRemoveMoment = onRemoveMoment,
        modifier = modifier,
    )
}
