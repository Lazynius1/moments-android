package com.moments.android.views.login

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsSheetHeader
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
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = true,
        containerColor = Surface,
    ) { _ ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MomentsSheetHeader(title = stringResource(R.string.privacy_title), titleSize = 20.sp)
            Text(
                stringResource(R.string.privacy_last_updated),
                fontSize = 14.sp,
                color = AuthColors.secondary(0.64f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            sections.forEach { section ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Text(stringResource(section.title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AuthColors.primary)
                    Text(stringResource(section.body), fontSize = 15.sp, lineHeight = 22.sp, color = AuthColors.secondary(0.78f))
                }
            }
        }
    }
}
