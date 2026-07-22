package com.moments.android.views.feed.sharing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.content.FeedMoment

/** Port de `share.swift` — sheet de compartir momento. */
fun buildMomentShareUrl(moment: FeedMoment): String {
    val base = "https://momentsapp.app/moment/${moment.id}"
    return if (moment.authorId.isNotBlank()) "$base?a=${moment.authorId}" else base
}

@Composable
fun ModernShareBottomSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    onSendMessage: () -> Unit,
    onAddToStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.feed_share_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        ShareActionRow(stringResource(R.string.feed_share_message)) { onSendMessage() }
        ShareActionRow(stringResource(R.string.feed_share_story)) { onAddToStory() }
        ShareActionRow(stringResource(R.string.feed_share_external)) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildMomentShareUrl(moment))
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        }
    }
}

@Composable
private fun ShareActionRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 15.sp)
    }
}

enum class ShareSheetViewState { Main, Messaging }
