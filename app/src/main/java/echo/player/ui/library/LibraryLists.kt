package echo.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoIconButton
import echo.player.design.mascot.EchoMascotStatic
import echo.player.companion.MascotExpression
import echo.player.design.components.EchoTrackRow
import echo.player.domain.ResolvedPlaylistEntry
import echo.player.domain.SectionedItems
import echo.player.domain.TrackSort
import echo.player.domain.countLabel
import echo.player.domain.formatTrackDuration
import echo.player.domain.model.Playlist
import echo.player.domain.model.Track
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder

/** Shown until the first library scan has finished. It is a progress state, not a result. */
@Composable
fun ScanningState() {
    EmptyState(message = "SCANNING YOUR LIBRARY\nThis only takes a moment.", mascot = MascotExpression.WAITING)
}

/** An empty list. ECHO keeps the person company, in a mood that fits (asleep, waiting, or just neutral). */
@Composable
fun EmptyState(message: String, mascot: MascotExpression = MascotExpression.NEUTRAL) {
    Box(modifier = Modifier.fillMaxSize().padding(EchoSpacing.Xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(EchoSpacing.L)) {
            EchoMascotStatic(expression = mascot, size = 84.dp)
            Text(
                text = message,
                style = EchoTypography.Heading.copy(color = EchoColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
    }
}

/** An alphabetical song list with the fast index; tapping a row plays the list from that song. */
@Composable
fun TrackSections(
    sections: SectionedItems<Track>,
    emptyMessage: String,
    emptyMascot: MascotExpression = MascotExpression.NEUTRAL,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    if (sections.items.isEmpty()) {
        EmptyState(emptyMessage, emptyMascot)
        return
    }
    val ordered = sections.items
    val indexById = remember(ordered) { ordered.withIndex().associate { it.value.id to it.index } }
    IndexedLazyList(sections, itemKey = { it.id }) { track ->
        EchoTrackRow(
            title = track.title,
            subtitle = "${track.artist} · ${track.album}",
            durationText = formatTrackDuration(track.durationMs),
            isCurrent = track.id == currentTrackId,
            isFavorite = track.id in favoriteIds,
            onClick = { onPlay(ordered, indexById[track.id] ?: 0) },
            onToggleFavorite = { onToggleFavorite(track) },
            onAddToPlaylist = { onAddToPlaylist(track) },
        )
    }
}

/** A song list for the drill-down screens (artist, album, folder), in the order given. */
@Composable
fun PlainTrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            EchoTrackRow(
                title = track.title,
                subtitle = "${track.artist} · ${track.album}",
                durationText = formatTrackDuration(track.durationMs),
                isCurrent = track.id == currentTrackId,
                isFavorite = track.id in favoriteIds,
                onClick = { onPlay(tracks, index) },
                onToggleFavorite = { onToggleFavorite(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
            )
        }
    }
}

@Composable
fun PlaylistList(
    playlists: List<Playlist>,
    tracksById: Map<String, Track>,
    onOpen: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { _, playlist ->
            val missing = playlist.entries.count { !tracksById.containsKey(it.trackId) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(EchoColors.Separator, Offset(16.dp.toPx(), size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                    }
                    .clickable(role = Role.Button) { onOpen(playlist) }
                    .padding(start = EchoSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = EchoSpacing.S)) {
                    Text(playlist.name, style = EchoTypography.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = countLabel(playlist.entries.size, "track") + if (missing > 0) " · $missing unavailable" else "",
                        style = EchoTypography.Caption,
                    )
                }
                OverflowMenu("More options for ${playlist.name}", listOf("Rename" to { onRename(playlist) }, "Delete playlist" to { onDelete(playlist) }))
            }
        }
    }
}

/**
 * The songs of one playlist in stored order. A song whose file is gone stays in the list, marked unavailable,
 * so nothing the user built is destroyed; it can be removed explicitly.
 */
@Composable
fun PlaylistEntryList(
    entries: List<ResolvedPlaylistEntry>,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(entries, key = { _, item -> item.entry.trackId }) { _, item ->
            val track = item.track
            val isCurrent = track != null && track.id == currentTrackId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(if (isCurrent) EchoColors.LimeFaint else EchoColors.Black)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(EchoColors.Separator, Offset(16.dp.toPx(), size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                    }
                    .then(if (track != null) Modifier.clickable(role = Role.Button) { onPlay(track) } else Modifier)
                    .padding(start = EchoSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${item.position + 1}",
                    style = EchoTypography.Readout.copy(color = EchoColors.TextMuted),
                    modifier = Modifier.padding(end = EchoSpacing.M),
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = EchoSpacing.S)) {
                    Text(
                        text = track?.title ?: item.entry.title,
                        style = EchoTypography.Body.copy(color = if (track == null) EchoColors.TextMuted else if (isCurrent) EchoColors.Lime else EchoColors.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (track != null) "${track.artist} · ${track.album}" else "Unavailable — file not found on this device",
                        style = EchoTypography.Caption.copy(color = if (track != null) EchoColors.TextSecondary else EchoColors.Danger),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track != null) {
                    val favorite = track.id in favoriteIds
                    EchoIconButton(
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        onClick = { onToggleFavorite(track) },
                        tint = if (favorite) EchoColors.Lime else EchoColors.TextSecondary,
                    )
                }
                OverflowMenu("More options for ${item.entry.title}", listOf("Remove from playlist" to { onRemove(item.entry.trackId) }))
            }
        }
    }
}

/** A three-dot menu for secondary actions only (rename, delete, remove). Primary actions stay visible on the row. */
@Composable
fun OverflowMenu(contentDescription: String, actions: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        EchoIconButton(Icons.Filled.MoreVert, contentDescription, { open = true }, tint = EchoColors.TextSecondary)
        if (open) {
            DropdownMenu(expanded = true, onDismissRequest = { open = false }) {
                actions.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
                }
            }
        }
    }
}

/** "SORT  A–Z" with a menu of the ways to order the songs below it. */
@Composable
fun SortBar(current: TrackSort, onSelect: (TrackSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(end = EchoSpacing.S), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(
                modifier = Modifier.heightIn(min = 40.dp).clickable(role = Role.Button) { open = true }.padding(horizontal = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SORT  ", style = EchoTypography.Label.copy(color = EchoColors.TextMuted))
                Text(current.label.uppercase() + " ▾", style = EchoTypography.Label.copy(color = EchoColors.Lime))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                TrackSort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onSelect(option) })
                }
            }
        }
    }
}

/** Play all / Shuffle for a list of songs. */
@Composable
fun PlayAllBar(onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(EchoSpacing.S),
    ) {
        echo.player.design.components.EchoButton("Play all", onPlayAll, Modifier.weight(1f))
        echo.player.design.components.EchoButton("Shuffle", onShuffle, Modifier.weight(1f), style = echo.player.design.components.EchoButtonStyle.Secondary)
    }
}
