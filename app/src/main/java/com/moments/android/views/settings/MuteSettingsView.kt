package com.moments.android.views.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.moments.android.services.firestore.fetchUser
import kotlinx.coroutines.launch

/**
 * Mirror 1:1 de `MuteSettingsView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuteSettingsView(
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }
    var isLoading by remember { mutableStateOf(true) }
    var mutedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var newWordText by remember { mutableStateOf("") }
    var muteNotifications by remember { mutableStateOf(false) }

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        if (uid != null) {
            scope.launch {
                try {
                    val user = firestoreService.fetchUser(uid)
                    mutedWords = emptyList() // words
                } catch (_: Exception) {
                } finally {
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Silenciar palabras y cuentas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = textColor)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Muted Words Section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "PALABRAS SILENCIADAS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryColor
                        )

                        Text(
                            text = "No verás momentos o comentarios que contengan estas palabras.",
                            fontSize = 13.sp,
                            color = secondaryColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newWordText,
                                onValueChange = { newWordText = it },
                                placeholder = { Text("Añadir palabra o hashtag") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val word = newWordText.trim().lowercase()
                                    if (word.isNotEmpty() && !mutedWords.contains(word) && uid != null) {
                                        val updated = mutedWords + word
                                        mutedWords = updated
                                        newWordText = ""
                                        firestoreService.db.collection("users").document(uid)
                                            .update("mutedWords", updated)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Añadir")
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            mutedWords.forEach { word ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(textColor.copy(alpha = 0.05f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#$word",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val updated = mutedWords.filter { it != word }
                                            mutedWords = updated
                                            if (uid != null) {
                                                firestoreService.db.collection("users").document(uid)
                                                    .update("mutedWords", updated)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Eliminar",
                                            tint = secondaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = secondaryColor.copy(alpha = 0.15f))

                    // Notifications Mute Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Silenciar avisos de palabras muteadas",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            Text(
                                text = "No recibirás notificaciones push si contienen palabras filtradas.",
                                fontSize = 12.sp,
                                color = secondaryColor
                            )
                        }

                        Switch(
                            checked = muteNotifications,
                            onCheckedChange = { checked ->
                                muteNotifications = checked
                                if (uid != null) {
                                    firestoreService.db.collection("users").document(uid)
                                        .update("muteNotifications", checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF34C759)
                            )
                        )
                    }
                }
            }
        }
    }
}
