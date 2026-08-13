package com.moments.android.views.creator

/** Stable identity shared by moment/story outbox entries to make retries idempotent. */
interface PendingPublication {
    val operationId: String
    val tempId: String
    val plannedRemoteId: String
}
