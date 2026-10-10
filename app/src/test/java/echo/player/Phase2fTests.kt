package echo.player

import echo.player.domain.FolderPaths
import echo.player.domain.LocalSearch
import echo.player.domain.PlaylistLogic
import echo.player.domain.TrackSort
import echo.player.domain.FolderGroup
import echo.player.domain.durationMs
import echo.player.domain.formatTrackDuration
import echo.player.domain.groupedByFolder
import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import echo.player.domain.model.Track
import echo.player.domain.Screen
import echo.player.widget.EchoWidgetState
import echo.player.widget.TrackPresentationState
import echo.player.widget.WidgetLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A Track without Android: the JVM test Uri is a stub (Uri.EMPTY is null), and none of this logic reads the uri, so the
 * object is allocated directly and only the fields the logic uses are set.
 */
private fun t(
    id: String, title: String, artist: String = "A", album: String = "Al", durationMs: Long = 60_000, added: Long = 0, path: String = "Music/", no: Int? = null,
): Track {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    val track = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Track::class.java) as Track
    fun set(name: String, value: Any?) = Track::class.java.getDeclaredField(name).apply { isAccessible = true }.set(track, value)
    set("id", id); set("title", title); set("artist", artist); set("album", album); set("albumArtist", artist)
    set("durationMs", durationMs); set("dateAdded", added); set("relativePath", path); set("trackNumber", no)
    return track
}

class TrackSortTests {
    private val tracks = listOf(
        t("1", "banana", "Zed", "Q", 100_000, added = 5),
        t("2", "Apple", "Amy", "R", 300_000, added = 9),
        t("3", "cherry", "Bob", "P", 200_000, added = 1),
    )

    private fun ids(sort: TrackSort) = sort.apply(tracks).map { it.id }

    @Test
    fun everyOrderUsesRealFieldsAndIgnoresCase() {
        assertEquals(listOf("2", "1", "3"), ids(TrackSort.A_Z))
        assertEquals(listOf("3", "1", "2"), ids(TrackSort.Z_A))
        assertEquals(listOf("2", "1", "3"), ids(TrackSort.RECENT))
        assertEquals(listOf("2", "3", "1"), ids(TrackSort.DURATION))
        assertEquals(listOf("2", "3", "1"), ids(TrackSort.ARTIST))
        assertEquals(listOf("3", "1", "2"), ids(TrackSort.ALBUM))
    }

    @Test
    fun sortingNeverLosesOrDuplicatesATrack() {
        for (sort in TrackSort.entries) assertEquals(tracks.map { it.id }.toSet(), sort.apply(tracks).map { it.id }.toSet())
        assertTrue(TrackSort.A_Z.apply(emptyList()).isEmpty())
    }

    @Test
    fun indicTitlesSortAsUnicodeWithoutCrashing() {
        val indic = listOf(t("a", "తెలుగు"), t("b", "हिन्दी"), t("c", "தமிழ்"), t("d", "English"))
        assertEquals(4, TrackSort.A_Z.apply(indic).size)
        assertEquals("d", TrackSort.A_Z.apply(indic).first().id)
    }
}

class FolderUxTests {
    @Test
    fun aFolderPathSplitsIntoNameParentAndBreadcrumb() {
        assertEquals("Telugu", FolderPaths.leaf("Music/Telugu"))
        assertEquals("Music", FolderPaths.parent("Music/Telugu"))
        assertEquals("Local Music › Music › Telugu", FolderPaths.breadcrumb("Music/Telugu"))
        assertEquals("Music", FolderPaths.leaf("Music"))
        assertEquals("", FolderPaths.parent("Music"))
        assertEquals("Root", FolderPaths.leaf("Root"))
    }

    @Test
    fun folderCountsAndDurationsComeFromTheTracks() {
        val groups = listOf(
            t("1", "a", path = "Music/Telugu/", durationMs = 120_000),
            t("2", "b", path = "Music/Telugu/", durationMs = 60_000),
            t("3", "c", path = "Download/", durationMs = 30_000),
        ).groupedByFolder()
        val telugu = groups.first { it.name == "Music/Telugu" }
        assertEquals(2, telugu.tracks.size)
        assertEquals(180_000L, telugu.durationMs)
        assertEquals(1, groups.first { it.name == "Download" }.tracks.size)
    }

    @Test
    fun durationsAreFormattedForRows() {
        assertEquals("3:05", formatTrackDuration(185_000))
        assertEquals("1:02:09", formatTrackDuration(3_729_000))
        assertEquals("", formatTrackDuration(0))
    }
}

class LocalSearchTests {
    private val telugu = t("1", "సమజవరగమన", "సిద్ శ్రీరామ్", "అల వైకుంఠపురములో", path = "Music/Telugu/")
    private val hindi = t("2", "तुम ही हो", "अरिजीत सिंह", "आशिकी 2", path = "Music/Hindi/")
    private val tamil = t("3", "என்னவளே", "ஏ.ஆர். ரஹ்மான்", "காதலன்")

    @Test
    fun indicScriptsMatchTitleArtistAndAlbum() {
        assertTrue(LocalSearch.matches(telugu, "సమజ"))
        assertTrue(LocalSearch.matches(telugu, "శ్రీరామ్"))
        assertTrue(LocalSearch.matches(hindi, "अरिजीत"))
        assertTrue(LocalSearch.matches(hindi, "आशिकी"))
        assertTrue(LocalSearch.matches(tamil, "என்னவளே"))
        assertFalse(LocalSearch.matches(hindi, "సమజ"))
    }

    @Test
    fun composedAndDecomposedFormsOfTheSameTextMatch() {
        val composed = t("4", "café")
        assertTrue(LocalSearch.matches(composed, "café"))
    }

    @Test
    fun theFolderPathIsSearchableAndBlankMatchesAll() {
        assertTrue(LocalSearch.matches(telugu, "telugu"))
        assertTrue(LocalSearch.matches(telugu, "   "))
        assertTrue(LocalSearch.matches(t("5", "Hello", "Me"), "HELLO"))
        assertFalse(LocalSearch.matches(t("5", "Hello", "Me"), "zzz"))
    }

    @Test
    fun existingTrackMatchingStillWorks() {
        assertTrue(Track.matchesQuery(t("6", "Midnight Echo", "A.R. Rahman", "Home"), "rahman"))
    }
}

class PlaylistFlowTests {
    private fun playlist(id: Long, name: String, vararg trackIds: String) =
        Playlist(id, name, trackIds.toList(), entries = trackIds.map { PlaylistEntry(it, "T$it", "A", "Al") })

    @Test
    fun theChooserListsEveryPlaylistAndMarksTheOnesAlreadyHoldingTheSong() {
        val choices = PlaylistLogic.choices(listOf(playlist(1, "Telugu", "x", "y"), playlist(2, "Night", "z")), "y")
        assertEquals(listOf("Telugu", "Night"), choices.map { it.name })
        assertEquals(listOf(2, 1), choices.map { it.trackCount })
        assertEquals(listOf(true, false), choices.map { it.alreadyHasTrack })
    }

    @Test
    fun withNoPlaylistsTheChooserIsEmptyAndCreateIsTheWayForward() {
        assertTrue(PlaylistLogic.choices(emptyList(), "x").isEmpty())
    }

    @Test
    fun aSongIsAddedOnceAndAppendedAtTheEnd() {
        assertTrue(PlaylistLogic.shouldAdd(listOf("a", "b"), "c"))
        assertFalse(PlaylistLogic.shouldAdd(listOf("a", "b"), "b"))
        assertEquals(0, PlaylistLogic.nextPosition(emptyList()))
        assertEquals(3, PlaylistLogic.nextPosition(listOf(0, 1, 2)))
    }

    @Test
    fun namesAreCleanedAndFeedbackNamesThePlaylist() {
        assertEquals("Night Drive", PlaylistLogic.cleanName("  Night    Drive "))
        assertEquals("", PlaylistLogic.cleanName("   "))
        assertEquals("Added to Telugu Favorites", PlaylistLogic.addedMessage("Telugu Favorites"))
        assertEquals("Already in Telugu Favorites", PlaylistLogic.alreadyInMessage("Telugu Favorites"))
        assertTrue(PlaylistLogic.createdMessage("Night").contains("Night"))
    }
}

class WidgetMetadataTests {
    private fun playing(title: String, artist: String) = EchoWidgetState(TrackPresentationState(true, title, artist, true, true, true))

    @Test
    fun titleAndArtistAreShownWhenPresent() {
        val s = playing("Samajavaragamana", "Sid Sriram")
        assertEquals("Samajavaragamana", s.titleText)
        assertEquals("Sid Sriram", s.subtitleText)
    }

    @Test
    fun placeholderArtistsAreNotShownAsNames() {
        for (raw in listOf("", "  ", "Unknown Artist", "unknown", "<unknown>", "null")) assertEquals(raw, "", playing("T", raw).subtitleText)
        assertEquals("", WidgetLabels.artist(null))
    }

    @Test
    fun aMissingTitleGetsADeliberateFallback() {
        assertEquals("Unknown track", playing("  ", "A").titleText)
        assertEquals("Unknown track", WidgetLabels.title(null))
    }

    @Test
    fun indicTitlesAndArtistsPassThroughUnchanged() {
        val s = playing("సమజవరగమన", "సిద్ శ్రీరామ్")
        assertEquals("సమజవరగమన", s.titleText)
        assertEquals("సిద్ శ్రీరామ్", s.subtitleText)
    }

    @Test
    fun aTrackChangeReplacesBothLinesAndNothingPlayingShowsNoStaleMetadata() {
        val a = playing("One", "A")
        val b = playing("Two", "B")
        assertEquals("Two", b.titleText)
        assertEquals("B", b.subtitleText)
        val idle = EchoWidgetState(TrackPresentationState.NOTHING)
        assertEquals("ECHO", idle.titleText)
        assertEquals("Nothing playing", idle.subtitleText)
        assertFalse(idle.titleText == a.titleText)
    }
}

class FmRemovalTests {
    private fun main(vararg parts: String) = File("src/main/${parts.joinToString("/")}")

    @Test
    fun noRadioScreenOrNavigationDestinationRemains() {
        assertFalse(Screen.entries.any { it.name.contains("RADIO", true) || it.name.contains("FM", true) })
        assertFalse(main("java", "echo", "player", "radio").exists())
        assertFalse(main("java", "echo", "player", "ui", "radio").exists())
    }

    @Test
    fun noSourceFileMentionsTheRadioFeature() {
        val offenders = main("java").walkTopDown().filter { it.extension == "kt" }.filter { f ->
            f.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }
                .any { Regex("Radio|\\bFm\\b|\\bFM\\b|Tuner", RegexOption.IGNORE_CASE).containsMatchIn(it) && !it.contains("echo_radio") }
        }.map { it.name }.toList()
        assertTrue("radio remnants: $offenders", offenders.isEmpty())
    }

    @Test
    fun theManifestHasNoFmAppQueriesAndNoRadioPermission() {
        val manifest = main("AndroidManifest.xml").readText()
        assertFalse(manifest.contains("<queries>"))
        assertFalse(Regex("fmradio|com.sec.android.app.fm|radio", RegexOption.IGNORE_CASE).containsMatchIn(manifest))
    }

    @Test
    fun thePlaylistActionIsADirectPlusNotAThreeDotMenu() {
        val row = main("java", "echo", "player", "design", "components", "EchoTrackRow.kt").readText()
        assertTrue(row.contains("Icons.Filled.Add"))
        assertFalse(row.contains("MoreVert"))
        assertFalse(row.contains("DropdownMenu"))
        // The chooser lives at app level (MainActivity) so the "+" works from the library and from Now Playing.
        val app = main("java", "echo", "player", "MainActivity.kt").readText()
        assertTrue(app.contains("PlaylistChooserDialog"))
        val library = main("java", "echo", "player", "ui", "library", "LibraryScreen.kt").readText()
        assertFalse(library.contains("PlaylistPickerDialog"))
    }
}
