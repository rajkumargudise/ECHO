package echo.player.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import echo.player.BuildConfig
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography

/** What the About and Privacy cards say. Plain data so the wording is easy to review and test. */
object AboutInfo {
    const val APP_NAME = "ECHO"
    const val CREDIT = "ECHO is a product of JARVEX (jarvex.in). Developed and maintained by JARVEX."
    const val COMPANY = "JARVEX is a digital services company from India: websites and hosting, web and business applications, " +
        "AI automation and ongoing support. Build Smarter. Grow Faster."
    const val WEBSITE_LABEL = "jarvex.in"
    const val WEBSITE_URL = "https://jarvex.in"
    const val EMAIL = "info@jarvex.in"
    const val PHONE_LABEL = "+91 99495 01828"
    const val PHONE_URI = "tel:+919949501828"

    val privacy: List<String> = listOf(
        "Your music stays on your phone. ECHO reads the music you allow it to and never uploads it.",
        "No account, no ads and no tracking. ECHO has no analytics and JARVEX collects nothing about you from this app.",
        "Your favorites, playlists, recent plays and settings are stored only on this phone. Uninstalling ECHO removes them.",
        "Permissions: music access to build your library, notifications for the player controls, and internet only for optional online features. Online connections use HTTPS.",
    )
    const val PRIVACY_CONTACT = "Questions about privacy? Write to info@jarvex.in."

    fun versionLine(versionName: String, versionCode: Int) = "Version $versionName (build $versionCode)"
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun cardModifier() = Modifier
        .padding(horizontal = EchoSpacing.L)
        .padding(top = EchoSpacing.L)
        .fillMaxWidth()
        .background(EchoColors.Graphite, EchoShapes.Large)
        .border(1.dp, EchoColors.Separator, EchoShapes.Large)
        .padding(EchoSpacing.L)

    Column(modifier = cardModifier()) {
        Text("ABOUT ECHO", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        Text(AboutInfo.versionLine(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        Text(AboutInfo.CREDIT, style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.Xs))
    }
    Column(modifier = cardModifier()) {
        Text("JARVEX", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        Text(AboutInfo.COMPANY, style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        LinkRow("Website: ${AboutInfo.WEBSITE_LABEL}") { open(Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.WEBSITE_URL))) }
        LinkRow("Email: ${AboutInfo.EMAIL}") { open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AboutInfo.EMAIL}"))) }
        LinkRow("Phone: ${AboutInfo.PHONE_LABEL}") { open(Intent(Intent.ACTION_DIAL, Uri.parse(AboutInfo.PHONE_URI))) }
    }
    Column(modifier = cardModifier().padding(bottom = EchoSpacing.Xs)) {
        Text("PRIVACY", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.semantics { heading() })
        for (line in AboutInfo.privacy) {
            Text(line, style = EchoTypography.Body, modifier = Modifier.padding(top = EchoSpacing.S))
        }
        Text(AboutInfo.PRIVACY_CONTACT, style = EchoTypography.Caption, modifier = Modifier.padding(top = EchoSpacing.M))
    }
    Text("", modifier = Modifier.padding(bottom = EchoSpacing.L))
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = EchoTypography.Body.copy(color = EchoColors.Lime),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
    )
}
