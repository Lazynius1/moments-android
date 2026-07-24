package com.moments.android.views.profile.editor.sections

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.profile.editor.PhotoCropEditorView
import com.moments.android.views.profile.editor.ProfileAlbumInfo

/** Port de `ProfileEditorPickerViews.swift`. */
@Composable
fun ProfileAlbumPickerView(albums: List<ProfileAlbumInfo>, selectedAlbum: ProfileAlbumInfo?, onAlbumSelected: (ProfileAlbumInfo) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) = Column(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6))) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.profile_editor_select_album), color = pickerPrimary(), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f)); Icon(Icons.Filled.Close, stringResource(R.string.common_close), tint = pickerPrimary(), modifier = Modifier.size(28.dp).clickable { onDismiss() }) }; LazyColumn { items(albums, key = ProfileAlbumInfo::id) { album -> ProfileAlbumRowView(album, album == selectedAlbum) { onAlbumSelected(album); onDismiss() } } } }
@Composable
fun ProfileAlbumRowView(album: ProfileAlbumInfo, isSelected: Boolean, onTap: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = onTap).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(pickerPrimary().copy(.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PhotoLibrary, null, tint = pickerPrimary()) }; Column(Modifier.weight(1f)) { Text(album.title, color = pickerPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(stringResource(R.string.profile_editor_album_count, album.assetCount), color = pickerSecondary(), fontSize = 14.sp) }; if (isSelected) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00A896)) }
@Composable
fun ProfileLibraryCropEntryView(onImageCropped: (Bitmap) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var uri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri = it }
    if (uri != null) { PhotoCropEditorView(uri!!, onImageCropped, onDismiss, modifier); return }
    Column(modifier.fillMaxSize().background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Filled.PhotoLibrary, null, tint = pickerPrimary(), modifier = Modifier.size(48.dp)); Text(stringResource(R.string.profile_editor_photos_access_title), color = pickerPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)); Text(stringResource(R.string.profile_editor_photos_access_body), color = pickerSecondary(), modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)); TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.profile_editor_allow_access)) }; TextButton(onDismiss) { Text(stringResource(R.string.common_cancel)) } }
}
@Composable private fun pickerPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
@Composable private fun pickerSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.55f) else Color.Black.copy(.55f)
