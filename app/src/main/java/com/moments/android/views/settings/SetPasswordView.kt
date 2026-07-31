package com.moments.android.views.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.login.reauthenticateWithGoogle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `SetPasswordView.swift` (349 líneas).
 * Apple Sign In → Google reauth (paridad Android ya usada en AccountManagement / Security).
 */
@Composable
fun SetPasswordView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firebaseUser by AuthService.currentFirebaseUser.collectAsState()

    var emailInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var googleIdentityVerified by remember { mutableStateOf(false) }
    var verificationErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var didUpdateEmail by remember { mutableStateOf(false) }

    val requiresGoogleVerification = AuthService.isGoogleLinked
    val needsEditableEmail = AuthService.requiresBackupEmailSetup
    val normalizedEmailInput = emailInput.trim().lowercase()
    val currentAuthEmail = firebaseUser?.email?.trim()?.lowercase().orEmpty()
    val shouldUpdateEmailBeforeLinking =
        needsEditableEmail || normalizedEmailInput != currentAuthEmail

    val passwordsValid = newPassword.length >= 8 && newPassword == confirmPassword
    val emailValid = AuthService.isValidEmail(normalizedEmailInput) &&
        !isPrivateRelayEmail(normalizedEmailInput)
    val isFormValid = if (requiresGoogleVerification) {
        passwordsValid && emailValid && googleIdentityVerified
    } else {
        passwordsValid && emailValid
    }

    LaunchedEffect(firebaseUser) {
        when (AuthService.backupEmailStatus) {
            AuthService.BackupEmailStatus.USABLE ->
                emailInput = firebaseUser?.email.orEmpty()
            AuthService.BackupEmailStatus.MISSING ->
                emailInput = ""
        }
    }

    fun linkPassword() {
        scope.launch {
            try {
                AuthService.linkPassword(normalizedEmailInput, newPassword)
                isLoading = false
                HapticManager.shared.success()
                showSuccess = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.localizedMessage ?: e.message.orEmpty()
                showError = true
            }
        }
    }

    fun savePassword() {
        if (!isFormValid) return
        isLoading = true
        if (shouldUpdateEmailBeforeLinking) {
            scope.launch {
                try {
                    AuthService.updateAccountEmail(normalizedEmailInput)
                    didUpdateEmail = true
                    linkPassword()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: e.message.orEmpty()
                    showError = true
                }
            }
        } else {
            linkPassword()
        }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.settings_security_password_add),
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.VpnKey, null, tint = accent, modifier = Modifier.size(50.dp))
                Text(
                    stringResource(R.string.settings_security_password_add),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(
                        if (needsEditableEmail) {
                            R.string.settings_security_password_add_sheet_description_no_email
                        } else {
                            R.string.settings_security_password_add_sheet_description
                        },
                    ),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Email section
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.settings_security_password_account_email),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
                if (needsEditableEmail) {
                    TextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        placeholder = {
                            Text(stringResource(R.string.settings_security_password_enter_email))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                        ),
                    )
                    Text(
                        stringResource(R.string.settings_security_password_email_required),
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.65f),
                    )
                } else {
                    Text(
                        emailInput,
                        fontSize = 15.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                    )
                    Text(
                        stringResource(R.string.settings_security_password_email_login_hint),
                        fontSize = 12.sp,
                        color = Color.Gray.copy(alpha = 0.65f),
                    )
                }
            }

            // Google verification (≡ Apple verification on iOS)
            if (requiresGoogleVerification) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_security_password_verify_google),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                    )
                    if (googleIdentityVerified) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF34C759).copy(0.1f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF34C759))
                            Text(
                                stringResource(R.string.account_management_identity_verified),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                        }
                    } else {
                        SetPasswordGoogleButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    verificationErrorMessage = null
                                    try {
                                        reauthenticateWithGoogle(context)
                                        googleIdentityVerified = true
                                        verificationErrorMessage = null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        if (e.message?.contains("cancel", ignoreCase = true) != true) {
                                            googleIdentityVerified = false
                                            verificationErrorMessage =
                                                e.localizedMessage ?: e.message.orEmpty()
                                        }
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                        )
                    }
                    verificationErrorMessage?.let { msg ->
                        Text(msg, fontSize = 12.sp, color = Color(0xFFFF3B30))
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SetPasswordField(
                    title = stringResource(R.string.password_change_new_password),
                    placeholder = stringResource(R.string.password_change_new_password_placeholder),
                    value = newPassword,
                    visible = showNewPassword,
                    textColor = textColor,
                    onValueChange = { newPassword = it },
                    onToggle = { showNewPassword = !showNewPassword },
                )
                SetPasswordField(
                    title = stringResource(R.string.password_change_confirm_password),
                    placeholder = stringResource(R.string.password_change_confirm_password_placeholder),
                    value = confirmPassword,
                    visible = showConfirmPassword,
                    textColor = textColor,
                    onValueChange = { confirmPassword = it },
                    onToggle = { showConfirmPassword = !showConfirmPassword },
                )
            }

            val canTap = isFormValid && !isLoading
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        if (isFormValid) accent else Color.Gray,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = canTap, onClick = ::savePassword)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = if (isFormValid) {
                            SettingsProfileColors.accentContrastingText(isDark)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(R.string.settings_security_password_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFormValid) {
                        SettingsProfileColors.accentContrastingText(isDark)
                    } else {
                        Color.White
                    },
                )
            }
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
    if (showSuccess) {
        AlertDialog(
            onDismissRequest = {
                showSuccess = false
                onNavigateBack()
            },
            title = { Text(stringResource(R.string.settings_security_password_add_success)) },
            text = {
                Text(
                    stringResource(
                        if (didUpdateEmail) {
                            R.string.settings_security_password_add_success_message_verify
                        } else {
                            R.string.settings_security_password_add_success_message
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuccess = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun SetPasswordField(
    title: String,
    placeholder: String,
    value: String,
    visible: Boolean,
    textColor: Color,
    onValueChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Color.Gray) },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(22.dp)
                    .clickable(onClick = onToggle),
            )
        }
    }
}

@Composable
private fun SetPasswordGoogleButton(onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color.White else Color.Black
    val fg = if (isDark) Color.Black else Color.White
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.google_icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.auth_google_continue),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

/** ≡ iOS `AuthService.isApplePrivateRelayEmail` (por si llega un relay en datos). */
private fun isPrivateRelayEmail(email: String): Boolean =
    Regex("@privaterelay\\.appleid\\.com$", RegexOption.IGNORE_CASE).containsMatchIn(email.trim())
