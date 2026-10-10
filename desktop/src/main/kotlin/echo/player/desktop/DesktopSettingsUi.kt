package echo.player.desktop

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import echo.player.companion.MascotExpression
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoIconButton
import echo.player.design.mascot.EchoMascotStatic
import echo.player.ui.settings.AboutInfo
import kotlinx.coroutines.launch

/** The version shown in About; it matches the installer's version in desktop/build.gradle.kts. */
object DesktopBuild {
    const val VERSION = "1.0.0"
}

@Composable
fun DesktopSettings(app: DesktopApp) {
    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Row(modifier = Modifier.fillMaxWidth().padding(EchoSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", app::back)
            Text("SETTINGS", style = EchoTypography.Heading, modifier = Modifier.padding(start = EchoSpacing.S).semantics { heading() })
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = card()
                        .toggleable(value = app.showCompanion, role = Role.Switch, onValueChange = app::updateShowCompanion)
                        .padding(EchoSpacing.L)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show ECHO companion", style = EchoTypography.Body)
                        Text(
                            "The little robot on Now Playing that moves with your music. Turn it off and it stops drawing and animating.",
                            style = EchoTypography.Caption,
                            modifier = Modifier.padding(top = EchoSpacing.Xs),
                        )
                    }
                    Switch(
                        checked = app.showCompanion,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EchoColors.Black,
                            checkedTrackColor = EchoColors.Lime,
                            uncheckedThumbColor = EchoColors.TextSecondary,
                            uncheckedTrackColor = EchoColors.Steel,
                        ),
                    )
                }
                Column(
                    modifier = card().clickable(role = Role.Button, onClick = { app.screen = DesktopScreen.MUSIC_FOLDERS }).padding(EchoSpacing.L).heightIn(min = 48.dp),
                ) {
                    Text("Music folders", style = EchoTypography.Body)
                    Text("Choose where ECHO looks for your music.", style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.Xs))
                }
                Column(
                    modifier = card().clickable(role = Role.Button, onClick = app::showWelcomeAgain).padding(EchoSpacing.L).heightIn(min = 48.dp),
                ) {
                    Text("Show welcome hints", style = EchoTypography.Body)
                    Text("A quick tour of ECHO.", style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.Xs))
                }
                DesktopAbout()
            }
        }
    }
}

private fun card(): Modifier = Modifier
    .padding(horizontal = EchoSpacing.L)
    .padding(top = EchoSpacing.L)
    .fillMaxWidth()
    .background(EchoColors.Graphite, EchoShapes.Large)
    .border(1.dp, EchoColors.Separator, EchoShapes.Large)

@Composable
private fun DesktopAbout() {
    Column(modifier = card().padding(EchoSpacing.L)) {
        Text("ABOUT ECHO", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        Text(AboutInfo.versionLine(DesktopBuild.VERSION, 1), style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        Text(AboutInfo.CREDIT, style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.Xs))
    }
    Column(modifier = card().padding(EchoSpacing.L)) {
        Text("JARVEX", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        Text(AboutInfo.COMPANY, style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        Link("Website: ${AboutInfo.WEBSITE_LABEL}") { browse(AboutInfo.WEBSITE_URL) }
        Link("Email: ${AboutInfo.EMAIL}") { mail(AboutInfo.EMAIL) }
        Text("Phone: ${AboutInfo.PHONE_LABEL}", style = EchoTypography.Body, modifier = Modifier.padding(vertical = 14.dp))
    }
    Column(modifier = card().padding(EchoSpacing.L)) {
        Text("PRIVACY", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        for (line in AboutInfo.desktopPrivacy) Text(line, style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        Text(AboutInfo.PRIVACY_CONTACT, style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.M))
    }
    Box(modifier = Modifier.size(EchoSpacing.L))
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = EchoTypography.Body.copy(color = EchoColors.Lime),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
    )
}

private fun browse(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

private fun mail(address: String) {
    runCatching { java.awt.Desktop.getDesktop().mail(java.net.URI("mailto:$address")) }
}

/** What the first-run welcome says on a computer. */
object DesktopWelcomeContent {
    val hints = listOf(
        Triple("WELCOME TO ECHO", "Your own music, on your own computer. No account, no ads, no subscription. These few hints show what ECHO can do.", MascotExpression.NEUTRAL),
        Triple("CHOOSE YOUR MUSIC", "Use the folder button to pick the folders your music is in. ECHO reads the tags and cover art, and finds new songs when you rescan.", MascotExpression.WAITING),
        Triple("YOUR LIBRARY", "Browse by songs, artists, albums and folders. Tap the heart for favorites, and the + to add a song to a playlist.", MascotExpression.NEUTRAL),
        Triple("NOW PLAYING", "Click the display or use the arrows to change the visualization. Space plays or pauses, Ctrl with the left and right arrows skips songs.", MascotExpression.WAITING),
    )
}

@Composable
fun DesktopWelcome(onDone: () -> Unit) {
    val hints = DesktopWelcomeContent.hints
    val pager = rememberPagerState(pageCount = { hints.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == hints.lastIndex
    Box(modifier = Modifier.fillMaxSize().background(EchoColors.Black).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().padding(EchoSpacing.L)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "SKIP",
                    style = EchoTypography.Label.copy(color = EchoColors.TextSecondary),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(EchoSpacing.M),
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val (title, body, mascot) = hints[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = EchoSpacing.Xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EchoMascotStatic(expression = mascot, size = 120.dp)
                    Text(title, style = EchoTypography.Title.copy(color = EchoColors.Lime, textAlign = TextAlign.Center), modifier = Modifier.padding(top = EchoSpacing.Xl))
                    Text(body, style = EchoTypography.Body.copy(color = EchoColors.TextSecondary, textAlign = TextAlign.Center), modifier = Modifier.padding(top = EchoSpacing.M))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = EchoSpacing.L), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
