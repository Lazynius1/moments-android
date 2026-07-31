package com.moments.android.views.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.login.reauthenticateWithGoogle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Port de `AdvancedAccountManagementView` (SettingsSections.swift) +
 * `DeleteAccountVerificationView` (AccountManagement.swift).
 *
 * Verificación social: Apple → Google.
 */

private enum class AdvancedFlowDestination { MAIN, DELETE_ACCOUNT, LOGIN_ACTIVITY }

enum class AccountDeletionVerificationMethod {
    PASSWORD,
    GOOGLE,
    PASSWORD_OR_GOOGLE,
}

object AccountDeletionAuthSupport {
    fun verificationMethod(): AccountDeletionVerificationMethod {
        val providers = FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.map { it.providerId }
            ?.toSet()
            .orEmpty()
        val hasPassword = providers.contains("password")
        val hasGoogle = providers.contains("google.com")
        return when {
            hasPassword && hasGoogle -> AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE
            hasGoogle -> AccountDeletionVerificationMethod.GOOGLE
            else -> AccountDeletionVerificationMethod.PASSWORD
        }
    }
}

object AccountDeletionErrorPresenter {
    fun passwordMessage(error: Throwable, wrongPassword: String): String? {
        val auth = (error as? AuthService.AuthServiceException)?.cause as? FirebaseAuthException
            ?: error as? FirebaseAuthException
            ?: error.cause as? FirebaseAuthException
        if (auth != null) {
            when (auth.errorCode) {
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_LOGIN_CREDENTIALS",
                -> return wrongPassword
            }
        }
        val message = (error.message ?: "").lowercase()
        return if (
            message.contains("password") ||
            message.contains("credential") ||
            message.contains("contraseña")
        ) {
            wrongPassword
        } else {
            null
        }
    }
}

@Composable
fun AdvancedAccountManagementView(
    onNavigateBack: () -> Unit = {},
    onProcessingChange: (Boolean) -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val scope = rememberCoroutineScope()

    var flowDestination by remember { mutableStateOf(AdvancedFlowDestination.MAIN) }
    var navigatingForward by remember { mutableStateOf(true) }
    var showDeactivateConfirmation by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var deletePasswordErrorMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    val wrongPasswordText = stringResource(R.string.auth_error_wrongPassword)
    val userNotFoundText = stringResource(R.string.account_management_user_not_found)
    val deleteErrorFormat = stringResource(R.string.account_management_error_delete)

    LaunchedEffect(isProcessing) { onProcessingChange(isProcessing) }

    fun navigate(to: AdvancedFlowDestination, forward: Boolean = true) {
        navigatingForward = forward
        deletePasswordErrorMessage = null
        flowDestination = to
    }

    BackHandler(
        enabled = flowDestination != AdvancedFlowDestination.MAIN && !isProcessing,
    ) {
        navigate(AdvancedFlowDestination.MAIN, forward = false)
    }

    fun deactivateAccount() {
        isProcessing = true
        scope.launch {
            try {
                AuthService.deactivateAccount()
                AuthService.logout()
                onNavigateBack()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage
                showError = true
            } finally {
                isProcessing = false
            }
        }
    }

    fun runDelete(confirmation: AuthService.AccountDeletionConfirmation) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            errorMessage = userNotFoundText
            showError = true
            return
        }
        isProcessing = true
        deletePasswordErrorMessage = null
        scope.launch {
            try {
                AuthService.deleteAccount(confirmation)
                onNavigateBack()
            } catch (e: Exception) {
                val pwd = AccountDeletionErrorPresenter.passwordMessage(e, wrongPasswordText)
                if (pwd != null) {
                    deletePasswordErrorMessage = pwd
                } else {
                    errorMessage = deleteErrorFormat.format(e.localizedMessage ?: e.toString())
                    showError = true
                }
            } finally {
                isProcessing = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = flowDestination,
            transitionSpec = {
                if (navigatingForward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "advancedAccountFlow",
        ) { dest ->
            when (dest) {
                AdvancedFlowDestination.MAIN -> AdvancedMainContent(
                    onDismiss = onNavigateBack,
                    onLoginActivity = { navigate(AdvancedFlowDestination.LOGIN_ACTIVITY) },
                    onDeactivate = { showDeactivateConfirmation = true },
                    onDelete = { navigate(AdvancedFlowDestination.DELETE_ACCOUNT) },
                )
                AdvancedFlowDestination.DELETE_ACCOUNT -> DeleteAccountVerificationView(
                    isProcessing = isProcessing,
                    passwordErrorMessage = deletePasswordErrorMessage,
                    onPasswordErrorClear = { deletePasswordErrorMessage = null },
                    onConfirm = ::runDelete,
                    onCancel = { navigate(AdvancedFlowDestination.MAIN, forward = false) },
                    onProcessingChange = { isProcessing = it },
                )
                AdvancedFlowDestination.LOGIN_ACTIVITY -> LoginActivityView(
                    onNavigateBack = { navigate(AdvancedFlowDestination.MAIN, forward = false) },
                )
            }
        }

        if (isProcessing && flowDestination == AdvancedFlowDestination.MAIN) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(if (isDark) 0.18f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = primary)
            }
        }
    }

    if (showDeactivateConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeactivateConfirmation = false },
            title = { Text(stringResource(R.string.account_management_deactivate_title)) },
            text = { Text(stringResource(R.string.account_management_deactivate_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeactivateConfirmation = false
                        deactivateAccount()
                    },
                ) {
                    Text(
                        stringResource(R.string.account_management_deactivate),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateConfirmation = false }) {
                    Text(stringResource(R.string.account_management_cancel))
                }
            },
        )
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(R.string.account_management_error_title)) },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(R.string.account_management_ok))
                }
            },
        )
    }
}

@Composable
private fun AdvancedMainContent(
    onDismiss: () -> Unit,
    onLoginActivity: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val secondary = primary.copy(alpha = 0.58f)
    Column(Modifier.fillMaxSize()) {
        AdvancedSheetHeader(
            title = stringResource(R.string.settings_advanced_title),
            subtitle = stringResource(R.string.settings_danger_zone_warning),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AdvancedAccountActionRow(
                    icon = Icons.Filled.History,
                    title = stringResource(R.string.settings_sections_login_activity),
                    subtitle = stringResource(R.string.settings_sections_login_activity_subtitle),
                    onClick = onLoginActivity,
                )
                AdvancedAccountActionRow(
                    icon = Icons.Filled.Pause,
                    title = stringResource(R.string.account_management_deactivate_title),
                    subtitle = stringResource(R.string.account_management_deactivate_subtitle),
                    onClick = onDeactivate,
                )
                AdvancedAccountActionRow(
                    icon = Icons.Filled.Delete,
                    title = stringResource(R.string.account_management_delete_account_title),
                    subtitle = stringResource(R.string.account_management_delete_subtitle),
                    isDestructive = true,
                    onClick = onDelete,
                )
            }

            Column(
                Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = primary.copy(0.62f),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        stringResource(R.string.settings_info_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary.copy(0.62f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.settings_info_deactivate), fontSize = 12.sp, color = secondary)
                    Text(stringResource(R.string.settings_info_delete), fontSize = 12.sp, color = secondary)
                    Text(stringResource(R.string.settings_info_reactivate), fontSize = 12.sp, color = secondary)
                }
            }
        }
    }
}

@Composable
private fun AdvancedSheetHeader(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector? = null,
    onLeadingTap: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp),
    ) {
        if (leadingIcon != null && onLeadingTap != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
                    .clickable(interactionSource = interaction, indication = null, onClick = onLeadingTap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(leadingIcon, contentDescription = null, tint = primary, modifier = Modifier.size(17.dp))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = primary.copy(0.58f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun AdvancedAccountActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val accent = if (isDestructive) Color.Red else primary
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) Color.Red else primary,
            )
            Text(subtitle, fontSize = 12.sp, color = primary.copy(0.54f), maxLines = 2)
        }
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = primary.copy(0.35f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
fun DeleteAccountVerificationView(
    isProcessing: Boolean,
    passwordErrorMessage: String?,
    onPasswordErrorClear: () -> Unit,
    onConfirm: (AuthService.AccountDeletionConfirmation) -> Unit,
    onCancel: () -> Unit,
    onProcessingChange: (Boolean) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val method = remember { AccountDeletionAuthSupport.verificationMethod() }
    val requiredText = stringResource(R.string.account_management_required_text)
    val googleReauthError = stringResource(R.string.account_management_error_google_reauth)

    var flowOverview by remember { mutableStateOf(true) }
    var navigatingForward by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }
    var agreeToDelete by remember { mutableStateOf(false) }
    var identityVerified by remember { mutableStateOf(false) }
    var verificationErrorMessage by remember { mutableStateOf<String?>(null) }

    val showsPasswordField = method == AccountDeletionVerificationMethod.PASSWORD ||
        method == AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE
    val showsGoogleVerification = method == AccountDeletionVerificationMethod.GOOGLE ||
        method == AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE

    val identityRequirementMet = when (method) {
        AccountDeletionVerificationMethod.PASSWORD -> password.isNotEmpty()
        AccountDeletionVerificationMethod.GOOGLE -> identityVerified
        AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE ->
            identityVerified || password.isNotEmpty()
    }
    val isFormValid = identityRequirementMet && confirmText == requiredText && agreeToDelete

    val headerTitle = when (method) {
        AccountDeletionVerificationMethod.GOOGLE ->
            stringResource(R.string.account_management_confirm_identity_title)
        else -> stringResource(R.string.account_management_confirm_password)
    }
    val headerSubtitle = when (method) {
        AccountDeletionVerificationMethod.GOOGLE ->
            stringResource(R.string.account_management_confirm_identity_subtitle)
        AccountDeletionVerificationMethod.PASSWORD ->
            stringResource(R.string.account_management_delete_message)
        AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE ->
            stringResource(R.string.account_management_verify_with_password_or_google)
    }

    fun navigateOverview(toOverview: Boolean, forward: Boolean) {
        navigatingForward = forward
        onPasswordErrorClear()
        verificationErrorMessage = null
        identityVerified = false
        flowOverview = toOverview
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = flowOverview,
            transitionSpec = {
                if (navigatingForward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "deleteAccountFlow",
        ) { overview ->
            if (overview) {
                DeleteOverviewContent(
                    onContinue = { navigateOverview(false, forward = true) },
                    onCancel = onCancel,
                )
            } else {
                DeleteConfirmationContent(
                    headerTitle = headerTitle,
                    headerSubtitle = headerSubtitle,
                    showsPasswordField = showsPasswordField,
                    showsGoogleVerification = showsGoogleVerification,
                    password = password,
                    onPasswordChange = {
                        password = it
                        onPasswordErrorClear()
                    },
                    isPasswordVisible = isPasswordVisible,
                    onTogglePasswordVisible = { isPasswordVisible = !isPasswordVisible },
                    passwordErrorMessage = passwordErrorMessage,
                    identityVerified = identityVerified,
                    verificationErrorMessage = verificationErrorMessage,
                    onVerifyGoogle = {
                        scope.launch {
                            onProcessingChange(true)
                            verificationErrorMessage = null
                            onPasswordErrorClear()
                            try {
                                reauthenticateWithGoogle(context)
                                identityVerified = true
                                HapticManager.shared.lightImpact()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (e.message?.contains("cancel", ignoreCase = true) != true) {
                                    identityVerified = false
                                    verificationErrorMessage =
                                        e.localizedMessage ?: googleReauthError
                                }
                            } finally {
                                onProcessingChange(false)
                            }
                        }
                    },
                    confirmText = confirmText,
                    onConfirmTextChange = { confirmText = it },
                    requiredText = requiredText,
                    agreeToDelete = agreeToDelete,
                    onToggleAgree = { agreeToDelete = !agreeToDelete },
                    isFormValid = isFormValid,
                    onBack = { navigateOverview(true, forward = false) },
                    onSubmit = {
                        when (method) {
                            AccountDeletionVerificationMethod.GOOGLE ->
                                onConfirm(AuthService.AccountDeletionConfirmation.GoogleVerified)
                            AccountDeletionVerificationMethod.PASSWORD ->
                                onConfirm(AuthService.AccountDeletionConfirmation.Password(password))
                            AccountDeletionVerificationMethod.PASSWORD_OR_GOOGLE -> {
                                if (identityVerified) {
                                    onConfirm(AuthService.AccountDeletionConfirmation.GoogleVerified)
                                } else {
                                    onConfirm(AuthService.AccountDeletionConfirmation.Password(password))
                                }
                            }
                        }
                    },
                )
            }
        }

        if (isProcessing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(if (isDark) 0.18f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                        .padding(horizontal = 22.dp)
                        .height(58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        stringResource(R.string.account_management_deleting),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteOverviewContent(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val interactionContinue = remember { MutableInteractionSource() }
    val interactionCancel = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxSize()) {
        AdvancedSheetHeader(
            title = stringResource(R.string.account_management_delete_account_title),
            subtitle = stringResource(R.string.account_management_irreversible),
            leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onLeadingTap = onCancel,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.account_management_permanent_deletion),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.account_management_will_be_deleted),
                    fontSize = 14.sp,
                    color = primary.copy(0.62f),
                    textAlign = TextAlign.Center,
                )
            }

            Column(Modifier.fillMaxWidth()) {
                DeleteAccountImpactRow(Icons.Filled.Person, stringResource(R.string.account_management_profile_info))
                DeleteAccountImpactRow(Icons.Filled.PhotoLibrary, stringResource(R.string.account_management_stories_moments))
                DeleteAccountImpactRow(Icons.AutoMirrored.Filled.Message, stringResource(R.string.account_management_conversations))
                DeleteAccountImpactRow(Icons.Filled.People, stringResource(R.string.account_management_connections))
                DeleteAccountImpactRow(Icons.Filled.Notifications, stringResource(R.string.account_management_notifications))
                DeleteAccountImpactRow(Icons.Filled.Folder, stringResource(R.string.account_management_saved_content))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .momentsPress(interactionContinue, MomentsPressDefaults.momentsPressSubtle)
                        .clickable(
                            interactionSource = interactionContinue,
                            indication = null,
                            onClick = onContinue,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.account_management_continue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Red,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
                        .momentsPress(interactionCancel, MomentsPressDefaults.momentsPressSubtle)
                        .clickable(
                            interactionSource = interactionCancel,
                            indication = null,
                            onClick = onCancel,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.account_management_cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationContent(
    headerTitle: String,
    headerSubtitle: String,
    showsPasswordField: Boolean,
    showsGoogleVerification: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    passwordErrorMessage: String?,
    identityVerified: Boolean,
    verificationErrorMessage: String?,
    onVerifyGoogle: () -> Unit,
    confirmText: String,
    onConfirmTextChange: (String) -> Unit,
    requiredText: String,
    agreeToDelete: Boolean,
    onToggleAgree: () -> Unit,
    isFormValid: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val interactionAgree = remember { MutableInteractionSource() }
    val interactionSubmit = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize()) {
        AdvancedSheetHeader(
            title = headerTitle,
            subtitle = headerSubtitle,
            leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onLeadingTap = onBack,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (showsPasswordField) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.account_management_confirm_password),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .padding(start = 18.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (password.isEmpty()) {
                                Text(
                                    stringResource(R.string.account_management_current_password),
                                    fontSize = 15.sp,
                                    color = primary.copy(0.42f),
                                )
                            }
                            BasicTextField(
                                value = password,
                                onValueChange = onPasswordChange,
                                singleLine = true,
                                textStyle = TextStyle(color = primary, fontSize = 15.sp),
                                cursorBrush = SolidColor(primary),
                                visualTransformation = if (isPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Icon(
                            if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = primary.copy(0.58f),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable(onClick = onTogglePasswordVisible)
                                .padding(6.dp),
                        )
                    }
                    if (passwordErrorMessage != null) {
                        Text(
                            passwordErrorMessage,
                            fontSize = 12.sp,
                            color = Color.Red.copy(if (isDark) 0.88f else 0.78f),
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                }
            }

            if (showsPasswordField && showsGoogleVerification) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(primary.copy(0.16f)))
                    Text(
                        stringResource(R.string.account_management_delete_auth_divider),
                        fontSize = 12.sp,
                        color = primary.copy(0.52f),
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(primary.copy(0.16f)))
                }
            }

            if (showsGoogleVerification) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (identityVerified) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.account_management_identity_verified),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primary,
                            )
                        }
                    } else {
                        ContinueWithGoogleButton(onClick = onVerifyGoogle)
                    }
                    if (verificationErrorMessage != null) {
                        Text(
                            verificationErrorMessage,
                            fontSize = 12.sp,
                            color = Color.Red.copy(if (isDark) 0.88f else 0.78f),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.account_management_write_exactly),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
                Text(
                    requiredText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Red,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                BasicTextField(
                    value = confirmText,
                    onValueChange = onConfirmTextChange,
                    singleLine = true,
                    textStyle = TextStyle(color = primary, fontSize = 15.sp),
                    cursorBrush = SolidColor(primary),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (confirmText.isEmpty()) {
                                    Text(
                                        stringResource(R.string.account_management_write_here),
                                        color = primary.copy(0.42f),
                                        fontSize = 15.sp,
                                    )
                                }
                                inner()
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        if (confirmText == requiredText) {
                                            Color.Green.copy(0.45f)
                                        } else {
                                            primary.copy(0.16f)
                                        },
                                    ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsPress(interactionAgree, MomentsPressDefaults.momentsPressSubtle)
                    .clickable(
                        interactionSource = interactionAgree,
                        indication = null,
                        onClick = onToggleAgree,
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (agreeToDelete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (agreeToDelete) Color.Red else primary.copy(0.45f),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.account_management_understand_irreversible),
                    fontSize = 13.sp,
                    color = primary.copy(0.72f),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .alpha(if (isFormValid) 1f else 0.45f)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = isFormValid)
                    .momentsPress(interactionSubmit, MomentsPressDefaults.momentsPressSubtle)
                    .clickable(
                        enabled = isFormValid,
                        interactionSource = interactionSubmit,
                        indication = null,
                        onClick = onSubmit,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.account_management_delete_account_permanently),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Red,
                )
            }
        }
    }
}

@Composable
private fun ContinueWithGoogleButton(onClick: () -> Unit) {
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

@Composable
private fun DeleteAccountImpactRow(icon: ImageVector, text: String) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = primary.copy(0.62f), modifier = Modifier.size(15.dp))
        Text(text, fontSize = 14.sp, color = primary, maxLines = 2, modifier = Modifier.weight(1f))
    }
}
