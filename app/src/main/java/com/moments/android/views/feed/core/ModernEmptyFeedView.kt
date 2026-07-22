package com.moments.android.views.feed.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.utilities.momentsPress
import com.moments.android.views.feed.controls.FeedType

/**
 * Port 1:1 de `ModernEmptyFeedView.swift`.
 */
@Composable
fun ModernEmptyFeedView(
    feedType: FeedType,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primaryText = if (isDark) Color.White else Color.Black
    val secondaryText = primaryText.copy(alpha = if (isDark) 0.58f else 0.52f)

    val emptyTitle = when (feedType) {
        FeedType.Following -> stringResource(R.string.feed_empty_following_title)
        FeedType.ForYou -> stringResource(R.string.feed_empty_foryou_title)
    }
    val emptyDescription = when (feedType) {
        FeedType.Following -> stringResource(R.string.feed_empty_following_description)
        FeedType.ForYou -> stringResource(R.string.feed_empty_foryou_description)
    }
    val primaryActionTitle = when (feedType) {
        FeedType.Following -> stringResource(R.string.feed_empty_action_find_people)
        FeedType.ForYou -> stringResource(R.string.feed_empty_action_explore)
    }
    // iOS SF Symbols: magnifyingglass / arrow.right
    val primaryActionIcon: ImageVector = when (feedType) {
        FeedType.Following -> Icons.Filled.Search
        FeedType.ForYou -> Icons.AutoMirrored.Filled.ArrowForward
    }
    // iOS SF Symbols: person.2 / sparkles
    val headerIcon: ImageVector = when (feedType) {
        FeedType.Following -> Icons.Outlined.People
        FeedType.ForYou -> Icons.Outlined.AutoAwesome
    }

    fun primaryAction() {
        // iOS: ambos cases → LegacyNavigationBridge.showExplore()
        LegacyNavigationBridge.showExplore()
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .momentsEmptyStateAppear(appearedOffsetY = -28f, initialOffsetY = -14f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        // iconView — 76×76, chrome glass Circle, icon 31 medium
        Box(
            Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(31.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = emptyTitle,
                color = primaryText,
                fontSize = with(density) { legacyPoppinsSize(context, 22).toSp() },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = emptyDescription,
                color = secondaryText,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        // Button: height 50, HStack spacing 10, chrome Capsule interactive, momentsPress
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .padding(top = 4.dp)
                .momentsPress(interactionSource = interaction)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { primaryAction() },
                )
                .height(50.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = primaryActionTitle,
                color = primaryText,
                fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = primaryActionIcon,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
