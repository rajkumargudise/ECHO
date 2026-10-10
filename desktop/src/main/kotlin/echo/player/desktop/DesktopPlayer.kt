package echo.player.desktop

import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.Track
import echo.player.playback.EchoRepeatMode
import echo.player.playback.PlaybackControls
import echo.player.playback.PlaybackError
import echo.player.playback.PlaybackLogic
import echo.player.playback.PlaybackState
import echo.player.playback.PlaybackStatus
import echo.player.playback.PreviousAction
import echo.player.playback.QueueItem
import echo.player.visualizer.AudioSampleTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * The desktop playback engine. It decodes the file with Java Sound (MP3, FLAC, OGG and WAV decoders are bundled),
 * plays it on the system's default output, and copies the same PCM into [AudioSampleTap] so the visualizer draws
 * the real sound, exactly as on Android. One thread plays one track; changing track or seeking starts a new one.
 */
class DesktopPlayer(private val scope: CoroutineScope) : PlaybackControls {
    private val lock = Any()
    private val _state = MutableStateFlow(PlaybackState(isConnected = true))
    private val _position = MutableStateFlow(0L)

    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val positionMs: StateFlow<Long> = _position.asStateFlow()

    private var original: List<Track> = emptyList()
    private var order: List<Track> = emptyList()
    private var queueItems: List<QueueItem> = emptyList()
    private var index = -1
    private var playWhenReady = false
    private var status = PlaybackStatus.IDLE
    private var error: PlaybackError? = null
    private var repeat = EchoRepeatMode.OFF
    private var shuffle = false
    private var session: Session? = null
    private var generation = 0

    init {
        scope.launch {
            while (isActive) {
                delay(250)
                val s = synchronized(lock) { session }
                if (s != null && s.isRunningAudio) _position.value = s.positionMs()
            }
        }
    }

    // -------- the controls the screens use --------

    override fun playQueue(tracks: List<Track>, startIndex: Int) = synchronized(lock) {
        if (tracks.isEmpty()) return@synchronized
        original = tracks
        val start = PlaybackLogic.clampStartIndex(tracks.size, startIndex)
        if (shuffle) {
            order = shuffledWithFirst(tracks, tracks[start])
            index = 0
        } else {
            order = tracks
            index = start
        }
        queueItems = order.map { QueueItem(it.id, it.title, it.artist) }
        startTrack(index, 0L, play = true)
    }

    /** Local tracks play; online tracks need an online source, which the desktop app does not have yet. */
    override fun playItems(items: List<PlayableTrack>, startIndex: Int) {
        val local = items.filterIsInstance<PlayableTrack.Local>().map { it.track }
        if (local.isNotEmpty()) playQueue(local, startIndex.coerceIn(0, local.lastIndex))
    }

    override fun togglePlayPause() = synchronized(lock) {
        if (index < 0) return@synchronized
        if (status == PlaybackStatus.ENDED || status == PlaybackStatus.IDLE) {
            startTrack(index, 0L, play = true)
            return@synchronized
        }
        playWhenReady = !playWhenReady
        session?.setPaused(!playWhenReady)
        publish()
    }

    override fun next() = synchronized(lock) {
        if (order.isEmpty()) return@synchronized
        val target = when {
            index + 1 < order.size -> index + 1
            repeat == EchoRepeatMode.ALL -> 0
            else -> return@synchronized
        }
        startTrack(target, 0L, play = true)
    }

    override fun previous() = synchronized(lock) {
        if (order.isEmpty()) return@synchronized
        val hasPrevious = index > 0 || repeat == EchoRepeatMode.ALL
        when (PlaybackLogic.previousAction(currentPositionMs(), hasPrevious)) {
            PreviousAction.RESTART_TRACK -> startTrack(index, 0L, play = playWhenReady || status == PlaybackStatus.ENDED)
            PreviousAction.GO_TO_PREVIOUS -> startTrack(if (index > 0) index - 1 else order.lastIndex, 0L, play = true)
        }
    }

    override fun seekTo(positionMs: Long) = synchronized(lock) {
        if (index < 0) return@synchronized
        val duration = order[index].durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration - 1) else positionMs.coerceAtLeast(0L)
        startTrack(index, target, play = playWhenReady)
    }

    override fun cycleRepeatMode() = synchronized(lock) {
        repeat = repeat.next()
        publish()
    }

    override fun toggleShuffle() = synchronized(lock) {
        shuffle = !shuffle
        if (order.isNotEmpty()) {
            val current = order.getOrNull(index)
            if (shuffle) {
                order = if (current != null) shuffledWithFirst(order, current) else order.shuffled()
                index = if (current != null) 0 else -1
            } else {
                order = original
                index = if (current != null) original.indexOfFirst { it.id == current.id }.coerceAtLeast(0) else -1
            }
            queueItems = order.map { QueueItem(it.id, it.title, it.artist) }
        }
        publish()
    }

    override fun skipToQueueItem(index: Int) = synchronized(lock) {
        if (index in order.indices) startTrack(index, 0L, play = true)
    }

    /** Stops playback and releases the audio line. Called when the window closes. */
    fun release() = synchronized(lock) {
        generation++
        session?.cancel()
        session = null
        AudioSampleTap.reset()
    }

    // -------- internals --------

    private fun shuffledWithFirst(tracks: List<Track>, first: Track): List<Track> =
        listOf(first) + tracks.filter { it.id != first.id }.shuffled()

    private fun currentPositionMs(): Long = session?.takeIf { it.isRunningAudio }?.positionMs() ?: _position.value

    private fun startTrack(target: Int, startMs: Long, play: Boolean) {
        session?.cancel()
        index = target
        val track = order[target]
        playWhenReady = play
        error = null
        status = PlaybackStatus.BUFFERING
        generation++
        AudioSampleTap.reset()
        _position.value = startMs
        val s = Session(track, startMs, generation, paused = !play)
        session = s
        publish()
        s.start()
    }

    private fun publish() {
        val track = order.getOrNull(index)
        _state.value = PlaybackState(
            isConnected = true,
            currentTrackId = track?.id,
            title = track?.title,
            artist = track?.artist,
            album = track?.album,
            isPlaying = playWhenReady && status == PlaybackStatus.READY,
            playWhenReady = playWhenReady,
            status = status,
            positionMs = _position.value,
            durationMs = track?.durationMs ?: 0L,
            repeatMode = repeat,
            shuffleEnabled = shuffle,
            queue = queueItems,
            currentIndex = index,
            error = error,
        )
    }

    private fun sessionReady(gen: Int) = synchronized(lock) {
        if (gen != generation) return@synchronized
        status = PlaybackStatus.READY
        publish()
    }

    private fun sessionFailed(gen: Int, why: PlaybackError) = synchronized(lock) {
        if (gen != generation) return@synchronized
        status = PlaybackStatus.IDLE
        playWhenReady = false
        error = why
        publish()
    }

    /** The track played to its end: repeat it, move on, or stop at the end of the queue. */
    private fun sessionEnded(gen: Int) = synchronized(lock) {
        if (gen != generation) return@synchronized
        when {
            repeat == EchoRepeatMode.ONE -> startTrack(index, 0L, play = true)
            index + 1 < order.size -> startTrack(index + 1, 0L, play = true)
            repeat == EchoRepeatMode.ALL && order.isNotEmpty() -> startTrack(0, 0L, play = true)
            else -> {
                status = PlaybackStatus.ENDED
                playWhenReady = false
                _position.value = 0L
                publish()
            }
        }
    }

    private inner class Session(
        private val track: Track,
        private val startMs: Long,
        private val gen: Int,
        paused: Boolean,
    ) : Thread("echo-audio") {
        @Volatile private var cancelled = false

        @Volatile private var paused = paused

        @Volatile private var line: SourceDataLine? = null

        @Volatile private var rate = 44_100f

        @Volatile var isRunningAudio = false
            private set

        private val pauseLock = Object()

        init {
            isDaemon = true
        }

        fun cancel() {
            cancelled = true
            synchronized(pauseLock) { pauseLock.notifyAll() }
        }

        fun setPaused(value: Boolean) {
            paused = value
            if (!value) synchronized(pauseLock) { pauseLock.notifyAll() }
        }

        fun positionMs(): Long {
            val l = line ?: return startMs
            return startMs + l.longFramePosition * 1000L / rate.toLong().coerceAtLeast(1L)
        }

        override fun run() {
            var raw: AudioInputStream? = null
            var pcm: AudioInputStream? = null
            var out: SourceDataLine? = null
            try {
                val file = File(track.uri.toString().let { java.net.URI(it) })
                if (!file.isFile) throw FileNotFoundException(file.path)
                raw = AudioSystem.getAudioInputStream(file)
                val base = raw.format
                val target = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, base.sampleRate, 16, base.channels, base.channels * 2, base.sampleRate, false,
                )
                pcm = AudioSystem.getAudioInputStream(target, raw)
                rate = target.sampleRate
                skipTo(pcm, target, startMs)

                val buffer = (target.frameSize * target.sampleRate / 5f).toInt() // about 0.2 s, so seeks and the visualizer feel immediate
                    .let { it - it % target.frameSize }
                    .coerceAtLeast(target.frameSize * 1024)
                out = try {
                    AudioSystem.getSourceDataLine(target).also { it.open(target, buffer) }
                } catch (e: IllegalArgumentException) {
                    throw LineUnavailableException(e.message) // no output device at all
                }
                line = out
                AudioSampleTap.configure(target.sampleRate.toInt())
                if (!paused) out.start()
                isRunningAudio = true
                sessionReady(gen)

                val chunk = ByteArray(4096 - 4096 % target.frameSize)
                var running = !paused
                while (!cancelled) {
                    if (paused) {
                        if (running) {
                            out.stop()
                            running = false
                        }
                        synchronized(pauseLock) { while (paused && !cancelled) pauseLock.wait(200) }
                        continue
                    }
                    if (!running) {
                        out.start()
                        running = true
                    }
                    val n = pcm.read(chunk, 0, chunk.size)
                    if (n < 0) break
                    if (n == 0) continue
                    AudioSampleTap.writePcm16(nativeOrderPcm(chunk, n), target.channels)
                    var written = 0
                    while (written < n && !cancelled) written += out.write(chunk, written, n - written)
                }
                if (!cancelled) {
                    out.drain()
                    if (!cancelled) sessionEnded(gen)
                }
            } catch (e: FileNotFoundException) {
                if (!cancelled) sessionFailed(gen, PlaybackError.FILE_UNAVAILABLE)
            } catch (e: UnsupportedAudioFileException) {
                if (!cancelled) sessionFailed(gen, PlaybackError.UNSUPPORTED_OR_CORRUPT)
            } catch (e: LineUnavailableException) {
                if (!cancelled) sessionFailed(gen, PlaybackError.NO_AUDIO_OUTPUT)
            } catch (e: Exception) {
                if (!cancelled) sessionFailed(gen, if (e is java.io.IOException) PlaybackError.UNSUPPORTED_OR_CORRUPT else PlaybackError.GENERIC)
            } finally {
                isRunningAudio = false
                runCatching { out?.stop(); out?.flush(); out?.close() }
                runCatching { pcm?.close() }
                runCatching { raw?.close() }
            }
        }

        /** Decoded audio cannot jump, so a seek decodes and discards up to the wanted spot (fast: far quicker than real time). */
        private fun skipTo(stream: AudioInputStream, format: AudioFormat, ms: Long) {
            if (ms <= 0L) return
            var remaining = (ms * format.sampleRate.toLong() / 1000L) * format.frameSize
            val sink = ByteArray(16 * 1024)
            while (remaining > 0 && !cancelled) {
                val n = stream.read(sink, 0, minOf(sink.size.toLong(), remaining).toInt())
                if (n < 0) break
                remaining -= n
            }
        }

        /** The tap reads PCM in the machine's byte order; the decoded audio is little-endian. */
        private fun nativeOrderPcm(source: ByteArray, n: Int): ByteBuffer {
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) return ByteBuffer.wrap(source, 0, n)
            val copy = source.copyOf(n)
            var i = 0
            while (i + 1 < n) {
                val b = copy[i]
                copy[i] = copy[i + 1]
                copy[i + 1] = b
                i += 2
            }
            return ByteBuffer.wrap(copy)
        }
    }
}
