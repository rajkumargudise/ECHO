package echo.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.domain.PlaylistChoice
import echo.player.domain.PlaylistLogic
import echo.player.domain.countLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoTypography
import echo.player.domain.model.Playlist

/**
 * The chooser behind the "+" on a song: Create playlist first, then the existing playlists. Picking one acts at once
 * (the screen confirms with a short message), and creating asks only for a name, then adds the song.
 */
@Composable
fun PlaylistChooserDialog(
    songTitle: String,
    choices: List<PlaylistChoice>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    focusNameOnOpen: Boolean = false,
) {
    var creating by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(creating) { if (creating && focusNameOnOpen) nameFocus.requestFocus() }
    var name by remember { mutableStateOf("") }
    val clean = PlaylistLogic.cleanName(name)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EchoColors.Charcoal, EchoShapes.Panel)
                .border(1.dp, EchoColors.Separator, EchoShapes.Panel)
                .padding(vertical = EchoSpacing.S),
        ) {
            Text("ADD TO PLAYLIST", style = EchoTypography.Label.copy(color = EchoColors.Lime), modifier = Modifier.padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S))
            Text(songTitle, style = EchoTypography.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = EchoSpacing.L))
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(EchoSpacing.L).focusRequester(nameFocus),
                    label = { Text("Playlist name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (clean.isNotEmpty()) onCreate(clean) }),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.S), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false }) { Text("Back") }
                    TextButton(onClick = { onCreate(clean) }, enabled = clean.isNotEmpty()) { Text("Create and add") }
                }
            } else {
                ChooserRow(title = "+  Create playlist", caption = null, lime = true) { creating = true }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(choices, key = { it.id }) { choice ->
                        ChooserRow(
                            title = choice.name,
                            caption = (countLabel(choice.trackCount, "track")) + if (choice.alreadyHasTrack) " · already added" else "",
                            lime = false,
                        ) { onPick(choice.id) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.S), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ChooserRow(title: String, caption: String?, lime: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = EchoTypography.Body.copy(color = if (lime) EchoColors.Lime else EchoColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) Text(caption, style = EchoTypography.Caption)
    }
}

/** Dialog windows resize for the keyboard, so the field and both buttons stay reachable. */
@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    focusNameOnOpen: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusNameOnOpen) nameFocus.requestFocus() }
    val trimmed = name.trim()
    val canConfirm = trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                label = { Text("Playlist name") },
                singleLine = true,
                isError = name.isNotEmpty() && !canConfirm,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(trimmed) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(trimmed) }, enabled = canConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
