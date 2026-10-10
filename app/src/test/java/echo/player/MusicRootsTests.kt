package echo.player

import echo.player.domain.AddRootResult
import echo.player.domain.MusicRoot
import echo.player.domain.MusicRootStatus
import echo.player.domain.MusicRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRootsTests {
    private val music = MusicRoots.DEFAULT_MUSIC
    private val roots = listOf(music)

    // region default root

    @Test
    fun defaultRootIsTheMusicFolderOnEveryStorageVolume() {
        assertEquals(MusicRoots.ANY_VOLUME, music.volumeId)
        assertEquals("Music", music.relativePath)
        assertTrue(music.isDefault)
        assertTrue(music.isAnyVolume)
        assertEquals("/Music", music.displayPath)
    }

    @Test
    fun defaultRootFindsMusicOnAnSdCardToo() {
        // Found on a real phone: most of the music lives in Music/ on the SD card (volume 3730-3530).
        assertTrue(MusicRoots.accepts(roots, "3730-3530", "Music/English/"))
        assertTrue(MusicRoots.accepts(roots, "3730-3530", "Music/Telugu Old Songs/"))
        assertFalse(MusicRoots.accepts(roots, "3730-3530", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/"))
        assertFalse(MusicRoots.accepts(roots, "3730-3530", "Download/"))
    }

    @Test
    fun aFolderPickedOnPrimaryStorageDoesNotMatchTheSameFolderOnTheSdCard() {
        val picked = MusicRoot("primary", "Music", "content://tree/music")
        assertTrue(MusicRoots.accepts(listOf(picked), "external_primary", "Music/Rock/"))
        assertFalse(MusicRoots.accepts(listOf(picked), "3730-3530", "Music/Rock/"))
    }

    @Test
    fun anyVolumeMarkerSurvivesVolumeNormalizing() {
        assertEquals(MusicRoots.ANY_VOLUME, MusicRoots.normalizeVolume("*"))
    }

    @Test
    fun defaultRootAcceptsMusicAndEverythingBelowIt() {
        assertTrue(MusicRoots.accepts(roots, "external_primary", "Music/"))
        assertTrue(MusicRoots.accepts(roots, "external_primary", "Music/Artist/"))
        assertTrue(MusicRoots.accepts(roots, "external_primary", "Music/Artist/Album/"))
        assertTrue(MusicRoots.accepts(roots, "external_primary", "music/artist/"))
    }

    @Test
    fun appAndMessengerAudioIsNotAcceptedUnlessItsFolderIsChosen() {
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Telegram/Telegram Audio/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Ringtones/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Notifications/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", ""))
    }

    @Test
    fun noRootsAcceptsNothing() {
        assertFalse(MusicRoots.accepts(emptyList(), "external_primary", "Music/"))
    }

    // endregion

    // region nested and boundary matching

    @Test
    fun matchingRespectsFolderBoundaries() {
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Music2/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", "MusicBackup/Songs/"))
        assertFalse(MusicRoots.accepts(roots, "external_primary", "Download/Music/"))
    }

    @Test
    fun nestedRootMatchesOnlyItsOwnBranch() {
        val rock = MusicRoot("primary", "Music/Rock", "content://tree/rock")
        assertTrue(MusicRoots.accepts(listOf(rock), "external_primary", "Music/Rock/"))
        assertTrue(MusicRoots.accepts(listOf(rock), "external_primary", "Music/Rock/Album/"))
        assertFalse(MusicRoots.accepts(listOf(rock), "external_primary", "Music/Jazz/"))
        assertFalse(MusicRoots.accepts(listOf(rock), "external_primary", "Music/"))
    }

    @Test
    fun parentAndChildRootsTogetherCountEachTrackOnce() {
        val both = listOf(music, MusicRoot("primary", "Music/Rock", "content://tree/rock"))
        assertTrue(MusicRoots.accepts(both, "external_primary", "Music/Rock/"))
        assertTrue(MusicRoots.accepts(both, "external_primary", "Music/Jazz/"))
    }

    @Test
    fun rootOnAnotherVolumeDoesNotMatchPrimaryStorage() {
        val sdCard = MusicRoot("1a2b-3c4d", "Music", "content://tree/sd")
        assertFalse(MusicRoots.accepts(listOf(sdCard), "external_primary", "Music/"))
        assertTrue(MusicRoots.accepts(listOf(sdCard), "1A2B-3C4D", "Music/Song/"))
    }

    @Test
    fun rootAtTheTopOfAVolumeAcceptsEverythingOnThatVolume() {
        val everything = MusicRoot("primary", "", "content://tree/all")
        assertTrue(MusicRoots.accepts(listOf(everything), "external_primary", "Download/Music/"))
    }

    // endregion

    // region add / remove / duplicates

    @Test
    fun addingAFolderAppendsIt() {
        val downloads = MusicRoot("primary", "Download/Music", "content://tree/dl")
        val result = MusicRoots.add(roots, downloads) as AddRootResult.Added
        assertEquals(listOf(music, downloads), result.roots)
    }

    @Test
    fun addingTheSameFolderTwiceIsADuplicateRegardlessOfCase() {
        val first = MusicRoots.add(emptyList(), MusicRoot("primary", "Download/Music", "content://tree/dl")) as AddRootResult.Added
        assertEquals(AddRootResult.Duplicate, MusicRoots.add(first.roots, MusicRoot("primary", "download/music", "content://tree/dl2")))
        assertEquals(AddRootResult.Duplicate, MusicRoots.add(roots, MusicRoots.DEFAULT_MUSIC))
    }

    @Test
    fun removingAFolderOnlyRemovesThatFolder() {
        val downloads = MusicRoot("primary", "Download/Music", "content://tree/dl")
        val remaining = MusicRoots.remove(listOf(music, downloads), downloads)
        assertEquals(listOf(music), remaining)
        assertEquals(emptyList<MusicRoot>(), MusicRoots.remove(remaining, music))
    }

    @Test
    fun removingAFolderStopsItsTracksBeingAccepted() {
        val downloads = MusicRoot("primary", "Download/Music", "content://tree/dl")
        val both = listOf(music, downloads)
        assertTrue(MusicRoots.accepts(both, "external_primary", "Download/Music/"))
        assertFalse(MusicRoots.accepts(MusicRoots.remove(both, downloads), "external_primary", "Download/Music/"))
    }

    // endregion

    // region status

    @Test
    fun defaultRootIsAlwaysAvailable() {
        assertEquals(MusicRootStatus.AVAILABLE, MusicRoots.statusOf(music, hasPersistedGrant = false, folderExists = false))
    }

    @Test
    fun pickedFolderWithoutItsGrantIsReportedRevoked() {
        val picked = MusicRoot("primary", "Download/Music", "content://tree/dl")
        assertEquals(MusicRootStatus.PERMISSION_REVOKED, MusicRoots.statusOf(picked, hasPersistedGrant = false, folderExists = true))
    }

    @Test
    fun pickedFolderThatNoLongerExistsIsReportedUnavailable() {
        val picked = MusicRoot("primary", "Download/Music", "content://tree/dl")
        assertEquals(MusicRootStatus.UNAVAILABLE, MusicRoots.statusOf(picked, hasPersistedGrant = true, folderExists = false))
    }

    @Test
    fun pickedFolderWithGrantAndExistenceIsAvailable() {
        val picked = MusicRoot("primary", "Download/Music", "content://tree/dl")
        assertEquals(MusicRootStatus.AVAILABLE, MusicRoots.statusOf(picked, hasPersistedGrant = true, folderExists = true))
    }

    // endregion

    // region parsing from Android

    @Test
    fun treeDocumentIdOnPrimaryStorageBecomesARoot() {
        val root = MusicRoots.fromTreeDocumentId("primary:Download/Music", "content://tree/x")!!
        assertEquals("primary", root.volumeId)
        assertEquals("Download/Music", root.relativePath)
        assertEquals("content://tree/x", root.treeUri)
    }

    @Test
    fun treeDocumentIdOnAnSdCardKeepsItsVolume() {
        val root = MusicRoots.fromTreeDocumentId("1A2B-3C4D:Songs/", "content://tree/sd")!!
        assertEquals("1a2b-3c4d", root.volumeId)
        assertEquals("Songs", root.relativePath)
    }

    @Test
    fun treeDocumentIdWithoutAVolumeIsRejected() {
        assertNull(MusicRoots.fromTreeDocumentId("downloads", null))
        assertNull(MusicRoots.fromTreeDocumentId(":Music", null))
    }

    @Test
    fun legacyFilePathsGiveVolumeAndFolderForAndroid9AndOlder() {
        assertEquals("primary" to "Music/Artist", MusicRoots.fromDataPath("/storage/emulated/0/Music/Artist/song.mp3"))
        assertEquals("primary" to "Music", MusicRoots.fromDataPath("/sdcard/Music/song.mp3"))
        assertEquals("1a2b-3c4d" to "Songs/Rock", MusicRoots.fromDataPath("/storage/1A2B-3C4D/Songs/Rock/a.flac"))
        assertEquals("primary" to "", MusicRoots.fromDataPath("/storage/emulated/0/song.mp3"))
        assertNull(MusicRoots.fromDataPath("/data/app/something.mp3"))
    }

    @Test
    fun volumeNamesAreNormalized() {
        assertEquals("primary", MusicRoots.normalizeVolume("external_primary"))
        assertEquals("primary", MusicRoots.normalizeVolume(null))
        assertEquals("primary", MusicRoots.normalizeVolume("Primary"))
        assertEquals("1a2b-3c4d", MusicRoots.normalizeVolume("1A2B-3C4D"))
    }

    // endregion

    // region persistence format

    @Test
    fun rootsSurviveEncodeAndDecode() {
        val saved = listOf(music, MusicRoot("primary", "Download/Music", "content://tree/dl"), MusicRoot("1a2b-3c4d", "Songs", "content://tree/sd"))
        assertEquals(saved, MusicRoots.decode(MusicRoots.encode(saved)))
        assertEquals(emptyList<MusicRoot>(), MusicRoots.decode(MusicRoots.encode(emptyList())))
        assertEquals(emptyList<MusicRoot>(), MusicRoots.decode(null))
    }

    @Test
    fun decodeSkipsMalformedLinesAndDuplicates() {
        val decoded = MusicRoots.decode("*\tMusic\t\ngarbage\n\t\t\n*\tmusic\t\nprimary\tPodcasts\tcontent://tree/p")
        assertEquals(listOf(music, MusicRoot("primary", "Podcasts", "content://tree/p")), decoded)
    }

    // endregion
}
