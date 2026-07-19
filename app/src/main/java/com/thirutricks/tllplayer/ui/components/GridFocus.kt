package com.thirutricks.tllplayer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Shared per-item focus-target selection for the paged Movies/Series/Live lists and grids:
 * bind the FocusRequester to the item the context menu was opened on, else the last-selected
 * item, else the first item — so "restore focus to id X, else first item" is expressed once
 * instead of copy-pasted per screen/branch.
 */
fun Modifier.gridFocusTarget(
    itemId: Long,
    index: Int,
    contextId: Long?,
    contextFocus: FocusRequester,
    selectedId: Long?,
    selectedFocus: FocusRequester,
    firstItemFocus: FocusRequester,
): Modifier = when {
    itemId == contextId -> focusRequester(contextFocus)
    itemId == selectedId -> focusRequester(selectedFocus)
    index == 0 -> focusRequester(firstItemFocus)
    else -> this
}
