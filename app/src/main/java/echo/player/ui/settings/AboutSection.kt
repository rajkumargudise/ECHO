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
