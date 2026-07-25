package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.SearchBar
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * A category as shown in the rail: a 2–3 char abbreviation plus its full name. Special rails
 * (Favorites / History) render an [icon] instead of the abbreviation.
 */
data class RailCategory(val abbr: String, val fullName: String, val icon: OwnTVIcon? = null)

/**
 * Layer 2 — the vertical folder rail. Collapsed (focus elsewhere) it shows compact abbreviation
 * pills (FAV, HIS, UK, …); when it holds focus it expands to show full names.
 *
 * Performance notes (providers can have hundreds of categories):
 *  - The pills live in a [LazyColumn], so only the visible ones are composed.
 *  - The rail's slot in the screen layout stays a fixed [Dimens.RailWidth]; the expanded rail is
 *    drawn as an overlay (zIndex) on top of the content pane instead of pushing it, so the channel
 *    grid is never re-laid-out during the expand animation.
 */
@Composable
fun CategoryRail(
    categories: List<RailCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    var hasFocus by remember { mutableStateOf(false) }
    // Folder search (for big libraries). Filters the rail by name but keeps each folder's ORIGINAL
    // index, so selection highlighting and onSelect still map correctly. Reset when the rail loses
    // focus, so it's fresh every time you open it.
    var query by remember { mutableStateOf("") }
    val visible = remember(categories, query) {
        val q = query.trim()
        if (q.isEmpty()) categories.indices.toList()
        else categories.indices.filter { categories[it].fullName.contains(q, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    // Keep the selected category in view when the selection changes while the rail isn't focused
    // (initial load, restored state). While the user D-pads inside, focus handles scrolling.
    LaunchedEffect(selectedIndex, categories.size) {
        if (!hasFocus && selectedIndex in categories.indices) {
            runCatching { listState.scrollToItem(selectedIndex) }
        }
    }

    val expanded = hasFocus
    val panelWidth by animateDpAsState(
        targetValue = if (expanded) Dimens.RailWidthExpanded else Dimens.RailWidth,
        animationSpec = com.thirutricks.tllplayer.ui.theme.ownTvTween(180),
        label = "categoryRailWidth",
    )
    val panelShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

    // Only the compact rail participates in the parent layout. The searchable picker expands over
    // the content, keeping the channel grid stable and returning the former fixed-column space.
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimens.RailWidth)
            .zIndex(if (expanded) 10f else 0f),
    ) {
        Column(
            modifier = Modifier
                .requiredWidth(panelWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (expanded) 28.dp else 0.dp,
                    shape = panelShape,
                    ambientColor = colors.focusGlow,
                    spotColor = colors.focusGlow,
                )
                .clip(panelShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.surfaceContainer, colors.surfaceContainerLow),
                    ),
                )
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), panelShape)
                .onFocusChanged {
                    // Spatial D-pad entry would land on whatever pill is horizontally aligned —
                    // redirect every entry (from the sidebar OR back from the content list) to the
                    // SELECTED category, so you return to the folder you're actually in (e.g. pressing
                    // Left from a channel lands back on that channel's category, not the top of the rail).
                    // Internal moves between pills don't re-trigger this. The redirect must be deferred a
                    // frame: requesting focus inside onFocusChanged is rejected (the focus transaction is
                    // still in progress).
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused() else query = "" // reset the search on leaving
                    if (entered) scope.launch {
                        if (selectedIndex in categories.indices) {
                            // Land on the current category; the search box (top) is one Up away.
                            runCatching { listState.scrollToItem(selectedIndex) }
                            runCatching { selectedFocus.requestFocus() }
                        } else {
                            // No selection (e.g. an empty/special rail) — fall back to the search box.
                            runCatching { listState.scrollToItem(0) }
                            runCatching { searchFocus.requestFocus() }
                        }
                    }
                }
                .focusGroup(),
        ) {
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OwnTVIcon(OwnTVIcon.PLAYLIST, tint = colors.primary, modifier = Modifier.size(19.dp))
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = categories.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Find a folder…",
                    modifier = Modifier
                        .focusRequester(searchFocus)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
                Spacer(Modifier.size(6.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    top = if (expanded) 4.dp else Dimens.GapLarge,
                    bottom = Dimens.GapLarge,
                    start = if (expanded) 14.dp else 10.dp,
                    end = if (expanded) 14.dp else 10.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
            ) {
                items(count = visible.size, key = { visible[it] }) { i ->
                    val index = visible[i]
                    RailPill(
                        category = categories[index],
                        selected = index == selectedIndex,
                        expanded = expanded,
                        onClick = { onSelect(index) },
                        modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
                if (expanded && visible.isEmpty()) {
                    item {
                        Text(
                            "No folders found",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailPill(
    category: RailCategory,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Keep the current folder visible in compact mode; focus adds a clear remote-navigation outline.
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primaryContainer
            focused -> colors.card
            else -> Color.Transparent
        },
        animationSpec = com.thirutricks.tllplayer.ui.theme.ownTvTween(140),
        label = "railPillBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected -> colors.onPrimaryContainer
            focused -> colors.accent
            else -> colors.textSecondary
        },
        animationSpec = com.thirutricks.tllplayer.ui.theme.ownTvTween(140),
        label = "railPillFg",
    )

    val shape = if (expanded) RoundedCornerShape(50) else CircleShape

    Row(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(Dimens.RailPillSize))
            .clip(shape)
            .background(bg)
            .then(
                if (focused) Modifier.border(Dimens.FocusBorderWidth, colors.focusBorder, shape)
                else Modifier
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(if (expanded) Modifier.padding(horizontal = 10.dp, vertical = 8.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        // The compact badge (icon or abbreviation) — the row's anchor in both states.
        Box(
            modifier = Modifier.size(if (expanded) 36.dp else Dimens.RailPillSize),
            contentAlignment = Alignment.Center,
        ) {
            if (category.icon != null) {
                OwnTVIcon(icon = category.icon, tint = fg, filled = selected, modifier = Modifier.size(if (expanded) 20.dp else Dimens.RailPillSize / 2))
            } else {
                Text(
                    text = category.abbr,
                    color = fg,
                    style = if (expanded) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.fullName,
                color = fg,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
