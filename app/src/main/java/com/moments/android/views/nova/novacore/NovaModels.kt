package com.moments.android.views.nova.novacore

import android.graphics.Bitmap
import java.util.Date
import java.util.UUID

data class NovaGroundingSource(val title: String, val url: String) {
    val id: String get() = url
}

data class NovaGroundingPayload(
    val sources: List<NovaGroundingSource>,
    val searchSuggestionsHtml: String?,
)

/** Chat model shared by Nova's screen, persistence, memory and AI layers. */
data class NovaChatMessage(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    val image: Bitmap? = null,
    val imageStoragePath: String? = null,
    val isUser: Boolean,
    val timestamp: Date = Date(),
    val isHistorical: Boolean = false,
    val isSystem: Boolean = false,
    var groundingSources: List<NovaGroundingSource> = emptyList(),
    var searchSuggestionsHtml: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is NovaChatMessage &&
        id == other.id && text == other.text && image == other.image && imageStoragePath == other.imageStoragePath &&
        isUser == other.isUser && isHistorical == other.isHistorical && isSystem == other.isSystem &&
        groundingSources == other.groundingSources && searchSuggestionsHtml == other.searchSuggestionsHtml

    override fun hashCode(): Int = arrayOf(
        id, text, image, imageStoragePath, isUser, isHistorical, isSystem, groundingSources, searchSuggestionsHtml,
    ).contentHashCode()
}
