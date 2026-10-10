package echo.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import echo.player.visualizer.AudioSampleTap
import java.nio.ByteBuffer

/**
 * Pass-through audio processor: the sound is not changed at all, but the PCM that is about to be played is
 * copied into [AudioSampleTap] for the visualizer. Being part of the player's own audio pipeline means the
 * display shows exactly what ECHO is playing, needs no RECORD_AUDIO permission, and follows the player's
 * lifecycle automatically.
 *
 * It only activates for 16-bit PCM. For any other format it reports itself inactive, so playback is
 * unaffected and the visualizer falls back to its "unavailable" state instead of showing fake data.
 */
@OptIn(UnstableApi::class)
class VisualizerAudioProcessor : BaseAudioProcessor() {
    private var channelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        AudioSampleTap.configure(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        AudioSampleTap.writePcm16(inputBuffer, channelCount)
        val output = replaceOutputBuffer(size)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        AudioSampleTap.reset()
    }

    override fun onReset() {
        AudioSampleTap.reset()
        channelCount = 0
    }
}
