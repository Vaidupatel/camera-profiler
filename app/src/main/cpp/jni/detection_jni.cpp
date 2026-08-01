#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>

#include "charuco_detector_engine.h"
#include "image_buffer_utils.h"

#define LOG_TAG "DetectionJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace openprofiler;

namespace {

/**
 * Returns true if a pending JNI exception exists.
 * Logs and clears it so subsequent JNI calls remain legal; the native
 * method then returns nullptr and Kotlin treats that as detection failure.
 */
bool clearPendingJniException(JNIEnv* env, const char* context) {
    if (env->ExceptionCheck()) {
        LOGE("Pending JNI exception at %s", context);
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }
    return false;
}

// Must match NativeDetectionResult primary constructor JVM signature exactly:
// (ZLjava/lang/String;II[I[I[F[F[F[D[DFJLjava/lang/String;[F[F[F[J)V
constexpr const char* kNativeDetectionResultCtorSig =
    "(ZLjava/lang/String;II[I[I[F[F[F[D[DFJLjava/lang/String;[F[F[F[J)V";

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openprofiler_native_1bridge_NativeDetectionEngine_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jstring boardConfigJson) {

    if (!boardConfigJson) {
        LOGE("boardConfigJson is null");
        return 0;
    }

    const char* jsonCStr = env->GetStringUTFChars(boardConfigJson, nullptr);
    if (!jsonCStr) {
        LOGE("GetStringUTFChars failed for boardConfigJson");
        clearPendingJniException(env, "nativeInit/GetStringUTFChars");
        return 0;
    }
    std::string configStr(jsonCStr);
    env->ReleaseStringUTFChars(boardConfigJson, jsonCStr);

    auto engine = std::make_unique<CharucoDetectorEngine>();
    if (!engine->initialize(configStr)) {
        LOGE("Failed to initialize CharucoDetectorEngine");
        return 0;
    }

    return reinterpret_cast<jlong>(engine.release());
}

JNIEXPORT jobject JNICALL
Java_com_openprofiler_native_1bridge_NativeDetectionEngine_nativeDetectBoard(
    JNIEnv* env,
    jobject /* thiz */,
    jlong handle,
    jobject yBuffer,
    jobject uBuffer,
    jobject vBuffer,
    jint yRowStride,
    jint uvRowStride,
    jint uvPixelStride,
    jint width,
    jint height,
    jint rotationDegrees,
    jdoubleArray cameraMatrixArray,
    jdoubleArray distCoeffsArray) {

    if (handle == 0) {
        LOGE("Native handle is null");
        return nullptr;
    }

    auto* engine = reinterpret_cast<CharucoDetectorEngine*>(handle);

    cv::Mat grayMat;
    bool convSuccess = ImageBufferUtils::yuvPlanesToGrayMat(
        env, yBuffer, uBuffer, vBuffer,
        yRowStride, uvRowStride, uvPixelStride,
        width, height, rotationDegrees, grayMat
    );

    if (!convSuccess || grayMat.empty()) {
        LOGE("Failed to convert image buffer to gray cv::Mat");
        return nullptr;
    }

    int frameWidth = grayMat.cols;
    int frameHeight = grayMat.rows;

    cv::Mat K, D;
    if (cameraMatrixArray != nullptr) {
        jsize len = env->GetArrayLength(cameraMatrixArray);
        if (len == 9) {
            jdouble* elems = env->GetDoubleArrayElements(cameraMatrixArray, nullptr);
            if (elems != nullptr) {
                K = cv::Mat(3, 3, CV_64F, elems).clone();
                env->ReleaseDoubleArrayElements(cameraMatrixArray, elems, JNI_ABORT);
            } else if (clearPendingJniException(env, "GetDoubleArrayElements(cameraMatrix)")) {
                return nullptr;
            }
        }
    }
    if (distCoeffsArray != nullptr) {
        jsize len = env->GetArrayLength(distCoeffsArray);
        if (len >= 4) {
            jdouble* elems = env->GetDoubleArrayElements(distCoeffsArray, nullptr);
            if (elems != nullptr) {
                D = cv::Mat(len, 1, CV_64F, elems).clone();
                env->ReleaseDoubleArrayElements(distCoeffsArray, elems, JNI_ABORT);
            } else if (clearPendingJniException(env, "GetDoubleArrayElements(distCoeffs)")) {
                return nullptr;
            }
        }
    }

    DetectionEngineResult result = engine->detect(grayMat, frameWidth, frameHeight, K, D);

    // Resolve Java type + constructor before any further JNI object allocation.
    jclass resultClass = env->FindClass("com/openprofiler/native_bridge/NativeDetectionResult");
    if (!resultClass || clearPendingJniException(env, "FindClass(NativeDetectionResult)")) {
        LOGE("Failed to find NativeDetectionResult class");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(
        resultClass,
        "<init>",
        kNativeDetectionResultCtorSig
    );
    if (!ctor || clearPendingJniException(env, "GetMethodID(NativeDetectionResult.<init>)")) {
        LOGE("Failed to resolve NativeDetectionResult constructor: %s",
             kNativeDetectionResultCtorSig);
        return nullptr;
    }

    jstring dictNameStr = env->NewStringUTF(result.dictionaryName.c_str());
    if (!dictNameStr || clearPendingJniException(env, "NewStringUTF(dictionaryName)")) {
        LOGE("Failed to create dictionaryName jstring");
        return nullptr;
    }

    jstring rejReasonStr = nullptr;
    if (!result.rejectedReason.empty()) {
        rejReasonStr = env->NewStringUTF(result.rejectedReason.c_str());
        if (!rejReasonStr || clearPendingJniException(env, "NewStringUTF(rejectedReason)")) {
            LOGE("Failed to create rejectedReason jstring");
            return nullptr;
        }
    }

    jintArray markerIdsArr = env->NewIntArray(static_cast<jsize>(result.markerIds.size()));
    if (!markerIdsArr || clearPendingJniException(env, "NewIntArray(markerIds)")) {
        return nullptr;
    }
    if (!result.markerIds.empty()) {
        env->SetIntArrayRegion(markerIdsArr, 0,
                               static_cast<jsize>(result.markerIds.size()),
                               result.markerIds.data());
        if (clearPendingJniException(env, "SetIntArrayRegion(markerIds)")) {
            return nullptr;
        }
    }

    jintArray charucoIdsArr = env->NewIntArray(static_cast<jsize>(result.charucoIds.size()));
    if (!charucoIdsArr || clearPendingJniException(env, "NewIntArray(charucoIds)")) {
        return nullptr;
    }
    if (!result.charucoIds.empty()) {
        env->SetIntArrayRegion(charucoIdsArr, 0,
                               static_cast<jsize>(result.charucoIds.size()),
                               result.charucoIds.data());
        if (clearPendingJniException(env, "SetIntArrayRegion(charucoIds)")) {
            return nullptr;
        }
    }

    jfloatArray cornerXArr = env->NewFloatArray(static_cast<jsize>(result.charucoCorners.size()));
    jfloatArray cornerYArr = env->NewFloatArray(static_cast<jsize>(result.charucoCorners.size()));
    if (!cornerXArr || !cornerYArr ||
        clearPendingJniException(env, "NewFloatArray(corners)")) {
        return nullptr;
    }
    if (!result.charucoCorners.empty()) {
        std::vector<float> xs(result.charucoCorners.size());
        std::vector<float> ys(result.charucoCorners.size());
        for (size_t i = 0; i < result.charucoCorners.size(); ++i) {
            xs[i] = result.charucoCorners[i].x;
            ys[i] = result.charucoCorners[i].y;
        }
        env->SetFloatArrayRegion(cornerXArr, 0, static_cast<jsize>(xs.size()), xs.data());
        env->SetFloatArrayRegion(cornerYArr, 0, static_cast<jsize>(ys.size()), ys.data());
        if (clearPendingJniException(env, "SetFloatArrayRegion(corners)")) {
            return nullptr;
        }
    }

    jfloatArray cornerPrecArr =
        env->NewFloatArray(static_cast<jsize>(result.cornerPrecisions.size()));
    if (!cornerPrecArr || clearPendingJniException(env, "NewFloatArray(cornerPrecision)")) {
        return nullptr;
    }
    if (!result.cornerPrecisions.empty()) {
        env->SetFloatArrayRegion(cornerPrecArr, 0,
                                 static_cast<jsize>(result.cornerPrecisions.size()),
                                 result.cornerPrecisions.data());
        if (clearPendingJniException(env, "SetFloatArrayRegion(cornerPrecision)")) {
            return nullptr;
        }
    }

    jdoubleArray rvecArr = nullptr;
    if (!result.rvec.empty() && result.rvec.rows == 3) {
        rvecArr = env->NewDoubleArray(3);
        if (!rvecArr || clearPendingJniException(env, "NewDoubleArray(rvec)")) {
            return nullptr;
        }
        env->SetDoubleArrayRegion(rvecArr, 0, 3,
                                  reinterpret_cast<const jdouble*>(result.rvec.data));
        if (clearPendingJniException(env, "SetDoubleArrayRegion(rvec)")) {
            return nullptr;
        }
    }

    jdoubleArray tvecArr = nullptr;
    if (!result.tvec.empty() && result.tvec.rows == 3) {
        tvecArr = env->NewDoubleArray(3);
        if (!tvecArr || clearPendingJniException(env, "NewDoubleArray(tvec)")) {
            return nullptr;
        }
        env->SetDoubleArrayRegion(tvecArr, 0, 3,
                                  reinterpret_cast<const jdouble*>(result.tvec.data));
        if (clearPendingJniException(env, "SetDoubleArrayRegion(tvec)")) {
            return nullptr;
        }
    }

    jfloatArray markerOutlinesArr = nullptr;
    if (!result.markerOutlines.empty()) {
        std::vector<float> coords;
        for (const auto& pts : result.markerOutlines) {
            for (const auto& pt : pts) {
                coords.push_back(pt.x);
                coords.push_back(pt.y);
            }
        }
        markerOutlinesArr = env->NewFloatArray(static_cast<jsize>(coords.size()));
        if (!markerOutlinesArr || clearPendingJniException(env, "NewFloatArray(markerOutlines)")) {
            return nullptr;
        }
        env->SetFloatArrayRegion(markerOutlinesArr, 0,
                                 static_cast<jsize>(coords.size()), coords.data());
        if (clearPendingJniException(env, "SetFloatArrayRegion(markerOutlines)")) {
            return nullptr;
        }
    }

    jfloatArray axesArr = nullptr;
    if (!result.boardAxes2D.empty()) {
        std::vector<float> coords;
        for (const auto& pt : result.boardAxes2D) {
            coords.push_back(pt.x);
            coords.push_back(pt.y);
        }
        axesArr = env->NewFloatArray(static_cast<jsize>(coords.size()));
        if (!axesArr || clearPendingJniException(env, "NewFloatArray(boardAxes)")) {
            return nullptr;
        }
        env->SetFloatArrayRegion(axesArr, 0, static_cast<jsize>(coords.size()), coords.data());
        if (clearPendingJniException(env, "SetFloatArrayRegion(boardAxes)")) {
            return nullptr;
        }
    }

    jfloatArray bboxArr = nullptr;
    if (!result.boundingBox2D.empty()) {
        std::vector<float> coords;
        for (const auto& pt : result.boundingBox2D) {
            coords.push_back(pt.x);
            coords.push_back(pt.y);
        }
        bboxArr = env->NewFloatArray(static_cast<jsize>(coords.size()));
        if (!bboxArr || clearPendingJniException(env, "NewFloatArray(boundingBox)")) {
            return nullptr;
        }
        env->SetFloatArrayRegion(bboxArr, 0, static_cast<jsize>(coords.size()), coords.data());
        if (clearPendingJniException(env, "SetFloatArrayRegion(boundingBox)")) {
            return nullptr;
        }
    }

    jlongArray stageTimingsArr =
        env->NewLongArray(static_cast<jsize>(result.stageTimingsMs.size()));
    if (!stageTimingsArr || clearPendingJniException(env, "NewLongArray(stageTimings)")) {
        return nullptr;
    }
    if (!result.stageTimingsMs.empty()) {
        std::vector<jlong> timings(result.stageTimingsMs.begin(), result.stageTimingsMs.end());
        env->SetLongArrayRegion(stageTimingsArr, 0,
                                static_cast<jsize>(timings.size()), timings.data());
        if (clearPendingJniException(env, "SetLongArrayRegion(stageTimings)")) {
            return nullptr;
        }
    }

    jobject obj = env->NewObject(
        resultClass,
        ctor,
        static_cast<jboolean>(result.boardDetected ? JNI_TRUE : JNI_FALSE),
        dictNameStr,
        result.markerCount,
        result.charucoCornerCount,
        markerIdsArr,
        charucoIdsArr,
        cornerXArr,
        cornerYArr,
        cornerPrecArr,
        rvecArr,
        tvecArr,
        result.detectionConfidence,
        static_cast<jlong>(result.processingTimeMs),
        rejReasonStr,
        markerOutlinesArr,
        axesArr,
        bboxArr,
        stageTimingsArr
    );

    if (!obj || clearPendingJniException(env, "NewObject(NativeDetectionResult)")) {
        LOGE("Failed to construct NativeDetectionResult");
        return nullptr;
    }

    return obj;
}

JNIEXPORT void JNICALL
Java_com_openprofiler_native_1bridge_NativeDetectionEngine_nativeRelease(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong handle) {
    if (handle != 0) {
        auto* engine = reinterpret_cast<CharucoDetectorEngine*>(handle);
        delete engine;
    }
}

} // extern "C"
