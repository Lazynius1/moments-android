package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import com.moments.android.views.profile.core.ProfileColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R

/** Port de `HighlightCreateFlowView.swift`. */
@Composable fun HighlightCreateFlowView(mode: HighlightFlowMode, onDismiss: () -> Unit, modifier: Modifier = Modifier) { val vm = remember(mode) { HighlightCreateFlowViewModel(mode) }; var deleteConfirm by remember { mutableStateOf(false) }; DisposableEffect(vm) { vm.loadIfNeeded(); onDispose(vm::clear) }; Column(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6))) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { if (vm.step == HighlightCreateStep.SELECT_STORIES) Icon(Icons.Filled.Close, stringResource(R.string.common_cancel), tint = highlightPrimary(), modifier = Modifier.clickable { onDismiss() }) else Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = highlightPrimary(), modifier = Modifier.clickable { vm.backToSelectStories() }); Text(stringResource(if (vm.step == HighlightCreateStep.SELECT_STORIES) R.string.highlight_add_to_highlights else if (vm.isEditMode) R.string.highlight_edit_title else R.string.highlight_new_title), color = highlightPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 16.dp)); if (vm.step == HighlightCreateStep.SELECT_STORIES) Icon(Icons.Filled.ArrowBack, null, tint = if (vm.canAdvance) ProfileColors.accent else Color.Gray, modifier = Modifier.clickable(enabled = vm.canAdvance) { vm.advanceToNameAndCover() }) else { if (vm.isEditMode) TextButton({ deleteConfirm = true }) { Text(stringResource(R.string.common_delete), color = Color.Red) }; Icon(Icons.Filled.Check, null, tint = if (vm.canSave) ProfileColors.accent else Color.Gray, modifier = Modifier.clickable(enabled = vm.canSave && !vm.isSaving) { vm.save { if (it == null) onDismiss() } }) } }; Box(Modifier.weight(1f)) { if (vm.step == HighlightCreateStep.SELECT_STORIES) HighlightSelectStoriesStep(vm) else HighlightNameCoverStep(vm) } }; if (deleteConfirm) AlertDialog({ deleteConfirm = false }, title = { Text(stringResource(R.string.common_delete)) }, dismissButton = { TextButton({ deleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }, confirmButton = { TextButton({ deleteConfirm = false; vm.deleteHighlight { if (it == null) onDismiss() } }) { Text(stringResource(R.string.common_delete), color = Color.Red) } }) }
@Composable fun HighlightFlowBackground(modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)))
