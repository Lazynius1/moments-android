package com.moments.android.views.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moments.android.R
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

internal val SettingsRowHorizontalPadding = 16.dp
internal val SettingsIconSlotWidth = 28.dp
internal val SettingsIconTextSpacing = 14.dp
internal val SettingsDividerStart =
    SettingsRowHorizontalPadding + SettingsIconSlotWidth + SettingsIconTextSpacing
val SettingsSectionOuterPadding = 8.dp
val SettingsSectionShape = RoundedCornerShape(20.dp)

@Composable
fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SettingsSectionShape,
        color = SettingsProfileColors.surfaceContainer(isDark),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun SettingsSubsectionGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsSectionOuterPadding),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title.uppercase(),
                fontWeight = FontWeight.Medium,
                color = SettingsProfileColors.onSurfaceVariant(isDark),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(
                    start = SettingsRowHorizontalPadding,
                    end = SettingsRowHorizontalPadding,
                    bottom = 8.dp,
                ),
            )
        }
        SettingsSectionCard(content = content)
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.Medium,
            color = SettingsProfileColors.onSurfaceVariant(isDark),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                start = SettingsRowHorizontalPadding,
                end = SettingsRowHorizontalPadding,
                bottom = 8.dp,
            ),
        )
        SettingsSectionCard {
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val titleColor = when {
        isDestructive -> Color.Red
        else -> SettingsProfileColors.onSurface(isDark)
    }
    val iconColor = when {
        isDestructive -> Color.Red
        starFillTint -> Color(0xFF34C759)
        else -> SettingsProfileColors.onSurface(isDark)
    }

    Column(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = 10.dp,
                ),
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
                        modifier = Modifier.width(SettingsIconSlotWidth),
                    )
                }
                attachmentIcon != null -> {
                    AttachmentIconView(
                        icon = attachmentIcon,
                        preset = AttachmentIconPreset.SETTINGS_ROW,
                        tintColor = iconColor,
                        modifier = Modifier.width(SettingsIconSlotWidth),
                    )
                }
                icon != null -> {
                    Box(
                        Modifier.width(SettingsIconSlotWidth),
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
            Spacer(Modifier.width(SettingsIconTextSpacing))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = SettingsProfileColors.onSurfaceVariant(isDark),
                        style = MaterialTheme.typography.bodyMedium,
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
                tint = SettingsProfileColors.onSurfaceVariant(isDark),
                modifier = Modifier.size(if (isExternal) 18.dp else 20.dp),
            )
        }
        HorizontalDivider(
            Modifier.padding(start = SettingsDividerStart),
            color = SettingsProfileColors.outlineVariant(isDark),
            thickness = 1.dp,
        )
    }
}

@Composable
fun SettingsVersionFooter() {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
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
            fontWeight = FontWeight.Medium,
            color = SettingsProfileColors.onSurfaceVariant(isDark),
            style = MaterialTheme.typography.labelMedium,
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
    val primary = SettingsProfileColors.onSurface(isDark)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(SettingsIconSlotWidth), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(SettingsIconTextSpacing))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    subtitle,
                    color = SettingsProfileColors.onSurfaceVariant(isDark),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SettingsProfileColors.toggleTint,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = SettingsProfileColors.surfaceContainer(isDark),
                    uncheckedBorderColor = SettingsProfileColors.outlineVariant(isDark),
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = SettingsDividerStart),
                color = SettingsProfileColors.outlineVariant(isDark),
                thickness = 1.dp,
            )
        }
    }
}
