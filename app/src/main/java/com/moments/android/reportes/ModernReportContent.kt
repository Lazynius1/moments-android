package com.moments.android.reportes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Port de ModernReportContent.swift */
@Composable
fun ModernReportContent(
    moment: Moment?,
    story: Story?,
    reportedUserId: String?,
    reportedUsername: String?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val persistence = remember { com.moments.android.services.persistence.LocalPersistenceService }

    var selectedCategory by remember { mutableStateOf<ReportCategory?>(null) }
    var additionalDetails by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val contentTypeLabel = when {
        moment != null -> stringResource(R.string.report_contentType_moment)
        story != null -> stringResource(R.string.report_contentType_story)
        else -> stringResource(R.string.report_contentType_user)
    }
    val contentId = moment?.id ?: story?.id ?: reportedUserId.orEmpty()
    val authorId = moment?.authorId ?: story?.authorId ?: reportedUserId.orEmpty()

    Column(modifier = modifier) {
        NativeReportSheetHeader(
            title = stringResource(R.string.report_title, contentTypeLabel.replaceFirstChar { it.uppercase() }),
            onBack = onBack,
        )

        if (showSuccessMessage) {
            ReportSuccessView(onDismiss = onDismiss)
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
            ) {
                Text(
                    stringResource(R.string.report_subtitle, contentTypeLabel),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                NativeReportOptionsSection {
                    ReportCategory.allCases.forEachIndexed { index, category ->
                        NativeReportOptionRow(
                            icon = category.icon,
                            title = stringResource(category.titleRes),
                            subtitle = stringResource(category.subtitleRes),
                            isSelected = selectedCategory == category,
                            showsChevron = selectedCategory != category,
                            onClick = { selectedCategory = category },
                        )
                        if (index < ReportCategory.allCases.lastIndex) NativeReportDivider()
                    }
                }
                selectedCategory?.let {
                    NativeReportDetailsSection(
                        title = stringResource(R.string.report_additionalDetails),
                        placeholder = stringResource(R.string.report_detailsPlaceholder),
                        text = additionalDetails,
                        onTextChange = { additionalDetails = it },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
            if (selectedCategory != null) {
                NativeReportSubmitBar(
                    isSubmitting = isSubmitting,
                    title = stringResource(R.string.report_sendButton),
                    onClick = {
                        val category = selectedCategory ?: return@NativeReportSubmitBar
                        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            ?: return@NativeReportSubmitBar
                        if (contentId.isEmpty()) return@NativeReportSubmitBar
                        isSubmitting = true
                        val reportedContentType = when {
                            moment != null -> "moment"
                            story != null -> "story"
                            else -> "user"
                        }
                        scope.launch {
                            persistence.reportContent(
                                reporterId = currentUserId,
                                reportedUserId = authorId,
                                reportedContentType = reportedContentType,
                                reportedContentId = contentId,
                                category = category.raw,
                                description = additionalDetails.trim(),
                                priority = category.priority.raw,
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
fun NativeReportSheetHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(44.dp))
    }
}

@Composable
fun NativeReportOptionsSection(content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) { content() }
}

@Composable
fun NativeReportOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    showsChevron: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun NativeReportDivider() {
    Divider(modifier = Modifier.padding(start = 36.dp))
}

@Composable
fun NativeReportDetailsSection(
    title: String,
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }
}

@Composable
fun NativeReportSubmitBar(isSubmitting: Boolean, title: String, onClick: () -> Unit) {
    Column {
        Divider()
        Button(
            onClick = onClick,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            if (isSubmitting) CircularProgressIndicator()
            else Text(title)
        }
    }
}

@Composable
private fun ReportSuccessView(onDismiss: () -> Unit) {
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
    }
}
