package com.moments.android.views.profile.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchAvailableInterests
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.removeProfilePicture
import com.moments.android.services.firestore.updateProfilePicture
import com.moments.android.services.storage.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Port de `ProfileEditor.swift`: biblioteca/crop y edición de bio, web e intereses. */
data class ProfileAlbumInfo(val id: String, val title: String, val assetCount: Int)

@Composable
fun GridPhotoPickerView(currentProfileImage: Bitmap?, onImageUploaded: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> selectedUri = uri }
    if (cropUri != null) { PhotoCropEditorView(cropUri!!, { bitmap -> uploadProfileImage(bitmap, onImageUploaded) }, { cropUri = null }); return }
    Column(modifier.fillMaxSize().background(editorBackground())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = editorPrimary(), modifier = Modifier.size(32.dp).clickable { onDismiss() }); Text(stringResource(R.string.profile_editor_library), color = editorPrimary(), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); if (selectedUri != null) TextButton({ cropUri = selectedUri }) { Text(stringResource(R.string.profile_editor_next)) } }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { if (selectedUri != null) AsyncImage(selectedUri, null, Modifier.fillMaxWidth().padding(20.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) else currentProfileImage?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.size(220.dp)) } }
        Button({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth().padding(20.dp)) { Icon(Icons.Filled.PhotoLibrary, null); Text(stringResource(R.string.profile_editor_library)) }
    }
}

@Composable
fun ModernEditProfileView(user: AppUser?, onSave: (bio: String, website: String?, interests: List<String>) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var bio by remember(user) { mutableStateOf(user?.bio.orEmpty()) }
    var website by remember(user) { mutableStateOf(user?.websiteUrl.orEmpty()) }
    var interests by remember(user) { mutableStateOf(user?.interests?.toSet().orEmpty()) }
    var availableInterests by remember { mutableStateOf<List<String>>(emptyList()) }
    var photoActions by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf<Uri?>(null) }
    var showInterests by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) showCrop = uri }
    LaunchedEffect(Unit) { availableInterests = runCatching { FirestoreService().fetchAvailableInterests() }.getOrDefault(emptyList()) }
    if (showCrop != null) { PhotoCropEditorView(showCrop!!, { bitmap -> uploadProfileImage(bitmap) { uploading = false }; showCrop = null; uploading = true }, { showCrop = null }); return }
    Column(modifier.fillMaxSize().background(editorBackground())) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = editorPrimary(), modifier = Modifier.size(34.dp).clickable { onDismiss() }); Text(stringResource(R.string.profile_editor_title), color = editorPrimary(), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); TextButton({ if (bio.length <= 150) onSave(bio, website.ifBlank { null }, interests.toList()) }, enabled = bio.length <= 150) { Text(stringResource(R.string.common_save)) } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(118.dp).clip(CircleShape).background(editorPrimary().copy(.08f)).clickable { photoActions = true }, contentAlignment = Alignment.Center) { if (user?.profileImagePath != null) AsyncImage(user.profileImagePath, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Icon(Icons.Filled.CameraAlt, null, tint = editorPrimary(), modifier = Modifier.size(42.dp)) }
            TextButton({ photoActions = true }, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Filled.Edit, null); Text(stringResource(R.string.profile_editor_change_photo)) }
            ProfileReadOnlyField(R.string.profile_editor_username, user?.username.orEmpty()); ProfileReadOnlyField(R.string.profile_editor_email, user?.email.orEmpty())
            OutlinedTextField(website, { website = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_editor_website)) }, singleLine = true)
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_editor_bio)) }, supportingText = { Text(stringResource(R.string.profile_editor_character_count, bio.length, 150)) }, minLines = 4, isError = bio.length > 150)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.profile_editor_interests), color = editorPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton({ showInterests = true }) { Text(stringResource(R.string.profile_editor_edit_interests)) } }
            if (interests.isEmpty()) Text(stringResource(R.string.profile_editor_interests_empty), color = editorSecondary()) else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { interests.sorted().forEach { interest -> Text(interest, color = editorPrimary(), modifier = Modifier.clip(RoundedCornerShape(50)).background(editorPrimary().copy(.08f)).padding(horizontal = 14.dp, vertical = 8.dp)) } }
        }
    }
    if (photoActions) AlertDialog({ photoActions = false }, title = { Text(stringResource(R.string.profile_editor_change_photo)) }, text = { Column { TextButton({ photoActions = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Filled.PhotoLibrary, null); Text(stringResource(R.string.profile_editor_library)) }; TextButton({ photoActions = false; deleteConfirm = true }) { Icon(Icons.Filled.Delete, null, tint = Color.Red); Text(stringResource(R.string.profile_editor_delete_photo), color = Color.Red) } } }, confirmButton = {})
    if (deleteConfirm) AlertDialog({ deleteConfirm = false }, title = { Text(stringResource(R.string.profile_editor_delete_photo)) }, text = { Text(stringResource(R.string.profile_editor_delete_photo_confirm)) }, dismissButton = { TextButton({ deleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }, confirmButton = { TextButton({ deleteConfirm = false; scope.launch { FirebaseAuth.getInstance().currentUser?.uid?.let { FirestoreService().removeProfilePicture(it) } } }) { Text(stringResource(R.string.common_delete), color = Color.Red) } })
    if (showInterests) InterestsPicker(availableInterests, interests, { interests = it }, { showInterests = false })
    if (uploading) Box(Modifier.fillMaxSize().background(editorBackground().copy(.84f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable private fun InterestsPicker(options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) = AlertDialog({ onDismiss() }, title = { Text(stringResource(R.string.profile_editor_interests)) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { options.forEach { interest -> val picked = interest in selected; Text(interest, color = editorPrimary(), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (picked) editorPrimary().copy(.1f) else Color.Transparent).clickable { onChange(if (picked) selected - interest else if (selected.size < 5) selected + interest else selected) }.padding(14.dp)) } } }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.common_save)) } })
@Composable private fun ProfileReadOnlyField(label: Int, value: String) = OutlinedTextField(value, { _ -> }, Modifier.fillMaxWidth(), label = { Text(stringResource(label)) }, enabled = false, singleLine = true)
private fun uploadProfileImage(bitmap: Bitmap, onUploaded: (String) -> Unit = {}) { val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) { runCatching { val old = FirestoreService().fetchUser(userId).profileImagePath; val path = StorageService.uploadProfileImage(userId, bitmap); FirestoreService().updateProfilePicture(userId, path); StorageService.deleteProfileImage(userId, old); path }.onSuccess(onUploaded) } }
@Composable private fun editorBackground() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
@Composable private fun editorPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
@Composable private fun editorSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.58f) else Color.Black.copy(.58f)
