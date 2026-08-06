#include "calibration_solver.h"
#include <opencv2/calib3d.hpp>
#include <android/log.h>

#define LOG_TAG "CalibrationSolver"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace openprofiler {

CalibrationSolveResult CalibrationSolver::solve(
    const std::vector<std::vector<cv::Point3f>>& objectPoints,
    const std::vector<std::vector<cv::Point2f>>& imagePoints,
    cv::Size imageSize
) {
    CalibrationSolveResult result;

    if (objectPoints.empty() || imagePoints.empty() || objectPoints.size() != imagePoints.size()) {
        LOGE("Calibration failed: Empty or mismatched point vectors");
        result.success = false;
        return result;
    }

    LOGD("Starting cv::calibrateCamera with %zu images at %dx%d",
         objectPoints.size(), imageSize.width, imageSize.height);

    cv::Mat cameraMatrix = cv::Mat::eye(3, 3, CV_64F);
    cv::Mat distCoeffs = cv::Mat::zeros(5, 1, CV_64F);
    std::vector<cv::Mat> rvecs, tvecs;

    try {
        // Standard calibration solve.
        // We do NOT use CALIB_USE_INTRINSIC_GUESS to ensure reproducibility from scratch.
        // We estimate 5 distortion coefficients (k1, k2, p1, p2, k3).
        int flags = 0;
        result.rms = cv::calibrateCamera(
            objectPoints,
            imagePoints,
            imageSize,
            cameraMatrix,
            distCoeffs,
            rvecs,
            tvecs,
            flags
        );

        result.cameraMatrix = cameraMatrix;
        result.distCoeffs = distCoeffs;
        result.success = true;

        LOGD("Calibration successful. RMS = %.4f", result.rms);
    } catch (const cv::Exception& e) {
        LOGE("OpenCV Exception during calibration: %s", e.what());
        result.success = false;
    }

    return result;
}

} // namespace openprofiler
