package com.moments.android.views.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience

/** Port de `ChainContinuationSetting` (ChainConfigurationView.swift). */
enum class ChainContinuationSetting(val raw: String, val contentAudience: ContentAudience) {
    EVERYONE("everyone", ContentAudience.EVERYONE),
    MUTUALS("mutuals", ContentAudience.MUTUALS),
    BEST_FRIENDS("bestFriends", ContentAudience.BEST_FRIENDS),
    CUSTOM("custom", ContentAudience.CUSTOM),
    CUSTOM_LIST("customList", ContentAudience.CUSTOM_LIST);

    val title: String get() = contentAudience.title
    val description: String get() = contentAudience.description
    val icon: String get() = contentAudience.assetName
}

/** Port Compose de `ChainConfigurationView`. */
@Composable
fun ChainConfigurationView(
    allowOthersToContinue: Boolean,
    onAllowOthersToContinueChange: (Boolean) -> Unit,
    continuationAudience: ChainContinuationSetting,
    onContinuationAudienceChange: (ChainContinuationSetting) -> Unit,
    selectedListId: String?,
    onSelectedListIdChange: (String?) -> Unit,
    selectedListName: String?,
    onSelectedListNameChange: (String?) -> Unit,
    customSelectedUsers: List<String>,
    onCustomSelectedUsersChange: (List<String>) -> Unit,
    chainTitleSummary: String?,
    isContinuing: Boolean,
    onConfirm: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    var selectingAudience by remember { mutableStateOf(false) }
    var titleValidation by remember { mutableStateOf(false) }

    if (titleValidation) {
        AlertDialog(
            onDismissRequest = { titleValidation = false },
            title = { Text("Title required") },
            text = { Text("Add a title before sharing this chain.") },
            confirmButton = { TextButton(onClick = { titleValidation = false }) { Text("OK") } },
        )
    }
    if (selectingAudience) {
        ChainContinuationSelectorView(
            selectedAudience = continuationAudience,
            onSelectedAudienceChange = onContinuationAudienceChange,
            selectedListId = selectedListId,
            onSelectedListIdChange = onSelectedListIdChange,
            selectedListName = selectedListName,
            onSelectedListNameChange = onSelectedListNameChange,
            customSelectedUsers = customSelectedUsers,
            onCustomSelectedUsersChange = onCustomSelectedUsersChange,
            embeddedInFlow = true,
            onBack = { selectingAudience = false },
            onComplete = { selectingAudience = false },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Link, null, tint = Color(0xFF007AFF), modifier = Modifier.padding(top = 20.dp).size(48.dp))
        Text("Chain configuration", color = content, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            if (isContinuing) "The original author defined these chain settings and they cannot be changed." else "Choose who may continue this chain.",
            color = content.copy(.65f), fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp),
        )
        chainTitleSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Chain title", color = content.copy(.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(title, color = content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            }
        }
        if (!isContinuing) {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow others to continue", color = content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Let selected people add to this chain.", color = content.copy(.6f), fontSize = 14.sp)
                }
                Switch(checked = allowOthersToContinue, onCheckedChange = onAllowOthersToContinueChange)
            }
        }
        if (allowOthersToContinue) {
            Text("Continuation audience", color = content, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            val display = if (continuationAudience == ChainContinuationSetting.CUSTOM && selectedListId != null) ContentAudience.CUSTOM_LIST else continuationAudience.contentAudience
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(content.copy(.06f)).clickable(enabled = !isContinuing) { selectingAudience = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AudienceIconView(display, AudienceIconMetrics.creatorRow, if (isContinuing) content.copy(.55f) else null)
                Text(
                    when {
                        continuationAudience == ChainContinuationSetting.CUSTOM && selectedListName != null -> selectedListName
                        continuationAudience == ChainContinuationSetting.CUSTOM -> "${customSelectedUsers.size} people"
                        else -> continuationAudience.title
                    },
                    color = if (isContinuing) content.copy(.55f) else content, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (!isContinuing) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = content, modifier = Modifier.size(18.dp).momentsChromeGlass(CircleShape, true))
            }
        }
        if (isContinuing) Text("As a collaborator, the chain rules set by its author apply.", color = content.copy(.6f), fontSize = 13.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 20.dp))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp).clip(RoundedCornerShape(25.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)))).clickable {
                if (!isContinuing && chainTitleSummary.isNullOrBlank()) titleValidation = true else { onDismiss(); onConfirm?.invoke() }
            }.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) { Icon(Icons.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text("Share chain", color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp)) }
    }
}
