#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "whisper.h"

#define LOG_TAG "MamYWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mamy_android_data_stt_jni_WhisperJni_pingNative(JNIEnv *env, jobject) {
    return env->NewStringUTF("ok");
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_mamy_android_data_stt_jni_WhisperJni_initContextNative(
    JNIEnv *env, jobject, jstring jModelPath) {
    const char *path = env->GetStringUTFChars(jModelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(jModelPath, path);
    if (ctx == nullptr) {
        LOGE("whisper_init_from_file failed for %s", path);
        return 0L;
    }
    LOGI("whisper context loaded: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_mamy_android_data_stt_jni_WhisperJni_freeContextNative(
    JNIEnv *, jobject, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (ctx) whisper_free(ctx);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mamy_android_data_stt_jni_WhisperJni_transcribeNative(
    JNIEnv *env, jobject, jlong ctxPtr, jshortArray jPcm, jstring jLang) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctxPtr);
    if (!ctx) return nullptr;

    jsize n = env->GetArrayLength(jPcm);
    std::vector<float> samples(n);
    jshort *raw = env->GetShortArrayElements(jPcm, nullptr);
    const float kInvScale = 1.0f / 32768.0f;
    for (jsize i = 0; i < n; ++i) samples[i] = static_cast<float>(raw[i]) * kInvScale;
    env->ReleaseShortArrayElements(jPcm, raw, JNI_ABORT);

    const char *lang = env->GetStringUTFChars(jLang, nullptr);

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = lang;
    wparams.n_threads = 4;
    wparams.suppress_blank = true;

    int rc = whisper_full(ctx, wparams, samples.data(), static_cast<int>(samples.size()));
    env->ReleaseStringUTFChars(jLang, lang);
    if (rc != 0) {
        LOGE("whisper_full rc=%d", rc);
        return nullptr;
    }

    std::string out;
    const int nseg = whisper_full_n_segments(ctx);
    for (int i = 0; i < nseg; ++i) {
        out += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(out.c_str());
}
