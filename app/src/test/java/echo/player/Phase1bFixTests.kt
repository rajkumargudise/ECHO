package echo.player

import echo.player.domain.countLabel
import echo.player.domain.folderOf
import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.domain.model.Track
import echo.player.domain.resolveAgainst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1bFixTests {
    @Test
    fun mediaStoreUnknownMarkerFallsBackToUnknownArtist() {
        // Found on a real device: MediaStore returns the literal "<unknown>" for untagged files.
        assertEquals(Track.UNKNOWN_ARTIST, Track.cleanMetadata("<unknown>", Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_ARTIST, Track.cleanMetadata("<UNKNOWN>", Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_ARTIST, Track.cleanMetadata(null, Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_ALBUM, Track.cleanMetadata("   ", Track.UNKNOWN_ALBUM))
    }

    @Test
    fun realMetadataIsKeptAndTrimmed() {
        assertEquals("Make Me Cry", Track.cleanMetadata(" Make Me Cry ", "x"))
        assertEquals("A R Rahman", Track.cleanMetadata("A R Rahman", Track.UNKNOWN_ARTIST))
    }

    @Test
    fun folderIdentityStripsTrailingSlashAndHandlesRoot() {
        assertEquals("Music/English", folderOf("Music/English/"))
        assertEquals("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio", folderOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/"))
        assertEquals("Root", folderOf(""))
        assertEquals("Root", folderOf("/"))
    }

    @Test
    fun countLabelPluralizes() {
        assertEquals("1 track", countLabel(1, "track"))
        assertEquals("2 tracks", countLabel(2, "track"))
        assertEquals("0 songs", countLabel(0, "song"))
    }

    @Test
    fun playlistEntriesWithMissingMediaAreKeptInStoredOrder() {
        val playlist = Playlist(
            id = 1,
            name = "Mix",
            trackIds = listOf("a", "b", "c"),
            entries = listOf(
                PlaylistEntry("a", "Song A", "Artist", "Album"),
                PlaylistEntry("b", "Song B", "Artist", "Album"),
                PlaylistEntry("c", "Song C", "Artist", "Album"),
            ),
        )

        // No library tracks at all: every entry is unavailable but nothing is dropped or reordered.
        val resolved = playlist.resolveAgainst(emptyMap())

        assertEquals(listOf("a", "b", "c"), resolved.map { it.entry.trackId })
        assertEquals(listOf(0, 1, 2), resolved.map { it.position })
        assertTrue(resolved.all { !it.isAvailable })
        assertFalse(resolved.any { it.track != null })
    }
}
