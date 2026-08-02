package com.moments.android.reportes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.views.shared.MomentsModalSheet

/**
 * Port de `ReportBottomSheet.swift`.
 * iOS `.sheet` + `.presentationDetents([.medium, .large])` → [MomentsModalSheet] `largeOnly = false`.
 */
@Composable
fun ReportBottomSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
) {
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = false,
    ) { dismiss ->
        when (target) {
            is ReportTarget.UserTarget -> UserReportContent(
                reportedUserId = target.userId,
                reportedUsername = target.username,
                onBack = dismiss,
                onDismiss = dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            is ReportTarget.MomentTarget -> ModernReportContent(
                moment = target.moment,
                story = null,
                reportedUserId = null,
                reportedUsername = null,
                onBack = dismiss,
                onDismiss = dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            is ReportTarget.StoryTarget -> ModernReportContent(
                moment = null,
                story = target.story,
                reportedUserId = null,
                reportedUsername = null,
                onBack = dismiss,
                onDismiss = dismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
