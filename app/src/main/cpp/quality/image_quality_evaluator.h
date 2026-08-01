#ifndef CAMERA_PROFILER_IMAGE_QUALITY_EVALUATOR_H
#define CAMERA_PROFILER_IMAGE_QUALITY_EVALUATOR_H

#include <opencv2/core.hpp>
#include <cstdint>

namespace openprofiler {

/**
 * Raw image-domain quality measurements. Values are measured, never estimated.
 */
struct ImageQualityMeasurements {
    double blurLaplacianVariance = 0.0;
    double sharpnessGradientMagnitude = 0.0;
    double meanBrightness = 0.0;
    double darkPixelRatio = 0.0;
    double brightPixelRatio = 0.0;
    double contrastScore = 0.0;
    double noiseScore = 0.0;
    double motionMad = 0.0;
    bool hasPriorFrame = false;
    int frameWidth = 0;
    int frameHeight = 0;
    bool success = false;
};

/**
 * Production image quality evaluator with reusable OpenCV Mats.
 * Thread-confined: caller must serialize access.
 */
class ImageQualityEvaluator {
public:
    ImageQualityEvaluator();
    ~ImageQualityEvaluator();

    ImageQualityMeasurements evaluate(
        const uint8_t* yData,
        int width,
        int height,
        int yRowStride,
        int rotationDegrees,
        double darkPixelThreshold,
        double brightPixelThreshold
    );

    void resetMotionState();

private:
    cv::Mat grayWorking_;
    cv::Mat laplacian_;
    cv::Mat sobelX_;
    cv::Mat sobelY_;
    cv::Mat sobelMag_;
    cv::Mat previousGray_;
    cv::Mat absDiff_;
    bool hasPrevious_ = false;

    void ensureCapacity(int width, int height);
    static void rotateGray(const cv::Mat& src, int rotationDegrees, cv::Mat& dst);
};

} // namespace openprofiler

#endif // CAMERA_PROFILER_IMAGE_QUALITY_EVALUATOR_H
