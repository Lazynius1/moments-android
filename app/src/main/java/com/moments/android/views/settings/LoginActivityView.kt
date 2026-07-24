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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

/**
 * Mirror 1:1 de `LoginActivityView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginActivityView(
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(600)
        isLoading = false
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Actividad de inicio de sesión",
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
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SESIÓN ACTUAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryColor
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(textColor.copy(alpha = 0.05f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Este dispositivo Android",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• Activo ahora",
                                        fontSize = 12.sp,
                                        color = Color(0xFF34C759),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Último acceso: Hoy a las 21:40",
                                    fontSize = 13.sp,
                                    color = secondaryColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = secondaryColor.copy(alpha = 0.15f))

                    // Other sessions
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "OTRAS SESIONES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryColor
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(textColor.copy(alpha = 0.03f))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = secondaryColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No hay otras sesiones activas",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Text(
                                text = "No se ha iniciado sesión en otros dispositivos.",
                                fontSize = 13.sp,
                                color = secondaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}
