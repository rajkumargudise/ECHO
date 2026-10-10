package echo.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoCard
import echo.player.ui.PermissionPrompt

@Composable
fun PermissionScreen(prompt: PermissionPrompt, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(EchoColors.Black).padding(EchoSpacing.L),
        contentAlignment = Alignment.Center,
    ) {
        EchoCard {
            Column(verticalArrangement = Arrangement.spacedBy(EchoSpacing.L)) {
                Text("ECHO", style = EchoTypography.Title.copy(color = EchoColors.Lime, letterSpacing = 4.sp))
                Text(
                    text = when (prompt) {
                        PermissionPrompt.INITIAL ->
                            "Your music stays on your device.\n\nECHO needs access to your music so it can build your local library.\nNothing is uploaded by the music-library feature."
                        PermissionPrompt.DENIED ->
                            "MUSIC ACCESS REQUIRED\n\nECHO cannot display your device music without permission."
                        PermissionPrompt.BLOCKED ->
                            "MUSIC ACCESS REQUIRED\n\nECHO cannot display your device music without permission. Android will no longer show the permission prompt, so allow \"Music and audio\" for ECHO in App Settings, then come back."
                    },
                    style = EchoTypography.Body.copy(color = EchoColors.TextSecondary),
                )
                EchoButton(
                    text = when (prompt) {
                        PermissionPrompt.INITIAL -> "Allow music access"
                        PermissionPrompt.DENIED -> "Try again"
                        PermissionPrompt.BLOCKED -> "Open settings"
                    },
                    onClick = if (prompt == PermissionPrompt.BLOCKED) onOpenSettings else onAllow,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
