package com.thirutricks.tllplayer.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.storage.StorageAccess
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import java.io.File

enum class BrowseMode { FOLDER, FILE }

/**
 * An in-app file/folder picker (the TV-safe replacement for SAF). In [BrowseMode.FOLDER] the user
 * navigates and taps "Use this folder"; in [BrowseMode.FILE] tapping a matching file picks it. Grabs
 * focus on open and after each navigation so the remote lands on the list immediately.
 */
@Composable
fun StorageBrowser(
    title: String,
    mode: BrowseMode,
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    fileExtensions: Set<String>? = null,
) {
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val roots = remember { StorageAccess.storageRoots(context) }
    var current by remember { mutableStateOf<File?>(null) } // null = the roots list
    var hasAccess by remember { mutableStateOf(StorageAccess.hasAllFilesAccess()) }
    var refresh by remember { mutableIntStateOf(0) }
    var showNewFolder by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    BackHandler { if (current != null) current = current?.parentFile else onDismiss() }

    // Re-grab focus whenever the listing changes (open / navigate / refresh). Deferred a beat: the
    // clicked row is removed in the same recompose, and its focus teardown lands AFTER an immediate
    // request — which would leave focus on whatever sits behind the overlay.
    LaunchedEffect(current, hasAccess, refresh) {
        kotlinx.coroutines.delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(660.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(current?.absolutePath ?: "Pick a location", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))

            val dir = current
            val children = remember(dir, refresh) { runCatching { dir?.listFiles()?.toList() }.getOrNull().orEmpty() }
            val folders = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            val files = if (mode == BrowseMode.FILE) {
                children.filter { it.isFile && (fileExtensions == null || it.extension.lowercase() in fileExtensions) }.sortedBy { it.name.lowercase() }
            } else emptyList()

            LazyColumn(Modifier.heightIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (dir == null) {
                    if (!hasAccess) {
                        item {
                            BrowserRow(OwnTVIcon.SETTINGS, "Grant full storage access", Modifier.focusRequester(firstFocus)) {
                                StorageAccess.requestAllFilesAccess(context); hasAccess = StorageAccess.hasAllFilesAccess()
                            }
                        }
                    }
                    itemsIndexed(roots) { i, (label, file) ->
                        val m = if (i == 0 && hasAccess) Modifier.focusRequester(firstFocus) else Modifier
                        BrowserRow(OwnTVIcon.DOWNLOADS, label, m) { current = file }
                    }
                } else {
                    item { BrowserRow(OwnTVIcon.BACK, "..", Modifier.focusRequester(firstFocus)) { current = dir.parentFile } }
                    itemsIndexed(folders) { _, f -> BrowserRow(OwnTVIcon.DOWNLOADS, f.name) { current = f } }
                    itemsIndexed(files) { _, f -> BrowserRow(OwnTVIcon.PLAYLIST, f.name) { onPick(f) } }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                if (current != null) OwnTVButton("New folder", onClick = { showNewFolder = true }, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.ADD)
                Spacer(Modifier.weight(1f))
                if (mode == BrowseMode.FOLDER && current != null) OwnTVButton("Use this folder", onClick = { current?.let(onPick) })
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onCreate = { name ->
                current?.let { runCatching { File(it, StorageAccess.sanitize(name)).mkdirs() } }
                showNewFolder = false
                refresh++
            },
            onDismiss = { showNewFolder = false },
        )
    }
}

@Composable
private fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(420.dp).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHighest).padding(24.dp)) {
            Text("New folder", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(14.dp))
            OwnTVTextField(name, { name = it }, label = "Folder name", placeholder = "e.g. My TV", modifier = Modifier.fillMaxWidth().focusRequester(focus))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Create", onClick = { onCreate(name) }, enabled = name.isNotBlank())
            }
        }
    }
}

@Composable
private fun BrowserRow(icon: OwnTVIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentAlignment = Alignment.CenterStart) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVIcon(icon, tint = if (focused) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (focused) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
