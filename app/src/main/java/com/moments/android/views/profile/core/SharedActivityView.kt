package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Port de `SharedActivityView.swift`. Las rutas de perfil/chat se conectan desde su contenedor. */
@Composable
fun SharedActivityView(
    currentUser: AppUser?,
    otherUser: AppUser,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenChat: (AppUser) -> Unit,
    onOpenMoment: (Moment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var detailCategory by remember { mutableStateOf<SharedActivityCategory?>(null) }
    var relationshipState by remember(otherUser.id) { mutableStateOf(viewModel.relationshipState(otherUser.id)) }
    var showUnfollowConfirmation by remember { mutableStateOf(false) }
    var followedYouAt by remember { mutableStateOf<Date?>(null) }
    var followingSince by remember { mutableStateOf<Date?>(null) }

    DisposableEffect(otherUser.id) {
        val listener: (String, FollowButtonState) -> Unit = { userId, state -> if (userId == otherUser.id) relationshipState = state }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    fun loadRelationshipTimeline() {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val followed = firestore.collection("users").document(viewerId).collection("followers").document(otherUser.id).get().await()
                val following = firestore.collection("users").document(viewerId).collection("following").document(otherUser.id).get().await()
                followedYouAt = (followed.data?.get("timestamp") as? Timestamp)?.toDate()
                followingSince = (following.data?.get("timestamp") as? Timestamp)?.toDate()
            }
        }
    }

    LaunchedEffect(otherUser.id) {
        viewModel.prefetchRelationshipState(otherUser.id)
        relationshipState = viewModel.relationshipState(otherUser.id)
        loadRelationshipTimeline()
    }

    detailCategory?.let { category ->
        SharedActivityDetailView(
            category = category,
            currentUser = currentUser,
            otherUser = otherUser,
            onOpenMoment = onOpenMoment,
            onOpenProfile = onOpenProfile,
            onDismiss = { detailCategory = null },
            modifier = modifier,
        )
        return
    }

    val background = sharedCanvas()
    val primary = sharedPrimary()
    val secondary = sharedSecondary()
    val timeline = buildList {
        followedYouAt?.let { add(SharedActivityTimelineItem(R.string.shared_activity_timeline_followed_you, otherUser.username, monthYearString(it))) }
        followingSince?.let { add(SharedActivityTimelineItem(R.string.shared_activity_timeline_you_followed, otherUser.username, monthYearString(it))) }
    }

    Column(modifier.fillMaxSize().background(background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = primary, modifier = Modifier.size(28.dp).clickable(onClick = onDismiss))
            Text(stringResource(R.string.shared_activity_title), color = primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp).weight(1f))
            Icon(Icons.Filled.Refresh, stringResource(R.string.shared_activity_refresh), tint = primary, modifier = Modifier.size(24.dp).clickable { loadRelationshipTimeline() })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SharedActivityHero(currentUser, otherUser, primary, secondary)
            SharedActivityRelationButtons(
                state = relationshipState,
                primary = primary,
                onRelationshipAction = {
                    when (relationshipState) {
                        FollowButtonState.FOLLOWING -> showUnfollowConfirmation = true
                        FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> {
                            relationshipState = if (relationshipState == FollowButtonState.CAN_REQUEST_FOLLOW) FollowButtonState.REQUEST_PENDING_CANCELLABLE else FollowButtonState.FOLLOWING
                            viewModel.followUser(otherUser.id)
                        }
                        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                            relationshipState = FollowButtonState.CAN_REQUEST_FOLLOW
                            viewModel.cancelFollowRequest(otherUser.id)
                        }
                        FollowButtonState.OWN_PROFILE, FollowButtonState.BLOCKED, FollowButtonState.REQUEST_PENDING -> Unit
                    }
                },
                onMessage = { onOpenChat(otherUser) },
                onProfile = { onOpenProfile(otherUser.id) },
            )
            if (timeline.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    timeline.forEach { item -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Filled.CalendarToday, null, tint = primary, modifier = Modifier.size(22.dp)); Text(stringResource(item.textRes, item.username, item.monthYear), color = primary, fontSize = 15.sp, fontWeight = FontWeight.Medium) } }
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).background(primary.copy(alpha = .08f)))
            SharedActivityModules(primary, secondary) { detailCategory = it }
        }
    }

    if (showUnfollowConfirmation) AlertDialog(
        onDismissRequest = { showUnfollowConfirmation = false },
        title = { Text(stringResource(R.string.shared_activity_unfollow_title)) },
        text = { Text(stringResource(R.string.shared_activity_unfollow_message)) },
        dismissButton = { TextButton({ showUnfollowConfirmation = false }) { Text(stringResource(R.string.shared_activity_cancel)) } },
        confirmButton = { TextButton({ showUnfollowConfirmation = false; relationshipState = FollowButtonState.CAN_FOLLOW; viewModel.unfollowUser(otherUser.id); viewModel.prefetchRelationshipState(otherUser.id) }) { Text(stringResource(R.string.shared_activity_unfollow_action), color = Color.Red) } },
    )
}

@Composable
private fun SharedActivityHero(currentUser: AppUser?, otherUser: AppUser, primary: Color, secondary: Color) = Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(width = 218.dp, height = 125.dp)) {
        SharedActivityAvatar(currentUser?.id, stringResource(R.string.shared_activity_avatar_fallback_current), primary, Modifier.align(Alignment.CenterStart))
        SharedActivityAvatar(otherUser.id, otherUser.username.take(1).uppercase(), primary, Modifier.align(Alignment.CenterEnd))
    }
    Text(stringResource(R.string.shared_activity_you_and_user, otherUser.username), color = primary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 28.dp, top = 18.dp, end = 28.dp))
    Text(stringResource(R.string.shared_activity_subtitle, otherUser.username), color = secondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 28.dp, top = 8.dp, end = 28.dp))
}

@Composable
private fun SharedActivityAvatar(userId: String?, fallbackLabel: String, primary: Color, modifier: Modifier = Modifier) = Box(modifier.size(125.dp), contentAlignment = Alignment.Center) { if (userId != null) StoryRingAvatarView(userId, 118.dp, lineWidth = 3.dp, showBaseStroke = true, baseStrokeColor = primary.copy(alpha = .12f), baseStrokeWidth = 1.dp) else Box(Modifier.size(118.dp).clip(CircleShape).background(primary.copy(alpha = .08f)), contentAlignment = Alignment.Center) { Text(fallbackLabel, color = primary, fontSize = 36.sp, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun SharedActivityRelationButtons(state: FollowButtonState, primary: Color, onRelationshipAction: () -> Unit, onMessage: () -> Unit, onProfile: () -> Unit) = Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    SharedActivityActionButton(sharedRelationshipLabel(state), primary, state.isActionable, Modifier.weight(1f), onRelationshipAction)
    SharedActivityActionButton(R.string.shared_activity_send_message, primary, true, Modifier.weight(1f), onMessage)
    SharedActivityActionButton(R.string.shared_activity_view_profile, primary, true, Modifier.weight(1f), onProfile)
}

@Composable
private fun SharedActivityActionButton(title: Int, primary: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) = Text(stringResource(title), color = primary.copy(alpha = if (enabled) 1f else .45f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = modifier.clip(RoundedCornerShape(16.dp)).background(primary.copy(alpha = .07f)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun SharedActivityModules(primary: Color, secondary: Color, onOpen: (SharedActivityCategory) -> Unit) = Column(Modifier.fillMaxWidth()) {
    SharedActivityModuleRow(SharedActivityCategory.TAGS, R.string.shared_activity_tags, R.string.shared_activity_module_tags, Icons.Filled.Label, primary, secondary, onOpen)
    SharedActivityModuleRow(SharedActivityCategory.REACTIONS, R.string.shared_activity_reactions, R.string.shared_activity_module_reactions, Icons.Filled.ThumbUp, primary, secondary, onOpen)
    SharedActivityModuleRow(SharedActivityCategory.COMMENTS, R.string.shared_activity_comments, R.string.shared_activity_module_comments, Icons.Filled.ChatBubbleOutline, primary, secondary, onOpen)
}

@Composable
private fun SharedActivityModuleRow(category: SharedActivityCategory, title: Int, subtitle: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Color, secondary: Color, onOpen: (SharedActivityCategory) -> Unit) = Row(Modifier.fillMaxWidth().clickable { onOpen(category) }.padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(icon, null, tint = primary, modifier = Modifier.size(28.dp)); Column(Modifier.weight(1f)) { Text(stringResource(title), color = primary, fontSize = 17.sp, fontWeight = FontWeight.Medium); Text(stringResource(subtitle), color = secondary, fontSize = 14.sp) }; Text(stringResource(R.string.shared_activity_disclosure), color = secondary, fontSize = 20.sp) }

private data class SharedActivityTimelineItem(val textRes: Int, val username: String, val monthYear: String)
private fun monthYearString(date: Date): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
private fun sharedRelationshipLabel(state: FollowButtonState): Int = when (state) { FollowButtonState.OWN_PROFILE -> R.string.shared_activity_relationship_own; FollowButtonState.BLOCKED -> R.string.shared_activity_relationship_blocked; FollowButtonState.FOLLOWING -> R.string.shared_activity_relationship_following; FollowButtonState.CAN_FOLLOW -> R.string.shared_activity_relationship_follow; FollowButtonState.CAN_REQUEST_FOLLOW -> R.string.shared_activity_relationship_request_follow; FollowButtonState.REQUEST_PENDING -> R.string.shared_activity_relationship_request_sent; FollowButtonState.REQUEST_PENDING_CANCELLABLE -> R.string.shared_activity_relationship_cancel_request }
