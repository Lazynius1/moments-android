package com.moments.android.views.creator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.searchUsers
import com.moments.android.views.creator.normalizeStickerUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de `ModernMentionInputView`: búsqueda real con el mismo repositorio Firestore del resto de Android. */
@Composable
fun ModernMentionInputView(onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val fg = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF111827)
    val context = LocalContext.current
    val firestore = remember { FirestoreService() }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(emptyList<com.moments.android.models.AppUser>()) }
    var recentUsers by remember { mutableStateOf(emptyList<com.moments.android.models.AppUser>()) }
    var suggestedUsers by remember { mutableStateOf(emptyList<com.moments.android.models.AppUser>()) }
    var suggestionsLoading by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    fun select(user: com.moments.android.models.AppUser) {
        val prefs = context.getSharedPreferences("moments_sticker_inputs", android.content.Context.MODE_PRIVATE)
        val ids = (listOf(user.id) + prefs.getString("recentMentionedUsers", "").orEmpty().split(','))
            .filter { it.isNotBlank() }.distinct().take(10)
        prefs.edit().putString("recentMentionedUsers", ids.joinToString(",")).apply()
        recentUsers = listOf(user) + recentUsers.filterNot { it.id == user.id }
        onSelect(user.username)
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("moments_sticker_inputs", android.content.Context.MODE_PRIVATE)
        val ids = prefs.getString("recentMentionedUsers", "").orEmpty().split(',').filter { it.isNotBlank() }.take(5)
        val currentId = FirebaseAuth.getInstance().currentUser?.uid
        val loaded = withContext(Dispatchers.IO) {
            val recent = runCatching { firestore.fetchUsers(ids) }.getOrDefault(emptyList())
            val suggested = currentId?.let { runCatching { firestore.fetchMutuals(it) }.getOrDefault(emptyList()) }.orEmpty()
            recent to suggested.filter { it.id != currentId }.take(6)
        }
        recentUsers = loaded.first
        suggestedUsers = loaded.second
        suggestionsLoading = false
    }
    LaunchedEffect(query) {
        val normalized = query.trim().removePrefix("@")
        if (normalized.isBlank()) { users = emptyList(); loading = false; return@LaunchedEffect }
        delay(300)
        loading = true
        users = runCatching { firestore.searchUsers(normalized.lowercase(), limit = 15) }
            .getOrDefault(emptyList())
            .filter { it.id != FirebaseAuth.getInstance().currentUser?.uid }
            .sortedBy { it.username.lowercase() }
        loading = false
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sticker_category_mention), color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        StickerInputField(query, { query = it.take(30) }, stringResource(R.string.sticker_mention_hint), fg, Icons.Filled.Search)
        if (loading) Text("…", color = fg.copy(alpha = 0.55f), modifier = Modifier.padding(12.dp))
        val idle = query.trim().isEmpty()
        val displayed = if (idle) recentUsers + suggestedUsers.filterNot { suggested -> recentUsers.any { it.id == suggested.id } } else users
        if (idle && recentUsers.isNotEmpty()) Text("Recent", color = fg.copy(.62f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (idle && suggestedUsers.isNotEmpty()) Text("Suggestions", color = fg.copy(.62f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (idle && suggestionsLoading) Text("…", color = fg.copy(.55f), modifier = Modifier.padding(12.dp))
        if (!idle && !loading && users.isEmpty()) Text("No users found for @${query.lowercase()}", color = fg.copy(.58f), modifier = Modifier.padding(12.dp))
        displayed.forEach { user ->
            Row(Modifier.fillMaxWidth().clickable { select(user) }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("@${user.username}", color = fg, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                user.bio?.takeIf { it.isNotBlank() }?.let { Text(it, color = fg.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

/** Port por chunks de `Views/Creator/Components/StickerInputViews.swift`: Link y Quiz. */
@Composable
fun ModernLinkInputView(
    onSelect: (url: String, customTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF111827)
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    val valid = normalizeStickerUrl(url) != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.sticker_link_add), color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.sticker_link_hint), color = fg.copy(alpha = 0.58f), fontSize = 13.sp)
        StickerInputField(url, { url = it.take(200) }, stringResource(R.string.sticker_link_url), fg, Icons.Filled.Link)
        StickerInputField(title, { title = it.take(48) }, stringResource(R.string.sticker_link_title), fg, null)
        Text(
            stringResource(R.string.sticker_link_add),
            color = if (valid) Color.White else fg.copy(alpha = 0.45f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (valid) Color(0xFF4AB8FA) else fg.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable(enabled = valid) { onSelect(url, title) }
                .padding(vertical = 15.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun StickerInputField(value: String, onValueChange: (String) -> Unit, placeholder: String, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(Modifier.fillMaxWidth().background(fg.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Color(0xFF4AB8FA), modifier = Modifier.padding(end = 10.dp)) }
        BasicTextField(value = value, onValueChange = onValueChange, singleLine = true, textStyle = TextStyle(color = fg, fontSize = 16.sp), cursorBrush = SolidColor(fg), modifier = Modifier.weight(1f), decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = fg.copy(alpha = 0.42f), fontSize = 16.sp); inner() })
    }
}
