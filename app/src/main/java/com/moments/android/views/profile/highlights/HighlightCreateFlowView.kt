package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.profile.core.ProfileColors

/** Port de `HighlightCreateFlowView.swift`. */
@Composable
fun HighlightCreateFlowView(
    mode: HighlightFlowMode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm = remember(mode) { HighlightCreateFlowViewModel(mode, context.applicationContext) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val canvas = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = ProfileColors.textPrimary()

    DisposableEffect(vm) {
        vm.loadIfNeeded()
        onDispose { vm.clear() }
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (vm.step) {
                HighlightCreateStep.SELECT_STORIES -> {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp),
                    )
                    Text(
                        stringResource(R.string.highlighted_stories_add_to_highlights),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.highlighted_stories_next),
                        tint = if (vm.canAdvance) ProfileColors.accent else Color.Gray,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = vm.canAdvance) { vm.advanceToNameAndCover() }
                            .padding(4.dp),
                    )
                }
                HighlightCreateStep.NAME_AND_COVER -> {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.common_back),
                        tint = primary,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { vm.backToSelectStories() }
                            .padding(4.dp),
                    )
                    Text(
                        stringResource(
                            if (vm.isEditMode) {
                                R.string.highlighted_stories_edit_highlight_title
                            } else {
                                R.string.highlighted_stories_new_highlight_title
                            },
                        ),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    if (vm.isEditMode) {
                        Box {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = primary,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { overflowExpanded = true }
                                    .padding(4.dp),
                            )
                            DropdownMenu(overflowExpanded, { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_delete), color = Color.Red) },
                                    onClick = {
                                        overflowExpanded = false
                                        deleteConfirm = true
                                    },
                                )
                            }
                        }
                    }
                    if (vm.isSaving) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp).padding(end = 6.dp),
                            color = ProfileColors.accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (vm.isEditMode) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = stringResource(vm.saveActionTitleRes),
                            tint = if (vm.canSave) ProfileColors.accent else Color.Gray,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = vm.canSave && !vm.isSaving) {
                                    vm.save { if (it == null) onDismiss() }
                                }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.step) {
                HighlightCreateStep.SELECT_STORIES -> HighlightSelectStoriesStep(vm)
                HighlightCreateStep.NAME_AND_COVER -> HighlightNameCoverStep(vm)
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.common_delete)) },
            dismissButton = {
                TextButton({ deleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            confirmButton = {
                TextButton({
                    deleteConfirm = false
                    vm.deleteHighlight { if (it == null) onDismiss() }
                }) {
                    Text(stringResource(R.string.common_delete), color = Color.Red)
                }
            },
        )
    }
}

/** Port de `HighlightFlowBackground`. */
@Composable
fun HighlightFlowBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)),
    )
}
