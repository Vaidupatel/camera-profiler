#include "image_buffer_utils.h"
#include <android/log.h>

#define LOG_TAG "ImageBufferUtils"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace openprofiler {

bool ImageBufferUtils::yuvPlanesToGrayMat(
    JNIEnv* env,
    jobject yBuffer,
    jobject uBuffer,
    jobject vBuffer,
    jint yRowStride,
    jint uvRowStride,
    jint uvPixelStride,
    jint width,
    jint height,
    jint rotationDegrees,
    cv::Mat& outGrayMat
) {
    if (!yBuffer) {
        LOGE("Y plane buffer is null");
        return false;
    }

    uint8_t* yData = static_cast<uint8_t*>(env->GetDirectBufferAddress(yBuffer));
    if (!yData) {
        LOGE("Failed to get direct buffer address for Y plane");
        return false;
    }

    // Zero-copy view of Y-plane byte buffer
    cv::Mat yMat(height, width, CV_8UC1, yData, yRowStride);

    // Apply rotation if required
    cv::Mat rotatedMat;
    if (rotationDegrees == 90) {
        cv::rotate(yMat, rotatedMat, cv::ROTATE_90_CLOCKWISE);
    } else if (rotationDegrees == 180) {
        cv::rotate(yMat, rotatedMat, cv::ROTATE_180);
    } else if (rotationDegrees == 270) {
        cv::rotate(yMat, rotatedMat, cv::ROTATE_90_COUNTERCLOCKWISE);
    } else {
        rotatedMat = yMat.clone();
    }

    outGrayMat = rotatedMat;
    return true;
}

void ImageBufferUtils::applyAdaptivePreprocessing(const cv::Mat& inputGray, cv::Mat& outputGray) {
    if (inputGray.empty()) return;

    cv::Scalar meanVal, stdDevVal;
    cv::meanStdDev(inputGray, meanVal, stdDevVal);

    // Only apply CLAHE if standard deviation (contrast) is below threshold (< 35.0)
    if (stdDevVal[0] < 35.0) {
        cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
        clahe->apply(inputGray, outputGray);
    } else {
        outputGray = inputGray;
    }
}

} // namespace openprofiler
