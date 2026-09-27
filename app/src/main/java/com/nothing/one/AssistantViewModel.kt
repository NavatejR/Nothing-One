package com.nothing.one

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.ai.AiAvailability
import com.nothing.one.ai.AiResult
import com.nothing.one.ai.AiTasks
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.Settings
import com.nothing.one.data.SettingsRepository
import com.nothing.one.data.model.EntrySource
import com.nothing.one.speech.AssistantState
import com.nothing.one.speech.DictationSink
import com.nothing.one.speech.SpeechManager
import com.nothing.one.ui.orb.OrbState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One assistant brain shared by every screen with an orb. Holds the speech
 * manager's state plus the orb's orbkit state and mic volume, so the bubble
 * and the full-screen orb always agree.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    val speech: SpeechManager,
    val aiAvailability: AiAvailability,
    private val aiTasks: AiTasks,
    private val entryRepository: EntryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** The orbkit state the orb should render right now. */
    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    /** Live partial dictation text, for the caption under the orb. */
    val partialText: StateFlow<String> = speech.partialText

    /** Final dictation result, cleared after the caller consumes it. */
    val finalResultText: StateFlow<String> = speech.finalResult

    val lastError: StateFlow<String?> = speech.lastError

    val reducedMotion: StateFlow<Boolean> = settingsRepository.settings
        .map { it.reducedMotion }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** First-launch flag resolved once; drives the start route. */
    val startRoute: StateFlow<String?> = settingsRepository.settings
        .map { if (it.onboarded) Routes.NOTES else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Mic level while dictating, null otherwise — feeds the orb's uInput. */
    val micLevel: StateFlow<Float?> = speech.pinnedMicLevel

    init {
        aiAvailability.start()
        // Mirror the speech manager's low-level state into the orb state.
        viewModelScope.launch {
            speech.state.collect { assistant ->
                _orbState.value = when (assistant) {
                    AssistantState.SLEEPING -> OrbState.SLEEPING
                    AssistantState.LISTENING -> OrbState.LISTENING
                    AssistantState.THINKING -> OrbState.THINKING
                    AssistantState.SPEAKING -> OrbState.SPEAKING
                    AssistantState.IDLE -> OrbState.IDLE
                }
            }
        }
        // Route every finished dictation to wherever it was started from.
        // HOME_NOTE auto-creates the note (stop == save); JOURNAL_APPEND
        // appends to today's page. ASSISTANT_REVIEW and EDITOR_CURSOR keep
        // their own in-screen flows and are left alone here.
        viewModelScope.launch {
            speech.finalResult.collect { text ->
                if (text.isBlank()) return@collect
                when (speech.sink.value) {
                    DictationSink.HOME_NOTE -> {
                        entryRepository.upsertNote(
                            id = null,
                            title = text.take(48).trim(),
                            body = text,
                            tags = emptyList(),
                            source = EntrySource.DICTATED,
                        )
                        _savedFeedback.value = "NOTE SAVED"
                        speech.clearFinalResult()
                    }
                    DictationSink.JOURNAL_APPEND -> {
                        entryRepository.appendToToday(text, EntrySource.ASSISTANT)
                        _savedFeedback.value = "ADDED TO TODAY"
                        speech.clearFinalResult()
                    }
                    DictationSink.ASSISTANT_REVIEW, DictationSink.EDITOR_CURSOR -> Unit
                }
            }
        }
    }

    /** Starts dictation and routes its result to [sink] when it finishes. */
    fun startListening(sink: DictationSink) {
        speech.sink.value = sink
        speech.startListening()
    }

    fun startListening() = speech.startListening()

    fun stopListening() = speech.stopListening()

    /** Cancels the running dictation and discards its transcript. */
    fun cancelListening() = speech.cancelListening()

    fun speak(text: String) = speech.speak(text)

    fun consumeError() = speech.consumeError()

    /** Brief confirmation after a dictation auto-saves ("NOTE SAVED"). */
    private val _savedFeedback = MutableStateFlow<String?>(null)
    val savedFeedback: StateFlow<String?> = _savedFeedback.asStateFlow()

    fun consumeSavedFeedback() {
        _savedFeedback.value = null
    }

    /** Saves dictated text as a quick note. */
    fun saveDictationAsNote() {
        val text = speech.finalResult.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            entryRepository.upsertNote(
                id = null,
                title = text.take(48).trim(),
                body = text,
                tags = emptyList(),
                source = EntrySource.DICTATED,
            )
        }
    }

    /** Appends dictated text to today's journal page. */
    fun appendDictationToJournal() {
        val text = speech.finalResult.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            entryRepository.appendToToday(text, EntrySource.ASSISTANT)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.completeOnboarding() }
    }

    /** Summarizes the last dictation and speaks it back through the orb. */
    fun summarizeAloud() {
        val text = speech.finalResult.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                when (val result = aiTasks.summarize(text)) {
                    is AiResult.Success -> {
                        _summary.value = result.value
                        speech.speak(result.value)
                    }
                    is AiResult.Unavailable -> {
                        _summary.value = result.fallback
                        speech.speak(result.fallback)
                    }
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()
}
