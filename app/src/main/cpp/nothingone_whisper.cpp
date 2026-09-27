/*
 * Minimal JNI bridge between Kotlin (com.nothing.one.ai.WhisperJni) and
 * the vendored whisper.cpp build. One process-wide context;
 * init/transcribe/free are serialized by the Kotlin caller (SpeechManager),
 * so no extra locking lives here.
 */
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "nothingone_whisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static whisper_context *g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nothing_one_ai_WhisperJni_nativeInit(JNIEnv *env, jobject, jstring jPath) {
    if (g_ctx != nullptr) return JNI_TRUE;
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    if (path == nullptr) return JNI_FALSE;

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;  // CPU inference: predictable memory, no GPU vendor deps
    g_ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(jPath, path);

    if (g_ctx == nullptr) {
        LOGE("whisper_init_from_file failed");
        return JNI_FALSE;
    }
    LOGI("whisper model loaded");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nothing_one_ai_WhisperJni_nativeTranscribe(
        JNIEnv *env, jobject, jfloatArray jPcm, jstring jLang) {
    if (g_ctx == nullptr || jPcm == nullptr) return nullptr;

    const jsize n = env->GetArrayLength(jPcm);
    std::vector<float> pcm(static_cast<size_t>(n));
    env->GetFloatArrayRegion(jPcm, 0, n, pcm.data());

    const char *lang = env->GetStringUTFChars(jLang, nullptr);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang != nullptr ? lang : "en";
    params.translate = false;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.no_context = true;      // independent utterances
    params.single_segment = false;
    params.beam_search.beam_size = -1;  // greedy: fastest for short dictation
    params.suppress_blank = true;
    params.temperature = 0.0f;
    if (lang != nullptr) env->ReleaseStringUTFChars(jLang, lang);

    if (whisper_full(g_ctx, params, pcm.data(), static_cast<int>(pcm.size())) != 0) {
        LOGE("whisper_full failed");
        return nullptr;
    }

    std::string text;
    const int nSegments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < nSegments; ++i) {
        const char *seg = whisper_full_get_segment_text(g_ctx, i);
        if (seg != nullptr) text += seg;
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nothing_one_ai_WhisperJni_nativeFree(JNIEnv *, jobject) {
    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}
