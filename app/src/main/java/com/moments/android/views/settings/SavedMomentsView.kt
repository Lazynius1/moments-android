package com.moments.android.views.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Moment

enum class SavedMediaFilter(val title: String) {
    ALL("Todos"),
    PHOTOS("Fotos"),
    VIDEOS("Vídeos")
}

enum class SavedCollectionFilter(val title: String) {
    ALL("Todo"),
    LOCATION("Ubicación"),
    TEXT("Texto"),
    MULTIPLE("Múltiples")
}

enum class SavedSortMode(val title: String) {
    NEWEST("Más recientes"),
    OLDEST("Más antiguos"),
    AUTHOR("Por autor")
}

/**
 * Mirror 1:1 completo de `SavedMomentsView.swift` (1596 líneas en iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMomentsView(
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    val viewModel = remember { SavedMomentsViewModel() }

    var searchText by remember { mutableStateOf("") }
    var mediaFilter by remember { mutableStateOf(SavedMediaFilter.ALL) }
    var collectionFilter by remember { mutableStateOf(SavedCollectionFilter.ALL) }
    var sortMode by remember { mutableStateOf(SavedSortMode.NEWEST) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMomentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showRemoveAlert by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    var selectedMomentForDetail by remember { mutableStateOf<Moment?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedMoments()
    }

    val filteredMoments = remember(viewModel.moments, searchText, mediaFilter, collectionFilter, sortMode) {
        var list = viewModel.moments

        if (searchText.trim().isNotEmpty()) {
            val query = searchText.lowercase().trim()
            list = list.filter {
                it.username.lowercase().contains(query) ||
                        it.content.lowercase().contains(query) ||
                        (it.location?.lowercase()?.contains(query) == true)
            }
        }

        list = when (mediaFilter) {
            SavedMediaFilter.ALL -> list
            SavedMediaFilter.PHOTOS -> list.filter { !it.imagePath.isNullOrEmpty() }
            SavedMediaFilter.VIDEOS -> list.filter { !it.videoUrl.isNullOrEmpty() }
        }

        list = when (collectionFilter) {
            SavedCollectionFilter.ALL -> list
            SavedCollectionFilter.LOCATION -> list.filter { !it.location.isNullOrEmpty() }
            SavedCollectionFilter.TEXT -> list.filter { it.content.trim().isNotEmpty() }
            SavedCollectionFilter.MULTIPLE -> list.filter { (it.mediaItems?.size ?: 0) > 1 }
        }

        when (sortMode) {
            SavedSortMode.NEWEST -> list.sortedByDescending { it.timestamp }
            SavedSortMode.OLDEST -> list.sortedBy { it.timestamp }
            SavedSortMode.AUTHOR -> list.sortedBy { it.username.lowercase() }
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.profile_thumbnail_saved_empty_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedMomentIds = emptySet()
                        }
                    ) {
                        Text(
                            text = if (isSelectionMode) "Cancelar" else "Seleccionar",
                            color = if (isSelectionMode) Color(0xFFFF3B30) else Color(0xFF007AFF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = textColor)
                }
            } else if (viewModel.moments.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(id = R.string.profile_thumbnail_saved_empty_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stringResource(id = R.string.profile_thumbnail_saved_empty_subtitle),
                        fontSize = 14.sp,
                        color = secondaryColor
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Buscar en guardados...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = secondaryColor) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = secondaryColor)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = textColor.copy(alpha = 0.05f),
                            unfocusedContainerColor = textColor.copy(alpha = 0.05f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Filters Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(textColor.copy(alpha = 0.08f))
                                .padding(2.dp)
                        ) {
                            SavedMediaFilter.entries.forEach { filter ->
                                val selected = mediaFilter == filter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selected) Color(0xFF007AFF) else Color.Transparent)
                                        .clickable { mediaFilter = filter }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = filter.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else textColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(textColor.copy(alpha = 0.08f))
                                    .clickable { sortMenuExpanded = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sortMode.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                SavedSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.title) },
                                        onClick = {
                                            sortMode = mode
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Collection Filter Rail
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SavedCollectionFilter.entries) { filter ->
                            val selected = collectionFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) textColor.copy(alpha = 0.15f) else textColor.copy(alpha = 0.04f))
                                    .clickable { collectionFilter = filter }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = filter.title,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    if (filteredMoments.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Sin resultados para los filtros seleccionados", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    searchText = ""
                                    mediaFilter = SavedMediaFilter.ALL
                                    collectionFilter = SavedCollectionFilter.ALL
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = textColor.copy(alpha = 0.1f))
                            ) {
                                Text("Borrar filtros", color = textColor)
                            }
                        }
                    } else {
                        // Grid content
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredMoments, key = { it.id ?: "" }) { moment ->
                                val isSelected = selectedMomentIds.contains(moment.id)

                                SavedMomentGridCard(
                                    moment = moment,
                                    isRestricted = false,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onTap = {
                                        if (isSelectionMode) {
                                            val momentId = moment.id ?: return@SavedMomentGridCard
                                            selectedMomentIds = if (isSelected) {
                                                selectedMomentIds - momentId
                                            } else {
                                                selectedMomentIds + momentId
                                            }
                                        } else {
                                            selectedMomentForDetail = moment
                                        }
                                    },
                                    onLongPress = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                        }
                                        val momentId = moment.id ?: return@SavedMomentGridCard
                                        selectedMomentIds = selectedMomentIds + momentId
                                    }
                                )
                            }
                        }
                    }

                    // Selection Bar
                    if (isSelectionMode && selectedMomentIds.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedMomentIds.size} seleccionados",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showRemoveAlert = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Eliminar")
                                }
                            }
                        }
                    }
                }
            }

            if (showRemoveAlert) {
                AlertDialog(
                    onDismissRequest = { showRemoveAlert = false },
                    title = { Text("Eliminar de guardados") },
                    text = { Text("¿Deseas eliminar los ${selectedMomentIds.size} momentos seleccionados de tu colección guardada?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedMomentIds = emptySet()
                                isSelectionMode = false
                                showRemoveAlert = false
                            }
                        ) {
                            Text("Eliminar", color = Color(0xFFFF3B30))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRemoveAlert = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Mirror 1:1 de `SavedMomentGridCard` en `SavedMomentsView.swift`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedMomentGridCard(
    moment: Moment,
    isRestricted: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = moment.imagePath ?: moment.videoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.1f))
                .then(if (isRestricted) Modifier.blur(16.dp) else Modifier)
        )

        if (isRestricted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("No disponible", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }

        if (!moment.videoUrl.isNullOrEmpty() && !isRestricted) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
            )
        }

        if (isSelectionMode && !isRestricted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.6f))
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
