package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.models.AppUser
import com.moments.android.models.OnlineStatus
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.sections.SettingsSectionCard
import com.moments.android.views.shared.MomentsModalSheet

/**
 * Port de `MessagingComposerAndStatusViews.swift`.
 * Sheets / superficies secundarias siguen [ANDROID_SETTINGS_UI_STYLE.md].
 */

// Retícula ≡ ANDROID_SETTINGS_UI_STYLE (cajas y filas).
private val SheetContentHorizontalPadding = 20.dp
private val SheetHeaderTopExtra = 0.dp
private val RowHorizontalPadding = 16.dp
private val RowMinHeight = 64.dp
private val IconSlotWidth = 28.dp
private val IconVisualSize = 19.dp
private val IconTextSpacing = 14.dp
private val DividerStart = RowHorizontalPadding + IconSlotWidth + IconTextSpacing // 58.dp

@Composable
fun MessageComposerView(
    selectedUser: AppUser?,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = AdaptiveColors(isDark)
    val canSend = messageText.trim().isNotEmpty()
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldFill = SettingsProfileColors.surfaceContainer(isDark)

    Column(
        modifier
            .fillMaxSize()
            // Canvas sólido adaptativo — sin gradient/glass iOS
            .background(colors.surfaceBackground)
            .imePadding()
            .padding(horizontal = SheetContentHorizontalPadding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.common_cancel),
                color = colors.accent,
                fontSize = 17.sp,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.messaging_compose_title),
                color = colors.primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(72.dp))
        }

        selectedUser?.let { user ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncProfileImageView(
                    userId = user.id,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                )
                Text(
                    user.username,
                    color = colors.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.messaging_write_message_to_start),
                    color = SettingsProfileColors.onSurfaceVariant(isDark),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp)
                    .clip(fieldShape)
                    .background(fieldFill)
                    .border(1.dp, SettingsProfileColors.outlineVariant(isDark), fieldShape)
                    .padding(16.dp),
                textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                cursorBrush = SolidColor(colors.accent),
                maxLines = 6,
                decorationBox = { inner ->
                    Box {
                        if (messageText.isEmpty()) {
                            Text(
                                stringResource(R.string.messaging_compose_placeholder),
                                color = SettingsProfileColors.onSurfaceVariant(isDark),
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSend) colors.accent else SettingsProfileColors.onSurfaceVariant(isDark))
                    .clickable(enabled = canSend, onClick = onSend)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.messaging_send_message),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Port de `OnlineStatusSelectorView`.
 * `.presentationDetents([.medium, .large])` → [MomentsModalSheet] `largeOnly = false`.
 */
@Composable
fun OnlineStatusSelectorView(
    currentStatus: OnlineStatus,
    onStatusSelected: (OnlineStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = false,
    ) { dismiss ->
        OnlineStatusSelectorSheetContent(
            currentStatus = currentStatus,
            onStatusSelected = { status ->
                onStatusSelected(status)
                dismiss()
            },
        )
    }
}

@Composable
private fun OnlineStatusSelectorSheetContent(
    currentStatus: OnlineStatus,
    onStatusSelected: (OnlineStatus) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val colors = AdaptiveColors(isDark)

    Column(
        Modifier
            .fillMaxWidth()
            // Header tras el handle: 4–8.dp; sin reaplicar insets del host
            .padding(top = SheetHeaderTopExtra)
            .padding(horizontal = SheetContentHorizontalPadding)
            .padding(bottom = 20.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.messaging_status_current),
                color = SettingsProfileColors.onSurfaceVariant(isDark),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Circle,
                    contentDescription = null,
                    tint = Color(currentStatus.colorArgb),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    stringResource(currentStatus.displayNameRes),
                    color = SettingsProfileColors.onSurface(isDark),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.size(24.dp))

        // Caja de filas ≡ SettingsSectionCard (radio 20, superficie sólida)
        SettingsSectionCard {
            OnlineStatus.entries.forEachIndexed { index, status ->
                val selected = status == currentStatus
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RowMinHeight)
                        .background(
                            if (selected) {
                                SettingsProfileColors.onSurface(isDark).copy(alpha = 0.06f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(role = Role.Button) { onStatusSelected(status) }
                        .padding(horizontal = RowHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(IconSlotWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            status.composeIcon,
                            contentDescription = null,
                            tint = Color(status.colorArgb),
                            modifier = Modifier.size(IconVisualSize),
                        )
                    }
                    Spacer(Modifier.width(IconTextSpacing))
                    Text(
                        stringResource(status.displayNameRes),
                        color = SettingsProfileColors.onSurface(isDark),
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (index < OnlineStatus.entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = DividerStart),
                        color = SettingsProfileColors.outlineVariant(isDark),
                    )
                }
            }
        }
    }
}

/** Colores/iconos UI ≡ iOS `OnlineStatus` + mapeo Material de Settings. */
private val OnlineStatus.displayNameRes: Int
    get() = when (this) {
        OnlineStatus.ONLINE -> R.string.messaging_status_online
        OnlineStatus.AWAY -> R.string.messaging_status_away
        OnlineStatus.BUSY -> R.string.messaging_status_busy
        OnlineStatus.OFFLINE -> R.string.messaging_status_offline
        OnlineStatus.INVISIBLE -> R.string.messaging_status_invisible
    }

private val OnlineStatus.composeIcon: ImageVector
    get() = when (this) {
        OnlineStatus.ONLINE -> Icons.Filled.Circle
        OnlineStatus.AWAY -> Icons.Filled.NightsStay
        OnlineStatus.BUSY -> Icons.Filled.Error
        OnlineStatus.OFFLINE -> Icons.Filled.RadioButtonUnchecked
        OnlineStatus.INVISIBLE -> Icons.Filled.VisibilityOff
    }
