package com.moments.android.services.cache

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SharedThumbnailRequestsTest {
    @Test fun simultaneousCellsGenerateOnlyOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = SharedThumbnailRequests<String>(scope)
            val ready = CompletableDeferred<String>()
            var calls = 0
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                requests.get("video") { calls++; ready.await() }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                requests.get("video") { calls++; "duplicate" }
            }
            assertEquals(1, calls)
            ready.complete("thumbnail")
            assertEquals("thumbnail", first.await())
            assertEquals("thumbnail", second.await())
        } finally { scope.cancel() }
    }

    @Test fun disappearingCellDoesNotCancelAnotherCellsThumbnail() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = SharedThumbnailRequests<String>(scope)
            val ready = CompletableDeferred<String>()
            var calls = 0
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                requests.get("video") { calls++; ready.await() }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                requests.get("video") { calls++; "duplicate" }
            }
            first.cancelAndJoin()
            ready.complete("thumbnail")
            assertEquals("thumbnail", second.await())
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun failedGenerationCanBeRetriedAndDifferentVideosDoNotShare() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = SharedThumbnailRequests<String?>(scope)
            assertNull(requests.get("video") { null })
            assertEquals("retry", requests.get("video") { "retry" })
            assertEquals("other", requests.get("other-video") { "other" })
        } finally { scope.cancel() }
    }
}
