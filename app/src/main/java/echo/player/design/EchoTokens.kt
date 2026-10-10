package echo.player.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object EchoSpacing {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp

    /** Smallest comfortable touch target. */
    val Touch = 48.dp
}

object EchoShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(18.dp)

    /** Inset display panels and the physical-looking transport keys. */
    val Panel = RoundedCornerShape(14.dp)
    val Key = RoundedCornerShape(16.dp)

    val material = Shapes(small = Small, medium = Medium, large = Large)
}

/** Depth is faked with offsets and edge highlights, not blur, so it is cheap and crisp on any screen. */
object EchoElevation {
    /** How far a key's ledge sits below its face when released. */
    val KeyLedge = 3.dp
    val KeyLedgePressed = 1.dp
    val Hairline = 1.dp
}

/** Short, purposeful transitions. Nothing bounces. */
object EchoMotion {
    const val Fast = 150
    const val Medium = 220
    const val Slow = 300

    val Standard = FastOutSlowInEasing
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
