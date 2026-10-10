package echo.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoIconButton

/** The few preferences ECHO has. They are stored on the phone and never leave it. */
@Composable
fun SettingsScreen(
    showCompanion: Boolean,
    onShowCompanionChange: (Boolean) -> Unit,
    onShowWelcome: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.Xs, vertical = EchoSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text("SETTINGS", style = EchoTypography.Heading, modifier = Modifier.padding(start = EchoSpacing.S).semantics { heading() })
        }
        Row(
            modifier = Modifier
                .padding(EchoSpacing.L)
                .fillMaxWidth()
                .background(EchoColors.Graphite, EchoShapes.Large)
                .border(1.dp, EchoColors.Separator, EchoShapes.Large)
                .toggleable(value = showCompanion, role = Role.Switch, onValueChange = onShowCompanionChange)
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
                checked = showCompanion,
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
            modifier = Modifier
                .padding(horizontal = EchoSpacing.L)
                .fillMaxWidth()
                .background(EchoColors.Graphite, EchoShapes.Large)
                .border(1.dp, EchoColors.Separator, EchoShapes.Large)
                .clickable(role = Role.Button, onClick = onShowWelcome)
                .padding(EchoSpacing.L)
                .heightIn(min = 48.dp),
        ) {
            Text("Show welcome hints", style = EchoTypography.Body)
            Text("A quick tour of swipes, playlists and widgets.", style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.Xs))
        }
        AboutSection()
    }
}
