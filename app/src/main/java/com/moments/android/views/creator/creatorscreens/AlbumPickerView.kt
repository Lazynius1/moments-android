package com.moments.android.views.creator.creatorscreens

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.creator.CreatorAlbumInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port de AlbumPickerView.swift.
 *
 * MediaStore no entrega una portada de álbum como Photos; se consulta
 * perezosamente el asset más reciente del bucket, equivalente al thumbnail que
 * Swift pide al PHImageManager al aparecer cada fila.
 */
@Composable
fun AlbumPickerView(
    albums: List<CreatorAlbumInfo>,
    selectedAlbum: CreatorAlbumInfo?,
    onAlbumSelected: (CreatorAlbumInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color.Black
    val surface = if (isDark) Color(0xFF182124).copy(alpha = .96f) else Color(0xFFF7F7F5)
    val selectedColor = Color(0xFF00A896)

    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = .30f), Color(0xFFFF6B8A).copy(alpha = .40f))),
                RoundedCornerShape(20.dp),
            )
            .padding(bottom = 20.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 16.dp)
                .size(width = 36.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = .60f)),
        )
        Text(
            text = stringResource(R.string.creator_album_select),
            color = contentColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumPickerRow(
                    album = album,
                    selected = selectedAlbum?.id == album.id,
                    selectedColor = selectedColor,
                    contentColor = contentColor,
                    onTap = { onAlbumSelected(album) },
                )
            }
        }
        Text(
            text = stringResource(R.string.common_cancel),
            color = contentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.White.copy(alpha = .10f))
                .border(1.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(25.dp))
                .clickable(onClick = onDismiss)
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun AlbumPickerRow(
    album: CreatorAlbumInfo,
    selected: Boolean,
    selectedColor: Color,
    contentColor: Color,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    var thumbnailUri by remember(album.id) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(album.id, album.bucketId) {
        thumbnailUri = withContext(Dispatchers.IO) {
            latestAlbumThumbnail(context, album.bucketId)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedColor.copy(alpha = .10f) else Color.Transparent)
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = .30f)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(Icons.Filled.PhotoLibrary, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(album.title, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                pluralStringResource(R.plurals.creator_album_elements, album.assetCount, album.assetCount),
                color = Color.Gray.copy(alpha = .80f),
                fontSize = 14.sp,
            )
        }
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (selected) selectedColor else Color.Gray.copy(alpha = .50f),
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun latestAlbumThumbnail(context: Context, bucketId: String?): Uri? {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
    val selection = buildString {
        append("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
        if (bucketId != null) append(" AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?")
    }
    val selectionArgs = buildList {
        add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        if (bucketId != null) add(bucketId)
    }.toTypedArray()
    context.contentResolver.query(
        collection,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC",
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return Uri.withAppendedPath(collection, cursor.getLong(0).toString())
        }
    }
    return null
}
