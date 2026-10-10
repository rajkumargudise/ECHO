package echo.player.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoMotion
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography

/**
 * One song in a list. The row that is playing is marked with a lime edge, a tinted background and a
 * level glyph (not color alone); a favorite shows a filled heart, and the lime "+" opens the playlist chooser directly.
 */
@Composable
fun EchoTrackRow(
    title: String,
    subtitle: String,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    durationText: String = "",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()

    // Rows are the hottest composable in the app (they are created while scrolling), so each one keeps to the
    // minimum: the press/current background and edge are painted in the draw phase, the text styles are shared
    // constants, and the overflow menu only exists while it is open.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (pressed.value) drawRect(EchoColors.Steel) else if (isCurrent) drawRect(EchoColors.LimeFaint)
                val stroke = 1.dp.toPx()
                drawLine(EchoColors.Separator, Offset(16.dp.toPx(), size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                if (isCurrent) drawRect(EchoColors.Lime, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = EchoSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = EchoSpacing.S)) {
            Text(
                text = title,
                style = if (isCurrent) RowTitleCurrent else RowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = EchoTypography.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = "Now playing",
                tint = EchoColors.Lime,
                modifier = Modifier.padding(horizontal = EchoSpacing.Xs).size(20.dp),
            )
        }
        if (durationText.isNotEmpty()) {
            Text(durationText, style = EchoTypography.Readout.copy(color = EchoColors.TextMuted), modifier = Modifier.padding(horizontal = EchoSpacing.Xs))
        }
        EchoIconButton(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            onClick = onToggleFavorite,
            tint = if (isFavorite) EchoColors.Lime else EchoColors.TextSecondary,
        )
        EchoIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add $title to a playlist",
            onClick = onAddToPlaylist,
            tint = EchoColors.Lime,
        )
    }
}

private val RowTitle = EchoTypography.Body
private val RowTitleCurrent = EchoTypography.Body.copy(color = EchoColors.Lime)

/** A row that opens a group (artist, album, folder, playlist). */
@Composable
fun EchoGroupRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                if (pressed.value) drawRect(EchoColors.Steel)
                val stroke = 1.dp.toPx()
                drawLine(EchoColors.Separator, Offset(16.dp.toPx(), size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = EchoSpacing.L, end = EchoSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = EchoSpacing.S)) {
            Text(title, style = EchoTypography.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = EchoTypography.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = EchoColors.TextMuted)
    }
}
