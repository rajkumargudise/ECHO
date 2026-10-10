package echo.player.ui.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoButtonStyle
import echo.player.design.components.EchoCard
import echo.player.design.components.EchoIconButton
import echo.player.design.mascot.EchoMascotStatic
import echo.player.companion.MascotExpression
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRootStatus
import echo.player.platform.android.MusicRootInfo

/** First run: ECHO does not scan the phone until the user says where their music is. */
@Composable
fun MusicSetupScreen(
    onUseDefaultFolder: () -> Unit,
    onAddFolder: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.Black)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(EchoSpacing.Xl).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EchoSpacing.L),
        ) {
            EchoMascotStatic(expression = MascotExpression.WAITING, size = 96.dp)
            Text("ECHO", style = EchoTypography.Title.copy(color = EchoColors.Lime, letterSpacing = 4.sp))
            Text("YOUR MUSIC", style = EchoTypography.ReadoutLarge)
            Text(
                text = "ECHO can start with your Music folder.\n\nIt only reads the folders you choose here. Nothing else on your phone is scanned, and nothing leaves your device.",
                style = EchoTypography.Body.copy(color = EchoColors.TextSecondary),
            )
            EchoButton(text = "Use Music folder", onClick = onUseDefaultFolder, modifier = Modifier.fillMaxWidth())
            EchoButton(text = "Add music folder", onClick = onAddFolder, style = EchoButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun MusicFoldersScreen(
    folders: List<MusicRootInfo>,
    message: String?,
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onReselect: (MusicRoot) -> Unit,
    onRemove: (MusicRoot) -> Unit,
    onRescan: () -> Unit,
    onUseDefaultFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.Black)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.Xs, vertical = EchoSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text("MUSIC FOLDERS", style = EchoTypography.Heading, modifier = Modifier.padding(start = EchoSpacing.S))
        }
        Text(
            text = "ECHO plays music from these folders only.",
            style = EchoTypography.Caption,
            modifier = Modifier.padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.Xs),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(EchoSpacing.L),
            verticalArrangement = Arrangement.spacedBy(EchoSpacing.M),
        ) {
            if (folders.isEmpty()) {
                item {
                    Text("No folders selected. Add one to see your music.", style = EchoTypography.Body.copy(color = EchoColors.TextSecondary))
                }
            }
            items(folders, key = { it.root.key }) { info ->
                MusicFolderCard(info = info, onReselect = onReselect, onRemove = onRemove)
            }
        }

        message?.let {
            Text(
                text = it,
                style = EchoTypography.Caption.copy(color = EchoColors.Lime),
                modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(EchoSpacing.L),
            verticalArrangement = Arrangement.spacedBy(EchoSpacing.S),
        ) {
            EchoButton(text = "Add music folder", onClick = onAddFolder, modifier = Modifier.fillMaxWidth())
            if (folders.none { it.root.isDefault }) {
                EchoButton(text = "Use standard Music folder", onClick = onUseDefaultFolder, style = EchoButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
            EchoButton(text = "Rescan music", onClick = onRescan, style = EchoButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MusicFolderCard(
    info: MusicRootInfo,
    onReselect: (MusicRoot) -> Unit,
    onRemove: (MusicRoot) -> Unit,
) {
    val root = info.root
    val ok = info.status == MusicRootStatus.AVAILABLE
    EchoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = if (ok) EchoColors.Lime else EchoColors.Warning, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.M)) {
                Text(root.displayPath, style = EchoTypography.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (root.isDefault) {
                        "Standard Music folder · internal storage and SD card"
                    } else {
                        "Chosen folder" + if (root.volumeId != "primary") " · SD card" else ""
                    },
                    style = EchoTypography.Caption,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = EchoSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) EchoColors.Lime else EchoColors.Warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when (info.status) {
                    MusicRootStatus.AVAILABLE -> "Available"
                    MusicRootStatus.PERMISSION_REVOKED -> "Access was revoked. Reselect this folder to use it again."
                    MusicRootStatus.UNAVAILABLE -> "Folder not found. Reselect it, or remove it."
                },
                style = EchoTypography.Caption.copy(color = if (ok) EchoColors.TextSecondary else EchoColors.Warning),
                modifier = Modifier.padding(start = EchoSpacing.S),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = EchoSpacing.M),
            horizontalArrangement = Arrangement.spacedBy(EchoSpacing.S),
        ) {
            if (!ok && !root.isDefault) {
                EchoButton(text = "Reselect", onClick = { onReselect(root) }, modifier = Modifier.weight(1f))
            }
            EchoButton(text = "Remove", onClick = { onRemove(root) }, style = EchoButtonStyle.Secondary, modifier = Modifier.weight(1f))
        }
    }
}
