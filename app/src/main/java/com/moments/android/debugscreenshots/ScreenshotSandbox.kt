package com.moments.android.debugscreenshots

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

/**
 * Aísla Firebase en modo capturas: Firestore solo en memoria, host inalcanzable
 * y red apagada; Storage y Functions a un emulador inexistente. Auth no se toca:
 * la sesión real del teléfono sigue intacta (sin signOut ni emulador).
 * Llamar al principio de `Application.onCreate`, antes de cualquier uso de Firebase.
 */
object ScreenshotSandbox {
    const val DEAD_HOST = "127.0.0.1"
    const val DEAD_PORT = 1
    private const val FUNCTIONS_REGION = "europe-southwest1"

    fun configure(context: Context) {
        ScreenshotNetworkGuard.install(context)

        // Sin runCatching: si Firestore no queda aislado, mejor fallar que usar producción.
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setHost("$DEAD_HOST:$DEAD_PORT")
            .setSslEnabled(false)
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        db.disableNetwork()

        FirebaseStorage.getInstance().useEmulator(DEAD_HOST, DEAD_PORT)
        FirebaseFunctions.getInstance().useEmulator(DEAD_HOST, DEAD_PORT)
        FirebaseFunctions.getInstance(FUNCTIONS_REGION).useEmulator(DEAD_HOST, DEAD_PORT)
    }
}
