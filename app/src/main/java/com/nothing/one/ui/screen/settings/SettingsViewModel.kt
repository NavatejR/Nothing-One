package com.nothing.one.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.ai.EngineState
import com.nothing.one.ai.OnDeviceAiClient
import com.nothing.one.data.EntryRepository
import com.nothing.one.data.SettingsRepository
import com.nothing.one.data.Tags
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val onDeviceAi: OnDeviceAiClient,
    private val entryRepository: EntryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val engineState: StateFlow<EngineState> = onDeviceAi.state

    private val _exportState = MutableStateFlow<String?>(null)
    val exportState: StateFlow<String?> = _exportState.asStateFlow()

    fun setReducedMotion(reduced: Boolean) {
        viewModelScope.launch { settingsRepository.setReducedMotion(reduced) }
    }

    /**
     * Exports everything to Markdown through SAF. No JSON variant — Markdown
     * is what a human actually reads when they leave an app behind.
     */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val md = withContext(Dispatchers.IO) { buildMarkdown() }
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(md.toByteArray())
                }
                _exportState.value = "Exported"
            } catch (e: Exception) {
                _exportState.value = "Export failed: ${e.message}"
            }
        }
    }

    private suspend fun buildMarkdown(): String = withContext(Dispatchers.IO) {
        val notes = entryRepository.observeNotes("").first()
        val journal = entryRepository.observeRecentJournal(Int.MAX_VALUE).first()
        buildString {
            appendLine("# Journal export")
            appendLine()
            appendLine("_Generated locally by Journal (NothingOS edition)._")
            appendLine()
            appendLine("## Notes")
            appendLine()
            notes.forEach { note ->
                appendLine("### ${note.title.ifBlank { "Untitled" }}")
                Tags.decode(note.tagsJson).takeIf { it.isNotEmpty() }
                    ?.let { appendLine("_${it.joinToString(", ")}_") }
                appendLine()
                appendLine(note.body)
                note.summary?.let { appendLine("> $it") }
                appendLine()
            }
            appendLine("## Journal")
            appendLine()
            journal.forEach { day ->
                val date = day.dayIndex?.let { LocalDate.ofEpochDay(it) } ?: return@forEach
                appendLine("### $date${day.mood?.let { " — mood $it" } ?: ""}")
                appendLine()
                appendLine(day.body)
                day.summary?.let { appendLine("> $it") }
                appendLine()
            }
        }
    }

    fun consumeExportState() {
        _exportState.value = null
    }
}
