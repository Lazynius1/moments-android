package com.moments.android.reportes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.components.momentRefresh
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.settings.SettingsNavigationBar
import kotlinx.coroutines.launch

/** Port de ModerationReviewStatusView.swift */
@Composable
fun ModerationReviewStatusView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val colors = rememberAdaptiveColors()
    val primary = colors.primary
    val secondary = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.72f)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }

    var requests by remember { mutableStateOf<List<ModerationReviewStatus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<ModerationReviewStatus?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    fun fetchRequests() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!refreshing) isLoading = true
        refreshing = true
        scope.launch {
            try {
                requests = appealService.fetchUserModerationReviews(userId)
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

    LaunchedEffect(Unit) { fetchRequests() }

    BackHandler {
        if (selectedRequest != null) selectedRequest = null else onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        // ≡ iOS toolbar SettingsToolbarBackButton + navigationTitle inline
        SettingsNavigationBar(
            title = if (selectedRequest == null) {
                stringResource(R.string.moderationReview_status_title)
            } else {
                stringResource(R.string.moderationReview_status_detailTitle)
            },
            onNavigateBack = {
                if (selectedRequest != null) selectedRequest = null else onBack()
            },
        )

        AnimatedContent(
            targetState = selectedRequest,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut(tween(220)))
                }
            },
            label = "moderationReviewNav",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { request ->
            when {
                request != null -> ModerationReviewDetailView(
                    request = request,
                    primary = primary,
                    secondary = secondary,
                )
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MomentsCircularProgressIndicator()
                }
                requests.isEmpty() -> ModerationReviewEmptyView(primary = primary, secondary = secondary)
                else -> ModerationReviewListView(
                    requests = requests,
                    onSelect = { selectedRequest = it },
                    onRefresh = {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@ModerationReviewListView
                        refreshing = true
                        try {
                            requests = appealService.fetchUserModerationReviews(userId)
                        } catch (error: Exception) {
                            errorMessage = (error as? AppealError)?.localizedMessage(context)
                                ?: error.localizedMessage
                            showError = true
                        } finally {
                            isLoading = false
                            refreshing = false
                        }
                    },
                    primary = primary,
                    secondary = secondary,
                )
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
        )
    }
}

@Composable
private fun ModerationReviewListView(
    requests: List<ModerationReviewStatus>,
    onSelect: (ModerationReviewStatus) -> Unit,
    onRefresh: suspend () -> Unit,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .momentRefresh(onRefresh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(requests, key = { it.id }) { request ->
            ModerationReviewCard(
                request = request,
                primary = primary,
                secondary = secondary,
                onTap = { onSelect(request) },
            )
        }
    }
}

@Composable
private fun ModerationReviewCard(
    request: ModerationReviewStatus,
    primary: Color,
    secondary: Color,
    onTap: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true)
            .border(0.8.dp, primary.copy(alpha = if (isDark) 0.10f else 0.08f), shape)
            .clickable(onClick = onTap)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.moderationReview_status_ticket, request.ticketNumber),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    request.submittedAt,
                    color = secondary.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppealStatusBadge(status = request.status, priority = request.priority)
            Text(
                if (request.contentType == "story") stringResource(R.string.moderationReview_context_story)
                else stringResource(R.string.moderationReview_context_moment),
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                request.reviewMessage,
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.moderationReview_status_estimatedResponse, request.estimatedResponseTime),
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = secondary.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ModerationReviewDetailView(
    request: ModerationReviewStatus,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        detailCard(
            title = stringResource(R.string.moderationReview_previewTitle),
            lines = listOfNotNull(
                if (request.contentType == "story") stringResource(R.string.moderationReview_context_story)
                else stringResource(R.string.moderationReview_context_moment),
                moderationReviewScopeText(request.moderationScope),
                request.moderationCategory?.takeIf { it.isNotEmpty() },
            ),
            primary = primary,
            secondary = secondary,
        )
        detailCard(
            title = stringResource(R.string.moderationReview_messageTitle),
            lines = listOf(request.reviewMessage),
            primary = primary,
            secondary = secondary,
        )
        request.additionalInfo?.takeIf { it.isNotEmpty() }?.let {
            detailCard(
                title = stringResource(R.string.moderationReview_additionalInfo),
                lines = listOf(it),
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
            primary = primary,
            secondary = secondary,
        )
        request.moderatorNotes?.takeIf { it.isNotEmpty() }?.let {
            detailCard(
                title = stringResource(R.string.moderationReview_status_teamNotes),
                lines = listOf(it),
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
    primary: Color,
    secondary: Color,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = false)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, color = secondary.copy(alpha = 0.68f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        lines.forEach { line ->
            Text(line, color = primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = secondary.copy(alpha = 0.6f),
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.moderationReview_status_empty_title),
            color = primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.moderationReview_status_empty_message),
            color = secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}
