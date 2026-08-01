#ifndef CAMERA_PROFILER_IMAGE_BUFFER_UTILS_H
#define CAMERA_PROFILER_IMAGE_BUFFER_UTILS_H

#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

namespace openprofiler {

class ImageBufferUtils {
public:
    /**
     * Converts JNI DirectByteBuffers from CameraX YUV_420_888 plane format to a grayscale cv::Mat.
     * Performs rotation if required based on rotationDegrees.
     */
    static bool yuvPlanesToGrayMat(
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
    );

    /**
     * Evaluates image contrast and applies adaptive CLAHE enhancement if image contrast is low.
     */
    static void applyAdaptivePreprocessing(const cv::Mat& inputGray, cv::Mat& outputGray);
};

} // namespace openprofiler

#endif // CAMERA_PROFILER_IMAGE_BUFFER_UTILS_H
