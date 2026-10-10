package echo.player.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoElevation
import echo.player.design.EchoMotion
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import kotlin.math.cos
import kotlin.math.sin

enum class EchoButtonStyle { Primary, Secondary }

/** Text button. Primary is the single lime call to action on a screen. */
@Composable
fun EchoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EchoButtonStyle = EchoButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val primary = style == EchoButtonStyle.Primary
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> EchoColors.Steel
            primary && pressed -> EchoColors.LimeDim
            primary -> EchoColors.Lime
            pressed -> EchoColors.Steel
            else -> EchoColors.Graphite
        },
        animationSpec = tween(EchoMotion.Fast),
        label = "buttonBackground",
    )
    Box(
        modifier = modifier
            .sizeIn(minHeight = EchoSpacing.Touch)
            .clip(EchoShapes.Medium)
            .background(background)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.dp, EchoColors.Metal), EchoShapes.Medium))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.M),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = EchoTypography.Label.copy(
                color = when {
                    !enabled -> EchoColors.TextMuted
                    primary -> EchoColors.OnLime
                    else -> EchoColors.TextPrimary
                },
            ),
        )
    }
}

/** Plain icon button with a 48dp touch target and a pressed highlight. */
@Composable
fun EchoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = EchoColors.TextPrimary,
    enabled: Boolean = true,
    size: Dp = EchoSpacing.Touch,
) {
    val interaction = remember { MutableInteractionSource() }
    // Icon buttons appear in every list row, so they stay light: the press highlight is painted while drawing
    // (no recomposition, no animation object, no clip layer).
    val pressed = interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { if (pressed.value) drawCircle(EchoColors.Steel) }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else EchoColors.TextMuted)
    }
}

/**
 * A two-state control (shuffle, repeat, favorite). State is shown three ways, never by color alone:
 * the icon can change, a lit indicator bar appears under it, and screen readers hear [stateText].
 */
@Composable
fun EchoToggle(
    icon: ImageVector,
    label: String,
    stateText: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeTint: Color = EchoColors.Lime,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(
        targetValue = if (active) activeTint else EchoColors.TextSecondary,
        animationSpec = tween(EchoMotion.Medium),
        label = "toggleTint",
    )
    val background by animateColorAsState(
        targetValue = when {
            pressed -> EchoColors.Steel
            active -> EchoColors.LimeFaint
            else -> Color.Transparent
        },
        animationSpec = tween(EchoMotion.Fast),
        label = "toggleBackground",
    )
    Box(
        modifier = modifier
            .size(EchoSpacing.Touch)
            .clip(EchoShapes.Medium)
            .background(background)
            .semantics { stateDescription = stateText }
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(width = 14.dp, height = 3.dp)
                .clip(CircleShape)
                .background(if (active) activeTint else Color.Transparent),
        )
    }
}

/**
 * Transport key (previous / play / next). It reads as a physical key: a lit top edge, a ledge below, and
 * on press the face drops onto the ledge. The primary key is lime.
 */
@Composable
fun EchoTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(EchoMotion.Fast),
        label = "keyDrop",
    )
    val ledge = EchoElevation.KeyLedge
    val faceTop = if (primary) EchoColors.Lime else EchoColors.Highlight
    val faceBottom = if (primary) EchoColors.LimeDim else EchoColors.Steel
    val shape = EchoShapes.Key
    Box(modifier = modifier.size(width = size, height = size + ledge)) {
        // The ledge underneath: this is what makes the key look raised.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(size)
                .clip(shape)
                .background(if (primary) Color(0xFF6E7A18) else EchoColors.DisplayDeep),
        )
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = (ledge - EchoElevation.KeyLedgePressed) * drop)
                .clip(shape)
                .background(Brush.verticalGradient(if (pressed) listOf(faceBottom, faceTop) else listOf(faceTop, faceBottom)))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(if (primary) Color(0xFFF6FFB0) else EchoColors.Highlight, EchoColors.Metal)),
                    shape,
                )
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> EchoColors.TextMuted
                    primary -> EchoColors.OnLime
                    else -> EchoColors.TextPrimary
                },
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/**
 * A small rotary selector. Tapping advances to the next detent and the knob turns to it.
 * Used for the display mode; the current value is also printed under the knob, so state is never color-only.
 */
@Composable
fun EchoDial(
    label: String,
    valueText: String,
    positions: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    onClick: (() -> Unit)? = null,
    spokenLabel: String = label,
    hint: String = "Tap to change.",
) {
    val sweep = 100f
    val targetAngle = if (positions <= 1) 0f else -sweep / 2 + sweep * selected / (positions - 1)
    val angle by animateFloatAsState(targetValue = targetAngle, animationSpec = tween(EchoMotion.Medium, easing = EchoMotion.Standard), label = "dialAngle")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            .clip(EchoShapes.Medium)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) { if (onClick != null) onClick() else onSelect((selected + 1) % positions) }
            .semantics(mergeDescendants = true) {
                contentDescription = "$spokenLabel: $valueText. $hint"
            }
            .padding(EchoSpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Canvas(modifier = Modifier.size(size).clearAndSetSemantics { }) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // Detent marks around the top of the knob.
            for (i in 0 until positions) {
                val markAngle = Math.toRadians((-90f + (if (positions <= 1) 0f else -sweep / 2 + sweep * i / (positions - 1))).toDouble())
                val cosA = cos(markAngle).toFloat()
                val sinA = sin(markAngle).toFloat()
                drawLine(
                    color = if (i == selected) EchoColors.Lime else EchoColors.Metal,
                    start = Offset(center.x + (radius - 5.dp.toPx()) * cosA, center.y + (radius - 5.dp.toPx()) * sinA),
                    end = Offset(center.x + radius * cosA, center.y + radius * sinA),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Knob body.
            val body = radius * 0.72f
            drawCircle(color = EchoColors.DisplayDeep, radius = body + 2.dp.toPx(), center = Offset(center.x, center.y + 2.dp.toPx()))
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(if (pressed) EchoColors.Steel else EchoColors.Highlight, if (pressed) EchoColors.Highlight else EchoColors.Steel),
                    startY = center.y - body,
                    endY = center.y + body,
                ),
                radius = body,
                center = center,
            )
            // Pointer.
            val pointer = Math.toRadians((angle - 90f).toDouble())
            drawLine(
                color = EchoColors.Lime,
                start = Offset(center.x + body * 0.25f * cos(pointer).toFloat(), center.y + body * 0.25f * sin(pointer).toFloat()),
                end = Offset(center.x + body * 0.85f * cos(pointer).toFloat(), center.y + body * 0.85f * sin(pointer).toFloat()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(valueText, style = EchoTypography.Readout.copy(color = EchoColors.Lime))
        Text(label.uppercase(), style = EchoTypography.Label.copy(fontSize = 10.sp, color = EchoColors.TextMuted))
    }
}
