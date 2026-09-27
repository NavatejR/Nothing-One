package com.nothing.one.ui.screen.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.ai.AiAvailability
import com.nothing.one.ai.AiResult
import com.nothing.one.ai.AiTasks
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.local.EntryEntity
import com.nothing.one.data.model.Mood
import com.nothing.one.speech.DictationSink
import com.nothing.one.speech.SpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val aiTasks: AiTasks,
    aiAvailability: AiAvailability,
    private val speech: SpeechManager,
) : ViewModel() {

    private val _selectedDay = MutableStateFlow(EntryRepository.todayIndex())
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    val entry: StateFlow<EntryEntity?> = _selectedDay
        .flatMapLatest { entryRepository.observeJournalByDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Mood per journal day for the calendar strip. */
    val moodByDay: StateFlow<Map<Long, Int>> = entryRepository.observeRecentJournal(60)
        .map { list -> list.mapNotNull { e -> e.dayIndex?.let { it to (e.mood ?: 0) } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Recent journal entries with moods, oldest first — the insights trend. */
    val moodSeries: StateFlow<List<Pair<Long, Int>>> = entryRepository.observeMoodSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One row of the RECENT DAYS board on the journal page. */
    data class DayPreview(val dayIndex: Long, val moodValue: Int, val preview: String)

    /** Last two weeks: day, mood and the entry's first line. */
    val recentDays: StateFlow<List<DayPreview>> = entryRepository.observeRecentJournal(14)
        .map { list ->
            list.mapNotNull { e ->
                e.dayIndex?.let {
                    DayPreview(
                        dayIndex = it,
                        moodValue = e.mood ?: 0,
                        preview = e.body.lineSequence().firstOrNull { line -> line.isNotBlank() }
                            ?.take(64).orEmpty(),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Current journaling streak for the stats row. */
    val streak: StateFlow<Int> = entryRepository.observeJournalDays()
        .map { days ->
            com.nothing.one.ui.screen.home.HomeViewModel.computeStreak(
                days.toSet(),
                EntryRepository.todayIndex(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Total days ever journaled — the second stat on the page. */
    val totalDays: StateFlow<Int> = entryRepository.observeJournalDays()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _bodyText = MutableStateFlow("")
    val bodyText: StateFlow<String> = _bodyText.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    private val engineAvailable: StateFlow<Boolean> = aiAvailability.available

    private var saveJob: Job? = null

    init {
        // Mirror the loaded entry into the editable text.
        viewModelScope.launch {
            entry.collect { entity ->
                if (_bodyText.value != (entity?.body ?: "")) {
                    _bodyText.value = entity?.body ?: ""
                }
            }
        }
        // Dictated text routing to today's page is owned by AssistantViewModel
        // via the shared dictation sink (DictationSink.JOURNAL_APPEND).
    }

    fun selectDay(dayIndex: Long) {
        viewModelScope.launch { saveNow() }
        _selectedDay.value = dayIndex
    }

    fun onBodyChange(text: String) {
        _bodyText.value = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val text = _bodyText.value
        val today = _selectedDay.value == EntryRepository.todayIndex()
        if (today) {
            // Ensure the row exists, then write text.
            val e = entryRepository.todayEntry()
            entryRepository.saveJournalText(e.id, text)
        } else {
            entry.value.let { e -> e?.let { entryRepository.saveJournalText(it.id, text) } }
        }
    }

    fun setMood(mood: Mood?) {
        viewModelScope.launch {
            val e = if (_selectedDay.value == EntryRepository.todayIndex()) {
                entryRepository.todayEntry()
            } else {
                entry.value
            } ?: return@launch
            entryRepository.saveJournalMood(e.id, mood)
        }
    }

    /** AI digest of the day's text; also spoken back through the orb. */
    fun summarizeDay() {
        val text = _bodyText.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                val prompt = "Journal entry:\n$text"
                when (val result = aiTasks.summarize(prompt)) {
                    is AiResult.Success -> {
                        _aiMessage.value = result.value
                        val e = if (_selectedDay.value == EntryRepository.todayIndex()) {
                            entryRepository.todayEntry()
                        } else {
                            entry.value
                        }
                        e?.let { entryRepository.setJournalSummary(it.id, result.value) }
                        speech.speak(result.value)
                    }
                    is AiResult.Unavailable -> {
                        _aiMessage.value = "Bundled engine unavailable — try again in a moment"
                    }
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun inferMood() {
        val text = _bodyText.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                when (val result = aiTasks.inferMood(text)) {
                    is AiResult.Success -> {
                        setMood(result.value)
                        _aiMessage.value = "Mood set: ${result.value?.label ?: "—"}"
                    }
                    is AiResult.Unavailable -> {
                        _aiMessage.value = "Engine unavailable — pick a mood manually"
                    }
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun startDictation() {
        speech.sink.value = DictationSink.JOURNAL_APPEND
        speech.startListening()
    }

    fun stopDictation() = speech.stopListening()

    /** Cancels dictation and discards the transcript so far. */
    fun cancelDictation() = speech.cancelListening()

    fun consumeAiMessage() {
        _aiMessage.value = null
    }
}
