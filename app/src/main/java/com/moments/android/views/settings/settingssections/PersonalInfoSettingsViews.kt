package com.moments.android.views.settings.settingssections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.changeUsername
import com.moments.android.services.firestore.fetchUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/**
 * Mirror 1:1 de `PersonalInfoSettingsViews.swift`.
 */
private enum class PersonalInfoState { MAIN, USERNAME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoView(
    username: String,
    email: String,
    onUsernameUpdated: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var currentState by remember { mutableStateOf(PersonalInfoState.MAIN) }
    var currentUsername by remember { mutableStateOf(username) }
    var lastUsernameChange by remember { mutableStateOf<Date?>(null) }
    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        scope.launch {
            try {
                val user = firestoreService.fetchUser(uid)
                lastUsernameChange = user.lastUsernameChange
            } catch (_: Exception) {}
        }
    }

    val canChangeUsername = remember(lastUsernameChange) {
        val last = lastUsernameChange ?: return@remember true
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }
        last.before(cal.time)
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentState == PersonalInfoState.MAIN) "Información personal" else "Cambiar nombre de usuario",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentState == PersonalInfoState.USERNAME) {
                                currentState = PersonalInfoState.MAIN
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentState,
                transitionSpec = {
                    if (targetState == PersonalInfoState.USERNAME) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "PersonalInfoStateTransition"
            ) { targetState ->
                when (targetState) {
                    PersonalInfoState.MAIN -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Row 1: Username
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.05f))
                                    .clickable(enabled = canChangeUsername) {
                                        currentState = PersonalInfoState.USERNAME
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Nombre de usuario",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor
                                    )
                                    if (!canChangeUsername) {
                                        Text(
                                            text = "Cambio disponible cada 6 meses",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9500)
                                        )
                                    }
                                }

                                Text(
                                    text = "@$currentUsername",
                                    fontSize = 14.sp,
                                    color = secondaryColor
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                if (canChangeUsername) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = secondaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9500),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Row 2: Email
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(textColor.copy(alpha = 0.05f))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Correo electrónico",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = email.ifEmpty { "No configurado" },
                                    fontSize = 14.sp,
                                    color = secondaryColor
                                )
                            }
                        }
                    }

                    PersonalInfoState.USERNAME -> {
                        UsernameChangeContent(
                            currentUsername = currentUsername,
                            textColor = textColor,
                            secondaryColor = secondaryColor,
                            onSuccess = { newUsername ->
                                currentUsername = newUsername
                                onUsernameUpdated(newUsername)
                                currentState = PersonalInfoState.MAIN
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsernameChangeContent(
    currentUsername: String,
    textColor: Color,
    secondaryColor: Color,
    onSuccess: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    var newUsername by remember { mutableStateOf(currentUsername) }
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

    fun triggerCheck() {
        isAvailable = null
        errorMessage = null
        checkJob?.cancel()
        val target = newUsername.lowercase()
        if (target.length < 3 || !isDifferent) return

        isChecking = true
        checkJob = scope.launch {
            delay(600) // Debounce 0.6s
            try {
                val doc = firestoreService.db.collection("usernames").document(target).get().await()
                isAvailable = !doc.exists()
            } catch (_: Exception) {
                isAvailable = false
            } finally {
                isChecking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Elige tu nuevo usuario",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = "Solo podrás volver a cambiarlo transcurridos 6 meses.",
                fontSize = 14.sp,
                color = secondaryColor
            )
        }

        OutlinedTextField(
            value = newUsername,
            onValueChange = {
                newUsername = it
                triggerCheck()
            },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("@", color = secondaryColor, fontWeight = FontWeight.Bold) },
            trailingIcon = {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (newUsername.length >= 3 && isDifferent && isAvailable != null) {
                    if (isAvailable == true) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Disponible", tint = Color(0xFF34C759))
                    } else {
                        Icon(Icons.Default.Warning, contentDescription = "No disponible", tint = Color(0xFFFF3B30))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isAvailable == true) Color(0xFF34C759) else Color(0xFF007AFF),
                unfocusedBorderColor = textColor.copy(alpha = 0.2f),
                focusedTextColor = textColor,
                unfocusedTextColor = textColor
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = Color(0xFFFF3B30), fontSize = 13.sp)
        } else if (newUsername.length >= 3 && isDifferent && isAvailable != null) {
            Text(
                text = if (isAvailable == true) "¡Nombre de usuario disponible!" else "Este nombre de usuario ya está cogido",
                color = if (isAvailable == true) Color(0xFF34C759) else Color(0xFFFF3B30),
                fontSize = 13.sp
            )
        } else {
            Text(
                text = "Entre 3 y 30 caracteres (letras, números y guión bajo _)",
                color = secondaryColor,
                fontSize = 12.sp
            )
        }

        Button(
            onClick = {
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button
                isSaving = true
                errorMessage = null

                scope.launch {
                    try {
                        firestoreService.changeUsername(
                            userId = uid,
                            oldUsername = currentUsername,
                            newUsername = newUsername
                        )
                        onSuccess(newUsername.lowercase())
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Error al cambiar el nombre de usuario"
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF007AFF),
                disabledContainerColor = textColor.copy(alpha = 0.15f)
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text(text = "Guardar cambios", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
