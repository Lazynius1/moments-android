package com.moments.android.views.creator.audienceselector

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.CustomAudienceList
import com.moments.android.notifications.services.InAppActionToast
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createCustomAudienceList
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchNewConversationSuggestions
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.firestore.updateCustomAudienceList
import com.moments.android.services.firestore.MAX_CUSTOM_AUDIENCE_MEMBERS
import com.moments.android.services.storage.StorageService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.creator.EmojiPickerView
import com.moments.android.views.profile.editor.sections.ProfileLibraryCropEntryView
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val CanvasDark = Color(0xFF0B1215)
private val CanvasLight = Color(0xFFFAF9F6)
private val AudienceBlue = Color(0xFF007AFF)
private val AudienceTeal = Color(0xFF00A896)

/** Port de `AudienceSelectionView.FlowDestination`. */
private sealed class FlowDestination {
    data object Main : FlowDestination()
    data object CustomPeople : FlowDestination()
    data object ManageLists : FlowDestination()
    data class CreateList(val returnToManageLists: Boolean) : FlowDestination()
    data class EditList(val list: CustomAudienceList) : FlowDestination()
}

/**
 * Port de `AudienceSelectionView.swift` (MARK principal + Create/Edit/Picker/cards/VMs).
 */
@Composable
fun AudienceSelectionView(
    selectedAudience: ContentAudience,
    selectedListId: String?,
    selectedListName: String?,
    customSelectedUsers: List<String>,
    onSelectedAudienceChange: (ContentAudience) -> Unit,
    onSelectedListIdChange: (String?) -> Unit,
    onSelectedListNameChange: (String?) -> Unit,
    onCustomSelectedUsersChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) CanvasDark else CanvasLight
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var flowDestination by remember { mutableStateOf<FlowDestination>(FlowDestination.Main) }
    var navigatingForward by remember { mutableStateOf(true) }
    var customLists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var isLoadingLists by remember { mutableStateOf(true) }
    var selectedUsersForCustom by remember { mutableStateOf<List<AppUser>>(emptyList()) }

    fun navigate(to: FlowDestination, forward: Boolean = true) {
        navigatingForward = forward
        flowDestination = to
    }

    fun reloadLists() {
        scope.launch {
            isLoadingLists = true
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            customLists = if (uid != null) {
                withContext(Dispatchers.IO) {
                    runCatching { FirestoreService().fetchCustomLists(uid) }.getOrDefault(emptyList())
                }
            } else emptyList()
            isLoadingLists = false
        }
    }

    fun showSaveFeedback() {
        InAppNotificationService.showActionToast(
            InAppActionToast.create(prefix = context.getString(R.string.audience_saved)),
        )
    }

    fun resetSelection() {
        onSelectedListIdChange(null)
        onSelectedListNameChange(null)
        onCustomSelectedUsersChange(emptyList())
    }

    LaunchedEffect(Unit) {
        reloadLists()
        if (customSelectedUsers.isNotEmpty()) {
            selectedUsersForCustom = withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchUsers(customSelectedUsers) }.getOrDefault(emptyList())
            }
        }
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        AnimatedContent(
            targetState = flowDestination,
            transitionSpec = {
                val springSpec = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                val offsetSpringSpec = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                if (navigatingForward) {
                    (slideInHorizontally(offsetSpringSpec) { it } + fadeIn(springSpec)) togetherWith
                        (slideOutHorizontally(offsetSpringSpec) { -it / 3 } + fadeOut(springSpec))
                } else {
                    (slideInHorizontally(offsetSpringSpec) { -it } + fadeIn(springSpec)) togetherWith
                        (slideOutHorizontally(offsetSpringSpec) { it / 3 } + fadeOut(springSpec))
                }
            },
            label = "audienceSelectionFlow",
            modifier = Modifier.fillMaxSize(),
        ) { dest ->
            when (dest) {
                FlowDestination.Main -> AudienceSelectionMainContent(
                    content = content,
                    secondary = content.copy(alpha = 0.7f),
                    selectedAudience = selectedAudience,
                    selectedListId = selectedListId,
                    customSelectedUsers = customSelectedUsers,
                    customLists = customLists,
                    isLoadingLists = isLoadingLists,
                    onSelectPredefined = { audience ->
                        onSelectedAudienceChange(audience)
                        resetSelection()
                        showSaveFeedback()
                    },
                    onSelectList = { list ->
                        onSelectedAudienceChange(ContentAudience.CUSTOM_LIST)
                        onSelectedListIdChange(list.id)
                        onSelectedListNameChange(list.name)
                        onCustomSelectedUsersChange(emptyList())
                        showSaveFeedback()
                    },
                    onManageLists = { navigate(FlowDestination.ManageLists) },
                    onCreateList = { navigate(FlowDestination.CreateList(returnToManageLists = false)) },
                    onCustomPeople = {
                        onSelectedAudienceChange(ContentAudience.CUSTOM)
                        navigate(FlowDestination.CustomPeople)
                    },
                    onDismiss = onDismiss,
                )
                FlowDestination.CustomPeople -> CustomAudienceSelector(
                    selectedUsers = selectedUsersForCustom,
                    onSelectedUsersChange = { selectedUsersForCustom = it },
                    onComplete = {
                        onCustomSelectedUsersChange(selectedUsersForCustom.map { it.id })
                        navigate(FlowDestination.Main, forward = false)
                    },
                    onBack = {
                        val updatedIds = selectedUsersForCustom.map { it.id }
                        val didChange = updatedIds.toSet() != customSelectedUsers.toSet()
                        onCustomSelectedUsersChange(updatedIds)
                        onSelectedAudienceChange(ContentAudience.CUSTOM)
                        onSelectedListIdChange(null)
                        onSelectedListNameChange(null)
                        if (didChange) showSaveFeedback()
                        navigate(FlowDestination.Main, forward = false)
                    },
                    embeddedInFlow = true,
                )
                FlowDestination.ManageLists -> CustomAudienceListsView(
                    embeddedInFlow = true,
                    onBack = {
                        reloadLists()
                        navigate(FlowDestination.Main, forward = false)
                    },
                    onCreateList = { navigate(FlowDestination.CreateList(returnToManageLists = true)) },
                    onEditList = { navigate(FlowDestination.EditList(it)) },
                    onListsChanged = { reloadLists() },
                )
                is FlowDestination.CreateList -> CreateCustomListView(
                    embeddedInFlow = true,
                    onBack = {
                        navigate(
                            if (dest.returnToManageLists) FlowDestination.ManageLists else FlowDestination.Main,
                            forward = false,
                        )
                    },
                    onCompleted = {
                        reloadLists()
                        navigate(
                            if (dest.returnToManageLists) FlowDestination.ManageLists else FlowDestination.Main,
                            forward = false,
                        )
                    },
                )
                is FlowDestination.EditList -> EditCustomListView(
                    list = dest.list,
                    embeddedInFlow = true,
                    onBack = { navigate(FlowDestination.ManageLists, forward = false) },
                    onCompleted = {
                        reloadLists()
                        navigate(FlowDestination.ManageLists, forward = false)
                    },
                )
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val keepName = selectedListName
    }
}

@Composable
private fun AudienceSelectionMainContent(
    content: Color,
    secondary: Color,
    selectedAudience: ContentAudience,
    selectedListId: String?,
    customSelectedUsers: List<String>,
    customLists: List<CustomAudienceList>,
    isLoadingLists: Boolean,
    onSelectPredefined: (ContentAudience) -> Unit,
    onSelectList: (CustomAudienceList) -> Unit,
    onManageLists: () -> Unit,
    onCreateList: () -> Unit,
    onCustomPeople: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                // El handle del MomentsModalSheet ya aporta el margen superior.
                // Mantener aquí 20.dp separaba demasiado el título en Android.
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.audience_selection_title),
                color = content,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { legacyPoppinsSize(context, 24).toSp() },
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.audience_selection_subtitle),
                color = secondary,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // predefinedAudienceSection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.audience_predefined),
                        color = content.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            ContentAudience.EVERYONE,
                            ContentAudience.MUTUALS,
                            ContentAudience.BEST_FRIENDS,
                            ContentAudience.ONLY_ME,
                        ).forEach { audience ->
                            AudienceGridCard(
                                audience = audience,
                                isSelected = selectedAudience == audience,
                                onTap = { onSelectPredefined(audience) },
                            )
                        }
                    }
                }

                // customListsSection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.audience_custom_lists),
                            color = content.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.audience_manage),
                            color = content,
                            fontWeight = FontWeight.Medium,
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            modifier = Modifier.clickable(onClick = onManageLists),
                        )
                    }
                    when {
                        isLoadingLists -> Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.audience_loadingLists),
                                color = content.copy(alpha = 0.6f),
                                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            )
                        }
                        customLists.isEmpty() -> EmptyCustomListsViewModern(content = content, onCreateList = onCreateList)
                        else -> LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            item {
                                Column(
                                    Modifier
                                        .width(100.dp)
                                        .height(140.dp)
                                        .clickable(onClick = onCreateList)
                                        .padding(top = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier.size(48.dp).momentsChromeGlass(CircleShape, interactive = true),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        stringResource(R.string.audience_create),
                                        color = content,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                                    )
                                }
                            }
                            items(customLists, key = { it.id ?: it.name }) { list ->
                                CustomListCard(
                                    list = list,
                                    isSelected = selectedAudience == ContentAudience.CUSTOM_LIST && selectedListId == list.id,
                                    onTap = { onSelectList(list) },
                                )
                            }
                        }
                    }
                }

                // manualSelectionSection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.audience_manualSelection),
                        color = content.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    val isCustomPeopleSelected = selectedAudience == ContentAudience.CUSTOM && selectedListId == null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCustomPeople)
                            .padding(horizontal = 2.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            AudienceIconView(
                                audience = ContentAudience.CUSTOM,
                                size = AudienceIconMetrics.gridCardEmphasis,
                                modifier = Modifier.graphicsLayer { alpha = if (isCustomPeopleSelected) 1f else 0.42f },
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.audience_custom),
                                color = content.copy(alpha = if (isCustomPeopleSelected) 1f else 0.82f),
                                fontWeight = if (isCustomPeopleSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                            )
                            Text(
                                if (customSelectedUsers.isEmpty()) {
                                    stringResource(R.string.audience_description_custom)
                                } else {
                                    stringResource(R.string.audience_people_count, customSelectedUsers.size)
                                },
                                color = content.copy(alpha = 0.55f * if (isCustomPeopleSelected) 1f else 0.72f),
                                fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                            )
                        }
                        if (isCustomPeopleSelected) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(
                                        if (isSystemInDarkTheme()) Color.White.copy(0.14f) else Color.Black.copy(0.08f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(13.dp))
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = content.copy(alpha = 0.55f),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
        }
        // Cerrar: iOS usa dismiss del sheet desde el host; aquí botón implícito en host.
        // Mantenemos onDismiss disponible vía back del sistema / host.
        @Suppress("UNUSED_VARIABLE")
        val dismissHook = onDismiss
    }
}

@Composable
private fun EmptyCustomListsViewModern(content: Color, onCreateList: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        if (dark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                        if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.04f),
                    ),
                ),
                RoundedCornerShape(16.dp),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AudienceIconView(ContentAudience.CUSTOM_LIST, AudienceIconMetrics.gridCardEmphasis)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.audience_noCustomLists_title),
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
            )
            Text(
                stringResource(R.string.audience_noCustomLists_description),
                color = content.copy(0.6f),
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier.clickable(onClick = onCreateList),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(30.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(R.string.audience_createFirstList),
                color = content,
                fontWeight = FontWeight.Medium,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
            )
        }
    }
}

// MARK: - Crear Nueva Lista (CreateCustomListView.swift section)

@Composable
fun CreateCustomListView(
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onCompleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listName by remember { mutableStateOf("") }
    var listDescription by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(CustomAudienceList.predefinedColors.first()) }
    var selectedIcon by remember { mutableStateOf(CustomAudienceList.predefinedIcons.first()) }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var showingMemberPicker by remember { mutableStateOf(false) }
    var showingEmojiPicker by remember { mutableStateOf(false) }
    var showingImageCrop by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val tint = Color.fromHex(selectedColor)

    if (embeddedInFlow && showingMemberPicker) {
        MemberPickerView(
            selectedMembers = selectedMembers,
            onSelectedMembersChange = { selectedMembers = it },
            embeddedInFlow = true,
            onBack = { showingMemberPicker = false },
            modifier = modifier,
        )
        return
    }

    if (!embeddedInFlow && showingMemberPicker) {
        Dialog(
            onDismissRequest = { showingMemberPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            MemberPickerView(
                selectedMembers = selectedMembers,
                onSelectedMembersChange = { selectedMembers = it },
                embeddedInFlow = false,
                onBack = { showingMemberPicker = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showingEmojiPicker) {
        // Igual que las reacciones de comentarios: sheet grande, pero nunca fullscreen.
        MomentsModalSheet(
            onDismissRequest = { showingEmojiPicker = false },
            largeOnly = false,
            containerColor = if (dark) CanvasDark else CanvasLight,
            expandedHeightFraction = 0.78f,
        ) { dismiss ->
            EmojiPickerView(
                onDismiss = dismiss,
                onSelect = { emoji ->
                    selectedIcon = emoji
                    selectedImage = null
                    showingEmojiPicker = false
                    HapticManager.shared.lightImpact()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showingImageCrop) {
        Dialog(
            onDismissRequest = { showingImageCrop = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ProfileLibraryCropEntryView(
                onImageCropped = { bitmap ->
                    selectedImage = bitmap
                    showingImageCrop = false
                    HapticManager.shared.lightImpact()
                },
                onDismiss = { showingImageCrop = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            EmbeddedFlowHeader(
                title = stringResource(R.string.audience_create_action),
                subtitle = stringResource(R.string.audience_custom_lists),
                content = content,
                onBack = { onBack?.invoke() },
            )
            ListHeroPreview(
                name = listName.ifEmpty { stringResource(R.string.audience_list_placeholder) },
                memberCount = selectedMembers.size,
                colorHex = selectedColor,
                icon = selectedIcon,
                localImage = selectedImage,
                isUploadingImage = isLoading && selectedImage != null,
                content = content,
                heroSize = 86.dp,
                iconSize = 36.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                ListNameDescriptionFields(
                    listName = listName,
                    onNameChange = { listName = it },
                    listDescription = listDescription,
                    onDescriptionChange = { listDescription = it },
                    content = content,
                )
                PersonalizationPickers(
                    selectedColor = selectedColor,
                    selectedIcon = selectedIcon,
                    onColor = {
                        selectedColor = it
                        HapticManager.shared.lightImpact()
                    },
                    onIcon = {
                        selectedIcon = it
                        selectedImage = null
                        HapticManager.shared.lightImpact()
                    },
                    localImage = selectedImage,
                    imagePath = null,
                    onEmoji = { showingEmojiPicker = true },
                    onPhoto = { showingImageCrop = true },
                    content = content,
                )
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.audience_members),
                            color = content,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.clickable { showingMemberPicker = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(stringResource(R.string.audience_view_all), color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Box(Modifier.size(20.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = content, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                    SuggestedMembersCarousel(
                        selectedMembers = selectedMembers,
                        onSelectedMembersChange = { selectedMembers = it },
                    )
                }
                val canCreate = listName.isNotBlank() && !isLoading
                Box(
                    Modifier
                        .fillMaxWidth()
                        .shadow(if (canCreate) 15.dp else 0.dp, RoundedCornerShape(24.dp), spotColor = tint.copy(0.3f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (canCreate) Brush.linearGradient(listOf(tint, tint.copy(0.8f)))
                            else Brush.linearGradient(listOf(Color.Gray.copy(0.3f), Color.Gray.copy(0.3f))),
                        )
                        .clickable(enabled = canCreate) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@clickable
                            isLoading = true
                            scope.launch {
                                var uploadedPath: String? = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val listId = UUID.randomUUID().toString()
                                        uploadedPath = selectedImage?.let {
                                            StorageService.uploadAudienceListImage(uid, listId, it)
                                        }
                                        FirestoreService().createCustomAudienceList(
                                            userId = uid,
                                            name = listName,
                                            description = listDescription,
                                            members = selectedMembers.toList(),
                                            color = selectedColor,
                                            icon = selectedIcon,
                                            imagePath = uploadedPath,
                                            listId = listId,
                                        )
                                    }
                                }.onSuccess {
                                    isLoading = false
                                    InAppNotificationService.showActionToast(
                                        InAppActionToast.create(prefix = context.getString(R.string.audience_saved)),
                                    )
                                    onCompleted?.invoke()
                                }.onFailure { error ->
                                    uploadedPath?.let { path ->
                                        runCatching { withContext(Dispatchers.IO) { StorageService.deleteMedia(path) } }
                                    }
                                    isLoading = false
                                    InAppNotificationService.showActionToast(
                                        InAppActionToast.create(prefix = error.localizedMessage ?: error.message.orEmpty()),
                                    )
                                }
                            }
                        }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AddCircle, null, tint = if (canCreate) Color.White else Color.Gray)
                            Text(
                                stringResource(R.string.audience_create_action),
                                color = if (canCreate) Color.White else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Carousel de Miembros Sugeridos

@Composable
fun SuggestedMembersCarousel(
    selectedMembers: Set<String>,
    onSelectedMembersChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    var suggestedUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            isLoading = false
            return@LaunchedEffect
        }
        suggestedUsers = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().fetchMutuals(uid).take(10) }.getOrDefault(emptyList())
        }
        isLoading = false
    }
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isLoading) {
            items(5) {
                Box(
                    Modifier
                        .size(60.dp)
                        .background(
                            if (dark) CanvasLight.copy(0.06f) else CanvasDark.copy(0.05f),
                            CircleShape,
                        ),
                )
            }
        } else {
            items(suggestedUsers, key = { it.id }) { user ->
                SuggestedUserCircle(
                    user = user,
                    isSelected = user.id in selectedMembers,
                    onToggle = {
                        onSelectedMembersChange(
                            if (user.id in selectedMembers) selectedMembers - user.id
                            else selectedMembers + user.id,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun SuggestedUserCircle(user: AppUser, isSelected: Boolean, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Box {
            Box(
                Modifier
                    .size(60.dp)
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) AudienceTeal else Color.Transparent, CircleShape),
            ) {
                UserAvatarCircle(user = user, size = 60.dp)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(18.dp)
                    .background(if (isSelected) AudienceTeal else Color.White, CircleShape)
                    .then(if (!isSelected) Modifier.shadow(2.dp, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSelected) Icons.Filled.Check else Icons.Filled.Add,
                    null,
                    tint = if (isSelected) Color.White else Color.Black,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
        Text(
            user.username,
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
        )
    }
}

// MARK: - Editar Lista

@Composable
fun EditCustomListView(
    list: CustomAudienceList,
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onCompleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listName by remember { mutableStateOf(list.name) }
    var listDescription by remember { mutableStateOf(list.description.orEmpty()) }
    var selectedColor by remember {
        mutableStateOf(list.color ?: CustomAudienceList.predefinedColors.first())
    }
    var selectedIcon by remember {
        mutableStateOf(list.icon ?: CustomAudienceList.predefinedIcons.first())
    }
    var selectedMembers by remember { mutableStateOf(list.members.toSet()) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImagePath by remember { mutableStateOf(list.imagePath) }
    var persistedImagePath by remember { mutableStateOf(list.imagePath) }
    var persistedName by remember { mutableStateOf(list.name) }
    var persistedDescription by remember { mutableStateOf(list.description.orEmpty()) }
    var persistedColor by remember { mutableStateOf(list.color ?: CustomAudienceList.predefinedColors.first()) }
    var persistedIcon by remember { mutableStateOf(list.icon ?: CustomAudienceList.predefinedIcons.first()) }
    var persistedMembers by remember { mutableStateOf(list.members.toSet()) }
    var showingMemberPicker by remember { mutableStateOf(false) }
    var showingEmojiPicker by remember { mutableStateOf(false) }
    var showingImageCrop by remember { mutableStateOf(false) }
    var currentMembers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var filteredMembers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isLoadingMembers by remember { mutableStateOf(false) }
    var visibleMembersLimit by remember { mutableIntStateOf(12) }
    var isLoading by remember { mutableStateOf(false) }
    val membersPageSize = 12
    fun persistSnapshot() {
        persistedName = listName
        persistedDescription = listDescription
        persistedColor = selectedColor
        persistedIcon = selectedIcon
        persistedMembers = selectedMembers
        persistedImagePath = selectedImagePath
    }

    fun hasUnsavedChanges(): Boolean =
        listName != persistedName ||
            listDescription != persistedDescription ||
            selectedColor != persistedColor ||
            selectedIcon != persistedIcon ||
            selectedMembers != persistedMembers ||
            selectedImage != null ||
            selectedImagePath != persistedImagePath

    fun saveList(
        successMessageRes: Int = R.string.audience_saved,
        onSuccess: () -> Unit,
    ) {
        if (isLoading) return
        if (!hasUnsavedChanges()) {
            onSuccess()
            return
        }
        if (listName.isBlank()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val listId = list.id ?: return
        isLoading = true
        scope.launch {
            var uploadedPath: String? = null
            var resolvedImagePath = selectedImagePath
            runCatching {
                withContext(Dispatchers.IO) {
                    selectedImage?.let { bitmap ->
                        uploadedPath = StorageService.uploadAudienceListImage(uid, listId, bitmap)
                        resolvedImagePath = uploadedPath
                    }
                    FirestoreService().updateCustomAudienceList(
                        userId = uid,
                        listId = listId,
                        name = listName,
                        description = listDescription,
                        members = selectedMembers.toList(),
                        color = selectedColor,
                        icon = selectedIcon,
                        imagePath = resolvedImagePath,
                    )
                    if (persistedImagePath != null && persistedImagePath != resolvedImagePath) {
                        runCatching { StorageService.deleteMedia(persistedImagePath.orEmpty()) }
                    }
                }
            }.onSuccess {
                isLoading = false
                selectedImage = null
                selectedImagePath = resolvedImagePath
                persistSnapshot()
                InAppNotificationService.showActionToast(
                    InAppActionToast.create(prefix = context.getString(successMessageRes)),
                )
                onSuccess()
            }.onFailure { error ->
                uploadedPath?.let { path ->
                    scope.launch(Dispatchers.IO) { runCatching { StorageService.deleteMedia(path) } }
                }
                isLoading = false
                InAppNotificationService.showActionToast(
                    InAppActionToast.create(prefix = error.localizedMessage ?: error.message.orEmpty()),
                )
            }
        }
    }

    suspend fun removeMembers(users: List<AppUser>): Result<Unit> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val listId = list.id
            ?: return Result.failure(IllegalStateException("Missing list id"))
        val removedIds = users.map { it.id }.toSet()
        val updated = selectedMembers - removedIds
        var uploadedPath: String? = null
        var resolvedImagePath = selectedImagePath
        val result = runCatching {
            withContext(Dispatchers.IO) {
                selectedImage?.let { bitmap ->
                    uploadedPath = StorageService.uploadAudienceListImage(uid, listId, bitmap)
                    resolvedImagePath = uploadedPath
                }
                FirestoreService().updateCustomAudienceList(
                    userId = uid,
                    listId = listId,
                    name = listName,
                    description = listDescription,
                    members = updated.toList(),
                    color = selectedColor,
                    icon = selectedIcon,
                    imagePath = resolvedImagePath,
                )
                if (persistedImagePath != null && persistedImagePath != resolvedImagePath) {
                    runCatching { StorageService.deleteMedia(persistedImagePath.orEmpty()) }
                }
            }
        }
        if (result.isSuccess) {
            selectedImage = null
            selectedImagePath = resolvedImagePath
            selectedMembers = updated
            persistSnapshot()
        } else {
            uploadedPath?.let { path ->
                withContext(Dispatchers.IO) { runCatching { StorageService.deleteMedia(path) } }
            }
        }
        return result
    }

    fun reloadMembers() {
        scope.launch {
            if (selectedMembers.isEmpty()) {
                currentMembers = emptyList()
                filteredMembers = emptyList()
                visibleMembersLimit = membersPageSize
                return@launch
            }
            isLoadingMembers = true
            currentMembers = withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchUsers(selectedMembers.toList()) }.getOrDefault(emptyList())
            }
            filteredMembers = currentMembers
            visibleMembersLimit = membersPageSize
            isLoadingMembers = false
        }
    }

    LaunchedEffect(Unit) { reloadMembers() }
    LaunchedEffect(showingMemberPicker) {
        if (!showingMemberPicker) reloadMembers()
    }

    if (embeddedInFlow && showingMemberPicker) {
        MemberPickerView(
            selectedMembers = selectedMembers,
            onSelectedMembersChange = { selectedMembers = it },
            existingMemberIDs = persistedMembers,
            listName = listName,
            embeddedInFlow = true,
            onBack = {
                saveList(successMessageRes = R.string.audience_list_updated) {
                    showingMemberPicker = false
                }
            },
            onRemoveMembers = ::removeMembers,
            modifier = modifier,
        )
        return
    }

    if (!embeddedInFlow && showingMemberPicker) {
        Dialog(
            onDismissRequest = { showingMemberPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            MemberPickerView(
                selectedMembers = selectedMembers,
                onSelectedMembersChange = { selectedMembers = it },
                existingMemberIDs = persistedMembers,
                listName = listName,
                embeddedInFlow = false,
                onBack = {
                    saveList(successMessageRes = R.string.audience_list_updated) {
                        showingMemberPicker = false
                    }
                },
                onRemoveMembers = ::removeMembers,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showingEmojiPicker) {
        // Reutiliza el mismo sheet grande y sólido de comentarios/reacciones.
        MomentsModalSheet(
            onDismissRequest = { showingEmojiPicker = false },
            largeOnly = false,
            containerColor = if (dark) CanvasDark else CanvasLight,
            expandedHeightFraction = 0.78f,
        ) { dismiss ->
            EmojiPickerView(
                onDismiss = dismiss,
                onSelect = { emoji ->
                    selectedIcon = emoji
                    selectedImage = null
                    selectedImagePath = null
                    showingEmojiPicker = false
                    HapticManager.shared.lightImpact()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showingImageCrop) {
        Dialog(
            onDismissRequest = { showingImageCrop = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ProfileLibraryCropEntryView(
                onImageCropped = { bitmap ->
                    selectedImage = bitmap
                    selectedImagePath = null
                    showingImageCrop = false
                    HapticManager.shared.lightImpact()
                },
                onDismiss = { showingImageCrop = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        EmbeddedFlowHeader(
            title = stringResource(R.string.common_edit),
            subtitle = list.name,
            content = content,
            onBack = { saveList { onCompleted?.invoke() ?: onBack?.invoke() } },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            ListHeroPreview(
            name = listName.ifEmpty { stringResource(R.string.audience_list_placeholder) },
            memberCount = selectedMembers.size,
            colorHex = selectedColor,
            icon = selectedIcon,
            imagePath = selectedImagePath,
            localImage = selectedImage,
            isUploadingImage = isLoading && selectedImage != null,
            content = content,
            heroSize = 76.dp,
            iconSize = 32.dp,
        )
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            ListNameDescriptionFields(
                listName = listName,
                onNameChange = { listName = it },
                listDescription = listDescription,
                onDescriptionChange = { listDescription = it },
                content = content,
            )
            PersonalizationPickers(
                selectedColor = selectedColor,
                selectedIcon = selectedIcon,
                onColor = {
                    selectedColor = it
                    HapticManager.shared.lightImpact()
                },
                onIcon = {
                    selectedIcon = it
                    selectedImage = null
                    selectedImagePath = null
                    HapticManager.shared.lightImpact()
                },
                imagePath = selectedImagePath,
                localImage = selectedImage,
                onEmoji = { showingEmojiPicker = true },
                onPhoto = { showingImageCrop = true },
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().clickable { showingMemberPicker = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.audience_picker_title), color = content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(stringResource(R.string.audience_members_count_long, selectedMembers.size), color = content.copy(0.6f), fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = content.copy(0.6f), modifier = Modifier.size(20.dp))
            }
            }
        }
    }
}

// MARK: - Fila de Miembro con Opción de Eliminar

@Composable
fun MemberRowWithRemove(user: AppUser, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    var showingRemoveAlert by remember { mutableStateOf(false) }
    if (showingRemoveAlert) {
        AlertDialog(
            onDismissRequest = { showingRemoveAlert = false },
            title = { Text(stringResource(R.string.audience_list_deleteMember_title)) },
            text = { Text(stringResource(R.string.audience_list_deleteMember_message, user.username)) },
            confirmButton = {
                TextButton(onClick = {
                    showingRemoveAlert = false
                    onRemove()
                }) { Text(stringResource(R.string.common_delete), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showingRemoveAlert = false }) {
                    Text(stringResource(R.string.audience_actions_cancel))
                }
            },
        )
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.02f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatarCircle(user = user, size = 44.dp)
        Text(user.username, color = content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.RemoveCircle,
            null,
            tint = Color.Red.copy(0.7f),
            modifier = Modifier.size(20.dp).clickable { showingRemoveAlert = true },
        )
    }
}

// MARK: - Selector de Miembros

@Composable
fun MemberPickerView(
    selectedMembers: Set<String>,
    onSelectedMembersChange: (Set<String>) -> Unit,
    existingMemberIDs: Set<String> = emptySet(),
    listName: String? = null,
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onRemoveMembers: (suspend (List<AppUser>) -> Result<Unit>)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) CanvasDark else CanvasLight
    val content = if (dark) Color.White else Color.Black
    val secondary = content.copy(alpha = if (dark) 0.65f else 0.62f)
    val context = LocalContext.current
    var currentExistingIds by remember(existingMemberIDs) { mutableStateOf(existingMemberIDs) }
    var showingManageMembers by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var suggestedUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(true) }
    val hasSearched = searchText.trim().length >= 2

    LaunchedEffect(selectedMembers) {
        currentExistingIds = currentExistingIds.intersect(selectedMembers)
    }

    LaunchedEffect(currentExistingIds) {
        isLoadingSuggestions = true
        suggestedUsers = withContext(Dispatchers.IO) {
            runCatching {
                FirestoreService().fetchNewConversationSuggestions(recentPartnerIds = emptyList())
            }.getOrDefault(emptyList()).filterNot { it.id in currentExistingIds }
        }
        isLoadingSuggestions = false
    }

    LaunchedEffect(searchText, currentExistingIds) {
        val query = searchText.trim()
        if (query.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        searchResults = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(query, limit = 20) }
                .getOrDefault(emptyList())
                .filterNot { it.id in currentExistingIds }
        }
        isSearching = false
    }

    fun toggle(user: AppUser) {
        if (user.id in selectedMembers) {
            onSelectedMembersChange(selectedMembers - user.id)
        } else if (selectedMembers.size < MAX_CUSTOM_AUDIENCE_MEMBERS) {
            onSelectedMembersChange(selectedMembers + user.id)
        } else {
            InAppNotificationService.showActionToast(
                InAppActionToast.create(prefix = context.getString(R.string.audience_members_limit, MAX_CUSTOM_AUDIENCE_MEMBERS)),
            )
        }
    }

    if (showingManageMembers && listName != null) {
        AudienceManageMembersView(
            selectedMembers = selectedMembers,
            memberIDs = currentExistingIds.intersect(selectedMembers),
            listName = listName,
            onSelectedMembersChange = onSelectedMembersChange,
            onBack = { showingManageMembers = false },
            onRemoveMembers = onRemoveMembers,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(if (!embeddedInFlow) canvas else Color.Transparent)) {
        MemberPickerHeader(
            title = stringResource(R.string.audience_picker_title),
            subtitle = stringResource(R.string.audience_members),
            content = content,
            secondary = secondary,
            onBack = { onBack?.invoke() },
        )

        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = content, fontSize = 16.sp),
                cursorBrush = SolidColor(content),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(stringResource(R.string.audience_picker_searchPlaceholder), color = secondary, fontSize = 16.sp)
                    }
                    inner()
                },
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    tint = secondary,
                    modifier = Modifier.size(18.dp).clickable { searchText = "" },
                )
            }
        }

        if (listName != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showingManageMembers = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Group, null, tint = content, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.audience_manage_members), color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(stringResource(R.string.audience_members_count_long, currentExistingIds.intersect(selectedMembers).size), color = secondary, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondary, modifier = Modifier.size(20.dp))
            }
        }

        when {
            isSearching || (!hasSearched && isLoadingSuggestions) -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MomentsCircularProgressIndicator()
                    Text(stringResource(R.string.common_searching), color = secondary, fontSize = 16.sp)
                }
            }
            hasSearched && searchResults.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(40.dp)) {
                    Icon(Icons.Filled.Person, null, tint = secondary, modifier = Modifier.size(50.dp))
                    Text(stringResource(R.string.common_no_results), color = content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(stringResource(R.string.audience_picker_noResultsDescription, searchText), color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            else -> {
                val users = if (hasSearched) searchResults else suggestedUsers
                if (users.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(40.dp)) {
                            Icon(Icons.Filled.Person, null, tint = secondary, modifier = Modifier.size(50.dp))
                            Text(stringResource(R.string.audience_picker_initialTitle), color = content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text(stringResource(R.string.audience_picker_initialDescription), color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!hasSearched) {
                            item {
                                Text(
                                    stringResource(R.string.messaging_new_suggestions),
                                    color = secondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(users, key = { it.id }) { user ->
                            AudienceMemberSelectionRow(
                                user = user,
                                isSelected = user.id in selectedMembers,
                                isSelectionEnabled = true,
                                onToggle = { toggle(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberPickerHeader(
    title: String,
    subtitle: String,
    content: Color,
    secondary: Color,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = content, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(subtitle, color = secondary, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun AudienceManageMembersView(
    selectedMembers: Set<String>,
    memberIDs: Set<String>,
    listName: String,
    onSelectedMembersChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onRemoveMembers: (suspend (List<AppUser>) -> Result<Unit>)?,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val secondary = content.copy(alpha = 0.62f)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var selectedForRemoval by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isRemoving by remember { mutableStateOf(false) }
    var showingConfirmation by remember { mutableStateOf(false) }
    val selectedUsers = members.filter { it.id in selectedForRemoval }

    LaunchedEffect(memberIDs) {
        members = if (memberIDs.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            runCatching { FirestoreService().fetchUsers(memberIDs.toList()) }.getOrDefault(emptyList())
        }
        isLoading = false
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.audience_manage_members), color = content, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text(stringResource(R.string.audience_members_count_short, members.size), color = secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            if (selectedForRemoval.isEmpty()) {
                Text(
                    stringResource(R.string.common_edit),
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .width(54.dp)
                        .clickable(enabled = members.isNotEmpty() && !isRemoving) { isEditing = true }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    Modifier
                        .width(54.dp)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = !isRemoving)
                        .clickable(enabled = !isRemoving) { showingConfirmation = true }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(selectedForRemoval.size.toString(), color = Color.Red, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MomentsCircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(members, key = { it.id }) { user ->
                    AudienceMemberSelectionRow(
                        user = user,
                        isSelected = user.id in selectedForRemoval,
                        isSelectionEnabled = isEditing,
                        onToggle = {
                            if (isEditing) {
                                selectedForRemoval = if (user.id in selectedForRemoval) selectedForRemoval - user.id else selectedForRemoval + user.id
                            }
                        },
                    )
                }
            }
        }
    }

    if (showingConfirmation) {
        val first = selectedUsers.firstOrNull()
        val message = when {
            first == null -> ""
            selectedUsers.size == 1 -> context.getString(R.string.audience_manage_members_remove_single, first.username, listName)
            else -> context.getString(R.string.audience_manage_members_remove_plural, first.username, selectedUsers.size - 1, listName)
        }
        AlertDialog(
            onDismissRequest = { showingConfirmation = false },
            title = { Text(stringResource(R.string.audience_manage_members_remove_title)) },
            text = { Text(message) },
            dismissButton = { TextButton(onClick = { showingConfirmation = false }) { Text(stringResource(R.string.audience_actions_cancel)) } },
            confirmButton = {
                TextButton(
                    enabled = !isRemoving,
                    onClick = {
                        showingConfirmation = false
                        isRemoving = true
                        scope.launch {
                            val result = onRemoveMembers?.invoke(selectedUsers)
                                ?: Result.failure(IllegalStateException("Missing member removal handler"))
                            result.onSuccess {
                                val removedIds = selectedForRemoval
                                onSelectedMembersChange(selectedMembers - removedIds)
                                members = members.filterNot { it.id in removedIds }
                                val subtitle = if (selectedUsers.size == 1) {
                                    context.getString(R.string.audience_list_members_removed_single, selectedUsers.first().username, listName)
                                } else {
                                    context.getString(R.string.audience_list_members_removed_plural, selectedUsers.first().username, selectedUsers.size - 1, listName)
                                }
                                selectedForRemoval = emptySet()
                                isEditing = false
                                InAppNotificationService.showActionToast(
                                    InAppActionToast.create(prefix = context.getString(R.string.audience_list_updated), subtitle = subtitle),
                                )
                            }.onFailure { error ->
                                InAppNotificationService.showActionToast(InAppActionToast.create(prefix = error.localizedMessage ?: error.message.orEmpty()))
                            }
                            isRemoving = false
                        }
                    },
                ) { Text(stringResource(R.string.common_delete), color = Color.Red) }
            },
        )
    }
}

// MARK: - Card / Fila de Usuario

@Composable
fun AudienceMemberSelectionRow(
    user: AppUser,
    isSelected: Boolean,
    isSelectionEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    val unselected = content.copy(alpha = if (dark) 0.28f else 0.20f)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionEnabled, onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatarCircle(user, 44.dp)
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(user.username, color = content, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            if (user.isVerified) VerifiedBadge(size = 13.dp)
        }
        if (isSelectionEnabled) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (isSelected) content else Color.Transparent, CircleShape)
                    .border(if (isSelected) 0.dp else 1.5.dp, unselected, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Filled.Check, null, tint = if (dark) Color.Black else Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun UserSelectionCard(user: AppUser, isSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    UserSelectionRowBase(user, isSelected, onToggle, showVerified = false, modifier)
}

@Composable
fun UserSelectionRowEnhanced(user: AppUser, isSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    UserSelectionRowBase(user, isSelected, onToggle, showVerified = true, modifier)
}

@Composable
private fun UserSelectionRowBase(
    user: AppUser,
    isSelected: Boolean,
    onToggle: () -> Unit,
    showVerified: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color.Black
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.025f))
            .border(
                1.dp,
                if (isSelected) AudienceBlue.copy(0.22f) else content.copy(0.08f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box {
            UserAvatarCircle(user, 52.dp)
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        if (isSelected) AudienceBlue.copy(0.35f) else content.copy(0.08f),
                        CircleShape,
                    ),
            )
        }
        Text(user.username, color = content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (showVerified && user.isVerified) {
            VerifiedBadge(size = 14.dp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(28.dp).momentsChromeGlass(CircleShape, interactive = true),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isSelected) Icons.Filled.Check else Icons.Filled.Add,
                null,
                tint = if (isSelected) AudienceBlue else content,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

// MARK: - Shared helpers (UI pieces used by Create/Edit)

@Composable
private fun EmbeddedFlowHeader(
    title: String,
    subtitle: String,
    content: Color,
    onBack: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isConfirming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        // El sheet ya reserva el handle; no duplicar ese aire antes del título.
        modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = content, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(subtitle, color = content.copy(0.55f), fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        if (onConfirm == null) {
            Spacer(Modifier.size(40.dp))
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer { alpha = if (confirmEnabled) 1f else 0.45f }
                    .momentsChromeGlass(CircleShape, interactive = confirmEnabled)
                    .clickable(enabled = confirmEnabled, onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ListHeroPreview(
    name: String,
    memberCount: Int,
    colorHex: String,
    icon: String,
    imagePath: String? = null,
    localImage: Bitmap? = null,
    isUploadingImage: Boolean = false,
    content: Color,
    heroSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val tint = Color.fromHex(colorHex)
    val hasImage = localImage != null || !imagePath.isNullOrBlank()
    // ≡ iOS create: círculo decorativo 60pt; edit: foto a imageSize = vidrio (76).
    val innerSize = heroSize * 0.7f
    val isCustomEmoji = !hasImage && icon !in CustomAudienceList.predefinedIcons
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(heroSize + 14.dp).background(tint.copy(0.3f), CircleShape))
            Box(
                Modifier.size(heroSize).momentsChromeGlass(CircleShape, interactive = false),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage) {
                    // Foto: llena el vidrio entero (≡ iOS imageSize del hero).
                    when {
                        localImage != null -> Image(
                            bitmap = localImage.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(heroSize).clip(CircleShape),
                        )
                        else -> AsyncImage(
                            model = imagePath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(heroSize).clip(CircleShape),
                        )
                    }
                } else {
                    Box(
                        Modifier.size(innerSize).background(tint.copy(0.1f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Emoji custom: usa todo el círculo interior (antes iconSize
                        // lo recortaba por métricas del glifo). Iconos Material: tamaño tipográfico.
                        CustomAudienceListIcon(
                            icon = icon,
                            imagePath = null,
                            tint = tint,
                            emojiFontSize = if (isCustomEmoji) {
                                (innerSize.value * 0.62f).sp
                            } else {
                                iconSize.value.sp
                            },
                            modifier = Modifier.size(if (isCustomEmoji) innerSize else iconSize),
                        )
                    }
                }
                if (isUploadingImage) {
                    Box(
                        Modifier.size(heroSize).background(Color.Black.copy(alpha = 0.38f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size((heroSize.value * 0.30f).dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        Text(name, color = content, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(stringResource(R.string.audience_members_count_short, memberCount), color = content.copy(0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ListNameDescriptionFields(
    listName: String,
    onNameChange: (String) -> Unit,
    listDescription: String,
    onDescriptionChange: (String) -> Unit,
    content: Color,
) {
    val dark = isSystemInDarkTheme()
    val fieldBg = if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.04f)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.audience_list_name), color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            AudienceTextField(listName, onNameChange, stringResource(R.string.audience_list_name_example), content, fieldBg)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.audience_list_description), color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            AudienceTextField(listDescription, onDescriptionChange, stringResource(R.string.audience_list_description_placeholder), content, fieldBg)
        }
    }
}

@Composable
private fun AudienceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    content: Color,
    fieldBg: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(fieldBg)
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = content, fontSize = 17.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(content),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = content.copy(0.4f), fontSize = 17.sp)
                inner()
            },
        )
    }
}

@Composable
private fun PersonalizationPickers(
    selectedColor: String,
    selectedIcon: String,
    onColor: (String) -> Unit,
    onIcon: (String) -> Unit,
    imagePath: String? = null,
    localImage: Bitmap? = null,
    onEmoji: () -> Unit,
    onPhoto: () -> Unit,
    content: Color,
) {
    val dark = isSystemInDarkTheme()
    var showingColorPicker by remember { mutableStateOf(false) }
    if (showingColorPicker) {
        AudienceColorPickerDialog(
            initialHex = selectedColor,
            onDismiss = { showingColorPicker = false },
            onConfirm = {
                onColor(it)
                showingColorPicker = false
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.audience_personalization), color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            items(CustomAudienceList.predefinedColors) { color ->
                val selected = selectedColor == color
                Box(
                    Modifier
                        .size(42.dp)
                        .graphicsLayer { scaleX = if (selected) 1.15f else 1f; scaleY = if (selected) 1.15f else 1f }
                        .background(Color.fromHex(color), CircleShape)
                        .border(if (selected) 3.dp else 0.dp, content, CircleShape)
                        .clickable { onColor(color) },
                )
            }
            item {
                Box(
                    Modifier
                        .size(42.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { showingColorPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Palette, null, tint = content, modifier = Modifier.size(20.dp))
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            items(CustomAudienceList.predefinedIcons) { icon ->
                val selected = selectedIcon == icon
                val tint = Color.fromHex(selectedColor)
                Box(
                    Modifier
                        .size(52.dp)
                        .graphicsLayer { scaleX = if (selected) 1.1f else 1f; scaleY = if (selected) 1.1f else 1f }
                        .background(
                            if (selected) tint.copy(0.15f) else if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                            CircleShape,
                        )
                        .border(if (selected) 2.dp else 0.dp, if (selected) tint.copy(0.3f) else Color.Transparent, CircleShape)
                        .clickable { onIcon(icon) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        listIconVector(icon),
                        null,
                        tint = if (selected) tint else content.copy(0.3f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            item {
                val isCustomEmoji = selectedIcon !in CustomAudienceList.predefinedIcons && imagePath == null && localImage == null
                Box(
                    Modifier
                        .size(52.dp)
                        .graphicsLayer { scaleX = if (isCustomEmoji) 1.1f else 1f; scaleY = if (isCustomEmoji) 1.1f else 1f }
                        .background(
                            if (isCustomEmoji) Color.fromHex(selectedColor).copy(0.15f)
                            else if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                            CircleShape,
                        )
                        .clickable(onClick = onEmoji),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isCustomEmoji) selectedIcon else "😊", fontSize = 22.sp)
                }
            }
            item {
                val photoSelected = localImage != null || imagePath != null
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            if (photoSelected) Color.fromHex(selectedColor).copy(0.15f)
                            else if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                            CircleShape,
                        )
                        .clickable(onClick = onPhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        localImage != null -> Image(
                            bitmap = localImage.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                        imagePath != null -> AsyncImage(
                            model = imagePath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                        else -> Icon(Icons.Filled.Photo, null, tint = content.copy(0.45f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AudienceColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val parsed = remember(initialHex) { runCatching { AndroidColor.parseColor("#$initialHex") }.getOrDefault(AndroidColor.BLACK) }
    var red by remember { mutableIntStateOf(AndroidColor.red(parsed)) }
    var green by remember { mutableIntStateOf(AndroidColor.green(parsed)) }
    var blue by remember { mutableIntStateOf(AndroidColor.blue(parsed)) }
    val preview = Color(red, green, blue)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(54.dp).background(preview, RoundedCornerShape(16.dp)))
                Text("R · $red")
                Slider(value = red.toFloat(), onValueChange = { red = it.toInt() }, valueRange = 0f..255f)
                Text("G · $green")
                Slider(value = green.toFloat(), onValueChange = { green = it.toInt() }, valueRange = 0f..255f)
                Text("B · $blue")
                Slider(value = blue.toFloat(), onValueChange = { blue = it.toInt() }, valueRange = 0f..255f)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.audience_actions_cancel)) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(String.format("%02X%02X%02X", red, green, blue)) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
    )
}

@Composable
private fun UserAvatarCircle(user: AppUser, size: androidx.compose.ui.unit.Dp) {
    val dark = isSystemInDarkTheme()
    val url = user.profileImagePath
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).border(
                if (size >= 48.dp) 2.dp else 1.dp,
                if (dark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                CircleShape,
            ),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (dark) CanvasLight.copy(0.06f) else CanvasDark.copy(0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.username.take(1).uppercase(),
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.33f).sp,
            )
        }
    }
}
