package com.moments.android.reportes

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentsNotification
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchStoriesByIds
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/** Port de AppealFormView.swift */
@Composable
fun AppealFormView(
    suspensionReason: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primary = colors.primary
    val secondary = if (isDark) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.72f)

    var appealMessage by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email.orEmpty()) }
    var additionalInfo by remember { mutableStateOf("") }
    var characterCount by remember { mutableIntStateOf(0) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessView by remember { mutableStateOf(false) }
    var appealResult by remember { mutableStateOf<AppealResult?>(null) }
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }

    fun updateCharacterCount() {
        characterCount = appealMessage.trim().length
        messageError = when {
            characterCount < 50 && appealMessage.isNotEmpty() ->
                context.getString(R.string.appeal_validation_tooShort, characterCount, 50)
            characterCount > 2000 ->
                context.getString(R.string.appeal_validation_tooLong, characterCount)
            else -> null
        }
    }

    val canSubmit = contactEmail.isNotEmpty() &&
        contactEmail.contains("@") &&
        characterCount in 50..2000 &&
        !isLoading

    fun submitAppeal() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            alertTitle = context.getString(R.string.appeal_error_title)
            alertMessage = context.getString(R.string.appeal_error_userInfo)
            showAlert = true
            return
        }
        isLoading = true
        scope.launch {
            try {
                val response = appealService.submitAppeal(
                    userId = userId,
                    message = appealMessage,
                    email = contactEmail,
                    additionalInfo = additionalInfo.takeIf { it.isNotEmpty() },
                )
                isLoading = false
                if (response.success) {
                    appealResult = AppealResult.from(
                        response,
                        context.getString(R.string.appeal_result_processed),
                    )
                    showSuccessView = true
                } else {
                    alertTitle = context.getString(R.string.appeal_error_title)
                    alertMessage = response.message ?: context.getString(R.string.appeal_error_unknown)
                    showAlert = true
                }
            } catch (error: AppealError) {
                isLoading = false
                alertTitle = context.getString(R.string.appeal_error_submit)
                alertMessage = error.localizedMessage(context)
                showAlert = true
            } catch (error: Exception) {
                isLoading = false
                alertTitle = context.getString(R.string.appeal_error_unexpected)
                alertMessage = error.localizedMessage ?: context.getString(R.string.appeal_error_unknown)
                showAlert = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showSuccessView && appealResult != null) {
            AppealSuccessView(result = appealResult!!, onDismiss = onDismiss)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                AppealSheetHeader(
                    canSubmit = canSubmit,
                    isLoading = isLoading,
                    submitLabel = stringResource(R.string.appeal_submitButton),
                    onDismiss = onDismiss,
                    onSubmit = { submitAppeal() },
                )
                AppealFormHeader(primary = primary, secondary = secondary)
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    AppealEmailField(
                        email = contactEmail,
                        onEmailChange = { contactEmail = it },
                        title = stringResource(R.string.appeal_contactEmail),
                        placeholder = "tu@email.com",
                        primary = primary,
                        secondary = secondary,
                    )
                    suspensionReason?.let {
                        AppealInfoCard(
                            title = stringResource(R.string.appeal_suspensionReason),
                            content = it,
                            icon = Icons.Default.Warning,
                            accent = Color(0xFFFF9800),
                            primary = primary,
                            secondary = secondary,
                        )
                    }
                    AppealMessageField(
                        message = appealMessage,
                        onMessageChange = {
                            appealMessage = it
                            updateCharacterCount()
                        },
                        characterCount = characterCount,
                        messageError = messageError,
                        title = stringResource(R.string.appeal_yourAppeal),
                        placeholder = stringResource(R.string.appeal_yourAppeal_placeholder),
                        minimumLength = 50,
                        maximumLength = 2000,
                        primary = primary,
                        secondary = secondary,
                    )
                    AppealOptionalField(
                        text = additionalInfo,
                        onTextChange = { additionalInfo = it },
                        title = stringResource(R.string.appeal_additionalInfo),
                        placeholder = stringResource(R.string.appeal_additionalInfo_placeholder),
                        primary = primary,
                        secondary = secondary,
                    )
                    AppealRequirements(
                        characterCount = characterCount,
                        email = contactEmail,
                        primary = primary,
                        secondary = secondary,
                    )
                }
            }
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(alertTitle) },
            text = { Text(alertMessage) },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text(stringResource(R.string.appeal_error_ok))
                }
            },
        )
    }
}

/** ≡ AppealSheetHeader.swift — chevron.down + submit glass. */
@Composable
fun AppealSheetHeader(
    canSubmit: Boolean,
    isLoading: Boolean,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primary = colors.primary
    val disabled = if (isDark) Color.White.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.42f)

    // Sheet Android: sin chevron dismiss; solo Submit a la derecha
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(percent = 50))
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = canSubmit && !isLoading)
                .clickable(enabled = canSubmit && !isLoading, onClick = onSubmit)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                MomentsCircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    submitLabel,
                    color = if (canSubmit) primary else disabled,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AppealFormHeader(primary: Color, secondary: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
    ) {
        Icon(
            Icons.Outlined.Balance,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(38.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.appeal_title),
                color = primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.appeal_subtitle),
                color = secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun AppealGlassField(
    focused: Boolean,
    primary: Color,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val subtle = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true)
            .border(
                width = if (focused) 1.4.dp else 0.8.dp,
                color = subtle.copy(alpha = if (focused) 0.28f else 0.12f),
                shape = shape,
            ),
    ) { content() }
}

@Composable
fun AppealEmailField(
    email: String,
    onEmailChange: (String) -> Unit,
    title: String,
    placeholder: String,
    primary: Color,
    secondary: Color,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Email, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
            Text(title, color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        AppealGlassField(focused = focused, primary = primary) {
            BasicTextField(
                value = email,
                onValueChange = onEmailChange,
                singleLine = true,
                textStyle = TextStyle(color = primary, fontSize = 16.sp),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (email.isEmpty()) {
                            Text(placeholder, color = secondary.copy(alpha = 0.48f), fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
fun AppealMessageField(
    message: String,
    onMessageChange: (String) -> Unit,
    characterCount: Int,
    messageError: String?,
    title: String,
    placeholder: String,
    minimumLength: Int,
    maximumLength: Int,
    primary: Color,
    secondary: Color,
) {
    var focused by remember { mutableStateOf(false) }
    val countColor = when {
        characterCount < minimumLength -> Color(0xFFFF9800)
        characterCount > maximumLength -> Color.Red
        else -> secondary.copy(alpha = 0.62f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sms, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.appeal_field_characterCount, characterCount),
                color = countColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        AppealGlassField(focused = focused, primary = primary) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(12.dp)) {
                if (message.isEmpty()) {
                    Text(
                        placeholder,
                        color = secondary.copy(alpha = 0.48f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                BasicTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    textStyle = TextStyle(color = primary, fontSize = 16.sp),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 126.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
        }
        messageError?.let { error ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(12.dp))
                Text(error, color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun AppealOptionalField(
    text: String,
    onTextChange: (String) -> Unit,
    title: String,
    placeholder: String,
    primary: Color,
    secondary: Color,
) {
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AddCircle, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
            Text(title, color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        AppealGlassField(focused = focused, primary = primary) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(8.dp)) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        color = secondary.copy(alpha = 0.46f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(color = primary, fontSize = 14.sp),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
        }
    }
}

@Composable
fun AppealInfoCard(
    title: String,
    content: String,
    icon: ImageVector = Icons.Default.Info,
    accent: Color = Color(0xFF2196F3),
    primary: Color = rememberAdaptiveColors().primary,
    secondary: Color = rememberAdaptiveColors().secondary,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = false)
            .border(0.8.dp, accent.copy(alpha = if (isDark) 0.24f else 0.18f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Text(title, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(content, color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppealRequirements(
    characterCount: Int,
    email: String,
    primary: Color,
    secondary: Color,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .momentsChromeGlass(shape, interactive = false)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF007AFF).copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.appeal_requirements), color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RequirementRow("Mínimo 50 caracteres", characterCount >= 50, primary, secondary)
            RequirementRow(
                "Máximo 2000 caracteres",
                characterCount in 1..2000,
                primary,
                secondary,
                failed = characterCount > 2000,
            )
            RequirementRow("Email válido requerido", email.contains("@"), primary, secondary)
        }
    }
}

@Composable
private fun RequirementRow(
    text: String,
    isCompleted: Boolean,
    primary: Color,
    secondary: Color,
    failed: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                failed -> Icons.Default.Warning
                isCompleted -> Icons.Default.CheckCircle
                else -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when {
                failed -> Color.Red
                isCompleted -> Color(0xFF34C759)
                else -> secondary
            },
            modifier = Modifier.size(16.dp),
        )
        Text(text, color = if (isCompleted) primary else secondary, fontSize = 13.sp)
    }
}

@Composable
fun AppealSuccessView(result: AppealResult, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primary = colors.primary
    val secondary = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.74f)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(42.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.appeal_success_title),
                color = primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(result.message, color = secondary, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            result.ticketNumber?.let {
                AppealInfoCard(
                    title = stringResource(R.string.appeal_success_ticketNumber),
                    content = it,
                    icon = Icons.Default.ConfirmationNumber,
                    accent = Color(0xFFAF52DE),
                    primary = primary,
                    secondary = secondary,
                )
            }
            result.estimatedResponseTime?.let {
                AppealInfoCard(
                    title = stringResource(R.string.appeal_success_estimatedResponse),
                    content = it,
                    icon = Icons.Default.Schedule,
                    accent = Color(0xFF007AFF),
                    primary = primary,
                    secondary = secondary,
                )
            }
            result.priority?.let {
                AppealInfoCard(
                    title = stringResource(R.string.appeal_success_priority),
                    content = it.replaceFirstChar { c -> c.uppercase() },
                    icon = Icons.Default.Info,
                    accent = Color(0xFFFF9800),
                    primary = primary,
                    secondary = secondary,
                )
            }
            if (result.nextSteps.isNotEmpty()) {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .momentsChromeGlass(shape, interactive = false)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.appeal_nextSteps),
                        color = primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    result.nextSteps.forEach { step ->
                        Text("• $step", color = secondary, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
        ) {
            Text(stringResource(R.string.appeal_understood), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Port de ModerationReviewRequestSheet en AppealFormView.swift */
@Composable
fun ModerationReviewRequestSheet(
    notification: MomentsNotification,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }
    val firestoreService = remember { FirestoreService() }

    var reviewMessage by remember { mutableStateOf("") }
    var additionalInfo by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email.orEmpty()) }
    var reviewCharacterCount by remember { mutableIntStateOf(0) }
    var reviewMessageError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessView by remember { mutableStateOf(false) }
    var successTicketNumber by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var alertTitle by remember { mutableStateOf("") }
    var alertMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }

    val minimumLength = 25
    val contentTypeIsStory = !notification.storyId.isNullOrEmpty()
    val canSubmit = !isLoading && contactEmail.contains("@") &&
        reviewCharacterCount in minimumLength..2000

    LaunchedEffect(notification) {
        val preview = notification.storyPreviewUrl?.trim().orEmpty()
        if (preview.isNotEmpty()) {
            previewUrl = preview
            return@LaunchedEffect
        }
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        notification.momentId?.takeIf { it.isNotEmpty() }?.let { momentId ->
            val ownerId = notification.targetAuthorId ?: currentUserId
            try {
                val moment = firestoreService.fetchMoment(momentId, ownerId)
                previewUrl = moment.imagePath ?: moment.videoUrl
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        }
        notification.storyId?.takeIf { it.isNotEmpty() }?.let { storyId ->
            val authorId = notification.storyAuthorId ?: notification.targetAuthorId ?: currentUserId
            try {
                val story = firestoreService.fetchStoriesByIds(authorId, listOf(storyId)).firstOrNull()
                previewUrl = story?.mediaItem?.url
            } catch (_: Exception) {
            }
        }
    }

    fun submitReviewRequest() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            alertTitle = context.getString(R.string.appeal_error_title)
            alertMessage = context.getString(R.string.appeal_error_userInfo)
            showAlert = true
            return
        }
        val contentType = if (contentTypeIsStory) "story" else "moment"
        val contentId = notification.storyId ?: notification.momentId.orEmpty()
        val moderationScope = notification.moderationScope ?: if (contentTypeIsStory) "story" else "post"
        isLoading = true
        scope.launch {
            try {
                val response = appealService.submitModerationReview(
                    userId = userId,
                    contentType = contentType,
                    contentId = contentId,
                    moderationScope = moderationScope,
                    message = reviewMessage,
                    email = contactEmail,
                    additionalInfo = additionalInfo.takeIf { it.isNotEmpty() },
                    notificationId = notification.id,
                )
                isLoading = false
                successTicketNumber = response.ticketNumber
                showSuccessView = true
            } catch (error: AppealError) {
                isLoading = false
                alertTitle = context.getString(R.string.appeal_error_submit)
                alertMessage = error.localizedMessage(context)
                showAlert = true
            } catch (error: Exception) {
                isLoading = false
                alertTitle = context.getString(R.string.appeal_error_unexpected)
                alertMessage = error.localizedMessage ?: context.getString(R.string.appeal_error_unknown)
                showAlert = true
            }
        }
    }

    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val primary = colors.primary
    val secondary = if (isDark) Color.White.copy(alpha = 0.76f) else Color.Black.copy(alpha = 0.76f)

    Box(modifier = modifier.fillMaxSize()) {
        if (showSuccessView) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.moderationReview_success_title),
                    color = primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    successTicketNumber?.takeIf { it.isNotEmpty() }?.let {
                        context.getString(R.string.moderationReview_success_message_ticket, it)
                    } ?: stringResource(R.string.moderationReview_success_message),
                    color = secondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                ) {
                    Text(stringResource(R.string.appeal_understood), fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    AppealSheetHeader(
                        canSubmit = canSubmit,
                        isLoading = isLoading,
                        submitLabel = stringResource(R.string.moderationReview_submit),
                        onDismiss = onDismiss,
                        onSubmit = { submitReviewRequest() },
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.moderationReview_title),
                            color = primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.moderationReview_subtitle),
                            color = secondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                val scopeLabel = moderationScopeLabel(notification.moderationScope, contentTypeIsStory)
                AppealInfoCard(
                    title = stringResource(R.string.moderationReview_previewTitle),
                    content = buildString {
                        append(
                            if (contentTypeIsStory) context.getString(R.string.moderationReview_context_story)
                            else context.getString(R.string.moderationReview_context_moment),
                        )
                        append("\n")
                        append(scopeLabel)
                        previewUrl?.let { append("\n").append(it) }
                        append("\n")
                        append(context.getString(R.string.moderationReview_helper))
                    },
                    icon = Icons.Default.Info,
                    accent = Color(0xFF007AFF),
                    primary = primary,
                    secondary = secondary,
                )
                AppealEmailField(
                    email = contactEmail,
                    onEmailChange = { contactEmail = it },
                    title = stringResource(R.string.moderationReview_contactEmail),
                    placeholder = stringResource(R.string.moderationReview_contactEmail_placeholder),
                    primary = primary,
                    secondary = secondary,
                )
                AppealMessageField(
                    message = reviewMessage,
                    onMessageChange = {
                        reviewMessage = it
                        reviewCharacterCount = it.trim().length
                        reviewMessageError = when {
                            reviewCharacterCount < minimumLength && it.isNotEmpty() ->
                                context.getString(
                                    R.string.moderationReview_messageTooShort,
                                    reviewCharacterCount,
                                    minimumLength,
                                )
                            reviewCharacterCount > 2000 ->
                                context.getString(R.string.moderationReview_messageTooLong, reviewCharacterCount)
                            else -> null
                        }
                    },
                    characterCount = reviewCharacterCount,
                    messageError = reviewMessageError,
                    title = stringResource(R.string.moderationReview_messageTitle),
                    placeholder = stringResource(R.string.moderationReview_messagePlaceholder),
                    minimumLength = minimumLength,
                    maximumLength = 2000,
                    primary = primary,
                    secondary = secondary,
                )
                AppealOptionalField(
                    text = additionalInfo,
                    onTextChange = { additionalInfo = it },
                    title = stringResource(R.string.moderationReview_additionalInfo),
                    placeholder = stringResource(R.string.moderationReview_additionalInfo_placeholder),
                    primary = primary,
                    secondary = secondary,
                )
            }
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(alertTitle) },
            text = { Text(alertMessage) },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text(stringResource(R.string.appeal_error_ok))
                }
            },
        )
    }
}

@Composable
private fun moderationScopeLabel(scope: String?, contentTypeIsStory: Boolean): String = when (scope) {
    "storySticker" -> stringResource(R.string.moderationReview_scope_storySticker)
    "postHiddenLayer" -> stringResource(R.string.moderationReview_scope_postHiddenLayer)
    "story" -> stringResource(R.string.moderationReview_scope_story)
    "post" -> stringResource(R.string.moderationReview_scope_post)
    else -> if (contentTypeIsStory) stringResource(R.string.moderationReview_scope_story)
    else stringResource(R.string.moderationReview_scope_post)
}
