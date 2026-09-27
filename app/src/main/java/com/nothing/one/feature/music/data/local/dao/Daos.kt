package com.nothing.one.feature.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nothing.one.feature.music.data.local.entity.PlaylistEntity
import com.nothing.one.feature.music.data.local.entity.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    data class PlaylistTrackCount(
        val playlistId: Long,
        val trackCount: Int,
    )

    @Query("SELECT playlistId, COUNT(*) AS trackCount FROM playlist_tracks GROUP BY playlistId")
    fun observePlaylistTrackCounts(): Flow<List<PlaylistTrackCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeTracksInPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getTracksInPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun countTracks(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePosition(playlistId: Long, trackId: Long, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}

@Dao
interface ScannedFolderDao {
    @Query("SELECT * FROM scanned_folders ORDER BY addedAt")
    fun observeFolders(): Flow<List<com.nothing.one.feature.music.data.local.entity.ScannedFolderEntity>>

    @Query("SELECT path FROM scanned_folders")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: com.nothing.one.feature.music.data.local.entity.ScannedFolderEntity)

    @Query("DELETE FROM scanned_folders WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT COUNT(*) FROM scanned_folders")
    suspend fun count(): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT trackId FROM favorites ORDER BY addedAt DESC")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Query("SELECT trackId FROM favorites")
    suspend fun getFavoriteIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: com.nothing.one.feature.music.data.local.entity.FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<com.nothing.one.feature.music.data.local.entity.SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: com.nothing.one.feature.music.data.local.entity.SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}