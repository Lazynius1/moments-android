package com.moments.android.views.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.settings.SettingsRoute

/** Port de ProfileSection / AccountSection / ArchiveSection (SettingsSections.swift). */

@Composable
fun ProfileSection(username: String) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentUser by AuthService.currentUser.collectAsState()
    val primary = if (isDark) Color.White else Color.Black

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val userId = currentUser?.id
            if (!userId.isNullOrBlank()) {
                AsyncProfileImageView(
                    userId = userId,
                    modifier = Modifier.size(80.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(if (isDark) 0.3f else 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        null,
                        tint = primary.copy(0.7f),
                        modifier = Modifier.size(35.dp),
                    )
                }
            }

            // Plus / badges 🚫 en Android — solo anillo Plus vía isPlusSubscriber (flag Firestore).
            if (currentUser?.isPlusSubscriber == true) {
                Box(
                    Modifier
                        .size(88.dp)
                        .border(
                            width = 3.dp,
                            brush = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))),
                            shape = CircleShape,
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-28).dp, y = (-28).dp)
                        .size(24.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♛", color = Color(0xFFFFD700), fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() })
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = username.ifBlank { stringResource(R.string.settings_profile_fallback_username) },
                    fontSize = with(density) { legacyPoppinsSize(context, 20).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
                if (currentUser?.isPlusSubscriber == true) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_common_pro),
                        fontSize = with(density) { legacyPoppinsSize(context, 10).toSp() },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (currentUser?.isPlusSubscriber == true) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFD700).copy(0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("♛", color = Color(0xFFFFD700), fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_plus_active),
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFD700),
                    )
                }
            }
        }
    }
}

@Composable
fun AccountSection(
    onShowPersonalInfo: () -> Unit,
    onShowQRCode: () -> Unit,
) {
    Column {
        SettingsRow(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.settings_sections_personal_info),
            subtitle = stringResource(R.string.settings_sections_personal_info_subtitle),
            onClick = onShowPersonalInfo,
        )
        SettingsRow(
            icon = Icons.Filled.QrCode,
            title = stringResource(R.string.settings_sections_qr_code),
            subtitle = stringResource(R.string.settings_sections_qr_code_subtitle),
            onClick = onShowQRCode,
        )
    }
}

@Composable
fun ArchiveSection(onRoute: (SettingsRoute) -> Unit) {
    SettingsRow(
        icon = Icons.Filled.Archive,
        title = stringResource(R.string.settings_sections_archived_stories),
        subtitle = stringResource(R.string.settings_sections_archived_stories_subtitle),
        onClick = { onRoute(SettingsRoute.ARCHIVED_STORIES) },
    )
}
