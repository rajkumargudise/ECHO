package echo.player.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EchoColorScheme = darkColorScheme(
    primary = EchoColors.Lime,
    onPrimary = EchoColors.OnLime,
    primaryContainer = EchoColors.LimeFaint,
    onPrimaryContainer = EchoColors.Lime,
    secondary = EchoColors.Lime,
    onSecondary = EchoColors.OnLime,
    background = EchoColors.Black,
    onBackground = EchoColors.TextPrimary,
    surface = EchoColors.Graphite,
    onSurface = EchoColors.TextPrimary,
    surfaceVariant = EchoColors.Steel,
    onSurfaceVariant = EchoColors.TextSecondary,
    surfaceContainer = EchoColors.Graphite,
    surfaceContainerHigh = EchoColors.Steel,
    outline = EchoColors.Metal,
    outlineVariant = EchoColors.Separator,
    error = EchoColors.Danger,
)

/** ECHO is a dark-only product; it does not follow the system light theme. */
@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EchoColorScheme,
        typography = EchoTypography.material,
        shapes = EchoShapes.material,
        content = content,
    )
}
