package echo.player.desktop

import android.net.Uri
import echo.player.domain.TrackSource
import echo.player.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** The few facts read from a file's tags. */
data class TagInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val durationMs: Long = 0L,
)

object AudioFiles {
    val extensions = setOf("mp3", "flac", "ogg", "oga", "wav", "m4a", "aac", "opus", "wma")

    /** Formats the built-in decoders play. The others are still listed so the library is complete, and say so when played. */
    val playable = setOf("mp3", "flac", "ogg", "oga", "wav", "m4a", "aac")

    fun isAudio(file: File) = file.extension.lowercase() in extensions

    fun isPlayable(file: File) = file.extension.lowercase() in playable

    fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        "m4a", "aac" -> "audio/mp4"
        "wma" -> "audio/x-ms-wma"
        else -> "audio/*"
    }

    /** A stable id for a file: the same path always gives the same id, so favorites and playlists survive a rescan. */
    fun idFor(file: File): String {
        val canonical = file.absoluteFile.invariantSeparatorsPath.lowercase()
        val digest = MessageDigest.getInstance("SHA-1").digest(canonical.toByteArray(Charsets.UTF_8))
        return "f:" + digest.take(10).joinToString("") { "%02x".format(it) }
    }
}

/** Builds a [Track] from a file and its tags. Folder names look like "Music/Telugu/", the same as on Android. */
object TrackFactory {
    fun build(file: File, root: File, tags: TagInfo?): Track {
        val parent = file.parentFile ?: root
        val rootName = root.name.ifBlank { root.path.trimEnd('\\', '/') }.ifBlank { "Music" }
        val below = runCatching { parent.relativeTo(root).invariantSeparatorsPath }.getOrDefault("").trim('/')
        val relative = (listOf(rootName) + below.split('/').filter { it.isNotBlank() && it != "." }).joinToString("/") + "/"
        val artist = Track.cleanMetadata(tags?.artist, Track.UNKNOWN_ARTIST)
        return Track(
            id = AudioFiles.idFor(file),
            uri = Uri.fromFile(file),
            title = Track.fallbackTitle(file.name, tags?.title),
            artist = artist,
            album = Track.cleanMetadata(tags?.album, Track.UNKNOWN_ALBUM),
            albumArtist = Track.cleanMetadata(tags?.albumArtist, artist),
            durationMs = tags?.durationMs ?: 0L,
            trackNumber = tags?.trackNumber,
            year = tags?.year,
            mimeType = AudioFiles.mimeOf(file),
            size = file.length(),
            relativePath = relative,
            dateAdded = file.lastModified() / 1000,
        )
    }
}

/** Reads tags. Kept behind an interface so the scanner can be tested without real audio. */
fun interface TagReader {
    fun read(file: File): TagInfo?
}

object JAudioTagReader : TagReader {
    init {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
    }

    override fun read(file: File): TagInfo? = runCatching {
        val audio = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = audio.tag
        fun field(key: org.jaudiotagger.tag.FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
        TagInfo(
            title = field(org.jaudiotagger.tag.FieldKey.TITLE),
            artist = field(org.jaudiotagger.tag.FieldKey.ARTIST),
            album = field(org.jaudiotagger.tag.FieldKey.ALBUM),
            albumArtist = field(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST),
            trackNumber = field(org.jaudiotagger.tag.FieldKey.TRACK)?.substringBefore('/')?.trim()?.toIntOrNull(),
            year = field(org.jaudiotagger.tag.FieldKey.YEAR)?.take(4)?.toIntOrNull(),
            durationMs = audio.audioHeader.trackLength.toLong() * 1000L,
        )
    }.getOrNull()
}

/**
 * Walks the chosen folders and lists every audio file. Tags are cached by path, size and modified time, so a
 * second scan of a big library only reads the files that changed.
 */
class DesktopTrackSource(
    private val roots: () -> List<File>,
    private val cacheFile: File? = null,
    private val tags: TagReader = JAudioTagReader,
) : TrackSource {
    override suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val cache = loadCache()
        val updated = HashMap<String, TagInfo?>()
        val found = mutableListOf<Pair<File, File>>()
        for (root in roots()) {
            if (!root.isDirectory) continue
            root.walkTopDown()
                .onEnter { !it.name.equals("\$RECYCLE.BIN", ignoreCase = true) }
                .filter { it.isFile && AudioFiles.isAudio(it) }
                .forEach { found += it to root }
        }
        val tracks = coroutineScope {
            found.chunked(64).map { chunk ->
                async {
                    chunk.map { (file, root) ->
                        val key = cacheKey(file)
                        val info = if (key in cache) cache[key] else tags.read(file)
                        synchronized(updated) { updated[key] = info }
                        TrackFactory.build(file, root, info)
                    }
                }
            }.awaitAll().flatten()
        }
        saveCache(updated)
        tracks.distinctBy { it.id }
    }

    private fun cacheKey(file: File) = "${file.absoluteFile.invariantSeparatorsPath}|${file.length()}|${file.lastModified()}"

    private fun loadCache(): Map<String, TagInfo?> {
        val f = cacheFile?.takeIf { it.isFile } ?: return emptyMap()
        return runCatching {
            val o = JSONObject(f.readText(Charsets.UTF_8))
            o.keys().asSequence().associateWith { key ->
                if (o.isNull(key)) {
                    null
                } else {
                    o.getJSONObject(key).let {
                        TagInfo(
                            it.optString("title").ifBlank { null },
                            it.optString("artist").ifBlank { null },
                            it.optString("album").ifBlank { null },
                            it.optString("albumArtist").ifBlank { null },
                            if (it.has("track")) it.getInt("track") else null,
                            if (it.has("year")) it.getInt("year") else null,
                            it.optLong("durationMs"),
                        )
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveCache(entries: Map<String, TagInfo?>) {
        val f = cacheFile ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val o = JSONObject()
            for ((key, t) in entries) {
                o.put(
                    key,
                    if (t == null) {
                        JSONObject.NULL
                    } else {
                        JSONObject().apply {
                            put("title", t.title.orEmpty())
                            put("artist", t.artist.orEmpty())
                            put("album", t.album.orEmpty())
                            put("albumArtist", t.albumArtist.orEmpty())
                            t.trackNumber?.let { put("track", it) }
                            t.year?.let { put("year", it) }
                            put("durationMs", t.durationMs)
                        }
                    },
                )
            }
            f.writeText(o.toString(), Charsets.UTF_8)
        }
    }
}
