package echo.player.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoIconButton
import echo.player.domain.FolderPaths
import echo.player.domain.countLabel
import echo.player.domain.formatTrackDuration
import echo.player.domain.folderOf
import echo.player.domain.model.Track
import echo.player.domain.resolveAgainst
import echo.player.ui.DetailTarget
import echo.player.ui.EchoViewModel

@Composable
fun DetailContent(
    detail: DetailTarget,
    viewModel: EchoViewModel,
    tracksById: Map<String, Track>,
    currentTrackId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onBack: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    @Composable
    fun trackList(tracks: List<Track>, emptyMessage: String) {
        if (tracks.isEmpty()) {
            EmptyState(emptyMessage)
        } else {
            val sorted = remember(tracks, viewModel.songSort) { viewModel.songSort.apply(tracks) }
            Column(modifier = Modifier.fillMaxSize()) {
                PlayAllBar(onPlayAll = { onPlayAll(sorted, false) }, onShuffle = { onPlayAll(sorted, true) })
                SortBar(viewModel.songSort) { viewModel.songSort = it }
                Box(modifier = Modifier.weight(1f)) {
                    PlainTrackList(
                        tracks = sorted,
                        currentTrackId = currentTrackId,
                        favoriteIds = viewModel.favorites,
                        onPlay = onPlay,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                }
            }
        }
    }

    when (detail) {
        is DetailTarget.ArtistTarget -> {
            val tracks = viewModel.tracks.filter { it.artist == detail.name }
            DetailScaffold(detail.name, countLabel(tracks.size, "track"), onBack) {
                trackList(tracks, "No songs by this artist are in your library.")
            }
        }
        is DetailTarget.AlbumTarget -> {
            val tracks = viewModel.tracks.filter { it.album == detail.name }
            DetailScaffold(detail.name, countLabel(tracks.size, "track"), onBack) {
                trackList(tracks, "No songs from this album are in your library.")
            }
        }
        is DetailTarget.FolderTarget -> {
            val tracks = viewModel.tracks.filter { folderOf(it.relativePath) == detail.name }
            val total = formatTrackDuration(tracks.sumOf { it.durationMs })
            DetailScaffold(
                FolderPaths.leaf(detail.name),
                FolderPaths.breadcrumb(detail.name) + " · " + countLabel(tracks.size, "track") + if (total.isNotEmpty()) " · $total" else "",
                onBack,
            ) {
                trackList(tracks, "No songs from this folder are in your library.")
            }
        }
        is DetailTarget.PlaylistTarget -> {
            val playlist = viewModel.playlists.firstOrNull { it.id == detail.id }
            if (playlist == null) {
                DetailScaffold("Playlist", "", onBack) { EmptyState("PLAYLIST NOT FOUND") }
            } else {
                val resolved = playlist.resolveAgainst(tracksById)
                val missing = resolved.count { !it.isAvailable }
                val subtitle = countLabel(resolved.size, "track") + if (missing > 0) " · $missing unavailable" else ""
                DetailScaffold(playlist.name, subtitle, onBack) {
                    if (resolved.isEmpty()) {
                        EmptyState("THIS PLAYLIST IS EMPTY\nTap + on any song to add it here.")
                    } else {
                        // Only available tracks are queued; unavailable entries stay in the playlist untouched.
                        val playable = resolved.mapNotNull { it.track }
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (playable.isNotEmpty()) PlayAllBar(onPlayAll = { onPlayAll(playable, false) }, onShuffle = { onPlayAll(playable, true) })
                            Box(modifier = Modifier.weight(1f)) {
                                PlaylistEntryList(
                                    entries = resolved,
                                    currentTrackId = currentTrackId,
                                    favoriteIds = viewModel.favorites,
                                    onPlay = { track -> onPlay(playable, playable.indexOfFirst { it.id == track.id }) },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onRemove = { trackId -> viewModel.removeTrackFromPlaylist(playlist.id, trackId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScaffold(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.Xs, vertical = EchoSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.S)) {
                Text(title, style = EchoTypography.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, style = EchoTypography.Readout.copy(color = EchoColors.TextMuted))
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}
