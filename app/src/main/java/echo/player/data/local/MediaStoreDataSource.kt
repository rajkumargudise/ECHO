package echo.player.data.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import echo.player.domain.MusicRoots
import echo.player.domain.TrackSource
import echo.player.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of [TrackSource]: reads the audio index that MediaStore already maintains.
 * It never crawls the filesystem. Each track carries its storage volume and folder so the library can
 * keep only the tracks inside folders the user chose.
 */
class MediaStoreDataSource(private val context: Context) : TrackSource {
    override suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (hasRelativePath) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            } else {
                // Android 9 and older have no RELATIVE_PATH; the legacy file path carries the same information.
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val columns = Columns(cursor, hasRelativePath)
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor, columns))
            }
        }
        result
    }

    private class Columns(cursor: Cursor, hasRelativePath: Boolean) {
        val id = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val displayName = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
        val duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val trackNumber = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val year = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val mimeType = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val size = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dateAdded = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val relativePath = if (hasRelativePath) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
        val volumeName = if (hasRelativePath) cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME) else -1

        @Suppress("DEPRECATION")
        val data = if (hasRelativePath) -1 else cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
    }

    private fun mapRow(cursor: Cursor, c: Columns): Track {
        val id = cursor.getLong(c.id)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val displayName = cursor.safeString(c.displayName)
        val title = Track.cleanMetadata(cursor.safeString(c.title), displayName?.stripExtension() ?: "Unknown Title")
        val artist = Track.cleanMetadata(cursor.safeString(c.artist), Track.UNKNOWN_ARTIST)
        val album = Track.cleanMetadata(cursor.safeString(c.album), Track.UNKNOWN_ALBUM)
        val albumArtist = Track.cleanMetadata(cursor.safeString(c.albumArtist), artist)

        val storeRelativePath = cursor.safeString(c.relativePath)?.takeIf { it.isNotBlank() }
        val legacy = if (storeRelativePath == null) cursor.safeString(c.data)?.let(MusicRoots::fromDataPath) else null
        val volumeId = when {
            storeRelativePath != null -> MusicRoots.normalizeVolume(cursor.safeString(c.volumeName))
            legacy != null -> legacy.first
            else -> MusicRoots.PRIMARY_VOLUME
        }
        val relativePath = storeRelativePath ?: legacy?.second?.let { if (it.isEmpty()) "" else "$it/" } ?: ""

        return Track(
            id = uri.toString(),
            uri = uri,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            durationMs = if (c.duration >= 0) cursor.getLong(c.duration) else 0L,
            trackNumber = if (c.trackNumber >= 0) cursor.getInt(c.trackNumber).takeIf { it > 0 } else null,
            year = if (c.year >= 0) cursor.getInt(c.year).takeIf { it > 0 } else null,
            mimeType = cursor.safeString(c.mimeType)?.takeIf { it.isNotBlank() },
            size = if (c.size >= 0) cursor.getLong(c.size) else 0L,
            relativePath = relativePath,
            volumeId = volumeId,
            dateAdded = if (c.dateAdded >= 0) cursor.getLong(c.dateAdded) else 0L,
            isAvailable = true,
        )
    }

    private fun Cursor.safeString(columnIndex: Int): String? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null

    private fun String.stripExtension(): String {
        val withoutExt = substringBeforeLast('.', missingDelimiterValue = this)
        return withoutExt.ifBlank { this }
    }
}
