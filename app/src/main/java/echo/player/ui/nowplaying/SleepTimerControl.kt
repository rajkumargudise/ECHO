package echo.player.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.playback.SleepTimer
import echo.player.playback.SleepTimerFormat
import echo.player.playback.SleepTimerPresets
import echo.player.playback.SleepTimerState

/**
 * The sleep timer control on Now Playing: a small key that reads "SLEEP" when off and the time left (23:41) when
 * running. Tapping it opens a compact menu: a duration to start or replace the timer, a custom length, and,
 * while one runs, Cancel timer. The countdown itself lives in [SleepTimer], not here, so leaving the screen or
 * rotating does not touch it; this only shows it, refreshed about once a second.
 */
@Composable
fun SleepTimerControl(timer: SleepTimer, modifier: Modifier = Modifier) {
    val state by timer.state.collectAsState()
    val active = state as? SleepTimerState.Active
    var menuOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        SleepTimerKey(active = active, onClick = { menuOpen = true })
        if (menuOpen) {
            DropdownMenu(expanded = true, onDismissRequest = { menuOpen = false }) {
                Text(
                    text = if (active == null) "SLEEP TIMER" else "SLEEP TIMER · ${SleepTimerFormat.format(active.remainingMs)}",
                    style = EchoTypography.Label.copy(color = EchoColors.Lime),
                    modifier = Modifier.padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
                )
                for (minutes in SleepTimerPresets.MINUTES) {
                    DropdownMenuItem(
                        text = { Text("$minutes min") },
                        onClick = {
                            menuOpen = false
                            timer.startMinutes(minutes)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Custom…") },
                    onClick = {
                        menuOpen = false
                        customOpen = true
                    },
                )
                if (active != null) {
                    DropdownMenuItem(
                        text = { Text("Cancel timer", color = EchoColors.Danger) },
                        onClick = {
                            menuOpen = false
                            timer.cancel()
                        },
                    )
                }
            }
        }
    }

    if (customOpen) {
        CustomSleepTimerDialog(
            onStart = { minutes ->
                customOpen = false
                timer.startMinutes(minutes)
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun SleepTimerKey(active: SleepTimerState.Active?, onClick: () -> Unit) {
    val description = if (active == null) {
        "Sleep timer off. Double tap to set."
    } else {
        "Sleep timer, ${SleepTimerFormat.spoken(active.remainingMs)} remaining. Double tap to change or cancel."
    }
    Column(
        modifier = Modifier
            .clip(EchoShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(EchoSpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(EchoShapes.Medium)
                .background(if (active != null) EchoColors.LimeFaint else EchoColors.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = null,
                tint = if (active != null) EchoColors.Lime else EchoColors.TextSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = if (active != null) SleepTimerFormat.format(active.remainingMs) else "SLEEP",
            style = EchoTypography.Readout.copy(color = if (active != null) EchoColors.Lime else EchoColors.TextMuted, fontSize = 10.sp),
        )
    }
}

@Composable
private fun CustomSleepTimerDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && SleepTimerPresets.isValidMinutes(minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom sleep timer") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(3) },
                label = { Text("Minutes (1 to ${SleepTimerPresets.MAX_MINUTES})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onStart(minutes!!) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onStart(minutes!!) }, enabled = valid) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
