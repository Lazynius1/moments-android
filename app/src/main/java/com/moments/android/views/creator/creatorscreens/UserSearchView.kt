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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de `UserSearchView.swift`: búsqueda Firestore y selección múltiple por id. */
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
            // Swift no expone sugerencias todavía: mantiene la lista limpia.
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cancel", color = Color.White, modifier = Modifier.clickable(onClick = onDismiss))
            Spacer(Modifier.weight(1f))
            Text("Tag people", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "Done",
                color = Color(0xFF0A84FF),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    onSelectedUsersChange(selectedIds.toList())
                    onDismiss()
                },
            )
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
            trailingIcon = if (searchText.isNotEmpty()) {
                {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = Color.Gray,
                        modifier = Modifier.clickable { searchText = "" },
                    )
                }
            } else {
                null
            },
            placeholder = { Text("Search", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        val selectedUsersInResults = results.filter { it.id in selectedIds }
        if (selectedUsersInResults.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                selectedUsersInResults.forEach { user ->
                    SelectedUserChip(user = user, onRemove = { toggle(user) })
                }
            }
        }

        if (isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
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

@Composable
private fun UserSearchRow(user: AppUser, isSelected: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Gray.copy(.3f)), contentAlignment = Alignment.Center) {
            if (!user.profileImagePath.isNullOrBlank()) {
                AsyncImage(user.profileImagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.Person, null, tint = Color.Gray)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(user.username, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            user.bio?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.Gray, fontSize = 12.sp, maxLines = 1) }
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            null,
            tint = if (isSelected) Color(0xFF0A84FF) else Color.Gray,
        )
    }
}

@Composable
private fun SelectedUserChip(user: AppUser, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF0A84FF)).padding(start = 12.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(user.username, color = Color.White, fontSize = 12.sp)
        Icon(Icons.Filled.Close, null, tint = Color.White.copy(.72f), modifier = Modifier.size(16.dp).padding(start = 4.dp).clickable(onClick = onRemove))
    }
}
