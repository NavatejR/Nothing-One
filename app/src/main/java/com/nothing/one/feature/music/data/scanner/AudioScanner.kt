package com.nothing.one.feature.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.nothing.one.feature.music.domain.model.Track
import com.nothing.one.feature.music.util.Constants
import com.nothing.one.feature.music.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun scanAllMusic(folderPaths: List<String>): List<Track> = withContext(Dispatchers.IO) {
        if (folderPaths.isEmpty()) return@withContext emptyList()
        val tracksFromMediaStore = scanMediaStore(folderPaths)
        val tracksFromFs = scanFileSystem(folderPaths)
        (tracksFromMediaStore + tracksFromFs)
            .distinctBy { it.path }
            .sortedBy { it.artist.lowercase() + it.title.lowercase() }
    }

    private fun scanMediaStore(folderPaths: List<String>): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
        )
        val folderClause = folderPaths.joinToString(" OR ") {
            "${MediaStore.Audio.Media.DATA} LIKE ?"
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.SIZE} > 0 AND ($folderClause)"
        val selectionArgs = folderPaths
            .map { it.trimEnd('/') + "/%" }
            .toTypedArray()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(5) ?: continue
                tracks += cursorToTrack(cursor, path)
            }
        }
        return tracks
    }

    private fun cursorToTrack(cursor: Cursor, path: String): Track {
        val id = cursor.getLong(0)
        val title = cursor.getString(1) ?: path.substringAfterLast('/').substringBeforeLast('.')
        val artist = cursor.getString(2) ?: "Unknown Artist"
        val album = cursor.getString(3) ?: "Unknown Album"
        val duration = cursor.getLong(4)
        val albumId = cursor.getLong(6)
        val dateAdded = cursor.getLong(7)
        val trackNumber = cursor.getInt(8)
        val year = cursor.getInt(9)
        val mimeType = cursor.getString(10)
        val size = cursor.getLong(11)
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            path = path,
            albumArtUri = FileUtils.getAlbumArtUri(albumId),
            folderPath = FileUtils.getParentFolderPath(path),
            dateAdded = dateAdded,
            trackNumber = trackNumber,
            year = year,
            mimeType = mimeType,
            sizeBytes = size,
        )
    }

    private fun scanFileSystem(folderPaths: List<String>): List<Track> {
        val tracks = mutableListOf<Track>()
        val visited = mutableSetOf<String>()
        for (rootPath in folderPaths) {
            val root = File(rootPath)
            if (!root.exists() || !root.canRead()) continue
            walkAudioFiles(root, visited).forEach { file ->
                tracks += fileToTrack(file)
            }
        }
        return tracks
    }

    private fun walkAudioFiles(dir: File, visited: MutableSet<String>): List<File> {
        val result = mutableListOf<File>()
        if (!visited.add(dir.absolutePath)) return result
        val children = dir.listFiles() ?: return result

        for (child in children) {
            if (child.isDirectory) {
                result += walkAudioFiles(child, visited)
            } else if (child.isFile && FileUtils.isSupportedAudioFile(child)) {
                result += child
            }
        }
        return result
    }

    fun isSupportedFileSafely(file: File): Boolean =
        FileUtils.isSupportedAudioFile(file)

    private fun fileToTrack(file: File): Track {
        val parsed = FileNameParser.parse(file.name)
        val probe = probeAudio(file)
        return Track(
            id = (file.absolutePath.hashCode().toLong() and Long.MAX_VALUE) or (1L shl 40),
            title = parsed.title,
            artist = parsed.artist ?: "Unknown Artist",
            album = "Unknown Album",
            durationMs = probe.durationMs,
            path = file.absolutePath,
            albumArtUri = null,
            folderPath = file.parent ?: "",
            dateAdded = file.lastModified() / 1000,
            trackNumber = parsed.trackNumber ?: 0,
            year = 0,
            mimeType = "audio/*",
            sizeBytes = file.length(),
            sampleRate = probe.sampleRate,
            bitrate = probe.bitrate,
        )
    }

    private data class AudioProbe(val durationMs: Long, val sampleRate: Int, val bitrate: Int)

    /**
     * Reads what the container itself knows: duration, sample rate and a
     * bitrate estimate. Fast enough per file on the IO dispatcher and it
     * means the quality chip in Now Playing works for untagged files too.
     */
    private fun probeAudio(file: File): AudioProbe {
        var durationMs = 0L
        var sampleRate = 0
        val retriever = android.media.MediaMetadataRetriever()
        try {
            @Suppress("DEPRECATION")
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
            )?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Unreadable container: leave zeros, the player still handles it.
        } finally {
            retriever.release()
        }
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        sampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                            format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                        } else 0
                        break
                    }
                }
        } catch (_: Exception) {
            // Quality stays unknown; format label still derives from extension.
        } finally {
            extractor.release()
        }
        val bitrate = if (durationMs > 0) (file.length() * 8 / (durationMs / 1000)).toInt() else 0
        return AudioProbe(durationMs, sampleRate, bitrate)
    }
}