package echo.player.visualizer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Where the visualizer reads real audio from. */
interface SampleSource {
    val sampleRate: Int

    /** True once any real audio has ever been delivered (false means the tap never saw PCM at all). */
    val hasReceivedAudio: Boolean

    /** True while audio is flowing, i.e. a buffer arrived within the last few hundred milliseconds. */
    fun isLive(nowNanos: Long): Boolean

    /** Copies the most recent dst.size samples of [channel] (0 = left, 1 = right), oldest first. */
    fun copyLatest(channel: Int, dst: FloatArray)
}

/**
 * Lock-free tap on the PCM that the player is actually sending to the speaker. The playback service's
 * audio processor writes into it from the audio thread; the Now Playing screen reads it from the UI thread.
 * It holds two fixed-size rings and no native resources, so there is nothing to leak or release.
 * Reads can tear by a few samples while a write is in flight, which is harmless for drawing.
 */
object AudioSampleTap : SampleSource {
    private const val RING_SIZE = 8192
    private const val LIVE_WINDOW_NANOS = 250_000_000L

    private val rings = arrayOf(FloatArray(RING_SIZE), FloatArray(RING_SIZE))

    @Volatile private var framesWritten = 0L

    @Volatile private var lastWriteNanos = 0L

    @Volatile override var sampleRate: Int = 44_100
        private set

    @Volatile override var hasReceivedAudio: Boolean = false
        private set

    fun configure(sampleRate: Int) {
        this.sampleRate = sampleRate
    }

    /** Appends interleaved 16-bit PCM (native byte order) from [buffer]'s position to its limit. */
    fun writePcm16(buffer: ByteBuffer, channelCount: Int, nowNanos: Long = System.nanoTime()) {
        if (channelCount < 1) return
        val shorts = buffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        val frames = shorts.remaining() / channelCount
        if (frames == 0) return
        // Only the newest RING_SIZE frames can survive, so skip the rest of an oversized buffer.
        val skipped = if (frames > RING_SIZE) frames - RING_SIZE else 0
        var position = framesWritten + skipped
        for (frame in skipped until frames) {
            val left = shorts.get(frame * channelCount) / 32768f
            val right = if (channelCount > 1) shorts.get(frame * channelCount + 1) / 32768f else left
            val index = (position and (RING_SIZE - 1).toLong()).toInt()
            rings[0][index] = left
            rings[1][index] = right
            position++
        }
        framesWritten = framesWritten + frames
        lastWriteNanos = nowNanos
        hasReceivedAudio = true
    }

    /** Called when the player flushes (seek, track change): stale audio must not stay on screen. */
    fun reset() {
        framesWritten = 0L
        lastWriteNanos = 0L
        rings[0].fill(0f)
        rings[1].fill(0f)
    }

    override fun isLive(nowNanos: Long): Boolean {
        val last = lastWriteNanos
        return last != 0L && nowNanos - last < LIVE_WINDOW_NANOS
    }

    override fun copyLatest(channel: Int, dst: FloatArray) {
        val ring = rings[channel.coerceIn(0, 1)]
        val end = framesWritten
        val count = dst.size.toLong()
        for (i in dst.indices) {
            val source = end - count + i
            dst[i] = if (source < 0) 0f else ring[(source and (RING_SIZE - 1).toLong()).toInt()]
        }
    }
}
