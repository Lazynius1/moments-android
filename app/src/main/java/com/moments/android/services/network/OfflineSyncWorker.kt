package com.moments.android.services.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moments.android.R
import com.moments.android.models.cache.CachedAction
import com.moments.android.services.persistence.LocalPersistenceService
import java.util.concurrent.TimeUnit

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        OfflineSyncService.syncPendingActions(requireAutomaticSync = false)
    }.fold(
        onSuccess = {
            // El drenado inmediato (one-time) reintenta mientras queden mensajes en cola; el
            // periódico se encarga del resto cuando se agoten los intentos.
            if (tags.contains(TAG_IMMEDIATE) && runAttemptCount < MAX_IMMEDIATE_ATTEMPTS && hasPendingChatActions()) {
                Result.retry()
            } else {
                Result.success()
            }
        },
        onFailure = { Result.retry() },
    )

    /** Requerido por trabajo expedited en API < 31 (corre como servicio en primer plano). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setContentTitle(context.getString(R.string.chat_outbox_sending))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun hasPendingChatActions(): Boolean =
        LocalPersistenceService.loadPendingActionsAsync().any {
            it.type == CachedAction.ActionType.MESSAGE.raw || it.type == CachedAction.ActionType.MEDIA_MESSAGE.raw
        }

    companion object {
        private const val UNIQUE_WORK = "moments-persistent-outbox-sync"
        private const val UNIQUE_IMMEDIATE_WORK = "moments-persistent-outbox-sync-now"
        private const val TAG_IMMEDIATE = "outbox-immediate"
        private const val MAX_IMMEDIATE_ATTEMPTS = 5
        private const val CHANNEL_ID = "moments_chat_outbox"
        private const val NOTIFICATION_ID = 0x0B0C

        private val networkConstraints: Constraints
            get() = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Drenado inmediato de la cola al guardar una acción offline: sobrevive a background y a
         * la muerte del proceso (antes solo había el periódico de 15 min). KEEP evita duplicar
         * ejecuciones si ya hay una pendiente.
         */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(networkConstraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(TAG_IMMEDIATE)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.chat_outbox_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
    }
}
