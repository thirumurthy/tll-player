package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.features.shell.MainSection
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVAvatar
import com.thirutricks.tllplayer.ui.components.NavDuotoneIcon
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Layer 1 — the MD3 navigation panel. Expands to a drawer (profile card + labels) when it holds
 * focus, and collapses to an icon rail when focus moves into a submenu. Settings is pinned at the
 * bottom; the browse list in the middle scrolls if it ever exceeds the height, so Settings is never
 * clipped.
 */
@Composable
fun Sidebar(
    selected: MainSection,
    onSelect: (MainSection) -> Unit,
    avatarId: Int,
    onPickAvatar: () -> Unit,
    profileName: String,
    sourceSummary: String,
    onSwitchProfile: () -> Unit,
    selectedItemFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    counts: (MainSection) -> Int = { 0 },
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    var hasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Phase 2 — the nav is a FIXED icon rail: it never expands or collapses, so the layout never jumps on
    // the D-pad and the profile avatar stays pinned top-left. Full section labels live in the content panes.
    val expanded = false

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged {
                // D-pad focus search is spatial — entering the panel from the content area would
                // land on whatever item happens to be horizontally aligned. Redirect every entry to
                // the SELECTED section instead (internal up/down moves don't re-trigger this).
                // Deferred a frame: requesting focus inside onFocusChanged is rejected mid-transaction.
                val entered = it.hasFocus && !hasFocus
                hasFocus = it.hasFocus
                if (it.hasFocus) onFocused()
                if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
            }
            .focusGroup()
            .width(Dimens.SidebarWidthCollapsed)
            .background(colors.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileCard(
            expanded = expanded,
            avatarId = avatarId,
            profileName = profileName,
            sourceSummary = sourceSummary,
            onPickAvatar = onPickAvatar,
            onSwitchProfile = onSwitchProfile,
        )

        Spacer(Modifier.height(16.dp))
        if (expanded) {
            SectionLabel("Browse")
            Spacer(Modifier.height(4.dp))
        }

        // Scrollable middle so Settings (pinned below) is never clipped.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            MainSection.entries.filter { it.isBrowse }.forEach { section ->
                NavItem(
                    section = section,
                    active = section == selected,
                    expanded = expanded,
                    count = counts(section),
                    onClick = { onSelect(section) },
                    modifier = if (section == selected) {
                        Modifier.focusRequester(selectedItemFocusRequester)
                    } else Modifier,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(colors.outlineVariant),
        )
        Spacer(Modifier.height(8.dp))

        NavItem(
            section = MainSection.SETTINGS,
            active = selected == MainSection.SETTINGS,
            expanded = expanded,
            count = 0,
            onClick = { onSelect(MainSection.SETTINGS) },
            modifier = if (selected == MainSection.SETTINGS) {
                Modifier.focusRequester(selectedItemFocusRequester)
            } else Modifier,
        )
    }
}

@Composable
private fun ProfileCard(
    expanded: Boolean,
    avatarId: Int,
    profileName: String,
    sourceSummary: String,
    onPickAvatar: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val colors = OwnTVTheme.colors

    if (!expanded) {
        // Fixed nav: just the avatar — click opens the profile switcher ("who's watching"), long-press
        // changes the avatar picture. Pinned top-left, always in the same spot.
        AvatarButton(avatarId = avatarId, sizeDp = 56, onClick = onSwitchProfile, onLongClick = onPickAvatar)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(colors.primaryContainer.copy(alpha = 0.45f)),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarButton(avatarId = avatarId, sizeDp = 64, onClick = onPickAvatar)
            Spacer(Modifier.height(10.dp))
            Text(
                profileName.ifBlank { "TLL User" },
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                sourceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            // Switch profile without quitting the app (routes back to the "Who's watching?" gate).
            FocusableSurface(
                onClick = onSwitchProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainer,
                contentAlignment = Alignment.Center,
            ) { focused ->
                val c = if (focused) colors.onSurface else colors.onSurfaceVariant
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(icon = OwnTVIcon.PERSON, tint = c, modifier = Modifier.size(18.dp))
                    Text("Switch Profile", style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AvatarButton(avatarId: Int, sizeDp: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(sizeDp.dp),
        shape = CircleShape,
        focusedScale = 1.08f,
        focusedContainerColor = OwnTVTheme.colors.surfaceContainerHighest,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVAvatar(avatarId = avatarId, modifier = Modifier.size((sizeDp - 4).dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = OwnTVTheme.colors.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
    )
}

@Composable
private fun NavItem(
    section: MainSection,
    active: Boolean,
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        selected = active,
        shape = RoundedCornerShape(26.dp),
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = Color.Transparent,
        selectedContainerColor = colors.secondaryContainer,
        contentAlignment = Alignment.Center,
    ) { focused ->
        val content = when {
            active -> colors.onSecondaryContainer
            focused -> colors.onSurface
            else -> colors.onSurfaceVariant
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.spacedBy(16.dp, Alignment.Start) else Arrangement.Center,
        ) {
            // Monochrome duotone nav icon — tints with the theme (muted idle, accent when selected) via the
            // shared `content` colour. Still — no per-frame animation on the always-visible nav.
            NavDuotoneIcon(
                section = section,
                color = content,
                modifier = Modifier.size(28.dp),
            )
            if (expanded) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private val MainSection.navIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SETTINGS -> OwnTVIcon.SETTINGS
    }

