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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.coordinators.AppRouter
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.models.EchoStatus
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import java.util.Date

/**
 * Port 1:1 de `EchoHistoryView.swift`.
 * Tap → [AppRouter.Destination.Echo] (visor completo cuando exista).
 */
@Composable
fun EchoHistoryView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    var echoes by remember { mutableStateOf<List<Echo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
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

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EchoHistoryHeader(
                primary = primary,
                onDismiss = onDismiss,
                onInfo = { showInfoSheet = true },
            )
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary, strokeWidth = 2.dp)
                }
                echoes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EchoHistoryEmpty(primary = primary)
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InfoChip(text = "${echoes.size} Echoes", primary = primary)
                        InfoChip(
                            text = "$activeCount ${stringResource(R.string.echo_status_active)}",
                            primary = primary,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(echoes, key = { it.id.orEmpty() }) { echo ->
                            EchoHistoryCard(
                                echo = echo,
                                primary = primary,
                                onTap = {
                                    val id = echo.id ?: return@EchoHistoryCard
                                    AppRouter.navigate(AppRouter.Destination.Echo(id))
                                    onDismiss()
                                },
                            )
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }

        if (showInfoSheet) {
            EchoHistoryInfoSheet(onDismiss = { showInfoSheet = false }, primary = primary)
        }
    }
}

@Composable
private fun EchoHistoryHeader(
    primary: Color,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = primary)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Info, null, tint = primary, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            stringResource(R.string.echo_history_title),
            color = primary,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
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
private fun InfoChip(text: String, primary: Color) {
    Text(
        text,
        color = primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = secondary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun EchoHistoryInfoSheet(onDismiss: () -> Unit, primary: Color) {
    val secondary = primary.copy(alpha = 0.6f)
    Column(
        Modifier
            .fillMaxSize()
            .momentsChromeGlass(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), interactive = false)
            .padding(bottom = 24.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = primary)
            }
            Text(
                stringResource(R.string.echo_info_title),
                color = primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
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
