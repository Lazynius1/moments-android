@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moments.android.views.login

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.shared.Surface

private data class PolicySection(@StringRes val title: Int, @StringRes val body: Int)

private val sections = listOf(
    PolicySection(R.string.privacy_summary_title, R.string.privacy_summary_body),
    PolicySection(R.string.privacy_data_title, R.string.privacy_data_body),
    PolicySection(R.string.privacy_use_title, R.string.privacy_use_body),
    PolicySection(R.string.privacy_nova_title, R.string.privacy_nova_body),
    PolicySection(R.string.privacy_visibility_title, R.string.privacy_visibility_body),
    PolicySection(R.string.privacy_messages_title, R.string.privacy_messages_body),
    PolicySection(R.string.privacy_permissions_title, R.string.privacy_permissions_body),
    PolicySection(R.string.privacy_ads_title, R.string.privacy_ads_body),
    PolicySection(R.string.privacy_moderation_title, R.string.privacy_moderation_body),
    PolicySection(R.string.privacy_providers_title, R.string.privacy_providers_body),
    PolicySection(R.string.privacy_retention_title, R.string.privacy_retention_body),
    PolicySection(R.string.privacy_rights_title, R.string.privacy_rights_body),
    PolicySection(R.string.privacy_minors_title, R.string.privacy_minors_body),
    PolicySection(R.string.privacy_contact_title, R.string.privacy_contact_body),
)

@Composable
fun PrivacyPolicySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Box(Modifier.fillMaxSize().background(Surface)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.privacy_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AuthColors.primary)
                    Text(stringResource(R.string.privacy_last_updated), fontSize = 14.sp, color = AuthColors.secondary(0.64f))
                }
                sections.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(section.title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary)
                        Text(stringResource(section.body), fontSize = 15.sp, lineHeight = 22.sp, color = AuthColors.secondary(0.78f))
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(34.dp).background(AuthColors.subtle(0.06f), CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.login_close), tint = AuthColors.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
