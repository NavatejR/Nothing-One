package com.nothing.one.feature.music.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.feature.music.domain.model.Album
import com.nothing.one.feature.music.domain.model.Artist
import com.nothing.one.feature.music.domain.model.Folder
import com.nothing.one.feature.music.domain.model.Playlist
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {

    val tracks: StateFlow<List<Track>> = repository.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<Artist>> = repository.artists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasScannedFolders: StateFlow<Boolean> = folders
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<List<Long>> = repository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteTracks: StateFlow<List<Track>> = combine(tracks, favoriteIds) { all, favs ->
        val favSet = favs.toSet()
        all.filter { it.id in favSet }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            repository.refreshLibrary(repository.getAllScannedFolderPaths())
        }
    }

    fun ensureScanned() {
        viewModelScope.launch {
            if (repository.getAllScannedFolderPaths().isNotEmpty()) {
                repository.refreshLibrary(repository.getAllScannedFolderPaths())
            }
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, trackIds)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(id, newName)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    private val playlistTrackFlows = mutableMapOf<Long, StateFlow<List<Track>>>()

    fun tracksFor(playlistId: Long): StateFlow<List<Track>> =
        playlistTrackFlows.getOrPut(playlistId) {
            combine(tracks, repository.observePlaylistTrackIds(playlistId)) { all, ids ->
                val byId = all.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }
}