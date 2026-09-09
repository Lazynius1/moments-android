package com.moments.android.views.messaging.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ChatRecoveryGateView
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.launch

@Composable private fun sheetCopy(key: String) = stringResource(groupStringId(key))

@Composable
internal fun GroupJoinLinkDialog(link: GroupInviteLink, onDismiss: () -> Unit, onJoined: () -> Unit, onViewRequests: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember(link.groupId, link.token) { GroupJoinSheetStore(scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(store, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { store.refreshPending() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); store.stop() }
    }
    LaunchedEffect(store.joined) { if (store.joined) onJoined() }
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) { _ ->
        ChatRecoveryGateView(onCancel = onDismiss) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(horizontal = 24.dp).padding(top = 36.dp, bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    GroupJoinSheetHeader(store.phase, store.name, store.image, store.requiresApproval, store.errorTitle, store.errorBody)
                    GroupJoinSheetActions(store.phase, store.busy, store.requiresApproval,
                        onSubmit = { scope.launch { store.submit() } },
                        onRetry = { scope.launch { store.retry() } },
                        onCancel = { scope.launch { store.cancel() } },
                        onClose = onDismiss, onRequests = onViewRequests)
                }
                val colors = rememberAdaptiveColors()
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp)) {
                    Icon(Icons.Default.Close, sheetCopy("sheet.close"), Modifier.size(32.dp)
                        .background(colors.primary.copy(alpha = .06f), CircleShape).padding(8.dp), tint = colors.primary)
                }
            }
            LaunchedEffect(link.groupId, link.token) { store.load(link) }
        }
    }
}

@Composable
private fun GroupJoinSheetHeader(phase: GroupJoinSheetPhase, name: String, image: String, requiresApproval: Boolean,
    errorTitle: String, errorBody: String) {
    val colors = rememberAdaptiveColors()
    val title = when (phase) {
        GroupJoinSheetPhase.LOADING -> sheetCopy("sheet.loadingTitle")
        GroupJoinSheetPhase.SENT -> sheetCopy("sheet.sentTitle")
        GroupJoinSheetPhase.PENDING -> sheetCopy("sheet.pendingTitle")
        GroupJoinSheetPhase.ERROR -> sheetCopy(errorTitle)
        GroupJoinSheetPhase.UNAVAILABLE -> sheetCopy("sheet.unavailableTitle")
        GroupJoinSheetPhase.FULL -> sheetCopy("sheet.fullTitle")
        else -> name
    }
    val bodyKey = when (phase) {
        GroupJoinSheetPhase.LOADING -> "sheet.loadingBody"
        GroupJoinSheetPhase.DIRECT -> "sheet.directBody"
        GroupJoinSheetPhase.APPROVAL -> "sheet.approvalBody"
        GroupJoinSheetPhase.SENDING -> if (requiresApproval) "sheet.sendingBody" else "sheet.joiningBody"
        GroupJoinSheetPhase.SENT -> "sheet.sentBody"
        GroupJoinSheetPhase.PENDING -> "sheet.pendingBody"
        GroupJoinSheetPhase.ERROR -> errorBody
        GroupJoinSheetPhase.UNAVAILABLE -> "sheet.unavailableBody"
        GroupJoinSheetPhase.FULL -> "sheet.fullBody"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.height(112.dp).fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            when (phase) {
                GroupJoinSheetPhase.LOADING -> CircularProgressIndicator(Modifier.size(36.dp), color = colors.secondary)
                GroupJoinSheetPhase.SENT -> GroupRequestSentAnimation()
                GroupJoinSheetPhase.PENDING -> Box {
                    GroupChatAvatar(image, size = 104.dp)
                    Icon(Icons.Default.Schedule, null, Modifier.align(Alignment.BottomEnd).size(32.dp)
                        .background(colors.surfaceBackground, CircleShape).padding(4.dp), tint = colors.secondary)
                }
                GroupJoinSheetPhase.ERROR, GroupJoinSheetPhase.UNAVAILABLE, GroupJoinSheetPhase.FULL ->
                    Icon(when (phase) {
                        GroupJoinSheetPhase.ERROR -> Icons.Default.ErrorOutline
                        GroupJoinSheetPhase.FULL -> Icons.Default.Groups
                        else -> Icons.Default.LinkOff
                    }, null, Modifier.size(48.dp), tint = colors.secondary)
                else -> GroupChatAvatar(image, size = 104.dp)
            }
        }
        Text(title, color = colors.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (phase == GroupJoinSheetPhase.SENT || phase == GroupJoinSheetPhase.PENDING) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.secondary, textAlign = TextAlign.Center)
        }
        Text(sheetCopy(bodyKey), style = MaterialTheme.typography.bodyMedium, color = colors.secondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GroupJoinSheetActions(phase: GroupJoinSheetPhase, busy: Boolean, requiresApproval: Boolean,
    onSubmit: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit, onClose: () -> Unit, onRequests: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (phase) {
            GroupJoinSheetPhase.LOADING -> Unit
            GroupJoinSheetPhase.DIRECT, GroupJoinSheetPhase.APPROVAL -> {
                GroupJoinPrimaryButton(sheetCopy(if (phase == GroupJoinSheetPhase.APPROVAL) "sheet.requestAction" else "joinLink"), onClick = onSubmit)
                if (phase == GroupJoinSheetPhase.APPROVAL) Text(sheetCopy("sheet.approvalFooter"),
                    style = MaterialTheme.typography.bodySmall, color = colors.secondary, textAlign = TextAlign.Center)
            }
            GroupJoinSheetPhase.SENDING -> GroupJoinPrimaryButton(sheetCopy(if (requiresApproval) "sheet.sendingAction" else "sheet.joiningAction"), loading = true, onClick = {})
            GroupJoinSheetPhase.SENT -> {
                GroupJoinPrimaryButton(sheetCopy("sheet.done"), onClick = onClose)
                TextButton(onClick = onRequests, colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)) { Text(sheetCopy("sheet.viewRequests")) }
            }
            GroupJoinSheetPhase.PENDING -> {
                GroupJoinPrimaryButton(sheetCopy("sheet.viewRequests"), enabled = !busy, onClick = onRequests)
                TextButton(onClick = onCancel, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)) {
                    if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(sheetCopy(if (busy) "sheet.cancelling" else "requestsCancel"))
                }
            }
            GroupJoinSheetPhase.ERROR -> {
                GroupJoinPrimaryButton(sheetCopy("requestsRetry"), onClick = onRetry)
                TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = colors.primary)) { Text(sheetCopy("sheet.close")) }
            }
            GroupJoinSheetPhase.UNAVAILABLE, GroupJoinSheetPhase.FULL -> GroupJoinPrimaryButton(sheetCopy("sheet.done"), onClick = onClose)
        }
    }
}

@Composable
private fun GroupJoinPrimaryButton(title: String, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Button(onClick = onClick, enabled = enabled && !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.surfaceBackground,
            disabledContainerColor = colors.primary.copy(alpha = .35f), disabledContentColor = colors.surfaceBackground)) {
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = colors.surfaceBackground, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GroupRequestSentAnimation() {
    if (MotionPolicy.reduceMotion) {
        Icon(Icons.Default.Check, null, Modifier.size(64.dp), tint = Color(0xFF2DAAF5))
    } else {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.group_join_request_sent))
        val progress by animateLottieCompositionAsState(composition, iterations = 1, isPlaying = composition != null)
        if (composition == null || progress >= .999f) {
            Icon(Icons.Default.Check, null, Modifier.size(64.dp), tint = Color(0xFF2DAAF5))
        } else {
            LottieAnimation(composition, progress = { progress }, modifier = Modifier.size(112.dp).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f })
        }
    }
}
