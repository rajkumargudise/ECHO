package echo.player.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.player.companion.MascotExpression
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoButton
import echo.player.design.components.EchoButtonStyle
import echo.player.design.components.EchoIconButton
import echo.player.domain.model.EchoLanguage
import echo.player.domain.model.LanguageFacets
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.RemoteTrack
import echo.player.online.DiscoverState
import echo.player.online.PlayOrigin
import echo.player.online.RecentPlay
import echo.player.online.TrackActions
import echo.player.ui.EchoMiniPlayer
import echo.player.ui.EchoViewModel
import echo.player.ui.library.EmptyState

/**
 * Online discovery: an explicit-submit search, real results from the configured source, and what was searched and
 * played recently. It shows only what the source actually returned; when no source is set up it says so.
 */
@Composable
fun DiscoverScreen(viewModel: EchoViewModel, onPlaybackRequested: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.discover.state.collectAsState()
    val playback by viewModel.playback.state.collectAsState()

    // The service records a play a moment after it starts; refresh the list when the playing item changes.
    LaunchedEffect(playback.currentTrackId) { viewModel.loadHistory() }

    Column(modifier = Modifier.fillMaxSize().background(EchoColors.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = EchoSpacing.Xs, end = EchoSpacing.L, top = EchoSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(modifier = Modifier.padding(start = EchoSpacing.S)) {
                Text("DISCOVER", style = EchoTypography.Title.copy(color = EchoColors.Lime, letterSpacing = 4.sp))
                val caption = when (val s = state) {
                    is DiscoverState.NoSource -> "No online source set up"
                    is DiscoverState.Success -> "Online · ${s.sourceName}"
                    else -> "Online music"
                }
                Text(caption, style = EchoTypography.Readout.copy(color = EchoColors.TextMuted))
            }
        }
        Spacer(Modifier.height(EchoSpacing.S))
        DiscoverSearchField(
            value = viewModel.discoverQuery,
            enabled = state !is DiscoverState.NoSource,
            onValueChange = { viewModel.discoverQuery = it },
            onSubmit = viewModel::submitDiscoverSearch,
        )
        Spacer(Modifier.height(EchoSpacing.S))

        viewModel.onlineMessage?.let { message ->
            Text(
                text = message,
                style = EchoTypography.Caption.copy(color = EchoColors.Warning),
                modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                DiscoverState.NoSource -> EmptyState(
                    "ONLINE MUSIC ISN'T SET UP\nThis build has no online music source configured, so nothing can be searched. Your local library works as usual.",
                    MascotExpression.SLEEPING,
                )
                DiscoverState.Idle -> IdleContent(
                    recentSearches = viewModel.recentSearches,
                    recentPlays = viewModel.recentPlays,
                    onRunSearch = viewModel::runRecentSearch,
                    onRemoveSearch = viewModel::removeRecentSearch,
                    onClearSearches = viewModel::clearRecentSearches,
                    onClearPlays = viewModel::clearRecentPlays,
                    onReplay = {
                        onPlaybackRequested()
                        viewModel.replay(it)
                    },
                )
                is DiscoverState.Loading -> EmptyState("SEARCHING\n“${s.query}”", MascotExpression.WAITING)
                is DiscoverState.Empty -> EmptyState("NO RESULTS\nNothing on ${s.sourceName} matched “${s.query}”.", MascotExpression.NEUTRAL)
                is DiscoverState.Offline -> RetryState(
                    "YOU'RE OFFLINE\nOnline search needs a connection. Your local library is unaffected.",
                    viewModel::submitDiscoverSearch,
                )
                is DiscoverState.Failed -> RetryState("SEARCH FAILED\n${s.error.userMessage()}", viewModel::submitDiscoverSearch)
                is DiscoverState.Success -> {
                    val shown = LanguageFacets.filter(s.tracks, viewModel.languageFilter)
                    Results(
                        allTracks = s.tracks,
                        tracks = shown,
                        currentMediaId = playback.currentTrackId,
                        viewModel = viewModel,
                        onPlay = { index ->
                            onPlaybackRequested()
                            val tapped = shown[index]
                            if (PlayableTrack.Remote(tapped).mediaId == playback.currentTrackId && playback.error == null) {
                                viewModel.openNowPlaying()
                            } else {
                                viewModel.playOnline(shown, index)
                            }
                        },
                    )
                }
            }
        }

        if (playback.currentTrackId != null) {
            EchoMiniPlayer(state = playback, controls = viewModel.playback, onOpen = viewModel::openNowPlaying)
        }
    }
}

@Composable
private fun RetryState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        EmptyState(message, MascotExpression.WAITING)
        EchoButton(
            text = "Try again",
            onClick = onRetry,
            style = EchoButtonStyle.Secondary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = EchoSpacing.Xxl),
        )
    }
}

@Composable
private fun Results(allTracks: List<RemoteTrack>, tracks: List<RemoteTrack>, currentMediaId: String?, viewModel: EchoViewModel, onPlay: (Int) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Language chips come only from languages the provider actually reported for these results.
        val facets = LanguageFacets.of(allTracks)
        if (facets.isNotEmpty()) {
            item(key = "language-chips") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
                    horizontalArrangement = Arrangement.spacedBy(EchoSpacing.S),
                ) {
                    LanguageChip("All", viewModel.languageFilter == null) { viewModel.languageFilter = null }
                    facets.forEach { LanguageChip(it.label, viewModel.languageFilter == it) { _ -> viewModel.languageFilter = it } }
                }
            }
        }
        items(tracks.size, key = { tracks[it].key }) { index ->
            val track = tracks[index]
            // What is offered follows the source's own capabilities for this track.
            val actions = TrackActions.of(track, viewModel.capabilitiesOf(track))
            val isCurrent = echo.player.domain.model.PlayableTrack.Remote(track).mediaId == currentMediaId
            RemoteRow(track = track, isCurrent = isCurrent, playable = actions.showPlay, showAttribution = actions.showAttribution, onClick = { onPlay(index) })
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: (Unit) -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(if (selected) EchoColors.Lime else EchoColors.Graphite, EchoShapes.Large)
            .clickable(role = Role.Button) { onClick(Unit) }
            .padding(horizontal = EchoSpacing.L),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = EchoTypography.Label.copy(color = if (selected) EchoColors.OnLime else EchoColors.TextPrimary))
    }
}

@Composable
private fun RemoteRow(track: RemoteTrack, isCurrent: Boolean, playable: Boolean, showAttribution: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (isCurrent) Modifier.background(EchoColors.LimeFaint) else Modifier)
            .clickable(enabled = playable, role = Role.Button, onClick = onClick)
            .padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
    ) {
        Text(
            track.title,
            style = if (isCurrent) EchoTypography.Body.copy(color = EchoColors.Lime) else EchoTypography.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(track.artist, track.album.takeIf { it.isNotBlank() }, track.language.takeIf { it != EchoLanguage.UNKNOWN }?.label, track.sourceDisplayName.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString(" · "),
            style = EchoTypography.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showAttribution) {
            Text(track.attributionText.orEmpty(), style = EchoTypography.Caption.copy(color = EchoColors.TextMuted, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!playable) Text("Not available for streaming", style = EchoTypography.Caption.copy(color = EchoColors.Warning))
    }
}

@Composable
private fun IdleContent(
    recentSearches: List<String>,
    recentPlays: List<RecentPlay>,
    onRunSearch: (String) -> Unit,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    onClearPlays: () -> Unit,
    onReplay: (RecentPlay) -> Unit,
) {
    if (recentSearches.isEmpty() && recentPlays.isEmpty()) {
        EmptyState("SEARCH ONLINE MUSIC\nType a song, artist or album and press search.", MascotExpression.WAITING)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (recentSearches.isNotEmpty()) {
            item(key = "searches-header") { SectionHeader("RECENT SEARCHES", "Clear all", onClearSearches) }
            items(recentSearches, key = { "s-$it" }) { query ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onRunSearch(query) }.padding(start = EchoSpacing.L),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = EchoColors.TextMuted, modifier = Modifier.size(18.dp))
                    Text(query, style = EchoTypography.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = EchoSpacing.M))
                    EchoIconButton(Icons.Filled.Close, "Remove $query from recent searches", { onRemoveSearch(query) }, tint = EchoColors.TextMuted)
                }
            }
        }
        if (recentPlays.isNotEmpty()) {
            item(key = "plays-header") { SectionHeader("RECENTLY PLAYED", "Clear", onClearPlays) }
            items(recentPlays, key = { "p-${it.sourceId}:${it.sourceTrackId}" }) { play ->
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onReplay(play) }.padding(horizontal = EchoSpacing.L, vertical = EchoSpacing.S),
                ) {
                    Text(play.title.ifBlank { "Unknown track" }, style = EchoTypography.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val origin = if (play.origin == PlayOrigin.LOCAL) "LOCAL" else "ONLINE"
                    Text("${play.artist} · $origin", style = EchoTypography.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = EchoSpacing.L, end = EchoSpacing.S, top = EchoSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = EchoTypography.Label, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = EchoSpacing.M),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = EchoTypography.Label.copy(color = EchoColors.Lime))
        }
    }
}

/** The search box submits only on the keyboard's search key or the search button: no request per keystroke. */
@Composable
private fun DiscoverSearchField(value: String, enabled: Boolean, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = EchoTypography.Body,
        cursorBrush = SolidColor(EchoColors.Lime),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EchoSpacing.L)
            .semantics { contentDescription = "Search online music" },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(EchoColors.Charcoal, EchoShapes.Medium)
                    .border(1.dp, EchoColors.Separator, EchoShapes.Medium)
                    .padding(start = EchoSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).padding(end = EchoSpacing.S)) {
                    if (value.isEmpty()) Text("Search songs, artists, albums", style = EchoTypography.Body.copy(color = EchoColors.TextMuted))
                    inner()
                }
                if (value.isNotEmpty()) EchoIconButton(Icons.Filled.Close, "Clear search", { onValueChange("") }, size = 44.dp, tint = EchoColors.TextSecondary)
                EchoIconButton(Icons.Filled.Search, "Search", onSubmit, enabled = enabled && value.isNotBlank(), size = 48.dp, tint = EchoColors.Lime)
            }
        },
    )
}
