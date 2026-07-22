package com.moments.android.views.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.services.cache.UserCacheService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Port de `VerifiedBadge` (VerifiedBadge.swift) — checkmark.seal.fill con gradiente. */
@Composable
fun VerifiedBadge(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = rememberVectorPainter(Icons.Filled.Verified),
        contentDescription = null,
        tint = Color(0xFF007AFF),
        modifier = modifier.size(size),
    )
}

/** Port de `VerifiedBadgeView` (StoryModels.swift) — carga `isVerified` del user. */
@Composable
fun VerifiedBadgeView(
    userId: String,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    var isVerified by remember(userId) { mutableStateOf(false) }
    var isLoading by remember(userId) { mutableStateOf(true) }

    LaunchedEffect(userId) {
        isLoading = true
        val user = suspendCancellableCoroutine { cont ->
            UserCacheService.getUser(userId) { cont.resume(it) }
        }
        isVerified = user?.isVerified == true
        isLoading = false
    }

    when {
        isLoading -> Unit
        isVerified -> VerifiedBadge(size = size, modifier = modifier)
    }
}

/** Port de `CurrentUserVerifiedBadge` (StoryModels.swift). */
@Composable
fun CurrentUserVerifiedBadge(
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    if (uid != null) {
        VerifiedBadgeView(userId = uid, size = size, modifier = modifier)
    }
}
