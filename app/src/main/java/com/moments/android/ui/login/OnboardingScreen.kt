@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.moments.android.ui.login

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TOTAL_STEPS = 5
private val usernameRegex = Regex("^[a-zA-Z0-9_.]{3,20}$")

@Composable
fun OnboardingScreen(onBack: () -> Unit, onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var usernameChecking by remember { mutableStateOf(false) }
    var usernameSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }

    val usernameFormatError = stringResource(R.string.register_err_username_format)
    val usernameUnavailableError = stringResource(R.string.register_err_username_unavailable)
    val usernameTakenMessage = stringResource(R.string.register_username_taken)
    val accountErrorMessage = stringResource(R.string.register_account_error)

    // Validación de username con debounce (equivalente al scheduleValidation de iOS).
    LaunchedEffect(username) {
        val value = username
        if (value.isEmpty()) {
            usernameError = null; usernameSuggestions = emptyList(); usernameChecking = false
            return@LaunchedEffect
        }
        if (!usernameRegex.matches(value)) {
            usernameError = usernameFormatError; usernameSuggestions = emptyList(); usernameChecking = false
            return@LaunchedEffect
        }
        usernameError = null; usernameChecking = true
        delay(400)
        val taken = runCatching {
            FirebaseFirestore.getInstance().collection("usernames").document(value.lowercase()).get().await().exists()
        }.getOrDefault(false)
        usernameChecking = false
        if (taken) {
            usernameError = usernameUnavailableError
            usernameSuggestions = suggestionsFor(value)
        } else {
            usernameError = null; usernameSuggestions = emptyList()
        }
    }

    val canProceed = when (step) {
        1 -> username.length >= 3 && usernameError == null && !usernameChecking
        2 -> isValidEmail(email.trim())
        3 -> password.length >= 8
        4 -> selectedInterests.size >= INTERESTS_MIN
        else -> privacyAccepted
    }

    fun goNext() {
        if (step < TOTAL_STEPS) {
            step += 1
        } else {
            isCreating = true
            errorMessage = null
            scope.launch {
                val start = System.currentTimeMillis()
                try {
                    completeEmailRegistration(context, username, email, password, selectedInterests, photoUri)
                    // Duración mínima para que se vea la animación de "creando perfil" (como iOS).
                    val elapsed = System.currentTimeMillis() - start
                    if (elapsed < 2500) delay(2500 - elapsed)
                    onAuthenticated()
                } catch (e: UsernameTakenException) {
                    isCreating = false; errorMessage = usernameTakenMessage
                } catch (e: Exception) {
                    isCreating = false; errorMessage = accountErrorMessage
                }
            }
        }
    }

    fun goBack() {
        if (step > 1) step -= 1 else onBack()
    }

    Box(Modifier.fillMaxSize().background(com.moments.android.ui.theme.Surface)) {
        Column(Modifier.fillMaxSize()) {
            OnboardingTopBar(step = step, onBack = ::goBack, onCancel = onBack)

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                OnboardingStepHeader(step = step)
                Spacer(Modifier.height(28.dp))

                Column(Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                    when (step) {
                        1 -> UsernameStep(
                            username = username,
                            onUsernameChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' } },
                            checking = usernameChecking,
                            error = usernameError,
                            suggestions = usernameSuggestions,
                            onPickSuggestion = { username = it; usernameError = null; usernameSuggestions = emptyList() },
                        )
                        2 -> EmailStep(email = email, onEmailChange = { email = it })
                        3 -> PasswordStep(password = password, onPasswordChange = { password = it }, showPassword = showPassword, onToggle = { showPassword = !showPassword })
                        4 -> InterestsStep(
                            photoUri = photoUri,
                            onPickPhoto = { photoUri = it },
                            selected = selectedInterests,
                            onToggleInterest = { raw ->
                                selectedInterests = when {
                                    selectedInterests.contains(raw) -> selectedInterests - raw
                                    selectedInterests.size < INTERESTS_MAX -> selectedInterests + raw
                                    else -> selectedInterests
                                }
                            },
                        )
                        else -> PreviewStep(
                            photoUri = photoUri,
                            username = username,
                            email = email,
                            interests = selectedInterests,
                            privacyAccepted = privacyAccepted,
                            onPrivacyChange = { privacyAccepted = it },
                            onOpenPrivacy = { showPrivacy = true },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 8.dp, bottom = 14.dp).imePadding()) {
                AuthPrimaryButton(
                    text = if (step < TOTAL_STEPS) stringResource(R.string.register_action_continue) else stringResource(R.string.register_action_create),
                    isLoading = isCreating,
                    isEnabled = canProceed,
                    modifier = Modifier.widthIn(max = 400.dp),
                    onClick = ::goNext,
                )
            }
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

    if (showPrivacy) {
        PrivacyPolicySheet(onDismiss = { showPrivacy = false })
    }

    if (isCreating) {
        CreatingProfileOverlay()
    }
}

private fun suggestionsFor(base: String): List<String> = listOf("${base}${(10..99).random()}", "${base}_", "${base}${(2020..2026).random()}").distinct().take(3)

private fun isValidEmail(email: String): Boolean =
    Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$").matches(email)

// MARK: - Top bar (back/close + progress dots + cancel)
@Composable
private fun OnboardingTopBar(step: Int, onBack: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp).background(AuthColors.subtle(0.06f), CircleShape)) {
            Icon(
                if (step > 1) Icons.Filled.ArrowBack else Icons.Filled.Close,
                contentDescription = stringResource(if (step > 1) R.string.register_back else R.string.register_close),
                tint = AuthColors.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ProgressDots(step = step)
        }
        if (step > 1) {
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp).background(AuthColors.subtle(0.06f), CircleShape)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.onboarding_cancel), tint = AuthColors.primary, modifier = Modifier.size(16.dp))
            }
        } else {
            Spacer(Modifier.size(36.dp))
        }
    }
}

@Composable
private fun ProgressDots(step: Int) {
    Row(
        Modifier
            .background(AuthColors.subtle(0.06f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..TOTAL_STEPS) {
            val width by animateDpAsState(if (i == step) 18.dp else 6.dp, label = "dotWidth")
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .background(AuthColors.primary.copy(alpha = if (i <= step) 0.82f else 0.22f), RoundedCornerShape(50)),
            )
        }
    }
}

// MARK: - Header (logo en paso 1 + título + subtítulo)
@Composable
private fun OnboardingStepHeader(step: Int) {
    val (titleRes, subtitleRes) = when (step) {
        1 -> R.string.onboarding_title_username to R.string.onboarding_subtitle_username
        2 -> R.string.onboarding_title_email to R.string.onboarding_subtitle_email
        3 -> R.string.onboarding_title_password to R.string.onboarding_subtitle_password
        4 -> R.string.onboarding_title_interests to R.string.onboarding_subtitle_interests
        else -> R.string.onboarding_title_preview to R.string.onboarding_subtitle_preview
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (step == 1) 18.dp else 0.dp)) {
        if (step == 1) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.login_logo),
                contentDescription = null,
                modifier = Modifier.height(54.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(titleRes), fontSize = 20.9.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary, textAlign = TextAlign.Center)
            Text(stringResource(subtitleRes), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.72f), textAlign = TextAlign.Center)
        }
    }
}

// MARK: - Campo grande de una pregunta (equivalente a OnboardingQuestionField)
@Composable
private fun BigQuestionField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    validation: FieldValidation = FieldValidation.Idle,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(22.dp)
    val border by animateColorAsState(
        when (validation) {
            FieldValidation.Invalid -> Color(0xFFE5484D).copy(alpha = 0.5f)
            FieldValidation.Valid -> Color(0xFF30A46C).copy(alpha = 0.35f)
            else -> AuthColors.primary.copy(alpha = if (focused) 0.22f else 0.1f)
        },
        label = "border",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(AuthColors.subtle(0.05f)).border(BorderStroke(1.dp, border), shape).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (prefix != null) {
            Text(prefix, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.5f))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.4f))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                textStyle = androidx.compose.ui.text.TextStyle(color = AuthColors.primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.moments.android.ui.theme.InterFamily),
                cursorBrush = SolidColor(AuthColors.primary),
                visualTransformation = if (trailing != null && keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
        when (validation) {
            FieldValidation.Checking -> CircularProgressIndicator(strokeWidth = 2.dp, color = AuthColors.secondary(0.6f), modifier = Modifier.size(18.dp))
            FieldValidation.Valid -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF30A46C).copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            FieldValidation.Invalid -> Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFE5484D).copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            FieldValidation.Idle -> {}
        }
        trailing?.invoke()
    }
}

private enum class FieldValidation { Idle, Checking, Valid, Invalid }

// MARK: - Step 1: username
@Composable
private fun UsernameStep(
    username: String,
    onUsernameChange: (String) -> Unit,
    checking: Boolean,
    error: String?,
    suggestions: List<String>,
    onPickSuggestion: (String) -> Unit,
) {
    val validation = when {
        username.isEmpty() -> FieldValidation.Idle
        checking -> FieldValidation.Checking
        error != null -> FieldValidation.Invalid
        username.length >= 3 -> FieldValidation.Valid
        else -> FieldValidation.Idle
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BigQuestionField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = stringResource(R.string.register_username_ph),
            prefix = "@",
            validation = validation,
        )
        if (error != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE5484D).copy(alpha = 0.85f))
                if (suggestions.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        suggestions.forEach { s ->
                            Text(
                                "@$s",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AuthColors.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AuthColors.subtle(0.06f))
                                    .clickable { onPickSuggestion(s) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Step 2: email
@Composable
private fun EmailStep(email: String, onEmailChange: (String) -> Unit) {
    val validation = when {
        email.trim().isEmpty() -> FieldValidation.Idle
        isValidEmail(email.trim()) -> FieldValidation.Valid
        else -> FieldValidation.Invalid
    }
    BigQuestionField(
        value = email,
        onValueChange = onEmailChange,
        placeholder = stringResource(R.string.register_email_ph),
        keyboardType = KeyboardType.Email,
        validation = validation,
    )
}

// MARK: - Step 3: password + strength
@Composable
private fun PasswordStep(password: String, onPasswordChange: (String) -> Unit, showPassword: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val interaction = remember { MutableInteractionSource() }
        val shape = RoundedCornerShape(22.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(AuthColors.subtle(0.05f)).border(BorderStroke(1.dp, AuthColors.primary.copy(alpha = 0.1f)), shape).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (password.isEmpty()) {
                    Text(stringResource(R.string.register_password_ph), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.4f))
                }
                BasicTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = androidx.compose.ui.text.TextStyle(color = AuthColors.primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.moments.android.ui.theme.InterFamily),
                    cursorBrush = SolidColor(AuthColors.primary),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            Text(
                if (showPassword) "🙈" else "👁",
                modifier = Modifier.clickable(onClick = onToggle).padding(4.dp),
                fontSize = 16.sp,
            )
        }
        if (password.isNotEmpty()) {
            PasswordStrength(password)
        }
    }
}

@Composable
private fun PasswordStrength(password: String) {
    val strength = passwordStrength(password)
    val color = when (strength) {
        1 -> Color(0xFFE5484D); 2 -> Color(0xFFF76B15); 3 -> Color(0xFFFFC53D); 4 -> Color(0xFF30A46C); else -> AuthColors.subtle(0.2f)
    }
    val messageRes = when (strength) {
        1 -> R.string.register_password_weak; 2 -> R.string.register_password_fair; 3 -> R.string.register_password_good; 4 -> R.string.register_password_excellent; else -> null
    }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 4) {
                Box(Modifier.weight(1f).height(4.dp).background(if (i < strength) color.copy(alpha = 0.8f) else AuthColors.subtle(0.2f), RoundedCornerShape(50)))
            }
        }
        if (messageRes != null) {
            Text(stringResource(messageRes), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color.copy(alpha = 0.85f))
        }
    }
}

private fun passwordStrength(p: String): Int {
    var s = 0
    if (p.length >= 8) s++
    if (p.any { it.isLetter() }) s++
    if (p.any { it.isDigit() }) s++
    if (p.any { !it.isLetterOrDigit() }) s++
    return s
}

// MARK: - Step 4: photo + interests
@Composable
private fun InterestsStep(
    photoUri: Uri?,
    onPickPhoto: (Uri?) -> Unit,
    selected: List<String>,
    onToggleInterest: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ProfilePhotoPicker(photoUri = photoUri, onPickPhoto = onPickPhoto)
        InterestsSelector(selected = selected, onToggle = onToggleInterest)
    }
}

@Composable
private fun ProfilePhotoPicker(photoUri: Uri?, onPickPhoto: (Uri?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onPickPhoto(uri) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(AuthColors.subtle(0.1f))
                .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = AuthColors.secondary(0.42f), modifier = Modifier.size(34.dp))
            }
        }
        Text(stringResource(R.string.register_photo_optional), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.62f))
    }
}

@Composable
private fun InterestsSelector(selected: List<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨ ${stringResource(R.string.register_interests_title)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.94f), modifier = Modifier.weight(1f))
            Text(
                String.format(stringResource(R.string.register_interests_count), selected.size),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected.size >= INTERESTS_MIN) AuthColors.primary else Color(0xFFF76B15),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.subtle(0.06f)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Text(stringResource(R.string.register_interests_description), fontSize = 14.sp, color = AuthColors.secondary(0.62f))
        if (selected.size < INTERESTS_MIN) {
            Text(stringResource(R.string.register_interests_min_hint), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF76B15).copy(alpha = 0.9f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AllInterests.forEach { interest ->
                val isSelected = selected.contains(interest.raw)
                Text(
                    stringResource(interest.labelRes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) AuthColors.primary else AuthColors.secondary(0.72f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) AuthColors.subtle(0.1f) else AuthColors.subtle(0.05f))
                        .border(BorderStroke(if (isSelected) 1.4.dp else 0.8.dp, AuthColors.subtle(if (isSelected) 0.34f else 0.08f)), RoundedCornerShape(50))
                        .clickable { onToggle(interest.raw) }
                        .padding(horizontal = 15.dp, vertical = 9.dp),
                )
            }
        }
    }
}

// MARK: - Step 5: preview + privacy
@Composable
private fun PreviewStep(
    photoUri: Uri?,
    username: String,
    email: String,
    interests: List<String>,
    privacyAccepted: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AuthColors.subtle(0.05f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(AuthColors.subtle(0.1f)), contentAlignment = Alignment.Center) {
                if (photoUri != null) {
                    AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = AuthColors.secondary(0.42f), modifier = Modifier.size(32.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("@$username", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary)
                Text(email, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.78f))
            }
            if (interests.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.register_summary_interests), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.secondary(0.68f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        interests.forEach { raw ->
                            val label = AllInterests.firstOrNull { it.raw == raw }?.labelRes
                            Text(
                                if (label != null) stringResource(label) else raw,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AuthColors.primary,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(AuthColors.subtle(0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.register_terms_accept),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AuthColors.secondary(0.9f),
                )
                Text(
                    stringResource(R.string.register_terms_privacy),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthColors.primary,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onOpenPrivacy),
                )
            }
            Switch(
                checked = privacyAccepted,
                onCheckedChange = onPrivacyChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF30A46C)),
            )
        }
        Text(stringResource(R.string.register_verification_notice), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AuthColors.secondary(0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
