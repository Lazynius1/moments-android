package com.moments.android.views.creator.audienceselector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.CustomAudienceList
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.AudienceIconView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CanvasDark = Color(0xFF0B1215)
private val CanvasLight = Color(0xFFFAF9F6)
private val AudienceBlue = Color(0xFF007AFF)
private val AudienceTeal = Color(0xFF00A896)

/**
 * Port de `CustomAudienceSelector` (CustomAudienceManagementViews.swift).
 * iOS usa `UserSelectionCard` (AudienceSelectionView.swift) para cada resultado.
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
    val canvas = if (dark) CanvasDark else CanvasLight
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val density = LocalDensity.current
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchResults = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(q, limit = 20) }.getOrDefault(emptyList())
        }
        isSearching = false
    }

    Column(
        modifier
            .fillMaxSize()
            .background(if (!embeddedInFlow) canvas else Color.Transparent),
    ) {
        if (embeddedInFlow) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { onBack?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        null,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.audience_actions_selectPeople),
                        color = content,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = with(density) { legacyPoppinsSize(context, 20).toSp() },
                    )
                    Text(
                        stringResource(R.string.audience_description_custom),
                        color = content.copy(alpha = if (dark) 0.6f else 0.55f),
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }
        }

        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = content,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                ),
                cursorBrush = SolidColor(content),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.audience_search_people_placeholder),
                            color = Color.Gray,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        )
                    }
                    inner()
                },
            )
        }

        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AudienceBlue)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(searchResults, key = { it.id }) { user ->
                    val selected = selectedUsers.any { it.id == user.id }
                    // iOS: UserSelectionCard (AudienceSelectionView.swift)
                    UserSelectionCard(
                        user = user,
                        isSelected = selected,
                        onToggle = {
                            onSelectedUsersChange(
                                if (selected) selectedUsers.filterNot { it.id == user.id }
                                else selectedUsers + user,
                            )
                        },
                    )
                }
            }
        }

        if (selectedUsers.isNotEmpty()) {
            Text(
                stringResource(R.string.audience_selectPeople, selectedUsers.size),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AudienceBlue)
                    .clickable(onClick = onComplete)
                    .padding(16.dp),
            )
        }
    }
}

/** Port de `UserSelectionRow` (fila simple con checkbox teal). */
@Composable
fun UserSelectionRow(
    user: AppUser,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val url = user.profileImagePath
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
        Text(
            user.username,
            color = Color.Gray,
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            null,
            tint = if (isSelected) AudienceTeal else Color.Gray,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Port de `CustomAudienceListsView`.
 * Sheets Create/Edit solo si `!embeddedInFlow` (como iOS).
 */
@Composable
fun CustomAudienceListsView(
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onCreateList: (() -> Unit)? = null,
    onEditList: ((CustomAudienceList) -> Unit)? = null,
    onListsChanged: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewModel = remember { CustomAudienceListsViewModel() }
    var showingCreateList by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<CustomAudienceList?>(null) }
    var showingDeleteAlert by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<CustomAudienceList?>(null) }
    var showingDeleteFeedback by remember { mutableStateOf(false) }
    var deletedListName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(viewModel) {
        viewModel.loadLists()
        onDispose { viewModel.clear() }
    }
    LaunchedEffect(viewModel.lists) { onListsChanged?.invoke() }

    fun openCreate() {
        if (embeddedInFlow) onCreateList?.invoke() else showingCreateList = true
    }

    fun openEdit(list: CustomAudienceList) {
        if (embeddedInFlow) onEditList?.invoke(list) else selectedList = list
    }

    fun showDeleteFeedback() {
        showingDeleteFeedback = true
        scope.launch {
            delay(1500)
            showingDeleteFeedback = false
        }
    }

    if (showingDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showingDeleteAlert = false },
            title = { Text(stringResource(R.string.audience_deleteList_title)) },
            text = {
                Text(stringResource(R.string.audience_deleteList_confirm, listToDelete?.name.orEmpty()))
            },
            confirmButton = {
                TextButton(onClick = {
                    val list = listToDelete
                    if (list != null) {
                        deletedListName = list.name
                        viewModel.deleteList(list)
                        onListsChanged?.invoke()
                        showDeleteFeedback()
                    }
                    showingDeleteAlert = false
                    listToDelete = null
                }) { Text(stringResource(R.string.common_delete), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showingDeleteAlert = false
                    listToDelete = null
                }) { Text(stringResource(R.string.audience_actions_cancel)) }
            },
        )
    }

    // iOS sheets cuando no está embedded
    if (!embeddedInFlow && showingCreateList) {
        Dialog(onDismissRequest = { showingCreateList = false }) {
            CreateCustomListView(
                embeddedInFlow = false,
                onCompleted = {
                    showingCreateList = false
                    onListsChanged?.invoke()
                },
                onBack = { showingCreateList = false },
                modifier = Modifier.fillMaxSize().background(if (dark) CanvasDark else CanvasLight),
            )
        }
    }
    selectedList?.let { list ->
        if (!embeddedInFlow) {
            Dialog(onDismissRequest = { selectedList = null }) {
                EditCustomListView(
                    list = list,
                    embeddedInFlow = false,
                    onCompleted = {
                        selectedList = null
                        onListsChanged?.invoke()
                    },
                    onBack = { selectedList = null },
                    modifier = Modifier.fillMaxSize().background(if (dark) CanvasDark else CanvasLight),
                )
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Color.Gray, strokeWidth = 2.dp)
                    Text(stringResource(R.string.common_loading), color = Color.Gray)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable {
                                if (embeddedInFlow) onBack?.invoke() else onDismiss?.invoke()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (embeddedInFlow) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            null,
                            tint = content,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.audience_customLists_title),
                            color = content,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = with(density) { legacyPoppinsSize(context, 20).toSp() },
                        )
                        Text(
                            stringResource(R.string.audience_custom_lists),
                            color = content.copy(alpha = if (dark) 0.6f else 0.55f),
                            fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(40.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { openCreate() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(18.dp))
                    }
                }

                if (viewModel.lists.isEmpty()) {
                    AudienceListsEmptyState(
                        content = content,
                        onCreate = { openCreate() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(viewModel.lists, key = { it.id ?: it.name }) { list ->
                            ManageableCustomListCard(
                                list = list,
                                onEdit = { openEdit(list) },
                                onDelete = {
                                    listToDelete = list
                                    showingDeleteAlert = true
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showingDeleteFeedback,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                stringResource(R.string.audience_deleteList_success, deletedListName),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .shadow(10.dp, RoundedCornerShape(50), spotColor = Color.Black.copy(0.2f))
                    .background(AudienceBlue, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AudienceListsEmptyState(
    content: Color,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Column(
        modifier
            .padding(top = 40.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AudienceIconView(
            audience = ContentAudience.CUSTOM_LIST,
            size = 44.dp,
            tintColor = Color.Gray,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.audience_noCustomLists_title),
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 20).toSp() },
            )
            Text(
                stringResource(R.string.audience_noCustomLists_description),
                color = Color.Gray,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
        Row(
            Modifier.padding(top = 6.dp).clickable(onClick = onCreate),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(30.dp).momentsChromeGlass(CircleShape, interactive = true),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(R.string.audience_createFirstList),
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
            )
        }
    }
}

/** Port de `ManageableCustomListCard` — tap = edit; long-press menú = delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageableCustomListCard(
    list: CustomAudienceList,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val tint = Color.fromHex(list.color ?: "00A896")
    val context = LocalContext.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .scale(if (pressed) 0.96f else 1f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onEdit,
                    onLongClick = { menuExpanded = true },
                )
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(56.dp).background(tint.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(listIconVector(list.icon), null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    list.name,
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Person, null, tint = content.copy(0.6f), modifier = Modifier.size(11.dp))
                    Text(
                        stringResource(R.string.audience_people_count, list.members.size),
                        color = content.copy(0.6f),
                        fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    )
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete), color = Color.Red) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color.Red) },
            )
        }
    }
}

/** Port de `CustomAudienceListsViewModel` — listener Firestore `customAudienceLists`. */
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
        listener?.remove()
        isLoading = true
        listener = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("customAudienceLists")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    errorMessage = error.localizedMessage
                    isLoading = false
                    return@addSnapshotListener
                }
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
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("customAudienceLists").document(listId)
            .delete()
    }

    fun clear() {
        listener?.remove()
        listener = null
    }
}
