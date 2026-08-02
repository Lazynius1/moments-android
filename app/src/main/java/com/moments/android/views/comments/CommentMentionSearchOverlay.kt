package com.moments.android.views.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `CommentMentionSearchOverlay.swift`.
 */
@Composable
fun CommentMentionSearchOverlay(
    query: String? = null,
    showsSearchField: Boolean = true,
    placeholder: String = stringResource(R.string.creator_tag_search),
    onSelect: (AppUser) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val colors = rememberAdaptiveColors()

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun searchUsers(raw: String) {
        val trimmed = raw.trim()
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return
        }
        isSearching = true
        searchJob = scope.launch {
            delay(180)
            val users = runCatching { firestore.searchUsers(trimmed, limit = 10) }.getOrDefault(emptyList())
            searchResults = users
            isSearching = false
        }
    }

    LaunchedEffect(query, showsSearchField) {
        if (!showsSearchField) {
            searchUsers(query.orEmpty())
        } else if (!query.isNullOrBlank()) {
            searchText = query
            searchUsers(query)
        }
    }

    // ≡ iOS `isSearchFocused = showsSearchField` onAppear
    LaunchedEffect(showsSearchField) {
        if (showsSearchField) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val effectiveQuery = if (showsSearchField) searchText else query.orEmpty()
    val shouldShowResults = isSearching || effectiveQuery.isNotEmpty()
    val panelHeight = (minOf(maxOf(searchResults.size, 1), 3) * 67).dp
    val capsuleShape = RoundedCornerShape(50)
    val panelShape = RoundedCornerShape(24.dp)

    Column(modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        if (showsSearchField) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(capsuleShape, interactive = true)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        searchUsers(it)
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text(placeholder, color = Color.Gray, fontSize = 15.sp)
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clickable {
                                searchText = ""
                                searchUsers("")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(capsuleShape, interactive = true)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.Gray)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (!query.isNullOrEmpty()) "@$query" else placeholder,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                }
            }
        }

        if (shouldShowResults) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(panelShape, interactive = false)
                    .border(1.dp, colors.primary.copy(alpha = 0.08f), panelShape)
                    .padding(vertical = 8.dp),
            ) {
                when {
                    isSearching -> {
                        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                            MomentsCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    searchResults.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.common_no_results), color = Color.Gray, fontSize = 15.sp)
                        }
                    }
                    else -> {
                        LazyColumn(Modifier.height(panelHeight)) {
                            items(searchResults, key = { it.id }) { user ->
                                CommentMentionSearchRow(user = user, onClick = { onSelect(user) })
                                if (user.id != searchResults.last().id) {
                                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentMentionSearchRow(user: AppUser, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            // ≡ iOS `person.circle.fill` placeholder
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(28.dp),
            )
            if (!user.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = user.profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(42.dp).clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(user.username, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.primary, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(28.dp)
                .momentsChromeGlass(CircleShape, interactive = true)
                .padding(4.dp),
        )
    }
}
