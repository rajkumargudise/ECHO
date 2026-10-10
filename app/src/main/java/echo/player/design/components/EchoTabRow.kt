package echo.player.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoMotion
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography

/**
 * Library tabs. Labels are always shown in full (never clipped to a letter) and the row scrolls sideways if a
 * narrow screen cannot hold them all. The selected tab has a lime underline and bright text.
 */
@Composable
fun EchoTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(EchoColors.Black)
            .drawBehind {
                drawLine(EchoColors.Separator, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
            }
            .horizontalScroll(rememberScrollState()),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val color by animateColorAsState(
                targetValue = if (selected) EchoColors.Lime else EchoColors.TextSecondary,
                animationSpec = tween(EchoMotion.Medium),
                label = "tabColor",
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { this.selected = selected }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = EchoSpacing.M)
                    .drawBehind {
                        if (selected) {
                            drawLine(EchoColors.Lime, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label.uppercase(), style = EchoTypography.Label.copy(color = color), maxLines = 1, softWrap = false)
            }
        }
    }
}
