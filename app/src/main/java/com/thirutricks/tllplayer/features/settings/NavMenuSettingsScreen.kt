package com.thirutricks.tllplayer.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.features.shell.MainSection
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.NavDuotoneIcon
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * v4.3.0 — Nav menu customization. Two modes:
 * - **STATIC** (default): the user toggles which of the six browse icons show in the side rail.
 *   Settings is always pinned at the bottom and can never be hidden here.
 * - **DYNAMIC**: the icons adapt to what the active playlist actually contains (Home & Settings always
 *   show). The per-icon list is hidden in this mode — there's nothing to toggle.
 */
@Composable
fun NavMenuSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settingsVm: SettingsViewModel = koinViewModel()
    val mode by settingsVm.navMenuMode.collectAsStateWithLifecycle()
    val hidden by settingsVm.navMenuHidden.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val firstFocus = remember { FocusRequester() }
    var showModePicker by remember { mutableStateOf(false) }

    // Grab focus on the first row the moment the screen opens (mirrors VideoPlayerSettingsScreen).
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
        Header(title = "Sidebar Menu Customization", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose which icons appear in the side menu, or let them adapt to your playlist.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row2(
            icon = OwnTVIcon.THEME,
            title = "Behavior",
            desc = when (mode) {
                SettingsRepository.NavMenuMode.DYNAMIC ->
                    "Dynamic — icons adapt to what your active playlist provides. Home & Settings always show."
                SettingsRepository.NavMenuMode.STATIC ->
                    "Static — manually choose which icons appear below."
            },
            chip = mode.label,
            primaryChip = mode == SettingsRepository.NavMenuMode.DYNAMIC,
            chevron = true,
            onClick = { showModePicker = true },
            modifier = Modifier.focusRequester(firstFocus),
        )

        // The per-icon toggle list only exists in STATIC mode. In DYNAMIC there's nothing to toggle —
        // the icons are decided by the playlist's content, so the list is hidden entirely.
        if (mode == SettingsRepository.NavMenuMode.STATIC) {
            Spacer(Modifier.height(10.dp))
            GroupLabel("Icons")
            MainSection.browseOrder.forEach { section ->
                NavMenuRow(
                    section = section,
                    shown = section !in hidden,
                    onToggle = { settingsVm.setNavSectionHidden(section, hidden = section !in hidden) },
                )
            }
        }
    }

    if (showModePicker) {
        PickerDialog(
            title = "Nav menu behavior",
            options = listOf(
                SettingsRepository.NavMenuMode.STATIC.name to SettingsRepository.NavMenuMode.STATIC.label,
                SettingsRepository.NavMenuMode.DYNAMIC.name to SettingsRepository.NavMenuMode.DYNAMIC.label,
            ),
            selected = mode.name,
            onSelect = { value ->
                settingsVm.setNavMenuMode(SettingsRepository.NavMenuMode.valueOf(value))
                showModePicker = false
            },
            onDismiss = { showModePicker = false },
        )
    }
}

/** One browse-icon row — toggles shown/hidden (STATIC mode only). */
@Composable
private fun NavMenuRow(
    section: MainSection,
    shown: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.IconTileSize)
                    .clip(RoundedCornerShape(Dimens.IconTileCorner))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                NavDuotoneIcon(
                    section = section,
                    color = if (shown) colors.onPrimaryContainer else colors.onPrimaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(section.label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(
                    if (shown) "Shown in the side menu" else "Hidden from the side menu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            val bg = if (shown) colors.primaryContainer else colors.secondaryContainer
            val fg = if (shown) colors.onPrimaryContainer else colors.onSecondaryContainer
            Text(
                if (shown) "Shown" else "Hidden",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
