package com.moments.android.reportes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/** Port de UserReportContent.swift */
@Composable
fun UserReportContent(
    reportedUserId: String,
    reportedUsername: String?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val persistence = remember { LocalPersistenceService }
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.58f)

    var selectedReason by remember { mutableStateOf<UserReportReason?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val reportTitle = if (!reportedUsername.isNullOrEmpty()) {
        stringResource(R.string.report_user_title_username, reportedUsername)
    } else {
        stringResource(R.string.report_user_title)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        NativeReportSheetHeader(title = reportTitle, onBack = onBack)

        if (showSuccessMessage) {
            ReportSuccessView(
                primaryText = primaryText,
                secondaryText = secondaryText,
                onDismiss = onDismiss,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = if (selectedReason != null) 120.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        stringResource(R.string.report_user_subtitle),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = secondaryText,
                        fontSize = 15.sp,
                    )
                    NativeReportOptionsSection {
                        UserReportReason.allCases.forEachIndexed { index, reason ->
                            NativeReportOptionRow(
                                icon = reason.icon,
                                title = stringResource(reason.titleRes),
                                subtitle = stringResource(reason.subtitleRes),
                                isSelected = selectedReason == reason,
                                showsChevron = selectedReason != reason,
                                onClick = { selectedReason = reason },
                            )
                            if (index < UserReportReason.allCases.lastIndex) NativeReportDivider()
                        }
                    }
                    AnimatedVisibility(
                        visible = selectedReason != null,
                        enter = slideInVertically { it / 4 } + fadeIn(),
                        exit = fadeOut(),
                    ) {
                        NativeReportDetailsSection(
                            title = stringResource(R.string.report_user_additionalDetails),
                            placeholder = stringResource(R.string.report_detailsPlaceholder),
                            text = additionalDetails,
                            onTextChange = { additionalDetails = it },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }

                if (selectedReason != null) {
                    NativeReportSubmitBar(
                        isSubmitting = isSubmitting,
                        title = stringResource(R.string.report_sendButton),
                        containerColor = colors.surfaceBackground,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = {
                            val reason = selectedReason ?: return@NativeReportSubmitBar
                            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                                ?: return@NativeReportSubmitBar
                            isSubmitting = true
                            scope.launch {
                                persistence.reportContent(
                                    reporterId = currentUserId,
                                    reportedUserId = reportedUserId,
                                    reportedContentType = "user",
                                    reportedContentId = reportedUserId,
                                    category = reason.raw,
                                    description = additionalDetails.trim(),
                                    priority = reason.priority.raw,
                                )
                                isSubmitting = false
                                showSuccessMessage = true
                            }
                        },
                    )
                }
            }
        }
    }
}
