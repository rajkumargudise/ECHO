package echo.player.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.util.Collections

/** Cover art embedded in the audio files, read on demand and kept in a small cache. */
object DesktopArtwork {
    private val paths = HashMap<String, File>()
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 96
    })

    @Synchronized
    fun register(idToFile: Map<String, File>) {
        paths.clear()
        paths.putAll(idToFile)
        cache.clear()
    }

    fun cached(trackId: String): ImageBitmap? = cache[trackId]

    fun load(trackId: String): ImageBitmap? {
        if (cache.containsKey(trackId)) return cache[trackId]
        val file = synchronized(this) { paths[trackId] } ?: return null
        val bitmap = runCatching {
            val artwork = org.jaudiotagger.audio.AudioFileIO.read(file).tag?.firstArtwork ?: return@runCatching null
            org.jetbrains.skia.Image.makeFromEncoded(artwork.binaryData).toComposeImageBitmap()
        }.getOrNull()
        cache[trackId] = bitmap
        return bitmap
    }
}
