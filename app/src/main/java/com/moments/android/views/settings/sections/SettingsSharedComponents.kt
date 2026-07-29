package com.moments.android.views.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.settings.SettingsProfileColors

/**
 * Port de `SettingsGroup` / `SettingsRow` / `SettingsVersionFooter`
 * (SettingsSections.swift) — componentes compartidos del formulario.
 */

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.35f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    attachmentIcon: AttachmentIcon? = null,
    audienceIcon: ContentAudience? = null,
    isDestructive: Boolean = false,
    isExternal: Boolean = false,
    /** ≡ iOS `icon == "star.fill"` → verde. */
    starFillTint: Boolean = false,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val titleColor = when {
        isDestructive -> Color.Red
        isDark -> Color.White
        else -> Color.Black
    }
    val iconColor = when {
        isDestructive -> Color.Red
        starFillTint -> Color(0xFF34C759)
        isDark -> Color.White
        else -> Color.Black
    }

    Column(
        Modifier
            .fillMaxWidth()
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                audienceIcon != null -> {
                    AudienceIconView(
                        audience = audienceIcon,
                        size = AudienceIconMetrics.row,
                        tintColor = if (audienceIcon == ContentAudience.BEST_FRIENDS) {
                            Color(0xFF34C759)
                        } else {
                            null
                        },
                        modifier = Modifier.width(28.dp),
                    )
                }
                attachmentIcon != null -> {
                    AttachmentIconView(
                        icon = attachmentIcon,
                        preset = AttachmentIconPreset.SETTINGS_ROW,
                        tintColor = iconColor,
                        modifier = Modifier.width(28.dp),
                    )
                }
                icon != null -> {
                    Box(
                        Modifier.width(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                        color = Color.Gray,
                    )
                }
            }
            Icon(
                imageVector = if (isExternal) {
                    Icons.AutoMirrored.Filled.OpenInNew
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = Color.Gray.copy(if (isExternal) 0.5f else 0.3f),
                modifier = Modifier.size(if (isExternal) 13.dp else 12.dp),
            )
        }
        HorizontalDivider(
            Modifier.padding(start = 42.dp),
            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.2f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
fun SettingsVersionFooter() {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(
                if (isDark) R.drawable.splash_logo_dark else R.drawable.splash_logo_light,
            ),
            contentDescription = null,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "v$version",
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color.White.copy(0.35f) else Color.Black.copy(0.30f),
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = if (isDark) Color.White else Color.Black
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.Medium,
                    color = primary,
                )
                Text(
                    subtitle,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    color = Color.Gray,
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = SettingsProfileColors.toggleTint,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = 42.dp),
                color = primary.copy(alpha = 0.2f),
                thickness = 0.5.dp,
            )
        }
    }
}
