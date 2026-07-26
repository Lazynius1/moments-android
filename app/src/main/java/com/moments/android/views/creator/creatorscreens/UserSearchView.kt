package com.moments.android.views.creator.creatorscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port de `UserSearchView.swift`: búsqueda Firestore y selección múltiple por id.
 * `loadSuggestions()` en iOS deja la lista vacía — mismo comportamiento.
 */
@Composable
fun UserSearchView(
    selectedUsers: List<String>,
    onSelectedUsersChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedIds by remember(selectedUsers) { mutableStateOf(selectedUsers.toSet()) }

    LaunchedEffect(searchText) {
        val query = searchText.trim()
        if (query.isEmpty()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        results = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(query, limit = 10) }.getOrDefault(emptyList())
        }
        isSearching = false
    }

    fun toggle(user: AppUser) {
        selectedIds = if (user.id in selectedIds) selectedIds - user.id else selectedIds + user.id
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        // ≡ NavigationStack toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.common_cancel),
                color = Color.White,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.creator_tag_people),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.creator_tag_done),
                color = Color(0xFF0A84FF),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    onSelectedUsersChange(selectedIds.toList())
                    onDismiss()
                },
            )
        }

        // ≡ Search bar (gray.opacity(0.2) + cornerRadius 10)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text(
                                stringResource(R.string.creator_tag_search),
                                color = Color.Gray,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            searchText = ""
                            results = emptyList()
                        },
                )
            }
        }

        // ≡ Selected chips (solo usuarios presentes en searchResults, como iOS)
        val selectedUsersInResults = results.filter { it.id in selectedIds }
        if (selectedUsersInResults.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                selectedUsersInResults.forEach { user ->
                    SelectedUserChip(user = user, onRemove = { toggle(user) })
                }
            }
        }

        if (isSearching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.creator_searching),
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { user ->
                    UserSearchRow(
                        user = user,
                        isSelected = user.id in selectedIds,
                        onTap = { toggle(user) },
                    )
                }
            }
        }
    }
}

/** Port de `UserSearchRow`. */
@Composable
private fun UserSearchRow(user: AppUser, isSelected: Boolean, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!user.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = user.profileImagePath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Person, null, tint = Color.Gray)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                user.username,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF0A84FF) else Color.Gray,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Port de `SelectedUserChip`. */
@Composable
private fun SelectedUserChip(user: AppUser, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0xFF0A84FF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(user.username, color = Color.White, fontSize = 12.sp)
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onRemove),
        )
    }
}
