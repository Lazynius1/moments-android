package com.moments.android.views.creator.audienceselector

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createCustomAudienceList
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.firestore.updateCustomAudienceList
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.components.VerifiedBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()

    var flowDestination by remember { mutableStateOf<FlowDestination>(FlowDestination.Main) }
    var navigatingForward by remember { mutableStateOf(true) }
    var customLists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var isLoadingLists by remember { mutableStateOf(true) }
    var selectedUsersForCustom by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var showingSaveFeedback by remember { mutableStateOf(false) }

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
        showingSaveFeedback = true
        scope.launch {
            delay(2000)
            showingSaveFeedback = false
        }
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
                    onBack = { navigate(FlowDestination.Main, forward = false) },
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

        AnimatedVisibility(
            visible = showingSaveFeedback,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = stringResource(R.string.audience_saved),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(bottom = 100.dp)
                    .shadow(10.dp, RoundedCornerShape(25.dp), spotColor = Color.Black.copy(0.3f))
                    .background(AudienceBlue, RoundedCornerShape(25.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
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
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 0.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.audience_selection_title),
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.audience_selection_subtitle),
                    color = secondary,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    textAlign = TextAlign.Center,
                )
            }

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
                            color = AudienceBlue,
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
                            CircularProgressIndicator(Modifier.size(18.dp), color = AudienceBlue, strokeWidth = 2.dp)
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
                                        .border(
                                            1.dp,
                                            AudienceBlue.copy(alpha = 0.3f),
                                            RoundedCornerShape(20.dp),
                                        )
                                        .clickable(onClick = onCreateList)
                                        .padding(top = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier.size(48.dp).momentsChromeGlass(CircleShape, interactive = true),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, null, tint = AudienceBlue, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        stringResource(R.string.audience_create),
                                        color = AudienceBlue,
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
                        AudienceBlue.copy(0.2f),
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
    val scope = rememberCoroutineScope()
    var listName by remember { mutableStateOf("") }
    var listDescription by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(CustomAudienceList.predefinedColors.first()) }
    var selectedIcon by remember { mutableStateOf(CustomAudienceList.predefinedIcons.first()) }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var showingMemberPicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val tint = Color.fromHex(selectedColor)

    if (embeddedInFlow && showingMemberPicker) {
        MemberPickerView(
            selectedMembers = selectedMembers,
            onSelectedMembersChange = { selectedMembers = it },
            embeddedInFlow = true,
            onBack = { showingMemberPicker = false },
            onConfirm = { showingMemberPicker = false },
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
                onConfirm = { showingMemberPicker = false },
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
            if (embeddedInFlow) {
                EmbeddedFlowHeader(
                    title = stringResource(R.string.audience_create_action),
                    subtitle = stringResource(R.string.audience_custom_lists),
                    content = content,
                    onBack = { onBack?.invoke() },
                )
            }
            ListHeroPreview(
                name = listName.ifEmpty { stringResource(R.string.audience_list_placeholder) },
                memberCount = selectedMembers.size,
                colorHex = selectedColor,
                icon = selectedIcon,
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
                        HapticManager.shared.lightImpact()
                    },
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
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        FirestoreService().createCustomAudienceList(
                                            userId = uid,
                                            name = listName,
                                            description = listDescription,
                                            members = selectedMembers.toList(),
                                            color = selectedColor,
                                            icon = selectedIcon,
                                        )
                                    }
                                }.onSuccess {
                                    isLoading = false
                                    onCompleted?.invoke()
                                }.onFailure { isLoading = false }
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
    var showingMemberPicker by remember { mutableStateOf(false) }
    var currentMembers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var filteredMembers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isLoadingMembers by remember { mutableStateOf(false) }
    var visibleMembersLimit by remember { mutableIntStateOf(12) }
    var isLoading by remember { mutableStateOf(false) }
    val membersPageSize = 12
    val tint = Color.fromHex(selectedColor)

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
            embeddedInFlow = true,
            onBack = { showingMemberPicker = false },
            onConfirm = { showingMemberPicker = false },
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
                onConfirm = { showingMemberPicker = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        if (embeddedInFlow) {
            EmbeddedFlowHeader(
                title = stringResource(R.string.common_edit),
                subtitle = list.name,
                content = content,
                onBack = { onBack?.invoke() },
            )
        }
        ListHeroPreview(
            name = listName.ifEmpty { stringResource(R.string.audience_list_placeholder) },
            memberCount = selectedMembers.size,
            colorHex = selectedColor,
            icon = selectedIcon,
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
                    HapticManager.shared.lightImpact()
                },
                content = content,
            )
            // membersManagementSection
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.audience_members), color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            stringResource(R.string.audience_members_count_long, selectedMembers.size),
                            color = Color.Gray,
                            fontSize = 12.sp,
                        )
                    }
                    Row(
                        Modifier.clickable { showingMemberPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(24.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(11.dp))
                        }
                        Text(stringResource(R.string.audience_list_add), color = content, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }
                when {
                    isLoadingMembers -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        MomentsCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    currentMembers.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Color.Gray.copy(0.3f), modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.audience_list_empty), color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(stringResource(R.string.audience_list_emptyAlt), color = Color.Gray, fontSize = 12.sp)
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredMembers.take(visibleMembersLimit).forEach { member ->
                                MemberRowWithRemove(
                                    user = member,
                                    onRemove = {
                                        selectedMembers = selectedMembers - member.id
                                        reloadMembers()
                                    },
                                )
                            }
                        }
                        if (filteredMembers.size > visibleMembersLimit) {
                            val more = minOf(membersPageSize, filteredMembers.size - visibleMembersLimit)
                            Row(
                                Modifier.padding(top = 4.dp).clickable { visibleMembersLimit += membersPageSize },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(24.dp).momentsChromeGlass(CircleShape, interactive = true), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = content, modifier = Modifier.size(14.dp))
                                }
                                Text(stringResource(R.string.audience_list_loadMoreMembers, more), color = content, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            val canSave = listName.isNotBlank() && !isLoading
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(15.dp, RoundedCornerShape(24.dp), spotColor = tint.copy(0.3f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(tint, tint.copy(0.8f))))
                    .clickable(enabled = canSave) {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@clickable
                        val listId = list.id ?: return@clickable
                        isLoading = true
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    FirestoreService().updateCustomAudienceList(
                                        userId = uid,
                                        listId = listId,
                                        name = listName,
                                        description = listDescription,
                                        members = selectedMembers.toList(),
                                        color = selectedColor,
                                        icon = selectedIcon,
                                    )
                                }
                            }.onSuccess {
                                isLoading = false
                                onCompleted?.invoke()
                            }.onFailure { isLoading = false }
                        }
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = Color.White)
                        Text(stringResource(R.string.common_save), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
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
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) CanvasDark else CanvasLight
    val content = if (dark) Color.White else Color.Black
    val secondary = content.copy(alpha = if (dark) 0.65f else 0.62f)
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var selectedUsersData by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var selectedCarouselVisibleLimit by remember { mutableIntStateOf(12) }
    val selectedCarouselPageSize = 10

    LaunchedEffect(selectedMembers) {
        if (selectedMembers.isEmpty()) {
            selectedUsersData = emptyList()
            return@LaunchedEffect
        }
        selectedUsersData = selectedUsersData.filter { it.id in selectedMembers }
        val missing = selectedMembers - selectedUsersData.map { it.id }.toSet()
        if (missing.isNotEmpty()) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchUsers(missing.toList()) }.getOrDefault(emptyList())
            }
            selectedUsersData = (selectedUsersData + fetched).distinctBy { it.id }
        }
        selectedCarouselVisibleLimit = 12
    }

    LaunchedEffect(searchText) {
        val q = searchText.trim()
        if (q.isEmpty()) {
            hasSearched = false
            searchResults = emptyList()
            return@LaunchedEffect
        }
        if (q.length < 2) return@LaunchedEffect
        hasSearched = true
        isSearching = true
        searchResults = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().searchUsers(q, limit = 20) }.getOrDefault(emptyList())
        }
        isSearching = false
    }

    fun toggle(user: AppUser) {
        if (user.id in selectedMembers) {
            onSelectedMembersChange(selectedMembers - user.id)
            selectedUsersData = selectedUsersData.filterNot { it.id == user.id }
        } else {
            onSelectedMembersChange(selectedMembers + user.id)
            if (selectedUsersData.none { it.id == user.id }) {
                selectedUsersData = selectedUsersData + user
            }
        }
    }

    Column(modifier.fillMaxSize().background(if (!embeddedInFlow) canvas else Color.Transparent)) {
        if (embeddedInFlow) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).momentsChromeGlass(CircleShape, interactive = true).clickable { onBack?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.audience_picker_title), color = content, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text(stringResource(R.string.audience_members), color = secondary, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.common_confirm),
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (selectedMembers.isEmpty()) 0.45f else 1f }
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = selectedMembers.isNotEmpty())
                        .clickable(enabled = selectedMembers.isNotEmpty()) { onConfirm?.invoke() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.audience_actions_cancel),
                    color = secondary,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable { onBack?.invoke() },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.audience_picker_title),
                    color = content,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.common_confirm),
                    color = AudienceTeal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (selectedMembers.isEmpty()) 0.45f else 1f }
                        .clickable(enabled = selectedMembers.isNotEmpty()) { onConfirm?.invoke() },
                )
            }
        }
        if (selectedUsersData.isNotEmpty()) {
            val visible = selectedUsersData.take(selectedCarouselVisibleLimit)
            val hidden = (selectedUsersData.size - selectedCarouselVisibleLimit).coerceAtLeast(0)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                items(visible, key = { it.id }) { user ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            UserAvatarCircle(user, 48.dp)
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(4.dp, (-4).dp)
                                    .size(16.dp)
                                    .background(Color.Black.copy(0.5f), CircleShape)
                                    .clickable { toggle(user) }
                                    .padding(2.dp),
                            )
                        }
                        Text(user.username, color = content, fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                    }
                }
                if (hidden > 0) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable { selectedCarouselVisibleLimit += selectedCarouselPageSize },
                        ) {
                            Box(
                                Modifier.size(48.dp).background(AudienceTeal.copy(0.18f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+$hidden", color = AudienceTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Text(stringResource(R.string.audience_more), color = secondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        // searchBar
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
                    modifier = Modifier.size(18.dp).clickable {
                        searchText = ""
                        hasSearched = false
                        searchResults = emptyList()
                    },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 40.dp),
                    ) {
                        Icon(Icons.Filled.Person, null, tint = secondary, modifier = Modifier.size(50.dp))
                        Text(stringResource(R.string.audience_picker_initialTitle), color = content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(stringResource(R.string.audience_picker_initialDescription), color = secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
                isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MomentsCircularProgressIndicator()
                        Text(stringResource(R.string.common_searching), color = secondary, fontSize = 16.sp)
                    }
                }
                searchResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 40.dp),
                    ) {
                        Icon(Icons.Filled.Person, null, tint = secondary, modifier = Modifier.size(50.dp))
                        Text(stringResource(R.string.common_no_results), color = content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            stringResource(R.string.audience_picker_noResultsDescription, searchText),
                            color = secondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(searchResults, key = { it.id }) { user ->
                        UserSelectionRowEnhanced(
                            user = user,
                            isSelected = user.id in selectedMembers,
                            onToggle = { toggle(user) },
                        )
                    }
                }
            }
        }
        if (selectedMembers.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.audience_picker_selectedCount, selectedMembers.size),
                        color = content,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Text(
                        stringResource(R.string.audience_picker_selectedDescription),
                        color = secondary,
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.common_clear),
                        color = Color.Red,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable { onSelectedMembersChange(emptySet()) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.common_confirm),
                        color = content,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (selectedMembers.isEmpty()) 0.45f else 1f }
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = selectedMembers.isNotEmpty())
                            .clickable(enabled = selectedMembers.isNotEmpty()) { onConfirm?.invoke() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Card / Fila de Usuario

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
private fun EmbeddedFlowHeader(title: String, subtitle: String, content: Color, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
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
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun ListHeroPreview(
    name: String,
    memberCount: Int,
    colorHex: String,
    icon: String,
    content: Color,
    heroSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val tint = Color.fromHex(colorHex)
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
                Box(
                    Modifier.size(heroSize * 0.7f).background(tint.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(listIconVector(icon), null, tint = tint, modifier = Modifier.size(iconSize))
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
    content: Color,
) {
    val dark = isSystemInDarkTheme()
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
        }
    }
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

