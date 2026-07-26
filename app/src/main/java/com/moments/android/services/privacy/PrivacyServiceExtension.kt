package com.moments.android.services.privacy

import com.moments.android.models.Moment
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Port de `PrivacyServiceExtension.swift`.
 * Solo filtrado de momentos visibles; el resto vive en PrivacyService.kt (= PrivacyService.swift).
 */
suspend fun PrivacyService.filterVisibleContent(
    moments: List<Moment>,
    viewerId: String,
): List<Moment> {
    if (moments.isEmpty()) return emptyList()
    val visibleIds = coroutineScope {
        moments.map { moment ->
            async {
                runCatching {
                    if (canViewMoment(moment, viewerId)) moment.id else null
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().toSet()
    }
    return moments.filter { it.id != null && it.id in visibleIds }
}

suspend fun PrivacyService.canViewMoment(moment: Moment, viewerId: String): Boolean =
    canUserViewMomentEnhanced(moment, viewerId)
