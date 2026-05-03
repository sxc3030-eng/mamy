#include <jni.h>
#include <android/log.h>

#define LOG_TAG "MamYWhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_com_mamy_android_data_stt_jni_WhisperJni_pingNative(
    JNIEnv *env, jobject thiz) {
    LOGI("pingNative called");
    return env->NewStringUTF("ok");
}
