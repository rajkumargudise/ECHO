package echo.player.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import echo.player.companion.MascotExpression
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoButtonStyle
import echo.player.design.mascot.EchoMascotStatic
import kotlinx.coroutines.launch

data class WelcomeHint(val title: String, val body: String, val mascot: MascotExpression)

/** What the first-run hints say. Kept as plain data so the wording is easy to review and test. */
object WelcomeContent {
    val hints: List<WelcomeHint> = listOf(
        WelcomeHint(
            "WELCOME TO ECHO",
            "Your own music, on your own phone. No account, no ads, no subscription. These few hints show what ECHO can do.",
            MascotExpression.NEUTRAL,
        ),
        WelcomeHint(
            "SWIPE THE LIBRARY",
            "Swipe left or right to move between Songs, Artists, Albums, Folders, Favorites and Playlists. Folders can be shown as tiles or a list.",
            MascotExpression.WAITING,
        ),
        WelcomeHint(
            "TAP + TO ADD TO A PLAYLIST",
            "The lime + on every song, and on Now Playing, opens your playlists. Choose one, or create a new playlist right there.",
            MascotExpression.NEUTRAL,
        ),
        WelcomeHint(
            "SWIPE ON NOW PLAYING",
            "Swipe the display to change the visualization. Swipe the song name: right for the next song, left for the previous one.",
            MascotExpression.WAITING,
        ),
        WelcomeHint(
            "WIDGETS ON YOUR HOME SCREEN",
            "Add an ECHO widget to see the song, its progress and a favorite heart, and to play, pause or skip without opening the app.",
            MascotExpression.NEUTRAL,
        ),
    )
}

/**
 * The first-run welcome: a few short cards with a Skip and a Next. It is shown once (the choice is remembered) and can be
 * brought back from Settings. Back also closes it.
 */
@Composable
fun WelcomeHints(onDone: () -> Unit) {
    val hints = WelcomeContent.hints
    val pager = rememberPagerState(pageCount = { hints.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == hints.lastIndex
    BackHandler(onBack = onDone)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.Black)
            .pointerInput(Unit) { detectTapGestures { } }, // nothing underneath may be tapped while the hints show
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(EchoSpacing.L)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "SKIP",
                    style = EchoTypography.Label.copy(color = EchoColors.TextSecondary),
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onDone)
                        .padding(horizontal = EchoSpacing.M, vertical = EchoSpacing.M),
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val hint = hints[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = EchoSpacing.Xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EchoMascotStatic(expression = hint.mascot, size = 120.dp)
                    Text(
                        hint.title,
                        style = EchoTypography.Title.copy(color = EchoColors.Lime, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(top = EchoSpacing.Xl),
                    )
                    Text(
                        hint.body,
                        style = EchoTypography.Body.copy(color = EchoColors.TextSecondary, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(top = EchoSpacing.M),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = EchoSpacing.L),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in hints.indices) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == pager.currentPage) 10.dp else 8.dp)
                            .background(if (i == pager.currentPage) EchoColors.Lime else EchoColors.Steel, CircleShape),
                    )
                }
            }
            EchoButton(
                text = if (last) "Get started" else "Next",
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (last) "Get started" else "Next hint" },
            )
        }
    }
}
