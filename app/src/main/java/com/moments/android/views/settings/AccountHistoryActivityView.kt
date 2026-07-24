package com.moments.android.views.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.AccountHistoryEventType
import com.moments.android.models.AccountHistoryItem
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/** Port de `AccountHistoryActivityView.swift`: timeline de cambios de la cuenta con filtros. */
@Composable
fun AccountHistoryActivityView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black

    var history by remember { mutableStateOf<List<AccountHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf<AccountHistoryEventType?>(null) }
    var sortDescending by remember { mutableStateOf(true) }
    var dateFilter by remember { mutableStateOf(ReactionsDateFilter.ALL) }

    LaunchedEffect(Unit) {
        history = fetchAccountHistoryWithJoinFallback()
        isLoading = false
    }

    val filtered = remember(history, selectedType, sortDescending, dateFilter) {
        history
            .filter { selectedType == null || it.type == selectedType }
            .filterByHistoryDate(dateFilter)
            .let { list -> if (sortDescending) list.sortedByDescending { it.timestamp } else list.sortedBy { it.timestamp } }
    }

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = primary)
                }
                Text(stringResource(R.string.user_activity_cat_account_history_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primary)
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primary)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HistoryFilters(
                        sortDescending = sortDescending, onSort = { sortDescending = it },
                        dateFilter = dateFilter, onDateFilter = { dateFilter = it },
                        selectedType = selectedType, onType = { selectedType = it },
                    )

                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.user_activity_account_history_screen_title),
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = primary, textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.user_activity_account_history_description),
                            fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center,
                        )
                    }

                    if (filtered.isEmpty()) {
                        Text(
                            stringResource(R.string.user_activity_account_history_no_changes),
                            fontSize = 14.sp, color = Color.Gray,
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center,
                        )
                    } else {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            filtered.forEachIndexed { index, item ->
                                AccountHistoryRow(item, isFirst = index == 0, isLast = index == filtered.lastIndex, primary = primary, background = background)
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryFilters(
    sortDescending: Boolean,
    onSort: (Boolean) -> Unit,
    dateFilter: ReactionsDateFilter,
    onDateFilter: (ReactionsDateFilter) -> Unit,
    selectedType: AccountHistoryEventType?,
    onType: (AccountHistoryEventType?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryChipMenu(
            titleRes = R.string.user_activity_filters_sort,
            value = stringResource(if (sortDescending) R.string.user_activity_account_history_filter_newest else R.string.user_activity_account_history_filter_oldest),
        ) { dismiss ->
            DropdownMenuItem(text = { Text(stringResource(R.string.user_activity_account_history_filter_newest)) }, onClick = { onSort(true); dismiss() })
            DropdownMenuItem(text = { Text(stringResource(R.string.user_activity_account_history_filter_oldest)) }, onClick = { onSort(false); dismiss() })
        }
        HistoryChipMenu(
            titleRes = R.string.user_activity_filters_date,
            value = stringResource(dateFilter.titleRes),
        ) { dismiss ->
            ReactionsDateFilter.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(option.titleRes)) }, onClick = { onDateFilter(option); dismiss() })
            }
        }
        HistoryChipMenu(
            titleRes = R.string.user_activity_filters_type,
            value = selectedType?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.user_activity_account_history_filter_all),
        ) { dismiss ->
            DropdownMenuItem(text = { Text(stringResource(R.string.user_activity_account_history_filter_all)) }, onClick = { onType(null); dismiss() })
            AccountHistoryEventType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(stringResource(type.titleRes())) }, onClick = { onType(type); dismiss() })
            }
        }
    }
}

@Composable
private fun HistoryChipMenu(@StringRes titleRes: Int, value: String, menu: @Composable (dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .background(MaterialThemeSurface(), CircleShape)
                .clickableChip { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(titleRes), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

@Composable
private fun AccountHistoryRow(
    item: AccountHistoryItem,
    isFirst: Boolean,
    isLast: Boolean,
    primary: Color,
    background: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Timeline: línea superior, punto, línea inferior.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(Modifier.width(2.dp).height(16.dp).background(if (isFirst) Color.Transparent else Color.Gray.copy(alpha = 0.3f)))
            Box(
                Modifier.size(28.dp).background(background, CircleShape).borderCircle(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.type.icon(), null, tint = primary, modifier = Modifier.size(13.dp))
            }
            Box(Modifier.width(2.dp).height(if (isLast) 0.dp else 60.dp).background(if (isLast) Color.Transparent else Color.Gray.copy(alpha = 0.3f)))
        }

        Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(item.type.titleRes()), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primary)
            Text(MomentsFormat.smartDate(from = item.timestamp, context = MomentsFormat.DateContext.MEDIUM_DATE_TIME), fontSize = 13.sp, color = Color.Gray)

            val old = item.oldValue
            val new = item.newValue
            if (old != null && new != null) {
                Column(
                    Modifier.padding(top = 8.dp).background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChangeLine(labelRes = R.string.user_activity_account_history_change_from, value = old, labelColor = Color(0xFFF97316), primary = primary)
                    ChangeLine(labelRes = R.string.user_activity_account_history_change_to, value = new, labelColor = primary, primary = primary)
                }
            }
        }
    }
}

@Composable
private fun ChangeLine(@StringRes labelRes: Int, value: String, labelColor: Color, primary: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(labelRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = labelColor)
        Text(value, fontSize = 14.sp, color = primary, maxLines = 3)
    }
}

// MARK: - Helpers

private fun AccountHistoryEventType.titleRes(): Int = when (this) {
    AccountHistoryEventType.JOIN -> R.string.user_activity_account_history_type_join
    AccountHistoryEventType.USERNAME -> R.string.user_activity_account_history_type_username
    AccountHistoryEventType.BIO -> R.string.user_activity_account_history_type_bio
    AccountHistoryEventType.WEBSITE -> R.string.user_activity_account_history_type_website
    AccountHistoryEventType.PRIVACY -> R.string.user_activity_account_history_type_privacy
}

private fun AccountHistoryEventType.icon(): ImageVector = when (this) {
    AccountHistoryEventType.JOIN -> Icons.Filled.PersonAdd
    AccountHistoryEventType.USERNAME -> Icons.Filled.Badge
    AccountHistoryEventType.BIO -> Icons.Filled.Notes
    AccountHistoryEventType.WEBSITE -> Icons.Filled.Link
    AccountHistoryEventType.PRIVACY -> Icons.Filled.Lock
}

private fun List<AccountHistoryItem>.filterByHistoryDate(filter: ReactionsDateFilter): List<AccountHistoryItem> {
    if (filter == ReactionsDateFilter.ALL) return this
    val cal = Calendar.getInstance()
    val from = when (filter) {
        ReactionsDateFilter.WEEK -> cal.apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        ReactionsDateFilter.MONTH -> cal.apply { add(Calendar.MONTH, -1) }.time
        ReactionsDateFilter.YEAR -> cal.apply { add(Calendar.YEAR, -1) }.time
        else -> return this // CUSTOM diferido a pulido, se comporta como ALL
    }
    return filter { it.timestamp >= from }
}

/**
 * Igual que iOS: si no hay evento `join`, se sintetiza uno con `users/{uid}.createdAt` o, en su
 * defecto, la fecha de creación de la cuenta de Auth.
 */
private suspend fun fetchAccountHistoryWithJoinFallback(): List<AccountHistoryItem> {
    val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
    val userId = user.uid
    return runCatching {
        val items = FirestoreService().fetchAccountHistory(userId).toMutableList()
        if (items.none { it.type == AccountHistoryEventType.JOIN }) {
            val joinDate = runCatching {
                val doc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
                (doc.get("createdAt") as? com.google.firebase.Timestamp)?.toDate()
            }.getOrNull() ?: user.metadata?.creationTimestamp?.let { Date(it) } ?: Date()
            items.add(
                AccountHistoryItem(
                    id = "synthetic-$userId-join",
                    type = AccountHistoryEventType.JOIN,
                    oldValue = null,
                    newValue = null,
                    timestamp = joinDate,
                ),
            )
        }
        items.toList()
    }.getOrDefault(emptyList())
}

@Composable
private fun MaterialThemeSurface(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

private fun Modifier.clickableChip(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

private fun Modifier.borderCircle(): Modifier = border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
