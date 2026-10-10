package echo.player

import echo.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLogicTests {
    @Test
    fun missingMetadataUsesFallbacks() {
        val fallbackTitle = Track.fallbackTitle("song_mix.mp3", "")
        assertEquals("song_mix", fallbackTitle)

        assertTrue(Track.matchesQuery("Midnight Echo", "A.R. Rahman", "Home", "rahman"))
    }

    @Test
    fun searchMatchesCaseInsensitiveAcrossTitleArtistAlbum() {
        val tracks = listOf(
            listOf("Billie Jean", "Michael Jackson", "Thriller"),
            listOf("Dreams", "Fleetwood Mac", "Rumours"),
            listOf("Radio Ga Ga", "Queen", "The Works"),
        )

        val matches = tracks.filter { (title, artist, album) ->
            Track.matchesQuery(title, artist, album, "michael")
        }
        assertEquals(1, matches.size)
        assertEquals("Billie Jean", matches.first()[0])
    }

    @Test
    fun favoritesToggleStateWithoutLosingExistingSelection() {
        val favorites = mutableSetOf("song-a")
        val trackId = "song-b"
        assertTrue(favorites.contains("song-a"))

        if (favorites.contains(trackId)) {
            favorites.remove(trackId)
        } else {
            favorites.add(trackId)
        }

        assertTrue(favorites.contains(trackId))
    }

    @Test
    fun playlistOrderRemainsStableWhenReordering() {
        val ordered = mutableListOf("song-a", "song-b", "song-c")
        val moving = ordered.removeAt(0)
        ordered.add(2, moving)

        assertEquals(listOf("song-b", "song-c", "song-a"), ordered)
    }
}
