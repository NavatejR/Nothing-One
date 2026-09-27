package com.nothing.one.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device dictation engine backed by vendored whisper.cpp (ggml-tiny.en,
 * ~75 MB). Tiny was chosen over base/medium deliberately: dictation utterances
 * are short and the transcribe step must feel instant on mid-range phones
 * without cooking the battery — tiny.en is ~2-4x faster than base.en and
 * roughly halves the RAM and heat for a small accuracy trade that short voice
 * notes barely notice. The model ships inside the APK and is staged to
 * filesDir on first use — MediaPipe-style mmap does not apply to ggml, which
 * wants a plain file. Every call is serialized through one mutex: whisper
 * holds a single context.
 */
@Singleton
class WhisperTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var initialized = false

    /**
     * True when the bundled STT model exists in the APK — the cheap check
     * used to pick the dictation engine before any weight is loaded.
     */
    fun modelPresent(): Boolean = try {
        context.assets.open("ai/$MODEL_FILE").use { it.read() >= 0 }
    } catch (_: Exception) {
        false
    }

    suspend fun transcribe(pcm: FloatArray, language: String = "en"): String? =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (!ensureInitialized()) return@withLock null
                if (pcm.isEmpty()) return@withLock null
                WhisperJni.nativeTranscribe(pcm, language)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

    /**
     * Loads the weights and runs one throwaway inference so the first real
     * utterance is not the one that pays the cold start. Called from
     * [SpeechManager.startListening] on a fire-and-forget coroutine; safe to
     * call repeatedly (the mutex serializes against real transcription).
     */
    suspend fun warmUp() {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (!ensureInitialized()) return@withLock
                WhisperJni.nativeTranscribe(FloatArray(16_000), "en") // 1s of silence
            }
        }
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        return try {
            val model = stagedModel()
            initialized = WhisperJni.nativeInit(model.absolutePath)
            if (initialized) Log.i(TAG, "whisper ready: ${model.length()} bytes")
            initialized
        } catch (t: Throwable) {
            Log.e(TAG, "whisper init failed", t)
            false
        }
    }

    /** Copies the APK asset to filesDir once; future runs reuse the copy. */
    private fun stagedModel(): File {
        val out = File(context.filesDir, MODEL_FILE)
        if (out.exists() && out.length() > MIN_VALID_BYTES) return out
        // Remove stale copies of previous model generations (e.g. base.en).
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("ggml-") && file.name != MODEL_FILE) file.delete()
        }
        val tmp = File(context.filesDir, "$MODEL_FILE.tmp")
        context.assets.open("ai/$MODEL_FILE").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, BUFFER_BYTES) }
        }
        if (tmp.length() <= MIN_VALID_BYTES) {
            tmp.delete()
            error("staged whisper model is incomplete (${tmp.length()} bytes)")
        }
        tmp.renameTo(out)
        return out
    }

    companion object {
        private const val TAG = "WhisperTranscriber"
        const val MODEL_FILE = "ggml-tiny.en.bin"
        private const val MIN_VALID_BYTES = 40L * 1024 * 1024
        private const val BUFFER_BYTES = 1 shl 16
    }
}
