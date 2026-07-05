package com.thirutricks.tllplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

@Composable
fun FocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(Dimens.CardCorner),
    focusedContainerColor: Color = OwnTVTheme.colors.card,
    unfocusedContainerColor: Color = Color.Transparent,
    selectedContainerColor: Color = OwnTVTheme.colors.card,
    focusedScale: Float = 1f,
    glowElevation: Int = 10,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        animationSpec = com.thirutricks.tllplayer.ui.theme.ownTvTween(140),
        label = "focusScale",
    )
    val container by animateColorAsState(
        when {
            focused -> focusedContainerColor
            selected -> selectedContainerColor
            else -> unfocusedContainerColor
        },
        animationSpec = com.thirutricks.tllplayer.ui.theme.ownTvTween(140),
        label = "focusContainer",
    )
    val showBorder = focused || selected

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (focused) glowElevation.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.focusGlow,
                spotColor = colors.focusGlow,
            )
            .clip(shape)
            .background(container)
            .then(
                if (showBorder) Modifier.border(Dimens.FocusBorderWidth, colors.focusBorder, shape)
                else Modifier
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                } else {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}
