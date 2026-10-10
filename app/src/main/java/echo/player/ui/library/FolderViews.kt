package echo.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.player.design.EchoColors
import echo.player.design.EchoShapes
import echo.player.design.EchoSpacing
import echo.player.design.EchoTypography
import echo.player.design.components.EchoGroupRow
import echo.player.design.components.EchoIconButton
import echo.player.domain.FolderGroup
import echo.player.domain.FolderPaths
import echo.player.domain.SectionedItems
import echo.player.domain.countLabel
import echo.player.domain.durationMs
import echo.player.domain.formatTrackDuration

/** One line of facts about a folder: where it sits, how many tracks, how long. Real library data only. */
fun folderFacts(folder: FolderGroup): String {
    val total = formatTrackDuration(folder.durationMs)
    return listOfNotNull(
        FolderPaths.parent(folder.name).takeIf { it.isNotEmpty() },
        countLabel(folder.tracks.size, "track"),
        total.takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}

/** The Folders tab: a list or tiles, switched with the two keys at the top. Opening one shows its songs. */
@Composable
fun FoldersPage(
    folders: SectionedItems<FolderGroup>,
    tiles: Boolean,
    onTilesChange: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = EchoSpacing.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(countLabel(folders.items.size, "folder").uppercase(), style = EchoTypography.Label.copy(color = EchoColors.TextMuted), modifier = Modifier.weight(1f))
            EchoIconButton(
                Icons.AutoMirrored.Filled.ViewList,
                "Show folders as a list",
                { onTilesChange(false) },
                tint = if (!tiles) EchoColors.Lime else EchoColors.TextSecondary,
            )
            EchoIconButton(
                Icons.Filled.GridView,
                "Show folders as tiles",
                { onTilesChange(true) },
                tint = if (tiles) EchoColors.Lime else EchoColors.TextSecondary,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (tiles) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(EchoSpacing.L),
                    horizontalArrangement = Arrangement.spacedBy(EchoSpacing.M),
                    verticalArrangement = Arrangement.spacedBy(EchoSpacing.M),
                ) {
                    items(folders.items, key = { "folder-tile-${it.name}" }) { folder ->
                        FolderTile(folder) { onOpen(folder.name) }
                    }
                }
            } else {
                IndexedLazyList(folders, itemKey = { "folder-${it.name}" }) { folder ->
                    EchoGroupRow(
                        title = FolderPaths.leaf(folder.name),
                        subtitle = folderFacts(folder),
                        onClick = { onOpen(folder.name) },
                    )
                }
            }
        }
    }
}

/** A folder as a compact tile: icon, name, and the track count and length. */
@Composable
private fun FolderTile(folder: FolderGroup, onClick: () -> Unit) {
    val total = formatTrackDuration(folder.durationMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .background(EchoColors.Charcoal, EchoShapes.Small)
            .border(1.dp, EchoColors.Separator, EchoShapes.Small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(EchoSpacing.M),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = EchoColors.Lime, modifier = Modifier.size(28.dp))
        Column {
            Text(FolderPaths.leaf(folder.name), style = EchoTypography.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val parent = FolderPaths.parent(folder.name)
            if (parent.isNotEmpty()) {
                Text(parent, style = EchoTypography.Caption.copy(color = EchoColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                countLabel(folder.tracks.size, "track") + if (total.isNotEmpty()) " · $total" else "",
                style = EchoTypography.Readout.copy(color = EchoColors.Lime),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
