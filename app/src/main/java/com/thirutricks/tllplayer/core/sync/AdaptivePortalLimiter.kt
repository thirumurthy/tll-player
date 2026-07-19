package com.thirutricks.tllplayer.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AIMD (additive-increase / multiplicative-decrease) concurrency gate for portal page crawls.
 * Portals differ wildly in how many parallel requests they tolerate; a fixed pool either
 * under-uses tolerant portals or trips strict ones. This starts conservative, grows by one
 * permit per [growAfter] consecutive successes up to [max], and halves (never below [min])
 * whenever a request fails with a throttle signal ([isThrottle] — 429/5xx/timeouts).
 *
 * Callers wrap each raw request in [withPermit]; retries/backoff must live OUTSIDE it so a
 * backoff delay doesn't hold a slot and every throttled attempt is learned from.
 */
internal class AdaptivePortalLimiter(
    start: Int = DEFAULT_START,
    private val min: Int = DEFAULT_MIN,
    val max: Int = DEFAULT_MAX,
    private val growAfter: Int = DEFAULT_GROW_AFTER,
    private val isThrottle: (Throwable) -> Boolean,
) {
    private val mutex = Mutex()
    private var limit = start.coerceIn(min, max)
    private var inFlight = 0
    private var successStreak = 0
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

    /** Snapshot for logging. */
    val currentLimit: Int get() = limit

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            val result = block()
            onSuccess()
            return result
        } catch (e: Throwable) {
            if (e !is CancellationException && isThrottle(e)) onThrottle()
            throw e
        } finally {
            release()
        }
    }

    private suspend fun acquire() {
        while (true) {
            val waiter = mutex.withLock {
                if (inFlight < limit) {
                    inFlight++
                    return
                }
                CompletableDeferred<Unit>().also { waiters.addLast(it) }
            }
            try {
                waiter.await() // woken waiters re-check under the lock, so a spurious wake is safe
            } catch (c: CancellationException) {
                mutex.withLock { waiters.remove(waiter) }
                throw c
            }
        }
    }

    private suspend fun release() {
        mutex.withLock {
            inFlight--
            wakeLocked()
        }
    }

    private suspend fun onSuccess() {
        mutex.withLock {
            if (++successStreak >= growAfter && limit < max) {
                limit++
                successStreak = 0
                wakeLocked()
            }
        }
    }

    private suspend fun onThrottle() {
        mutex.withLock {
            successStreak = 0
            limit = (limit / 2).coerceAtLeast(min)
        }
    }

    private fun wakeLocked() {
        var free = limit - inFlight
        while (free-- > 0 && waiters.isNotEmpty()) waiters.removeFirst().complete(Unit)
    }

    companion object {
        /** Start where the old fixed pool sat minus headroom — safe on strict portals. */
        const val DEFAULT_START = 6
        const val DEFAULT_MIN = 2
        const val DEFAULT_MAX = 16
        /** Consecutive successful requests before adding one permit (~20 pages ≈ 300 items). */
        const val DEFAULT_GROW_AFTER = 20
    }
}
