package echo.player.domain.model

import android.net.Uri

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val mimeType: String? = null,
    val size: Long = 0L,
    val relativePath: String = "",
    /** Normalized storage volume ("primary" or an SD card id); with [relativePath] it decides folder membership. */
    val volumeId: String = "primary",
    val dateAdded: Long = 0L,
    val isAvailable: Boolean = true,
    val isFavorite: Boolean = false,
) {
    companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
        private const val MEDIASTORE_UNKNOWN = "<unknown>"

        /** MediaStore reports missing tags as the literal "<unknown>", not null; treat it like blank. */
        fun cleanMetadata(value: String?, fallback: String): String {
            val trimmed = value?.trim()
            return if (trimmed.isNullOrEmpty() || trimmed.equals(MEDIASTORE_UNKNOWN, ignoreCase = true)) {
                fallback
            } else {
                trimmed
            }
        }

        fun fallbackTitle(displayName: String?, title: String?): String {
            return title?.takeIf { it.isNotBlank() }
                ?: displayName?.substringBeforeLast('.', missingDelimiterValue = displayName)?.ifBlank { displayName }
                ?: "Unknown Title"
        }

        fun matchesQuery(track: Track, query: String): Boolean = echo.player.domain.LocalSearch.matches(track, query)

        fun matchesQuery(title: String?, artist: String?, album: String?, query: String): Boolean {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) return true
            val haystack = listOf(title, artist, album)
                .joinToString(" ")
                .lowercase()
            return haystack.contains(normalizedQuery.lowercase())
        }
    }
}
