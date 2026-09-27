package com.nothing.one.ui.screen.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.data.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    entryRepository: EntryRepository,
) : ViewModel() {

    val moodSeries: StateFlow<List<Pair<Long, Int>>> = entryRepository.observeMoodSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val moodCounts: StateFlow<Map<Int, Int>> = entryRepository.observeMoodCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val journalDays: StateFlow<List<Long>> = entryRepository.observeJournalDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topTags: StateFlow<List<Pair<String, Int>>> = entryRepository.observeAllNoteTags()
        .map { tags -> tags.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
