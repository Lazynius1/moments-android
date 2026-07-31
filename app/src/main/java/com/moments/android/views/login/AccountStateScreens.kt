@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moments.android.views.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.reportes.AppealFormView
import com.moments.android.reportes.AppealStatusView
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

// MARK: - Cuenta desactivada (≡ DeactivatedAccountView.swift)
@Composable
fun DeactivatedScreen(state: AccountState.Deactivated, onReactivated: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isVerifying by AuthService.isVerifyingAccount.collectAsState()
    var isReactivating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    LaunchedEffect(isVerifying, isReactivating) {
        if (!isVerifying && isReactivating) {
            delay(500)
            isReactivating = false
        }
    }

    Box(Modifier.fillMaxSize().background(Surface)) {
        WelcomeAuroraHalo(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp),
            size = 420.dp,
        )

        if (isVerifying) {
            DeactivationLoadingView()
        } else {
            DeactivationContent(
                state = state,
                isReactivating = isReactivating,
                isVisible = isVisible,
                onReactivate = {
                    isReactivating = true
                    scope.launch {
                        try {
                            AuthService.reactivateAccount()
                            onReactivated()
                        } catch (e: Exception) {
                            isReactivating = false
                            error = e.message ?: e.localizedMessage
                        }
                    }
                },
                onLogout = { AuthService.logout() },
            )
        }
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = {
                TextButton(onClick = { error = null; isReactivating = false }) {
                    Text(stringResource(R.string.login_ok))
                }
            },
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(error ?: "") },
        )
    }
}

@Composable
private fun DeactivationLoadingView() {
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.deactivated_reactivating),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AuthColors.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.deactivated_verifying),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = AuthColors.secondary(0.72f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeactivationContent(
    state: AccountState.Deactivated,
    isReactivating: Boolean,
    isVisible: Boolean,
    onReactivate: () -> Unit,
    onLogout: () -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
        label = "deactivatedAppearAlpha",
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 12f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
        label = "deactivatedAppearOffset",
    )

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .offset(y = contentOffset.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Outlined.DarkMode,
            contentDescription = null,
            tint = AuthColors.primary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.deactivated_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AuthColors.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.deactivated_subtitle),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AuthColors.secondary(0.72f),
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(28.dp))

        DeactivatedProfileCard(state.username, state.email, state.profileImagePath)

        Spacer(Modifier.height(28.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.deactivated_reactivate),
            isLoading = isReactivating,
            modifier = Modifier.widthIn(max = 400.dp),
            onClick = onReactivate,
        )
        Spacer(Modifier.height(12.dp))
        AuthOutlineButton(
            text = stringResource(R.string.settings_logout),
            modifier = Modifier.widthIn(max = 400.dp),
            onClick = onLogout,
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun DeactivatedProfileCard(username: String?, email: String?, profileImagePath: String?) {
    val name = username ?: stringResource(R.string.profile_default_username)
    val dark = isSystemInDarkTheme()
    val fadeColor = if (dark) Color.Black else Color.White
    val shape = RoundedCornerShape(30.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp)
            .height(340.dp)
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.1f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.24f else 0.1f),
            )
            .clip(shape)
            .border(0.8.dp, AuthColors.subtle(0.14f), shape)
            .background(AuthColors.subtle(0.08f)),
    ) {
        if (!profileImagePath.isNullOrEmpty()) {
            AsyncImage(
                model = profileImagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = AuthColors.secondary(0.48f),
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            fadeColor.copy(alpha = 0.1f),
                            fadeColor.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary, maxLines = 1)
                if (!email.isNullOrEmpty()) {
                    Text(
                        email,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AuthColors.secondary(0.68f),
                        maxLines = 1,
                    )
                }
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AuthColors.subtle(0.14f))
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Pause, contentDescription = null, tint = AuthColors.primary, modifier = Modifier.size(10.dp))
                Text(
                    stringResource(R.string.deactivated_status),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthColors.primary,
                )
            }
        }
    }
}

// MARK: - Cuenta suspendida (≡ SuspendedAccountView.swift)
@Composable
fun SuspendedScreen(state: AccountState.Suspended) {
    var showContactForm by remember { mutableStateOf(false) }
    var showAppealsStatus by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
        label = "suspendedAppearAlpha",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
        label = "suspendedAppearScale",
    )

    Box(Modifier.fillMaxSize().background(Surface)) {
        WelcomeAuroraHalo(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp),
            size = 420.dp,
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .scale(contentScale)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = AuthColors.primary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.suspended_title),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = AuthColors.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.suspended_subtitle),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AuthColors.secondary(0.72f),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(34.dp))

            Column(Modifier.widthIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (!state.reason.isNullOrEmpty()) {
                    SuspendedInfoRow(
                        Icons.Outlined.Description,
                        stringResource(R.string.suspended_reason),
                        state.reason,
                    )
                }
                if (state.expiresAt != null) {
                    SuspendedInfoRow(
                        Icons.Outlined.Schedule,
                        stringResource(R.string.suspended_expires),
                        MomentsFormat.smartDate(Date(state.expiresAt), MomentsFormat.DateContext.FULL_DATE_TIME),
                    )
                    CountdownTimer(state.expiresAt)
                } else {
                    SuspendedInfoRow(
                        Icons.Filled.AllInclusive,
                        stringResource(R.string.suspended_permanent),
                        stringResource(R.string.suspended_permanent_msg),
                    )
                }
                SuspendedInfoRow(
                    Icons.Outlined.Info,
                    stringResource(R.string.suspended_what_can_do),
                    stringResource(R.string.suspended_what_can_do_msg),
                )
            }

            Spacer(Modifier.height(34.dp))
            Column(
                Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthOutlineButton(
                    text = stringResource(R.string.suspended_view_appeals),
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.ManageSearch,
                            contentDescription = null,
                            tint = AuthColors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) { showAppealsStatus = true }

                AuthPrimaryButton(text = stringResource(R.string.suspended_appeal)) {
                    showContactForm = true
                }

                AuthOutlineButton(
                    text = stringResource(R.string.suspended_logout),
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = AuthColors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) { AuthService.logout() }
            }
            Spacer(Modifier.height(34.dp))
        }
    }

    // ≡ iOS `.sheet` AppealFormView · presentationDetents([.medium, .large])
    if (showContactForm) {
        MomentsModalSheet(
            onDismissRequest = { showContactForm = false },
            largeOnly = false,
        ) {
            AppealFormView(
                suspensionReason = state.reason,
                onDismiss = { showContactForm = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ≡ iOS `.sheet` AppealStatusView · presentationDetents([.medium, .large])
    if (showAppealsStatus) {
        MomentsModalSheet(
            onDismissRequest = { showAppealsStatus = false },
            largeOnly = false,
        ) {
            AppealStatusView(
                onBack = { showAppealsStatus = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SuspendedInfoRow(icon: ImageVector, title: String, message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = AuthColors.primary, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary)
            Text(
                message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AuthColors.secondary(0.72f),
                lineHeight = 20.sp,
            )
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
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AuthColors.subtle(0.06f))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.suspended_time_remaining),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AuthColors.secondary(0.72f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeComponent(days, stringResource(R.string.suspended_days))
            TimeComponent(hours, stringResource(R.string.suspended_hours))
            TimeComponent(minutes, stringResource(R.string.suspended_minutes))
        }
    }
}

@Composable
private fun TimeComponent(value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "$value",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = AuthColors.primary,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.Center,
        )
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.7f))
    }
}
