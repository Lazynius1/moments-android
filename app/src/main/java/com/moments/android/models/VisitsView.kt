package com.moments.android.models

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.services.content.ProfileVisitsService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.components.shimmer
import com.moments.android.views.profile.core.EmptyUserListViewModel
import com.moments.android.views.profile.core.SocialConnectionAvatarTapRouting
import com.moments.android.views.profile.core.SocialConnectionRowMetrics
import com.moments.android.views.profile.core.SocialConnectionsNoResultsView
import com.moments.android.views.profile.core.SocialConnectionsSortMode
import com.moments.android.views.profile.core.SocialConnectionsSorting
import com.moments.android.views.profile.core.UserListViewModel
import com.moments.android.views.story.StoryRingAvatarView
import java.util.Calendar
import java.util.Date
import kotlin.math.max

/** Port de `VisitorAnalysis`. */
data class VisitorAnalysis(
    val userId: String,
    val username: String,
    val profileImagePath: String?,
    val totalVisits: Int,
    val visitsLast24h: Int,
    val visitsLastWeek: Int,
    val frequencyType: VisitorFrequencyType,
    val lastVisit: Date,
    val firstVisit: Date,
) {
    val daysSinceFirstVisit: Int
        get() = ((Date().time - firstVisit.time) / 86_400_000L).toInt()
}

/** Port de `VisitsViewModel.analyzeStalkers`. */
object VisitorAnalysisBuilder {
    fun analyze(groupedVisits: List<GroupedVisit>): List<VisitorAnalysis> {
        val now = Date()
        val oneDayAgo = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -1) }.time
        val oneWeekAgo = Calendar.getInstance().apply { time = now; add(Calendar.DAY_OF_YEAR, -7) }.time

        return groupedVisits.mapNotNull { grouped ->
            val visits = grouped.visits
            if (visits.size < 3) return@mapNotNull null

            val sorted = visits.sortedBy { it.timestamp }
            val visitsLast24h = visits.count { !it.timestamp.before(oneDayAgo) }
            val frequencyType = VisitorFrequencyType.forVisitsLast24h(visitsLast24h)
            if (frequencyType == VisitorFrequencyType.NORMAL) return@mapNotNull null

            VisitorAnalysis(
                userId = grouped.user.id,
                username = grouped.user.username,
                profileImagePath = grouped.user.profileImagePath,
                totalVisits = visits.size,
                visitsLast24h = visitsLast24h,
                visitsLastWeek = visits.count { !it.timestamp.before(oneWeekAgo) },
                frequencyType = frequencyType,
                lastVisit = sorted.lastOrNull()?.timestamp ?: now,
                firstVisit = sorted.firstOrNull()?.timestamp ?: now,
            )
        }.sortedWith(
            compareByDescending<VisitorAnalysis> { it.frequencyType.rank }
                .thenByDescending { it.visitsLast24h },
        )
    }
}

/**
 * Port de `VisitsViewModel` (VisitsView.swift): carga visitas + alerta de stalker.
 */
class VisitsViewModel {
    var groupedVisits by mutableStateOf<List<GroupedVisit>>(emptyList())
        private set
    var stalkerAnalysis by mutableStateOf<List<VisitorAnalysis>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var showStalkerAlert by mutableStateOf(false)
    var detectedStalker by mutableStateOf<VisitorAnalysis?>(null)
        private set

    suspend fun fetchVisits() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            isLoading = false
            return
        }
        isLoading = true
        try {
            val grouped = runCatching { ProfileVisitsService.fetchGroupedVisits(userId) }.getOrDefault(emptyList())
            groupedVisits = grouped
            val analyses = VisitorAnalysisBuilder.analyze(grouped)
            stalkerAnalysis = analyses
            val superStalker = analyses.firstOrNull { it.frequencyType == VisitorFrequencyType.SUPER_STALKER }
            if (superStalker != null) {
                detectedStalker = superStalker
                showStalkerAlert = true
            }
        } finally {
            isLoading = false
        }
    }
}

/** Legacy sheet wrapper (deprecated en iOS) — `VisitsView`. */
@Composable
fun VisitsView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUserTap: (String) -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.7f) else Color.Black.copy(0.7f)
    val viewModel = remember { VisitsViewModel() }
    val listViewModel = remember { EmptyUserListViewModel() }

    LaunchedEffect(Unit) { viewModel.fetchVisits() }

    Box(modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.visits_title),
                    color = primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (viewModel.groupedVisits.size == 1) {
                        stringResource(R.string.visits_visitor_count_single, 1)
                    } else {
                        stringResource(R.string.visits_visitor_count_multiple, viewModel.groupedVisits.size)
                    },
                    color = secondary,
                    fontSize = 13.sp,
                )
            }

            VisitsTabContent(
                groupedVisits = viewModel.groupedVisits,
                isLoading = viewModel.isLoading,
                listViewModel = listViewModel,
                searchText = "",
                onUserTap = onUserTap,
                onAvatarTap = { userId, hasStory ->
                    SocialConnectionAvatarTapRouting.route(
                        userId = userId,
                        hasStory = hasStory,
                        openProfile = onUserTap,
                        openStories = { uid ->
                            NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(uid))
                        },
                    )
                },
                usesOwnScroll = true,
                modifier = Modifier.weight(1f),
            )
        }

        if (viewModel.showStalkerAlert) {
            viewModel.detectedStalker?.let { stalker ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.8f))
                        .clickable { viewModel.showStalkerAlert = false },
                    contentAlignment = Alignment.Center,
                ) {
                    StalkerAlertView(
                        stalker = stalker,
                        onDismiss = { viewModel.showStalkerAlert = false },
                        modifier = Modifier.clickable(enabled = false) {},
                    )
                }
            }
        }
    }
}

/** Port de `VisitsTabContent` embebido en SocialConnections. */
@Composable
fun VisitsTabContent(
    groupedVisits: List<GroupedVisit>,
    isLoading: Boolean,
    listViewModel: UserListViewModel,
    searchText: String,
    sortMode: SocialConnectionsSortMode = SocialConnectionsSortMode.DEFAULT,
    onUserTap: (String) -> Unit,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    usesOwnScroll: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(groupedVisits, searchText, sortMode) {
        val base = if (searchText.isBlank()) {
            groupedVisits
        } else {
            groupedVisits.filter {
                it.user.username.contains(searchText, ignoreCase = true) ||
                    (it.user.bio?.contains(searchText, ignoreCase = true) == true)
            }
        }
        SocialConnectionsSorting.sortVisits(base, sortMode)
    }

    when {
        isLoading -> VisitsTabSkeletonView(modifier.fillMaxWidth().height(400.dp))
        usesOwnScroll && groupedVisits.isEmpty() -> {
            Box(modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                ModernEmptyVisitsView()
            }
        }
        usesOwnScroll && filtered.isEmpty() -> {
            SocialConnectionsNoResultsView(modifier.fillMaxWidth().height(400.dp))
        }
        usesOwnScroll -> {
            LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
                items(filtered, key = { it.id }) { grouped ->
                    GroupedVisitRow(
                        grouped = grouped,
                        listViewModel = listViewModel,
                        onUserTap = { onUserTap(grouped.user.id) },
                        onAvatarTap = onAvatarTap,
                    )
                }
            }
        }
        groupedVisits.isEmpty() -> {
            Box(modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                ModernEmptyVisitsView()
            }
        }
        filtered.isEmpty() -> {
            SocialConnectionsNoResultsView(modifier.fillMaxWidth().height(400.dp))
        }
        else -> {
            Column(modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                filtered.forEach { grouped ->
                    GroupedVisitRow(
                        grouped = grouped,
                        listViewModel = listViewModel,
                        onUserTap = { onUserTap(grouped.user.id) },
                        onAvatarTap = onAvatarTap,
                    )
                }
            }
        }
    }
}

/** Port de `StalkerCard` (tarjeta vertical 80pt). */
@Composable
fun StalkerCard(
    analysis: VisitorAnalysis,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val freq = analysis.frequencyType

    Column(
        modifier
            .width(80.dp)
            .padding(vertical = 12.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = freq.color.copy(0.3f), spotColor = freq.color.copy(0.3f))
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
            .border(1.dp, freq.color.copy(0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(0.3f))
                    .border(3.dp, freq.color, CircleShape),
            ) {
                if (!analysis.profileImagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = analysis.profileImagePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Filled.Person, null, tint = Color.Gray, modifier = Modifier.align(Alignment.Center).size(30.dp))
                }
            }
            Text(
                freq.badge,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .background(freq.color, CircleShape),
                textAlign = TextAlign.Center,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    analysis.username,
                    color = primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VerifiedBadgeView(userId = analysis.userId, size = 8.dp)
            }
            Text(
                stringResource(R.string.visits_count, analysis.visitsLast24h),
                color = freq.color,
                fontSize = 10.sp,
            )
        }
    }
}

/** Port de `StalkerAlertView`. */
@Composable
fun StalkerAlertView(
    stalker: VisitorAnalysis,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.Gray.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.7f)
    Column(
        modifier
            .padding(20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) Color.White.copy(0.1f) else Color.White.copy(0.92f))
            .border(1.dp, Color.Red.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.visits_stalker_alert_title),
            color = primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f))
                .border(3.dp, Color.Red.copy(0.6f), CircleShape),
        ) {
            if (!stalker.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = stalker.profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stalker.username, color = primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                VerifiedBadgeView(userId = stalker.userId, size = 16.dp)
            }
            Text(
                stringResource(R.string.visits_stalker_alert_message, stalker.visitsLast24h),
                color = secondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            stalker.frequencyType.messageRes?.let { res ->
                Text(
                    stringResource(res),
                    color = stalker.frequencyType.color.copy(alpha = 1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(stalker.frequencyType.color.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            stringResource(R.string.common_understood),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF00A896))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 30.dp, vertical = 12.dp),
        )
    }
}

/** Port de `GroupedVisitRow`. */
@Composable
fun GroupedVisitRow(
    grouped: GroupedVisit,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    listViewModel: UserListViewModel? = null,
    onUserTap: (() -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
    val open = onUserTap ?: onTap
    var isPressed by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = SocialConnectionRowMetrics.horizontalPadding,
                vertical = SocialConnectionRowMetrics.verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SocialConnectionRowMetrics.contentSpacing),
    ) {
        StoryRingAvatarView(
            userId = grouped.user.id,
            size = SocialConnectionRowMetrics.avatarSize,
            lineWidth = 2.2.dp,
            showBaseStroke = true,
            baseStrokeColor = if (dark) Color.White.copy(0.18f) else Color.Black.copy(0.14f),
            baseStrokeWidth = 0.9.dp,
            onTap = { hasStory ->
                if (onAvatarTap != null) {
                    onAvatarTap(grouped.user.id, hasStory)
                } else if (!hasStory) {
                    open()
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .background(if (isPressed) primary.copy(0.06f) else Color.Transparent)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { open() },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(SocialConnectionRowMetrics.textLineSpacing),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    grouped.user.username,
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (grouped.user.isVerified) {
                    VerifiedBadge(13.dp)
                }
                if (grouped.isRecent) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00A896)))
                }
            }
            Text(visitRowSubtitle(grouped), color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        listViewModel?.let { vm ->
            VisitsRelationshipButton(user = grouped.user, viewModel = vm)
        }
    }
}

/** Port de `VisitsRelationshipButton`. */
@Composable
private fun VisitsRelationshipButton(
    user: AppUser,
    viewModel: UserListViewModel,
) {
    var followState by remember(user.id) { mutableStateOf(viewModel.relationshipState(user.id)) }
    var isFollowLoading by remember { mutableStateOf(false) }
    var showingUnfollowConfirmation by remember { mutableStateOf(false) }

    fun refreshFollowState() {
        followState = viewModel.relationshipState(user.id)
    }

    LaunchedEffect(user.id) {
        refreshFollowState()
        viewModel.prefetchRelationshipState(user.id)
    }
    DisposableEffect(user.id) {
        val listener: (String, FollowButtonState) -> Unit = { changed, _ ->
            if (changed == user.id) refreshFollowState()
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    if (followState == FollowButtonState.OWN_PROFILE) return

    ModernFollowButton(
        state = followState,
        isLoading = isFollowLoading,
        targetUserId = user.id,
        onClick = {
            if (isFollowLoading) return@ModernFollowButton
            when (followState) {
                FollowButtonState.FOLLOWING -> showingUnfollowConfirmation = true
                FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> {
                    isFollowLoading = true
                    viewModel.followUser(user.id)
                    val next = if (followState == FollowButtonState.CAN_REQUEST_FOLLOW) {
                        FollowButtonState.REQUEST_PENDING_CANCELLABLE
                    } else {
                        FollowButtonState.FOLLOWING
                    }
                    FollowStateStore.setState(next, user.id)
                    followState = next
                    isFollowLoading = false
                }
                FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                    viewModel.cancelFollowRequest(user.id)
                    FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, user.id)
                    refreshFollowState()
                }
                else -> Unit
            }
        },
    )

    if (showingUnfollowConfirmation) {
        AlertDialog(
            onDismissRequest = { showingUnfollowConfirmation = false },
            title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
            text = { Text(stringResource(R.string.user_profile_unfollow_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unfollowUser(user.id)
                        FollowStateStore.setState(FollowButtonState.CAN_FOLLOW, user.id)
                        refreshFollowState()
                        showingUnfollowConfirmation = false
                    },
                ) {
                    Text(stringResource(R.string.user_profile_unfollow_confirm_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingUnfollowConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Port de `VisitsVisitorSkeletonRow`. */
@Composable
fun VisitsVisitorSkeletonRow(modifier: Modifier = Modifier) {
    val surface = if (isSystemInDarkTheme()) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = SocialConnectionRowMetrics.horizontalPadding,
                vertical = SocialConnectionRowMetrics.verticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SocialConnectionRowMetrics.contentSpacing),
    ) {
        Box(Modifier.size(SocialConnectionRowMetrics.avatarSize).background(surface, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Box(Modifier.width(132.dp).height(12.dp).background(surface, RoundedCornerShape(4.dp)))
            Box(Modifier.width(88.dp).height(10.dp).background(surface, RoundedCornerShape(4.dp)))
        }
        Box(Modifier.width(108.dp).height(34.dp).background(surface, RoundedCornerShape(50)))
    }
}

/** Port de `VisitsTabSkeletonView`. */
@Composable
fun VisitsTabSkeletonView(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .shimmer(true)
            .padding(bottom = 40.dp),
    ) {
        repeat(8) { VisitsVisitorSkeletonRow() }
    }
}

/** Port de `VisitModernLoadingView`. */
@Composable
fun VisitModernLoadingView(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "visits-load")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(60.dp)
                    .border(4.dp, Color(0xFF00A896).copy(0.3f), CircleShape),
            )
            Box(
                Modifier
                    .size(60.dp)
                    .rotate(rotation)
                    .border(
                        width = 4.dp,
                        brush = Brush.linearGradient(
                            if (dark) listOf(Color(0xFF00A896), Color.White)
                            else listOf(Color(0xFF00A896), Color.Black),
                        ),
                        shape = CircleShape,
                    ),
            )
        }
        Text(
            stringResource(R.string.visits_loading),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (dark) Color.White.copy(0.8f) else Color.Black.copy(0.7f),
        )
    }
}

/** Port de `ModernEmptyVisitsView`. */
@Composable
fun ModernEmptyVisitsView(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            Modifier
                .size(100.dp)
                .background(if (dark) Color.White.copy(0.08f) else Color.Black.copy(0.05f), CircleShape)
                .border(
                    2.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF00A896).copy(0.4f),
                            if (dark) Color.White.copy(0.2f) else Color.Black.copy(0.1f),
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = Color(0xFF00A896),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.visits_empty_title),
                color = primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.visits_empty_description),
                color = if (dark) Color.Gray.copy(0.8f) else Color.Gray.copy(0.6f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Port de `GroupedVisit.rowSubtitle`. */
@Composable
private fun visitRowSubtitle(grouped: GroupedVisit): String {
    val lastVisit = grouped.lastVisit ?: return ""
    val intervalSec = (Date().time - lastVisit.time) / 1000
    val relative = when {
        intervalSec < 60 -> stringResource(R.string.visits_time_just_now)
        intervalSec < 3_600 -> stringResource(R.string.visits_time_minutes_ago, max(1, (intervalSec / 60).toInt()))
        intervalSec < 86_400 -> stringResource(R.string.visits_time_hours_ago, max(1, (intervalSec / 3_600).toInt()))
        intervalSec < 604_800 -> stringResource(R.string.visits_time_days_ago, max(1, (intervalSec / 86_400).toInt()))
        else -> MomentsFormat.smartDate(lastVisit, MomentsFormat.DateContext.NUMERIC_DAY_MONTH)
    }
    return if (grouped.visitCount > 1) "$relative · ${grouped.visitCount}×" else relative
}
