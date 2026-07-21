package com.moments.android.models

import java.util.Date

/**
 * Afinidad hacia otro usuario, calculada en local. Nunca sale del dispositivo; se usa
 * para ordenar feeds y sugerencias. Equivalente al @Model (SwiftData) UserAffinity de iOS.
 *
 * NOTA: la persistencia local (SwiftData → Room) se añadirá al montar la capa de caché;
 * por ahora es solo el modelo de datos.
 */
data class UserAffinity(
    val ownerUserId: String,
    val targetUserId: String,
    val score: Double = 0.0,
    val lastInteractionDate: Date = Date(),
    val interactionCounts: Map<String, Int> = emptyMap(),
) {
    /** Clave compuesta única: ownerUserId|targetUserId (evita mezclar cuentas locales). */
    val affinityKey: String get() = makeAffinityKey(ownerUserId, targetUserId)

    companion object {
        fun makeAffinityKey(ownerUserId: String, targetUserId: String): String = "$ownerUserId|$targetUserId"
    }
}
