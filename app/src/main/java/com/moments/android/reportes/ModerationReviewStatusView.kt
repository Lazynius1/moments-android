package com.moments.android.reportes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.moments.android.views.settings.SettingsProfileColors
import kotlinx.coroutines.launch

/** Port de ModerationReviewStatusView.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationReviewStatusView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) androidx.compose.ui.graphics.Color(0xFF0B1215)
    else androidx.compose.ui.graphics.Color(0xFFFAF9F6)
    val surface = SettingsProfileColors.surfaceContainer(isDark)
    val primary = SettingsProfileColors.onSurface(isDark)
    val secondary = SettingsProfileColors.onSurfaceVariant(isDark)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }

    var requests by remember { mutableStateOf<List<ModerationReviewStatus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRequest by remember { mutableStateOf<ModerationReviewStatus?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    fun fetchRequests() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoading = true
        scope.launch {
            try {
                requests = appealService.fetchUserModerationReviews(userId)
                isLoading = false
            } catch (error: Exception) {
                errorMessage = (error as? AppealError)?.localizedMessage(context) ?: error.localizedMessage
                showError = true
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchRequests() }

    BackHandler {
        if (selectedRequest != null) selectedRequest = null else onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedRequest == null) stringResource(R.string.moderationReview_status_title)
                        else stringResource(R.string.moderationReview_status_detailTitle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedRequest != null) selectedRequest = null else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = canvas,
                    titleContentColor = primary,
                    navigationIconContentColor = primary,
                ),
            )
        },
        containerColor = canvas,
        contentColor = primary,
    ) { padding ->
        when {
            selectedRequest != null -> ModerationReviewDetailView(
                request = selectedRequest!!,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                surface = surface,
                primary = primary,
                secondary = secondary,
            )
            isLoading -> Column(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(color = primary) }
            requests.isEmpty() -> ModerationReviewEmptyView(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                primary = primary,
                secondary = secondary,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(requests, key = { it.id }) { request ->
                    ModerationReviewCard(
                        request = request,
                        surface = surface,
                        primary = primary,
                        secondary = secondary,
                        onTap = { selectedRequest = request },
                    )
                }
            }
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
            containerColor = surface,
            titleContentColor = primary,
            textContentColor = secondary,
        )
    }
}

@Composable
private fun ModerationReviewCard(
    request: ModerationReviewStatus,
    surface: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    onTap: () -> Unit,
) {
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.moderationReview_status_ticket, request.ticketNumber), color = primary)
            Text(request.submittedAt, style = MaterialTheme.typography.labelSmall, color = secondary)
            AppealStatusBadge(status = request.status, priority = request.priority)
            Text(
                if (request.contentType == "story") stringResource(R.string.moderationReview_context_story)
                else stringResource(R.string.moderationReview_context_moment),
                color = primary,
            )
            Text(
                request.reviewMessage,
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
            Text(
                stringResource(R.string.moderationReview_status_estimatedResponse, request.estimatedResponseTime),
                color = secondary,
            )
        }
    }
}

@Composable
private fun ModerationReviewDetailView(
    request: ModerationReviewStatus,
    surface: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        detailCard(
            title = stringResource(R.string.moderationReview_previewTitle),
            lines = listOfNotNull(
                if (request.contentType == "story") stringResource(R.string.moderationReview_context_story)
                else stringResource(R.string.moderationReview_context_moment),
                moderationReviewScopeText(request.moderationScope),
                request.moderationCategory?.takeIf { it.isNotEmpty() },
            ),
            surface = surface,
            primary = primary,
            secondary = secondary,
        )
        detailCard(
            title = stringResource(R.string.moderationReview_messageTitle),
            lines = listOf(request.reviewMessage),
            surface = surface,
            primary = primary,
            secondary = secondary,
        )
        request.additionalInfo?.takeIf { it.isNotEmpty() }?.let {
            detailCard(
                title = stringResource(R.string.moderationReview_additionalInfo),
                lines = listOf(it),
                surface = surface,
                primary = primary,
                secondary = secondary,
            )
        }
        detailCard(
            title = stringResource(R.string.moderationReview_contactEmail),
            lines = listOf(
                request.contactEmail,
                stringResource(R.string.moderationReview_status_estimatedResponse, request.estimatedResponseTime),
            ),
            surface = surface,
            primary = primary,
            secondary = secondary,
        )
        request.moderatorNotes?.takeIf { it.isNotEmpty() }?.let {
            detailCard(
                title = stringResource(R.string.moderationReview_status_teamNotes),
                lines = listOf(it),
                surface = surface,
                primary = primary,
                secondary = secondary,
            )
        }
    }
}

@Composable
private fun detailCard(
    title: String,
    lines: List<String>,
    surface: androidx.compose.ui.graphics.Color,
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = secondary)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = primary) }
        }
    }
}

@Composable
private fun moderationReviewScopeText(scope: String): String = when (scope) {
    "storySticker" -> stringResource(R.string.moderationReview_scope_storySticker)
    "postHiddenLayer" -> stringResource(R.string.moderationReview_scope_postHiddenLayer)
    "story" -> stringResource(R.string.moderationReview_scope_story)
    else -> stringResource(R.string.moderationReview_scope_post)
}

@Composable
private fun ModerationReviewEmptyView(
    primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.moderationReview_status_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = primary,
        )
        Text(
            stringResource(R.string.moderationReview_status_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = secondary,
        )
    }
}
