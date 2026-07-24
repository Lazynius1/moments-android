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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de `CustomListSelectorView.swift`. */
@Composable
fun CustomListSelectorView(
    selectedListId: String?,
    onListSelected: (id: String?, name: String?) -> Unit,
    onDismiss: () -> Unit,
    userId: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
    onCreateList: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (dark) Color.White else Color.Black
    var lists by remember(userId) { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var loading by remember(userId) { mutableStateOf(true) }

    LaunchedEffect(userId) {
        loading = true
        lists = if (userId.isBlank()) emptyList() else withContext(Dispatchers.IO) {
            runCatching { FirestoreService().fetchCustomLists(userId) }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Custom lists", color = content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable { onCreateList?.invoke() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(18.dp)) }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            lists.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Group, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Text("No custom lists yet", color = content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp))
                Text("Create your first custom list", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Create first list",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0xFF007AFF))
                        .clickable { onCreateList?.invoke() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(lists, key = { it.id ?: it.name }) { list ->
                    val selected = list.id == selectedListId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onListSelected(list.id, list.name)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(list.name, color = content, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${list.members.size} people", color = content.copy(.6f), fontSize = 13.sp)
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
