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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.services.messaging.ChatCacheStore

/**
 * Mirror 1:1 de `ChatStorageSettingsView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStorageSettingsView(
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var showClearDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var mediaBytes by remember { mutableStateOf(1024 * 1024 * 45L) } // 45 MB simulated cache

    val maxQuota = 1024 * 1024 * 500L // 500 MB quota
    val progress = (mediaBytes.toFloat() / maxQuota.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Almacenamiento de chat",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header / Quota usage
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${mediaBytes / (1024 * 1024)} MB",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "Caché ocupada en este dispositivo (Límite: 500 MB)",
                        fontSize = 13.sp,
                        color = secondaryColor
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF007AFF),
                        trackColor = textColor.copy(alpha = 0.1f)
                    )
                }

                HorizontalDivider(color = secondaryColor.copy(alpha = 0.15f))

                // Action buttons
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ACCIONES DE LIMPIEZA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor
                    )

                    Button(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3B30).copy(alpha = 0.1f),
                            contentColor = Color(0xFFFF3B30)
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vaciar caché de fotos y vídeos", fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = "Los archivos borrados se volverán a descargar automáticamente si abres el chat.",
                        fontSize = 12.sp,
                        color = secondaryColor
                    )
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        fontSize = 13.sp,
                        color = Color(0xFF34C759)
                    )
                }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Vaciar caché multimedia") },
                    text = { Text("¿Deseas eliminar las imágenes y vídeos almacenados localmente en este dispositivo?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                mediaBytes = 0
                                statusMessage = "Caché de chat vaciada correctamente"
                                showClearDialog = false
                            }
                        ) {
                            Text("Vaciar", color = Color(0xFFFF3B30))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}
