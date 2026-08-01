#include "image_quality_evaluator.h"

#include <android/log.h>
#include <opencv2/imgproc.hpp>
#include <cmath>
#include <algorithm>

#define LOG_TAG "ImageQualityEvaluator"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace openprofiler {

ImageQualityEvaluator::ImageQualityEvaluator() = default;
ImageQualityEvaluator::~ImageQualityEvaluator() = default;

void ImageQualityEvaluator::resetMotionState() {
    hasPrevious_ = false;
    previousGray_.release();
}

void ImageQualityEvaluator::ensureCapacity(int width, int height) {
    if (grayWorking_.rows != height || grayWorking_.cols != width) {
        grayWorking_.create(height, width, CV_8UC1);
        laplacian_.create(height, width, CV_64F);
        sobelX_.create(height, width, CV_32F);
        sobelY_.create(height, width, CV_32F);
        sobelMag_.create(height, width, CV_32F);
        absDiff_.create(height, width, CV_8UC1);
    }
}

void ImageQualityEvaluator::rotateGray(const cv::Mat& src, int rotationDegrees, cv::Mat& dst) {
    if (rotationDegrees == 90) {
        cv::rotate(src, dst, cv::ROTATE_90_CLOCKWISE);
    } else if (rotationDegrees == 180) {
        cv::rotate(src, dst, cv::ROTATE_180);
    } else if (rotationDegrees == 270) {
        cv::rotate(src, dst, cv::ROTATE_90_COUNTERCLOCKWISE);
    } else {
        src.copyTo(dst);
    }
}

ImageQualityMeasurements ImageQualityEvaluator::evaluate(
    const uint8_t* yData,
    int width,
    int height,
    int yRowStride,
    int rotationDegrees,
    double darkPixelThreshold,
    double brightPixelThreshold
) {
    ImageQualityMeasurements out;
    if (yData == nullptr || width <= 0 || height <= 0 || yRowStride < width) {
        LOGE("Invalid input for image quality evaluation");
        return out;
    }

    cv::Mat yView(height, width, CV_8UC1, const_cast<uint8_t*>(yData), static_cast<size_t>(yRowStride));

    int outW = width;
    int outH = height;
    if (rotationDegrees == 90 || rotationDegrees == 270) {
        outW = height;
        outH = width;
    }
    ensureCapacity(outW, outH);

    rotateGray(yView, rotationDegrees, grayWorking_);
    out.frameWidth = grayWorking_.cols;
    out.frameHeight = grayWorking_.rows;

    // --- Blur: variance of Laplacian ---
    cv::Laplacian(grayWorking_, laplacian_, CV_64F);
    cv::Scalar lapMean, lapStd;
    cv::meanStdDev(laplacian_, lapMean, lapStd);
    out.blurLaplacianVariance = lapStd[0] * lapStd[0];

    // --- Sharpness: mean Sobel gradient magnitude ---
    cv::Sobel(grayWorking_, sobelX_, CV_32F, 1, 0, 3);
    cv::Sobel(grayWorking_, sobelY_, CV_32F, 0, 1, 3);
    cv::magnitude(sobelX_, sobelY_, sobelMag_);
    out.sharpnessGradientMagnitude = cv::mean(sobelMag_)[0];

    // --- Exposure: mean + dark/bright ratios ---
    cv::Scalar meanVal, stdVal;
    cv::meanStdDev(grayWorking_, meanVal, stdVal);
    out.meanBrightness = meanVal[0];

    const double darkT = darkPixelThreshold;
    const double brightT = brightPixelThreshold;
    int darkCount = 0;
    int brightCount = 0;
    const int total = grayWorking_.rows * grayWorking_.cols;
    for (int r = 0; r < grayWorking_.rows; ++r) {
        const uint8_t* row = grayWorking_.ptr<uint8_t>(r);
        for (int c = 0; c < grayWorking_.cols; ++c) {
            const uint8_t v = row[c];
            if (v < darkT) ++darkCount;
            if (v > brightT) ++brightCount;
        }
    }
    out.darkPixelRatio = total > 0 ? static_cast<double>(darkCount) / total : 0.0;
    out.brightPixelRatio = total > 0 ? static_cast<double>(brightCount) / total : 0.0;

    // --- Contrast: histogram spread (p5..p95) / 255 ---
    int hist[256] = {0};
    for (int r = 0; r < grayWorking_.rows; ++r) {
        const uint8_t* row = grayWorking_.ptr<uint8_t>(r);
        for (int c = 0; c < grayWorking_.cols; ++c) {
            hist[row[c]]++;
        }
    }
    const int p5Target = std::max(1, total / 20);
    const int p95Target = std::max(1, (total * 19) / 20);
    int cumulative = 0;
    int p5 = 0;
    int p95 = 255;
    bool foundP5 = false;
    for (int i = 0; i < 256; ++i) {
        cumulative += hist[i];
        if (!foundP5 && cumulative >= p5Target) {
            p5 = i;
            foundP5 = true;
        }
        if (cumulative >= p95Target) {
            p95 = i;
            break;
        }
    }
    out.contrastScore = (p95 - p5) / 255.0;

    // --- Noise: stddev of Laplacian residual (high-frequency energy) ---
    // Reuse laplacian_ already computed; noise score = stddev of Laplacian.
    out.noiseScore = lapStd[0];

    // --- Motion: mean absolute difference vs prior frame ---
    if (hasPrevious_ &&
        previousGray_.rows == grayWorking_.rows &&
        previousGray_.cols == grayWorking_.cols) {
        cv::absdiff(grayWorking_, previousGray_, absDiff_);
        out.motionMad = cv::mean(absDiff_)[0];
        out.hasPriorFrame = true;
    } else {
        out.motionMad = 0.0;
        out.hasPriorFrame = false;
    }
    grayWorking_.copyTo(previousGray_);
    hasPrevious_ = true;

    out.success = true;
    return out;
}

} // namespace openprofiler
