package echo.player.design.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import echo.player.companion.CompanionBrain
import echo.player.companion.CompanionState
import echo.player.companion.MascotExpression
import echo.player.companion.MascotPose
import echo.player.companion.StaticMascotPose
import echo.player.design.EchoColors
import kotlin.math.max

/*
 * ECHO, the one mascot. Every place the character appears (Now Playing, the Pulse visualization, empty states,
 * idle screens) draws it with the single function below, so it is always the same character: a
 * dark graphite music machine with a screen for a face, little speaker "ears" at the sides, an antenna with a
 * status light and a lime glow that stands for the display and the music.
 *
 * It is drawn from a handful of shapes on a 16 x 16 grid (no bitmaps, no animation files), so it costs almost
 * nothing to store or to draw. The launcher icon is drawn from the same proportions (res/drawable/ic_launcher_foreground.xml).
 * Everything here is decorative and hidden from accessibility.
 */

/** Draws ECHO into the square (0,0)-(extent,extent) of the current drawing scope. */
fun DrawScope.drawEchoMascot(pose: MascotPose, extent: Float) {
    val u = extent / 16f
    val glow = pose.glow
    val lit = EchoColors.Lime.copy(alpha = 0.25f + 0.75f * glow)
    val bobPx = pose.bob * 1.0f * u
    val slumpPx = pose.slump * 1.8f * u
    val state = pose.state
    val outline = Stroke(0.35f * u)

    // Feet stay planted; the body above them bobs and sinks.
    drawRoundRect(EchoColors.Metal, Offset(5.2f * u, 14.3f * u), Size(1.9f * u, 1.5f * u), CornerRadius(0.4f * u))
    drawRoundRect(EchoColors.Metal, Offset(8.9f * u, 14.3f * u), Size(1.9f * u, 1.5f * u), CornerRadius(0.4f * u))

    translate(top = bobPx * 0.6f + slumpPx * 0.5f) {
        // Arms: hanging at rest, swung up as the music gets louder.
        val raise = pose.arms
        val shoulderY = 10.9f * u
        val handLeft = Offset((3.0f - 1.4f * raise) * u, (13.6f - 5.2f * raise) * u)
        val handRight = Offset((13.0f + 1.4f * raise) * u, (13.6f - 5.2f * raise) * u)
        drawLine(EchoColors.Metal, Offset(4.2f * u, shoulderY), handLeft, 1.3f * u, StrokeCap.Round)
        drawLine(EchoColors.Metal, Offset(11.8f * u, shoulderY), handRight, 1.3f * u, StrokeCap.Round)
        drawCircle(lit, 0.7f * u, handLeft)
        drawCircle(lit, 0.7f * u, handRight)

        // Body with a chest light.
        drawRoundRect(EchoColors.Steel, Offset(4f * u, 10f * u), Size(8f * u, 4.8f * u), CornerRadius(1.2f * u))
        drawRoundRect(EchoColors.Metal, Offset(4f * u, 10f * u), Size(8f * u, 4.8f * u), CornerRadius(1.2f * u), style = outline)
        drawCircle(EchoColors.Lime.copy(alpha = 0.18f * glow), 1.9f * u, Offset(8f * u, 12.4f * u))
        drawCircle(lit, 0.85f * u, Offset(8f * u, 12.4f * u))
    }

    // Head: tilts and bobs, and drops when sad.
    val headTop = 2.6f * u + bobPx + slumpPx
    rotate(degrees = pose.tilt * 10f, pivot = Offset(8f * u, headTop + 3.5f * u)) {
        // Antenna with its status light.
        drawLine(EchoColors.Metal, Offset(8f * u, headTop), Offset(8f * u, headTop - 1.3f * u), 0.8f * u, StrokeCap.Round)
        val tipAlpha = if (state == CompanionState.SLEEPING) 0.15f else 0.3f + 0.7f * glow
        drawCircle(EchoColors.Lime.copy(alpha = tipAlpha), 0.95f * u, Offset(8f * u, headTop - 1.5f * u))

        // Speaker "ears": the detail that makes ECHO a music machine and gives it its silhouette.
        for (side in 0..1) {
            val x = if (side == 0) 1.0f * u else 13.8f * u
            drawRoundRect(EchoColors.Metal, Offset(x, headTop + 2.0f * u), Size(1.2f * u, 3.2f * u), CornerRadius(0.5f * u))
            drawRoundRect(EchoColors.Lime.copy(alpha = 0.15f + 0.5f * glow), Offset(x + 0.3f * u, headTop + 2.7f * u), Size(0.6f * u, 1.8f * u), CornerRadius(0.3f * u))
        }

        // Head shell and face screen.
        drawRoundRect(EchoColors.Graphite, Offset(2.1f * u, headTop), Size(11.8f * u, 7.2f * u), CornerRadius(1.8f * u))
        drawRoundRect(EchoColors.Highlight, Offset(2.1f * u, headTop), Size(11.8f * u, 7.2f * u), CornerRadius(1.8f * u), style = Stroke(0.4f * u))
        drawRoundRect(EchoColors.DisplayDeep, Offset(3.4f * u, headTop + 1.2f * u), Size(9.2f * u, 4.8f * u), CornerRadius(1f * u))

        // Eyes: a glowing bar that squints shut when sleepy, with sad brows when paused.
        val eyeHeight = max(0.45f * u, pose.eyeOpen * 2.5f * u)
        val eyeY = headTop + 2.2f * u + (2.5f * u - eyeHeight) / 2f + (if (state == CompanionState.PAUSED) 0.5f * u else 0f)
        val eyeColor = EchoColors.Lime.copy(alpha = 0.2f + 0.8f * glow)
        drawRoundRect(eyeColor, Offset(5.0f * u, eyeY), Size(1.9f * u, eyeHeight), CornerRadius(0.4f * u))
        drawRoundRect(eyeColor, Offset(9.1f * u, eyeY), Size(1.9f * u, eyeHeight), CornerRadius(0.4f * u))
        if (state == CompanionState.PAUSED) {
            // Sad, sleepy brows: the inner ends are higher than the outer ends (the other way round reads as angry).
            drawLine(eyeColor, Offset(4.7f * u, headTop + 2.3f * u), Offset(6.9f * u, headTop + 1.7f * u), 0.4f * u, StrokeCap.Round)
            drawLine(eyeColor, Offset(11.3f * u, headTop + 2.3f * u), Offset(9.1f * u, headTop + 1.7f * u), 0.4f * u, StrokeCap.Round)
        }

        // Mouth: a small smile while music plays, a flat or drooping line otherwise.
        // Low enough that even the widest, most lifted smile stays clear of the bottom of the eyes.
        val mouthY = headTop + 5.3f * u
        val happy = state == CompanionState.DANCING || state == CompanionState.ENERGETIC ||
            state == CompanionState.CELEBRATING || state == CompanionState.WAKING
        val corner = if (happy) -0.35f * u else if (state == CompanionState.PAUSED) 0.4f * u else 0f
        val mouthWidth = if (state == CompanionState.ENERGETIC || state == CompanionState.CELEBRATING) 2.6f * u else 1.8f * u
        if (state != CompanionState.SLEEPING) {
            drawLine(eyeColor, Offset(8f * u - mouthWidth / 2f, mouthY + corner), Offset(8f * u, mouthY), 0.45f * u, StrokeCap.Round)
            drawLine(eyeColor, Offset(8f * u, mouthY), Offset(8f * u + mouthWidth / 2f, mouthY + corner), 0.45f * u, StrokeCap.Round)
        }
    }
}

/** The animated mascot. [tick] is read only while drawing, so it redraws without recomposing anything. */
@Composable
fun EchoMascotView(
    pose: CompanionBrain,
    tick: () -> Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
        tick()
        drawEchoMascot(pose, this.size.minDimension)
    }
}

/** A still ECHO for empty states. It has no animation, so it is drawn once and never again. */
@Composable
fun EchoMascotStatic(
    expression: MascotExpression,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val pose = remember(expression) { StaticMascotPose(expression) }
    Canvas(modifier = modifier.size(size).clearAndSetSemantics { }) {
        drawEchoMascot(pose, this.size.minDimension)
    }
}
