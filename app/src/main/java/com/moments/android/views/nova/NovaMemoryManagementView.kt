package com.moments.android.views.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.views.nova.novacore.NovaColors
import com.moments.android.views.nova.memory.NovaContextStore
import com.moments.android.views.nova.memory.NovaFact
import com.moments.android.views.nova.memory.NovaFactType
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryStore
import kotlinx.coroutines.launch

@Composable
fun NovaMemoryManagementView(onDismiss: () -> Unit, viewModel: NovaMemoryViewModel = remember { NovaMemoryViewModel() }) {
    var editingFact by remember { mutableStateOf<NovaFact?>(null) }
    var editingText by remember { mutableStateOf("") }
    if (viewModel.showClearAllAlert) AlertDialog(
        onDismissRequest = { viewModel.showClearAllAlert = false },
        title = { Text(stringResource(R.string.nova_memory_clear_all_confirm)) },
        text = { Text(stringResource(R.string.nova_memory_clear_all_message)) },
        confirmButton = { Text(stringResource(R.string.common_delete), color = Color.Red, modifier = Modifier.clickable { viewModel.clearAllMemory() }.padding(16.dp)) },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { viewModel.showClearAllAlert = false }.padding(16.dp)) },
    )
    editingFact?.let { fact -> AlertDialog(
        onDismissRequest = { editingFact = null; editingText = "" },
        title = { Text(stringResource(R.string.nova_memory_edit_title)) },
        text = { Column { OutlinedTextField(editingText, { editingText = it }, label = { Text(stringResource(R.string.nova_memory_edit_placeholder)) }); Text(stringResource(R.string.nova_memory_edit_message), modifier = Modifier.padding(top = 8.dp)) } },
        confirmButton = { Text(stringResource(R.string.nova_memory_edit_save), modifier = Modifier.clickable { viewModel.updateFact(fact, editingText); editingFact = null; editingText = "" }.padding(16.dp)) },
        dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { editingFact = null; editingText = "" }.padding(16.dp)) },
    ) }
    Column(Modifier.fillMaxSize()) {
        NovaMemoryHeader(onDismiss)
        when {
            viewModel.isLoading -> LoadingMemory()
            viewModel.memory?.facts.isNullOrEmpty() -> EmptyMemory()
            else -> MemoryContent(viewModel.memory!!.facts, { fact -> editingFact = fact; editingText = fact.content }, viewModel::toggleImportant, viewModel::deleteFact) { viewModel.showClearAllAlert = true }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }
}

@Composable private fun NovaMemoryHeader(onDismiss: () -> Unit) = Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.nova_memory_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NovaColors.textPrimary)
        Text(stringResource(R.string.nova_memory_description), fontSize = 13.sp, color = NovaColors.textSecondary, textAlign = TextAlign.Center)
    }
    IconButton(onDismiss, Modifier.align(Alignment.CenterStart).size(38.dp).clip(CircleShape).background(NovaColors.materialBackground)) { Icon(Icons.Default.Close, null, tint = NovaColors.textPrimary) }
}

@Composable private fun LoadingMemory() = Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(color = NovaColors.textPrimary); Text(stringResource(R.string.settings_loading), color = NovaColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium) } }
@Composable private fun EmptyMemory() = Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 34.dp)) { Icon(Icons.Default.Star, null, tint = NovaColors.textPrimary, modifier = Modifier.size(48.dp).clip(CircleShape).background(NovaColors.materialBackground).padding(10.dp)); Text(stringResource(R.string.nova_memory_empty), color = NovaColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center); Text(stringResource(R.string.nova_memory_empty_subtitle), color = NovaColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center) } }

@Composable private fun MemoryContent(facts: List<NovaFact>, onEdit: (NovaFact) -> Unit, onToggle: (NovaFact) -> Unit, onDelete: (NovaFact) -> Unit, onClear: () -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
    NovaFactType.entries.forEach { type -> facts.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { typed -> item { MemoryCategorySection(type, typed, onEdit, onToggle, onDelete) } } }
    item { Text(stringResource(R.string.nova_memory_clear_all), color = Color.Red, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clip(CircleShape).background(NovaColors.materialBackground).clickable(onClick = onClear).padding(vertical = 13.dp)) }
}

@Composable private fun MemoryCategorySection(type: NovaFactType, facts: List<NovaFact>, onEdit: (NovaFact) -> Unit, onToggle: (NovaFact) -> Unit, onDelete: (NovaFact) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(type.emoji, fontSize = 16.sp, modifier = Modifier.size(28.dp).clip(CircleShape).background(NovaColors.materialBackground).padding(5.dp)); Spacer(Modifier.width(9.dp)); Column { Text(stringResource(type.titleRes()), color = NovaColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(stringResource(if (facts.size == 1) R.string.nova_memory_item_singular else R.string.nova_memory_item_plural, facts.size), color = NovaColors.textSecondary, fontSize = 12.sp) } }
    Column { facts.forEachIndexed { index, fact -> MemoryFactRow(fact, onEdit, onToggle, onDelete); if (index < facts.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 2.dp), color = NovaColors.borderColor.copy(alpha = .45f)) } }
}

@Composable private fun MemoryFactRow(fact: NovaFact, onEdit: (NovaFact) -> Unit, onToggle: (NovaFact) -> Unit, onDelete: (NovaFact) -> Unit) { var menuOpen by remember { mutableStateOf(false) }; Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(fact.content, color = NovaColors.textPrimary, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis); Row(verticalAlignment = Alignment.CenterVertically) { if (fact.importance >= 5) Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(12.dp)); Text(fact.timestamp.timeAgoDisplay(), color = NovaColors.textTertiary, fontSize = 11.sp) } }; Box { IconButton({ menuOpen = true }, Modifier.size(34.dp).clip(CircleShape).background(NovaColors.materialBackground)) { Icon(Icons.Default.MoreVert, null, tint = NovaColors.textPrimary) }; DropdownMenu(menuOpen, { menuOpen = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.nova_memory_edit_action)) }, onClick = { menuOpen = false; onEdit(fact) }, leadingIcon = { Icon(Icons.Default.Edit, null) }); DropdownMenuItem(text = { Text(stringResource(if (fact.importance >= 5) R.string.nova_memory_unmark_important else R.string.nova_memory_mark_important)) }, onClick = { menuOpen = false; onToggle(fact) }, leadingIcon = { Icon(if (fact.importance >= 5) Icons.Outlined.StarBorder else Icons.Default.Star, null) }); DropdownMenuItem(text = { Text(stringResource(R.string.common_delete)) }, onClick = { menuOpen = false; onDelete(fact) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }) } } } }

private fun NovaFactType.titleRes() = when (this) { NovaFactType.PREFERENCE -> R.string.nova_memory_section_preference; NovaFactType.PERSONAL -> R.string.nova_memory_section_personal; NovaFactType.PROFESSIONAL -> R.string.nova_memory_section_professional; NovaFactType.INTEREST -> R.string.nova_memory_section_interest; NovaFactType.GENERAL -> R.string.nova_memory_section_general }

class NovaMemoryViewModel : ViewModel() {
    var memory by mutableStateOf<NovaMemory?>(null); var isLoading by mutableStateOf(false); var showClearAllAlert by mutableStateOf(false)
    private val userId get() = FirebaseAuth.getInstance().currentUser?.uid
    fun load() { val id = userId ?: return; isLoading = true; viewModelScope.launch { memory = NovaMemoryStore.loadMemory(id); isLoading = false } }
    fun deleteFact(fact: NovaFact) { memory?.let { save(it.removingFact(fact.id)) } }
    fun updateFact(fact: NovaFact, content: String) { content.trim().takeIf { it.isNotEmpty() }?.let { text -> memory?.let { save(it.updatingFact(fact.id, content = text)) } } }
    fun toggleImportant(fact: NovaFact) { memory?.let { save(it.updatingFact(fact.id, importance = if (fact.importance >= 5) maxOf(3, fact.type.priority) else 5)) } }
    fun clearAllMemory() { val id = userId ?: return; isLoading = true; showClearAllAlert = false; viewModelScope.launch { runCatching { val cleared = NovaMemory(userId = id).clearingFacts(); NovaMemoryStore.saveMemory(cleared); NovaContextStore.clearContext(id); memory = cleared }.onFailure { load() }; isLoading = false } }
    private fun save(updated: NovaMemory) { memory = updated; viewModelScope.launch { runCatching { NovaMemoryStore.saveMemory(updated) }.onFailure { load() } } }
}
