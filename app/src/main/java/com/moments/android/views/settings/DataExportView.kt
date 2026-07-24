package com.moments.android.views.settings

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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ExportType { COMPLETE, TEXT_ONLY, MEDIA_ONLY, CHATS_ONLY }

/**
 * Mirror 1:1 de `DataExportView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportView(
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var selectedType by remember { mutableStateOf(ExportType.COMPLETE) }
    var isExporting by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Descargar tu información",
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
                // Header hero
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Copia de seguridad de tus datos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Obtén un archivo ZIP con tus publicaciones, fotos, vídeos, chats e historial de actividad.",
                        fontSize = 14.sp,
                        color = secondaryColor,
                        textAlign = TextAlign.Center
                    )
                }

                // Information card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF007AFF).copy(alpha = 0.08f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "¿Qué incluye la descarga?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                    Text(
                        text = "• Datos del perfil e historial de cuenta\n• Momentos e Historias subidas\n• Mensajes y chats privados\n• Conexiones y mejores amigos",
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }

                // Export Options
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "TIPO DE DESCARGA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor
                    )

                    ExportOptionCard(
                        title = "Completo (Recomendado)",
                        subtitle = "Fotos, vídeos, texto y chats (~45 MB)",
                        icon = Icons.Default.Download,
                        isSelected = selectedType == ExportType.COMPLETE,
                        textColor = textColor,
                        onClick = { selectedType = ExportType.COMPLETE }
                    )

                    ExportOptionCard(
                        title = "Solo texto y publicaciones",
                        subtitle = "Perfil, comentarios e historial (~2 MB)",
                        icon = Icons.Default.TextSnippet,
                        isSelected = selectedType == ExportType.TEXT_ONLY,
                        textColor = textColor,
                        onClick = { selectedType = ExportType.TEXT_ONLY }
                    )

                    ExportOptionCard(
                        title = "Solo archivos multimedia",
                        subtitle = "Fotos y vídeos en resolución original (~40 MB)",
                        icon = Icons.Default.Image,
                        isSelected = selectedType == ExportType.MEDIA_ONLY,
                        textColor = textColor,
                        onClick = { selectedType = ExportType.MEDIA_ONLY }
                    )

                    ExportOptionCard(
                        title = "Solo conversaciones de chat",
                        subtitle = "Historial de mensajes privados (~5 MB)",
                        icon = Icons.Default.Chat,
                        isSelected = selectedType == ExportType.CHATS_ONLY,
                        textColor = textColor,
                        onClick = { selectedType = ExportType.CHATS_ONLY }
                    )
                }

                if (exportSuccessMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF34C759).copy(alpha = 0.1f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = exportSuccessMessage!!,
                            fontSize = 14.sp,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        isExporting = true
                        exportSuccessMessage = null
                        scope.launch {
                            delay(2000) // Simular exportación
                            isExporting = false
                            exportSuccessMessage = "Solicitud enviada. Te enviaremos un email cuando el archivo esté listo."
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                    } else {
                        Text(text = "Solicitar descarga", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF007AFF).copy(alpha = 0.1f) else textColor.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF007AFF) else textColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = textColor.copy(alpha = 0.5f)
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}
