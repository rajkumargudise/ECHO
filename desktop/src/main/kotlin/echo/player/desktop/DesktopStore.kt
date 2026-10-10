package echo.player.desktop

import echo.player.domain.model.Playlist
import echo.player.domain.model.PlaylistEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Everything ECHO remembers between runs on a computer. It lives in one small file and never leaves the machine. */
data class DesktopData(
    val roots: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val showCompanion: Boolean = true,
    val folderTiles: Boolean = false,
    val welcomeSeen: Boolean = false,
    val visualizerMode: String? = null,
    val nextPlaylistId: Long = 1L,
)

/** Where ECHO keeps its files: AppData on Windows, the XDG data folder on Linux, a library folder on a Mac. */
object DesktopPaths {
    fun dataDir(
        env: Map<String, String> = System.getenv(),
        osName: String = System.getProperty("os.name").orEmpty(),
        home: String = System.getProperty("user.home"),
    ): File {
        val os = osName.lowercase()
        return when {
            "win" in os -> File(env["APPDATA"] ?: "$home/AppData/Roaming", "ECHO")
            "mac" in os -> File(home, "Library/Application Support/ECHO")
            else -> File(env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "echo")
        }
    }

    fun defaultMusicFolder(home: String = System.getProperty("user.home")): File = File(home, "Music")
}

class DesktopStore(private val dir: File) {
    private val file = File(dir, "echo-data.json")

    fun load(): DesktopData {
        if (!file.isFile) return DesktopData()
        return runCatching { decode(file.readText(Charsets.UTF_8)) }.getOrElse {
            // A damaged file must not stop ECHO from starting, and must not be lost before it can be inspected.
            runCatching { Files.copy(file.toPath(), File(dir, "echo-data.damaged.json").toPath(), StandardCopyOption.REPLACE_EXISTING) }
            DesktopData()
        }
    }

    fun save(data: DesktopData) {
        dir.mkdirs()
        val temp = File(dir, "echo-data.json.tmp")
        temp.writeText(encode(data), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        fun encode(data: DesktopData): String = JSONObject().apply {
            put("version", 1)
            put("roots", JSONArray(data.roots))
            put("favorites", JSONArray(data.favorites.toList()))
            put("showCompanion", data.showCompanion)
            put("folderTiles", data.folderTiles)
            put("welcomeSeen", data.welcomeSeen)
            put("visualizerMode", data.visualizerMode ?: JSONObject.NULL)
            put("nextPlaylistId", data.nextPlaylistId)
            put(
                "playlists",
                JSONArray().also { list ->
                    for (p in data.playlists) {
                        list.put(
                            JSONObject().apply {
                                put("id", p.id)
                                put("name", p.name)
                                put("createdAt", p.createdAt)
                                put(
                                    "entries",
                                    JSONArray().also { entries ->
                                        for (e in p.entries) {
                                            entries.put(
                                                JSONObject().apply {
                                                    put("trackId", e.trackId)
                                                    put("title", e.title)
                                                    put("artist", e.artist)
                                                    put("album", e.album)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }.toString(2)

        fun decode(text: String): DesktopData {
            val o = JSONObject(text)
            fun strings(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val playlists = o.optJSONArray("playlists")?.let { a ->
                List(a.length()) { i ->
                    val p = a.getJSONObject(i)
                    val entries = p.optJSONArray("entries")?.let { ea ->
                        List(ea.length()) { j ->
                            val e = ea.getJSONObject(j)
                            PlaylistEntry(e.getString("trackId"), e.optString("title"), e.optString("artist"), e.optString("album"))
                        }
                    } ?: emptyList()
                    Playlist(p.getLong("id"), p.getString("name"), entries.map { it.trackId }, p.optLong("createdAt"), entries)
                }
            } ?: emptyList()
            return DesktopData(
                roots = strings("roots"),
                favorites = strings("favorites").toSet(),
                playlists = playlists,
                showCompanion = o.optBoolean("showCompanion", true),
                folderTiles = o.optBoolean("folderTiles", false),
                welcomeSeen = o.optBoolean("welcomeSeen", false),
                visualizerMode = if (o.isNull("visualizerMode")) null else o.optString("visualizerMode"),
                nextPlaylistId = o.optLong("nextPlaylistId", 1L).coerceAtLeast((playlists.maxOfOrNull { it.id } ?: 0L) + 1),
            )
        }
    }
}
