package com.moments.android.views.messaging.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.momentsScrollEdgeChrome
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.MessageRequest
import com.moments.android.views.shared.tabbar.MomentsTabBarHidden
import java.util.Date
import kotlin.math.min

private val DestructiveRed = Color(0xFFD84B57)

/** Recent requests are shown first; older requests load ten at a time on demand. */
@Composable
fun MessageRequestsView(
    service: MessageRequestService? = null,
    isEditing: Boolean = false,
    onEditingChange: (Boolean) -> Unit = {},
    onCanEditChange: (Boolean) -> Unit = {},
    onOpenRequest: (MessageRequest) -> Unit = {},
    onOpenPrivacySettings: () -> Unit = {},
    onOpenMuteSettings: () -> Unit = {},
    onShowingHiddenRequests: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val ownedService = remember { MessageRequestService() }
    MomentsTabBarHidden()
    val requestService = service ?: ownedService
    val ownsListeners = service == null
    val pendingRequests by requestService.pendingRequests.collectAsState()
    val oldRequests by requestService.oldRequests.collectAsState()
    val hiddenRequests by requestService.hiddenRequests.collectAsState()
    var showingOlderRequests by remember { mutableStateOf(false) }
    var visibleOlderRequestCount by remember { mutableIntStateOf(10) }
    var showingHiddenRequests by remember { mutableStateOf(false) }
    var actionRequest by remember { mutableStateOf<MessageRequest?>(null) }
    var showingDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showingDeleteSelectedConfirmation by remember { mutableStateOf(false) }
    var selectedRequestIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val allRequests = (pendingRequests + oldRequests).sortedByDescending { it.lastActivityAt }
    val cutoff = Date(System.currentTimeMillis() - THIRTY_DAYS_MILLIS)
    val recentRequests = allRequests.filter { it.lastActivityAt >= cutoff }
    val olderRequests = allRequests.filter { it.lastActivityAt < cutoff }
    val visibleOlderRequests = olderRequests.take(visibleOlderRequestCount)
    val hasVisibleRequests = recentRequests.isNotEmpty() || (showingOlderRequests && visibleOlderRequests.isNotEmpty())
    val requestIds = remember(allRequests) { allRequests.map(::requestKey).toSet() }
    val defaultUsername = stringResource(R.string.messaging_user_default)

    DisposableEffect(requestService, ownsListeners) {
        FirebaseAuth.getInstance().currentUser?.uid?.let(requestService::listenToPendingRequests)
        onDispose { if (ownsListeners) requestService.removeAllListeners() }
    }
    LaunchedEffect(showingHiddenRequests) {
        onShowingHiddenRequests(showingHiddenRequests)
        if (showingHiddenRequests) {
            onEditingChange(false)
            selectedRequestIds = emptySet()
        }
    }
    LaunchedEffect(allRequests.isEmpty(), isEditing) {
        onCanEditChange(allRequests.isNotEmpty())
        if (allRequests.isEmpty() && isEditing) onEditingChange(false)
    }
    LaunchedEffect(requestIds) {
        selectedRequestIds = selectedRequestIds.intersect(requestIds)
    }
    LaunchedEffect(isEditing) {
        if (!isEditing) selectedRequestIds = emptySet()
    }
    BackHandler(enabled = showingHiddenRequests) { showingHiddenRequests = false }

    fun toggleSelection(request: MessageRequest) {
        val id = requestKey(request)
        selectedRequestIds = if (id in selectedRequestIds) selectedRequestIds - id else selectedRequestIds + id
    }

    fun openOrSelect(request: MessageRequest) {
        if (isEditing) toggleSelection(request) else onOpenRequest(request)
    }

    fun accept(request: MessageRequest) {
        requestService.acceptRequest(request) { result ->
            if (result.isSuccess) {
                InAppNotificationService.showActionToast(InAppActionToast.messageRequestAccepted())
            }
        }
    }

    fun reject(request: MessageRequest, toastPlural: Boolean = false) {
        requestService.rejectRequest(request) { result ->
            if (result.isSuccess && !toastPlural) {
                InAppNotificationService.showActionToast(InAppActionToast.messageRequestDeleted())
            }
        }
    }

    fun report(request: MessageRequest) {
        requestService.reportRequest(request) { result ->
            if (result.isSuccess) {
                InAppNotificationService.showActionToast(InAppActionToast.messageRequestReported())
            }
        }
    }

    fun block(request: MessageRequest) {
        val username = request.senderUsername?.takeIf { it.isNotBlank() } ?: defaultUsername
        requestService.blockUser(request) { result ->
            if (result.isSuccess) {
                InAppNotificationService.showActionToast(InAppActionToast.blocked(username))
            }
        }
    }

    if (showingHiddenRequests) {
        HiddenMessageRequestsContent(
            requests = hiddenRequests,
            onOpen = onOpenRequest,
            onAction = { actionRequest = it },
            onOpenMuteSettings = onOpenMuteSettings,
            modifier = modifier.background(colors.surfaceBackground),
        )
    } else {
        Column(modifier.fillMaxSize().background(colors.surfaceBackground)) {
            LazyColumn(Modifier.weight(1f).momentsScrollEdgeChrome()) {
                if (hasVisibleRequests) {
                    item { MessageRequestPrivacyNotice(onOpenPrivacySettings) }
                    items(recentRequests, key = ::requestKey) { request ->
                        RequestListRow(
                            request = request,
                            isEditing = isEditing,
                            isSelected = requestKey(request) in selectedRequestIds,
                            onTap = { openOrSelect(request) },
                            onAction = { actionRequest = request },
                            onToggleSelection = { toggleSelection(request) },
                        )
                    }
                    if (showingOlderRequests) {
                        items(visibleOlderRequests, key = ::requestKey) { request ->
                            RequestListRow(
                                request = request,
                                isEditing = isEditing,
                                isSelected = requestKey(request) in selectedRequestIds,
                                onTap = { openOrSelect(request) },
                                onAction = { actionRequest = request },
                                onToggleSelection = { toggleSelection(request) },
                            )
                        }
                        if (visibleOlderRequests.size < olderRequests.size) {
                            item {
                                MessageRequestTextAction(R.string.message_requests_load_more) {
                                    visibleOlderRequestCount = min(visibleOlderRequestCount + 10, olderRequests.size)
                                }
                            }
                        }
                    } else {
                        item {
                            MessageRequestTextAction(R.string.message_requests_view_all) {
                                visibleOlderRequestCount = min(10, olderRequests.size)
                                showingOlderRequests = true
                            }
                        }
                    }
                    item { HiddenRequestsRow(hiddenRequests.size) { showingHiddenRequests = true } }
                } else {
                    item { HiddenRequestsRow(hiddenRequests.size) { showingHiddenRequests = true } }
                    item { RecentRequestsEmptyState() }
                    if (showingOlderRequests) {
                        items(visibleOlderRequests, key = ::requestKey) { request ->
                            RequestListRow(
                                request = request,
                                isEditing = isEditing,
                                isSelected = requestKey(request) in selectedRequestIds,
                                onTap = { openOrSelect(request) },
                                onAction = { actionRequest = request },
                                onToggleSelection = { toggleSelection(request) },
                            )
                        }
                        if (visibleOlderRequests.size < olderRequests.size) {
                            item {
                                MessageRequestTextAction(R.string.message_requests_load_more) {
                                    visibleOlderRequestCount = min(visibleOlderRequestCount + 10, olderRequests.size)
                                }
                            }
                        }
                    } else {
                        item {
                            MessageRequestTextAction(R.string.message_requests_view_all) {
                                visibleOlderRequestCount = min(10, olderRequests.size)
                                showingOlderRequests = true
                            }
                        }
                    }
                }
            }
            if (isEditing && selectedRequestIds.isNotEmpty()) {
                Button(
                    onClick = { showingDeleteSelectedConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text(stringResource(R.string.message_requests_delete), fontWeight = FontWeight.SemiBold) }
            } else if (showingOlderRequests && visibleOlderRequests.isNotEmpty() && !isEditing) {
                Button(
                    onClick = { showingDeleteAllConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text(stringResource(R.string.message_requests_delete_all), fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    actionRequest?.let { request ->
        MessageRequestActionDialog(
            onDismiss = { actionRequest = null },
            onAccept = {
                accept(request)
                actionRequest = null
            },
            onReport = {
                report(request)
                actionRequest = null
            },
            onDelete = {
                reject(request)
                actionRequest = null
            },
            onBlock = {
                block(request)
                actionRequest = null
            },
        )
    }
    if (showingDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showingDeleteAllConfirmation = false },
            title = { Text(stringResource(R.string.message_requests_delete_all_confirmation_title)) },
            text = { Text(stringResource(R.string.message_requests_delete_all_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    allRequests.forEach { reject(it, toastPlural = true) }
                    selectedRequestIds = emptySet()
                    onEditingChange(false)
                    showingDeleteAllConfirmation = false
                    InAppNotificationService.showActionToast(InAppActionToast.messageRequestsDeleted())
                }) { Text(stringResource(R.string.message_requests_delete_all), color = DestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteAllConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (showingDeleteSelectedConfirmation) {
        val selected = allRequests.filter { requestKey(it) in selectedRequestIds }
        AlertDialog(
            onDismissRequest = { showingDeleteSelectedConfirmation = false },
            title = { Text(stringResource(R.string.message_requests_delete_all_confirmation_title)) },
            text = { Text(stringResource(R.string.message_requests_delete_all_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    selected.forEach { reject(it, toastPlural = true) }
                    selectedRequestIds = emptySet()
                    onEditingChange(false)
                    showingDeleteSelectedConfirmation = false
                    InAppNotificationService.showActionToast(
                        if (selected.size == 1) InAppActionToast.messageRequestDeleted()
                        else InAppActionToast.messageRequestsDeleted(),
                    )
                }) { Text(stringResource(R.string.message_requests_delete), color = DestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteSelectedConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun MessageRequestPrivacyNotice(onOpenPrivacySettings: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.message_requests_privacy_description),
            color = colors.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.message_requests_privacy_settings),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onOpenPrivacySettings).padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun HiddenRequestsRow(count: Int, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.VisibilityOff, null, tint = colors.primary, modifier = Modifier.size(26.dp))
        Text(
            stringResource(R.string.message_requests_hidden_row),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) Text(count.toString(), color = colors.secondary)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.secondary)
    }
}

@Composable
private fun RecentRequestsEmptyState() {
    MessageRequestEmptyState(
        titleRes = R.string.message_requests_recent_empty_title,
        descriptionRes = R.string.message_requests_recent_empty_description,
    )
}

@Composable
private fun MessageRequestEmptyState(titleRes: Int, descriptionRes: Int) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 28.dp, end = 28.dp)
            .momentsEmptyStateAppear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .border(2.dp, colors.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Send, null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        Text(
            stringResource(titleRes),
            color = colors.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(descriptionRes),
            color = colors.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageRequestTextAction(textRes: Int, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(textRes),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun HiddenMessageRequestsContent(
    requests: List<MessageRequest>,
    onOpen: (MessageRequest) -> Unit,
    onAction: (MessageRequest) -> Unit,
    onOpenMuteSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var showingOlderRequests by remember { mutableStateOf(false) }
    var visibleOlderRequestCount by remember { mutableIntStateOf(10) }
    val cutoff = Date(System.currentTimeMillis() - THIRTY_DAYS_MILLIS)
    val recentRequests = requests.filter { it.lastActivityAt >= cutoff }
    val olderRequests = requests.filter { it.lastActivityAt < cutoff }
    val visibleOlderRequests = olderRequests.take(visibleOlderRequestCount)
    LazyColumn(modifier.fillMaxSize().momentsScrollEdgeChrome()) {
        if (recentRequests.isEmpty()) {
            item {
                MessageRequestEmptyState(
                    titleRes = R.string.message_requests_hidden_empty_title,
                    descriptionRes = R.string.message_requests_hidden_empty_description,
                )
            }
        } else {
            items(recentRequests, key = ::requestKey) { request ->
                RequestListRow(request, onTap = { onOpen(request) }, onAction = { onAction(request) })
            }
        }
        if (showingOlderRequests) {
            items(visibleOlderRequests, key = ::requestKey) { request ->
                RequestListRow(request, onTap = { onOpen(request) }, onAction = { onAction(request) })
            }
            if (visibleOlderRequests.size < olderRequests.size) {
                item {
                    MessageRequestTextAction(R.string.message_requests_load_more) {
                        visibleOlderRequestCount = min(visibleOlderRequestCount + 10, olderRequests.size)
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp, start = 28.dp, end = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.message_requests_recent_empty_description),
                        color = colors.secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.message_requests_view_all),
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            visibleOlderRequestCount = min(10, olderRequests.size)
                            showingOlderRequests = true
                        }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.message_requests_hidden_preferences),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMuteSettings)
                    .padding(vertical = 22.dp),
            )
        }
    }
}

@Composable
private fun MessageRequestActionDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_requests_action_title)) },
        text = { Text(stringResource(R.string.message_requests_action_message)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAccept) { Text(stringResource(R.string.message_requests_accept)) }
                TextButton(onClick = onReport) {
                    Text(stringResource(R.string.message_requests_report), color = Color(0xFFD84B57))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.message_requests_delete), color = Color(0xFFD84B57))
                }
                TextButton(onClick = onBlock) {
                    Text(stringResource(R.string.message_requests_block_user), color = Color(0xFFD84B57))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
        dismissButton = {},
    )
}

@Composable
fun RequestListRow(
    request: MessageRequest,
    onTap: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    isEditing: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isEditing) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF0A84FF) else colors.secondary,
                modifier = Modifier.size(28.dp).clickable(onClick = onToggleSelection),
            )
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.12f))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            request.senderProfileImagePath?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } ?: Icon(Icons.Filled.Person, null, tint = colors.secondary)
        }
        Row(
            Modifier.weight(1f).clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    request.senderUsername ?: stringResource(R.string.messaging_user_default),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    request.messagePreview(context),
                    color = colors.secondary,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${request.messageCount}/5",
                    color = colors.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                MomentsFormat.relativeTime(from = request.lastActivityAt),
                color = colors.secondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        if (!isEditing) {
            Box(
                Modifier
                    .size(34.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreHoriz, null, tint = colors.secondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun requestKey(request: MessageRequest): String =
    request.id ?: "${request.senderId}_${request.timestamp.time}"

private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1_000L
