package echo.player.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.domain.AlphabetIndex

/**
 * Fast-scroll strip down the right edge of a long list. Touch or drag anywhere on it to jump; the letter under
 * the finger is reported through [onActiveLetter] so the list can show a bubble. Letters with no items are
 * dimmed (and a jump on them lands on the next populated letter). On very short screens, where 27 letters
 * would become too small to read, only populated letters are listed.
 */
@Composable
fun EchoAlphabetIndex(
    present: Set<Char>,
    onJump: (Char) -> Unit,
    onActiveLetter: (Char?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val jump by rememberUpdatedState(onJump)
    val active by rememberUpdatedState(onActiveLetter)

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(28.dp)) {
        val comfortable = maxHeight / AlphabetIndex.LETTERS.size >= 14.dp
        val letters = remember(comfortable, present) {
            if (comfortable) AlphabetIndex.LETTERS else AlphabetIndex.LETTERS.filter { it in present }
        }
        if (letters.isEmpty()) return@BoxWithConstraints

        var lastLetter by remember { mutableStateOf<Char?>(null) }
        fun select(y: Float, height: Float) {
            val index = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
            val letter = letters[index]
            if (letter != lastLetter) {
                lastLetter = letter
                active(letter)
                jump(letter)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(EchoColors.Black.copy(alpha = 0.55f))
                .pointerInput(letters) {
                    detectTapGestures(
                        onPress = { offset ->
                            select(offset.y, size.height.toFloat())
                            tryAwaitRelease()
                            lastLetter = null
                            active(null)
                        },
                    )
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> select(offset.y, size.height.toFloat()) },
                        onDragEnd = {
                            lastLetter = null
                            active(null)
                        },
                        onDragCancel = {
                            lastLetter = null
                            active(null)
                        },
                    ) { change, _ -> select(change.position.y, size.height.toFloat()) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (letter in letters) {
                val populated = letter in present
                Text(
                    text = letter.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Button
                            contentDescription = if (letter == AlphabetIndex.OTHER) "Jump to numbers and symbols" else "Jump to $letter"
                            onClick {
                                jump(letter)
                                true
                            }
                        },
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (populated) FontWeight.Bold else FontWeight.Normal,
                        color = if (populated) EchoColors.Lime else EchoColors.TextMuted,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
