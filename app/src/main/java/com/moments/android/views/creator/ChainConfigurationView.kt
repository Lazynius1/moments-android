package com.moments.android.views.creator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
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

    companion object {
        fun from(raw: String?): ChainContinuationSetting =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: EVERYONE
    }
}

private enum class ChainConfigFlow {
    Main,
    ContinuationAudience,
}

/** Port Compose de `ChainConfigurationView.swift`. */
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
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    var flow by remember { mutableStateOf(ChainConfigFlow.Main) }
    var navigatingForward by remember { mutableStateOf(true) }
    var titleValidation by remember { mutableStateOf(false) }

    if (titleValidation) {
        AlertDialog(
            onDismissRequest = { titleValidation = false },
            title = { Text(stringResource(R.string.story_chains_title_required_title)) },
            text = { Text(stringResource(R.string.story_chains_title_required_message)) },
            confirmButton = {
                TextButton(onClick = { titleValidation = false }) {
                    Text(stringResource(R.string.story_chains_ok))
                }
            },
        )
    }

    fun navigate(to: ChainConfigFlow, forward: Boolean = true) {
        navigatingForward = forward
        flow = to
    }

    AnimatedContent(
        targetState = flow,
        transitionSpec = {
            val springSpec = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            val offsetSpringSpec = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            if (navigatingForward) {
                (slideInHorizontally(offsetSpringSpec) { it } + fadeIn(springSpec)) togetherWith
                    (slideOutHorizontally(offsetSpringSpec) { -it / 3 } + fadeOut(springSpec))
            } else {
                (slideInHorizontally(offsetSpringSpec) { -it } + fadeIn(springSpec)) togetherWith
                    (slideOutHorizontally(offsetSpringSpec) { it / 3 } + fadeOut(springSpec))
            }
        },
        label = "chainConfigFlow",
        modifier = modifier,
    ) { destination ->
        when (destination) {
            ChainConfigFlow.ContinuationAudience -> {
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
                    onBack = { navigate(ChainConfigFlow.Main, forward = false) },
                    onComplete = { navigate(ChainConfigFlow.Main, forward = false) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ChainConfigFlow.Main -> {
                ChainConfigurationMainContent(
                    allowOthersToContinue = allowOthersToContinue,
                    onAllowOthersToContinueChange = onAllowOthersToContinueChange,
                    continuationAudience = continuationAudience,
                    selectedListId = selectedListId,
                    selectedListName = selectedListName,
                    customSelectedUsers = customSelectedUsers,
                    chainTitleSummary = chainTitleSummary,
                    isContinuing = isContinuing,
                    content = content,
                    onOpenAudience = { navigate(ChainConfigFlow.ContinuationAudience) },
                    onShare = {
                        if (!isContinuing && chainTitleSummary.isNullOrBlank()) {
                            titleValidation = true
                        } else {
                            onDismiss()
                            onConfirm?.invoke()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ChainConfigurationMainContent(
    allowOthersToContinue: Boolean,
    onAllowOthersToContinueChange: (Boolean) -> Unit,
    continuationAudience: ChainContinuationSetting,
    selectedListId: String?,
    selectedListName: String?,
    customSelectedUsers: List<String>,
    chainTitleSummary: String?,
    isContinuing: Boolean,
    content: Color,
    onOpenAudience: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = Color(0xFF007AFF),
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.story_chains_configuration_title),
                color = content,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    if (isContinuing) R.string.story_chains_inherited_settings_info
                    else R.string.story_chains_visibility_info,
                ),
                color = content.copy(alpha = 0.65f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        chainTitleSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.story_chains_chain_title),
                    color = content.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    title,
                    color = content,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!isContinuing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.story_chains_allow_others_toggle),
                            color = content,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = allowOthersToContinue,
                            onCheckedChange = onAllowOthersToContinueChange,
                        )
                    }
                    Text(
                        stringResource(R.string.story_chains_allow_others_description),
                        color = content.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                }
            }

            if (allowOthersToContinue) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.story_chains_continuation_audience),
                        color = content,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    val displayAudience =
                        if (continuationAudience == ChainContinuationSetting.CUSTOM && selectedListId != null) {
                            ContentAudience.CUSTOM_LIST
                        } else {
                            continuationAudience.contentAudience
                        }
                    val audienceLabel = when {
                        continuationAudience == ChainContinuationSetting.CUSTOM &&
                            !selectedListName.isNullOrBlank() -> selectedListName
                        continuationAudience == ChainContinuationSetting.CUSTOM -> {
                            val count = customSelectedUsers.size
                            if (count == 1) {
                                stringResource(R.string.story_editor_custom_audience_single, count)
                            } else {
                                stringResource(R.string.story_editor_custom_audience_multiple, count)
                            }
                        }
                        continuationAudience == ChainContinuationSetting.EVERYONE ->
                            stringResource(R.string.audience_type_everyone)
                        continuationAudience == ChainContinuationSetting.MUTUALS ->
                            stringResource(R.string.audience_type_mutuals)
                        continuationAudience == ChainContinuationSetting.BEST_FRIENDS ->
                            stringResource(R.string.audience_type_best_friends)
                        continuationAudience == ChainContinuationSetting.CUSTOM_LIST ->
                            stringResource(R.string.audience_type_custom_list)
                        else -> continuationAudience.title
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isContinuing, onClick = onOpenAudience)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            AudienceIconView(
                                audience = displayAudience,
                                size = AudienceIconMetrics.creatorRow,
                                tintColor = if (isContinuing) content.copy(alpha = 0.55f) else null,
                            )
                        }
                        Text(
                            audienceLabel,
                            color = if (isContinuing) content.copy(alpha = 0.55f) else content,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                        )
                        if (!isContinuing) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = content,
                                modifier = Modifier
                                    .size(28.dp)
                                    .momentsChromeGlass(CircleShape, interactive = true)
                                    .padding(5.dp),
                            )
                        }
                    }
                }
            }

            if (isContinuing) {
                Text(
                    stringResource(R.string.story_chains_collaborator_notice),
                    color = content.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f)),
                    ),
                    shape = RoundedCornerShape(25.dp),
                )
                .clickable(onClick = onShare)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.story_chains_share_chain),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
