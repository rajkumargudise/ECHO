package echo.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoTypography
import echo.player.design.components.EchoAlphabetIndex
import echo.player.design.components.EchoSectionHeader
import echo.player.domain.IndexRow
import echo.player.domain.SectionedItems
import kotlinx.coroutines.launch

/**
 * A long list grouped under letter headers with the fast-scroll index on its right edge. The index jumps to
 * header positions taken from [sections] itself, so what it scrolls to is exactly what is on screen.
 */
@Composable
fun <T> IndexedLazyList(
    sections: SectionedItems<T>,
    itemKey: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var bubble by remember { mutableStateOf<Char?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = 28.dp),
        ) {
            itemsIndexed(
                items = sections.rows,
                key = { _, row ->
                    when (row) {
                        is IndexRow.Header -> "header-${row.letter}"
                        is IndexRow.Entry -> itemKey(row.item)
                    }
                },
                contentType = { _, row -> if (row is IndexRow.Header) 0 else 1 },
            ) { _, indexRow ->
                when (indexRow) {
                    is IndexRow.Header -> EchoSectionHeader(indexRow.letter.toString())
                    is IndexRow.Entry -> row(indexRow.item)
                }
            }
        }

        EchoAlphabetIndex(
            present = sections.presentLetters,
            onJump = { letter -> sections.positionFor(letter)?.let { scope.launch { listState.scrollToItem(it) } } },
            onActiveLetter = { bubble = it },
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        bubble?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(EchoColors.Black.copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                    .border(1.dp, EchoColors.Lime, RoundedCornerShape(16.dp))
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter.toString(),
                    style = EchoTypography.ReadoutLarge.copy(fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = EchoColors.Lime),
                )
            }
        }
    }
}
