#ifndef CAMERA_PROFILER_BOARD_POSE_ESTIMATOR_H
#define CAMERA_PROFILER_BOARD_POSE_ESTIMATOR_H

#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/calib3d.hpp>
#include <opencv2/objdetect/charuco_detector.hpp>

namespace openprofiler {

struct PoseEstimationResult {
    bool success = false;
    cv::Mat rvec; // 3x1 CV_64F
    cv::Mat tvec; // 3x1 CV_64F
    std::vector<cv::Point2f> axes2D; // [origin, xAxis, yAxis, zAxis]
    std::vector<cv::Point2f> boundingBox2D; // 4 corner points
};

class BoardPoseEstimator {
public:
    /**
     * Estimates 3D board pose (rvec, tvec) and projects axes/bounding box onto 2D image plane.
     */
    static PoseEstimationResult estimatePose(
        const cv::Mat& charucoCorners,
        const cv::Mat& charucoIds,
        const cv::Ptr<cv::aruco::CharucoBoard>& board,
        int imgWidth,
        int imgHeight,
        const cv::Mat& cameraMatrix,
        const cv::Mat& distCoeffs
    );
};

} // namespace openprofiler

#endif // CAMERA_PROFILER_BOARD_POSE_ESTIMATOR_H
