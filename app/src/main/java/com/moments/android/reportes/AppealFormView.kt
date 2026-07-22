package com.moments.android.reportes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.MomentsNotification
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchStoriesByIds
import kotlinx.coroutines.launch

/** Port de AppealFormView.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppealFormView(
    suspensionReason: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appealService = remember { AppealService.getInstance(context) }

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

    val canSubmit = contactEmail.contains("@") &&
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appeal_title)) },
                navigationIcon = {
                    TextButton(onClick = onDismiss) { Text("✕") }
                },
                actions = {
                    TextButton(onClick = { submitAppeal() }, enabled = canSubmit) {
                        if (isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text(stringResource(R.string.appeal_submitButton))
                    }
                },
            )
        },
    ) { padding ->
        if (showSuccessView && appealResult != null) {
            AppealSuccessView(result = appealResult!!, onDismiss = onDismiss, modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AppealFormHeader()
                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text(stringResource(R.string.appeal_contactEmail)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                suspensionReason?.let {
                    AppealInfoCard(
                        title = stringResource(R.string.appeal_suspensionReason),
                        content = it,
                    )
                }
                OutlinedTextField(
                    value = appealMessage,
                    onValueChange = {
                        appealMessage = it
                        updateCharacterCount()
                    },
                    label = { Text(stringResource(R.string.appeal_yourAppeal)) },
                    placeholder = { Text(stringResource(R.string.appeal_yourAppeal_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    supportingText = {
                        Text(stringResource(R.string.appeal_field_characterCount, characterCount))
                    },
                    isError = messageError != null,
                )
                messageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    label = { Text(stringResource(R.string.appeal_additionalInfo)) },
                    placeholder = { Text(stringResource(R.string.appeal_additionalInfo_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
                AppealRequirements(characterCount = characterCount, email = contactEmail)
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
private fun AppealFormHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.appeal_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.appeal_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AppealInfoCard(title: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppealRequirements(characterCount: Int, email: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.appeal_requirements), style = MaterialTheme.typography.titleSmall)
            RequirementRow("Min 50", characterCount >= 50)
            RequirementRow("Max 2000", characterCount in 1..2000)
            RequirementRow(stringResource(R.string.appeal_contactEmail), email.contains("@"))
        }
    }
}

@Composable
private fun RequirementRow(text: String, isCompleted: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AppealSuccessView(result: AppealResult, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.appeal_success_title), style = MaterialTheme.typography.headlineMedium)
        Text(result.message, style = MaterialTheme.typography.bodyMedium)
        result.ticketNumber?.let {
            AppealInfoCard(stringResource(R.string.appeal_success_ticketNumber), it)
        }
        result.estimatedResponseTime?.let {
            AppealInfoCard(stringResource(R.string.appeal_success_estimatedResponse), it)
        }
        result.priority?.let {
            AppealInfoCard(stringResource(R.string.appeal_success_priority), it.replaceFirstChar { c -> c.uppercase() })
        }
        if (result.nextSteps.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.appeal_nextSteps), style = MaterialTheme.typography.titleSmall)
                    result.nextSteps.forEach { step -> Text("• $step", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.appeal_understood))
        }
    }
}

/** Port de ModerationReviewRequestSheet en AppealFormView.swift */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderationReview_title)) },
                navigationIcon = { TextButton(onClick = onDismiss) { Text("✕") } },
                actions = {
                    TextButton(onClick = { submitReviewRequest() }, enabled = canSubmit) {
                        if (isLoading) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text(stringResource(R.string.moderationReview_submit))
                    }
                },
            )
        },
    ) { padding ->
        if (showSuccessView) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.moderationReview_success_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    successTicketNumber?.takeIf { it.isNotEmpty() }?.let {
                        context.getString(R.string.moderationReview_success_message_ticket, it)
                    } ?: stringResource(R.string.moderationReview_success_message),
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.appeal_understood))
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(stringResource(R.string.moderationReview_subtitle), style = MaterialTheme.typography.bodyMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.moderationReview_previewTitle), style = MaterialTheme.typography.labelMedium)
                        Text(
                            if (contentTypeIsStory) stringResource(R.string.moderationReview_context_story)
                            else stringResource(R.string.moderationReview_context_moment),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(moderationScopeLabel(notification.moderationScope, contentTypeIsStory))
                        previewUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(stringResource(R.string.moderationReview_helper), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text(stringResource(R.string.moderationReview_contactEmail)) },
                    placeholder = { Text(stringResource(R.string.moderationReview_contactEmail_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reviewMessage,
                    onValueChange = {
                        reviewMessage = it
                        reviewCharacterCount = it.trim().length
                        reviewMessageError = when {
                            reviewCharacterCount < minimumLength && it.isNotEmpty() ->
                                context.getString(R.string.moderationReview_messageTooShort, reviewCharacterCount, minimumLength)
                            reviewCharacterCount > 2000 ->
                                context.getString(R.string.moderationReview_messageTooLong, reviewCharacterCount)
                            else -> null
                        }
                    },
                    label = { Text(stringResource(R.string.moderationReview_messageTitle)) },
                    placeholder = { Text(stringResource(R.string.moderationReview_messagePlaceholder)) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                )
                reviewMessageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    label = { Text(stringResource(R.string.moderationReview_additionalInfo)) },
                    placeholder = { Text(stringResource(R.string.moderationReview_additionalInfo_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
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
