package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.profile.core.ProfileColors
import com.moments.android.views.shared.AppErrorBanner
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay

/** Port de `HighlightNameCoverStep.swift`. */
@Composable
fun HighlightNameCoverStep(viewModel: HighlightCreateFlowViewModel) {
    val coverSize = 118.dp
    val coverUrl = viewModel.coverStory?.mediaItem?.thumbnailUrl
        ?: viewModel.coverStory?.mediaItem?.url
        ?: viewModel.editingHighlight?.coverImageUrl
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focusRequester.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        viewModel.errorMessage?.let { errorMessage ->
            AppErrorBanner(
                message = errorMessage,
                onRetry = { viewModel.errorMessage = null },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(coverSize)
                    .clip(CircleShape)
                    .clickable { viewModel.showCoverPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Photo,
                            null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.highlighted_stories_edit_cover),
                color = ProfileColors.accent,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.clickable { viewModel.showCoverPicker = true },
            )
        }

        Spacer(Modifier.height(40.dp))

        BasicTextField(
            value = viewModel.title,
            onValueChange = { viewModel.title = it },
            singleLine = true,
            textStyle = TextStyle(
                color = ProfileColors.textPrimary(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(ProfileColors.textPrimary()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (viewModel.title.isEmpty()) {
                        Text(
                            stringResource(R.string.highlighted_stories_default_title),
                            color = ProfileColors.textSecondary(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(Modifier.weight(1f))
    }

    if (viewModel.showCoverPicker) {
        MomentsModalSheet(
            onDismissRequest = { viewModel.showCoverPicker = false },
            largeOnly = false,
        ) {
            HighlightCoverPickerSheet(
                stories = viewModel.selectedStories,
                selectedCoverId = viewModel.coverStory?.id,
                onSelect = { viewModel.coverStory = it },
                onDismiss = { viewModel.showCoverPicker = false },
            )
        }
    }
}
