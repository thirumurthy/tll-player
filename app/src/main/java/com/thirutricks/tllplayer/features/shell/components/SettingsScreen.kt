package com.thirutricks.tllplayer.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.features.customize.CustomizeScreen
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.features.update.UpdateDialog
import com.thirutricks.tllplayer.features.settings.BackupScreen
import com.thirutricks.tllplayer.features.settings.ManageProfilesScreen
import com.thirutricks.tllplayer.features.settings.ManageSourcesScreen
import com.thirutricks.tllplayer.features.settings.SettingsViewModel
import com.thirutricks.tllplayer.features.settings.VideoPlayerSettingsScreen
import com.thirutricks.tllplayer.features.shell.MainSection
import com.thirutricks.tllplayer.ui.components.BrandLockup
import com.thirutricks.tllplayer.ui.components.BrowseMode
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.StorageBrowser
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import com.thirutricks.tllplayer.ui.theme.ThemeMode
import com.thirutricks.tllplayer.ui.theme.UiZoom

private enum class TileTone { PRIMARY, SECONDARY, TERTIARY }

private enum class SettingsTab { ROOT, SOURCES, EPG, PROFILES, BACKUP, VIDEO, CUSTOMIZE }

/**
 * The MD3 Settings screen (shown when [MainSection.SETTINGS] is active): grouped sections, each row
 * a tonal icon tile + title/description + a trailing chip or chevron. Theme / UI Zoom are live;
 * unfinished features show a "Soon" chip.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    modifier: Modifier = Modifier,
    openEpgAdd: Boolean = false,
    onEpgAddConsumed: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(SettingsTab.ROOT) }
    // Deep-link from the Guide's "Add EPG" button: jump straight to EPG Sources in add mode.
    var consumeEpgAdd by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCatchupTime by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showAnimations by remember { mutableStateOf(false) }
    var showStartup by remember { mutableStateOf(false) }

    // Dialog-close focus return: closing a dialog/picker refocuses the row that opened it (focus
    // would otherwise fall spatially back to the sidebar).
    val folderRowFocus = remember { FocusRequester() }
    val themeRowFocus = remember { FocusRequester() }
    val accentRowFocus = remember { FocusRequester() }
    val zoomRowFocus = remember { FocusRequester() }
    val updateRowFocus = remember { FocusRequester() }
    val aboutRowFocus = remember { FocusRequester() }
    val catchupRowFocus = remember { FocusRequester() }
    val clearHistoryRowFocus = remember { FocusRequester() }
    val animationsRowFocus = remember { FocusRequester() }
    val startupRowFocus = remember { FocusRequester() }
    // NOTE: this restore request crosses INTO the root focus group from outside (the dialog), so
    // the group's onEnter intercepts it — onEnter must consult dialogReturn first (it does, below)
    // or it would hijack the restore to its own default target. dialogReturn is cleared by onEnter.
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(showZoom, showTheme, showAccent, showFolderPicker, showUpdate, showAbout, showCatchupTime, showClearHistory, showAnimations, showStartup) {
        if (!showZoom && !showTheme && !showAccent && !showFolderPicker && !showUpdate && !showAbout && !showCatchupTime && !showClearHistory && !showAnimations && !showStartup) {
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
        }
    }
    val settingsVm: SettingsViewModel = koinViewModel()
    val downloadRoot by settingsVm.downloadRoot.collectAsStateWithLifecycle()
    val livePreview by settingsVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewAudio by settingsVm.livePreviewAudio.collectAsStateWithLifecycle()
    val hdr by settingsVm.hdrEnabled.collectAsStateWithLifecycle()
    val surroundSound by settingsVm.surroundSound.collectAsStateWithLifecycle()
    val autoPlayNext by settingsVm.autoPlayNext.collectAsStateWithLifecycle()
    val androidTvHomeEnabled by settingsVm.androidTvHomeEnabled.collectAsStateWithLifecycle()
    val updateCheckOnStart by settingsVm.updateCheckOnStart.collectAsStateWithLifecycle()
    val resumeLastChannel by settingsVm.resumeLastChannel.collectAsStateWithLifecycle()
    val startupMode by settingsVm.startupMode.collectAsStateWithLifecycle()
    val catchupTz by settingsVm.catchupTimezone.collectAsStateWithLifecycle()
    val catchupOffset by settingsVm.catchupOffsetMinutes.collectAsStateWithLifecycle()
    val catchupChannels by settingsVm.catchupChannelCount.collectAsStateWithLifecycle()
    val accent by settingsVm.accent.collectAsStateWithLifecycle()
    val customAccent by settingsVm.customAccent.collectAsStateWithLifecycle()
    val animationLevel by settingsVm.animationLevel.collectAsStateWithLifecycle()

    // Restore focus to the row a sub-screen was opened from when the user navigates back.
    var lastTab by remember { mutableStateOf<SettingsTab?>(null) }
    val rowFocus = remember { mapOf(
        SettingsTab.SOURCES to FocusRequester(),
        SettingsTab.EPG to FocusRequester(),
        SettingsTab.PROFILES to FocusRequester(),
        SettingsTab.BACKUP to FocusRequester(),
        SettingsTab.VIDEO to FocusRequester(),
        SettingsTab.CUSTOMIZE to FocusRequester(),
    ) }
    val open: (SettingsTab) -> Unit = { lastTab = it; tab = it }
    LaunchedEffect(tab) {
        if (tab == SettingsTab.ROOT && lastTab != null) {
            kotlinx.coroutines.delay(60)
            runCatching { rowFocus[lastTab]?.requestFocus() }
        }
    }
    LaunchedEffect(openEpgAdd) {
        if (openEpgAdd) { consumeEpgAdd = true; open(SettingsTab.EPG); onEpgAddConsumed() }
    }

    when (tab) {
        SettingsTab.SOURCES -> { ManageSourcesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.EPG -> { com.thirutricks.tllplayer.features.settings.EpgSourcesScreen(onBack = { tab = SettingsTab.ROOT; consumeEpgAdd = false }, modifier = modifier, startOnAdd = consumeEpgAdd); return }
        SettingsTab.PROFILES -> { ManageProfilesScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.BACKUP -> { BackupScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.VIDEO -> { VideoPlayerSettingsScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.CUSTOMIZE -> { CustomizeScreen(onBack = { tab = SettingsTab.ROOT }, modifier = modifier); return }
        SettingsTab.ROOT -> Unit
    }

    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // onEnter fires for ANY focus entry from outside the group: D-pad entry from the
            // sidebar, but ALSO our own programmatic dialog-close restores (the dialogs live
            // outside this group). So it must route to the pending dialog-return row first, then
            // the last-opened sub-menu's row, then the first row.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: rowFocus[lastTab] ?: rowFocus.getValue(SettingsTab.SOURCES)
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        GroupLabel("Content")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.PLAYLIST,
            title = "Playlists", desc = "Add, re-sync or remove M3U / Xtream / TLL playlists",
            onClick = { open(SettingsTab.SOURCES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.SOURCES)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.EPG,
            title = "EPG Sources", desc = "Add XMLTV guide feeds for the TV Guide",
            onClick = { open(SettingsTab.EPG) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.EPG)),
        )
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.SORT,
            title = "Customize", desc = "Hide, rename & reorder categories and channels",
            onClick = { open(SettingsTab.CUSTOMIZE) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.CUSTOMIZE)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PERSON,
            title = "Profiles", desc = "Manage viewers, kids mode & PIN locks",
            onClick = { open(SettingsTab.PROFILES) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.PROFILES)),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.LIVE_TV,
            title = "Live preview", desc = "Auto-play a channel when you focus it",
            chip = if (livePreview) "On" else "Off",
            chipTone = if (livePreview) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setLivePreviewEnabled(!livePreview) },
        )
        if (livePreview) {
            SettingsRow(
                tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
                title = "Preview audio", desc = "Play sound in the Live preview",
                chip = if (previewAudio) "On" else "Off",
                chipTone = if (previewAudio) TileTone.PRIMARY else TileTone.SECONDARY,
                onClick = { settingsVm.setLivePreviewAudio(!previewAudio) },
            )
        }
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Download folder",
            chip = downloadRoot.ifBlank { "App storage" }.let { java.io.File(it).name.ifBlank { it } },
            chipTone = TileTone.TERTIARY,
            onClick = { dialogReturn = folderRowFocus; showFolderPicker = true }, showChevron = true,
            modifier = Modifier.focusRequester(folderRowFocus),
        )
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Backup & Restore", desc = "Export or restore profiles & sources",
            onClick = { open(SettingsTab.BACKUP) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.BACKUP)),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Clear watch history", desc = "Remove this profile's recently-watched & continue rows",
            onClick = { dialogReturn = clearHistoryRowFocus; showClearHistory = true }, showChevron = true,
            modifier = Modifier.focusRequester(clearHistoryRowFocus),
        )
        SectionDivider()
        GroupLabel("Appearance")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.THEME,
            title = "Theme", desc = "Light, dark or follow the system",
            chip = themeLabel(themeMode), chipTone = TileTone.PRIMARY,
            onClick = { dialogReturn = themeRowFocus; showTheme = true }, showChevron = true,
            modifier = Modifier.focusRequester(themeRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.PALETTE,
            title = "Accent color", desc = "Tint the interface — presets, palette or hex code",
            chip = if (customAccent.isNotBlank()) customAccent.uppercase() else accent.label,
            chipTone = TileTone.SECONDARY,
            onClick = { dialogReturn = accentRowFocus; showAccent = true }, showChevron = true,
            modifier = Modifier.focusRequester(accentRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.ZOOM,
            title = "UI Zoom", desc = "Scale the whole interface",
            chip = UiZoom.label(uiZoomPercent), chipTone = TileTone.SECONDARY,
            onClick = { dialogReturn = zoomRowFocus; showZoom = true }, showChevron = true,
            modifier = Modifier.focusRequester(zoomRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.THEME,
            title = "Animations", desc = "Turn interface motion on or off — Off feels snappier on lower-end TV boxes",
            chip = animationLevel.label, chipTone = TileTone.SECONDARY,
            onClick = { dialogReturn = animationsRowFocus; showAnimations = true }, showChevron = true,
            modifier = Modifier.focusRequester(animationsRowFocus),
        )

        SectionDivider()
        GroupLabel("Playback")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.VIDEO,
            title = "HDR", desc = "Use HDR output when the video & TV support it",
            chip = if (hdr) "On" else "Off",
            chipTone = if (hdr) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setHdrEnabled(!hdr) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.AUDIO,
            title = "Surround sound",
            desc = "Decode Dolby/DTS to surround (5.1/7.1) for a real 5.1/7.1 receiver. Leave OFF for TV speakers or a stereo soundbar — multichannel can lag audio behind video on some TVs/soundbars. If it drifts, nudge the player's Audio menu → A/V sync.",
            chip = if (surroundSound) "On" else "Off",
            chipTone = if (surroundSound) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setSurroundSound(!surroundSound) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.LIVE_TV,
            title = "Startup",
            desc = "Where this profile opens: Home, the last live channel you watched, or Live TV on Favorites.",
            chip = startupMode.label, chipTone = TileTone.SECONDARY,
            onClick = { dialogReturn = startupRowFocus; showStartup = true }, showChevron = true,
            modifier = Modifier.focusRequester(startupRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.SKIP_NEXT,
            title = "Auto-play next episode",
            desc = "When an episode ends, automatically start the next one — and roll into the next season.",
            chip = if (autoPlayNext) "On" else "Off",
            chipTone = if (autoPlayNext) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAutoPlayNext(!autoPlayNext) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.EPG,
            title = "Catch-up time",
            desc = if (catchupChannels > 0) "$catchupChannels channels support catch-up · timezone for archive playback"
                else "No catch-up channels available on this playlist",
            chip = when (catchupTz) {
                SettingsRepository.CatchupTimezone.DEVICE -> "Device"
                SettingsRepository.CatchupTimezone.MANUAL -> utcOffsetLabel(catchupOffset)
            },
            chipTone = TileTone.PRIMARY,
            onClick = { dialogReturn = catchupRowFocus; showCatchupTime = true }, showChevron = true,
            modifier = Modifier.focusRequester(catchupRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Android TV home", desc = "Show Continue Watching and recent live channels on the TV home screen",
            chip = if (androidTvHomeEnabled) "On" else "Off",
            chipTone = if (androidTvHomeEnabled) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setAndroidTvHomeEnabled(!androidTvHomeEnabled) },
        )
        if (androidTvHomeEnabled) {
            val tvHomeRefresh by settingsVm.tvHomeRefresh.collectAsStateWithLifecycle()
            SettingsRow(
                tone = TileTone.TERTIARY, icon = OwnTVIcon.SHARE,
                title = "Refresh now", desc = "Rebuild the Continue Watching / recent cards on the Android TV home",
                chip = when (tvHomeRefresh) {
                    com.thirutricks.tllplayer.features.settings.SettingsViewModel.TvHomeRefresh.REFRESHING -> "Rebuilding…"
                    com.thirutricks.tllplayer.features.settings.SettingsViewModel.TvHomeRefresh.DONE -> "Done ✓"
                    else -> null
                },
                chipTone = TileTone.PRIMARY,
                onClick = {
                    if (tvHomeRefresh == com.thirutricks.tllplayer.features.settings.SettingsViewModel.TvHomeRefresh.IDLE) {
                        settingsVm.refreshAndroidTvHome()
                    }
                },
            )
        }
        SettingsRow(
            tone = TileTone.TERTIARY, icon = OwnTVIcon.VIDEO,
            title = "Video Player Settings", desc = "Decoder, subtitles, sync",
            onClick = { open(SettingsTab.VIDEO) }, showChevron = true,
            modifier = Modifier.focusRequester(rowFocus.getValue(SettingsTab.VIDEO)),
        )

        SectionDivider()
        GroupLabel("App")
        SettingsRow(
            tone = TileTone.PRIMARY, icon = OwnTVIcon.DOWNLOADS,
            title = "Check for updates", desc = "Get the latest version from GitHub Releases",
            chip = "v${com.thirutricks.tllplayer.BuildConfig.VERSION_NAME}",
            onClick = { dialogReturn = updateRowFocus; showUpdate = true }, showChevron = true,
            modifier = Modifier.focusRequester(updateRowFocus),
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.HISTORY,
            title = "Check updates on startup", desc = "Look for a new version when the app opens",
            chip = if (updateCheckOnStart) "On" else "Off",
            chipTone = if (updateCheckOnStart) TileTone.PRIMARY else TileTone.SECONDARY,
            onClick = { settingsVm.setUpdateCheckOnStart(!updateCheckOnStart) },
        )
        SettingsRow(
            tone = TileTone.SECONDARY, icon = OwnTVIcon.MENU,
            title = "About", desc = "Version, license & project info",
            onClick = { dialogReturn = aboutRowFocus; showAbout = true }, showChevron = true,
            modifier = Modifier.focusRequester(aboutRowFocus),
        )
    }

    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
    }
    if (showCatchupTime) {
        CatchupTimeDialog(
            mode = catchupTz,
            offsetMinutes = catchupOffset,
            offsetRange = settingsVm.catchupOffsetRangeMinutes,
            onSetMode = settingsVm::setCatchupTimezone,
            onAdjustOffset = settingsVm::adjustCatchupOffset,
            onDismiss = { showCatchupTime = false },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showClearHistory) {
        ClearHistoryDialog(
            onClear = { type -> settingsVm.clearWatchHistory(type); showClearHistory = false },
            onDismiss = { showClearHistory = false },
        )
    }
    if (showTheme) {
        com.thirutricks.tllplayer.features.settings.PickerDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name to themeLabel(it) },
            selected = themeMode.name,
            onSelect = { settingsVm.setThemeMode(ThemeMode.valueOf(it)); showTheme = false },
            onDismiss = { showTheme = false },
        )
    }
    if (showAnimations) {
        com.thirutricks.tllplayer.features.settings.PickerDialog(
            title = "Animations",
            options = com.thirutricks.tllplayer.ui.theme.AnimationLevel.entries.map { it.name to it.label },
            selected = animationLevel.name,
            onSelect = { settingsVm.setAnimationLevel(com.thirutricks.tllplayer.ui.theme.AnimationLevel.valueOf(it)); showAnimations = false },
            onDismiss = { showAnimations = false },
        )
    }
    if (showStartup) {
        com.thirutricks.tllplayer.features.settings.PickerDialog(
            title = "Startup",
            options = com.thirutricks.tllplayer.features.settings.data.StartupMode.entries.map { it.name to it.label },
            selected = startupMode.name,
            onSelect = { settingsVm.setStartupMode(com.thirutricks.tllplayer.features.settings.data.StartupMode.valueOf(it)); showStartup = false },
            onDismiss = { showStartup = false },
        )
    }
    if (showAccent) {
        AccentPaletteDialog(
            accent = accent,
            customAccent = customAccent,
            onPickPreset = { settingsVm.setAccent(it) },
            onPickCustom = { settingsVm.setCustomAccent(it) },
            onDismiss = { showAccent = false },
        )
    }
    if (showZoom) {
        ZoomDialog(current = uiZoomPercent, onSet = onSetZoom, onDismiss = { showZoom = false })
    }
    if (showFolderPicker) {
        StorageBrowser(
            title = "Choose the download folder",
            mode = BrowseMode.FOLDER,
            onPick = { settingsVm.setDownloadRoot(it.absolutePath); showFolderPicker = false },
            onDismiss = { showFolderPicker = false },
        )
    }
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.AMOLED_DARK -> "AMOLED Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "System"
}

/**
 * Accent picker: preset swatches, a hue/shade palette, and a hex-code field for an exact color.
 * Presets clear the custom color; palette/hex set it (custom overrides the preset in the theme).
 */
@Composable
private fun AccentPaletteDialog(
    accent: com.thirutricks.tllplayer.ui.theme.AccentColor,
    customAccent: String,
    onPickPreset: (com.thirutricks.tllplayer.ui.theme.AccentColor) -> Unit,
    onPickCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val isDark = colors.isDark
    val firstFocus = remember { FocusRequester() }
    var hexInput by remember { mutableStateOf(customAccent.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHigh)
                .padding(28.dp),
        ) {
            Text("Accent color", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(16.dp))

            Text("Presets", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.thirutricks.tllplayer.ui.theme.AccentColor.entries.forEachIndexed { i, ac ->
                    val isSel = customAccent.isBlank() && ac == accent
                    Swatch(
                        color = ac.primary(isDark),
                        selected = isSel,
                        onClick = { onPickPreset(ac); onDismiss() },
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Palette", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val hues = (0 until 360 step 30).toList()
            listOf(0.85f to 0.55f, 0.55f to 0.72f).forEach { (sat, light) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    hues.forEach { h ->
                        val c = Color.hsl(h.toFloat(), sat, light)
                        val hex = "#%06X".format(c.toArgb() and 0xFFFFFF)
                        Swatch(
                            color = c,
                            selected = customAccent.equals(hex, ignoreCase = true),
                            onClick = { onPickCustom(hex); onDismiss() },
                            sizeDp = 36,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text("Hex code", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                com.thirutricks.tllplayer.ui.components.OwnTVTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it.take(6); hexError = false },
                    label = "Hex",
                    placeholder = "52DBC8",
                    modifier = Modifier.width(200.dp),
                )
                OwnTVButton("Apply", onClick = {
                    val parsed = com.thirutricks.tllplayer.ui.theme.parseAccentHex(hexInput)
                    if (parsed != null) {
                        onPickCustom("#" + hexInput.trim().removePrefix("#").uppercase())
                        onDismiss()
                    } else {
                        hexError = true
                    }
                })
                if (hexError) {
                    Text("Enter 6 hex digits, e.g. 52DBC8", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}

/** A focusable color swatch circle; the selected one is ringed. */
@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size((sizeDp + 14).dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        selected = selected,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { _ ->
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, colors.onSurface, androidx.compose.foundation.shape.CircleShape)
                    else Modifier,
                ),
        )
    }
}

private const val GITHUB_REPO = "github.com/ahXN00/OwnTV"
private const val TELEGRAM_LINK = "t.me/owntvplayer"

/** About OwnTV: version, license, author and project link — all readable on screen (no TV browser). */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(520.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLockup(markSize = 48, textSize = 30)
            Spacer(Modifier.height(6.dp))
            Text("Version ${com.thirutricks.tllplayer.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            Spacer(Modifier.height(14.dp))
            Text(
                "Your own IPTV player for Android TV — a free, open-source, player-only app. " +
                    "It provides no channels or content; you bring your own legally accessible sources.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text("© 2026 Ashiq Hasan · MIT License", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(GITHUB_REPO, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            Spacer(Modifier.height(16.dp))
            // Community: Telegram link + a QR, side-by-side to keep the dialog compact, so TV users can
            // join from their phone — no TV browser needed.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Join us on Telegram", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(TELEGRAM_LINK, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Scan the QR to join from your phone, or open the link above.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(com.thirutricks.tllplayer.R.drawable.telegram_qr),
                        contentDescription = "Telegram group QR code",
                        modifier = Modifier.size(120.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Contributions, bug reports & stars are welcome on GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
        }
    }
}

/**
 * Pick what watch history to clear: everything, or just Live TV / Movies / Series. Over a dimmed scrim;
 * Cancel is focused first so a stray OK doesn't wipe anything. [onClear] gets null for "all".
 */
@Composable
private fun ClearHistoryDialog(
    onClear: (com.thirutricks.tllplayer.core.model.MediaType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    // null = still choosing a scope; non-null = confirming that scope (type + label; type null = everything).
    var pending by remember { mutableStateOf<Pair<com.thirutricks.tllplayer.core.model.MediaType?, String>?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(pending) { runCatching { firstFocus.requestFocus() } }
    BackHandler { if (pending != null) pending = null else onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val p = pending
            if (p == null) {
                Text("Clear watch history", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose what to remove for this profile. Playlists, favorites and downloads are not affected.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                Spacer(Modifier.height(10.dp))
                OwnTVButton("Live TV", onClick = { pending = com.thirutricks.tllplayer.core.model.MediaType.LIVE to "Live TV" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("Movies", onClick = { pending = com.thirutricks.tllplayer.core.model.MediaType.MOVIE to "Movies" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("Series", onClick = { pending = com.thirutricks.tllplayer.core.model.MediaType.SERIES to "Series" }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OwnTVButton("All history", onClick = { pending = (null as com.thirutricks.tllplayer.core.model.MediaType?) to "all" }, modifier = Modifier.fillMaxWidth())
            } else {
                Text("Clear ${p.second} history?", style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OwnTVButton("No", onClick = { pending = null }, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(firstFocus))
                    OwnTVButton("Yes, clear", onClick = { onClear(p.first) })
                }
            }
        }
    }
}

/** A stepper for the global UI scale. Changes apply live (the whole UI re-scales as you adjust). */
@Composable
private fun ZoomDialog(current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(460.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("UI Zoom", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Scale the whole interface (${UiZoom.MIN}%–${UiZoom.MAX}%).",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StepButton("–", enabled = current > UiZoom.MIN) { onSet(UiZoom.clamp(current - UiZoom.STEP)) }
                Text(
                    UiZoom.label(current),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.primary,
                    modifier = Modifier.width(120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton("+", enabled = current < UiZoom.MAX, modifier = Modifier.focusRequester(firstFocus)) { onSet(UiZoom.clamp(current + UiZoom.STEP)) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Reset", onClick = { onSet(UiZoom.DEFAULT) }, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDismiss)
            }
        }
    }
}

/** "UTC", "UTC+05:00", "UTC-03:30" — labels a UTC offset (in minutes) for catch-up. */
private fun utcOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "UTC"
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign%02d:%02d".format(abs / 60, abs % 60)
}

@Composable
private fun CatchupTimeDialog(
    mode: SettingsRepository.CatchupTimezone,
    offsetMinutes: Int,
    offsetRange: IntRange,
    onSetMode: (SettingsRepository.CatchupTimezone) -> Unit,
    onAdjustOffset: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onDismiss() }
    val manual = mode == SettingsRepository.CatchupTimezone.MANUAL
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(480.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Catch-up time", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "How catch-up timestamps are sent. Use your device timezone, or set the offset your provider's server expects.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            // Mode toggle: Device / Manual.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(
                    "Device",
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.DEVICE) },
                    style = if (!manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                OwnTVButton(
                    "Manual",
                    onClick = { onSetMode(SettingsRepository.CatchupTimezone.MANUAL) },
                    style = if (manual) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                )
            }
            if (manual) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StepButton("–", enabled = offsetMinutes > offsetRange.first) { onAdjustOffset(-60) }
                    Text(
                        utcOffsetLabel(offsetMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.width(150.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    StepButton("+", enabled = offsetMinutes < offsetRange.last) { onAdjustOffset(60) }
                }
            }
            Spacer(Modifier.height(24.dp))
            OwnTVButton("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.Center,
    ) { _ ->
        Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) colors.onSurface else colors.outline)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(1.dp)
            .background(OwnTVTheme.colors.outlineVariant),
    )
}

@Composable
private fun SettingsRow(
    tone: TileTone,
    icon: OwnTVIcon,
    title: String,
    desc: String? = null,
    chip: String? = null,
    chipTone: TileTone = TileTone.PRIMARY,
    soon: Boolean = false,
    showChevron: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tonal icon tile
            val (tileBg, tileOn) = tone.colors()
            Box(
                modifier = Modifier
                    .size(Dimens.IconTileSize)
                    .clip(RoundedCornerShape(Dimens.IconTileCorner))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(icon = icon, tint = tileOn, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (desc != null) {
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (soon) {
                    SoonChip()
                }
                if (chip != null) {
                    ValueChip(chip, chipTone)
                }
                if (showChevron) {
                    OwnTVIcon(icon = OwnTVIcon.CHEVRON, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SoonChip() {
    val colors = OwnTVTheme.colors
    Text(
        text = "SOON",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHighest)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ValueChip(text: String, tone: TileTone) {
    val (bg, on) = tone.colors()
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = on,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun TileTone.colors(): Pair<Color, Color> {
    val c = OwnTVTheme.colors
    return when (this) {
        TileTone.PRIMARY -> c.primaryContainer to c.onPrimaryContainer
        TileTone.SECONDARY -> c.secondaryContainer to c.onSecondaryContainer
        TileTone.TERTIARY -> c.tertiaryContainer to c.onTertiaryContainer
    }
}
