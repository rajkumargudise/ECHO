package echo.player.domain

import echo.player.domain.model.Track

/**
 * Where the library's tracks come from. On Android this is MediaStore; a desktop build would read the
 * local filesystem behind the same interface. Sources return every track they can see; deciding which
 * ones belong to the user's chosen folders is [MusicRoots]' job.
 */
interface TrackSource {
    suspend fun loadTracks(): List<Track>
}

/** Where the user's chosen music folders are remembered. */
interface MusicRootStore {
    fun load(): List<MusicRoot>
    fun save(roots: List<MusicRoot>)
}
