package com.moments.android.views.creator.audienceselector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Primer bloque del port de `CustomAudienceManagementViews.swift`.
 * Equivalente Compose del selector de personas con búsqueda Firestore.
 */
@Composable
fun CustomAudienceSelector(
    selectedUsers: List<AppUser>,
    onSelectedUsersChange: (List<AppUser>) -> Unit,
    onComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    embeddedInFlow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (dark) Color.White else Color.Black
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        results = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(normalized, limit = 20) }.getOrDefault(emptyList())
        }
        searching = false
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        if (embeddedInFlow) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable { onBack?.invoke() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select people", color = content, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text("Choose specific people", color = content.copy(.6f), fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.Gray) },
            placeholder = { Text("Search people") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        when {
            searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.id }) { user ->
                    UserSelectionRow(
                        user = user,
                        isSelected = selectedUsers.any { it.id == user.id },
                        onToggle = {
                            onSelectedUsersChange(
                                if (selectedUsers.any { it.id == user.id }) selectedUsers.filterNot { it.id == user.id }
                                else selectedUsers + user,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        if (selectedUsers.isNotEmpty()) {
            Text(
                text = "Select ${selectedUsers.size} people",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF007AFF))
                    .clickable(onClick = onComplete)
                    .padding(16.dp),
            )
        }
    }
}

/** Port de `UserSelectionRow` (el Swift llama `UserSelectionCard` desde el selector). */
@Composable
fun UserSelectionRow(
    user: AppUser,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(.3f)),
            error = null,
        )
        if (user.profileImagePath.isNullOrBlank()) {
            Box(Modifier.size(40.dp).background(Color.Gray.copy(.3f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = Color.Gray)
            }
        }
        Text(user.username, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            null,
            tint = if (isSelected) Color(0xFF00A896) else Color.Gray,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Segundo bloque del port: gestión en tiempo real de listas personalizadas. */
@Composable
fun CustomAudienceListsView(
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onCreateList: (() -> Unit)? = null,
    onEditList: ((CustomAudienceList) -> Unit)? = null,
    onListsChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (dark) Color.White else Color.Black
    val viewModel = remember { CustomAudienceListsViewModel() }
    var listToDelete by remember { mutableStateOf<CustomAudienceList?>(null) }
    var deletedName by remember { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel) {
        viewModel.loadLists()
        onDispose { viewModel.clear() }
    }
    LaunchedEffect(viewModel.lists) { onListsChanged?.invoke() }

    if (listToDelete != null) {
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Delete custom list?") },
            text = { Text("Delete ${listToDelete?.name.orEmpty()}?") },
            confirmButton = {
                TextButton(onClick = {
                    val list = listToDelete ?: return@TextButton
                    viewModel.deleteList(list)
                    deletedName = list.name
                    listToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { listToDelete = null }) { Text("Cancel") } },
        )
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (embeddedInFlow) {
                Box(
                    Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable { onBack?.invoke() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp)) }
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Custom lists", color = content, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Manage your audiences", color = content.copy(.6f), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable { onCreateList?.invoke() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(18.dp)) }
        }

        when {
            viewModel.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            viewModel.lists.isEmpty() -> AudienceListsEmptyState(
                onCreate = { onCreateList?.invoke() },
                modifier = Modifier.weight(1f),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(viewModel.lists, key = { it.id ?: it.name }) { list ->
                    ManageableCustomListCard(
                        list = list,
                        onEdit = { onEditList?.invoke(list) },
                        onDelete = { listToDelete = list },
                    )
                }
            }
        }

        deletedName?.let { name ->
            Text(
                "$name deleted",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF007AFF))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AudienceListsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    val content = if (isSystemInDarkTheme()) Color.White else Color.Black
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Group, null, tint = Color.Gray, modifier = Modifier.size(44.dp))
        Text("No custom lists yet", color = content, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
        Text("Create a list to share with a specific group", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
        Text("Create first list", color = content, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp).clickable(onClick = onCreate))
    }
}

/** Port de `ManageableCustomListCard`, con borrado delegado al menú/host. */
@Composable
fun ManageableCustomListCard(
    list: CustomAudienceList,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (isSystemInDarkTheme()) Color.White else Color.Black
    val tint = Color.fromHex(list.color ?: "00A896")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(56.dp).background(tint.copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Group, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(list.name, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = content.copy(.6f), modifier = Modifier.size(11.dp))
            Text("${list.members.size} people", color = content.copy(.6f), fontSize = 12.sp)
        }
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Delete list",
            tint = content.copy(.55f),
            modifier = Modifier.size(18.dp).clickable(onClick = onDelete),
        )
    }
}

/** Equivalente observable de `CustomAudienceListsViewModel` con listener Firestore. */
class CustomAudienceListsViewModel {
    var lists by mutableStateOf<List<CustomAudienceList>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var listener: ListenerRegistration? = null

    fun loadLists() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            lists = emptyList()
            isLoading = false
            return
        }
        isLoading = true
        listener?.remove()
        listener = FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("customAudienceLists")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                errorMessage = error?.localizedMessage
                lists = snapshot?.documents.orEmpty().mapNotNull { document ->
                    @Suppress("UNCHECKED_CAST")
                    CustomAudienceList.from(document.id, document.data as? Map<String, Any?> ?: emptyMap())
                }
                isLoading = false
            }
    }

    fun deleteList(list: CustomAudienceList) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val listId = list.id ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("customAudienceLists").document(listId).delete()
    }

    fun clear() {
        listener?.remove()
        listener = null
    }
}
