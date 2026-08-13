package com.moments.android.views.profile.editor.sections

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import com.moments.android.views.profile.editor.PhotoCropEditorView
import com.moments.android.views.profile.editor.ProfileAlbumInfo
import com.moments.android.views.profile.editor.fetchAlbumThumbnailUri
import com.moments.android.views.profile.editor.fetchMostRecentProfileImageUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port de `ProfileAlbumPickerView` — lista de álbumes con thumbnails.
 * Fondo canvas AdaptiveColors (sin material/blur iOS).
 */
@Composable
fun ProfileAlbumPickerView(
    albums: List<ProfileAlbumInfo>,
    selectedAlbum: ProfileAlbumInfo?,
    onAlbumSelected: (ProfileAlbumInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.55f) else Color.Black.copy(0.55f)
    val thumbnails = remember { mutableStateMapOf<String, Uri>() }

    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(canvas)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(0.3f), Color(0xFF00A896).copy(0.4f)),
                ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ),
    ) {
        Box(
            Modifier
                .padding(top = 12.dp)
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Gray.copy(0.3f)),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.profile_editor_select_album),
                color = primary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = Color.Gray.copy(0.5f),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
            )
        }

        HorizontalDivider(color = primary.copy(0.08f))

        LazyColumn(Modifier.padding(vertical = 8.dp)) {
            items(albums, key = ProfileAlbumInfo::id) { album ->
                LaunchedEffect(album.id) {
                    if (thumbnails[album.id] == null) {
                        val uri = withContext(Dispatchers.IO) {
                            fetchAlbumThumbnailUri(context, album.bucketId)
                        }
                        if (uri != null) thumbnails[album.id] = uri
                    }
                }
                ProfileAlbumRowView(
                    album = album,
                    thumbnailUri = thumbnails[album.id],
                    isSelected = selectedAlbum?.id == album.id,
                    primary = primary,
                    secondary = secondary,
                    onTap = {
                        onAlbumSelected(album)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** Port de `ProfileAlbumRowView`. */
@Composable
fun ProfileAlbumRowView(
    album: ProfileAlbumInfo,
    thumbnailUri: Uri?,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Color = pickerPrimary(),
    secondary: Color = pickerSecondary(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Gray.copy(0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUri != null) {
                AsyncImage(
                    thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.PhotoLibrary, null, tint = primary.copy(0.45f))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                album.title,
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                stringResource(R.string.profile_editor_album_count, album.assetCount),
                color = secondary,
                fontSize = 14.sp,
            )
        }
        if (isSelected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF00A896),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Port de `ProfileLibraryCropEntryView`.
 * Con permiso → foto más reciente → [PhotoCropEditorView]
 * (grid del crop permite cambiar; PermissionPrimerGate ≡ iOS).
 */
@Composable
fun ProfileLibraryCropEntryView(
    onImageCropped: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.65f) else Color.Black.copy(0.6f)

    val photosGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS) }
    var isLoading by remember { mutableStateOf(true) }
    var initialUri by remember { mutableStateOf<Uri?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }
    var showAllowPrompt by remember { mutableStateOf(false) }

    fun fetchMostRecent() {
        isLoading = true
        permissionDenied = false
        showAllowPrompt = false
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                fetchMostRecentProfileImageUri(context)
            }
            permissionGranted = true
            initialUri = uri
            isLoading = false
            if (uri == null) onDismiss()
        }
    }

    fun openSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    LaunchedEffect(Unit) {
        if (hasPhotosPermission(context)) {
            fetchMostRecent()
        } else {
            photosGate.requestAccess(context) { fetchMostRecent() }
            isLoading = false
            showAllowPrompt = true
        }
    }

    var wasPresenting by remember { mutableStateOf(false) }
    LaunchedEffect(photosGate.isPresenting) {
        if (wasPresenting && !photosGate.isPresenting && !permissionGranted) {
            if (!hasPhotosPermission(context)) {
                permissionDenied = true
                showAllowPrompt = false
                isLoading = false
            }
        }
        wasPresenting = photosGate.isPresenting
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        when {
            initialUri != null -> {
                PhotoCropEditorView(
                    originalUri = initialUri!!,
                    onSave = onImageCropped,
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            isLoading -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MomentsCircularProgressIndicator()
                    Text(
                        stringResource(R.string.profile_editor_loading_photos),
                        color = primary.copy(0.75f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            permissionDenied -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        null,
                        tint = primary.copy(0.7f),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.profile_editor_photos_access_title),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        stringResource(R.string.profile_editor_photos_access_body),
                        color = secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        stringResource(R.string.creator_permissions_open_settings),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable { openSettings() }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            showAllowPrompt && !photosGate.isPresenting -> {
                // Fallback si el usuario cerró el primer sin conceder — botón Allow access
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.profile_editor_photos_access_title),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        stringResource(R.string.profile_editor_allow_access),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable {
                                photosGate.requestAccess(context) { fetchMostRecent() }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }

        PermissionPrimerGateHost(gate = photosGate)
    }
}

@Composable
private fun pickerPrimary() =
    if (isSystemInDarkTheme()) Color.White else Color.Black

@Composable
private fun pickerSecondary() =
    if (isSystemInDarkTheme()) Color.White.copy(0.55f) else Color.Black.copy(0.55f)

private fun hasPhotosPermission(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        val selected = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val full = listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        ).all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return selected || full
    }
    val perms = if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return perms.all {
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
