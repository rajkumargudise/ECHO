package echo.player.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

enum class SwipeDirection { LEFT, RIGHT, NONE }

/** Turns a finished horizontal drag into a swipe. Short drags (a tap with a wobble, a slow scroll) are ignored. */
object SwipeDecision {
    const val THRESHOLD_DP = 72f

    fun of(totalDragPx: Float, thresholdPx: Float): SwipeDirection = when {
        totalDragPx <= -thresholdPx -> SwipeDirection.LEFT
        totalDragPx >= thresholdPx -> SwipeDirection.RIGHT
        else -> SwipeDirection.NONE
    }
}

/**
 * Calls [onLeft] after a swipe towards the left and [onRight] after one towards the right. It only watches horizontal
 * movement, so vertical scrolling and taps on the same area keep working.
 */
fun Modifier.onHorizontalSwipe(onLeft: () -> Unit, onRight: () -> Unit): Modifier = composed {
    val left = rememberUpdatedState(onLeft)
    val right = rememberUpdatedState(onRight)
    val thresholdPx = with(LocalDensity.current) { SwipeDecision.THRESHOLD_DP.dp.toPx() }
    pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { _, delta -> total += delta },
            onDragEnd = {
                when (SwipeDecision.of(total, thresholdPx)) {
                    SwipeDirection.LEFT -> left.value()
                    SwipeDirection.RIGHT -> right.value()
                    SwipeDirection.NONE -> Unit
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
        )
    }
}

