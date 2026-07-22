package com.moments.android.reportes

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/** Port de ReportBottomSheet.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBottomSheet(
    target: ReportTarget,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (target) {
            is ReportTarget.UserTarget -> UserReportContent(
                reportedUserId = target.userId,
                reportedUsername = target.username,
                onBack = onDismiss,
                onDismiss = onDismiss,
            )
            is ReportTarget.MomentTarget -> ModernReportContent(
                moment = target.moment,
                story = null,
                reportedUserId = null,
                reportedUsername = null,
                onBack = onDismiss,
                onDismiss = onDismiss,
            )
            is ReportTarget.StoryTarget -> ModernReportContent(
                moment = null,
                story = target.story,
                reportedUserId = null,
                reportedUsername = null,
                onBack = onDismiss,
                onDismiss = onDismiss,
            )
        }
    }
}
