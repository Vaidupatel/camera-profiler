#include <jni.h>
#include <string>
#include "common.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_openprofiler_native_1bridge_NativeBridge_getNativeVersion(
    JNIEnv *env,
    jobject /* this */) {
    return env->NewStringUTF(PROFILER_NATIVE_VERSION);
}
