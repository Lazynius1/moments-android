package com.moments.android.utilities

import org.junit.Assert.*
import org.junit.Test

class AudioFocusCoordinatorTest {
    @Test fun overlappingConsumersShareFocusUntilLastRelease() {
        var requests = 0
        var abandoned = 0
        val coordinator = AudioFocusCoordinator { abandoned++ }
        val video = Any()
        val recording = Any()
        fun acquire(owner: Any) = coordinator.acquire(owner, 1, { true }, { requests++; true }, {})
        assertTrue(acquire(video))
        assertTrue(acquire(recording))
        assertEquals(1, requests)
        coordinator.release(video, 2)
        assertEquals(0, abandoned)
        coordinator.release(recording, 2)
        assertEquals(1, abandoned)
    }

    @Test fun delayedCleanupCannotReleaseResumedConsumer() {
        var abandoned = 0
        val coordinator = AudioFocusCoordinator { abandoned++ }
        val owner = Any()
        assertTrue(coordinator.acquire(owner, 3, { true }, { true }, {}))
        coordinator.release(owner, 2)
        assertEquals(0, abandoned)
        coordinator.release(owner, 4)
        assertEquals(1, abandoned)
    }

    @Test fun cancelledAndDeniedRequestsNeverRetainFocus() {
        var requests = 0
        var abandoned = 0
        val coordinator = AudioFocusCoordinator { abandoned++ }
        val owner = Any()
        assertFalse(coordinator.acquire(owner, 1, { false }, { requests++; true }, {}))
        assertEquals(0, requests)
        assertFalse(coordinator.acquire(owner, 2, { true }, { false }, {}))
        coordinator.release(owner, 3)
        assertEquals(0, abandoned)
        assertTrue(coordinator.acquire(owner, 4, { true }, { requests++; true }, {}))
        assertEquals(1, requests)
    }

    @Test fun interruptionPausesAllOwnersAndNewPlaybackCanRequestAgain() {
        var abandoned = 0
        var paused = 0
        var requests = 0
        val coordinator = AudioFocusCoordinator { abandoned++ }
        val video = Any()
        val music = Any()
        fun acquire(owner: Any, generation: Long) = coordinator.acquire(owner, generation, { true }, { requests++; true }, { paused++ })
        assertTrue(acquire(video, 1))
        assertTrue(acquire(music, 1))
        coordinator.interrupt().forEach { it() }
        assertEquals(2, paused)
        assertEquals(1, abandoned)
        coordinator.release(video, 2)
        assertEquals(1, abandoned)
        assertTrue(acquire(video, 3))
        assertEquals(2, requests)
    }
}
