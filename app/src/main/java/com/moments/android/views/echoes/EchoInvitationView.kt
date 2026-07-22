package com.moments.android.views.echoes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.services.social.EchoService
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `EchoInvitationView.swift`.
 * Escucha `echoes/{echoId}`; accept/decline vía [EchoService].
 */
@Composable
fun EchoInvitationView(
    echoId: String,
    onDismiss: () -> Unit,
    onAccept: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var echo by remember(echoId) { mutableStateOf<Echo?>(null) }
    var isLoading by remember(echoId) { mutableStateOf(true) }
    var errorMessage by remember(echoId) { mutableStateOf<String?>(null) }
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.58f)
    val decodeError = stringResource(R.string.echo_invitation_error_decoding)
    val unavailable = stringResource(R.string.echo_invitation_unavailable)

    DisposableEffect(echoId) {
        var registration: ListenerRegistration? = null
        registration = FirebaseFirestore.getInstance()
            .collection("echoes")
            .document(echoId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    errorMessage = error.localizedMessage
                    isLoading = false
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    errorMessage = unavailable
                    isLoading = false
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as? Map<String, Any?>
                val decoded = data?.let { Echo.from(snapshot.id, it) }
                if (decoded == null) {
                    errorMessage = decodeError
                    isLoading = false
                } else {
                    echo = decoded
                    errorMessage = null
                    isLoading = false
                }
            }
        onDispose { registration?.remove() }
    }

    fun acceptEcho() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching { EchoService.acceptEcho(echoId, userId) }
                .onSuccess {
                    onDismiss()
                    onAccept?.invoke(echoId)
                }
        }
    }

    fun declineEcho() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching { EchoService.declineEcho(echoId, userId) }
                .onSuccess { onDismiss() }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                Box(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            echo != null -> {
                InvitationCard(
                    echo = echo!!,
                    primary = primary,
                    secondary = secondary,
                    isDark = isDark,
                    onDismiss = onDismiss,
                    onMaybeLater = { declineEcho() },
                    onJoin = { acceptEcho() },
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
            errorMessage != null -> {
                Text(
                    errorMessage.orEmpty(),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                        .padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun InvitationCard(
    echo: Echo,
    primary: Color,
    secondary: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onMaybeLater: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val joinFill = if (isDark) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.88f)
    val joinText = if (isDark) Color.Black else Color.White
    val locationFallback = stringResource(R.string.echo_viewer_location_fallback)
    val participants = echo.participants

    Column(
        modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(30.dp), interactive = false)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoesIconView(
                size = EchoesIconMetrics.invitation,
                gradient = EchoesIconGradients.brandHorizontal,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.echo_invitation_spark_detected),
                    color = primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    echo.locationName?.takeIf { it.isNotBlank() } ?: locationFallback,
                    color = secondary,
                    fontSize = 14.sp,
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = primary, modifier = Modifier.size(15.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.echo_invitation_participants),
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                participants.forEachIndexed { index, participant ->
                    AsyncProfileImageView(
                        userId = participant.userId,
                        modifier = Modifier
                            .size(44.dp)
                            .offset(x = (-10 * index).dp)
                            .clip(CircleShape),
                    )
                }
                if (participants.size > 1) {
                    val count = participants.size
                    Text(
                        text = if (count == 1) {
                            stringResource(R.string.echo_participants_singular, count)
                        } else {
                            stringResource(R.string.echo_participants_plural, count)
                        },
                        color = primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 20.dp),
                    )
                }
            }
        }

        Text(
            stringResource(R.string.echo_invitation_description),
            color = primary,
            fontSize = 15.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
                    .clickable(onClick = onMaybeLater),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.echo_invitation_maybe_later),
                    color = primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(joinFill)
                    .clickable(onClick = onJoin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.echo_invitation_join),
                    color = joinText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
