package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CustomAudienceList
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.creator.audienceselector.listIconVector
import com.moments.android.views.feed.rememberAdaptiveColors

/** Port de `UserRelationshipChip`: chip cápsula con icono opcional. */
@Composable
fun UserRelationshipChip(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val fg = if (colors.isDark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.68f)
    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(11.dp))
        Text(title, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Port de `UserRelationshipManagementSheet`: hoja de gestión de relación (mejor amigo, silenciar,
 * listas personalizadas, dejar de seguir).
 *
 * `confirmationDialog` iOS → `AlertDialog`. `userId` se acepta por paridad de firma (iOS no lo usa
 * en el cuerpo). Presentación del host: `MomentsModalSheet(largeOnly = false)` ≡ detents medium/large.
 */
@Composable
fun UserRelationshipManagementSheet(
    username: String,
    profileImagePath: String?,
    @Suppress("UNUSED_PARAMETER") userId: String,
    isBestFriend: Boolean,
    isMuted: Boolean,
    isMutual: Boolean,
    customListCount: Int,
    customLists: List<CustomAudienceList>,
    isUpdatingBestFriend: Boolean,
    isUpdatingMute: Boolean,
    isUpdatingLists: Boolean,
    onToggleBestFriend: () -> Unit,
    onToggleMute: () -> Unit,
    onRemoveFromList: (CustomAudienceList) -> Unit,
    onUnfollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black
    var showingLists by remember { mutableStateOf(false) }
    var listPendingRemoval by remember { mutableStateOf<CustomAudienceList?>(null) }

    val summaryItems = buildList {
        if (isMutual) add(stringResource(R.string.user_profile_relationship_mutual))
        if (customListCount > 0) add(stringResource(R.string.user_profile_relationship_in_lists))
        if (isMuted) add(stringResource(R.string.user_profile_relationship_muted))
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            Modifier.padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RelationshipAvatar(profileImagePath, colors.isDark)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.user_profile_relationship_sheet_title, username),
                    color = content,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.user_profile_relationship_sheet_subtitle),
                    color = if (colors.isDark) Color.White.copy(0.62f) else Color.Black.copy(0.56f),
                    fontSize = 14.sp,
                )
            }
            if (summaryItems.isNotEmpty()) {
                Text(
                    summaryItems.joinToString(" · "),
                    color = if (colors.isDark) Color.White.copy(0.52f) else Color.Black.copy(0.46f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        AnimatedContent(
            targetState = showingLists,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "relationshipContent",
        ) { lists ->
            if (lists) {
                RelationshipListsContent(
                    customLists = customLists,
                    isUpdatingLists = isUpdatingLists,
                    onBack = { showingLists = false },
                    onRequestRemove = { listPendingRemoval = it },
                )
            } else {
                RelationshipMainContent(
                    isBestFriend = isBestFriend,
                    isMuted = isMuted,
                    customListCount = customListCount,
                    isUpdatingBestFriend = isUpdatingBestFriend,
                    isUpdatingMute = isUpdatingMute,
                    onToggleBestFriend = onToggleBestFriend,
                    onToggleMute = onToggleMute,
                    onOpenLists = { showingLists = true },
                    onUnfollow = onUnfollow,
                )
            }
        }
    }

    listPendingRemoval?.let { list ->
        AlertDialog(
            onDismissRequest = { listPendingRemoval = null },
            title = { Text(stringResource(R.string.user_profile_relationship_lists_remove_title, username, list.name)) },
            text = { Text(stringResource(R.string.user_profile_relationship_lists_remove_message)) },
            confirmButton = {
                TextButton(onClick = { onRemoveFromList(list); listPendingRemoval = null }) {
                    Text(stringResource(R.string.user_profile_relationship_lists_remove_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { listPendingRemoval = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun RelationshipMainContent(
    isBestFriend: Boolean,
    isMuted: Boolean,
    customListCount: Int,
    isUpdatingBestFriend: Boolean,
    isUpdatingMute: Boolean,
    onToggleBestFriend: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenLists: () -> Unit,
    onUnfollow: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black
    val secondary = if (colors.isDark) Color.White.copy(0.52f) else Color.Black.copy(0.48f)

    Column(Modifier.fillMaxWidth()) {
        RelationshipActionRow(
            title = stringResource(
                if (isBestFriend) R.string.user_profile_relationship_best_friends
                else R.string.user_profile_relationship_best_friends_add,
            ),
            subtitle = stringResource(
                if (isBestFriend) R.string.user_profile_relationship_best_friends_remove_subtitle
                else R.string.user_profile_relationship_best_friends_add_subtitle,
            ),
            isLoading = isUpdatingBestFriend,
            onClick = onToggleBestFriend,
        ) {
            AudienceIconView(
                audience = ContentAudience.BEST_FRIENDS,
                size = AudienceIconMetrics.row,
                tintColor = if (isBestFriend) Color(0xFF34C759) else content,
                modifier = Modifier.width(24.dp),
            )
        }

        RelationshipActionRow(
            title = stringResource(
                if (isMuted) R.string.user_profile_relationship_mute_disable
                else R.string.user_profile_relationship_mute_enable,
            ),
            subtitle = stringResource(
                if (isMuted) R.string.user_profile_relationship_mute_disabled_subtitle
                else R.string.user_profile_relationship_mute_enabled_subtitle,
            ),
            isLoading = isUpdatingMute,
            onClick = onToggleMute,
        ) {
            Icon(
                if (isMuted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                null,
                tint = content,
                modifier = Modifier.width(24.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenLists).padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.user_profile_relationship_lists_title),
                    color = content,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (customListCount > 0) {
                        stringResource(R.string.user_profile_relationship_lists_count, customListCount)
                    } else {
                        stringResource(R.string.user_profile_relationship_lists_empty)
                    },
                    color = secondary,
                    fontSize = 12.sp,
                )
            }
            AudienceIconView(
                audience = ContentAudience.CUSTOM_LIST,
                size = AudienceIconMetrics.row,
                tintColor = content,
                modifier = Modifier.width(24.dp),
            )
            if (customListCount > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondary, modifier = Modifier.size(12.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().clickable(onClick = onUnfollow).padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.user_profile_relationship_unfollow),
                color = Color.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.PersonRemove, null, tint = Color.Red, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun RelationshipListsContent(
    customLists: List<CustomAudienceList>,
    isUpdatingLists: Boolean,
    onBack: () -> Unit,
    onRequestRemove: (CustomAudienceList) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black
    val secondary = if (colors.isDark) Color.White.copy(0.52f) else Color.Black.copy(0.48f)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.user_profile_relationship_lists_title),
                    color = content,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.user_profile_relationship_lists_manage), color = secondary, fontSize = 12.sp)
            }
        }

        if (customLists.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AudienceIconView(audience = ContentAudience.CUSTOM_LIST, size = 34.dp, tintColor = secondary)
                Text(
                    stringResource(R.string.user_profile_relationship_lists_empty),
                    color = secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                customLists.forEach { list ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUpdatingLists) { onRequestRemove(list) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(list.name, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.audience_people_count, list.members.size),
                                color = secondary,
                                fontSize = 12.sp,
                            )
                        }
                        if (isUpdatingLists) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                listIconVector(list.icon),
                                contentDescription = null,
                                tint = content,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipActionRow(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black
    val secondary = if (colors.isDark) Color.White.copy(0.52f) else Color.Black.copy(0.48f)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isLoading, onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = secondary, fontSize = 12.sp, maxLines = 2)
        }
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else trailing()
    }
}

@Composable
private fun RelationshipAvatar(profileImagePath: String?, isDark: Boolean) {
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!profileImagePath.isNullOrBlank()) {
            AsyncImage(
                model = profileImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                null,
                tint = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.58f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
