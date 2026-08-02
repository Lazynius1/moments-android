package com.moments.android.views.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUser
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `BlockedUsersView.swift` + `BlockedUsersViewModel`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val secondary = primary.copy(alpha = 0.5f)
    val scope = rememberCoroutineScope()
    val firestoreService = remember { FirestoreService() }

    var hasFetched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var blockedUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val notAuthenticated = stringResource(R.string.blocked_users_not_authenticated)
    val fetchProfileError = stringResource(R.string.blocked_users_error_fetch_profile)
    val fetchUsersError = stringResource(R.string.blocked_users_error_fetch_users)
    val unblockError = stringResource(R.string.blocked_users_error_unblock)

    fun showError(message: String) {
        errorMessage = message
        showError = true
    }

    fun fetchBlockedUsers(fromRefresh: Boolean = false) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            showError(notAuthenticated)
            isLoading = false
            isRefreshing = false
            return
        }
        if (fromRefresh) isRefreshing = true else isLoading = true
        scope.launch {
            try {
                val user = firestoreService.fetchUser(uid)
                val blockedIds = user.blockedUsers
                blockedUsers = if (blockedIds.isEmpty()) {
                    emptyList()
                } else {
                    try {
                        firestoreService.fetchUsers(blockedIds)
                    } catch (e: Exception) {
                        showError("$fetchUsersError: ${e.localizedMessage}")
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                showError("$fetchProfileError: ${e.localizedMessage}")
            } finally {
                isLoading = false
                if (fromRefresh) {
                    delay(400)
                    isRefreshing = false
                }
            }
        }
    }

    fun unblockUser(userId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            showError(notAuthenticated)
            return
        }
        isLoading = true
        scope.launch {
            try {
                firestoreService.unblockUser(currentUserId = currentUid, targetUserId = userId)
                blockedUsers = blockedUsers.filterNot { it.id == userId }
            } catch (e: Exception) {
                showError("$unblockError: ${e.localizedMessage}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasFetched) {
            hasFetched = true
            fetchBlockedUsers()
        }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.blocked_users_title),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
            when {
                isLoading && blockedUsers.isEmpty() && !isRefreshing -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MomentsCircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(
                            stringResource(R.string.common_searching),
                            fontSize = 16.sp,
                            color = secondary,
                        )
                    }
                }
                blockedUsers.isEmpty() && !isLoading -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        ) {
                        Icon(
                            Icons.Filled.FrontHand,
                            contentDescription = null,
                            tint = secondary,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            stringResource(R.string.blocked_users_empty),
                            fontSize = 16.sp,
                            color = secondary,
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { fetchBlockedUsers(fromRefresh = true) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item {
                                Text(
                                    stringResource(
                                        R.string.settings_sections_blocked_accounts_subtitle,
                                        blockedUsers.size,
                                    ),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = secondary,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(blockedUsers, key = { it.id }) { user ->
                                BlockedUserRow(
                                    username = user.username,
                                    primary = primary,
                                    onUnblock = { unblockUser(user.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(R.string.blocked_users_error_title)) },
            text = {
                Text(errorMessage ?: stringResource(R.string.blocked_users_unknown_error))
            },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(R.string.blocked_users_ok))
                }
            },
        )
    }
}

@Composable
private fun BlockedUserRow(
    username: String,
    primary: Color,
    onUnblock: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            username,
            fontSize = 15.sp,
            color = primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.blocked_users_unblock),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = primary,
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
                .clickable(interactionSource = interaction, indication = null, onClick = onUnblock)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
