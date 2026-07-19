package com.thirutricks.tllplayer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps D-pad focus INSIDE this focus group for vertical moves: when a held Up/Down outruns a lazy
 * list's composition, Compose's focus search finds nothing above/below within the pane and would
 * escape to the nearest focusable outside it (e.g. the top bar). Cancelling the vertical exit pins
 * focus to the pane's edge row instead. Left/Right/Back still leave the pane normally, and moves
 * BETWEEN children inside the group are unaffected (onExit only fires when leaving the group).
 */
fun Modifier.trapVerticalFocusExit(): Modifier = focusProperties {
    onExit = {
        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
            cancelFocusChange()
        }
    }
}

/**
 * Traps D-pad focus inside this group for ALL directions (Up/Down/Left/Right). Apply to a full-screen
 * modal scrim so a directional press can never escape into the UI behind it. Back is NOT affected — it
 * must still be handled by a BackHandler above (onExit only blocks directional exits).
 *
 * Use this (not [trapVerticalFocusExit]) on modals/dialogs/overlays where every direction must stay
 * inside the dialog. Inside the group, moves between children are unaffected (onExit only fires when
 * leaving the group).
 */
fun Modifier.trapAllFocusExit(): Modifier = focusProperties {
    onExit = { cancelFocusChange() }
}
