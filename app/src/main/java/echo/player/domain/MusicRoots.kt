package echo.player.domain

/**
 * A folder the user chose to index. Identity is the storage volume plus the folder path below it, which
 * is exactly what MediaStore reports for every audio file (VOLUME_NAME + RELATIVE_PATH).
 * [treeUri] is the Storage Access Framework tree the user picked; it is null for the built-in Music default.
 */
data class MusicRoot(
    val volumeId: String,
    val relativePath: String,
    val treeUri: String? = null,
) {
    val key: String get() = volumeId.lowercase() + ":" + relativePath.lowercase()
    val isDefault: Boolean get() = treeUri == null
    val displayPath: String get() = "/$relativePath"
    val isAnyVolume: Boolean get() = volumeId == MusicRoots.ANY_VOLUME
}

enum class MusicRootStatus {
    AVAILABLE,

    /** ECHO no longer holds the persisted read grant for the picked folder. */
    PERMISSION_REVOKED,

    /** The folder cannot be reached any more (deleted, moved, storage removed). */
    UNAVAILABLE,
}

sealed interface AddRootResult {
    data class Added(val roots: List<MusicRoot>) : AddRootResult
    data object Duplicate : AddRootResult
}

/** Platform-independent rules for which tracks belong to the user's chosen music folders. */
object MusicRoots {
    const val PRIMARY_VOLUME = "primary"

    /** Matches a folder on any storage volume (internal storage or an SD card). */
    const val ANY_VOLUME = "*"

    /**
     * The standard "Music" folder. Android keeps one on every storage volume, and on many phones the
     * music is on the SD card, so the default covers Music on all of them. It needs no folder-picker grant.
     * Folders the user picks themselves stay tied to the volume they were picked on.
     */
    val DEFAULT_MUSIC = MusicRoot(ANY_VOLUME, "Music", treeUri = null)

    fun normalizePath(path: String): String = path.replace('\\', '/').trim().trim('/')

    /** MediaStore calls primary storage "external_primary"; SD cards use their lower-case volume id. */
    fun normalizeVolume(volume: String?): String {
        val v = volume?.trim().orEmpty()
        if (v == ANY_VOLUME) return ANY_VOLUME
        return if (v.isEmpty() || v.equals("external_primary", true) || v.equals("external", true) || v.equals(PRIMARY_VOLUME, true)) {
            PRIMARY_VOLUME
        } else {
            v.lowercase()
        }
    }

    /** "primary:Music/Rock" (a SAF tree document id) -> root on primary storage at Music/Rock. */
    fun fromTreeDocumentId(documentId: String, treeUri: String?): MusicRoot? {
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        return MusicRoot(
            volumeId = normalizeVolume(documentId.substring(0, colon)),
            relativePath = normalizePath(documentId.substring(colon + 1)),
            treeUri = treeUri,
        )
    }

    /**
     * Volume and containing folder for a legacy file path ("/storage/emulated/0/Music/A/x.mp3"), used
     * on Android 9 and older where MediaStore has no RELATIVE_PATH column.
     */
    fun fromDataPath(path: String): Pair<String, String>? {
        val clean = path.replace('\\', '/')
        val relativeFile = PRIMARY_PATTERNS.firstNotNullOfOrNull { it.matchEntire(clean)?.groupValues?.get(1) }
        if (relativeFile != null) return PRIMARY_VOLUME to normalizePath(relativeFile.substringBeforeLast('/', ""))
        val removable = REMOVABLE_PATTERN.matchEntire(clean) ?: return null
        val (volume, rest) = removable.destructured
        return normalizeVolume(volume) to normalizePath(rest.substringBeforeLast('/', ""))
    }

    /** True when a track in [relativePath] on [volumeId] lies inside any chosen root (folder boundaries respected). */
    fun accepts(roots: List<MusicRoot>, volumeId: String, relativePath: String): Boolean {
        val volume = normalizeVolume(volumeId)
        val path = normalizePath(relativePath).lowercase()
        return roots.any { root ->
            val rootPath = normalizePath(root.relativePath).lowercase()
            (root.volumeId == ANY_VOLUME || normalizeVolume(root.volumeId) == volume) &&
                (rootPath.isEmpty() || path == rootPath || path.startsWith("$rootPath/"))
        }
    }

    fun add(existing: List<MusicRoot>, root: MusicRoot): AddRootResult =
        if (existing.any { it.key == root.key }) AddRootResult.Duplicate else AddRootResult.Added(existing + root)

    fun remove(existing: List<MusicRoot>, root: MusicRoot): List<MusicRoot> =
        existing.filterNot { it.key == root.key }

    fun statusOf(root: MusicRoot, hasPersistedGrant: Boolean, folderExists: Boolean): MusicRootStatus = when {
        root.isDefault -> MusicRootStatus.AVAILABLE
        !hasPersistedGrant -> MusicRootStatus.PERMISSION_REVOKED
        !folderExists -> MusicRootStatus.UNAVAILABLE
        else -> MusicRootStatus.AVAILABLE
    }

    private const val FIELD_SEPARATOR = '\t'

    fun encode(roots: List<MusicRoot>): String =
        roots.joinToString("\n") { listOf(it.volumeId, it.relativePath, it.treeUri.orEmpty()).joinToString(FIELD_SEPARATOR.toString()) }

    fun decode(encoded: String?): List<MusicRoot> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.lines().mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 2 || parts[0].isBlank()) {
                null
            } else {
                MusicRoot(normalizeVolume(parts[0]), normalizePath(parts[1]), parts.getOrNull(2)?.takeIf { it.isNotEmpty() })
            }
        }.distinctBy { it.key }
    }

    private val PRIMARY_PATTERNS = listOf(
        Regex("^/storage/emulated/\\d+/(.+)$"),
        Regex("^/sdcard/(.+)$"),
        Regex("^/storage/self/primary/(.+)$"),
    )
    private val REMOVABLE_PATTERN = Regex("^/storage/([^/]+)/(.+)$")
}
