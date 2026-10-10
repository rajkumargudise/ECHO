package echo.player.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoGroupRow
import echo.player.companion.MascotExpression
import echo.player.design.components.EchoIconButton
import echo.player.design.components.EchoTabRow
import echo.player.design.components.EchoTrackRow
import echo.player.domain.AlphabetIndex
import echo.player.domain.FolderPaths
import echo.player.domain.LibraryScanState
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSort
import echo.player.domain.durationMs
import echo.player.domain.formatTrackDuration
import kotlinx.coroutines.delay
import echo.player.domain.countLabel
import echo.player.domain.groupedByAlbum
import echo.player.domain.groupedByArtist
import echo.player.domain.groupedByFolder
import echo.player.domain.model.Playlist
import echo.player.domain.model.Track
import echo.player.ui.DetailTarget
import echo.player.ui.EchoMiniPlayer
import echo.player.ui.EchoViewModel
import echo.player.ui.LibraryTab

/** The five library groupings of one list of tracks, each built on first use. */
private class LibrarySections(private val tracks: List<Track>) {
    val songs by lazy { AlphabetIndex.buildSections(tracks) { it.title } }
    val artists by lazy { AlphabetIndex.buildSections(tracks.groupedByArtist()) { it.name } }
    val albums by lazy { AlphabetIndex.buildSections(tracks.groupedByAlbum()) { it.name } }
    val folders by lazy { AlphabetIndex.buildSections(tracks.groupedByFolder()) { it.name } }
}

private const val NO_MUSIC = "NO MUSIC YET\nChoose a music folder with the folder button."

private const val NO_MATCHES = "NO MATCHES\nTry another song, artist, or album."

@Composable
fun LibraryScreen(
    viewModel: EchoViewModel,
    onRescan: () -> Unit,
    onPlaybackRequested: () -> Unit,
) {
    val playback by viewModel.playback.state.collectAsState()
    val scanState = viewModel.scanState
    var selectedTrackForPlaylist by viewModel::playlistTarget
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }

    val detail = viewModel.detail
    BackHandler(enabled = detail != null) { viewModel.detail = null }
    val compact = LocalConfiguration.current.screenHeightDp < 500

    val query = viewModel.searchQuery
    val filteredTracks = remember(viewModel.tracks, query) {
        viewModel.tracks.filter { Track.matchesQuery(it, query) }
    }
    // Each tab's sections are built the first time that tab is shown, then kept until the list changes, so a
    // search or a rescan no longer rebuilds all five of them up front.
    val sections = remember(filteredTracks) { LibrarySections(filteredTracks) }
    val favoriteSections = if (viewModel.selectedTab == LibraryTab.FAVORITES) {
        remember(filteredTracks, viewModel.favorites) {
            AlphabetIndex.buildSections(filteredTracks.filter { it.id in viewModel.favorites }) { it.title }
        }
    } else {
        null
    }
    val tracksById = remember(viewModel.tracks) { viewModel.tracks.associateBy { it.id } }

    // Swiping left or right moves between Songs, Artists, Albums, Folders, Favorites and Playlists. The tab row and the
    // pager stay in step: tapping a tab scrolls the pager, and swiping updates the selected tab.
    val pagerState = rememberPagerState(initialPage = viewModel.selectedTab.ordinal, pageCount = { LibraryTab.entries.size })
    LaunchedEffect(viewModel.selectedTab) {
        if (pagerState.currentPage != viewModel.selectedTab.ordinal) pagerState.animateScrollToPage(viewModel.selectedTab.ordinal)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { viewModel.selectedTab = LibraryTab.entries[it] }
    }

    // Tapping a song plays the list it was tapped in, starting there. Tapping the song that is already
    // loaded opens Now Playing instead of restarting it.
    val playFrom: (List<Track>, Int) -> Unit = { queue, index ->
        onPlaybackRequested()
        val tapped = queue.getOrNull(index)
        if (tapped != null && tapped.id == playback.currentTrackId && playback.error == null) {
            viewModel.openNowPlaying()
        } else {
            viewModel.playback.playQueue(queue, index)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black)) {
        if (detail == null) {
            if (compact) {
                // Short screens (landscape): title, search and actions share one row so the list gets the height.
                LibraryHeader(
                    trackCount = viewModel.tracks.size,
                    scanning = scanState == LibraryScanState.SCANNING,
                    playlistCount = viewModel.playlists.size,
                    onRescan = onRescan,
                    onOpenFolders = viewModel::openMusicFolders,
                    onOpenDiscover = viewModel::openDiscover,
                    onOpenSettings = viewModel::openSettings,
                    inlineSearch = { modifier ->
                        SearchField(value = query, onValueChange = { viewModel.searchQuery = it }, modifier = modifier)
                    },
                )
            } else {
                LibraryHeader(
                    trackCount = viewModel.tracks.size,
                    scanning = scanState == LibraryScanState.SCANNING,
                    playlistCount = viewModel.playlists.size,
                    onRescan = onRescan,
                    onOpenFolders = viewModel::openMusicFolders,
                    onOpenDiscover = viewModel::openDiscover,
                    onOpenSettings = viewModel::openSettings,
                )
                SearchField(value = query, onValueChange = { viewModel.searchQuery = it })
                Spacer(Modifier.height(EchoSpacing.S))
            }
            EchoTabRow(
                labels = LibraryTab.entries.map { it.label },
                selectedIndex = viewModel.selectedTab.ordinal,
                onSelect = { viewModel.selectedTab = LibraryTab.entries[it] },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (detail != null) {
                DetailContent(
                    detail = detail,
                    viewModel = viewModel,
                    tracksById = tracksById,
                    currentTrackId = playback.currentTrackId,
                    onPlay = playFrom,
                    onPlayAll = { list, shuffle ->
                        onPlaybackRequested()
                        viewModel.playAll(list, shuffle)
                    },
                    onBack = { viewModel.detail = null },
                    onAddToPlaylist = { selectedTrackForPlaylist = it },
                )
            } else {
                if (scanState == LibraryScanState.SCANNING) {
                    // Never claim "no music" (or show 0 tracks as a result) until the first scan has really finished.
                    ScanningState()
                } else HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page -> when (LibraryTab.entries[page]) {
                    LibraryTab.SONGS -> Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredTracks.isNotEmpty()) SortBar(viewModel.songSort) { viewModel.songSort = it }
                        Box(modifier = Modifier.weight(1f)) {
                            if (viewModel.songSort == TrackSort.A_Z || filteredTracks.isEmpty()) {
                                TrackSections(
                                    sections = sections.songs,
                                    emptyMessage = if (scanState == LibraryScanState.READY_EMPTY && query.isBlank()) NO_MUSIC else NO_MATCHES,
                                    emptyMascot = if (scanState == LibraryScanState.READY_EMPTY && query.isBlank()) MascotExpression.SLEEPING else MascotExpression.NEUTRAL,
                                    currentTrackId = playback.currentTrackId,
                                    favoriteIds = viewModel.favorites,
                                    onPlay = playFrom,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToPlaylist = { selectedTrackForPlaylist = it },
                                )
                            } else {
                                // Another order has no alphabet to jump through, so it is a plain list.
                                val ordered = remember(filteredTracks, viewModel.songSort) { viewModel.songSort.apply(filteredTracks) }
                                PlainTrackList(
                                    tracks = ordered,
                                    currentTrackId = playback.currentTrackId,
                                    favoriteIds = viewModel.favorites,
                                    onPlay = playFrom,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onAddToPlaylist = { selectedTrackForPlaylist = it },
                                )
                            }
                        }
                    }
                    LibraryTab.FAVORITES -> TrackSections(
                        sections = favoriteSections ?: sections.songs,
                        emptyMessage = "YOUR FAVORITES\nSave songs you love here.",
                        emptyMascot = MascotExpression.WAITING,
                        currentTrackId = playback.currentTrackId,
                        favoriteIds = viewModel.favorites,
                        onPlay = playFrom,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlaylist = { selectedTrackForPlaylist = it },
                    )
                    LibraryTab.ARTISTS -> if (sections.artists.items.isEmpty()) {
                        EmptyState(NO_MATCHES)
                    } else {
                        IndexedLazyList(sections.artists, itemKey = { "artist-${it.name}" }) { artist ->
                            EchoGroupRow(
                                title = artist.name,
                                subtitle = countLabel(artist.tracks.size, "track"),
                                onClick = { viewModel.detail = DetailTarget.ArtistTarget(artist.name) },
                            )
                        }
                    }
                    LibraryTab.ALBUMS -> if (sections.albums.items.isEmpty()) {
                        EmptyState(NO_MATCHES)
                    } else {
                        IndexedLazyList(sections.albums, itemKey = { "album-${it.name}" }) { album ->
                            EchoGroupRow(
                                title = album.name,
                                subtitle = "${album.artist} · ${countLabel(album.tracks.size, "track")}",
                                onClick = { viewModel.detail = DetailTarget.AlbumTarget(album.name) },
                            )
                        }
                    }
                    LibraryTab.FOLDERS -> if (sections.folders.items.isEmpty()) {
                        EmptyState(NO_MATCHES)
                    } else {
                        FoldersPage(
                            folders = sections.folders,
                            tiles = viewModel.folderTiles,
                            onTilesChange = viewModel::updateFolderTiles,
                            onOpen = { viewModel.detail = DetailTarget.FolderTarget(it) },
                        )
                    }
                    LibraryTab.PLAYLISTS -> Column(modifier = Modifier.fillMaxSize()) {
                        EchoButton(
                            text = "New playlist",
                            onClick = { showCreatePlaylist = true },
                            modifier = Modifier.fillMaxWidth().padding(EchoSpacing.L),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (viewModel.playlists.isEmpty()) {
                                EmptyState("NO PLAYLISTS\nCreate your first local playlist.")
                            } else {
                                PlaylistList(
                                    playlists = viewModel.playlists,
                                    tracksById = tracksById,
                                    onOpen = { viewModel.detail = DetailTarget.PlaylistTarget(it.id) },
                                    onDelete = { viewModel.deletePlaylist(it.id) },
                                    onRename = { renameTarget = it },
                                )
                            }
                        }
                    }
                }
                }
            }
        }

        if (viewModel.isLoading && scanState != LibraryScanState.SCANNING) {
            Text(
                text = "Scanning your library…",
                style = EchoTypography.Caption,
                modifier = Modifier.fillMaxWidth().padding(EchoSpacing.S),
            )
        }

        if (playback.currentTrackId != null) {
            EchoMiniPlayer(state = playback, controls = viewModel.playback, onOpen = viewModel::openNowPlaying)
        }
    }

    if (showCreatePlaylist) {
        PlaylistNameDialog(
            title = "New playlist",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = {
                viewModel.createPlaylist(it)
                showCreatePlaylist = false
            },
            onDismiss = { showCreatePlaylist = false },
        )
    }
    renameTarget?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            confirmLabel = "Rename",
            onConfirm = {
                viewModel.renamePlaylist(playlist.id, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun LibraryHeader(
    trackCount: Int,
    scanning: Boolean,
    playlistCount: Int,
    onRescan: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    inlineSearch: (@Composable (Modifier) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = EchoSpacing.L, end = EchoSpacing.Xs, top = EchoSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = if (inlineSearch == null) Modifier.weight(1f) else Modifier) {
            Text("ECHO", style = EchoTypography.Title.copy(color = EchoColors.Lime, letterSpacing = 4.sp))
            Text(
                text = if (scanning) "Scanning your library…" else "${countLabel(trackCount, "track")} · ${countLabel(playlistCount, "playlist")}",
                style = EchoTypography.Readout.copy(color = EchoColors.TextMuted),
            )
        }
        inlineSearch?.invoke(Modifier.weight(1f).padding(horizontal = EchoSpacing.M))
        EchoIconButton(Icons.Filled.Explore, "Discover online music", onOpenDiscover)
        EchoIconButton(Icons.Filled.FolderOpen, "Music folders", onOpenFolders)
        EchoIconButton(Icons.Filled.Settings, "Settings", onOpenSettings)
        EchoIconButton(Icons.Filled.Refresh, "Rescan library", onRescan)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = EchoSpacing.L),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = EchoTypography.Body,
        cursorBrush = SolidColor(EchoColors.Lime),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search music" },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(EchoColors.Charcoal, EchoShapes.Medium)
                    .border(1.dp, EchoColors.Separator, EchoShapes.Medium)
                    .padding(start = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = EchoColors.TextMuted, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.S)) {
                    if (value.isEmpty()) Text("Search music", style = EchoTypography.Body.copy(color = EchoColors.TextMuted))
                    inner()
                }
                if (value.isNotEmpty()) {
                    EchoIconButton(Icons.Filled.Close, "Clear search", { onValueChange("") }, size = 44.dp, tint = EchoColors.TextSecondary)
                }
            }
        },
    )
}
