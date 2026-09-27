package com.nothing.one.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.nothing.one.ai.WhisperTranscriber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** High-level voice assistant state, shared by every orb on screen. */
enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING, SLEEPING }

/**
 * Where the next finished dictation should land. The screen that starts a
 * dictation claims the sink; when the transcript settles, the owning view
 * model routes the result there. ASSISTANT_REVIEW keeps the explicit
 * save/append buttons, EDITOR_CURSOR means the open note editor inserts at
 * its own cursor.
 */
enum class DictationSink { HOME_NOTE, JOURNAL_APPEND, ASSISTANT_REVIEW, EDITOR_CURSOR }

/**
 * Owns on-device voice dictation and TTS.
 *
 * Engine 1 (primary): bundled whisper.cpp (ggml-tiny.en) over a raw
 * [AudioRecord] stream. The app never routes audio through any service; the
 * buffer stays in-process until whisper returns text.
 *
 * Engine 2 (fallback): the platform [SpeechRecognizer] with
 * EXTRA_PREFER_OFFLINE, used when the whisper model is absent (e.g. a build
 * without bundled assets) or the microphone stream cannot be opened.
 *
 * Live feel: the capture loop calibrates the room's noise floor during the
 * first second of listening (a fixed gate missed quiet mics entirely), then
 * transcribes incrementally every ~1.5s of continuous speech so the caption
 * under the orb streams while the user is still talking — not only after a
 * pause. A whisper warm-up runs the moment the orb is tapped, so the model
 * load never lands in the middle of the first utterance.
 *
 * Exposes the assistant state, live partial text, mic level and final results
 * as state flows the UI and the orb engine subscribe to. No bytes leave the
 * device through anything Journal adds.
 */
@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisper: WhisperTranscriber,
) {
    private val _state = MutableStateFlow(AssistantState.SLEEPING)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Live mic level (0..1) while listening; null when not dictating. */
    val pinnedMicLevel = MutableStateFlow<Float?>(null)

    private val _finalResult = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult.asStateFlow()

    /** Where the next finished dictation is delivered (set before starting). */
    val sink = MutableStateFlow(DictationSink.ASSISTANT_REVIEW)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recording = AtomicBoolean(false)

    /** Set by [cancelListening]; drops the pending final publish. */
    @Volatile
    private var cancelPending = false

    private var captureJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val micAvailable: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------------
    // Engine 1: bundled whisper over AudioRecord
    // ---------------------------------------------------------------------

    fun startListening() {
        if (recording.get()) return // already dictating
        if (!micAvailable) {
            _lastError.value = "Microphone permission needed"
            return
        }
        cancelPending = false
        stopSpeaking()
        _lastError.value = null
        _partialText.value = ""
        _finalResult.value = ""
        pinnedMicLevel.value = 0f

        // Warm the model up front (load + one throwaway inference) so the
        // first real utterance is not the one that pays the cold start.
        scope.launch { runCatching { whisper.warmUp() } }

        if (whisper.modelPresent() && startWhisperCapture()) {
            _state.value = AssistantState.LISTENING
            return
        }
        // Fallback: platform recognizer (on-device when the ROM ships one).
        startPlatformRecognition()
    }

    /** Opens AudioRecord and streams utterances into whisper. */
    private fun startWhisperCapture(): Boolean {
        // Lint-visible guard: the mic permission is requested by the UI before
        // this path runs, but double-check so a revoked grant can never crash.
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            )
            if (minBuf <= 0) return false
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuf, 8 * 1024),
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }
            recording.set(true)
            captureJob = scope.launch { captureLoop(recorder) }
            true
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "whisper capture unavailable", t)
            false
        }
    }

    /**
     * Reads mic frames until stop, gating speech from quiet with an adaptive
     * threshold. Untranscribed audio accumulates in [pendingBuf]; it is
     * flushed to whisper every [LIVE_FLUSH_S] of continuous speech (the live
     * transcript under the orb) or when END_SILENCE_S of quiet closes an
     * utterance. The flush runs on the capture thread — tiny.en on a short
     * buffer returns well under a second, so the gap in captured audio is
     * smaller than the silence that triggers it.
     */
    private suspend fun captureLoop(recorder: AudioRecord) {
        recorder.startRecording()
        val frame = FloatArray(FRAME_SAMPLES)
        val pendingBuf = FloatArray(MAX_UTTERANCE_SAMPLES)
        var pendingLen = 0
        var pendingVoiced = 0
        val leadIn = ArrayDeque<FloatArray>(LEAD_IN_FRAMES)
        var voicedFrames = 0
        var quietFrames = 0
        // Adaptive gate: measured room level; starts from the quiet-room
        // constant and is replaced by the calibration pass, then drifts to
        // track the room while quiet.
        var roomLevel = NOISE_FLOOR
        var calibrating = true
        var calibFrames = 0
        try {
            while (recording.get()) {
                val n = recorder.read(frame, 0, FRAME_SAMPLES, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                var energy = 0f
                for (i in 0 until n) energy += frame[i] * frame[i]
                val rms = sqrt(energy / n)

                if (calibrating) {
                    roomLevel = maxOf(roomLevel, rms)
                    if (++calibFrames >= CALIBRATION_FRAMES) calibrating = false
                } else if (rms <= speechThreshold(roomLevel) && voicedFrames == 0) {
                    // Quiet: drift the room estimate so a moved phone or a
                    // fan kicking in re-calibrates within a few seconds.
                    roomLevel = roomLevel * 0.95f + rms * 0.05f
                }
                val gate = speechThreshold(roomLevel)
                pinnedMicLevel.value = ((rms - roomLevel) / DYNAMIC_RANGE).coerceIn(0f, 1f)

                if (rms > gate) {
                    voicedFrames++
                    quietFrames = 0
                    if (voicedFrames == 1) {
                        // Speech starts: prepend the last few quiet frames so
                        // word onsets survive the energy gate.
                        for (buf in leadIn) pendingLen = appendFrame(pendingBuf, pendingLen, buf)
                        leadIn.clear()
                    }
                    pendingLen = appendFrame(pendingBuf, pendingLen, frame)
                    pendingVoiced++
                } else if (voicedFrames > 0) {
                    quietFrames++
                    pendingLen = appendFrame(pendingBuf, pendingLen, frame)
                    if (quietFrames * FRAME_SECONDS >= END_SILENCE_S) {
                        flushUtterance(pendingBuf, pendingLen, pendingVoiced)
                        pendingLen = 0
                        pendingVoiced = 0
                        voicedFrames = 0
                        quietFrames = 0
                    }
                } else {
                    // Quiet before any speech: rolling lead-in window.
                    leadIn.addLast(frame.copyOf(n))
                    if (leadIn.size > LEAD_IN_FRAMES) leadIn.removeFirst()
                }

                // Live streaming: hand whisper what we have mid-utterance so
                // the transcript grows while the user keeps talking.
                if (pendingVoiced * FRAME_SECONDS >= LIVE_FLUSH_S) {
                    flushUtterance(pendingBuf, pendingLen, pendingVoiced)
                    pendingLen = 0
                    pendingVoiced = 0
                    voicedFrames = 0
                    quietFrames = 0
                }
            }
            // Tail on manual stop.
            if (pendingLen >= MIN_VOICED_FRAMES * FRAME_SAMPLES) {
                flushUtterance(pendingBuf, pendingLen, pendingVoiced)
            }
        } catch (t: Throwable) {
            if (t !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e(TAG, "capture loop failed", t)
                _lastError.value = "Dictation failed"
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            pinnedMicLevel.value = null
            if (_state.value == AssistantState.LISTENING || _state.value == AssistantState.THINKING) {
                _state.value = AssistantState.IDLE
            }
            // Publish the accumulated transcript as the final result one beat
            // after the state settles, so UI built around state == IDLE sees
            // the settled value first and the sink sees the text after.
            scope.launch {
                delay((FINAL_AFTER_STOP_S * 1000).toLong())
                if (recording.get()) return@launch
                if (cancelPending) {
                    // A cancel landed during the drain window: drop the
                    // transcript entirely instead of publishing it.
                    cancelPending = false
                    _partialText.value = ""
                    _finalResult.value = ""
                    return@launch
                }
                _finalResult.value = _partialText.value.trim()
            }
        }
    }

    /** Speech gate: comfortably above the measured room, never below the base. */
    private fun speechThreshold(roomLevel: Float): Float =
        maxOf(SPEECH_RMS, roomLevel * 2.5f)

    private fun appendFrame(dst: FloatArray, dstLen: Int, src: FloatArray): Int {
        val m = minOf(src.size, dst.size - dstLen)
        if (m > 0) System.arraycopy(src, 0, dst, dstLen, m)
        return dstLen + m
    }

    /** Transcribes [len] samples if enough of them were voiced; appends live. */
    private suspend fun flushUtterance(buf: FloatArray, len: Int, voiced: Int) {
        if (cancelPending) return // a cancel landed: drop pending audio too
        if (voiced < MIN_VOICED_FRAMES || len < MIN_VOICED_FRAMES * FRAME_SAMPLES) return
        _state.value = AssistantState.THINKING
        val text = whisper.transcribe(buf.copyOf(len))
        if (text != null) {
            _partialText.value = (_partialText.value + " " + text).trim()
        } else {
            _lastError.value = "Transcription failed"
        }
        if (recording.get()) _state.value = AssistantState.LISTENING
    }

    fun stopListening() {
        if (recording.get()) {
            // Let the loop drain, transcribe the tail, and settle the state
            // itself — the orb shows THINKING meanwhile.
            recording.set(false)
        } else {
            recognizer?.stopListening()
            if (_state.value == AssistantState.LISTENING || _state.value == AssistantState.THINKING) {
                _state.value = AssistantState.IDLE
            }
            pinnedMicLevel.value = null
        }
    }

    /**
     * Stops dictation and discards everything transcribed so far — the
     * cancel action of the dictation menu. Safe to call when idle.
     */
    fun cancelListening() {
        cancelPending = true
        stopListening()
        _partialText.value = ""
        _finalResult.value = ""
        if (_state.value == AssistantState.LISTENING || _state.value == AssistantState.THINKING) {
            _state.value = AssistantState.IDLE
        }
    }

    // ---------------------------------------------------------------------
    // Engine 2: platform SpeechRecognizer fallback
    // ---------------------------------------------------------------------

    private var recognizer: SpeechRecognizer? = null

    private val recognizerListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = AssistantState.LISTENING
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (_state.value == AssistantState.LISTENING) {
                pinnedMicLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = AssistantState.THINKING
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing heard"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                else -> "Recognition unavailable"
            }
            _lastError.value = message
            _state.value = AssistantState.IDLE
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _partialText.value = text
            _state.value = AssistantState.IDLE
            _finalResult.value = text
        }

        override fun onPartialResults(partialResults: Bundle?) {
            _partialText.value = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startPlatformRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _lastError.value = "No speech service on this device"
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer?.setRecognitionListener(recognizerListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
        }
        recognizer?.startListening(intent)
        _state.value = AssistantState.LISTENING
    }

    // ---------------------------------------------------------------------
    // TTS
    // ---------------------------------------------------------------------

    /** Speaks [text] through TTS; the SPEAKING state drives the orb. */
    fun speak(text: String) {
        if (text.isBlank()) return
        ensureTts()
        _state.value = AssistantState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "journal-" + System.nanoTime())
    }

    fun stopSpeaking() {
        tts?.stop()
        if (_state.value == AssistantState.SPEAKING) _state.value = AssistantState.IDLE
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (_state.value == AssistantState.SPEAKING) {
                            _state.value = AssistantState.IDLE
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_state.value == AssistantState.SPEAKING) {
                            _state.value = AssistantState.IDLE
                        }
                    }
                })
            }
        }
    }

    /** Clear the error once it has been shown. */
    fun consumeError() {
        _lastError.value = null
    }

    /** Acknowledge the final dictation result after the caller has saved it. */
    fun clearFinalResult() {
        _finalResult.value = ""
        _partialText.value = ""
    }

    fun shutdown() {
        recording.set(false)
        captureJob?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        scope.cancel()
    }

    private companion object {
        const val TAG = "SpeechManager"
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_600          // 100 ms of 16 kHz mono
        const val FRAME_SECONDS = 0.1f
        const val SPEECH_RMS = 0.012f            // base gate; adapts up with the room
        const val NOISE_FLOOR = 0.004f           // starting room-level estimate
        const val DYNAMIC_RANGE = 0.10f
        const val CALIBRATION_FRAMES = 10        // 1s room calibration on open
        const val END_SILENCE_S = 0.5f           // quiet that closes an utterance
        const val LIVE_FLUSH_S = 1.5f            // transcribe this often mid-speech
        const val MIN_VOICED_FRAMES = 3          // ignore sub-0.3s blips
        const val LEAD_IN_FRAMES = 3             // 300 ms of pre-speech kept
        const val MAX_UTTERANCE_SAMPLES = 160_000 // 10s cap keeps memory bounded
        const val FINAL_AFTER_STOP_S = 1.4f      // drain beat before finalResult lands
    }
}
