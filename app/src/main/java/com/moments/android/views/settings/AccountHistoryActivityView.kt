package com.moments.android.views.settings

import android.app.DatePickerDialog
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AccountHistoryEventType
import com.moments.android.models.AccountHistoryItem
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.MomentsFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.tasks.await

/**
 * Port 1:1 de `AccountHistoryActivityView.swift` (430 líneas).
 * Timeline de cambios de cuenta + filtros sort/fecha/tipo + join sintético.
 */
@Composable
fun AccountHistoryActivityView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)

    var history by remember { mutableStateOf<List<AccountHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf<AccountHistoryEventType?>(null) }
    var sortDescending by remember { mutableStateOf(true) }
    var dateFilter by remember { mutableStateOf(ReactionsDateFilter.ALL) }

    var customDateFrom by remember {
        mutableStateOf(
            Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time,
        )
    }
    var customDateTo by remember { mutableStateOf(Date()) }

    suspend fun reload() {
        history = fetchAccountHistoryWithJoinFallback()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val filtered = remember(
        history,
        selectedType,
        sortDescending,
        dateFilter,
        customDateFrom,
        customDateTo,
    ) {
        getFilteredAndSortedHistory(
            history = history,
            selectedType = selectedType,
            dateFilter = dateFilter,
            customDateFrom = customDateFrom,
            customDateTo = customDateTo,
            sortDescending = sortDescending,
        )
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_cat_account_history_title),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MomentsCircularProgressIndicator()
                }
            } else {
                ActivityCollapsibleFilterScroll(
                    onRefresh = { reload() },
                    header = {
                        Column {
                            AccountHistoryFilterHeader(
                                sortDescending = sortDescending,
                                onSort = { sortDescending = it },
                                dateFilter = dateFilter,
                                onDateFilter = { dateFilter = it },
                                selectedType = selectedType,
                                onType = { selectedType = it },
                                isDark = isDark,
                                primary = primary,
                            )
                            if (dateFilter == ReactionsDateFilter.CUSTOM) {
                                CustomDateRangeControls(
                                    from = customDateFrom,
                                    to = customDateTo,
                                    onFrom = { customDateFrom = it },
                                    onTo = { customDateTo = it },
                                    isDark = isDark,
                                    primary = primary,
                                )
                            }
                        }
                    },
                    content = {
                        AccountHistoryScrollBody(
                            filtered = filtered,
                            primary = primary,
                            background = background,
                            accent = accent,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountHistoryScrollBody(
    filtered: List<AccountHistoryItem>,
    primary: Color,
    background: Color,
    accent: Color,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.user_activity_account_history_screen_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.user_activity_account_history_description),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
        }

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.user_activity_account_history_no_changes),
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                filtered.forEachIndexed { index, item ->
                    AccountHistoryRow(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == filtered.lastIndex,
                        primary = primary,
                        background = background,
                        accent = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountHistoryFilterHeader(
    sortDescending: Boolean,
    onSort: (Boolean) -> Unit,
    dateFilter: ReactionsDateFilter,
    onDateFilter: (ReactionsDateFilter) -> Unit,
    selectedType: AccountHistoryEventType?,
    onType: (AccountHistoryEventType?) -> Unit,
    isDark: Boolean,
    primary: Color,
) {
    val stroke = Color.White.copy(alpha = if (isDark) 0.06f else 0.16f)
    var menuExpanded by remember { mutableStateOf(false) }
    var lastSegment by remember {
        mutableStateOf(
            if (dateFilter == ReactionsDateFilter.CUSTOM) ReactionsDateFilter.ALL else dateFilter,
        )
    }
    val segmentOptions = ReactionsDateFilter.entries.filter { it != ReactionsDateFilter.CUSTOM }
    val hasSecondary =
        !sortDescending || dateFilter == ReactionsDateFilter.CUSTOM || selectedType != null

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .border(1.dp, stroke, RoundedCornerShape(50))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segmentOptions.forEach { option ->
                val isSelected = dateFilter != ReactionsDateFilter.CUSTOM && dateFilter == option
                Text(
                    text = stringResource(option.titleRes),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    if (isDark) Color.White.copy(alpha = 0.14f) else Color.White,
                                )
                            } else Modifier,
                        )
                        .clickable {
                            lastSegment = option
                            onDateFilter(option)
                        }
                        .padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    maxLines = 1,
                )
            }
        }
        Box {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (hasSecondary) {
                            Modifier.momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        } else {
                            Modifier.border(1.dp, stroke, RoundedCornerShape(50))
                        },
                    )
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.saved_moments_filters_button),
                    tint = primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                Text(
                    text = stringResource(R.string.user_activity_filters_sort),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary.copy(alpha = 0.55f),
                )
                HistoryMenuItem(
                    label = stringResource(R.string.user_activity_account_history_filter_newest),
                    selected = sortDescending,
                    onClick = { onSort(true); menuExpanded = false },
                )
                HistoryMenuItem(
                    label = stringResource(R.string.user_activity_account_history_filter_oldest),
                    selected = !sortDescending,
                    onClick = { onSort(false); menuExpanded = false },
                )
                Text(
                    text = stringResource(R.string.user_activity_filters_type),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary.copy(alpha = 0.55f),
                )
                HistoryMenuItem(
                    label = stringResource(R.string.user_activity_account_history_filter_all),
                    selected = selectedType == null,
                    onClick = { onType(null); menuExpanded = false },
                )
                AccountHistoryEventType.entries.forEach { type ->
                    HistoryMenuItem(
                        label = stringResource(type.labelRes),
                        selected = selectedType == type,
                        onClick = { onType(type); menuExpanded = false },
                    )
                }
                Text(
                    text = stringResource(R.string.user_activity_filters_date),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary.copy(alpha = 0.55f),
                )
                HistoryMenuItem(
                    label = stringResource(ReactionsDateFilter.CUSTOM.titleRes),
                    selected = dateFilter == ReactionsDateFilter.CUSTOM,
                    onClick = { onDateFilter(ReactionsDateFilter.CUSTOM); menuExpanded = false },
                )
                if (hasSecondary) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.saved_moments_filters_reset),
                                color = Color(0xFFFF3B30),
                            )
                        },
                        onClick = {
                            onSort(true)
                            if (dateFilter == ReactionsDateFilter.CUSTOM) onDateFilter(lastSegment)
                            onType(null)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDateRangeControls(
    from: Date,
    to: Date,
    onFrom: (Date) -> Unit,
    onTo: (Date) -> Unit,
    isDark: Boolean,
    primary: Color,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    val chipBg = (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            dateFormat.format(from),
            modifier = Modifier
                .background(chipBg, CircleShape)
                .border(1.dp, Color.Gray.copy(alpha = 0.22f), CircleShape)
                .clickable {
                    pickHistoryDate(context, from, onFrom)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = primary,
        )
        Text(
            dateFormat.format(to),
            modifier = Modifier
                .background(chipBg, CircleShape)
                .border(1.dp, Color.Gray.copy(alpha = 0.22f), CircleShape)
                .clickable {
                    pickHistoryDate(context, to, onTo)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = primary,
        )
    }
}

@Composable
private fun HistoryChipMenu(
    @StringRes titleRes: Int,
    value: String,
    isDark: Boolean,
    primary: Color,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chipBg = (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f)
    Box {
        Row(
            Modifier
                .background(chipBg, CircleShape)
                .border(1.dp, Color.Gray.copy(alpha = 0.22f), CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(titleRes),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
            )
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

@Composable
private fun HistoryMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label)
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun AccountHistoryRow(
    item: AccountHistoryItem,
    isFirst: Boolean,
    isLast: Boolean,
    primary: Color,
    background: Color,
    accent: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(if (isFirst) Color.Transparent else Color.Gray.copy(alpha = 0.3f)),
            )
            Box(
                Modifier
                    .size(28.dp)
                    .background(background, CircleShape)
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.type.icon(), null, tint = primary, modifier = Modifier.size(13.dp))
            }
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else Color.Gray.copy(alpha = 0.3f)),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(item.type.labelRes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = primary,
            )
            Text(
                MomentsFormat.smartDate(
                    from = item.timestamp,
                    context = MomentsFormat.DateContext.MEDIUM_DATE_TIME,
                ),
                fontSize = 13.sp,
                color = Color.Gray,
            )

            val old = item.oldValue
            val new = item.newValue
            if (old != null && new != null) {
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChangeLine(
                        labelRes = R.string.user_activity_account_history_change_from,
                        value = old,
                        labelColor = Color(0xFFF97316),
                        primary = primary,
                    )
                    ChangeLine(
                        labelRes = R.string.user_activity_account_history_change_to,
                        value = new,
                        labelColor = accent,
                        primary = primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeLine(
    @StringRes labelRes: Int,
    value: String,
    labelColor: Color,
    primary: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(labelRes),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
        )
        Text(value, fontSize = 14.sp, color = primary, maxLines = 3)
    }
}

private fun AccountHistoryEventType.icon(): ImageVector = when (this) {
    AccountHistoryEventType.JOIN -> Icons.Filled.PersonAdd
    AccountHistoryEventType.USERNAME -> Icons.Filled.Badge
    AccountHistoryEventType.BIO -> Icons.AutoMirrored.Filled.Notes
    AccountHistoryEventType.WEBSITE -> Icons.Filled.Link
    AccountHistoryEventType.PRIVACY -> Icons.Filled.Lock
}

private fun getFilteredAndSortedHistory(
    history: List<AccountHistoryItem>,
    selectedType: AccountHistoryEventType?,
    dateFilter: ReactionsDateFilter,
    customDateFrom: Date,
    customDateTo: Date,
    sortDescending: Boolean,
): List<AccountHistoryItem> {
    var filtered = history
    if (selectedType != null) {
        filtered = filtered.filter { it.type == selectedType }
    }

    val now = Date()
    filtered = filtered.filter { item ->
        when (dateFilter) {
            ReactionsDateFilter.ALL -> true
            ReactionsDateFilter.WEEK -> {
                val start = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.DAY_OF_YEAR, -7)
                }.time
                item.timestamp >= start
            }
            ReactionsDateFilter.MONTH -> {
                val start = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.MONTH, -1)
                }.time
                item.timestamp >= start
            }
            ReactionsDateFilter.YEAR -> {
                val start = Calendar.getInstance().apply {
                    time = now
                    add(Calendar.YEAR, -1)
                }.time
                item.timestamp >= start
            }
            ReactionsDateFilter.CUSTOM -> {
                val startCal = Calendar.getInstance().apply {
                    time = minOf(customDateFrom, customDateTo)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    time = maxOf(customDateFrom, customDateTo)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                item.timestamp >= startCal.time && item.timestamp < endCal.time
            }
        }
    }

    return if (sortDescending) {
        filtered.sortedByDescending { it.timestamp }
    } else {
        filtered.sortedBy { it.timestamp }
    }
}

/**
 * Igual que iOS: si no hay evento `join`, se sintetiza uno con `users/{uid}.createdAt` o
 * la fecha de creación de Auth.
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

private fun pickHistoryDate(
    context: android.content.Context,
    initial: Date,
    onChosen: (Date) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { time = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onChosen(
                Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time,
            )
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}
