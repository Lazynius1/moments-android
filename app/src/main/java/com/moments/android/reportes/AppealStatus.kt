package com.moments.android.reportes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch

/** Port de AppealStatus.swift — lista y detalle de apelaciones del usuario. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AppealStatusView(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primary = colors.primary
    val secondary = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.72f)

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

    Column(modifier = modifier.fillMaxSize()) {
        AppealStatusHeader(
            title = if (selectedAppeal == null) {
                stringResource(R.string.appeal_status_title)
            } else {
                stringResource(R.string.appeal_detail_title)
            },
            // ≡ iOS: back solo en detalle; dismiss del sheet = swipe (onBack opcional no en header)
            showsBackButton = selectedAppeal != null,
            showsRefreshButton = selectedAppeal == null,
            refreshing = refreshing,
            onBack = { selectedAppeal = null },
            onRefresh = { if (!refreshing) fetchAppeals() },
            primary = primary,
        )

        AnimatedContent(
            targetState = selectedAppeal,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it } + fadeIn(
                        animationSpec = tween(280),
                    )) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut(animationSpec = tween(220)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(
                        animationSpec = tween(280),
                    )) togetherWith (slideOutHorizontally { it / 4 } + fadeOut(animationSpec = tween(220)))
                }
            },
            label = "appealStatusNav",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { appeal ->
            when {
                appeal != null -> AppealDetailFlowView(
                    appeal = appeal,
                    primary = primary,
                    secondary = secondary,
                )
                isLoading -> LoadingView(primary = primary, secondary = secondary)
                appeals.isEmpty() -> EmptyAppealsView(primary = primary, secondary = secondary)
                else -> AppealsListView(
                    appeals = appeals,
                    onSelect = { selectedAppeal = it },
                    onRefresh = {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@AppealsListView
                        refreshing = true
                        try {
                            appeals = appealService.fetchUserAppeals(userId)
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

/** ≡ AppealStatusHeader.swift */
@Composable
fun AppealStatusHeader(
    title: String,
    showsBackButton: Boolean,
    showsRefreshButton: Boolean,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    primary: Color = rememberAdaptiveColors().primary,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 12.dp),
    ) {
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            color = primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showsBackButton) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    tint = primary,
                    onClick = onBack,
                )
            } else {
                Spacer(Modifier.size(42.dp))
            }
            Spacer(Modifier.weight(1f))
            if (showsRefreshButton) {
                val transition = rememberInfiniteTransition(label = "appealRefreshSpin")
                val spin by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                    label = "spin",
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .momentsChromeGlass(CircleShape, interactive = !refreshing)
                        .clickable(enabled = !refreshing, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (refreshing) spin else 0f),
                    )
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AppealsListView(
    appeals: List<AppealStatus>,
    onSelect: (AppealStatus) -> Unit,
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
        items(appeals, key = { it.id }) { appeal ->
            AppealCard(
                appeal = appeal,
                onTap = { onSelect(appeal) },
                primary = primary,
                secondary = secondary,
            )
        }
    }
}

@Composable
private fun AppealCard(
    appeal: AppealStatus,
    onTap: () -> Unit,
    primary: Color,
    secondary: Color,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true)
            .border(
                0.8.dp,
                primary.copy(alpha = if (isDark) 0.10f else 0.08f),
                shape,
            )
            .clickable(onClick = onTap)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.appeal_status_ticket, appeal.ticketNumber),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    appeal.submittedAt,
                    color = secondary.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            AppealStatusBadge(status = appeal.status, priority = appeal.priority)
            appeal.suspensionReason?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    stringResource(R.string.appeal_status_reason, it),
                    color = secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.appeal_status_estimatedResponse, appeal.estimatedResponseTime),
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
fun AppealStatusBadge(status: String, priority: String) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val statusColor = when (status) {
        "approved" -> Color(0xFF34C759)
        "denied" -> Color.Red
        "requires_info" -> Color(0xFFFF9800)
        else -> colors.primary
    }
    val icon = when (status) {
        "pending" -> Icons.Default.Schedule
        "reviewing" -> Icons.Default.Visibility
        "approved" -> Icons.Default.CheckCircle
        "denied" -> Icons.Default.Close
        "requires_info" -> Icons.Default.Help
        else -> Icons.Default.RadioButtonUnchecked
    }
    @Suppress("UNUSED_VARIABLE")
    val unusedPriority = priority

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .border(
                0.8.dp,
                statusColor.copy(alpha = if (isDark) 0.28f else 0.18f),
                RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
        Text(
            appealStatusLabel(status),
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
private fun AppealDetailFlowView(
    appeal: AppealStatus,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.appeal_status_ticket, appeal.ticketNumber),
                color = primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            AppealStatusBadge(status = appeal.status, priority = appeal.priority)
            if (appeal.statusDescription.isNotEmpty()) {
                Text(
                    appeal.statusDescription,
                    color = secondary.copy(alpha = 0.74f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppealDetailLine(stringResource(R.string.appeal_status_submitted), appeal.submittedAt, primary, secondary, isDark)
            AppealDetailLine(stringResource(R.string.appeal_detail_estimatedTime), appeal.estimatedResponseTime, primary, secondary, isDark)
            appeal.suspensionReason?.takeIf { it.isNotEmpty() }?.let {
                AppealDetailLine(stringResource(R.string.appeal_detail_suspensionReason), it, primary, secondary, isDark)
            }
            appeal.moderatorNotes?.takeIf { it.isNotEmpty() }?.let {
                AppealDetailLine(stringResource(R.string.appeal_detail_moderatorNotes), it, primary, secondary, isDark)
            }
        }

        AppealTextSection(stringResource(R.string.appeal_detail_yourMessage), appeal.appealMessage, primary, secondary)
        appeal.additionalInfo?.takeIf { it.isNotEmpty() }?.let {
            AppealTextSection(stringResource(R.string.appeal_detail_additionalInfo), it, primary, secondary)
        }

        if (appeal.nextSteps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.appeal_detail_nextSteps),
                    color = primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    appeal.nextSteps.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .momentsChromeGlass(CircleShape, interactive = false),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                step,
                                color = secondary.copy(alpha = 0.76f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppealDetailLine(
    title: String,
    value: String,
    primary: Color,
    secondary: Color,
    isDark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = secondary.copy(alpha = 0.62f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun AppealTextSection(title: String, text: String, primary: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text,
            color = secondary.copy(alpha = 0.76f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun EmptyAppealsView(primary: Color, secondary: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.FindInPage,
            contentDescription = null,
            tint = secondary.copy(alpha = 0.58f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.appeal_status_noAppeals_title),
            color = primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.appeal_status_noAppeals_description),
            color = secondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingView(primary: Color, secondary: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MomentsCircularProgressIndicator(modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.appeal_status_loading),
            color = secondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
