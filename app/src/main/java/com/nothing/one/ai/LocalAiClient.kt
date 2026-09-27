@file:Suppress("DEPRECATION")

package com.nothing.one.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A text-in / text-out local AI engine. Every feature degrades to manual
 * fallbacks when the engine is unavailable, so the UI never blocks on AI.
 */
interface LocalAiClient {
    /** True when the engine can answer. Cheap; must not load heavy weights. */
    suspend fun isAvailable(): Boolean

    /** Models the engine serves (for the Settings picker). */
    suspend fun listModels(): List<String>

    /** One-shot completion. Returns null when the engine is down or errors. */
    suspend fun generate(prompt: String, system: String? = null): String?
}

/** Lifecycle of the bundled inference engine. */
enum class EngineState { LOADING, READY, UNAVAILABLE }

/**
 * The bundled engine: Gemma 3 1B Instruct (int4, ~530 MB) running through
 * Google AI Edge's MediaPipe LLM Inference. The model ships inside the APK
 * under assets/ai/ and is staged to filesDir on first use; nothing is ever
 * downloaded at runtime (the manifest declares no INTERNET permission).
 *
 * Heat policy (why there is no preload anymore): the 1B weights cost roughly
 * 600 MB of RAM and a sustained burst of SoC work to load. Loading them the
 * moment the app opens — before the user has asked for a single AI feature —
 * was the dominant source of device heating. The engine is therefore lazy:
 * weights load only when the first real request arrives.
 *
 * The preferred backend is GPU: on mid-range Adreno/Mali GPUs the int4 model
 * decodes several times faster than the CPU path and the NPU-sized compute
 * bursts keep the CPU clusters idle, which also cuts peak heat. Devices whose
 * GPU delegate fails to compile the graph fall back to a CPU reload.
 *
 * A per-conversation [Conversation] wraps a MediaPipe [LlmInferenceSession]
 * so multi-turn chat keeps its context.
 */
@Singleton
class OnDeviceAiClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalAiClient {

    private val _state = MutableStateFlow(EngineState.LOADING)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private var llm: Any? = null // LlmInference; typed lazily below

    override suspend fun isAvailable(): Boolean =
        _state.value != EngineState.UNAVAILABLE && modelAssetExists()

    override suspend fun listModels(): List<String> =
        if (modelAssetExists()) listOf(MODEL_NAME) else emptyList()

    override suspend fun generate(prompt: String, system: String?): String? =
        withContext(Dispatchers.Default) {
            conversation()?.use { it.respond(prompt, system) }
        }

    /**
     * Opens a multi-turn conversation. Each [Conversation.respond] call sees
     * every turn before it, so follow-up questions keep their context. Null
     * when the engine cannot load.
     */
    suspend fun conversation(): Conversation? {
        val engine = engine() ?: return null
        return Conversation(engine)
    }

    private suspend fun engine(): LlmInference? = loadMutex.withLock {
        llm?.let { return@withLock it as LlmInference }
        if (_state.value == EngineState.UNAVAILABLE) return@withLock null
        try {
            val path = stagedModel()
            var engine = try {
                LlmInference.createFromOptions(context, options(path, preferGpu = true))
            } catch (t: Throwable) {
                Log.w(TAG, "GPU backend unavailable, falling back to CPU", t)
                null
            }
            if (engine == null) {
                engine = try {
                    LlmInference.createFromOptions(context, options(path, preferGpu = false))
                } catch (t: Throwable) {
                    Log.e(TAG, "engine init failed", t)
                    _state.value = EngineState.UNAVAILABLE
                    return@withLock null
                }
            }
            llm = engine
            _state.value = EngineState.READY
            Log.i(TAG, "engine ready: $MODEL_NAME (${path.length()} bytes)")
            engine
        } catch (t: Throwable) {
            Log.e(TAG, "engine init failed", t)
            _state.value = EngineState.UNAVAILABLE
            null
        }
    }

    private fun options(path: File, preferGpu: Boolean): LlmInference.LlmInferenceOptions {
        val builder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path.absolutePath)
            .setMaxTokens(MAX_TOKENS)
        if (preferGpu) builder.setPreferredBackend(LlmInference.Backend.GPU)
        return builder.build()
    }

    private fun modelAssetExists(): Boolean = try {
        context.assets.open("ai/$MODEL_NAME").use { it.read() >= 0 }
    } catch (_: Exception) {
        false
    }

    /** Copies the model asset to filesDir once; future runs reuse the copy. */
    private fun stagedModel(): File {
        val out = File(context.filesDir, MODEL_NAME)
        if (out.exists() && out.length() > MIN_VALID_BYTES) return out
        val tmp = File(context.filesDir, "$MODEL_NAME.tmp")
        context.assets.open("ai/$MODEL_NAME").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, BUFFER_BYTES) }
        }
        if (tmp.length() <= MIN_VALID_BYTES) {
            tmp.delete()
            error("staged engine model is incomplete (${tmp.length()} bytes)")
        }
        tmp.renameTo(out)
        return out
    }

    /**
     * One conversation thread: wraps a MediaPipe session whose history grows
     * with every turn. Not thread-safe by design — keep it on one caller.
     */
    class Conversation internal constructor(private val engine: LlmInference) : AutoCloseable {
        private val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.4f)
                .setTopK(40)
                .build(),
        )
        private var closed = false

        /** Sends one turn and returns the reply, or null on failure. */
        fun respond(input: String, system: String? = null): String? {
            if (closed) return null
            return try {
                if (system != null) session.addQueryChunk(system)
                session.addQueryChunk(input)
                session.generateResponse()?.trim()?.takeIf { it.isNotEmpty() }
            } catch (t: Throwable) {
                Log.e("OnDeviceAiClient", "generate failed", t)
                null
            }
        }

        override fun close() {
            closed = true
            runCatching { session.close() }
        }
    }

    companion object {
        private const val TAG = "OnDeviceAiClient"

        /** Bundled Gemma 3 1B instruct, int4-quantized LiteRT task file. */
        const val MODEL_NAME = "gemma3-1b-it-int4.task"
        const val MODEL_LABEL = "Gemma 3 1B IT (int4)"

        /**
         * Short summaries, moods and tags only — 512 output tokens let a
         * request run for tens of seconds on a mid-range SoC, which felt
         * broken and dumped heat. 256 keeps every answer comfortably short.
         */
        private const val MAX_TOKENS = 256
        private const val MIN_VALID_BYTES = 256L * 1024 * 1024
        private const val BUFFER_BYTES = 1 shl 16
    }
}
