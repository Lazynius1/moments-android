@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moments.android.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.ui.theme.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

// MARK: - Estado de cuenta (resuelto tras login, equivalente a checkAccountStatus de iOS)
sealed interface AccountState {
    data object Loading : AccountState
    data object Active : AccountState
    data class Suspended(val reason: String?, val expiresAt: Long?) : AccountState
    data class Deactivated(val username: String?, val email: String?, val profileImagePath: String?) : AccountState
}

suspend fun resolveAccountState(uid: String): AccountState {
    val firestore = FirebaseFirestore.getInstance()
    val data = runCatching { firestore.collection("users").document(uid).get().await().data }.getOrNull()
        ?: return AccountState.Active

    val isSuspended = data["isSuspended"] as? Boolean ?: false
    if (isSuspended) {
        val until = data["suspendedUntil"] as? Timestamp
        val reason = data["suspensionReason"] as? String
        if (until != null) {
            val expMillis = until.toDate().time
            if (System.currentTimeMillis() > expMillis) {
                runCatching {
                    firestore.collection("users").document(uid).update(
                        mapOf(
                            "isSuspended" to false,
                            "suspendedUntil" to FieldValue.delete(),
                            "suspensionReason" to FieldValue.delete(),
                        ),
                    ).await()
                }
            } else {
                return AccountState.Suspended(reason, expMillis)
            }
        } else {
            return AccountState.Suspended(reason, null)
        }
    }

    val isActive = data["isActive"] as? Boolean ?: true
    if (!isActive) {
        return AccountState.Deactivated(
            username = data["username"] as? String,
            email = data["email"] as? String,
            profileImagePath = data["profileImagePath"] as? String,
        )
    }
    return AccountState.Active
}

private fun signOut() = FirebaseAuth.getInstance().signOut()

// MARK: - Cuenta desactivada (equivalente a DeactivatedAccountView)
@Composable
fun DeactivatedScreen(state: AccountState.Deactivated, onReactivated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isReactivating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Surface).systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text("🌙", fontSize = 34.sp)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.deactivated_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.deactivated_subtitle), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.72f), textAlign = TextAlign.Center, lineHeight = 21.sp)
            Spacer(Modifier.height(28.dp))

            ProfileCard(state.username, state.email, state.profileImagePath)

            Spacer(Modifier.height(28.dp))
            AuthPrimaryButton(
                text = stringResource(R.string.deactivated_reactivate),
                isLoading = isReactivating,
                modifier = Modifier.widthIn(max = 400.dp),
            ) {
                isReactivating = true
                scope.launch {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    val ok = uid != null && runCatching {
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update(mapOf("isActive" to true, "deactivatedAt" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp())).await()
                    }.isSuccess
                    isReactivating = false
                    if (ok) onReactivated() else error = "No se pudo reactivar la cuenta."
                }
            }
            Spacer(Modifier.height(12.dp))
            AuthOutlineButton(text = stringResource(R.string.settings_logout), modifier = Modifier.widthIn(max = 400.dp)) { signOut() }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.login_ok)) } },
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(error ?: "") },
        )
    }
}

@Composable
private fun ProfileCard(username: String?, email: String?, profileImagePath: String?) {
    val name = username ?: stringResource(R.string.profile_default_username)
    Box(
        Modifier.fillMaxWidth().widthIn(max = 400.dp).height(340.dp).clip(RoundedCornerShape(30.dp)).background(AuthColors.subtle(0.08f)),
    ) {
        if (!profileImagePath.isNullOrEmpty()) {
            AsyncImage(model = profileImagePath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = AuthColors.secondary(0.48f), modifier = Modifier.size(56.dp))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.55f)))))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, maxLines = 1)
                if (!email.isNullOrEmpty()) {
                    Text(email, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.68f), maxLines = 1)
                }
            }
            Text(
                "⏸ ${stringResource(R.string.deactivated_status)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuthColors.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.subtle(0.14f)).padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

// MARK: - Cuenta suspendida (equivalente a SuspendedAccountView)
@Composable
fun SuspendedScreen(state: AccountState.Suspended) {
    var showAppeal by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Surface).systemBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Text("🛡️", fontSize = 44.sp)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.suspended_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.suspended_subtitle), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.72f), textAlign = TextAlign.Center, lineHeight = 21.sp)
            Spacer(Modifier.height(34.dp))

            Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (!state.reason.isNullOrEmpty()) {
                    InfoRow(Icons.Filled.Info, stringResource(R.string.suspended_reason), state.reason)
                }
                if (state.expiresAt != null) {
                    InfoRow(Icons.Filled.DateRange, stringResource(R.string.suspended_expires), formatDate(state.expiresAt))
                    CountdownTimer(state.expiresAt)
                } else {
                    InfoRow(Icons.Filled.Lock, stringResource(R.string.suspended_permanent), stringResource(R.string.suspended_permanent_msg))
                }
                InfoRow(Icons.Filled.Info, stringResource(R.string.suspended_what_can_do), stringResource(R.string.suspended_what_can_do_msg))
            }

            Spacer(Modifier.height(34.dp))
            Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthPrimaryButton(text = stringResource(R.string.suspended_appeal)) { showAppeal = true }
                AuthOutlineButton(text = stringResource(R.string.suspended_logout)) { signOut() }
            }
            Spacer(Modifier.height(34.dp))
        }
    }

    if (showAppeal) {
        AppealSheet(reason = state.reason, onDismiss = { showAppeal = false })
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = AuthColors.primary, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary)
            Text(message, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.72f), lineHeight = 20.sp)
        }
    }
}

@Composable
private fun CountdownTimer(expiresAt: Long) {
    var remaining by remember { mutableLongStateOf((expiresAt - System.currentTimeMillis()).coerceAtLeast(0)) }
    LaunchedEffect(expiresAt) {
        while (remaining > 0) {
            remaining = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
    }
    if (remaining <= 0) return
    val totalSec = remaining / 1000
    val days = (totalSec / (24 * 3600)).toInt()
    val hours = ((totalSec % (24 * 3600)) / 3600).toInt()
    val minutes = ((totalSec % 3600) / 60).toInt()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AuthColors.subtle(0.06f)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.suspended_time_remaining), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.72f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeComponent(days, stringResource(R.string.suspended_days))
            TimeComponent(hours, stringResource(R.string.suspended_hours))
            TimeComponent(minutes, stringResource(R.string.suspended_minutes))
        }
    }
}

@Composable
private fun TimeComponent(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$value", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, modifier = Modifier.width(58.dp), textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.7f))
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(millis))

// MARK: - Formulario de apelación (equivalente a EnhancedContactSupportView, envío simulado como iOS)
@Composable
private fun AppealSheet(reason: String?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val successMessage = stringResource(R.string.suspended_contact_success)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.suspended_contact_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.suspended_contact_subtitle), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.72f), textAlign = TextAlign.Center)

            AuthTextField(icon = Icons.Filled.MailOutline, placeholder = stringResource(R.string.suspended_contact_email), value = email, onValueChange = { email = it }, keyboardType = KeyboardType.Email)
            AppealMessageField(message = message, onChange = { message = it })

            AuthPrimaryButton(text = stringResource(R.string.suspended_contact_send), isLoading = isLoading, isEnabled = email.isNotBlank() && message.isNotBlank()) {
                isLoading = true
                scope.launch {
                    delay(1500) // envío simulado como iOS
                    isLoading = false
                    sent = true
                }
            }
        }
    }

    if (sent) {
        AlertDialog(
            onDismissRequest = { sent = false; onDismiss() },
            confirmButton = { TextButton(onClick = { sent = false; onDismiss() }) { Text(stringResource(R.string.login_ok)) } },
            text = { Text(successMessage) },
        )
    }
}

@Composable
private fun AppealMessageField(message: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(shape).background(AuthColors.subtle(0.05f)).padding(16.dp)) {
        if (message.isEmpty()) {
            Text(stringResource(R.string.suspended_contact_placeholder), fontSize = 16.sp, color = AuthColors.secondary(0.52f))
        }
        BasicTextField(
            value = message,
            onValueChange = onChange,
            textStyle = TextStyle(color = AuthColors.primary, fontSize = 16.sp),
            cursorBrush = SolidColor(AuthColors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
