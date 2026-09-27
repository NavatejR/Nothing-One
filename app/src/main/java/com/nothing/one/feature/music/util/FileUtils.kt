package com.nothing.one.feature.music.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object FileUtils {

    fun isSupportedAudioFile(file: File): Boolean =
        Constants.SUPPORTED_AUDIO_EXTENSIONS.contains(file.extension.lowercase(Locale.ROOT))

    fun isSupportedAudioUri(context: Context, uri: Uri): Boolean {
        context.contentResolver.getType(uri)?.let { mime ->
            if (Constants.SUPPORTED_MIME_TYPES.contains(mime)) return true
        }
        val name = uri.lastPathSegment?.lowercase(Locale.ROOT).orEmpty()
        return Constants.SUPPORTED_AUDIO_EXTENSIONS.any { name.endsWith(".$it") }
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }

    fun getParentFolderPath(path: String): String {
        val file = File(path)
        return file.parent ?: File.separator
    }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
        else -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
    }

    fun readableDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}