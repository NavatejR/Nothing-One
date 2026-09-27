package com.nothing.one.feature.music.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.feature.music.domain.model.Lyrics
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _lyrics = MutableStateFlow(Lyrics.Empty)
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadedTrackId: Long = -1L

    fun loadForTrack(track: Track?) {
        val t = track ?: return
        if (t.id == loadedTrackId) return
        loadedTrackId = t.id
        _loading.value = true
        viewModelScope.launch {
            _lyrics.value = repository.getLyrics(t)
            _loading.value = false
        }
    }
}