package com.nothing.one.feature.music.data.repository

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import com.nothing.one.feature.music.data.local.dao.FavoriteDao
import com.nothing.one.feature.music.data.local.dao.PlaylistDao
import com.nothing.one.feature.music.data.local.dao.ScannedFolderDao
import com.nothing.one.feature.music.data.local.dao.SearchHistoryDao
import com.nothing.one.feature.music.data.local.entity.PlaylistEntity
import com.nothing.one.feature.music.data.local.entity.PlaylistTrackEntity
import com.nothing.one.feature.music.data.local.entity.ScannedFolderEntity
import com.nothing.one.feature.music.data.scanner.AudioScanner
import com.nothing.one.feature.music.domain.model.Album
import com.nothing.one.feature.music.domain.model.Artist
import com.nothing.one.feature.music.domain.model.Folder
import com.nothing.one.feature.music.domain.model.Lyrics
import com.nothing.one.feature.music.domain.model.Playlist
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.domain.repository.MusicRepository
import com.nothing.one.feature.music.data.lyrics.LyricsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: ContextLike,
    private val scanner: AudioScanner,
    private val playlistDao: PlaylistDao,
    private val scannedFolderDao: ScannedFolderDao,
    private val favoriteDao: FavoriteDao,
) : MusicRepository {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks

    override val favoriteIds: StateFlow<List<Long>> =
        favoriteDao.observeFavoriteIds()
            .stateIn(scannerScope(), SharingStarted.WhileSubscribed(5_000), emptyList())

    override val albums: StateFlow<List<Album>> =
        tracks.map { list ->
            list.groupBy { it.album to it.artist }
                .map { (key, groupTracks) ->
                    Album(
                        name = key.first,
                        artist = key.second,
                        albumArtUri = groupTracks.firstNotNullOfOrNull { it.albumArtUri?.toString() },
                        trackCount = groupTracks.size,
                        year = groupTracks.firstOrNull()?.year ?: 0,
                        durationMs = groupTracks.sumOf { it.durationMs },
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.stateIn(scannerScope(), SharingStarted.WhileSubscribed(5_000), emptyList())

    override val artists: StateFlow<List<Artist>> =
        tracks.map { list ->
            list.groupBy { it.artist }
                .map { (name, groupTracks) ->
                    Artist(
                        name = name,
                        albumCount = groupTracks.map { it.album }.distinct().size,
                        trackCount = groupTracks.size,
                        avatarUri = groupTracks.firstNotNullOfOrNull { it.albumArtUri?.toString() },
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.stateIn(scannerScope(), SharingStarted.WhileSubscribed(5_000), emptyList())

    override val folders: StateFlow<List<Folder>> =
        combine(tracks, scannedFolderDao.observeFolders()) { allTracks, scanned ->
            scanned.map { entity ->
                val folderTracks = allTracks.filter { it.folderPath.startsWith(entity.path) }
                Folder(
                    path = entity.path,
                    name = entity.path.substringAfterLast('/'),
                    trackCount = folderTracks.size,
                    isSelected = true,
                )
            }.sortedBy { it.name.lowercase() }
        }.stateIn(scannerScope(), SharingStarted.WhileSubscribed(5_000), emptyList())

    override val playlists: StateFlow<List<Playlist>> =
        combine(
            playlistDao.observePlaylists(),
            playlistDao.observePlaylistTrackCounts(),
        ) { playlists, counts ->
            playlists.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    trackCount = counts.firstOrNull { it.playlistId == entity.id }?.trackCount ?: 0,
                    createdAt = entity.createdAt,
                )
            }
        }.stateIn(scannerScope(), SharingStarted.WhileSubscribed(5_000), emptyList())

    override suspend fun refreshLibrary(scannedFolders: List<String>) {
        val scanned = scannedFolders.ifEmpty { getAllScannedFolderPaths() }
        val result = scanner.scanAllMusic(scanned)
        _tracks.value = result
    }

    override suspend fun getAllScannedFolderPaths(): List<String> = scannedFolderDao.getAllPaths()

    override suspend fun getScannedFolders(): List<String> = scannedFolderDao.getAllPaths()

    override suspend fun addScannedFolder(path: String) {
        scannedFolderDao.insert(ScannedFolderEntity(path = path))
        refreshLibrary(getAllScannedFolderPaths())
    }

    override suspend fun removeScannedFolder(path: String) {
        scannedFolderDao.delete(path)
        refreshLibrary(getAllScannedFolderPaths())
    }

    override suspend fun getAllTracks(): List<Track> = _tracks.value

    override suspend fun trackById(id: Long): Track? = _tracks.value.firstOrNull { it.id == id }

    override suspend fun tracksByIds(ids: List<Long>): List<Track> {
        val byId = _tracks.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override fun observePlaylistTrackIds(playlistId: Long): Flow<List<Long>> =
        playlistDao.observeTracksInPlaylist(playlistId).map { list -> list.map { it.trackId } }

    override suspend fun toggleFavorite(trackId: Long) {
        val favorites = favoriteDao.getFavoriteIds()
        if (trackId in favorites) favoriteDao.delete(trackId)
        else favoriteDao.insert(com.nothing.one.feature.music.data.local.entity.FavoriteEntity(trackId = trackId))
    }

    override suspend fun isFavorite(trackId: Long): Boolean = trackId in favoriteDao.getFavoriteIds()

    override fun search(query: String): Flow<List<Track>> =
        tracks.map { list ->
            if (query.isBlank()) emptyList()
            else {
                val q = query.lowercase().trim()
                list.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.path.lowercase().contains(q)
                }
            }
        }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)

    override suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val existing = playlistDao.getTracksInPlaylist(playlistId).map { it.trackId }.toSet()
        val startPosition = playlistDao.countTracks(playlistId)
        val tracks = trackIds.filterNot { it in existing }
            .mapIndexed { index, trackId ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = startPosition + index,
                )
            }
        if (tracks.isNotEmpty()) playlistDao.insertTracks(tracks)
    }

    override suspend fun renamePlaylist(id: Long, newName: String) =
        playlistDao.renamePlaylist(id, newName)

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        playlistDao.removeTrack(playlistId, trackId)

    override suspend fun getLyrics(track: Track): Lyrics =
        LyricsParser.parse(track)

    private fun scannerScope() = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
}

private typealias ContextLike = android.content.Context