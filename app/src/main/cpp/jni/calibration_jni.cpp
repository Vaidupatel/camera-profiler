#include <jni.h>
#include <vector>
#include <android/log.h>
#include <opencv2/core.hpp>
#include "calibration_solver.h"

#define LOG_TAG "CalibrationJNI"
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

} // namespace

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_openprofiler_native_1bridge_NativeCalibrationEngine_nativeCalibrate(
    JNIEnv* env,
    jobject /* thiz */,
    jobjectArray objectPointsArray,
    jobjectArray imagePointsArray,
    jint width,
    jint height
) {
    if (!objectPointsArray || !imagePointsArray) {
        LOGE("Input arrays are null");
        return nullptr;
    }

    jsize imageCount = env->GetArrayLength(objectPointsArray);
    if (imageCount == 0 || imageCount != env->GetArrayLength(imagePointsArray)) {
        LOGE("Image count is zero or mismatched: %d", imageCount);
        return nullptr;
    }

    std::vector<std::vector<cv::Point3f>> allObjectPoints(imageCount);
    std::vector<std::vector<cv::Point2f>> allImagePoints(imageCount);

    for (jsize i = 0; i < imageCount; ++i) {
        auto objArr = (jfloatArray)env->GetObjectArrayElement(objectPointsArray, i);
        auto imgArr = (jfloatArray)env->GetObjectArrayElement(imagePointsArray, i);

        jsize objLen = env->GetArrayLength(objArr);
        jsize imgLen = env->GetArrayLength(imgArr);

        if (objLen / 3 != imgLen / 2) {
            LOGE("Point count mismatch in image %d: objPoints=%d, imgPoints=%d", i, objLen/3, imgLen/2);
            return nullptr;
        }

        jfloat* objElems = env->GetFloatArrayElements(objArr, nullptr);
        jfloat* imgElems = env->GetFloatArrayElements(imgArr, nullptr);

        for (jsize j = 0; j < objLen / 3; ++j) {
            allObjectPoints[i].emplace_back(objElems[j*3], objElems[j*3 + 1], objElems[j*3 + 2]);
            allImagePoints[i].emplace_back(imgElems[j*2], imgElems[j*2 + 1]);
        }

        env->ReleaseFloatArrayElements(objArr, objElems, JNI_ABORT);
        env->ReleaseFloatArrayElements(imgArr, imgElems, JNI_ABORT);
        env->DeleteLocalRef(objArr);
        env->DeleteLocalRef(imgArr);
    }

    CalibrationSolveResult solveResult = CalibrationSolver::solve(
        allObjectPoints,
        allImagePoints,
        cv::Size(width, height)
    );

    jclass resultClass = env->FindClass("com/openprofiler/native_bridge/NativeCalibrationResult");
    if (!resultClass || clearPendingJniException(env, "FindClass(NativeCalibrationResult)")) {
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(D[D[D[D[D[D[I[DZ)V");
    if (!ctor || clearPendingJniException(env, "GetMethodID(NativeCalibrationResult.<init>)")) {
        return nullptr;
    }

    jdoubleArray cameraMatrixArr = env->NewDoubleArray(9);
    if (solveResult.success) {
        env->SetDoubleArrayRegion(cameraMatrixArr, 0, 9, (jdouble*)solveResult.cameraMatrix.data);
    }

    jsize distLen = solveResult.success ? solveResult.distCoeffs.total() : 0;
    jdoubleArray distCoeffsArr = env->NewDoubleArray(distLen);
    if (solveResult.success && distLen > 0) {
        env->SetDoubleArrayRegion(distCoeffsArr, 0, distLen, (jdouble*)solveResult.distCoeffs.data);
    }

    jsize stdIntLen = solveResult.success ? solveResult.stdDevIntrinsics.total() : 0;
    jdoubleArray stdIntArr = env->NewDoubleArray(stdIntLen);
    if (solveResult.success && stdIntLen > 0) {
        env->SetDoubleArrayRegion(stdIntArr, 0, stdIntLen, (jdouble*)solveResult.stdDevIntrinsics.data);
    }

    jsize stdExtLen = solveResult.success ? solveResult.stdDevExtrinsics.total() : 0;
    jdoubleArray stdExtArr = env->NewDoubleArray(stdExtLen);
    if (solveResult.success && stdExtLen > 0) {
        env->SetDoubleArrayRegion(stdExtArr, 0, stdExtLen, (jdouble*)solveResult.stdDevExtrinsics.data);
    }

    jsize perViewLen = solveResult.success ? solveResult.perViewErrors.size() : 0;
    jdoubleArray perViewArr = env->NewDoubleArray(perViewLen);
    if (solveResult.success && perViewLen > 0) {
        env->SetDoubleArrayRegion(perViewArr, 0, perViewLen, solveResult.perViewErrors.data());
    }

    jsize rejectedLen = solveResult.success ? solveResult.rejectedFrames.size() : 0;
    jintArray rejectedArr = env->NewIntArray(rejectedLen);
    if (solveResult.success && rejectedLen > 0) {
        env->SetIntArrayRegion(rejectedArr, 0, rejectedLen, (jint*)solveResult.rejectedFrames.data());
    }

    jsize residualLen = solveResult.success ? solveResult.residuals.size() : 0;
    jdoubleArray residualArr = env->NewDoubleArray(residualLen);
    if (solveResult.success && residualLen > 0) {
        env->SetDoubleArrayRegion(residualArr, 0, residualLen, solveResult.residuals.data());
    }

    jobject obj = env->NewObject(
        resultClass,
        ctor,
        solveResult.rms,
        cameraMatrixArr,
        distCoeffsArr,
        stdIntArr,
        stdExtArr,
        perViewArr,
        rejectedArr,
        residualArr,
        static_cast<jboolean>(solveResult.success ? JNI_TRUE : JNI_FALSE)
    );

    return obj;
}

} // extern "C"
