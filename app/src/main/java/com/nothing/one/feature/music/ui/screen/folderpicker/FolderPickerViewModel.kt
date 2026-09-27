package com.nothing.one.feature.music.ui.screen.folderpicker

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nothing.one.feature.music.data.scanner.AudioScanner
import com.nothing.one.feature.music.domain.model.Folder
import com.nothing.one.feature.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val scanner: AudioScanner,
) : ViewModel() {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _scannedPaths = MutableStateFlow<Set<String>>(emptySet())
    val scannedPaths: StateFlow<Set<String>> = _scannedPaths.asStateFlow()

    init {
        viewModelScope.launch {
            _scannedPaths.value = repository.getScannedFolders().toSet()
            scanDirectories()
        }
    }

    fun scanDirectories() {
        _loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val dirs = discoverAudioFolders(
                root = Environment.getExternalStorageDirectory(),
                maxDepth = 3,
                currentDepth = 0,
            )
            val sorted = dirs
                .sortedBy { it.name.lowercase() }
                .map {
                    Folder(
                        path = it.path,
                        name = it.name,
                        trackCount = countAudioFiles(it),
                        isSelected = it.path in _scannedPaths.value,
                    )
                }
            _folders.value = sorted
            _loading.value = false
        }
    }

    private val skipDirs = setOf(
        "android", "android/data", "android/obb", ".thumbnails", ".trashed",
        "alarms", "notifications", "ringtones", "downloads", "cache", "dcim",
        "pictures", "movies", "recording", "recordings", "crashpad",
    )

    private fun discoverAudioFolders(
        root: File,
        maxDepth: Int,
        currentDepth: Int,
    ): List<File> {
        if (currentDepth > maxDepth || !root.exists() || !root.canRead()) return emptyList()
        val result = mutableListOf<File>()
        val children = runCatching { root.listFiles() }.getOrNull() ?: return result

        var hasAudio = false
        val subDirs = mutableListOf<File>()

        for (child in children) {
            if (child.isDirectory) {
                val lowerName = child.name.lowercase()
                if (child.isHidden || skipDirs.contains(lowerName) ||
                    lowerName.startsWith(".") || !child.canRead()
                ) continue
                subDirs += child
            } else if (child.isFile && scanner.isSupportedFileSafely(child)) {
                hasAudio = true
            }
        }

        if (hasAudio) result += root
        subDirs.forEach { result += discoverAudioFolders(it, maxDepth, currentDepth + 1) }
        return result
    }

    private fun countAudioFiles(dir: File): Int {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        return children.count { it.isFile && scanner.isSupportedFileSafely(it) }
    }

    fun toggleFolder(path: String) {
        viewModelScope.launch {
            if (path in _scannedPaths.value) {
                repository.removeScannedFolder(path)
            } else {
                repository.addScannedFolder(path)
            }
            _scannedPaths.value = repository.getScannedFolders().toSet()
            _folders.value = _folders.value.map {
                if (it.path == path) it.copy(isSelected = path in _scannedPaths.value) else it
            }
        }
    }

    fun isFolderSelected(path: String): Boolean = path in _scannedPaths.value
}