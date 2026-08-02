package com.moments.android.views.echoes

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sensors
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.models.EchoStatus
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsSheetHeader
import java.util.Date

/**
 * Port de `EchoHistoryView.swift` en [MomentsModalSheet].
 * Sheet Android: sin chevron (dismiss = handle/swipe); cabecera pegada al handle.
 * Tap → fullScreenCover [EchoViewerUI].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoHistoryView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black
    var echoes by remember { mutableStateOf<List<Echo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedEcho by remember { mutableStateOf<Echo?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }
    val activeCount = echoes.count { it.status == EchoStatus.ACTIVE }

    DisposableEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        var registration: ListenerRegistration? = null
        if (userId == null) {
            isLoading = false
        } else {
            registration = EchoService.fetchEchoHistory(userId) { fetched ->
                echoes = fetched
                isLoading = false
            }
        }
        onDispose { registration?.remove() }
    }

    // weight desde el sheet host: lista scrollea; empty/loading arriba (visible en medium).
    Column(modifier.fillMaxSize().background(canvas)) {
        MomentsSheetHeader(
            title = stringResource(R.string.echo_history_title),
            titleSize = 18.sp,
            trailing = {
                Box(
                    Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { showInfoSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
                }
            },
        )
        when {
            isLoading -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MomentsCircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            echoes.isEmpty() -> {
                // Pegado arriba (visible en medium), no centrado en altura expanded
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 40.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    EchoHistoryEmpty(primary = primary)
                }
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // ≡ waveform.path.ecg / dot.radiowaves.left.and.right
                        InfoChip(icon = Icons.Filled.GraphicEq, text = "${echoes.size} Echoes", primary = primary)
                        InfoChip(
                            icon = Icons.Filled.Sensors,
                            text = "$activeCount ${stringResource(R.string.echo_status_active)}",
                            primary = primary,
                        )
                    }
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(echoes, key = { it.id.orEmpty() }) { echo ->
                            EchoHistoryCard(
                                echo = echo,
                                primary = primary,
                                onTap = { selectedEcho = echo },
                            )
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }

    // ≡ .fullScreenCover(item: $selectedEcho)
    selectedEcho?.let { echo ->
        Dialog(
            onDismissRequest = { selectedEcho = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            EchoViewerUI(
                echoId = echo.id.orEmpty(),
                initialEcho = echo,
                onDismiss = { selectedEcho = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showInfoSheet) {
        MomentsModalSheet(
            onDismissRequest = { showInfoSheet = false },
            largeOnly = false,
            containerColor = canvas,
        ) { dismiss ->
            EchoHistoryInfoSheet(onDismiss = dismiss, primary = primary)
        }
    }
}

@Composable
private fun EchoHistoryEmpty(primary: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 40.dp),
    ) {
        EchoesIconView(
            size = EchoesIconMetrics.historyEmpty,
            gradient = EchoesIconGradients.brandDiagonal,
        )
        Text(
            stringResource(R.string.echo_history_empty_title),
            color = primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.echo_history_empty_subtitle),
            color = primary.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, primary: Color) {
    Row(
        Modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = primary, modifier = Modifier.size(12.dp))
        Text(text, color = primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EchoHistoryCard(
    echo: Echo,
    primary: Color,
    onTap: () -> Unit,
) {
    val now = remember { Date() }
    val isIncomplete = !echo.expiresAt.after(now) && !echo.hasMinimumMomentParticipants
    val statusColor = when {
        isIncomplete -> Color(0xFFFF9500)
        echo.status == EchoStatus.PENDING -> Color(0xFFFF9500)
        echo.status == EchoStatus.ACTIVE -> Color(0xFF34C759)
        echo.status == EchoStatus.EXPIRED -> Color.Gray
        echo.status == EchoStatus.COMPLETED -> Color(0xFFAF52DE)
        else -> Color.Gray
    }
    val statusText = when {
        isIncomplete -> stringResource(R.string.echo_status_incomplete)
        echo.status == EchoStatus.PENDING -> stringResource(R.string.echo_status_pending)
        echo.status == EchoStatus.ACTIVE -> stringResource(R.string.echo_status_active)
        echo.status == EchoStatus.EXPIRED -> stringResource(R.string.echo_status_expired)
        echo.status == EchoStatus.COMPLETED -> stringResource(R.string.echo_status_completed)
        else -> echo.status.raw
    }
    val preview = echo.moments.lastOrNull()?.thumbnailUrl ?: echo.moments.lastOrNull()?.mediaUrl
    val expiresLabel = if (!echo.expiresAt.after(now)) {
        stringResource(R.string.echo_status_expired)
    } else {
        MomentsFormat.relativeTime(echo.expiresAt, MomentsFormat.RelativeTimeStyle.CONVERSATIONAL)
    }
    val count = echo.participants.size
    val participantsLabel = if (count == 1) {
        stringResource(R.string.echo_participants_singular, count)
    } else {
        stringResource(R.string.echo_participants_plural, count)
    }
    val secondary = primary.copy(alpha = 0.6f)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            if (!preview.isNullOrBlank()) {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            } else {
                EchoesIconView(
                    size = EchoesIconMetrics.historyRow,
                    gradient = EchoesIconGradients.brandHorizontal,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                echo.locationName?.takeIf { it.isNotBlank() } ?: "Echo",
                color = primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(participantsLabel, color = secondary, fontSize = 12.sp)
                Text("•", color = secondary, fontSize = 12.sp)
                Text(expiresLabel, color = secondary, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = secondary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun EchoHistoryInfoSheet(onDismiss: () -> Unit, primary: Color) {
    val secondary = primary.copy(alpha = 0.6f)
    Column(Modifier.padding(bottom = 24.dp)) {
        MomentsSheetHeader(title = stringResource(R.string.echo_info_title), titleSize = 18.sp)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            InfoRow(stringResource(R.string.echo_info_what_title), stringResource(R.string.echo_info_what_body), primary, secondary)
            InfoRow(stringResource(R.string.echo_info_how_title), stringResource(R.string.echo_info_how_body), primary, secondary)
            InfoRow(stringResource(R.string.echo_info_privacy_title), stringResource(R.string.echo_info_privacy_body), primary, secondary)
            InfoRow(stringResource(R.string.echo_info_status_title), stringResource(R.string.echo_info_status_body), primary, secondary)
            InfoRow(stringResource(R.string.echo_info_controls_title), stringResource(R.string.echo_info_controls_body), primary, secondary)
        }
    }
}

@Composable
private fun InfoRow(title: String, body: String, primary: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = secondary, fontSize = 14.sp)
    }
}
