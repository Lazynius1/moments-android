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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.CustomAudienceList
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.utilities.legacyPoppinsSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CanvasDark = Color(0xFF0B1215)
private val CanvasLight = Color(0xFFFAF9F6)
private val AccentBlue = Color(0xFF007AFF)

/**
 * Port de `CustomListSelectorView.swift`.
 *
 * Bindings iOS → callbacks. Sheet de creación → `CustomAudienceListsView`;
 * al cerrar recarga. La carga usa `Auth.currentUser?.uid` como el Swift.
 */
@Composable
fun CustomListSelectorView(
    selectedListId: String?,
    selectedListName: String?,
    onSelectedListIdChange: (String?) -> Unit,
    onSelectedListNameChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") userId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) CanvasDark else CanvasLight
    val content = if (dark) Color.White else Color.Black
    val secondary = content.copy(alpha = 0.55f)
    val context = LocalContext.current
    val density = LocalDensity.current

    var customLists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showingCreateList by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableStateOf(0) }

    @Suppress("UNUSED_VARIABLE")
    val keepName = selectedListName

    suspend fun loadCustomLists() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        isLoading = true
        customLists = if (uid == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchCustomLists(uid) }.getOrDefault(emptyList())
            }
        }
        isLoading = false
    }

    LaunchedEffect(reloadToken) {
        loadCustomLists()
    }

    if (showingCreateList) {
        Dialog(
            onDismissRequest = {
                showingCreateList = false
                reloadToken++
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CustomAudienceListsView(
                embeddedInFlow = false,
                onDismiss = {
                    showingCreateList = false
                    reloadToken++
                },
                onListsChanged = { reloadToken++ },
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvas),
            )
        }
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = AccentBlue)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.audience_custom_lists),
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 17).toSp() },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showingCreateList = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = AccentBlue, strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.audience_loadingLists),
                        color = secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    )
                }
            }
            customLists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(Icons.Filled.List, null, tint = secondary, modifier = Modifier.size(48.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.audience_noCustomLists_title),
                            color = content,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                        )
                        Text(
                            stringResource(R.string.audience_noCustomLists_description),
                            color = secondary,
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            textAlign = TextAlign.Center,
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(25.dp))
                            .background(AccentBlue)
                            .clickable { showingCreateList = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.AddCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.audience_createFirstList),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        )
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(customLists, key = { it.id ?: it.name }) { list ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectedListIdChange(list.id)
                                onSelectedListNameChange(list.name)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                list.name,
                                color = content,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                            )
                            Text(
                                stringResource(R.string.audience_people_count, list.members.size),
                                color = secondary,
                                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                            )
                        }
                        if (selectedListId == list.id) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = content.copy(alpha = 0.08f))
                }
            }
        }
    }
}
