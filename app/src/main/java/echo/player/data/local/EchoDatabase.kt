package echo.player.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import android.content.Context
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val trackId: String,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = 0L,
)

/** A search the user ran in Discover. One row per normalized query; running it again moves it to the top. */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val queryKey: String,
    val displayQuery: String,
    val lastUsedAt: Long,
)

/**
 * Something that was played, local or online, identified by source + stable id. No stream address is ever stored:
 * an online track is resolved again from its id when it is replayed.
 */
@Entity(tableName = "recent_plays", primaryKeys = ["sourceId", "sourceTrackId"])
data class RecentPlayEntity(
    val sourceId: String,
    val sourceTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val lastPlayedAt: Long,
    val positionMs: Long,
    val origin: String,
    @ColumnInfo(defaultValue = "UNKNOWN") val language: String = "UNKNOWN",
)

@Dao
interface EchoDao : HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(track: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT trackId FROM favorite_tracks")
    suspend fun favoriteTrackIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY created_at DESC")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(track: PlaylistTrackEntity)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun playlistTracks(playlistId: Long): List<PlaylistTrackEntity>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT * FROM play_history WHERE trackId = :trackId LIMIT 1")
    suspend fun playHistory(trackId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayHistory(entry: PlayHistoryEntity)
}

/** The history queries, split out so the history logic can be tested against an in-memory fake. */
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(entry: RecentSearchEntity)

    @Query("SELECT * FROM recent_searches ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recentSearches(limit: Int): List<RecentSearchEntity>

    @Query("DELETE FROM recent_searches WHERE queryKey NOT IN (SELECT queryKey FROM recent_searches ORDER BY lastUsedAt DESC LIMIT :limit)")
    suspend fun trimRecentSearches(limit: Int)

    @Query("DELETE FROM recent_searches WHERE queryKey = :queryKey")
    suspend fun removeRecentSearch(queryKey: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentPlay(entry: RecentPlayEntity)

    @Query("SELECT * FROM recent_plays ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun recentPlays(limit: Int): List<RecentPlayEntity>

    @Query("UPDATE recent_plays SET positionMs = :positionMs WHERE sourceId = :sourceId AND sourceTrackId = :sourceTrackId")
    suspend fun updateRecentPlayPosition(sourceId: String, sourceTrackId: String, positionMs: Long)

    @Query("DELETE FROM recent_plays WHERE sourceId = :sourceId AND sourceTrackId = :sourceTrackId")
    suspend fun removeRecentPlay(sourceId: String, sourceTrackId: String)

    @Query("DELETE FROM recent_plays WHERE sourceId || ':' || sourceTrackId NOT IN (SELECT sourceId || ':' || sourceTrackId FROM recent_plays ORDER BY lastPlayedAt DESC LIMIT :limit)")
    suspend fun trimRecentPlays(limit: Int)

    @Query("DELETE FROM recent_plays")
    suspend fun clearRecentPlays()
}

/** Version 1 -> 2: adds the two history tables. Existing favorites, playlists and play counts are untouched. */
object EchoMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (sql in MIGRATION_1_2_SQL) db.execSQL(sql)
        }
    }

    /** Version 2 -> 3: remembers an online track's language in history. Existing rows become UNKNOWN. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (sql in MIGRATION_2_3_SQL) db.execSQL(sql)
        }
    }

    val MIGRATION_2_3_SQL = listOf("ALTER TABLE `recent_plays` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'UNKNOWN'")

    val MIGRATION_1_2_SQL = listOf(
        "CREATE TABLE IF NOT EXISTS `recent_searches` (`queryKey` TEXT NOT NULL, `displayQuery` TEXT NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`queryKey`))",
        "CREATE TABLE IF NOT EXISTS `recent_plays` (`sourceId` TEXT NOT NULL, `sourceTrackId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `artworkUrl` TEXT, `lastPlayedAt` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `origin` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `sourceTrackId`))",
    )
}

@Database(
    entities = [
        FavoriteTrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, PlayHistoryEntity::class,
        RecentSearchEntity::class, RecentPlayEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun echoDao(): EchoDao

    companion object {
        @Volatile
        private var INSTANCE: EchoDatabase? = null

        fun getInstance(context: Context): EchoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EchoDatabase::class.java,
                    "echo_local.db",
                ).addMigrations(EchoMigrations.MIGRATION_1_2, EchoMigrations.MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

