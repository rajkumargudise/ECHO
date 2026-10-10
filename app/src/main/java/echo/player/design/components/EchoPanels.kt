package echo.player.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography

/**
 * An inset display, like the window of a stereo: dark glass, a shadow falling from the top edge, and a
 * thin lit rim. Optional small captions sit in the corners like the printing on a front panel.
 */
@Composable
fun EchoDisplayPanel(
    modifier: Modifier = Modifier,
    caption: String? = null,
    readout: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(EchoShapes.Panel)
            .background(Brush.verticalGradient(listOf(EchoColors.DisplayDeep, Color(0xFF0E0E0E))))
            .border(1.dp, Brush.verticalGradient(listOf(EchoColors.Separator, EchoColors.Metal)), EchoShapes.Panel)
            .drawWithContent {
                drawContent()
                // Inset shadow from the top edge.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        endY = 18.dp.toPx(),
                    ),
                )
            },
    ) {
        content()
        if (caption != null) {
            Text(
                text = caption,
                style = EchoTypography.Label.copy(color = EchoColors.LimeDim),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = EchoSpacing.M, top = EchoSpacing.S),
            )
        }
        if (readout != null) {
            Text(
                text = readout,
                style = EchoTypography.Readout.copy(color = EchoColors.TextMuted),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = EchoSpacing.M, top = EchoSpacing.S),
            )
        }
    }
}

/** Letter header that groups an alphabetical list. Announced as a heading to screen readers. */
@Composable
fun EchoSectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(EchoColors.Charcoal)
            .padding(horizontal = EchoSpacing.L)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .drawBehind { drawRoundRect(EchoColors.Lime, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)) },
        )
        Text(
            text = text,
            style = EchoTypography.Label.copy(color = EchoColors.Lime),
            modifier = Modifier.padding(start = EchoSpacing.S),
        )
    }
}

/** Card with a title, supporting lines and trailing actions; used for music folders. */
@Composable
fun EchoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EchoShapes.Large)
            .background(EchoColors.Graphite)
            .border(1.dp, EchoColors.Separator, EchoShapes.Large)
            .padding(EchoSpacing.L),
    ) {
        content()
    }
}
