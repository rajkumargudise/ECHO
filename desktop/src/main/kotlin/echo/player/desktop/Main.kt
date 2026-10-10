package echo.player.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import echo.player.design.EchoTheme
import java.awt.Dimension

@Composable
fun EchoDesktopApp(app: DesktopApp) {
    EchoTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (app.screen) {
                DesktopScreen.LIBRARY -> DesktopLibrary(app)
                DesktopScreen.NOW_PLAYING -> DesktopNowPlaying(app)
                DesktopScreen.SETTINGS -> DesktopSettings(app)
                DesktopScreen.MUSIC_FOLDERS -> DesktopMusicFolders(app)
            }
            if (app.showWelcome) DesktopWelcome(onDone = app::dismissWelcome)
        }
    }
}

fun main() {
    val dataDir = DesktopPaths.dataDir()
    val app = DesktopApp(DesktopStore(dataDir), dataDir)
    application {
        val icon = remember {
            runCatching { BitmapPainter(DesktopApp::class.java.getResourceAsStream("/echo-icon.png")!!.use(::loadImageBitmap)) }.getOrNull()
        }
        Window(
            onCloseRequest = {
                app.close()
                exitApplication()
            },
            title = "ECHO",
            state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
            icon = icon,
            // Preview, so the shortcuts work even while a button has focus (Space would otherwise press that button).
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown || (app.typing && !event.isCtrlPressed && event.key != Key.Escape)) {
                    false
                } else {
                    when {
                        event.key == Key.MediaPlayPause || (event.key == Key.Spacebar && !event.isCtrlPressed) -> {
                            app.player.togglePlayPause()
                            true
                        }
                        event.key == Key.MediaNext || (event.isCtrlPressed && event.key == Key.DirectionRight) -> {
                            app.player.next()
                            true
                        }
                        event.key == Key.MediaPrevious || (event.isCtrlPressed && event.key == Key.DirectionLeft) -> {
                            app.player.previous()
                            true
                        }
                        event.key == Key.Escape -> {
                            app.back()
                            true
                        }
                        else -> false
                    }
                }
            },
        ) {
            window.minimumSize = Dimension(760, 560)
            EchoDesktopApp(app)
        }
    }
}
