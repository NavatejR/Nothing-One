package com.nothing.one.ai

/**
 * JNI surface of libjournal_whisper.so. Internal to the AI layer — callers
 * go through [WhisperTranscriber], which owns the serialized lifecycle.
 */
internal object WhisperJni {
    init {
        System.loadLibrary("journal_whisper")
    }

    /** Loads ggml-base.en from [path]; returns false when init fails. */
    external fun nativeInit(path: String): Boolean

    /** Transcribes 16 kHz mono PCM. Returns null on failure. */
    external fun nativeTranscribe(pcm: FloatArray, language: String): String?

    external fun nativeFree()
}
