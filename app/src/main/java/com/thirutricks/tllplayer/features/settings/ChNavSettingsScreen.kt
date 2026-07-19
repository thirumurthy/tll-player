package com.thirutricks.tllplayer.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import com.thirutricks.tllplayer.features.settings.data.ChNavLimits
import com.thirutricks.tllplayer.ui.components.NumberInputDialog
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

private enum class ChNavDialog { NONE, ENABLED, UP_SKIP, DOWN_SKIP }

/**
 * CH+- Key Paging — lets the user page the focused panel (category rail or item list/grid) in
 * Live/Movies/Series, and the category list in Settings → Customize, using the remote's CH+ / CH− keys.
 *
 * Behaviour (set here, applied everywhere CH paging is wired):
 *  - Short press CH+ : skip [upSkip] items toward the FIRST item.
 *  - Short press CH− : skip [downSkip] items toward the LAST item.
 *  - Long-press CH+  : jump straight to the FIRST item.
 *  - Long-press CH−  : jump straight to the LAST item.
 *  - Skips are clamped at the ends (a short list reaches the end in one press for free).
 *
 * Counts are advisory-warned above [ChNavLimits.WARN_THRESHOLD] but never hard-blocked;
 * they're clamped to [ChNavLimits.HARD_MAX] on save.
 */
@Composable
fun ChNavSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.chNavEnabled.collectAsStateWithLifecycle()
    val upSkip by vm.chNavUpSkip.collectAsStateWithLifecycle()
    val downSkip by vm.chNavDownSkip.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val firstFocus = remember { FocusRequester() }
    var dialog by remember { mutableStateOf(ChNavDialog.NONE) }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Header(title = "CH+- Key Paging", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            "Page the category or item list with the remote's CH+ / CH− keys.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row2(
            icon = OwnTVIcon.MENU,
            title = "Use CH+- keys to page lists",
            desc = "When on, CH+ and CH− skip inside the focused panel (category rail or item list, and the Customize category list). When off, those keys do nothing in those screens.",
            chip = if (enabled) "On" else "Off",
            primaryChip = enabled,
            chevron = true,
            onClick = { dialog = ChNavDialog.ENABLED },
            modifier = Modifier.focusRequester(firstFocus),
        )

        Spacer(Modifier.height(10.dp))
        GroupLabel("Skip counts")

        Row2(
            icon = OwnTVIcon.SKIP_PREVIOUS,
            title = "CH+ skip (toward first)",
            desc = "How many items one CH+ press jumps up. Long-press always goes to the first item.",
            chip = upSkip.toString(),
            chevron = true,
            onClick = { dialog = ChNavDialog.UP_SKIP },
        )
        Row2(
            icon = OwnTVIcon.SKIP_NEXT,
            title = "CH− skip (toward last)",
            desc = "How many items one CH− press jumps down. Long-press always goes to the last item.",
            chip = downSkip.toString(),
            chevron = true,
            onClick = { dialog = ChNavDialog.DOWN_SKIP },
        )

        Spacer(Modifier.height(12.dp))
        GroupLabel("How it works")
        Text(
            "• Skips are clamped at the ends, so short lists reach the end in one press.\n" +
                "• Long-press CH+ = first item, long-press CH− = last item.\n" +
                "• Long-press is disabled on the \"All\" list (every channel / movie / series) — short-press skipping still works there.\n" +
                "• Applies to whichever panel currently has focus — the category rail or the item list/grid.\n" +
                "• Also pages the category list in Settings → Customize Categories & Items.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    val warnText = "Large skips overshoot short lists and may feel jumpy on low-end TVs."
    when (dialog) {
        ChNavDialog.ENABLED -> PickerDialog(
            title = "CH+- key paging",
            options = listOf("true" to "On", "false" to "Off"),
            selected = enabled.toString(),
            onSelect = { v -> vm.setChNavEnabled(v.toBoolean()); dialog = ChNavDialog.NONE },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.UP_SKIP -> NumberInputDialog(
            title = "CH+ skip (toward first)",
            value = upSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = { vm.setChNavUpSkip(it) },
            onReset = { vm.setChNavUpSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.DOWN_SKIP -> NumberInputDialog(
            title = "CH− skip (toward last)",
            value = downSkip,
            min = 1,
            max = ChNavLimits.HARD_MAX,
            step = 5,
            warnAbove = ChNavLimits.WARN_THRESHOLD,
            warningText = warnText,
            onSet = { vm.setChNavDownSkip(it) },
            onReset = { vm.setChNavDownSkip(ChNavLimits.DEFAULT_SKIP) },
            onDismiss = { dialog = ChNavDialog.NONE },
        )
        ChNavDialog.NONE -> {}
    }
}
