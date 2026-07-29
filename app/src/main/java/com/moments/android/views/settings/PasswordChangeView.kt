package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R

private data class PasswordStrength(
    val titleRes: Int,
    val color: Color,
    val percentage: Float,
)

/**
 * Port 1:1 de `PasswordChangeView.swift` (421 líneas).
 */
@Composable
fun PasswordChangeView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    var currentPasswordError by remember { mutableStateOf(false) }
    var newPasswordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val strength = evaluatePasswordStrength(newPassword)
    val isFormValid = currentPassword.isNotEmpty() &&
        newPassword.length >= 8 &&
        newPassword == confirmPassword &&
        !currentPasswordError &&
        !newPasswordError &&
        !confirmPasswordError

    fun onNewPasswordChange(value: String) {
        newPassword = value
        newPasswordError = value.isNotEmpty() && value.length < 8
        confirmPasswordError = confirmPassword.isNotEmpty() && confirmPassword != value
    }

    fun onConfirmPasswordChange(value: String) {
        confirmPassword = value
        confirmPasswordError = value.isNotEmpty() && value != newPassword
    }

    fun changePassword() {
        if (!isFormValid) return
        isLoading = true
        currentPasswordError = false

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            isLoading = false
            errorMessage = context.getString(R.string.password_change_user_not_found)
            showError = true
            return
        }

        val credential = EmailAuthProvider.getCredential(user.email.orEmpty(), currentPassword)
        user.reauthenticate(credential).addOnCompleteListener { reauth ->
            if (!reauth.isSuccessful) {
                isLoading = false
                currentPasswordError = true
                errorMessage = context.getString(R.string.password_change_current_incorrect)
                showError = true
                return@addOnCompleteListener
            }
            user.updatePassword(newPassword).addOnCompleteListener { update ->
                isLoading = false
                if (update.isSuccessful) {
                    showSuccess = true
                } else {
                    val msg = update.exception?.localizedMessage ?: update.exception?.message.orEmpty()
                    errorMessage = context.getString(R.string.password_change_update_error_prefix) + msg
                    showError = true
                }
            }
        }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.password_change_title),
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(50.dp),
                )
                Text(
                    stringResource(R.string.password_change_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.password_change_subtitle),
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PasswordFieldBlock(
                    label = stringResource(R.string.password_change_current_password),
                    placeholder = stringResource(R.string.password_change_current_password_placeholder),
                    value = currentPassword,
                    visible = showCurrentPassword,
                    hasError = currentPasswordError,
                    textColor = textColor,
                    accent = accent,
                    onValueChange = {
                        currentPassword = it
                        currentPasswordError = false
                    },
                    onToggleVisibility = { showCurrentPassword = !showCurrentPassword },
                    errorText = if (currentPasswordError) {
                        stringResource(R.string.password_change_current_password_error)
                    } else {
                        null
                    },
                )

                PasswordFieldBlock(
                    label = stringResource(R.string.password_change_new_password),
                    placeholder = stringResource(R.string.password_change_new_password_placeholder),
                    value = newPassword,
                    visible = showNewPassword,
                    hasError = newPasswordError,
                    textColor = textColor,
                    accent = accent,
                    onValueChange = ::onNewPasswordChange,
                    onToggleVisibility = { showNewPassword = !showNewPassword },
                    errorText = if (newPasswordError) {
                        stringResource(R.string.password_change_password_requirement)
                    } else {
                        null
                    },
                    strengthSlot = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(
                                    stringResource(R.string.password_change_strength),
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(strength.titleRes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = strength.color,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { strength.percentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = strength.color,
                                trackColor = Color.Gray.copy(alpha = 0.3f),
                            )
                        }
                    },
                )

                PasswordFieldBlock(
                    label = stringResource(R.string.password_change_confirm_password),
                    placeholder = stringResource(R.string.password_change_confirm_password_placeholder),
                    value = confirmPassword,
                    visible = showConfirmPassword,
                    hasError = confirmPasswordError,
                    textColor = textColor,
                    accent = accent,
                    onValueChange = ::onConfirmPasswordChange,
                    onToggleVisibility = { showConfirmPassword = !showConfirmPassword },
                    errorText = if (confirmPasswordError) {
                        stringResource(R.string.password_change_passwords_dont_match)
                    } else {
                        null
                    },
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.password_change_security_tips),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                }
                PasswordSecurityTipRow(stringResource(R.string.password_change_tip_length), accent, textColor)
                PasswordSecurityTipRow(stringResource(R.string.password_change_tip_case), accent, textColor)
                PasswordSecurityTipRow(stringResource(R.string.password_change_tip_symbols), accent, textColor)
                PasswordSecurityTipRow(stringResource(R.string.password_change_tip_personal), accent, textColor)
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
                    .clickable(enabled = canTap, onClick = ::changePassword)
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
                        Icons.Default.Lock,
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
                    text = if (isLoading) {
                        stringResource(R.string.password_change_changing)
                    } else {
                        stringResource(R.string.password_change_button)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFormValid) {
                        SettingsProfileColors.accentContrastingText(isDark)
                    } else {
                        Color.White
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
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
            title = { Text(stringResource(R.string.password_change_success_message)) },
            text = { Text(stringResource(R.string.password_change_success_message)) },
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
private fun PasswordFieldBlock(
    label: String,
    placeholder: String,
    value: String,
    visible: Boolean,
    hasError: Boolean,
    textColor: Color,
    accent: Color,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    errorText: String?,
    strengthSlot: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (hasError) Color(0xFFFF3B30) else accent.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                )
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
                imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(22.dp)
                    .clickable(onClick = onToggleVisibility),
            )
        }
        strengthSlot?.invoke()
        if (errorText != null) {
            Text(errorText, fontSize = 12.sp, color = Color(0xFFFF3B30))
        }
    }
}

@Composable
private fun PasswordSecurityTipRow(text: String, accent: Color, textColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 14.sp, color = textColor)
    }
}

private fun evaluatePasswordStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 8) score += 1
    if (password.length >= 12) score += 1
    if (password.any { it.isLowerCase() }) score += 1
    if (password.any { it.isUpperCase() }) score += 1
    if (password.any { it.isDigit() }) score += 1
    if (password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) score += 1

    return when (score) {
        in 0..2 -> PasswordStrength(R.string.password_change_strength_weak, Color(0xFFFF3B30), 0.25f)
        in 3..4 -> PasswordStrength(R.string.password_change_strength_fair, Color(0xFFFF9500), 0.5f)
        5 -> PasswordStrength(R.string.password_change_strength_good, Color(0xFFFFCC00), 0.75f)
        6 -> PasswordStrength(R.string.password_change_strength_strong, Color(0xFF34C759), 1f)
        else -> PasswordStrength(R.string.password_change_strength_very_strong, Color(0xFF34C759), 1f)
    }
}
