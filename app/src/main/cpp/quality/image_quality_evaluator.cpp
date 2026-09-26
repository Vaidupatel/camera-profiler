#include "image_quality_evaluator.h"

#include <android/log.h>
#include <opencv2/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <cmath>
#include <algorithm>
#include <vector>

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
    double brightPixelThreshold,
    int roiLeft,
    int roiTop,
    int roiRight,
    int roiBottom,
    const float* charucoCorners,
    const int* charucoIds,
    int numCorners,
    int squaresX,
    int squaresY
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

    // --- Target-Aware Metrics (Exposure, Contrast, Black Level) ---
    bool targetAwareSuccess = false;
    if (charucoCorners != nullptr && charucoIds != nullptr && numCorners >= 4 && squaresX > 1 && squaresY > 1) {
        const float cellSize = 40.0f;
        std::vector<cv::Point2f> imagePoints;
        std::vector<cv::Point2f> canonicalPoints;
        for (int i = 0; i < numCorners; ++i) {
            int id = charucoIds[i];
            float cx = static_cast<float>(id % (squaresX - 1)) + 1.0f;
            float cy = static_cast<float>(id / (squaresX - 1)) + 1.0f;
            imagePoints.push_back(cv::Point2f(charucoCorners[i * 2], charucoCorners[i * 2 + 1]));
            canonicalPoints.push_back(cv::Point2f(cx * cellSize, cy * cellSize));
        }

        cv::Mat H = cv::findHomography(imagePoints, canonicalPoints, cv::RANSAC);
        if (!H.empty()) {
            cv::Mat warped;
            cv::Size warpedSize(squaresX * cellSize, squaresY * cellSize);
            cv::warpPerspective(grayWorking_, warped, H, warpedSize);

            std::vector<double> whiteMeans;
            std::vector<double> blackMeans;
            long whiteSaturatedCount = 0;
            long whiteDarkCount = 0;
            long whiteTotalCount = 0;
            long blackClippedCount = 0;
            long blackTotalCount = 0;

            // Inset to avoid edges and partial pixels
            int inset = static_cast<int>(cellSize * 0.15f);
            int sampleSize = static_cast<int>(cellSize) - 2 * inset;

            for (int y = 0; y < squaresY; ++y) {
                for (int x = 0; x < squaresX; ++x) {
                    cv::Rect roi(x * cellSize + inset, y * cellSize + inset, sampleSize, sampleSize);
                    cv::Mat cell = warped(roi);
                    cv::Scalar mean, std;
                    cv::meanStdDev(cell, mean, std);

                    // Standard ChArUco: (0,0) is black. (x+y)%2 == 0 -> black, else white.
                    bool isWhite = ((x + y) % 2 != 0);
                    if (isWhite) {
                        whiteMeans.push_back(mean[0]);
                        whiteTotalCount += cell.total();
                        for (int r = 0; r < cell.rows; ++r) {
                            const uint8_t* p = cell.ptr<uint8_t>(r);
                            for (int c = 0; c < cell.cols; ++c) {
                                if (p[c] >= brightPixelThreshold) whiteSaturatedCount++;
                                if (p[c] <= darkPixelThreshold) whiteDarkCount++;
                            }
                        }
                    } else {
                        blackMeans.push_back(mean[0]);
                        blackTotalCount += cell.total();
                        for (int r = 0; r < cell.rows; ++r) {
                            const uint8_t* p = cell.ptr<uint8_t>(r);
                            for (int c = 0; c < cell.cols; ++c) {
                                if (p[c] <= darkPixelThreshold) blackClippedCount++;
                            }
                        }
                    }
                }
            }

            if (!whiteMeans.empty()) {
                double whiteSum = 0;
                for (double m : whiteMeans) whiteSum += m;
                out.whiteMeanBrightness = whiteSum / whiteMeans.size();
                out.whiteSaturationRatio = whiteTotalCount > 0 ? (double)whiteSaturatedCount / whiteTotalCount : 0.0;

                // Repurpose main fields for "Exposure" rule
                out.meanBrightness = out.whiteMeanBrightness;
                out.brightPixelRatio = out.whiteSaturationRatio;
                out.darkPixelRatio = whiteTotalCount > 0 ? (double)whiteDarkCount / whiteTotalCount : 0.0;

                targetAwareSuccess = true;
            }

            if (!blackMeans.empty()) {
                double blackSum = 0;
                for (double m : blackMeans) blackSum += m;
                out.blackMeanBrightness = blackSum / blackMeans.size();
                out.blackClippingRatio = blackTotalCount > 0 ? (double)blackClippedCount / blackTotalCount : 0.0;

                // Repurpose main fields for "Exposure" rule (darkRatio)
                out.darkPixelRatio = out.blackClippingRatio;
            }

            if (!whiteMeans.empty() && !blackMeans.empty()) {
                out.targetContrast = (out.whiteMeanBrightness - out.blackMeanBrightness) / 255.0;
                out.contrastScore = out.targetContrast;
            }
        }
    }

    if (!targetAwareSuccess) {
        // Fallback to global metrics if target detection fails or homography fails
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

        // --- Global Contrast: histogram spread (p5..p95) / 255 ---
        int hist[256] = {0};
        const int totalPixels = grayWorking_.rows * grayWorking_.cols;
        for (int r = 0; r < grayWorking_.rows; ++r) {
            const uint8_t* row = grayWorking_.ptr<uint8_t>(r);
            for (int c = 0; c < grayWorking_.cols; ++c) {
                hist[row[c]]++;
            }
        }
        const int p5Target = std::max(1, totalPixels / 20);
        const int p95Target = std::max(1, (totalPixels * 19) / 20);
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
    }

    // --- Noise: Stddev of Laplacian residual in non-edge regions ---
    // Algorithm: Partition image into 32x32 patches. Compute Laplacian stddev for each.
    // Use the 25th percentile to represent sensor noise while ignoring high-contrast board edges.
    // If a detector ROI is provided, completely exclude patches that intersect with it.
    std::vector<double> patchStdDevs;
    const int patchSize = 32;
    for (int r = 0; r <= grayWorking_.rows - patchSize; r += patchSize) {
        for (int c = 0; c <= grayWorking_.cols - patchSize; c += patchSize) {
            if (roiLeft >= 0 && roiRight >= 0 && roiTop >= 0 && roiBottom >= 0) {
                // Skip if patch intersects with the board ROI
                if (!(c + patchSize <= roiLeft || c >= roiRight ||
                      r + patchSize <= roiTop || r >= roiBottom)) {
                    continue;
                }
            }
            cv::Mat patch = laplacian_(cv::Rect(c, r, patchSize, patchSize));
            cv::Scalar pMean, pStd;
            cv::meanStdDev(patch, pMean, pStd);
            patchStdDevs.push_back(pStd[0]);
        }
    }

    if (patchStdDevs.empty()) {
        out.noiseScore = lapStd[0];
    } else {
        std::sort(patchStdDevs.begin(), patchStdDevs.end());
        out.noiseScore = patchStdDevs[patchStdDevs.size() / 4];
    }

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
