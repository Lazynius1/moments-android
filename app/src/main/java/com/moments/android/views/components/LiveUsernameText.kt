package com.moments.android.views.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.moments.android.services.cache.UserCacheService

/**
 * Port de `LiveUsernameContent`.
 * iOS hardcodea el fallback final como `"Usuario"` (sin Localizable en este archivo).
 */
@Composable
fun LiveUsernameContent(
    userId: String,
    fallbackUsername: String,
    content: @Composable (String) -> Unit,
) {
    var liveUsername by remember(userId) { mutableStateOf("") }

    fun resolvedUsername(): String {
        val live = liveUsername.trim()
        val fallback = fallbackUsername.trim()
        if (live.isNotEmpty()) return live
        return if (fallback.isEmpty()) "Usuario" else fallback
    }

    fun refreshUsername() {
        val trimmedUserId = userId.trim()
        if (trimmedUserId.isEmpty()) {
            liveUsername = ""
            return
        }
        UserCacheService.refreshUser(trimmedUserId) { user ->
            val fetched = user?.username?.trim().orEmpty()
            if (userId.trim() == trimmedUserId) {
                liveUsername = fetched
            }
        }
    }

    // iOS: onAppear + onChange(userId)
    LaunchedEffect(userId) {
        refreshUsername()
    }

    // iOS: onChange(fallbackUsername) solo si live está vacío
    LaunchedEffect(fallbackUsername) {
        if (liveUsername.trim().isEmpty()) {
            refreshUsername()
        }
    }

    content(resolvedUsername())
}

/** Port de `LiveUsernameText`. [color]/[style],[modifier] = Environment/modifiers Compose. */
@Composable
fun LiveUsernameText(
    userId: String,
    fallbackUsername: String,
    prefix: String = "",
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    LiveUsernameContent(userId = userId, fallbackUsername = fallbackUsername) { username ->
        Text(
            text = prefix + username,
            modifier = modifier,
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
