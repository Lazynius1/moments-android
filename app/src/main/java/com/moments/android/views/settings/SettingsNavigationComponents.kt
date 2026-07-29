package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.ChromeIconDescription
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.views.messaging.components.momentsScrollEdgeChrome
import com.moments.android.views.profile.core.sections.momentZoomNavigationSurface
import com.moments.android.views.profile.core.sections.profileGridNavigationChrome

/**
 * Port 1:1 de `SettingsNavigationComponents.swift`.
 *
 * Canvas sólido AdaptiveColors (`#0B1215` / `#FAF9F6`) — sin material/blur del sheet iOS
 * (el `ultraThinMaterial` iOS va a opacity 0.02; en Android no se porta).
 */

/** ≡ iOS `SettingsToolbarBackButton` — ProfileChromeIconButton · navigationBack · sin glass standalone. */
@Composable
fun SettingsToolbarBackButton(
    onNavigateBack: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    ProfileChromeIconButton(
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        onClick = onNavigateBack,
        foregroundColor = if (isDark) Color.White else Color.Black,
        preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
        standaloneGlass = false,
        contentDescriptionKey = ChromeIconDescription.BACK,
    )
}

/**
 * ≡ iOS `SettingsNavigationBar` — back + título centrado + trailing opcional (o spacer del controlSize).
 * En Compose [onNavigateBack] sustituye a `@Environment(\.dismiss)`.
 */
@Composable
fun SettingsNavigationBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val titleColor = if (isDark) Color.White else Color.Black
    val controlSize = MomentsGlassButtonPreset.NAVIGATION_BACK.controlSize

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsToolbarBackButton(onNavigateBack = onNavigateBack)
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Box(
                Modifier.size(width = controlSize * 2.2f, height = controlSize),
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailing()
            }
        } else {
            // Espacio para mantener el título centrado ≡ iOS Rectangle clear · navigationBack.controlSize
            Box(Modifier.size(controlSize))
        }
    }
}

/** ≡ iOS `SettingsSubsectionBackground` — canvas sólido AdaptiveColors. */
@Composable
fun SettingsSubsectionBackground(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
    )
}

/**
 * ≡ iOS `SettingsSubsectionWrapper` — fondo + chrome + barra leading back + contenido.
 * [onNavigateBack] ≡ dismiss iOS.
 */
@Composable
fun SettingsSubsectionWrapper(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .settingsSubsectionNavigationChrome()
            .statusBarsPadding(),
    ) {
        SettingsSubsectionBackground()
        Column(Modifier.fillMaxSize()) {
            // ≡ navigationTitle(inline) + toolbar leading back (+ trailing opcional)
            SettingsNavigationBar(
                title = title,
                onNavigateBack = onNavigateBack,
                trailing = trailing,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * ≡ iOS `settingsSubsectionNavigationChrome` /
 * `SettingsSubsectionNavigationChromeModifier`.
 *
 * Compose: surface + chrome no-op de NavigationStack + scroll-edge soft.
 * (backButtonHidden / interactivePop no aplican fuera de NavHost nativo.)
 */
fun Modifier.settingsSubsectionNavigationChrome(): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    this
        .momentZoomNavigationSurface(isDark)
        .profileGridNavigationChrome()
        .momentsScrollEdgeChrome()
}
