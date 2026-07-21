package com.moments.android.ui.login

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private enum class AuthScreen { Welcome, LoginForm, Register }

@Composable
fun LoginScreen(onAuthenticated: () -> Unit) {
    var screen by remember { mutableStateOf(AuthScreen.Welcome) }

    Box(Modifier.fillMaxSize().background(com.moments.android.ui.theme.Surface).systemBarsPadding()) {
        when (screen) {
            AuthScreen.Welcome -> WelcomeScreen(
                onCreateAccount = { screen = AuthScreen.Register },
                onLogin = { screen = AuthScreen.LoginForm },
                onAuthenticated = onAuthenticated,
            )
            AuthScreen.LoginForm -> LoginFormScreen(
                onBack = { screen = AuthScreen.Welcome },
                onRegister = { screen = AuthScreen.Register },
                onAuthenticated = onAuthenticated,
            )
            AuthScreen.Register -> OnboardingScreen(
                onBack = { screen = AuthScreen.Welcome },
                onAuthenticated = onAuthenticated,
            )
        }
    }
}

// MARK: - Header (logo + título; equivalente a EnhancedHeaderView)
@Composable
private fun AuthHeader(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(R.drawable.login_logo),
            contentDescription = stringResource(R.string.brand_wordmark),
            modifier = Modifier.size(84.dp),
        )
        Text(
            stringResource(R.string.auth_hero_title),
            fontSize = 23.75.sp,
            fontWeight = FontWeight.Bold,
            color = AuthColors.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GoogleGlyph() {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Text("G", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF4285F4))
    }
}

// MARK: - Welcome (equivalente a WelcomeContent) — centrado vertical con halo tras el logo
@Composable
private fun WelcomeScreen(onCreateAccount: () -> Unit, onLogin: () -> Unit, onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isGoogleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val googleErrorMessage = stringResource(R.string.auth_google_error)
    val googleConnecting = stringResource(R.string.auth_google_connecting)
    val googleContinue = stringResource(R.string.auth_google_continue)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1.5f))

        Box(contentAlignment = Alignment.Center) {
            WelcomeAuroraHalo(Modifier.offset(y = (-24).dp))
            AuthHeader(modifier = Modifier.widthIn(max = 400.dp))
        }

        Spacer(Modifier.weight(1.2f))

        Column(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AuthPrimaryButton(text = stringResource(R.string.welcome_create_account), onClick = onCreateAccount)

            AuthOutlineButton(
                text = if (isGoogleLoading) googleConnecting else googleContinue,
                leadingIcon = { GoogleGlyph() },
                isEnabled = !isGoogleLoading,
            ) {
                isGoogleLoading = true
                scope.launch {
                    try {
                        signInWithGoogle(context)
                        onAuthenticated()
                    } catch (error: Exception) {
                        googleError = googleErrorMessage
                    } finally {
                        isGoogleLoading = false
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${stringResource(R.string.welcome_have_account)}  ", color = AuthColors.secondary(0.54f), fontSize = 14.sp)
                Text(
                    stringResource(R.string.welcome_log_in),
                    color = AuthColors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onLogin),
                )
            }
        }

        Spacer(Modifier.weight(1.0f))

        LoginDisclaimer(modifier = Modifier.widthIn(max = 400.dp))
        Spacer(Modifier.height(24.dp))
    }

    if (googleError != null) {
        AlertDialog(
            onDismissRequest = { googleError = null },
            confirmButton = { TextButton(onClick = { googleError = null }) { Text(stringResource(R.string.login_ok)) } },
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(googleError ?: "") },
        )
    }
}

// MARK: - Login (equivalente a LoginFormScreen)
@Composable
private fun LoginFormScreen(onBack: () -> Unit, onRegister: () -> Unit, onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showResetPassword by remember { mutableStateOf(false) }

    val invalidCredentialsMessage = stringResource(R.string.login_invalid_credentials)
    val googleErrorMessage = stringResource(R.string.auth_google_error)
    val googleConnecting = stringResource(R.string.auth_google_connecting)
    val googleContinue = stringResource(R.string.auth_google_continue)
    val signingIn = stringResource(R.string.login_signing_in)
    val signIn = stringResource(R.string.login_sign_in)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, bottom = 2.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp).background(AuthColors.subtle(0.06f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.login_back),
                    tint = AuthColors.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            AuthHeader(modifier = Modifier.widthIn(max = 400.dp))
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuthTextField(
                    icon = Icons.Filled.Person,
                    placeholder = stringResource(R.string.login_username_or_email),
                    value = identifier,
                    onValueChange = { identifier = it },
                    keyboardType = KeyboardType.Email,
                )
                AuthSecureField(
                    placeholder = stringResource(R.string.login_password),
                    value = password,
                    onValueChange = { password = it },
                    isVisible = showPassword,
                    onToggleVisible = { showPassword = !showPassword },
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        stringResource(R.string.login_forgot_password),
                        color = AuthColors.secondary(0.58f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { showResetPassword = true }.padding(vertical = 6.dp),
                    )
                }

                Spacer(Modifier.height(4.dp))

                AuthPrimaryButton(
                    text = if (isLoading) signingIn else signIn,
                    isLoading = isLoading,
                    isEnabled = identifier.trim().isNotEmpty() && password.isNotEmpty(),
                ) {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            loginWithIdentifier(identifier, password)
                            onAuthenticated()
                        } catch (error: Exception) {
                            errorMessage = invalidCredentialsMessage
                        } finally {
                            isLoading = false
                        }
                    }
                }

                AuthDivider(text = stringResource(R.string.login_or_continue))

                AuthOutlineButton(
                    text = if (isGoogleLoading) googleConnecting else googleContinue,
                    leadingIcon = { GoogleGlyph() },
                    isEnabled = !isGoogleLoading,
                ) {
                    isGoogleLoading = true
                    scope.launch {
                        try {
                            signInWithGoogle(context)
                            onAuthenticated()
                        } catch (error: Exception) {
                            errorMessage = googleErrorMessage
                        } finally {
                            isGoogleLoading = false
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("${stringResource(R.string.login_no_account)}  ", color = AuthColors.secondary(0.54f), fontSize = 14.sp)
                    Text(
                        stringResource(R.string.login_register),
                        color = AuthColors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onRegister),
                    )
                }

                LoginDisclaimer(modifier = Modifier.padding(top = 10.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text(stringResource(R.string.login_ok)) } },
            title = { Text(stringResource(R.string.login_error_title)) },
            text = { Text(errorMessage ?: "") },
        )
    }

    if (showResetPassword) {
        ResetPasswordSheet(onDismiss = { showResetPassword = false })
    }
}

private suspend fun loginWithIdentifier(identifier: String, password: String) {
    val trimmed = identifier.trim()
    require(trimmed.isNotEmpty() && password.isNotEmpty())

    val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$")
    val email = if (emailRegex.matches(trimmed)) {
        trimmed
    } else {
        val doc = FirebaseFirestore.getInstance()
            .collection("usernames")
            .document(trimmed.lowercase())
            .get()
            .await()
        doc.getString("email") ?: throw IllegalStateException("Usuario no encontrado")
    }
    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
}

// MARK: - Reset password (equivalente a EnhancedResetPasswordView)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResetPasswordSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var succeeded by remember { mutableStateOf(false) }

    val successMessage = stringResource(R.string.login_reset_password_success)
    val genericErrorMessage = stringResource(R.string.login_reset_password_generic_error)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.login_reset_password_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.login_reset_password_description),
                        fontSize = 13.sp,
                        color = AuthColors.secondary(0.68f),
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).background(AuthColors.subtle(0.06f), CircleShape)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.login_close), tint = AuthColors.primary)
                }
            }

            Spacer(Modifier.height(22.dp))

            AuthTextField(
                icon = Icons.Filled.Email,
                placeholder = stringResource(R.string.login_email),
                value = email,
                onValueChange = { email = it },
                keyboardType = KeyboardType.Email,
                capitalization = KeyboardCapitalization.None,
            )

            Spacer(Modifier.height(16.dp))

            AuthPrimaryButton(text = stringResource(R.string.login_reset_password_send_link), isLoading = isLoading, isEnabled = email.isNotBlank()) {
                isLoading = true
                scope.launch {
                    try {
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
                        resultMessage = successMessage
                        succeeded = true
                    } catch (error: Exception) {
                        resultMessage = genericErrorMessage
                        succeeded = false
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    }

    if (resultMessage != null) {
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            confirmButton = {
                TextButton(onClick = {
                    resultMessage = null
                    if (succeeded) onDismiss()
                }) { Text(stringResource(R.string.login_ok)) }
            },
            title = { Text(stringResource(R.string.login_info_title)) },
            text = { Text(resultMessage ?: "") },
        )
    }
}
