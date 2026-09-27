package com.nothing.one.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.ai.AiAvailability
import com.nothing.one.ai.AiResult
import com.nothing.one.ai.AiTasks
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.NoteTemplate
import com.nothing.one.data.NoteTemplates
import com.nothing.one.data.Tags
import com.nothing.one.data.local.EntryEntity
import com.nothing.one.data.model.EntrySource
import com.nothing.one.speech.AssistantState
import com.nothing.one.speech.DictationSink
import com.nothing.one.speech.SpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val aiTasks: AiTasks,
    private val speech: SpeechManager,
) : ViewModel() {

    private val _note = MutableStateFlow<EntryEntity?>(null)
    val note: StateFlow<EntryEntity?> = _note.asStateFlow()

    val title = MutableStateFlow("")
    val body = MutableStateFlow("")

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage.asStateFlow()

    /** Live dictation state so the editor can show the orb while listening. */
    val dictationState: StateFlow<AssistantState> = speech.state
    val partialText: StateFlow<String> = speech.partialText
    val micLevel: StateFlow<Float?> = speech.pinnedMicLevel

    /** Settled transcript of the last dictation (drives cursor insertion). */
    val finalResult: StateFlow<String> get() = speech.finalResult

    private var noteId: Long? = null
    private var autosaveJob: Job? = null
    private var source: EntrySource = EntrySource.TYPED

    fun load(id: Long?) {
        if (id == null) return
        viewModelScope.launch {
            val entity = entryRepository.byId(id) ?: return@launch
            noteId = entity.id
            source = entity.source
            _note.value = entity
            title.value = entity.title
            body.value = entity.body
            _tags.value = Tags.decode(entity.tagsJson)
            _pinned.value = entity.pinned
        }
    }

    /** Seeds a brand-new note from a home-screen template choice. */
    fun applyTemplate(template: NoteTemplate) {
        if (noteId != null || title.value.isNotBlank() || body.value.isNotBlank()) return
        title.value = template.title
        body.value = template.body
        source = EntrySource.TYPED
        onTextChanged()
    }

    /** Debounced autosave: fires 600ms after the last keystroke. */
    fun onTextChanged() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(600)
            save()
        }
    }

    fun togglePinned() {
        val entity = _note.value ?: return
        val next = !_pinned.value
        _pinned.value = next
        viewModelScope.launch { entryRepository.setPinned(entity.id, next) }
    }

    fun addTag(tag: String) {
        val t = tag.trim().lowercase().removePrefix("#")
        if (t.isEmpty() || t in _tags.value) return
        _tags.value = _tags.value + t
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch { save() }
    }

    fun removeTag(tag: String) {
        _tags.value = _tags.value - tag
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch { save() }
    }

    suspend fun save(): Long? {
        val id = entryRepository.upsertNote(
            id = noteId,
            title = title.value,
            body = body.value,
            tags = _tags.value,
            source = source,
            pinned = _pinned.value,
        )
        if (noteId == null && id > 0) {
            noteId = id
            _note.value = entryRepository.byId(id)
        }
        return id
    }

    /** Deletes the note if it exists; new unsaved notes simply go back. */
    fun delete(onDeleted: () -> Unit) {
        val entity = _note.value ?: run { onDeleted(); return }
        viewModelScope.launch {
            entryRepository.delete(entity.id)
            onDeleted()
        }
    }

    fun summarize() {
        val current = body.value.trim()
        if (current.isEmpty()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                val combined = listOf(title.value, current).filter { it.isNotBlank() }.joinToString("\n")
                val summary = when (val result = aiTasks.summarize(combined)) {
                    is AiResult.Success -> result.value
                    is AiResult.Unavailable -> result.fallback
                }
                _note.value?.let { entryRepository.setSummary(it.id, summary) }
                _aiMessage.value = summary
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun suggestTags() {
        val current = body.value.trim()
        if (current.isEmpty()) return
        viewModelScope.launch {
            _aiBusy.value = true
            try {
                val combined = listOf(title.value, current).filter { it.isNotBlank() }.joinToString("\n")
                val suggested = when (val result = aiTasks.suggestTags(combined)) {
                    is AiResult.Success -> result.value
                    is AiResult.Unavailable -> emptyList()
                }
                if (suggested.isEmpty()) {
                    _aiMessage.value = "Engine unavailable — add tags manually"
                } else {
                    _tags.value = (_tags.value + suggested).distinct().take(8)
                    save()
                }
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun startDictation() {
        speech.sink.value = DictationSink.EDITOR_CURSOR
        speech.startListening()
    }

    fun stopDictation() = speech.stopListening()

    /** Cancels dictation and discards the transcript so far. */
    fun cancelDictation() = speech.cancelListening()

    fun clearFinalResult() = speech.clearFinalResult()
    fun consumeAiMessage() {
        _aiMessage.value = null
    }
}
