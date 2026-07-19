package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * CH+- key paging for a browse panel (category rail OR item list/grid).
 *
 * Behaviour:
 *  - **CH− short press**: jump [downSkip] items toward the last item (clamped at the end).
 *  - **CH+ short press**: jump [upSkip] items toward the first item (clamped at the end).
 *  - **CH− long-press**: jump straight to the LAST item.
 *  - **CH+ long-press**: jump straight to the FIRST item.
 *
 * Long-press is detected via Android's key-repeat mechanism: while a key is held, the platform re-fires
 * KeyDown events whose `nativeEvent.repeatCount > 0`. The FIRST repeat is treated as the long-press
 * trigger, and subsequent repeats are swallowed until the key is released. No timer coroutine is
 * needed, so there's no race on fast taps.
 *
 * Everything only fires when [enabled] AND [isFocused] (the pane this modifier is attached to must
 * currently hold focus). All jumps use the caller-supplied [onJumpToIndex] which is expected to call
 * `scrollToItem` (instant — never `animateScrollToItem`, which janks on low-end TVs over big skips)
 * and then request focus on the target row.
 *
 * Returns `true` (consume) for CH+/- KeyDown/KeyUp **when the pane has focus**, so the key never
 * leaks to the player shell; `false` for all other keys and when unfocused.
 */
fun Modifier.chNavPaging(
    enabled: Boolean,
    upSkip: Int,
    downSkip: Int,
    isFocused: () -> Boolean,
    lastIndex: () -> Int,
    currentTargetIndex: () -> Int,
    onJumpToIndex: (Int) -> Unit,
    // When false, long-press (jump-to-first / jump-to-last) is ignored — short-press skipping still
    // works. Used to disable the end-jump on the huge "All" list (a jump to the last of 170k items is
    // pointless and just janks), while keeping it on real categories/folders.
    longPressEnabled: () -> Boolean = { true },
): Modifier = composed {
    if (!enabled) return@composed this

    // Per-key cycle state. A CH key cycle is KeyDown → (repeat KeyDown × N) → KeyUp. A held key on
    // Android TV re-fires KeyDown events roughly every ~50ms after the initial press; we detect the
    // long-press as "a second KeyDown arrives within LONG_PRESS_MS of the previous one without an
    // intervening KeyUp" — no dependency on nativeEvent.repeatCount, which isn't uniformly accessible.
    var longPressFired by remember { mutableStateOf(false) }
    var lastKeyDownAt by remember { mutableStateOf(0L) }

    onPreviewKeyEvent { e ->
        val isChKey = e.key == Key.ChannelUp || e.key == Key.ChannelDown
        if (!isChKey) return@onPreviewKeyEvent false

        // When this pane doesn't have focus, never consume — let the key reach whichever pane does
        // (the focused pane's own chNavPaging modifier will handle it).
        if (!isFocused()) return@onPreviewKeyEvent false

        val dir = if (e.key == Key.ChannelDown) -1 else 1 // -1 = CH− (toward last), +1 = CH+ (toward first)

        when (e.type) {
            KeyEventType.KeyDown -> {
                val now = System.currentTimeMillis()
                // A repeat = another KeyDown within LONG_PRESS_MS of the previous one (no KeyUp between).
                val isRepeat = lastKeyDownAt > 0 && now - lastKeyDownAt < LONG_PRESS_MS
                lastKeyDownAt = now
                if (isRepeat && longPressEnabled()) {
                    // First repeat = the long-press threshold was reached. Fire the end-jump once;
                    // swallow further repeats so a held key doesn't keep re-jumping.
                    if (!longPressFired) {
                        longPressFired = true
                        val last = lastIndex()
                        val target = if (dir < 0) last else 0
                        if (last >= 0 && target != currentTargetIndex()) onJumpToIndex(target)
                    }
                } else {
                    // First press of a fresh cycle: arm for a possible long-press. (The short-press
                    // action itself runs on KeyUp, so we don't fire anything here.)
                    longPressFired = false
                }
                true // consume so the shell doesn't also react
            }
            KeyEventType.KeyUp -> {
                if (!longPressFired) {
                    // Short press: skip N in the key's direction, clamped to [0, lastIndex].
                    val cur = currentTargetIndex()
                    val last = lastIndex()
                    val delta = if (dir < 0) downSkip else upSkip
                    val target = if (dir < 0) cur + delta else cur - delta
                    val clamped = target.coerceIn(0, last.coerceAtLeast(0))
                    if (last >= 0 && clamped != cur) onJumpToIndex(clamped)
                }
                longPressFired = false
                lastKeyDownAt = 0L
                true
            }
            else -> true
        }
    }
}

/** Two KeyDown events closer together than this (ms) are treated as a held key → long-press. */
private const val LONG_PRESS_MS = 350L

/**
 * Helper for screens that hold a [LazyListState]: scroll instantly to [index] and (optionally) request
 * focus after a one-frame wait so the target row is composed. Mirrors the restore pattern already used
 * for player-return focus (e.g. LiveScreen.restoreToContextRow).
 */
fun CoroutineScope.jumpLazyListTo(
    listState: LazyListState,
    index: Int,
    focus: (suspend () -> Unit)? = null,
) = launch {
    runCatching { listState.scrollToItem(index) }
    if (focus != null) {
        withFrameNanos { } // wait one frame so the row is laid out
        runCatching { focus() }
    }
}

/** Same as [jumpLazyListTo] but for a [LazyGridState] (Movies/Series grid mode). */
fun CoroutineScope.jumpLazyGridTo(
    gridState: LazyGridState,
    index: Int,
    focus: (suspend () -> Unit)? = null,
) = launch {
    runCatching { gridState.scrollToItem(index) }
    if (focus != null) {
        withFrameNanos { }
        runCatching { focus() }
    }
}
