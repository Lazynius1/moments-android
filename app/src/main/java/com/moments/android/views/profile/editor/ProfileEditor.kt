package com.moments.android.views.profile.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.InterestEmojiHelper
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.InterestOption
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchAvailableInterests
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.firestore.removeProfilePicture
import com.moments.android.services.firestore.updateProfilePicture
import com.moments.android.services.storage.StorageService
import com.moments.android.views.creator.creatoruikit.CameraCapture
import com.moments.android.views.creator.creatoruikit.CameraCaptureMediaType
import com.moments.android.views.permissions.CameraAccessBoundary
import com.moments.android.views.profile.editor.sections.ProfileLibraryCropEntryView
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsSheetHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Port de `ProfileAlbumInfo` en `ProfileEditor.swift`. */
data class ProfileAlbumInfo(
    val id: String,
    val title: String,
    val assetCount: Int,
    val bucketId: String? = null,
)

private enum class EditSection {
    BASIC,
    INTERESTS,
}

private val DefaultInterestsFallback = listOf(
    "Música", "Cine", "Deportes", "Viajes", "Fotografía", "Arte", "Tecnología",
    "Lectura", "Cocina", "Moda", "Gaming", "Fitness", "Naturaleza", "Animales",
    "Baile", "Teatro", "Escritura", "Ciencia", "Historia", "Idiomas", "Anime",
    "K-pop", "Streaming", "Yoga", "Meditación", "Senderismo", "Ciclismo",
)

/**
 * Port simplificado de `GridPhotoPickerView` — entrada a crop vía
 * [ProfileLibraryCropEntryView] (como `ProfileLibraryCropEntryView` iOS).
 */
@Composable
fun GridPhotoPickerView(
    currentProfileImage: Bitmap?,
    onImageUploaded: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    ProfileLibraryCropEntryView(
        onImageCropped = { bitmap ->
            scope.launch {
                uploadProfileImage(bitmap)?.let(onImageUploaded)
                onDismiss()
            }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
    // Preview del avatar actual solo como referencia en el host si hace falta.
    currentProfileImage?.let { /* no-op: el crop entry gestiona la selección */ }
}

/**
 * Port de `ModernEditProfileView` — tabs Perfil/Intereses, foto (library+camera+delete),
 * sheets vía [MomentsModalSheet], carga Firestore + guardado optimista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModernEditProfileView(
    user: AppUser?,
    onSave: (bio: String, website: String?, interests: List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = context.resources
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.58f) else Color.Black.copy(0.58f)

    var username by remember { mutableStateOf(user?.username.orEmpty()) }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var bio by remember { mutableStateOf(user?.bio.orEmpty()) }
    var website by remember { mutableStateOf(user?.websiteUrl.orEmpty()) }
    var profileImagePath by remember { mutableStateOf(user?.profileImagePath) }
    var selectedInterests by remember { mutableStateOf(user?.interests?.toSet().orEmpty()) }
    var availableInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentProfileImage by remember { mutableStateOf<Bitmap?>(null) }

    var activeSection by remember { mutableStateOf(EditSection.BASIC) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPhotoActions by remember { mutableStateOf(false) }
    var showLibraryCrop by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showInterestsPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val characterCount = bio.length

    fun loadUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            errorMessage = context.getString(R.string.profile_editor_error_unauthenticated)
            isLoading = false
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            runCatching { FirestoreService().fetchUserProfile(userId) }
                .onSuccess { profile ->
                    username = profile.username
                    email = profile.email
                    profileImagePath = profile.profileImagePath
                    bio = profile.bio.orEmpty()
                    website = profile.websiteUrl.orEmpty()
                    selectedInterests = profile.interests.toSet()
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.message
                        ?: context.getString(R.string.profile_editor_error)
                    isLoading = false
                }
        }
    }

    fun loadInterests() {
        scope.launch {
            availableInterests = runCatching { FirestoreService().fetchAvailableInterests() }
                .getOrElse { DefaultInterestsFallback }
                .ifEmpty { DefaultInterestsFallback }
        }
    }

    fun uploadCaptured(bitmap: Bitmap) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoading = true
        errorMessage = null
        scope.launch {
            runCatching {
                val old = runCatching { FirestoreService().fetchUserProfile(userId).profileImagePath }
                    .getOrNull()
                val path = StorageService.uploadProfileImage(userId, bitmap)
                FirestoreService().updateProfilePicture(userId, path)
                StorageService.deleteProfileImage(userId, old)
                path
            }.onSuccess { path ->
                profileImagePath = path
                currentProfileImage = bitmap
                isLoading = false
            }.onFailure {
                isLoading = false
                errorMessage = context.getString(
                    R.string.profile_editor_error_upload_image,
                    it.message.orEmpty(),
                )
            }
        }
    }

    fun deleteCurrentProfileImage() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val old = profileImagePath
        isLoading = true
        errorMessage = null
        scope.launch {
            runCatching {
                FirestoreService().removeProfilePicture(userId)
                StorageService.deleteProfileImage(userId, old)
            }.onSuccess {
                currentProfileImage = null
                profileImagePath = null
                isLoading = false
            }.onFailure {
                isLoading = false
                errorMessage = context.getString(
                    R.string.profile_editor_error_update_profile,
                    it.message.orEmpty(),
                )
            }
        }
    }

    fun saveProfile() {
        if (FirebaseAuth.getInstance().currentUser?.uid == null) {
            errorMessage = context.getString(R.string.profile_editor_error_unauthenticated)
            return
        }
        isLoading = true
        onSave(bio, website.ifBlank { null }, selectedInterests.toList())
        scope.launch {
            delay(100)
            isLoading = false
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        loadUserData()
        loadInterests()
    }

    if (showLibraryCrop) {
        ProfileLibraryCropEntryView(
            onImageCropped = { bitmap ->
                showLibraryCrop = false
                uploadCaptured(bitmap)
            },
            onDismiss = { showLibraryCrop = false },
            modifier = modifier,
        )
        return
    }

    if (showCamera) {
        CameraAccessBoundary(onCancel = { showCamera = false }) {
            CameraCapture(
                mediaTypes = setOf(CameraCaptureMediaType.IMAGE),
                onCapture = { media ->
                    scope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(media.uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        }
                        showCamera = false
                        bitmap?.let(::uploadCaptured)
                    }
                },
                onDismiss = { showCamera = false },
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }

    Box(
        modifier
            .fillMaxSize()
            .background(canvas)
            .safeDrawingPadding(),
    ) {
        when {
            isLoading && username.isEmpty() && errorMessage == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MomentsCircularProgressIndicator()
                    Text(
                        stringResource(R.string.profile_editor_loading_profile),
                        color = primary.copy(0.8f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
            errorMessage != null && username.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color.Red.copy(0.8f), modifier = Modifier.size(50.dp))
                    Text(
                        stringResource(R.string.profile_editor_error),
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        errorMessage.orEmpty(),
                        color = primary.copy(0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.profile_editor_retry),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .clip(RoundedCornerShape(50))
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable { loadUserData() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    EditProfileHeader(
                        primary = primary,
                        characterCount = characterCount,
                        currentProfileImage = currentProfileImage,
                        profileImagePath = profileImagePath,
                        onDismiss = onDismiss,
                        onSave = { if (characterCount <= 150) saveProfile() },
                        onPhotoTap = { showPhotoActions = true },
                    )
                    EditSectionTabs(
                        active = activeSection,
                        onSelect = { activeSection = it },
                        primary = primary,
                        dark = dark,
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .padding(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        when (activeSection) {
                            EditSection.BASIC -> BasicProfileSection(
                                username = username,
                                email = email,
                                website = website,
                                onWebsiteChange = { website = it },
                                bio = bio,
                                onBioChange = { bio = it },
                                characterCount = characterCount,
                                primary = primary,
                                secondary = secondary,
                                dark = dark,
                            )
                            EditSection.INTERESTS -> InterestsSection(
                                selectedInterests = selectedInterests,
                                primary = primary,
                                secondary = secondary,
                                dark = dark,
                                onEdit = { showInterestsPicker = true },
                                localize = { InterestOption.localize(it, resources) },
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && username.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(canvas.copy(0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                MomentsCircularProgressIndicator()
            }
        }
    }

    if (showPhotoActions) {
        MomentsModalSheet(onDismissRequest = { showPhotoActions = false }, largeOnly = false) {
            PhotoActionsSheetContent(
                primary = primary,
                canDelete = currentProfileImage != null || profileImagePath != null,
                onLibrary = {
                    showPhotoActions = false
                    scope.launch {
                        delay(200)
                        showLibraryCrop = true
                    }
                },
                onCamera = {
                    showPhotoActions = false
                    scope.launch {
                        delay(200)
                        showCamera = true
                    }
                },
                onDelete = {
                    showPhotoActions = false
                    scope.launch {
                        delay(200)
                        showDeleteConfirm = true
                    }
                },
            )
        }
    }

    if (showInterestsPicker) {
        MomentsModalSheet(onDismissRequest = { showInterestsPicker = false }, largeOnly = false) {
            InterestsPickerSheet(
                available = availableInterests,
                selected = selectedInterests,
                onChange = { selectedInterests = it },
                primary = primary,
                secondary = secondary,
                dark = dark,
                localize = { InterestOption.localize(it, resources) },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.profile_editor_delete_photo)) },
            text = { Text(stringResource(R.string.profile_editor_delete_photo_confirm)) },
            dismissButton = {
                TextButton({ showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton({
                    showDeleteConfirm = false
                    deleteCurrentProfileImage()
                }) {
                    Text(stringResource(R.string.common_delete), color = Color.Red)
                }
            },
        )
    }
}

@Composable
private fun EditProfileHeader(
    primary: Color,
    characterCount: Int,
    currentProfileImage: Bitmap?,
    profileImagePath: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onPhotoTap: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MomentsSheetHeader(
            title = stringResource(R.string.profile_editor_title),
            titleSize = 18.sp,
            trailing = {
                Text(
                    stringResource(R.string.common_save),
                    color = if (characterCount <= 150) primary else primary.copy(0.4f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = characterCount <= 150)
                        .clickable(enabled = characterCount <= 150, onClick = onSave)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(118.dp)
                        .border(
                            BorderStroke(1.dp, primary.copy(if (isSystemInDarkTheme()) 0.16f else 0.12f)),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        currentProfileImage != null -> {
                            androidx.compose.foundation.Image(
                                currentProfileImage.asImageBitmap(),
                                null,
                                Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPhotoTap),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        !profileImagePath.isNullOrBlank() -> {
                            AsyncImage(
                                profileImagePath,
                                null,
                                Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPhotoTap),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        else -> {
                            Box(
                                Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(if (isSystemInDarkTheme()) 0.08f else 0.05f))
                                    .clickable(onClick = onPhotoTap),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    null,
                                    tint = primary.copy(if (isSystemInDarkTheme()) 0.6f else 0.35f),
                                    modifier = Modifier.size(50.dp),
                                )
                            }
                        }
                    }
                }
                Icon(
                    Icons.Filled.CameraAlt,
                    null,
                    tint = primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onPhotoTap)
                        .padding(8.dp),
                )
            }

            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onPhotoTap)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PhotoLibrary, null, tint = primary, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.profile_editor_change),
                    color = primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun EditSectionTabs(
    active: EditSection,
    onSelect: (EditSection) -> Unit,
    primary: Color,
    dark: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditSection.entries.forEach { section ->
            val selected = active == section
            val title = stringResource(
                when (section) {
                    EditSection.BASIC -> R.string.profile_editor_section_basic
                    EditSection.INTERESTS -> R.string.profile_editor_section_interests
                },
            )
            val icon = when (section) {
                EditSection.BASIC -> Icons.Filled.Person
                EditSection.INTERESTS -> Icons.Filled.Favorite
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (selected) {
                            Modifier.momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        } else {
                            Modifier.background(
                                if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.04f),
                                RoundedCornerShape(50),
                            )
                        },
                    )
                    .clickable { onSelect(section) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (selected) primary else primary.copy(if (dark) 0.65f else 0.55f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    title,
                    color = if (selected) primary else primary.copy(if (dark) 0.65f else 0.55f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun BasicProfileSection(
    username: String,
    email: String,
    website: String,
    onWebsiteChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    characterCount: Int,
    primary: Color,
    secondary: Color,
    dark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ReadOnlyProfileField(
                icon = Icons.Filled.Person,
                label = stringResource(R.string.profile_editor_username),
                value = username,
                primary = primary,
                secondary = secondary,
                dark = dark,
            )
            ReadOnlyProfileField(
                icon = Icons.Filled.Email,
                label = stringResource(R.string.profile_editor_email),
                value = email,
                primary = primary,
                secondary = secondary,
                dark = dark,
            )
            EditableProfileField(
                icon = Icons.Filled.Link,
                label = stringResource(R.string.profile_editor_website),
                value = website,
                placeholder = stringResource(R.string.profile_editor_website_placeholder),
                onValueChange = onWebsiteChange,
                primary = primary,
                secondary = secondary,
                dark = dark,
                singleLine = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, null, tint = primary.copy(0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.profile_editor_bio),
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$characterCount/150",
                    color = if (characterCount > 150) Color.Red else secondary.copy(0.9f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
            Box {
                BasicTextField(
                    value = bio,
                    onValueChange = onBioChange,
                    textStyle = TextStyle(color = primary, fontSize = 15.sp),
                    cursorBrush = SolidColor(primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .background(
                            if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.035f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(12.dp),
                )
                if (bio.isEmpty()) {
                    Text(
                        stringResource(R.string.profile_editor_bio_placeholder),
                        color = primary.copy(if (dark) 0.3f else 0.28f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyProfileField(
    icon: ImageVector,
    label: String,
    value: String,
    primary: Color,
    secondary: Color,
    dark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = primary.copy(0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = secondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.profile_editor_not_editable),
                color = primary.copy(if (dark) 0.4f else 0.35f),
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(
                        if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.04f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            value,
            color = primary,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.035f),
                    RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        )
    }
}

@Composable
private fun EditableProfileField(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    primary: Color,
    secondary: Color,
    dark: Boolean,
    singleLine: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = primary.copy(0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = secondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = primary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(primary),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.035f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(16.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = primary.copy(if (dark) 0.3f else 0.28f), fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsSection(
    selectedInterests: Set<String>,
    primary: Color,
    secondary: Color,
    dark: Boolean,
    onEdit: () -> Unit,
    localize: (String) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = primary.copy(0.8f), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.profile_editor_interests_title),
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.creator_edit),
                color = primary,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text(
            stringResource(R.string.profile_editor_interests_description),
            color = secondary,
            fontSize = 14.sp,
        )

        if (selectedInterests.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.035f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    null,
                    tint = primary.copy(if (dark) 0.2f else 0.16f),
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    stringResource(R.string.profile_editor_interests_empty_title),
                    color = primary.copy(0.8f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Text(
                    stringResource(R.string.profile_editor_interests_empty_subtitle),
                    color = secondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    stringResource(R.string.profile_editor_add_interests),
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                selectedInterests.sorted().forEach { interest ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(InterestEmojiHelper.emoji(interest), fontSize = 14.sp)
                        Text(
                            localize(interest),
                            color = primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoActionsSheetContent(
    primary: Color,
    canDelete: Boolean,
    onLibrary: () -> Unit,
    onCamera: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(36.dp))
            Text(
                stringResource(R.string.profile_editor_change),
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(36.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 18.dp)) {
            PhotoActionRow(Icons.Filled.PhotoLibrary, stringResource(R.string.profile_editor_library), primary, false, onLibrary)
            PhotoActionRow(Icons.Filled.CameraAlt, stringResource(R.string.creator_camera), primary, false, onCamera)
            if (canDelete) {
                PhotoActionRow(
                    Icons.Filled.Delete,
                    stringResource(R.string.profile_editor_delete_photo),
                    primary,
                    true,
                    onDelete,
                )
            }
        }
    }
}

@Composable
private fun PhotoActionRow(
    icon: ImageVector,
    title: String,
    primary: Color,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (destructive) Color.Red.copy(0.9f) else primary,
            modifier = Modifier.width(20.dp),
        )
        Text(
            title,
            color = if (destructive) Color.Red.copy(0.9f) else primary,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun InterestsPickerSheet(
    available: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    primary: Color,
    secondary: Color,
    dark: Boolean,
    localize: (String) -> String,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(36.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.profile_editor_interests_navigation_title),
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Text(
                    stringResource(R.string.profile_editor_interests_select_title),
                    color = secondary,
                    fontSize = 13.sp,
                )
            }
            Text(
                "${selected.size}/5",
                color = if (selected.size >= 5) Color.Red else primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(available, key = { it }) { interest ->
                val isSelected = interest in selected
                val disabled = !isSelected && selected.size >= 5
                InterestPickerRow(
                    interest = interest,
                    label = localize(interest),
                    isSelected = isSelected,
                    isDisabled = disabled,
                    primary = primary,
                    dark = dark,
                    onTap = {
                        onChange(
                            when {
                                isSelected -> selected - interest
                                selected.size < 5 -> selected + interest
                                else -> selected
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun InterestPickerRow(
    interest: String,
    label: String,
    isSelected: Boolean,
    isDisabled: Boolean,
    primary: Color,
    dark: Boolean,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (isDisabled) 0.5f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false)
                } else {
                    Modifier.background(
                        if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.04f),
                        RoundedCornerShape(12.dp),
                    )
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        isSelected && dark -> Color.White.copy(0.22f)
                        isSelected -> Color.Black.copy(0.14f)
                        dark -> Color.White.copy(0.10f)
                        else -> Color.Black.copy(0.08f)
                    },
                ),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !isDisabled, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(InterestEmojiHelper.emoji(interest), fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = primary,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, tint = primary, modifier = Modifier.size(18.dp))
        }
    }
}

private suspend fun uploadProfileImage(bitmap: Bitmap): String? {
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
    return runCatching {
        val old = FirestoreService().fetchUserProfile(userId).profileImagePath
        val path = StorageService.uploadProfileImage(userId, bitmap)
        FirestoreService().updateProfilePicture(userId, path)
        StorageService.deleteProfileImage(userId, old)
        path
    }.getOrNull()
}
