package com.thirutricks.tllplayer.features.profiles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ProfileEntity
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVAvatar
import com.thirutricks.tllplayer.ui.components.OwnTVAvatars
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVTextField
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** Modal scrim wrapper for the profile dialogs. */
@Composable
internal fun ProfileScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(480.dp).clip(RoundedCornerShape(20.dp)).background(OwnTVTheme.colors.surfaceContainerHigh).padding(28.dp),
        ) { content() }
    }
}

/** Numeric PIN entry. Calls [onSubmit] with the entered digits. */
@Composable
internal fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ProfileScrim(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(16.dp))
        OwnTVTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = "PIN",
            placeholder = "····",
            keyboardType = KeyboardType.NumberPassword,
            isPassword = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(20.dp))
        // Explicit left/right links: spatial D-pad search from Cancel would otherwise wander into
        // the gate's profile tiles BEHIND the dialog before reaching OK.
        val cancelFocus = remember { FocusRequester() }
        val okFocus = remember { FocusRequester() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton(
                "Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY,
                modifier = Modifier.focusRequester(cancelFocus).focusProperties { right = okFocus },
            )
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                "OK", onClick = { onSubmit(pin) }, enabled = pin.length >= 4,
                modifier = Modifier.focusRequester(okFocus).focusProperties { left = cancelFocus },
            )
        }
    }
}

/**
 * Create / edit a profile: name, avatar, kids flag and an optional PIN. [initial] non-null = edit.
 * [onConfirm] receives (name, avatarId, isKids, pin): null = leave the PIN unchanged,
 * "" = remove the PIN lock, otherwise = set this PIN.
 */
@Composable
internal fun ProfileEditorDialog(
    initial: ProfileEntity?,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: 0) }
    var isKids by remember { mutableStateOf(initial?.isKids ?: false) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ProfileScrim(onDismiss) {
        Text(if (initial == null) "New profile" else "Edit profile", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(16.dp))
        val nameLocked = initial != null && ProfilesViewModel.isDefaultProfile(initial)
        OwnTVTextField(
            name,
            { if (!nameLocked) name = it },
            label = "Name",
            placeholder = "e.g. Alex",
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (nameLocked) {
            Spacer(Modifier.height(4.dp))
            Text("The default profile's name is locked.", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        Text("AVATAR", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((0 until OwnTVAvatars.COUNT).toList()) { id ->
                FocusableSurface(
                    onClick = { avatarId = id },
                    modifier = Modifier.size(60.dp),
                    selected = id == avatarId,
                    shape = CircleShape,
                    selectedContainerColor = colors.primaryContainer,
                    contentAlignment = Alignment.Center,
                ) { _ ->
                    OwnTVAvatar(avatarId = id, modifier = Modifier.size(48.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        ToggleRow(label = "Kids profile", desc = "Simplified, safe browsing", checked = isKids) { isKids = it }
        Spacer(Modifier.height(12.dp))
        if (initial?.pinHash != null) {
            ToggleRow(label = "Remove PIN lock", desc = "This profile opens without a PIN", checked = removePin) { removePin = it }
            Spacer(Modifier.height(12.dp))
        }
        if (!removePin) {
            OwnTVTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = if (initial?.pinHash != null) "Change PIN (blank = keep)" else "PIN (optional)",
                placeholder = "4–6 digits",
                keyboardType = KeyboardType.NumberPassword,
                isPassword = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            OwnTVButton(
                label = if (initial == null) "Create" else "Save",
                onClick = { onConfirm(name, avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && (removePin || pin.isEmpty() || pin.length >= 4),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { onToggle(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.width(52.dp).height(30.dp).clip(CircleShape)
                    .background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
