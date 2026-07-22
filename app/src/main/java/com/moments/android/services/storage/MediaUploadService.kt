package com.moments.android.services.storage

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Errores de Storage (port de StorageError.swift).
sealed class StorageError(message: String) : Exception(message) {
    object InvalidData : StorageError("invalid data")
    object UploadFailed : StorageError("upload failed")
    object UrlRetrievalFailed : StorageError("url retrieval failed")
    object DeleteFailed : StorageError("delete failed")
    object InvalidPath : StorageError("invalid path")
}

// Carga a subir: bytes en memoria o fichero (Uri). Port de MediaUploadPayload.
sealed class MediaUploadPayload {
    data class Data(val bytes: ByteArray) : MediaUploadPayload()
    data class File(val uri: Uri) : MediaUploadPayload()
}

// Port de MediaUploadService.swift. Retiene las UploadTask activas para poder cancelarlas
// por prefijo de ruta (equivalente a `sessions` en iOS).
object MediaUploadService {

    private val storage get() = FirebaseStorage.getInstance().reference
    private val sessions = ConcurrentHashMap<String, UploadTask>()

    // MARK: - Subidas públicas (resuelven downloadURL con token)

    suspend fun upload(
        target: StorageUploadTarget,
        payload: MediaUploadPayload,
        progress: ((Double) -> Unit)? = null
    ): String {
        // Data marcada como cifrada → devuelve objectPath, no downloadURL.
        if (payload is MediaUploadPayload.Data && target.customMetadata["encrypted"] == "true") {
            return uploadEncryptedBlob(target, payload.bytes, progress)
        }
        return startUpload(target.objectPath, target, payload, progress)
    }

    // Subida cifrada (blob en memoria): devuelve el objectPath en vez de downloadURL.
    suspend fun uploadEncryptedBlob(
        target: StorageUploadTarget,
        data: ByteArray,
        progress: ((Double) -> Unit)? = null
    ): String {
        val patched = target.copy(
            customMetadata = target.customMetadata + ("returnObjectPath" to "true")
        )
        return startUpload(patched.objectPath, patched, MediaUploadPayload.Data(data), progress)
    }

    // Variante para ciphertext grande leído desde fichero.
    suspend fun uploadEncryptedFile(
        target: StorageUploadTarget,
        fileUri: Uri,
        progress: ((Double) -> Unit)? = null
    ): String {
        val patched = target.copy(
            customMetadata = target.customMetadata + ("returnObjectPath" to "true")
        )
        return startUpload(patched.objectPath, patched, MediaUploadPayload.File(fileUri), progress)
    }

    private suspend fun startUpload(
        path: String,
        target: StorageUploadTarget,
        payload: MediaUploadPayload,
        progress: ((Double) -> Unit)?
    ): String {
        val ref = storage.child(path)
        val metadata = StorageMetadata.Builder().apply {
            setContentType(target.contentType)
            target.customMetadata.forEach { (k, v) -> setCustomMetadata(k, v) }
        }.build()

        // Solo chat cifrado devuelve objectPath; moments/stories necesitan downloadURL con token.
        val shouldResolveDownloadURL = target.customMetadata["returnObjectPath"] != "true"

        val uploadTask: UploadTask = when (payload) {
            is MediaUploadPayload.Data -> ref.putBytes(payload.bytes, metadata)
            is MediaUploadPayload.File -> ref.putFile(payload.uri, metadata)
        }
        sessions[path] = uploadTask

        try {
            suspendCancellableCoroutine { cont ->
                if (progress != null) {
                    uploadTask.addOnProgressListener { snapshot ->
                        if (snapshot.totalByteCount > 0) {
                            progress(snapshot.bytesTransferred.toDouble() / snapshot.totalByteCount.toDouble())
                        }
                    }
                }
                uploadTask.addOnSuccessListener { cont.resume(Unit) }
                uploadTask.addOnFailureListener { e -> cont.resumeWithException(e) }
                cont.invokeOnCancellation { uploadTask.cancel() }
            }
        } finally {
            sessions.remove(path)
        }

        if (!shouldResolveDownloadURL) return path
        val url = ref.downloadUrl.await() ?: throw StorageError.UrlRetrievalFailed
        return url.toString()
    }

    // Cancela subidas activas cuyo path empiece por el prefijo dado.
    fun cancelUploads(pathPrefix: String) {
        val paths = sessions.keys.filter { it.startsWith(pathPrefix) }
        for (path in paths) {
            sessions.remove(path)?.cancel()
        }
    }

    // Convierte object path guardado en Firestore a URL HTTPS con token.
    suspend fun resolveDownloadURL(storedValue: String): String {
        val trimmed = storedValue.trim()
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return trimmed

        val objectPath = StoragePathBuilder.extractObjectPath(trimmed)
        if (objectPath.isEmpty()) throw StorageError.InvalidPath

        val url = storage.child(objectPath).downloadUrl.await() ?: throw StorageError.UrlRetrievalFailed
        return url.toString()
    }

    suspend fun delete(pathOrURL: String) {
        val objectPath = StoragePathBuilder.extractObjectPath(pathOrURL)
        if (objectPath.isEmpty()) throw StorageError.InvalidPath
        storage.child(objectPath).delete().await()
    }
}
