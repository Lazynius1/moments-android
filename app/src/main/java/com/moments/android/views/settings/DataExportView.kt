package com.moments.android.views.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.MomentsFormat

/**
 * Port 1:1 de `DataExportView.swift` (788 líneas).
 */
@Composable
fun DataExportView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    val viewModel = remember { DataExportViewModel() }
    val context = LocalContext.current

    DisposableEffect(viewModel) {
        viewModel.checkExistingRequests()
        onDispose { viewModel.clear() }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.data_export_navigation_title),
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderZip,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(50.dp),
                )
                Text(
                    text = stringResource(R.string.data_export_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.data_export_subtitle),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // What includes
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.data_export_what_includes_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DataIncludeRow(
                        Icons.Default.Person,
                        stringResource(R.string.data_export_profile_info_title),
                        stringResource(R.string.data_export_profile_info_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.Default.GridView,
                        stringResource(R.string.data_export_posts_title),
                        stringResource(R.string.data_export_posts_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.Default.AccessTime,
                        stringResource(R.string.data_export_stories_title),
                        stringResource(R.string.data_export_stories_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.AutoMirrored.Filled.Message,
                        stringResource(R.string.data_export_messages_title),
                        stringResource(R.string.data_export_messages_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.Default.Favorite,
                        stringResource(R.string.data_export_interactions_title),
                        stringResource(R.string.data_export_interactions_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.Default.People,
                        stringResource(R.string.data_export_connections_title),
                        stringResource(R.string.data_export_connections_description),
                        accent, textColor,
                    )
                    DataIncludeRow(
                        Icons.Default.AccessTime,
                        stringResource(R.string.data_export_activity_title),
                        stringResource(R.string.data_export_activity_description),
                        accent, textColor,
                    )
                }
            }

            // Export options
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.data_export_options_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExportOptionCard(
                        title = stringResource(R.string.data_export_complete_title),
                        description = stringResource(R.string.data_export_complete_description),
                        icon = Icons.Default.Download,
                        estimatedSize = stringResource(R.string.data_export_complete_size),
                        isSelected = viewModel.selectedExportType == DataExportType.COMPLETE,
                        textColor = textColor,
                        accent = accent,
                        onTap = { viewModel.selectedExportType = DataExportType.COMPLETE },
                    )
                    ExportOptionCard(
                        title = stringResource(R.string.data_export_text_only_title),
                        description = stringResource(R.string.data_export_text_only_description),
                        icon = Icons.AutoMirrored.Filled.TextSnippet,
                        estimatedSize = stringResource(R.string.data_export_text_only_size),
                        isSelected = viewModel.selectedExportType == DataExportType.TEXT_ONLY,
                        textColor = textColor,
                        accent = accent,
                        onTap = { viewModel.selectedExportType = DataExportType.TEXT_ONLY },
                    )
                    ExportOptionCard(
                        title = stringResource(R.string.data_export_media_only_title),
                        description = stringResource(R.string.data_export_media_only_description),
                        icon = Icons.Default.PhotoLibrary,
                        estimatedSize = stringResource(R.string.data_export_media_only_size),
                        isSelected = viewModel.selectedExportType == DataExportType.MEDIA_ONLY,
                        textColor = textColor,
                        accent = accent,
                        onTap = { viewModel.selectedExportType = DataExportType.MEDIA_ONLY },
                    )
                    ExportOptionCard(
                        title = stringResource(R.string.data_export_conversations_only_title),
                        description = stringResource(R.string.data_export_conversations_only_description),
                        icon = Icons.Default.Lock,
                        estimatedSize = stringResource(R.string.data_export_conversations_only_size),
                        isSelected = viewModel.selectedExportType == DataExportType.CONVERSATIONS_ONLY,
                        textColor = textColor,
                        accent = accent,
                        onTap = {
                            viewModel.selectedExportType = DataExportType.CONVERSATIONS_ONLY
                        },
                    )
                }
            }

            // Format
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.data_export_format_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatButton(
                        title = stringResource(R.string.data_export_format_json_title),
                        description = stringResource(R.string.data_export_format_json_description),
                        isSelected = viewModel.selectedFormat == DataExportFormat.JSON,
                        isDark = isDark,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.selectedFormat = DataExportFormat.JSON },
                    )
                    FormatButton(
                        title = stringResource(R.string.data_export_format_csv_title),
                        description = stringResource(R.string.data_export_format_csv_description),
                        isSelected = viewModel.selectedFormat == DataExportFormat.CSV,
                        isDark = isDark,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.selectedFormat = DataExportFormat.CSV },
                    )
                }
            }

            viewModel.currentRequest?.let { request ->
                CurrentRequestSection(
                    request = request,
                    textColor = textColor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onOpenUrl = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }

            // PIN
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.data_export_pin_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Text(
                    stringResource(R.string.data_export_pin_description),
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                OutlinedTextField(
                    value = viewModel.recoveryPIN,
                    onValueChange = { raw ->
                        viewModel.recoveryPIN = raw.filter { it.isDigit() }.take(6)
                    },
                    placeholder = {
                        Text(stringResource(R.string.data_export_pin_placeholder))
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f),
                        unfocusedContainerColor = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f),
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                    ),
                )
            }

            // Privacy
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color(0xFFFF9500).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.data_export_privacy_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.data_export_privacy_bullet1), fontSize = 14.sp, color = Color.Gray)
                    Text(stringResource(R.string.data_export_privacy_bullet2), fontSize = 14.sp, color = Color.Gray)
                    Text(stringResource(R.string.data_export_privacy_bullet3), fontSize = 14.sp, color = Color.Gray)
                    Text(stringResource(R.string.data_export_privacy_bullet4), fontSize = 14.sp, color = Color.Gray)
                }
            }

            // Request button
            val canTap = viewModel.canRequestExport && !viewModel.isProcessing
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        if (viewModel.canRequestExport) accent else Color.Gray,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = canTap) {
                        viewModel.requestDataExport(
                            pinRequiredMessage = context.getString(R.string.data_export_conversations_only_pin_required),
                            userNotAuthMessage = context.getString(R.string.data_export_user_not_authenticated),
                            pinIncorrectMessage = context.getString(R.string.data_export_pin_incorrect),
                            submitErrorPrefix = context.getString(R.string.data_export_submit_error_prefix),
                        )
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.Default.Download,
                        null,
                        tint = if (viewModel.canRequestExport) {
                            SettingsProfileColors.accentContrastingText(isDark)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (viewModel.isProcessing) {
                        stringResource(R.string.data_export_processing)
                    } else {
                        stringResource(R.string.data_export_request_download)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (viewModel.canRequestExport) {
                        SettingsProfileColors.accentContrastingText(isDark)
                    } else {
                        Color.White
                    },
                )
            }

            if (!viewModel.canRequestExport && viewModel.currentRequest == null) {
                Text(
                    text = stringResource(
                        R.string.data_export_already_requested,
                        viewModel.daysUntilNextRequest,
                    ),
                    fontSize = 14.sp,
                    color = Color(0xFFFF9500),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (viewModel.showSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.showSuccess = false },
            title = { Text(stringResource(R.string.data_export_success_title)) },
            text = { Text(stringResource(R.string.data_export_success_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.showSuccess = false }) {
                    Text(stringResource(R.string.data_export_ok))
                }
            },
        )
    }
    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.showError = false },
            title = { Text(stringResource(R.string.data_export_error_title)) },
            text = { Text(viewModel.errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.showError = false }) {
                    Text(stringResource(R.string.data_export_ok))
                }
            },
        )
    }
}

@Composable
private fun DataIncludeRow(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color,
    textColor: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.width(24.dp).size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(description, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    estimatedSize: String,
    isSelected: Boolean,
    textColor: Color,
    accent: Color,
    onTap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(textColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSelected) accent else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) accent else Color.Gray,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(description, fontSize = 14.sp, color = Color.Gray)
        Row {
            Text(stringResource(R.string.data_export_estimated_size), fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.width(4.dp))
            Text(estimatedSize, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = accent)
        }
    }
}

@Composable
private fun FormatButton(
    title: String,
    description: String,
    isSelected: Boolean,
    isDark: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val textColor = if (isDark) Color.White else Color.Black
    val selectedFg = SettingsProfileColors.accentContrastingText(isDark)
    Column(
        modifier
            .background(
                if (isSelected) accent else textColor.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (isSelected) accent else Color.Gray.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onTap)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) selectedFg else textColor,
        )
        Text(
            description,
            fontSize = 12.sp,
            color = if (isSelected) selectedFg.copy(alpha = 0.8f) else Color.Gray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CurrentRequestSection(
    request: DataExportRequest,
    textColor: Color,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF007AFF).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF007AFF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.data_export_request_in_progress_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(stringResource(R.string.data_export_status_label), fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.width(6.dp))
                Text(
                    statusDisplayName(request.status),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = request.status.color,
                )
            }
            Row {
                Text(stringResource(R.string.data_export_requested_at), fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.width(6.dp))
                Text(
                    MomentsFormat.smartDate(request.requestDate, MomentsFormat.DateContext.MEDIUM_DATE_TIME),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }
            request.estimatedCompletion?.let { completion ->
                Row {
                    Text(stringResource(R.string.data_export_estimated_completion), fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        MomentsFormat.smartDate(completion, MomentsFormat.DateContext.MEDIUM_DATE_TIME),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF007AFF),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(stringResource(R.string.data_export_progress), fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(request.progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF007AFF),
                )
            }
            LinearProgressIndicator(
                progress = { request.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Color(0xFF007AFF),
                trackColor = Color.Gray.copy(alpha = 0.3f),
            )
        }

        if (request.status == DataExportStatus.READY && request.downloadURLs.isNotEmpty()) {
            Column(
                Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                request.downloadURLs.forEachIndexed { index, url ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(url) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Download, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (request.downloadURLs.size > 1) {
                                stringResource(
                                    R.string.data_export_download_part,
                                    index + 1,
                                    request.downloadURLs.size,
                                )
                            } else {
                                stringResource(R.string.data_export_download)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF007AFF),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusDisplayName(status: DataExportStatus): String = when (status) {
    DataExportStatus.PENDING -> stringResource(R.string.data_export_status_pending)
    DataExportStatus.PROCESSING -> stringResource(R.string.data_export_status_processing)
    DataExportStatus.READY -> stringResource(R.string.data_export_status_ready)
    DataExportStatus.COMPLETED -> stringResource(R.string.data_export_status_completed)
    DataExportStatus.FAILED -> stringResource(R.string.data_export_status_failed)
}
