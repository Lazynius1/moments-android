package com.moments.android.reportes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.delay
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

    var selectedReason by remember { mutableStateOf<UserReportReason?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val reportTitle = if (!reportedUsername.isNullOrEmpty()) {
        stringResource(R.string.report_user_title_username, reportedUsername)
    } else {
        stringResource(R.string.report_user_title)
    }

    Column(modifier = modifier) {
        NativeReportSheetHeader(title = reportTitle, onBack = onBack)

        if (showSuccessMessage) {
            UserReportSuccessView(onDismiss = onDismiss)
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
            ) {
                Text(
                    stringResource(R.string.report_user_subtitle),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
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
                selectedReason?.let {
                    NativeReportDetailsSection(
                        title = stringResource(R.string.report_user_additionalDetails),
                        placeholder = stringResource(R.string.report_detailsPlaceholder),
                        text = additionalDetails,
                        onTextChange = { additionalDetails = it },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            selectedReason?.let { reason ->
                NativeReportSubmitBar(
                    isSubmitting = isSubmitting,
                    title = stringResource(R.string.report_sendButton),
                    onClick = {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@NativeReportSubmitBar
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

@Composable
private fun UserReportSuccessView(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onDismiss()
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.report_success_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.report_success_message), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
    }
}
