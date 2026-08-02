package com.moments.android.views.settings.settingssections

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.changeUsername
import com.moments.android.services.firestore.fetchUser
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsToolbarBackButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/**
 * Port de `PersonalInfoSettingsViews.swift` — `PersonalInfoView` + `UsernameChangeContent`.
 *
 * `phoneNumber` existe en iOS como Binding pero no se muestra en UI → no se inventa fila.
 */

private enum class PersonalInfoViewState { MAIN, USERNAME }

@Composable
fun PersonalInfoView(
    username: String,
    email: String,
    onUsernameUpdated: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val firestoreService = remember { FirestoreService() }

    var viewState by remember { mutableStateOf(PersonalInfoViewState.MAIN) }
    var currentUsername by remember(username) { mutableStateOf(username) }
    var lastUsernameChange by remember { mutableStateOf<Date?>(null) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        runCatching {
            lastUsernameChange = firestoreService.fetchUser(uid).lastUsernameChange
        }
    }

    val canChangeUsername = remember(lastUsernameChange) {
        val last = lastUsernameChange ?: return@remember true
        val sixMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.time
        !last.after(sixMonthsAgo)
    }

    val nextAvailableDate = remember(lastUsernameChange, canChangeUsername) {
        if (canChangeUsername) return@remember null
        val last = lastUsernameChange ?: return@remember null
        val next = Calendar.getInstance().apply {
            time = last
            add(Calendar.MONTH, 6)
        }.time
        MomentsFormat.smartDate(next, MomentsFormat.DateContext.LONG_DATE)
    }

    val title = when (viewState) {
        PersonalInfoViewState.MAIN -> stringResource(R.string.settings_sections_personal_info)
        PersonalInfoViewState.USERNAME -> stringResource(R.string.username_change_title)
    }

    BackHandler(enabled = viewState == PersonalInfoViewState.USERNAME) {
        viewState = PersonalInfoViewState.MAIN
    }

    Column(Modifier.fillMaxSize()) {
        // ≡ navigationTitle + toolbar leading back solo en .username
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            if (viewState == PersonalInfoViewState.USERNAME) {
                SettingsToolbarBackButton(
                    onNavigateBack = { viewState = PersonalInfoViewState.MAIN },
                )
            }
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedContent(
            targetState = viewState,
            transitionSpec = {
                if (targetState == PersonalInfoViewState.USERNAME) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "personalInfoFlow",
            modifier = Modifier.fillMaxSize(),
        ) { state ->
            when (state) {
                PersonalInfoViewState.MAIN -> PersonalInfoMainContent(
                    username = currentUsername,
                    email = email,
                    canChangeUsername = canChangeUsername,
                    nextAvailableDate = nextAvailableDate,
                    primary = primary,
                    onOpenUsername = {
                        if (canChangeUsername) viewState = PersonalInfoViewState.USERNAME
                    },
                )
                PersonalInfoViewState.USERNAME -> UsernameChangeContent(
                    currentUsername = currentUsername,
                    onUsernameChanged = { newName, changeDate ->
                        currentUsername = newName
                        lastUsernameChange = changeDate
                        onUsernameUpdated(newName)
                        viewState = PersonalInfoViewState.MAIN
                    },
                )
            }
        }
    }
}

@Composable
private fun PersonalInfoMainContent(
    username: String,
    email: String,
    canChangeUsername: Boolean,
    nextAvailableDate: String?,
    primary: Color,
    onOpenUsername: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
                .clickable(
                    enabled = canChangeUsername,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onOpenUsername,
                )
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    stringResource(R.string.settings_profile_username),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                )
                if (nextAvailableDate != null) {
                    Text(
                        stringResource(R.string.username_available_on, nextAvailableDate),
                        fontSize = 12.sp,
                        color = Color(0xFFFF9500),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "@${username.ifEmpty { "—" }}",
                fontSize = 14.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (canChangeUsername) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray.copy(0.3f),
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color(0xFFFF9500).copy(0.8f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        HorizontalDivider(
            Modifier
                .padding(start = 2.dp)
                .padding(vertical = 4.dp),
            color = Color.Gray.copy(0.2f),
            thickness = 0.5.dp,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 2.dp)
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_profile_email),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (email.isEmpty()) {
                    stringResource(R.string.settings_not_configured)
                } else {
                    email
                },
                fontSize = 14.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UsernameChangeContent(
    currentUsername: String,
    onUsernameChanged: (String, Date) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val secondary = primary.copy(0.55f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    var newUsername by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkJob by remember { mutableStateOf<Job?>(null) }

    val isValidFormat = remember(newUsername) {
        val clean = newUsername.lowercase()
        clean.length in 3..30 && clean.all { it.isLetterOrDigit() || it == '_' }
    }
    val isDifferent = newUsername.lowercase() != currentUsername.lowercase()
    val canSave = isValidFormat && isDifferent && isAvailable == true && !isSaving

    val borderColor = when {
        isAvailable != null && newUsername.length >= 3 && isDifferent -> {
            if (isAvailable == true) Color.Green.copy(0.6f) else Color.Red.copy(0.6f)
        }
        else -> Color.White.copy(if (isDark) 0.15f else 0.4f)
    }

    fun triggerAvailabilityCheck(value: String) {
        isAvailable = null
        errorMessage = null
        checkJob?.cancel()
        val clean = value.lowercase()
        val different = clean != currentUsername.lowercase()
        if (clean.length < 3 || !different) return
        isChecking = true
        checkJob = scope.launch {
            delay(600)
            try {
                val doc = firestoreService.db.collection("usernames").document(clean).get().await()
                isAvailable = !doc.exists()
            } catch (_: Exception) {
                isAvailable = false
            } finally {
                isChecking = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.username_change_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primary,
            )
            Text(
                stringResource(R.string.username_change_subtitle),
                fontSize = 14.sp,
                color = secondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        (if (isDark) Color.White else Color.Black).copy(0.06f),
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = newUsername,
                    onValueChange = {
                        newUsername = it
                        triggerAvailabilityCheck(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = primary, fontSize = 17.sp),
                    cursorBrush = SolidColor(primary),
                    decorationBox = { inner ->
                        Box {
                            if (newUsername.isEmpty()) {
                                Text(currentUsername, color = secondary.copy(0.5f), fontSize = 17.sp)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                when {
                    isChecking -> MomentsCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    newUsername.length >= 3 && isDifferent && isAvailable != null -> {
                        Icon(
                            if (isAvailable == true) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (isAvailable == true) Color(0xFF34C759) else Color.Red,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            when {
                errorMessage != null -> Text(errorMessage!!, fontSize = 13.sp, color = Color.Red)
                newUsername.length >= 3 && isDifferent && isAvailable != null -> Text(
                    if (isAvailable == true) {
                        stringResource(R.string.username_available)
                    } else {
                        stringResource(R.string.username_taken)
                    },
                    fontSize = 13.sp,
                    color = if (isAvailable == true) Color(0xFF34C759) else Color.Red,
                )
                else -> Text(
                    stringResource(R.string.username_rules),
                    fontSize = 13.sp,
                    color = secondary,
                )
            }
        }

        val saveInteraction = remember { MutableInteractionSource() }
        val saveBg = if (canSave) primary else Color.Gray.copy(0.3f)
        val saveFg = if (canSave) {
            if (isDark) Color.Black else Color.White
        } else {
            Color.Gray
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(saveBg, RoundedCornerShape(14.dp))
                .momentsPress(saveInteraction, MomentsPressDefaults.momentsPressSubtle)
                .clickable(
                    enabled = canSave,
                    interactionSource = saveInteraction,
                    indication = null,
                    onClick = {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@clickable
                        val oldLower = currentUsername.lowercase()
                        isSaving = true
                        errorMessage = null
                        scope.launch {
                            try {
                                firestoreService.changeUsername(
                                    userId = uid,
                                    oldUsername = currentUsername,
                                    newUsername = newUsername,
                                )
                                val newLower = newUsername.lowercase()
                                val now = Date()
                                val email = FirebaseAuth.getInstance().currentUser?.email
                                val prefs = context.getSharedPreferences(
                                    "moments_auth",
                                    Context.MODE_PRIVATE,
                                )
                                prefs.edit().apply {
                                    if (!email.isNullOrEmpty()) {
                                        putString("cachedEmail_$newLower", email)
                                    }
                                    remove("cachedEmail_$oldLower")
                                    putString("current_username", newLower)
                                    apply()
                                }
                                onUsernameChanged(newLower, now)
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = saveFg,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.username_change_save),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = saveFg,
            )
        }
    }
}
