package com.moments.android.views.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.moments.android.services.cache.UserCacheService

/** Port genérico de `LiveUsernameContent`: resuelve el nombre fresco sin ocultar el fallback. */
@Composable
fun LiveUsernameContent(
    userId: String,
    fallbackUsername: String,
    content: @Composable (String) -> Unit,
) {
    var liveUsername by remember(userId) { mutableStateOf("") }
    val resolved = liveUsername.trim().ifEmpty { fallbackUsername.trim().ifEmpty { "Usuario" } }
    LaunchedEffect(userId, fallbackUsername) {
        val requestedId = userId.trim()
        if (requestedId.isEmpty()) {
            liveUsername = ""
        } else {
            UserCacheService.refreshUser(requestedId) { user ->
                if (userId.trim() == requestedId) liveUsername = user?.username?.trim().orEmpty()
            }
        }
    }
    content(resolved)
}

/** Port de `LiveUsernameText.swift`. */
@Composable
fun LiveUsernameText(
    userId: String,
    fallbackUsername: String,
    prefix: String = "",
    color: Color = LocalContentColor.current,
    style: TextStyle = TextStyle.Default,
) {
    LiveUsernameContent(userId, fallbackUsername) { username ->
        Text(prefix + username, color = color, style = style)
    }
}
