package com.moments.android.reportes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import kotlinx.coroutines.launch

/** Port de AppealStatus.swift — lista y detalle de apelaciones del usuario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppealStatusView(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }

    var appeals by remember { mutableStateOf<List<AppealStatus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedAppeal by remember { mutableStateOf<AppealStatus?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    fun fetchAppeals() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!refreshing) isLoading = true
        refreshing = true
        scope.launch {
            try {
                appeals = appealService.fetchUserAppeals(userId)
                isLoading = false
                refreshing = false
            } catch (error: Exception) {
                errorMessage = (error as? AppealError)?.localizedMessage(context) ?: error.localizedMessage
                showError = true
                isLoading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchAppeals() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedAppeal == null) {
                            stringResource(R.string.appeal_status_title)
                        } else {
                            stringResource(R.string.appeal_detail_title)
                        },
                    )
                },
                navigationIcon = {
                    if (selectedAppeal != null || onBack != null) {
                        IconButton(onClick = {
                            if (selectedAppeal != null) selectedAppeal = null else onBack?.invoke()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    if (selectedAppeal == null) {
                        IconButton(onClick = { if (!refreshing) fetchAppeals() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            selectedAppeal != null -> AppealDetailFlowView(
                appeal = selectedAppeal!!,
                modifier = Modifier.padding(padding),
            )
            isLoading -> LoadingView(modifier = Modifier.padding(padding))
            appeals.isEmpty() -> EmptyAppealsView(modifier = Modifier.padding(padding))
            else -> AppealsListView(
                appeals = appeals,
                onSelect = { selectedAppeal = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(R.string.appeal_error_title)) },
            text = { Text(errorMessage ?: stringResource(R.string.appeal_error_unknown)) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(R.string.appeal_error_ok))
                }
            },
        )
    }
}

@Composable
private fun AppealsListView(
    appeals: List<AppealStatus>,
    onSelect: (AppealStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(appeals, key = { it.id }) { appeal ->
            AppealCard(appeal = appeal, onTap = { onSelect(appeal) })
        }
    }
}

@Composable
private fun AppealCard(appeal: AppealStatus, onTap: () -> Unit) {
    Card(onClick = onTap, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.appeal_status_ticket, appeal.ticketNumber),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(appeal.submittedAt, style = MaterialTheme.typography.labelSmall)
            }
            AppealStatusBadge(status = appeal.status, priority = appeal.priority)
            appeal.suspensionReason?.takeIf { it.isNotEmpty() }?.let {
                Text(stringResource(R.string.appeal_status_reason, it), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.appeal_status_estimatedResponse, appeal.estimatedResponseTime),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun AppealStatusBadge(status: String, priority: String) {
    val label = appealStatusLabel(status)
    Text(label, style = MaterialTheme.typography.labelMedium, color = appealStatusColor(status))
}

@Composable
private fun appealStatusLabel(status: String): String = when (status) {
    "pending" -> stringResource(R.string.appeal_status_pending)
    "reviewing" -> stringResource(R.string.appeal_status_reviewing)
    "approved" -> stringResource(R.string.appeal_status_approved)
    "denied" -> stringResource(R.string.appeal_status_denied)
    "requires_info" -> stringResource(R.string.appeal_status_requiresInfo)
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
private fun appealStatusColor(status: String) = when (status) {
    "approved" -> MaterialTheme.colorScheme.primary
    "denied" -> MaterialTheme.colorScheme.error
    "requires_info" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun AppealDetailFlowView(appeal: AppealStatus, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.appeal_status_ticket, appeal.ticketNumber), style = MaterialTheme.typography.headlineMedium)
        AppealStatusBadge(status = appeal.status, priority = appeal.priority)
        if (appeal.statusDescription.isNotEmpty()) {
            Text(appeal.statusDescription, style = MaterialTheme.typography.bodyMedium)
        }
        AppealDetailLine(stringResource(R.string.appeal_status_submitted), appeal.submittedAt)
        AppealDetailLine(stringResource(R.string.appeal_detail_estimatedTime), appeal.estimatedResponseTime)
        appeal.suspensionReason?.takeIf { it.isNotEmpty() }?.let {
            AppealDetailLine(stringResource(R.string.appeal_detail_suspensionReason), it)
        }
        appeal.moderatorNotes?.takeIf { it.isNotEmpty() }?.let {
            AppealDetailLine(stringResource(R.string.appeal_detail_moderatorNotes), it)
        }
        AppealTextSection(stringResource(R.string.appeal_detail_yourMessage), appeal.appealMessage)
        appeal.additionalInfo?.takeIf { it.isNotEmpty() }?.let {
            AppealTextSection(stringResource(R.string.appeal_detail_additionalInfo), it)
        }
        if (appeal.nextSteps.isNotEmpty()) {
            Text(stringResource(R.string.appeal_detail_nextSteps), style = MaterialTheme.typography.titleMedium)
            appeal.nextSteps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AppealDetailLine(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun AppealTextSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyAppealsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.appeal_status_noAppeals_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.appeal_status_noAppeals_description),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.appeal_status_loading))
    }
}
