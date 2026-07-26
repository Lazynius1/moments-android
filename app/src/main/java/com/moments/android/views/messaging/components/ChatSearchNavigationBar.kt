package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.AdaptiveColors

/** Port de `ChatSearchNavigationBar.swift`. */
@Composable
fun ChatSearchNavigationBar(
    text: String,
    onTextChange: (String) -> Unit,
    adaptiveColors: AdaptiveColors,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(adaptiveColors.chatBackground.first())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatInThreadSearchField(
            text = text,
            onTextChange = onTextChange,
            adaptiveColors = adaptiveColors,
            onClear = onClear,
            onSubmit = onSubmit,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = adaptiveColors.primary,
            )
        }
    }
}
