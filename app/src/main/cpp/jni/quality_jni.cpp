#include <jni.h>
#include <cstdint>
#include <memory>
#include <android/log.h>

#include "image_quality_evaluator.h"

#define LOG_TAG "QualityJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace openprofiler;

namespace {

bool clearPendingJniException(JNIEnv* env, const char* context) {
    if (env->ExceptionCheck()) {
        LOGE("Pending JNI exception at %s", context);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

// Must match NativeImageQualityMeasurements primary constructor exactly:
constexpr const char* kNativeImageQualityCtorSig = "(ZDDDDDDDDZII)V";

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openprofiler_native_1bridge_NativeQualityEngine_nativeInit(
    JNIEnv* /* env */,
    jobject /* thiz */) {
    auto* evaluator = new (std::nothrow) ImageQualityEvaluator();
    if (evaluator == nullptr) {
        LOGE("Failed to allocate ImageQualityEvaluator");
        return 0;
    }
    return reinterpret_cast<jlong>(evaluator);
}

JNIEXPORT jobject JNICALL
Java_com_openprofiler_native_1bridge_NativeQualityEngine_nativeEvaluate(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject yBuffer,
    jint width,
    jint height,
    jint yRowStride,
    jint rotationDegrees,
    jdouble darkPixelThreshold,
    jdouble brightPixelThreshold) {

    if (handle == 0) {
        LOGE("Native quality handle is null");
        return nullptr;
    }
    if (yBuffer == nullptr) {
        LOGE("Y buffer is null");
        return nullptr;
    }

    auto* yData = static_cast<uint8_t*>(env->GetDirectBufferAddress(yBuffer));
    if (yData == nullptr) {
        LOGE("Y buffer is not a direct ByteBuffer");
        clearPendingJniException(env, "GetDirectBufferAddress");
        return nullptr;
    }

    auto* evaluator = reinterpret_cast<ImageQualityEvaluator*>(handle);
    ImageQualityMeasurements m = evaluator->evaluate(
        yData,
        width,
        height,
        yRowStride,
        rotationDegrees,
        darkPixelThreshold,
        brightPixelThreshold
    );

    jclass cls = env->FindClass(
        "com/openprofiler/native_bridge/NativeImageQualityMeasurements");
    if (!cls || clearPendingJniException(env, "FindClass(NativeImageQualityMeasurements)")) {
        LOGE("Failed to find NativeImageQualityMeasurements");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(cls, "<init>", kNativeImageQualityCtorSig);
    if (!ctor || clearPendingJniException(env, "GetMethodID(NativeImageQualityMeasurements.<init>)")) {
        LOGE("Failed to resolve NativeImageQualityMeasurements constructor");
        return nullptr;
    }

    jobject obj = env->NewObject(
        cls,
        ctor,
        static_cast<jboolean>(m.success ? JNI_TRUE : JNI_FALSE),
        m.blurLaplacianVariance,
        m.sharpnessGradientMagnitude,
        m.meanBrightness,
        m.darkPixelRatio,
        m.brightPixelRatio,
        m.contrastScore,
        m.noiseScore,
        m.motionMad,
        static_cast<jboolean>(m.hasPriorFrame ? JNI_TRUE : JNI_FALSE),
        m.frameWidth,
        m.frameHeight
    );

    if (!obj || clearPendingJniException(env, "NewObject(NativeImageQualityMeasurements)")) {
        LOGE("Failed to construct NativeImageQualityMeasurements");
        return nullptr;
    }
    return obj;
}

JNIEXPORT void JNICALL
Java_com_openprofiler_native_1bridge_NativeQualityEngine_nativeResetMotion(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle) {
    if (handle != 0) {
        reinterpret_cast<ImageQualityEvaluator*>(handle)->resetMotionState();
    }
}

JNIEXPORT void JNICALL
Java_com_openprofiler_native_1bridge_NativeQualityEngine_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle) {
    if (handle != 0) {
        delete reinterpret_cast<ImageQualityEvaluator*>(handle);
    }
}

} // extern "C"
