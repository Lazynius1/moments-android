package com.moments.android.views.messaging.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.ChatTranslationService
import kotlinx.coroutines.CancellationException

@Composable
internal fun ChatTranslationContainer(
    text: String,
    messageId: String,
    isOutgoing: Boolean,
    content: @Composable (String) -> Unit,
) {
    if (isOutgoing) {
        content(text)
        return
    }
    val target = LocalConfiguration.current.locales[0].toLanguageTag()
    var eligible by remember(messageId, text, target, isOutgoing) { mutableStateOf(false) }
    var translation by remember(messageId, text, target) { mutableStateOf<String?>(null) }
    var translated by remember(messageId, text, target) { mutableStateOf(false) }
    var requested by remember(messageId, text, target) { mutableStateOf(false) }
    var failed by remember(messageId, text, target) { mutableStateOf(false) }
    LaunchedEffect(messageId, text, target, isOutgoing) {
        eligible = !isOutgoing && try { ChatTranslationService.needsTranslation(text, target) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    LaunchedEffect(requested, messageId, text, target) {
        if (requested) {
            failed = false
            try {
                translation = ChatTranslationService.translate(text, target)
                translated = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { requested = false }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content(if (translated) translation ?: text else text)
        if (eligible) {
            val label = stringResource(when {
                requested -> R.string.caption_translating
                failed -> R.string.caption_translation_retry
                translated -> R.string.caption_show_original
                else -> R.string.chat_translate_message
            })
            IconButton(
                onClick = {
                    if (translation != null) translated = !translated else requested = true
                },
                enabled = !requested,
                modifier = Modifier.size(32.dp).semantics { contentDescription = label },
            ) {
                if (requested) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_chat_translate),
                        tint = when {
                            failed -> MaterialTheme.colorScheme.error
                            translated -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
