package com.moments.android.views.nova

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.nova.memory.NovaContextStore
import com.moments.android.views.nova.memory.NovaFact
import com.moments.android.views.nova.memory.NovaFactType
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryStore
import com.moments.android.views.nova.novacore.NovaColors
import kotlinx.coroutines.launch

/**
 * Port de `Views/Nova/NovaMemoryManagementView.swift`.
 * Header, empty/loading, categorías, filas y ViewModel.
 */

@Composable
fun NovaMemoryManagementView(
    onDismiss: () -> Unit,
    viewModel: NovaMemoryViewModel = remember { NovaMemoryViewModel() },
) {
    var editingFact by remember { mutableStateOf<NovaFact?>(null) }
    var editingText by remember { mutableStateOf("") }

    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) { viewModel.load() }

    if (viewModel.showClearAllAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearAllAlert = false },
            title = { Text(stringResource(R.string.nova_memory_clear_all_confirm)) },
            text = { Text(stringResource(R.string.nova_memory_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllMemory() }) {
                    Text(stringResource(R.string.common_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearAllAlert = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    editingFact?.let { fact ->
        AlertDialog(
            onDismissRequest = {
                editingFact = null
                editingText = ""
            },
            title = { Text(stringResource(R.string.nova_memory_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.nova_memory_edit_message))
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        label = { Text(stringResource(R.string.nova_memory_edit_placeholder)) },
                        singleLine = false,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateFact(fact, editingText)
                    editingFact = null
                    editingText = ""
                }) {
                    Text(stringResource(R.string.nova_memory_edit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingFact = null
                    editingText = ""
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        NovaMemoryHeader()
        when {
            viewModel.isLoading -> LoadingMemory()
            viewModel.memory?.facts.isNullOrEmpty() -> EmptyMemory()
            else -> MemoryContent(
                facts = viewModel.memory!!.facts,
                onEdit = { fact ->
                    editingFact = fact
                    editingText = fact.content
                },
                onToggle = viewModel::toggleImportant,
                onDelete = viewModel::deleteFact,
                onClear = { viewModel.showClearAllAlert = true },
            )
        }
    }
}

@Composable
private fun NovaMemoryHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 0.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.nova_memory_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = NovaColors.textPrimary,
        )
        Text(
            text = stringResource(R.string.nova_memory_description),
            fontSize = 12.sp,
            color = NovaColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingMemory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = NovaColors.textPrimary)
            Text(
                text = stringResource(R.string.settings_loading),
                color = NovaColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyMemory() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .momentsEmptyStateAppear(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 34.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .momentsChromeGlass(CircleShape, interactive = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = NovaColors.textPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.nova_memory_empty),
                    color = NovaColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.nova_memory_empty_subtitle),
                    color = NovaColors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MemoryContent(
    facts: List<NovaFact>,
    onEdit: (NovaFact) -> Unit,
    onToggle: (NovaFact) -> Unit,
    onDelete: (NovaFact) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        NovaFactType.entries.forEach { type ->
            val typed = facts.filter { it.type == type }
            if (typed.isNotEmpty()) {
                item(key = type.name) {
                    MemoryCategorySection(type, typed, onEdit, onToggle, onDelete)
                }
            }
        }
        item {
            ClearAllMemoryButton(onClear)
        }
    }
}

@Composable
private fun ClearAllMemoryButton(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .clickable(onClick = onClear)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.nova_memory_clear_all),
            color = Color.Red,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MemoryCategorySection(
    type: NovaFactType,
    facts: List<NovaFact>,
    onEdit: (NovaFact) -> Unit,
    onToggle: (NovaFact) -> Unit,
    onDelete: (NovaFact) -> Unit,
) {
    val itemLabel = stringResource(
        if (facts.size == 1) R.string.nova_memory_item_singular else R.string.nova_memory_item_plural,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .momentsChromeGlass(CircleShape, interactive = false),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = type.sectionIcon(),
                    contentDescription = null,
                    tint = NovaColors.textPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stringResource(type.titleRes()),
                    color = NovaColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${facts.size} $itemLabel",
                    color = NovaColors.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Column {
            facts.forEachIndexed { index, fact ->
                MemoryFactRow(fact, onEdit, onToggle, onDelete)
                if (index < facts.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 2.dp),
                        color = NovaColors.borderColor.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryFactRow(
    fact: NovaFact,
    onEdit: (NovaFact) -> Unit,
    onToggle: (NovaFact) -> Unit,
    onDelete: (NovaFact) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = fact.content,
                color = NovaColors.textPrimary,
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (fact.importance >= 5) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Yellow,
                        modifier = Modifier.size(9.dp),
                    )
                }
                Text(
                    text = fact.timestamp.timeAgoDisplay(),
                    color = NovaColors.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }

        Box {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = NovaColors.textPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nova_memory_edit_action)) },
                    onClick = {
                        menuOpen = false
                        onEdit(fact)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (fact.importance >= 5) {
                                    R.string.nova_memory_unmark_important
                                } else {
                                    R.string.nova_memory_mark_important
                                },
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onToggle(fact)
                    },
                    leadingIcon = {
                        Icon(
                            if (fact.importance >= 5) Icons.Outlined.StarBorder else Icons.Default.Star,
                            null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = Color.Red) },
                    onClick = {
                        menuOpen = false
                        onDelete(fact)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) },
                )
            }
        }
    }
}

private fun NovaFactType.titleRes(): Int = when (this) {
    NovaFactType.PREFERENCE -> R.string.nova_memory_section_preference
    NovaFactType.PERSONAL -> R.string.nova_memory_section_personal
    NovaFactType.PROFESSIONAL -> R.string.nova_memory_section_professional
    NovaFactType.INTEREST -> R.string.nova_memory_section_interest
    NovaFactType.GENERAL -> R.string.nova_memory_section_general
}

/** ≡ iOS SF Symbols del category header. */
private fun NovaFactType.sectionIcon(): ImageVector = when (this) {
    NovaFactType.PREFERENCE -> Icons.Default.Tune
    NovaFactType.PERSONAL -> Icons.Default.Person
    NovaFactType.PROFESSIONAL -> Icons.Default.Work
    NovaFactType.INTEREST -> Icons.Default.AutoAwesome
    NovaFactType.GENERAL -> Icons.AutoMirrored.Filled.Chat
}

class NovaMemoryViewModel : ViewModel() {
    var memory by mutableStateOf<NovaMemory?>(null)
    var isLoading by mutableStateOf(false)
    var showClearAllAlert by mutableStateOf(false)

    private val userId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    fun load() {
        val id = userId ?: return
        isLoading = true
        viewModelScope.launch {
            memory = NovaMemoryStore.loadMemory(id)
            isLoading = false
        }
    }

    fun deleteFact(fact: NovaFact) {
        memory?.let { save(it.removingFact(fact.id)) }
    }

    fun updateFact(fact: NovaFact, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        memory?.let { save(it.updatingFact(fact.id, content = trimmed)) }
    }

    fun toggleImportant(fact: NovaFact) {
        memory?.let {
            save(
                it.updatingFact(
                    fact.id,
                    importance = if (fact.importance >= 5) maxOf(3, fact.type.priority) else 5,
                ),
            )
        }
    }

    fun clearAllMemory() {
        val id = userId ?: return
        isLoading = true
        showClearAllAlert = false
        viewModelScope.launch {
            runCatching {
                val cleared = NovaMemory(userId = id).clearingFacts()
                NovaMemoryStore.saveMemory(cleared)
                NovaContextStore.clearContext(id)
                memory = cleared
            }.onFailure { load() }
            isLoading = false
        }
    }

    private fun save(updated: NovaMemory) {
        memory = updated
        viewModelScope.launch {
            runCatching { NovaMemoryStore.saveMemory(updated) }.onFailure { load() }
        }
    }
}
