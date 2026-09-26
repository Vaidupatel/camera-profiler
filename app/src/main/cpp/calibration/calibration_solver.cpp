#include "calibration_solver.h"
#include <opencv2/calib3d.hpp>
#include <android/log.h>
#include <numeric>
#include <algorithm>

#define LOG_TAG "CalibrationSolver"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace openprofiler {

CalibrationSolveResult CalibrationSolver::solve(
    const std::vector<std::vector<cv::Point3f>>& objectPoints,
    const std::vector<std::vector<cv::Point2f>>& imagePoints,
    cv::Size imageSize,
    bool outlierRejection
) {
    CalibrationSolveResult result;

    if (objectPoints.empty() || imagePoints.empty() || objectPoints.size() != imagePoints.size()) {
        LOGE("Calibration failed: Empty or mismatched point vectors");
        result.success = false;
        return result;
    }

    std::vector<std::vector<cv::Point3f>> currentObjPoints = objectPoints;
    std::vector<std::vector<cv::Point2f>> currentImgPoints = imagePoints;
    std::vector<int> originalIndices(objectPoints.size());
    std::iota(originalIndices.begin(), originalIndices.end(), 0);

    bool converged = false;
    int iteration = 0;
    const int maxIterations = 5;
    const double outlierThresholdMultiplier = 2.5; // Stricter threshold for production
    const int minFramesForRejection = 10;

    while (!converged && iteration < maxIterations) {
        LOGD("Starting calibration iteration %d with %zu images",
             iteration, currentObjPoints.size());

        cv::Mat cameraMatrix = cv::Mat::eye(3, 3, CV_64F);
        cv::Mat distCoeffs = cv::Mat::zeros(5, 1, CV_64F);
        std::vector<cv::Mat> rvecs, tvecs;
        cv::Mat stdDevIntrinsics, stdDevExtrinsics, perViewErrors;

        try {
            result.rms = cv::calibrateCamera(
                currentObjPoints,
                currentImgPoints,
                imageSize,
                cameraMatrix,
                distCoeffs,
                rvecs,
                tvecs,
                stdDevIntrinsics,
                stdDevExtrinsics,
                perViewErrors,
                0 // Default flags: estimate 5 distortion coeffs
            );

            result.cameraMatrix = cameraMatrix;
            result.distCoeffs = distCoeffs;
            result.stdDevIntrinsics = stdDevIntrinsics;
            result.stdDevExtrinsics = stdDevExtrinsics;
            result.rvecs = rvecs;
            result.tvecs = tvecs;

            result.perViewErrors.clear();
            for (int i = 0; i < perViewErrors.rows; ++i) {
                result.perViewErrors.push_back(perViewErrors.at<double>(i));
            }

            if (!outlierRejection || currentObjPoints.size() <= minFramesForRejection) {
                converged = true;
                break;
            }

            // Task 5: Outlier Rejection
            double meanError = 0;
            for (double e : result.perViewErrors) meanError += e;
            meanError /= result.perViewErrors.size();

            double maxError = 0;
            int worstFrameIdx = -1;
            for (int i = 0; i < result.perViewErrors.size(); ++i) {
                if (result.perViewErrors[i] > maxError) {
                    maxError = result.perViewErrors[i];
                    worstFrameIdx = i;
                }
            }

            // Reject worst frame if it's significantly worse than average
            if (maxError > meanError * outlierThresholdMultiplier && maxError > 0.5) {
                LOGD("Rejecting outlier frame %d with error %.4f (mean %.4f)",
                     originalIndices[worstFrameIdx], maxError, meanError);
                result.rejectedFrames.push_back(originalIndices[worstFrameIdx]);

                currentObjPoints.erase(currentObjPoints.begin() + worstFrameIdx);
                currentImgPoints.erase(currentImgPoints.begin() + worstFrameIdx);
                originalIndices.erase(originalIndices.begin() + worstFrameIdx);
                iteration++;
            } else {
                converged = true;
            }

        } catch (const cv::Exception& e) {
            LOGE("OpenCV Exception during calibration: %s", e.what());
            result.success = false;
            return result;
        }
    }

    result.success = true;

    // Task 7: Validation - Compute per-corner residuals
    result.residuals.clear();
    for (size_t i = 0; i < currentObjPoints.size(); ++i) {
        std::vector<cv::Point2f> projected;
        cv::projectPoints(currentObjPoints[i], result.rvecs[i], result.tvecs[i], result.cameraMatrix, result.distCoeffs, projected);
        for (size_t j = 0; j < projected.size(); ++j) {
            result.residuals.push_back(projected[j].x - currentImgPoints[i][j].x);
            result.residuals.push_back(projected[j].y - currentImgPoints[i][j].y);
        }
    }

    LOGD("Calibration successful. Final RMS = %.4f", result.rms);
    return result;
}

} // namespace openprofiler
