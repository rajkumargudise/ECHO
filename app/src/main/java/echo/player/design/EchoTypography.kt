package echo.player.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sans for reading, monospace for anything that behaves like a digital readout (time, counters, mode names).
 * System fonts only, so there is nothing to bundle and it renders the same offline everywhere.
 */
object EchoTypography {
    val Title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = EchoColors.TextPrimary)
    val Heading = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = EchoColors.TextPrimary)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = EchoColors.TextPrimary)
    val Caption = TextStyle(fontSize = 12.sp, color = EchoColors.TextSecondary)

    /** Small all-caps label used for tabs, section names and panel captions. */
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = EchoColors.TextSecondary)

    val Readout = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = EchoColors.TextSecondary)
    val ReadoutLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EchoColors.TextPrimary)

    val material = Typography(
        titleLarge = Title,
        titleMedium = Heading,
        bodyLarge = Body,
        bodyMedium = Body.copy(fontSize = 14.sp),
        bodySmall = Caption,
        labelLarge = Label,
        labelMedium = Label,
        labelSmall = Label.copy(fontSize = 11.sp),
    )
}
