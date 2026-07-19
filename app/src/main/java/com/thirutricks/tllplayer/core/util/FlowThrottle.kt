package com.thirutricks.tllplayer.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Backpressure-based throttle for hot Room query Flows (C2): emits the first value immediately,
 * then suspends the upstream for [windowMs] after every emission.
 *
 * Why this instead of `debounce`: Room's generated Flow re-runs its query once per invalidation
 * *when the collector is ready*; invalidation signals arriving while the collector is busy are
 * conflated into one. `debounce` buffers downstream (upstream keeps re-querying), but blocking in
 * `collect` genuinely caps query executions — so a live `COUNT(*)` over a 170k-row table re-runs
 * at most once per window during a bulk sync instead of once per committed batch, while staying
 * instant when the user switches categories (first emission is not delayed).
 */
fun <T> Flow<T>.throttleLatest(windowMs: Long = 1_000): Flow<T> = flow {
    collect { value ->
        emit(value)
        delay(windowMs)
    }
}
