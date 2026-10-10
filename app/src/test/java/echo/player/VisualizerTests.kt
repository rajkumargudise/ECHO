package echo.player

import echo.player.visualizer.AudioSampleTap
import echo.player.visualizer.Ballistics
import echo.player.visualizer.Fft
import echo.player.visualizer.LevelMath
import echo.player.visualizer.SampleSource
import echo.player.visualizer.SpectrumAnalyzer
import echo.player.visualizer.VisualizerEngine
import echo.player.visualizer.VisualizerMode
import echo.player.visualizer.Waveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Test

class VisualizerTests {
    private val rate = 44_100

    private fun sine(freq: Double, amplitude: Float, count: Int, phase: Double = 0.0) =
        FloatArray(count) { i -> (amplitude * sin(2.0 * PI * freq * i / rate + phase)).toFloat() }

    private fun argMax(values: FloatArray): Int = values.indices.maxByOrNull { values[it] }!!

    // region FFT / spectrum

    @Test
    fun fftPutsASineOnItsOwnBin() {
        val size = 2048
        val bin = 100
        val freq = bin.toDouble() * rate / size
        val re = sine(freq, 1f, size)
        val im = FloatArray(size)
        Fft(size).forward(re, im)
        val magnitudes = FloatArray(size / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(bin, argMax(magnitudes))
        // A unit sine concentrates about size/2 of magnitude in its bin.
        assertEquals(size / 2f, magnitudes[bin], size * 0.02f)
    }

    @Test
    fun fftOfSilenceIsZero() {
        val re = FloatArray(1024)
        val im = FloatArray(1024)
        Fft(1024).forward(re, im)
        assertTrue(re.all { it == 0f } && im.all { it == 0f })
    }

    @Test
    fun spectrumOfSilenceIsFlat() {
        val analyzer = SpectrumAnalyzer()
        val out = FloatArray(analyzer.bandCount) { 1f }
        analyzer.analyze(FloatArray(2048), rate, out)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun spectrumPeaksInTheBandOfTheTone() {
        val analyzer = SpectrumAnalyzer()
        val low = FloatArray(analyzer.bandCount)
        val mid = FloatArray(analyzer.bandCount)
        val high = FloatArray(analyzer.bandCount)
        analyzer.analyze(sine(200.0, 0.5f, 2048), rate, low)
        analyzer.analyze(sine(1000.0, 0.5f, 2048), rate, mid)
        analyzer.analyze(sine(5000.0, 0.5f, 2048), rate, high)

        assertTrue("200 Hz band ${argMax(low)} should sit below 1 kHz band ${argMax(mid)}", argMax(low) < argMax(mid))
        assertTrue("1 kHz band ${argMax(mid)} should sit below 5 kHz band ${argMax(high)}", argMax(mid) < argMax(high))
        // A half-scale tone is about -6 dBFS, which is high on a 60 dB scale.
        assertTrue(mid[argMax(mid)] > 0.8f)
    }

    @Test
    fun differentAudioGivesDifferentSpectra() {
        val analyzer = SpectrumAnalyzer()
        val a = FloatArray(analyzer.bandCount)
        val b = FloatArray(analyzer.bandCount)
        analyzer.analyze(sine(440.0, 0.5f, 2048), rate, a)
        analyzer.analyze(sine(3000.0, 0.5f, 2048), rate, b)
        val difference = a.indices.sumOf { abs(a[it] - b[it]).toDouble() }
        assertTrue("spectra should differ, difference=$difference", difference > 1.0)
    }

    @Test
    fun louderToneGivesTallerBars() {
        val analyzer = SpectrumAnalyzer()
        val quiet = FloatArray(analyzer.bandCount)
        val loud = FloatArray(analyzer.bandCount)
        analyzer.analyze(sine(1000.0, 0.02f, 2048), rate, quiet)
        analyzer.analyze(sine(1000.0, 0.8f, 2048), rate, loud)
        assertTrue(loud.max() > quiet.max())
    }

    @Test
    fun spectrumValuesStayInsideZeroToOne() {
        val analyzer = SpectrumAnalyzer()
        val out = FloatArray(analyzer.bandCount)
        analyzer.analyze(sine(1000.0, 5f, 2048), rate, out) // deliberately clipped input
        assertTrue(out.all { it in 0f..1f })
    }

    // endregion

    // region ballistics / levels / waveform

    @Test
    fun barsRiseFasterThanTheyFall() {
        val rising = floatArrayOf(0f)
        val falling = floatArrayOf(1f)
        Ballistics.step(rising, floatArrayOf(1f), 0.033f, attackTau = 0.04f, releaseTau = 0.28f)
        Ballistics.step(falling, floatArrayOf(0f), 0.033f, attackTau = 0.04f, releaseTau = 0.28f)
        assertTrue("rise ${rising[0]} should cover more distance than fall ${1f - falling[0]}", rising[0] > 1f - falling[0])
        assertTrue(rising[0] in 0f..1f && falling[0] in 0f..1f)
    }

    @Test
    fun ballisticsConvergeOnTheTarget() {
        val value = floatArrayOf(0f)
        repeat(200) { Ballistics.step(value, floatArrayOf(0.6f), 0.033f, 0.04f, 0.28f) }
        assertEquals(0.6f, value[0], 0.001f)
    }

    @Test
    fun levelMathMeasuresRmsAndPeak() {
        val constant = FloatArray(1000) { 0.5f }
        assertEquals(0.5f, LevelMath.rms(constant), 0.0001f)
        assertEquals(0.5f, LevelMath.peak(constant), 0f)
        val tone = sine(1000.0, 1f, 4410)
        assertEquals(1f / sqrt(2f), LevelMath.rms(tone), 0.01f)
    }

    @Test
    fun meterPositionIsMonotonicAndBounded() {
        assertEquals(0f, LevelMath.toMeter(0f), 0f)
        assertEquals(1f, LevelMath.toMeter(1f), 0f)
        assertTrue(LevelMath.toMeter(0.01f) < LevelMath.toMeter(0.1f))
        assertTrue(LevelMath.toMeter(0.1f) < LevelMath.toMeter(0.5f))
    }

    @Test
    fun waveformStartsAtARisingZeroCrossingSoATonePlotIsSteady() {
        val out = FloatArray(256)
        // Two different phases of the same tone must give (almost) the same trace.
        val first = FloatArray(256)
        val second = FloatArray(256)
        Waveform.fill(sine(441.0, 0.8f, 2048, phase = 0.0), first)
        Waveform.fill(sine(441.0, 0.8f, 2048, phase = 1.3), second)
        val difference = first.indices.maxOf { abs(first[it] - second[it]) }
        assertTrue("trigger should align phases, max difference $difference", difference < 0.15f)
        Waveform.fill(FloatArray(2048), out)
        assertTrue(out.all { it == 0f })
    }

    // endregion

    // region sample tap

    private fun pcm(vararg stereo: Pair<Short, Short>): ByteBuffer {
        val buffer = ByteBuffer.allocate(stereo.size * 4).order(ByteOrder.nativeOrder())
        stereo.forEach { (l, r) -> buffer.putShort(l); buffer.putShort(r) }
        buffer.flip()
        return buffer
    }

    @Test
    fun tapReturnsTheLatestSamplesOldestFirstWithZeroPadding() {
        AudioSampleTap.reset()
        AudioSampleTap.writePcm16(pcm(16384.toShort() to (-16384).toShort(), 8192.toShort() to (-8192).toShort()), 2, nowNanos = 1)
        val left = FloatArray(4)
        val right = FloatArray(4)
        AudioSampleTap.copyLatest(0, left)
        AudioSampleTap.copyLatest(1, right)
        assertEquals(listOf(0f, 0f, 0.5f, 0.25f), left.toList())
        assertEquals(listOf(0f, 0f, -0.5f, -0.25f), right.toList())
        assertTrue(AudioSampleTap.hasReceivedAudio)
    }

    @Test
    fun monoAudioFeedsBothChannels() {
        AudioSampleTap.reset()
        val mono = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putShort(16384).putShort(-16384)
        mono.flip()
        AudioSampleTap.writePcm16(mono, 1, nowNanos = 1)
        val left = FloatArray(2)
        val right = FloatArray(2)
        AudioSampleTap.copyLatest(0, left)
        AudioSampleTap.copyLatest(1, right)
        assertEquals(left.toList(), right.toList())
        assertEquals(listOf(0.5f, -0.5f), left.toList())
    }

    @Test
    fun tapReadingDoesNotConsumeTheBuffer() {
        AudioSampleTap.reset()
        val buffer = pcm(1000.toShort() to 1000.toShort(), 2000.toShort() to 2000.toShort())
        AudioSampleTap.writePcm16(buffer, 2, nowNanos = 1)
        assertEquals(8, buffer.remaining())
    }

    @Test
    fun tapIsLiveOnlyBrieflyAfterAWrite() {
        AudioSampleTap.reset()
        assertFalse(AudioSampleTap.isLive(1_000_000_000L))
        AudioSampleTap.writePcm16(pcm(1.toShort() to 1.toShort()), 2, nowNanos = 1_000_000_000L)
        assertTrue(AudioSampleTap.isLive(1_100_000_000L))
        assertFalse(AudioSampleTap.isLive(2_000_000_000L))
    }

    @Test
    fun resetClearsStaleAudio() {
        AudioSampleTap.writePcm16(pcm(30000.toShort() to 30000.toShort()), 2, nowNanos = 5)
        AudioSampleTap.reset()
        val left = FloatArray(8)
        AudioSampleTap.copyLatest(0, left)
        assertTrue(left.all { it == 0f })
        assertFalse(AudioSampleTap.isLive(6))
    }

    @Test
    fun tapKeepsOnlyTheNewestFramesOfAHugeBuffer() {
        AudioSampleTap.reset()
        val frames = 20_000
        val buffer = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            buffer.putShort((i % 1000).toShort())
            buffer.putShort((i % 1000).toShort())
        }
        buffer.flip()
        AudioSampleTap.writePcm16(buffer, 2, nowNanos = 1)
        val left = FloatArray(4)
        AudioSampleTap.copyLatest(0, left)
        val expected = (frames - 4 until frames).map { (it % 1000) / 32768f }
        assertEquals(expected, left.toList())
    }

    // endregion

    // region engine

    private class FakeSource(var live: Boolean, var received: Boolean = live, val signal: FloatArray) : SampleSource {
        override val sampleRate = 44_100
        override val hasReceivedAudio get() = received
        override fun isLive(nowNanos: Long) = live
        override fun copyLatest(channel: Int, dst: FloatArray) {
            for (i in dst.indices) dst[i] = signal[i % signal.size]
        }
    }

    private fun runFrames(engine: VisualizerEngine, frames: Int, startNanos: Long, playing: Boolean = true): Long {
        var now = startNanos
        repeat(frames) {
            now += 33_000_000L
            engine.update(now, 0.033f, playing)
        }
        return now
    }

    @Test
    fun engineShowsTheRealSignalWhileLive() {
        val source = FakeSource(live = true, signal = sine(1000.0, 0.5f, 2048))
        val engine = VisualizerEngine(source)
        runFrames(engine, 30, 0)
        assertTrue(engine.isLive)
        assertTrue(engine.bars.max() > 0.5f)
        assertTrue(engine.levelLeft > 0.3f && engine.levelRight > 0.3f)
        assertTrue(engine.peakLeft >= engine.levelLeft - 0.2f)
        assertTrue(engine.waveform.any { abs(it) > 0.1f })
        assertFalse(engine.unavailable)
    }

    @Test
    fun engineMovesWhenTheAudioChanges() {
        val signal = sine(300.0, 0.5f, 2048)
        val source = FakeSource(live = true, signal = signal)
        val engine = VisualizerEngine(source)
        var now = runFrames(engine, 40, 0)
        val lowPeakBand = argMax(engine.bars)

        val highTone = sine(6000.0, 0.5f, 2048)
        System.arraycopy(highTone, 0, signal, 0, signal.size)
        runFrames(engine, 40, now)
        assertTrue("peak band should move up: $lowPeakBand -> ${argMax(engine.bars)}", argMax(engine.bars) > lowPeakBand)
    }

    @Test
    fun engineDecaysToRestWhenAudioStops() {
        val source = FakeSource(live = true, signal = sine(1000.0, 0.5f, 2048))
        val engine = VisualizerEngine(source)
        var now = runFrames(engine, 30, 0)
        assertTrue(engine.bars.max() > 0.3f)

        source.live = false
        now = runFrames(engine, 400, now, playing = false)
        assertFalse(engine.isLive)
        assertTrue(engine.bars.all { it < 0.01f })
        assertTrue(engine.isIdle)
    }

    @Test
    fun engineFlagsUnavailableWhenPlayingButNoAudioEverArrives() {
        val source = FakeSource(live = false, received = false, signal = FloatArray(2048))
        val engine = VisualizerEngine(source)
        runFrames(engine, 30, 0, playing = true)
        assertFalse("not flagged before the grace period", engine.unavailable)
        runFrames(engine, 120, 30 * 33_000_000L, playing = true)
        assertTrue(engine.unavailable)
        // Paused clears the flag instead of claiming the visualizer is broken.
        engine.update(Long.MAX_VALUE / 2, 0.033f, false)
        assertFalse(engine.unavailable)
    }

    @Test
    fun engineNeverShowsMotionWithoutALiveSource() {
        val source = FakeSource(live = false, received = true, signal = sine(1000.0, 1f, 2048))
        val engine = VisualizerEngine(source)
        runFrames(engine, 60, 0)
        assertTrue(engine.bars.all { it == 0f })
        assertEquals(0f, engine.levelLeft, 0f)
    }

    // endregion
}

/** The data paths behind the added display modes. */
class VisualizerModeTests {
    private val rate = 44_100

    private class ToneSource(private val freq: Double, private val amplitude: Float, private val rate: Int = 44_100) : SampleSource {
        var live = true
        override val sampleRate = rate
        override val hasReceivedAudio = true
        override fun isLive(nowNanos: Long) = live
        override fun copyLatest(channel: Int, dst: FloatArray) {
            for (i in dst.indices) dst[i] = (amplitude * sin(2.0 * PI * freq * i / rate)).toFloat()
        }
    }

    private fun run(engine: VisualizerEngine, frames: Int, playing: Boolean = true, needs: Int = VisualizerEngine.NEED_ALL) {
        var now = 1_000_000_000L
        repeat(frames) {
            engine.update(now, 0.033f, playing, needs)
            now += 33_000_000L
        }
    }

    @Test
    fun everyModeHasADistinctNameAndSomeNeeds() {
        val modes = VisualizerMode.entries
        assertEquals(10, modes.size)
        assertEquals(modes.size, modes.map { it.label }.toSet().size)
        assertEquals("ECHO PULSE is the signature default", VisualizerMode.ECHO_PULSE, VisualizerMode.fromName("nonsense"))
        assertEquals(VisualizerMode.ECHO_PULSE, VisualizerMode.fromName(null))
        assertEquals(VisualizerMode.PULSE, VisualizerMode.fromName("PULSE"))
    }

    @Test
    fun modesAskOnlyForTheAnalysisTheyDraw() {
        assertEquals(VisualizerEngine.NEED_SPECTRUM, VisualizerMode.SPECTRUM.needs)
        assertEquals(VisualizerEngine.NEED_SPECTRUM, VisualizerMode.NEON_LINE.needs)
        assertEquals(VisualizerEngine.NEED_WAVEFORM, VisualizerMode.SCOPE.needs)
        assertEquals("levels-only modes skip the FFT and the waveform", 0, VisualizerMode.VU.needs)
        assertEquals(0, VisualizerMode.WAVEFORM.needs)
        assertEquals(0, VisualizerMode.PULSE.needs)
        assertEquals("ECHO PULSE needs only the loudness", 0, VisualizerMode.ECHO_PULSE.needs)
    }

    @Test
    fun skippingTheSpectrumLeavesTheBarsAtRestButStillMetersTheAudio() {
        val engine = VisualizerEngine(ToneSource(1000.0, 0.5f))
        run(engine, 30, needs = 0)
        assertTrue("bars stay down when nothing asked for them", engine.bars.all { it < 0.01f })
        assertTrue("levels still follow the audio", engine.levelLeft > 0.5f)
        assertTrue(engine.instantLevel > 0.5f)
        run(engine, 30, needs = VisualizerEngine.NEED_SPECTRUM)
        assertTrue("asking for the spectrum brings the bars up", engine.bars.max() > 0.5f)
    }

    @Test
    fun theLoudnessHistoryFillsFromRealAudioAndScrolls() {
        val engine = VisualizerEngine(ToneSource(440.0, 0.5f))
        assertTrue(engine.envelopeAt(0) == 0f)
        run(engine, 40, needs = 0)
        assertTrue("newest entry reflects the tone", engine.envelopeAt(0) > 0.5f)
        // Older entries reach back before the audio started: they are still empty.
        assertTrue(engine.envelopeAt(engine.envelopeSize - 1) < 0.5f || engine.envelopeSize <= 40)
    }

    @Test
    fun theLoudnessHistoryDecaysWhenTheAudioStopsAndTheEngineGoesIdle() {
        val source = ToneSource(440.0, 0.5f)
        val engine = VisualizerEngine(source)
        run(engine, 40, needs = 0)
        assertFalse(engine.isIdle)
        source.live = false
        run(engine, 200, playing = false, needs = 0)
        assertTrue("history empties", (0 until engine.envelopeSize).all { engine.envelopeAt(it) < 0.01f })
        assertTrue(engine.isIdle)
    }

    @Test
    fun aLouderToneGivesALargerLoudnessAndInstantLevel() {
        val quiet = VisualizerEngine(ToneSource(440.0, 0.05f)).also { run(it, 30, needs = 0) }
        val loud = VisualizerEngine(ToneSource(440.0, 0.8f)).also { run(it, 30, needs = 0) }
        assertTrue(loud.envelopeAt(0) > quiet.envelopeAt(0))
        assertTrue(loud.instantLevel > quiet.instantLevel)
    }

    @Test
    fun silenceGivesZeroInstantLevel() {
        val engine = VisualizerEngine(ToneSource(440.0, 0f))
        run(engine, 10)
        assertEquals(0f, engine.instantLevel, 0.0001f)
    }
}
