package com.moments.android.views.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.legacyPoppinsSize

/** Port de `AppErrorBanner.swift`. */
@Composable
fun AppErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    retryTitle: String = stringResource(R.string.maps_error_retry),
    onRetry: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .momentsChromeGlass(RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color(0xFFFF9500),
            modifier = Modifier.size(15.dp),
        )

        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.weight(1f))

        if (onRetry != null) {
            Text(
                text = retryTitle,
                color = MaterialTheme.colorScheme.primary,
                fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}
