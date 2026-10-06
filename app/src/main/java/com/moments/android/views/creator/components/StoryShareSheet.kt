package com.moments.android.views.creator.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Photo
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.settings.SettingsProfileColors

@Composable
fun StoryShareSheet(
    preview: Bitmap?,
    audienceTitle: String,
    isChain: Boolean,
    chainTitle: String,
    expirationHours: Int,
    onExpirationChange: (Int) -> Unit,
    allowMessages: Boolean,
    onMessagesChange: (Boolean) -> Unit,
    allowReactions: Boolean,
    onReactionsChange: (Boolean) -> Unit,
    allowEphemeralPhotos: Boolean,
    onEphemeralPhotosChange: (Boolean) -> Unit,
    isPublishing: Boolean,
    hasOriginalAudio: Boolean,
    hasAudio: Boolean = hasOriginalAudio,
    canShareOriginalAudio: Boolean,
    allowOriginalAudioReuse: Boolean,
    onAudioReuseChange: (Boolean) -> Unit,
    onAudience: () -> Unit,
    onChainSettings: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var interactions by remember { mutableStateOf(false) }
    var expirationMenu by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (interactions) interactions = false else onDismiss() }, enabled = !isPublishing) {
                Icon(if (interactions) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                    stringResource(if (interactions) R.string.story_share_back else R.string.story_share_close))
            }
            Text(stringResource(if (interactions) R.string.story_share_interactions else R.string.story_share_title),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (interactions) {
                ShareInteractionToggle(stringResource(R.string.content_visibility_interactions_messages_title),
                    stringResource(R.string.content_visibility_interactions_messages_description), allowMessages, !isPublishing, onMessagesChange)
                ShareInteractionToggle(stringResource(R.string.content_visibility_interactions_reactions_title),
                    stringResource(R.string.content_visibility_interactions_reactions_description), allowReactions, !isPublishing, onReactionsChange)
                ShareInteractionToggle(stringResource(R.string.content_visibility_interactions_ephemeral_title),
                    stringResource(R.string.content_visibility_interactions_ephemeral_description), allowEphemeralPhotos, !isPublishing, onEphemeralPhotosChange)
                Text(stringResource(R.string.story_share_only_this_story), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    SharePreviewHeader(preview, if (isChain) chainTitle else stringResource(R.string.story_share_your_story),
                        if (isChain) 48 else expirationHours, hasAudio)
                    ShareOptionRow(Icons.Default.People, stringResource(R.string.story_share_audience), audienceTitle,
                        editable = !isChain, enabled = !isChain && !isPublishing, onClick = onAudience)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box {
                        ShareOptionRow(Icons.Default.Schedule, stringResource(R.string.story_share_expiration),
                            if (isChain) "48 h" else "$expirationHours h", editable = !isChain,
                            enabled = !isChain && !isPublishing, onClick = { expirationMenu = true })
                        DropdownMenu(expanded = expirationMenu, onDismissRequest = { expirationMenu = false }) {
                            for (hours in listOf(24, 48)) DropdownMenuItem(text = { Text("$hours h") },
                                onClick = { onExpirationChange(hours); expirationMenu = false })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (isChain) {
                        ShareOptionRow(Icons.Default.Link, stringResource(R.string.story_share_chain_settings),
                            enabled = !isPublishing, onClick = onChainSettings)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    ShareOptionRow(Icons.Default.Tune, stringResource(R.string.story_share_interactions),
                        stringResource(if (allowMessages || allowReactions || allowEphemeralPhotos) R.string.story_share_customize else R.string.story_share_disabled),
                        enabled = !isPublishing, onClick = { interactions = true })
                    if (hasOriginalAudio) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.story_audio_allow_reuse), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(if (canShareOriginalAudio) R.string.story_audio_allow_reuse_detail else R.string.story_audio_restricted_audience),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = canShareOriginalAudio && allowOriginalAudioReuse, onCheckedChange = onAudioReuseChange,
                                enabled = canShareOriginalAudio && !isPublishing,
                                colors = SwitchDefaults.colors(checkedTrackColor = SettingsProfileColors.toggleTint, checkedThumbColor = Color.White))
                        }
                    }
                    Text(stringResource(if (isChain) R.string.story_share_chain_audience else R.string.story_share_only_this_story),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
                }
            }
        }
        Button(onClick = onShare, enabled = !isPublishing,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
            if (isPublishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.story_editor_share))
        }
    }
}

@Composable
private fun ShareInteractionToggle(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(description) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SettingsProfileColors.toggleTint,
                    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                ))
        },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharePreviewHeader(preview: Bitmap?, title: String, expirationHours: Int, hasAudio: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(100.dp, 160.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) {
            if (preview != null && !preview.isRecycled) {
                Image(preview.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.story_share_review), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SharePreviewBadge(Icons.Default.Schedule, "$expirationHours h")
                if (hasAudio) SharePreviewBadge(Icons.Default.GraphicEq, stringResource(R.string.story_audio_title))
            }
        }
    }
}

@Composable
private fun SharePreviewBadge(icon: ImageVector, label: String) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShareOptionRow(icon: ImageVector, label: String, value: String? = null, editable: Boolean = true,
    enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).alpha(if (enabled) 1f else 0.4f)
        .clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, Modifier.widthIn(max = 132.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        if (editable) Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
