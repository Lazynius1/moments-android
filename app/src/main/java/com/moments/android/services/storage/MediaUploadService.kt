package com.moments.android.services.storage

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Port de `MediaUploadPayload`. */
sealed class MediaUploadPayload {
    data class Data(val bytes: ByteArray) : MediaUploadPayload() {
        override fun equals(other: Any?): Boolean =
            other is Data && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    data class File(val uri: Uri) : MediaUploadPayload()
}

/** Port de `UploadSession` — retiene la UploadTask para cancelación por prefijo. */
private class UploadSession(
    val objectPath: String,
    val task: UploadTask,
) {
    @Volatile var didFinish: Boolean = false
}

/**
 * Port de `MediaUploadService.swift`.
 *
 * Retiene las `UploadTask` activas en `sessions` (como iOS): si no se retienen,
 * el GC / fin de scope puede cancelar la subida y el backend responde mal.
 *
 * `StorageError` vive en [StorageService] (paridad iOS: StorageService.swift).
 */
object MediaUploadService {

    private val storage get() = FirebaseStorage.getInstance().reference
    private val sessions = ConcurrentHashMap<String, UploadSession>()

    // MARK: - Subidas públicas

    /**
     * Port de `upload(target:payload:progress:) async throws`.
     * Data con `encrypted=true` → `uploadEncryptedBlob` (objectPath, no downloadURL).
     */
    suspend fun upload(
        target: StorageUploadTarget,
        payload: MediaUploadPayload,
        progress: ((Double) -> Unit)? = null,
    ): String {
        if (payload is MediaUploadPayload.Data && target.customMetadata["encrypted"] == "true") {
            return uploadEncryptedBlob(target, payload.bytes, progress)
        }
        return startUpload(
            path = target.objectPath,
            target = target,
            payload = payload,
            progress = progress,
        )
    }

    /**
     * Port de `uploadEncryptedBlob`.
     * `returnObjectPath=true` → startUpload devuelve objectPath, no downloadURL.
     */
    suspend fun uploadEncryptedBlob(
        target: StorageUploadTarget,
        data: ByteArray,
        progress: ((Double) -> Unit)? = null,
    ): String {
        val patched = target.copy(
            customMetadata = target.customMetadata + ("returnObjectPath" to "true"),
        )
        return startUpload(
            path = patched.objectPath,
            target = patched,
            payload = MediaUploadPayload.Data(data),
            progress = progress,
        )
    }

    /**
     * Port de `uploadEncryptedFile` — ciphertext grande desde fichero.
     */
    suspend fun uploadEncryptedFile(
        target: StorageUploadTarget,
        fileUri: Uri,
        progress: ((Double) -> Unit)? = null,
    ): String {
        val patched = target.copy(
            customMetadata = target.customMetadata + ("returnObjectPath" to "true"),
        )
        return startUpload(
            path = patched.objectPath,
            target = patched,
            payload = MediaUploadPayload.File(fileUri),
            progress = progress,
        )
    }

    // MARK: - startUpload (núcleo)

    private suspend fun startUpload(
        path: String,
        target: StorageUploadTarget,
        payload: MediaUploadPayload,
        progress: ((Double) -> Unit)?,
    ): String {
        val ref = storage.child(path)
        val metadata = StorageMetadata.Builder().apply {
            setContentType(target.contentType)
            target.customMetadata.forEach { (k, v) -> setCustomMetadata(k, v) }
        }.build()

        // Solo returnObjectPath → objectPath; moments/stories necesitan downloadURL con token.
        val shouldResolveDownloadURL = target.customMetadata["returnObjectPath"] != "true"

        val uploadTask: UploadTask = when (payload) {
            is MediaUploadPayload.Data -> ref.putBytes(payload.bytes, metadata)
            is MediaUploadPayload.File -> ref.putFile(payload.uri, metadata)
        }

        val session = UploadSession(objectPath = path, task = uploadTask)
        sessions[path] = session

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                var finished = false
                fun finishSuccess() {
                    if (finished) return
                    finished = true
                    cont.resume(Unit)
                }
                fun finishFailure(e: Exception) {
                    if (finished) return
                    finished = true
                    cont.resumeWithException(e)
                }

                if (progress != null) {
                    uploadTask.addOnProgressListener { snapshot ->
                        val total = snapshot.totalByteCount
                        if (total > 0) {
                            progress(snapshot.bytesTransferred.toDouble() / total.toDouble())
                        }
                    }
                }
                uploadTask.addOnSuccessListener { finishSuccess() }
                uploadTask.addOnFailureListener { e ->
                    finishFailure(e as? Exception ?: StorageError.UploadFailed)
                }
                cont.invokeOnCancellation {
                    if (!session.didFinish) {
                        uploadTask.cancel()
                    }
                }
            }
        } catch (e: Exception) {
            session.didFinish = true
            sessions.remove(path)
            throw e
        }

        // iOS: finish() (quita sesión) solo tras success o tras resolver downloadURL.
        // Aquí resolvemos URL mientras la sesión sigue viva (cancelable), luego limpiamos.
        return try {
            if (!shouldResolveDownloadURL) {
                path
            } else {
                val url = ref.downloadUrl.await()
                    ?: throw StorageError.UrlRetrievalFailed
                url.toString()
            }
        } finally {
            session.didFinish = true
            sessions.remove(path)
        }
    }

    /**
     * Cancela subidas activas cuyo path empieza por el prefijo
     * (p. ej. `users/{uid}/moments/{momentId}/`).
     */
    fun cancelUploads(pathPrefix: String) {
        val paths = sessions.keys.filter { it.startsWith(pathPrefix) }
        for (path in paths) {
            val session = sessions[path] ?: continue
            if (session.didFinish) continue
            session.task.cancel()
            session.didFinish = true
            sessions.remove(path)
        }
    }

    /**
     * Convierte object path guardado en Firestore a URL HTTPS con token
     * (para mostrar en feed/perfil).
     */
    suspend fun resolveDownloadURL(storedValue: String): String {
        val trimmed = storedValue.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed
        }

        val objectPath = StoragePathBuilder.extractObjectPath(trimmed)
        if (objectPath.isEmpty()) throw StorageError.InvalidPath

        val url = storage.child(objectPath).downloadUrl.await()
            ?: throw StorageError.UrlRetrievalFailed
        return url.toString()
    }

    suspend fun delete(pathOrURL: String) {
        val objectPath = StoragePathBuilder.extractObjectPath(pathOrURL)
        if (objectPath.isEmpty()) throw StorageError.InvalidPath
        storage.child(objectPath).delete().await()
    }
}
