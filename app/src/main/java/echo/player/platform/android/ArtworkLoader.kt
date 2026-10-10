package echo.player.platform.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the artwork embedded in an audio file (decoded small), caching results in memory.
 * Tracks without artwork are remembered so the file is not re-opened on every screen.
 */
object ArtworkLoader {
    private const val MAX_EDGE_PX = 600
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val noArtwork = HashSet<String>()

    fun cached(trackId: String): Bitmap? = cache.get(trackId)

    suspend fun load(context: Context, trackId: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(trackId)?.let { return@withContext it }
        if (synchronized(noArtwork) { trackId in noArtwork }) return@withContext null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context.applicationContext, Uri.parse(trackId))
            val bytes = retriever.embeddedPicture
            val bitmap = bytes?.let { decodeSmall(it) }
            if (bitmap != null) {
                cache.put(trackId, bitmap)
            } else {
                synchronized(noArtwork) { noArtwork.add(trackId) }
            }
            bitmap
        } catch (e: Exception) {
            synchronized(noArtwork) { noArtwork.add(trackId) }
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Nothing useful to do.
            }
        }
    }

    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE_PX && bounds.outHeight / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
