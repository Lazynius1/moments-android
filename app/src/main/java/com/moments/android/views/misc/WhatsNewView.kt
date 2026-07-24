package com.moments.android.views.misc

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.delay

private data class WhatsNewFeature(@StringRes val title: Int, @StringRes val description: Int, val icon: ImageVector)

/** Port de `Views/Misc/WhatsNewView.swift`. */
@Composable
fun WhatsNewView(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var appeared by remember { mutableStateOf(false) }
    val features = remember {
        listOf(
            WhatsNewFeature(R.string.whats_new_voice_title, R.string.whats_new_voice_description, Icons.Default.Mic),
            WhatsNewFeature(R.string.whats_new_reliable_title, R.string.whats_new_reliable_description, Icons.Default.CheckCircle),
            WhatsNewFeature(R.string.whats_new_search_title, R.string.whats_new_search_description, Icons.Default.Search),
            WhatsNewFeature(R.string.whats_new_nova_title, R.string.whats_new_nova_description, Icons.Default.AutoAwesome),
            WhatsNewFeature(R.string.whats_new_encryption_title, R.string.whats_new_encryption_description, Icons.Default.Lock),
            WhatsNewFeature(R.string.whats_new_chat_settings_title, R.string.whats_new_chat_settings_description, Icons.Default.Tune),
            WhatsNewFeature(R.string.whats_new_mute_title, R.string.whats_new_mute_description, Icons.Default.NotificationsOff),
            WhatsNewFeature(R.string.whats_new_requests_title, R.string.whats_new_requests_description, Icons.Default.Mail),
            WhatsNewFeature(R.string.whats_new_vanish_replies_title, R.string.whats_new_vanish_replies_description, Icons.Default.Timer),
            WhatsNewFeature(R.string.whats_new_signin_title, R.string.whats_new_signin_description, Icons.Default.Person),
        )
    }
    LaunchedEffect(Unit) { appeared = true }
    ScreenshotProtectedView(isProtected = true) {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(appeared, enter = androidx.compose.animation.fadeIn(tween(300))) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.whats_new_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            features.forEachIndexed { index, feature -> WhatsNewFeatureRow(feature, index, appeared) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(stringResource(R.string.whats_new_button), modifier = Modifier.padding(vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun WhatsNewFeatureRow(feature: WhatsNewFeature, index: Int, appeared: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(appeared) { if (appeared) { delay(index * 40L); visible = true } }
    AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(tween(220))) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(feature.icon, null, modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).padding(10.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(feature.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(feature.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
