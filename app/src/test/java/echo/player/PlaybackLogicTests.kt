package echo.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import echo.player.playback.EchoRepeatMode
import echo.player.playback.PlaybackError
import echo.player.playback.PlaybackLogic
import echo.player.playback.PreviousAction
import echo.player.playback.SavedPlayback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLogicTests {
    // region previous / queue index

    @Test
    fun previousRestartsTheTrackWhenPastTheThreshold() {
        assertEquals(PreviousAction.RESTART_TRACK, PlaybackLogic.previousAction(positionMs = 3_001, hasPrevious = true))
        assertEquals(PreviousAction.RESTART_TRACK, PlaybackLogic.previousAction(positionMs = 120_000, hasPrevious = true))
    }

    @Test
    fun previousGoesBackNearTheStartOfATrack() {
        assertEquals(PreviousAction.GO_TO_PREVIOUS, PlaybackLogic.previousAction(positionMs = 0, hasPrevious = true))
        assertEquals(PreviousAction.GO_TO_PREVIOUS, PlaybackLogic.previousAction(positionMs = 3_000, hasPrevious = true))
    }

    @Test
    fun previousOnTheFirstTrackAlwaysRestartsIt() {
        assertEquals(PreviousAction.RESTART_TRACK, PlaybackLogic.previousAction(positionMs = 500, hasPrevious = false))
    }

    @Test
    fun startIndexIsClampedIntoTheQueue() {
        assertEquals(2, PlaybackLogic.clampStartIndex(queueSize = 5, requested = 2))
        assertEquals(4, PlaybackLogic.clampStartIndex(queueSize = 5, requested = 99))
        assertEquals(0, PlaybackLogic.clampStartIndex(queueSize = 5, requested = -3))
        assertEquals(0, PlaybackLogic.clampStartIndex(queueSize = 0, requested = 7))
    }

    // endregion

    // region repeat

    @Test
    fun repeatModesMapToMedia3AndBack() {
        assertEquals(Player.REPEAT_MODE_OFF, EchoRepeatMode.OFF.toPlayerMode())
        assertEquals(Player.REPEAT_MODE_ALL, EchoRepeatMode.ALL.toPlayerMode())
        assertEquals(Player.REPEAT_MODE_ONE, EchoRepeatMode.ONE.toPlayerMode())
        EchoRepeatMode.entries.forEach { mode ->
            assertEquals(mode, EchoRepeatMode.fromPlayerMode(mode.toPlayerMode()))
        }
        assertEquals(EchoRepeatMode.OFF, EchoRepeatMode.fromPlayerMode(-1))
    }

    @Test
    fun repeatButtonCyclesOffAllOneOff() {
        assertEquals(EchoRepeatMode.ALL, EchoRepeatMode.OFF.next())
        assertEquals(EchoRepeatMode.ONE, EchoRepeatMode.ALL.next())
        assertEquals(EchoRepeatMode.OFF, EchoRepeatMode.ONE.next())
    }

    // endregion

    // region errors

    @Test
    fun missingFilesAreReportedAsUnavailable() {
        assertEquals(PlaybackError.FILE_UNAVAILABLE, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlaybackError.FILE_UNAVAILABLE, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
    }

    @Test
    fun undecodableFilesAreReportedAsUnsupportedOrCorrupt() {
        assertEquals(PlaybackError.UNSUPPORTED_OR_CORRUPT, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(PlaybackError.UNSUPPORTED_OR_CORRUPT, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(PlaybackError.UNSUPPORTED_OR_CORRUPT, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
    }

    @Test
    fun unknownErrorsFallBackToGeneric() {
        assertEquals(PlaybackError.GENERIC, PlaybackLogic.mapError(PlaybackException.ERROR_CODE_UNSPECIFIED))
        assertEquals(PlaybackError.GENERIC, PlaybackLogic.mapError(123_456))
    }

    // endregion

    // region persistence

    @Test
    fun queueIdsRoundTripThroughTheStoredFormat() {
        val ids = listOf(
            "content://media/external/audio/media/10",
            "content://media/external/audio/media/11",
            "content://media/external/audio/media/12",
        )
        assertEquals(ids, PlaybackLogic.decodeIds(PlaybackLogic.encodeIds(ids)))
        assertEquals(emptyList<String>(), PlaybackLogic.decodeIds(null))
        assertEquals(emptyList<String>(), PlaybackLogic.decodeIds(""))
    }

    private fun saved(ids: List<String>, index: Int, positionMs: Long = 42_000) =
        SavedPlayback(ids, index, positionMs, EchoRepeatMode.ALL, shuffleEnabled = true)

    @Test
    fun restoreKeepsQueueIndexAndPositionWhenEverythingStillExists() {
        val plan = PlaybackLogic.planRestore(saved(listOf("a", "b", "c"), index = 1), setOf("a", "b", "c"))!!
        assertEquals(listOf("a", "b", "c"), plan.ids)
        assertEquals(1, plan.index)
        assertEquals(42_000, plan.positionMs)
    }

    @Test
    fun restoreDropsMissingTracksButFollowsTheCurrentOne() {
        // "a" was deleted; "c" is still the track that was playing and now sits at index 1.
        val plan = PlaybackLogic.planRestore(saved(listOf("a", "b", "c", "d"), index = 2), setOf("b", "c", "d"))!!
        assertEquals(listOf("b", "c", "d"), plan.ids)
        assertEquals(1, plan.index)
        assertEquals(42_000, plan.positionMs)
    }

    @Test
    fun restoreWhenTheCurrentTrackIsGoneLandsOnTheNextSurvivorFromTheStart() {
        val plan = PlaybackLogic.planRestore(saved(listOf("a", "b", "c", "d"), index = 1), setOf("a", "c", "d"))!!
        assertEquals(listOf("a", "c", "d"), plan.ids)
        assertEquals(1, plan.index)
        assertEquals(0, plan.positionMs)
    }

    @Test
    fun restoreClampsWhenTheCurrentTrackWasLastAndIsGone() {
        val plan = PlaybackLogic.planRestore(saved(listOf("a", "b", "c"), index = 2), setOf("a", "b"))!!
        assertEquals(listOf("a", "b"), plan.ids)
        assertEquals(1, plan.index)
        assertEquals(0, plan.positionMs)
    }

    @Test
    fun restoreProducesNothingWhenNoTrackSurvives() {
        assertNull(PlaybackLogic.planRestore(saved(listOf("a", "b"), index = 0), emptySet()))
    }

    @Test
    fun restoreToleratesAnOutOfRangeSavedIndex() {
        val plan = PlaybackLogic.planRestore(saved(listOf("a", "b"), index = 9), setOf("a", "b"))!!
        assertEquals(listOf("a", "b"), plan.ids)
        assertEquals(1, plan.index)
        assertEquals(0, plan.positionMs)
    }

    // endregion
}
