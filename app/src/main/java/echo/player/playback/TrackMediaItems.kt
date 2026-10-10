package echo.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.core.net.toUri
import echo.player.domain.model.PlayableTrack
import echo.player.domain.model.RemoteTrack
import echo.player.domain.model.Track

/**
 * The mediaId is the track's MediaStore content URI string (Track.id), so two tracks with the same title
 * stay distinct and a playing item can always be matched back to the library.
 * The URI is also set as request metadata because the session service rebuilds items from it.
 */
@OptIn(UnstableApi::class)
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()

/**
 * An online track as a Media3 item. The mediaId carries source + track id only (see RemoteMediaId); the https stream
 * address is the playable URI for this session and is never persisted. Null when the track has no authorized stream.
 */
/** MediaMetadata extra carrying the ECHO language name of an online track (for history). */
const val EXTRA_LANGUAGE = "echo.language"

@OptIn(UnstableApi::class)
fun RemoteTrack.toMediaItem(): MediaItem? {
    val url = streamUrl?.takeIf { playable && it.startsWith("https://") } ?: return null
    val uri = url.toUri()
    return MediaItem.Builder()
        .setMediaId(PlayableTrack.Remote(this).mediaId)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album.ifBlank { null })
                .setAlbumArtist(albumArtist.ifBlank { null })
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setArtworkUri(artworkUrl?.toUri())
                .setExtras(android.os.Bundle().apply { putString(EXTRA_LANGUAGE, language.name) })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

fun PlayableTrack.toMediaItem(): MediaItem? = when (this) {
    is PlayableTrack.Local -> track.toMediaItem()
    is PlayableTrack.Remote -> track.toMediaItem()
}
