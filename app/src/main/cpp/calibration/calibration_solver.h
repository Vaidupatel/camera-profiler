#ifndef OPENPROFILER_CALIBRATION_SOLVER_H
#define OPENPROFILER_CALIBRATION_SOLVER_H

#include <opencv2/core.hpp>
#include <vector>

namespace openprofiler {

/**
 * Result of the native calibration solve.
 */
struct CalibrationSolveResult {
    double rms = 0.0;
    cv::Mat cameraMatrix;
    cv::Mat distCoeffs;
    cv::Mat stdDevIntrinsics;
    cv::Mat stdDevExtrinsics;
    std::vector<double> perViewErrors;
    std::vector<double> residuals;
    std::vector<int> rejectedFrames;
    std::vector<cv::Mat> rvecs;
    std::vector<cv::Mat> tvecs;
    bool success = false;
};

/**
 * Native engine for solving camera calibration using cv::calibrateCamera.
 */
class CalibrationSolver {
public:
    /**
     * Executes the calibration solve with optional outlier rejection.
     *
     * @param objectPoints 3D coordinates of board corners in meters.
     * @param imagePoints 2D coordinates of detected corners in pixels.
     * @param imageSize Resolution of the camera frames.
     * @param outlierRejection Whether to perform iterative outlier rejection.
     * @return CalibrationSolveResult containing RMS error and solved K/D.
     */
    static CalibrationSolveResult solve(
        const std::vector<std::vector<cv::Point3f>>& objectPoints,
        const std::vector<std::vector<cv::Point2f>>& imagePoints,
        cv::Size imageSize,
        bool outlierRejection = true
    );
};

} // namespace openprofiler

#endif // OPENPROFILER_CALIBRATION_SOLVER_H
