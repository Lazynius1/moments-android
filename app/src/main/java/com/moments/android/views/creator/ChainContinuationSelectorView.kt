package com.moments.android.views.creator

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import com.moments.android.views.components.MomentsCircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.AudienceGridCard
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.creator.audienceselector.CreateCustomListView
import com.moments.android.views.creator.audienceselector.CustomAudienceList
import com.moments.android.views.creator.audienceselector.CustomAudienceListsView
import com.moments.android.views.creator.audienceselector.CustomAudienceSelector
import com.moments.android.views.creator.audienceselector.CustomListCard
import com.moments.android.views.creator.audienceselector.EditCustomListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ChainContinuationFlow {
    Main,
    CustomPeople,
    ManageLists,
    CreateList,
    EditList,
}

/** Port de `CustomUserSelectorView` — carga usuarios ya seleccionados (≡ loadSelectedUsersInfo). */
@Composable
fun CustomUserSelectorView(
    selectedUsers: List<String>,
    onSelectedUsersChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    LaunchedEffect(selectedUsers) {
        if (selectedUsers.isEmpty()) {
            users = emptyList()
            return@LaunchedEffect
        }
        users = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().fetchUsers(selectedUsers) }.getOrDefault(emptyList())
        }
    }
    CustomAudienceSelector(
        selectedUsers = users,
        onSelectedUsersChange = { users = it },
        onComplete = {
            onSelectedUsersChange(users.map { it.id })
            onDismiss()
        },
        onBack = onBack ?: onDismiss,
        embeddedInFlow = true,
        modifier = modifier,
    )
}

/** Port Compose de `ChainContinuationSelectorView.swift`. */
@Composable
fun ChainContinuationSelectorView(
    selectedAudience: ChainContinuationSetting,
    onSelectedAudienceChange: (ChainContinuationSetting) -> Unit,
    selectedListId: String?,
    onSelectedListIdChange: (String?) -> Unit,
    selectedListName: String?,
    onSelectedListNameChange: (String?) -> Unit,
    customSelectedUsers: List<String>,
    onCustomSelectedUsersChange: (List<String>) -> Unit,
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (dark) Color.White else Color.Black

    var flow by remember { mutableStateOf(ChainContinuationFlow.Main) }
    var navigatingForward by remember { mutableStateOf(true) }
    var createReturnsToManage by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<CustomAudienceList?>(null) }
    var lists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun reloadLists() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        lists = if (uid == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchCustomLists(uid) }.getOrDefault(emptyList())
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) { reloadLists() }

    fun navigate(to: ChainContinuationFlow, forward: Boolean = true) {
        navigatingForward = forward
        flow = to
    }

    fun finishSelection() {
        if (embeddedInFlow) onComplete?.invoke() else onBack?.invoke()
    }

    fun resetSelection() {
        onSelectedListIdChange(null)
        onSelectedListNameChange(null)
        onCustomSelectedUsersChange(emptyList())
    }

    AnimatedContent(
        targetState = flow,
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
        label = "chainContinuationFlow",
        modifier = modifier.fillMaxSize().background(canvas),
    ) { destination ->
        when (destination) {
            ChainContinuationFlow.CustomPeople -> {
                CustomUserSelectorView(
                    selectedUsers = customSelectedUsers,
                    onSelectedUsersChange = { ids ->
                        onCustomSelectedUsersChange(ids)
                        onSelectedAudienceChange(ChainContinuationSetting.CUSTOM)
                        onSelectedListIdChange(null)
                        onSelectedListNameChange(null)
                    },
                    onDismiss = { finishSelection() },
                    onBack = { navigate(ChainContinuationFlow.Main, forward = false) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ChainContinuationFlow.ManageLists -> {
                CustomAudienceListsView(
                    embeddedInFlow = true,
                    onBack = {
                        loading = true
                        navigate(ChainContinuationFlow.Main, forward = false)
                    },
                    onCreateList = {
                        createReturnsToManage = true
                        navigate(ChainContinuationFlow.CreateList)
                    },
                    onEditList = { list ->
                        editingList = list
                        navigate(ChainContinuationFlow.EditList)
                    },
                    onListsChanged = { /* reload on return */ },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ChainContinuationFlow.CreateList -> {
                CreateCustomListView(
                    embeddedInFlow = true,
                    onBack = {
                        navigate(
                            if (createReturnsToManage) ChainContinuationFlow.ManageLists
                            else ChainContinuationFlow.Main,
                            forward = false,
                        )
                    },
                    onCompleted = {
                        loading = true
                        navigate(
                            if (createReturnsToManage) ChainContinuationFlow.ManageLists
                            else ChainContinuationFlow.Main,
                            forward = false,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ChainContinuationFlow.EditList -> {
                val list = editingList
                if (list != null) {
                    EditCustomListView(
                        list = list,
                        embeddedInFlow = true,
                        onBack = { navigate(ChainContinuationFlow.ManageLists, forward = false) },
                        onCompleted = {
                            loading = true
                            editingList = null
                            navigate(ChainContinuationFlow.ManageLists, forward = false)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Sin lista → volver a manage (no debería ocurrir).
                    CustomAudienceListsView(
                        embeddedInFlow = true,
                        onBack = { navigate(ChainContinuationFlow.Main, forward = false) },
                        onCreateList = {
                            createReturnsToManage = true
                            navigate(ChainContinuationFlow.CreateList)
                        },
                        onEditList = { edited ->
                            editingList = edited
                            navigate(ChainContinuationFlow.EditList)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            ChainContinuationFlow.Main -> {
                LaunchedEffect(loading) {
                    if (loading) reloadLists()
                }
                ChainContinuationMainContent(
                    selectedAudience = selectedAudience,
                    selectedListId = selectedListId,
                    customSelectedUsers = customSelectedUsers,
                    lists = lists,
                    loading = loading,
                    embeddedInFlow = embeddedInFlow,
                    content = content,
                    onBack = onBack,
                    onSelectPredefined = { choice ->
                        onSelectedAudienceChange(choice)
                        resetSelection()
                        finishSelection()
                    },
                    onManageLists = { navigate(ChainContinuationFlow.ManageLists) },
                    onCreateList = {
                        createReturnsToManage = false
                        navigate(ChainContinuationFlow.CreateList)
                    },
                    onSelectList = { list ->
                        onSelectedAudienceChange(ChainContinuationSetting.CUSTOM_LIST)
                        onSelectedListIdChange(list.id)
                        onSelectedListNameChange(list.name)
                        onCustomSelectedUsersChange(emptyList())
                        finishSelection()
                    },
                    onCustomPeople = { navigate(ChainContinuationFlow.CustomPeople) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ChainContinuationMainContent(
    selectedAudience: ChainContinuationSetting,
    selectedListId: String?,
    customSelectedUsers: List<String>,
    lists: List<CustomAudienceList>,
    loading: Boolean,
    embeddedInFlow: Boolean,
    content: Color,
    onBack: (() -> Unit)?,
    onSelectPredefined: (ChainContinuationSetting) -> Unit,
    onManageLists: () -> Unit,
    onCreateList: () -> Unit,
    onSelectList: (CustomAudienceList) -> Unit,
    onCustomPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (embeddedInFlow) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable { onBack?.invoke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.story_chains_continuation_audience_nav_title),
                            color = content,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.story_chains_continuation_audience_subtitle),
                            color = content.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(40.dp))
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.story_chains_continuation_audience),
                        color = content,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.story_chains_visibility_info),
                        color = content.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Predefined
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.audience_predefined),
                        color = content.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            ChainContinuationSetting.EVERYONE,
                            ChainContinuationSetting.MUTUALS,
                            ChainContinuationSetting.BEST_FRIENDS,
                        ).forEach { choice ->
                            AudienceGridCard(
                                audience = choice.contentAudience,
                                isSelected = choice == selectedAudience,
                                onTap = { onSelectPredefined(choice) },
                            )
                        }
                    }
                }

                // Custom lists
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.audience_custom_lists),
                            color = content.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.audience_manage),
                            color = Color(0xFF007AFF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(onClick = onManageLists),
                        )
                    }
                    when {
                        loading -> {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MomentsCircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    stringResource(R.string.audience_loadingLists),
                                    color = content.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        lists.isEmpty() -> {
                            EmptyCustomListsCard(content = content, onCreate = onCreateList)
                        }
                        else -> {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                item {
                                    Column(
                                        Modifier
                                            .width(100.dp)
                                            .height(140.dp)
                                            .border(
                                                width = 1.dp,
                                                color = Color(0xFF007AFF).copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(20.dp),
                                            )
                                            .clickable(onClick = onCreateList)
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(48.dp)
                                                .momentsChromeGlass(CircleShape, interactive = true),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Add, null, tint = Color(0xFF007AFF))
                                        }
                                        Text(
                                            stringResource(R.string.audience_create),
                                            color = Color(0xFF007AFF),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                                items(lists, key = { it.id ?: it.name }) { list ->
                                    CustomListCard(
                                        list = list,
                                        isSelected = selectedAudience == ChainContinuationSetting.CUSTOM_LIST &&
                                            selectedListId == list.id,
                                        onTap = { onSelectList(list) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Manual selection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.audience_manualSelection),
                        color = content.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    val isCustomPeopleSelected =
                        selectedAudience == ChainContinuationSetting.CUSTOM && selectedListId == null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCustomPeople)
                            .padding(vertical = 11.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .then(
                                    if (isCustomPeopleSelected) Modifier
                                    else Modifier, // opacity applied on icon via tint below
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AudienceIconView(
                                audience = ContentAudience.CUSTOM,
                                size = AudienceIconMetrics.gridCardEmphasis,
                                tintColor = content.copy(
                                    alpha = if (isCustomPeopleSelected) 1f else 0.42f,
                                ),
                            )
                        }
                        Column(
                            Modifier
                                .padding(start = 14.dp)
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(R.string.audience_type_custom),
                                color = content.copy(alpha = if (isCustomPeopleSelected) 1f else 0.82f),
                                fontSize = 16.sp,
                                fontWeight = if (isCustomPeopleSelected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            Text(
                                if (selectedAudience == ChainContinuationSetting.CUSTOM &&
                                    customSelectedUsers.isNotEmpty()
                                ) {
                                    stringResource(R.string.audience_people_count, customSelectedUsers.size)
                                } else {
                                    stringResource(R.string.audience_description_custom)
                                },
                                color = content.copy(alpha = if (isCustomPeopleSelected) 0.55f else 0.45f),
                                fontSize = 13.sp,
                            )
                        }
                        if (isCustomPeopleSelected) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (dark) Color.White.copy(0.14f) else Color.Black.copy(0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Check, null, tint = content, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null,
                                tint = content.copy(alpha = 0.55f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCustomListsCard(
    content: Color,
    onCreate: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(content.copy(alpha = if (dark) 0.06f else 0.04f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        content.copy(alpha = 0.1f),
                        Color(0xFF007AFF).copy(alpha = 0.2f),
                    ),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AudienceIconView(
            audience = ContentAudience.CUSTOM_LIST,
            size = AudienceIconMetrics.gridCardEmphasis,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.audience_noCustomLists_title),
                color = content,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.audience_noCustomLists_description),
                color = content.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier
                .clickable(onClick = onCreate)
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .momentsChromeGlass(CircleShape, interactive = true),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                stringResource(R.string.audience_createFirstList),
                color = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
