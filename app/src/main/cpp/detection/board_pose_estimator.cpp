#include "board_pose_estimator.h"
#include <algorithm>

namespace openprofiler {

PoseEstimationResult BoardPoseEstimator::estimatePose(
    const cv::Mat& charucoCorners,
    const cv::Mat& charucoIds,
    const cv::Ptr<cv::aruco::CharucoBoard>& board,
    int imgWidth,
    int imgHeight,
    const cv::Mat& inCameraMatrix,
    const cv::Mat& inDistCoeffs
) {
    PoseEstimationResult res;
    if (charucoCorners.empty() || charucoIds.empty() || board.empty()) {
        res.success = false;
        return res;
    }

    // Scientific integrity: never fabricate K. Callers must supply device-reported
    // factory seed intrinsics (or calibrated K). Empty/invalid K ⇒ no pose.
    cv::Mat K = inCameraMatrix.empty() ? cv::Mat() : inCameraMatrix.clone();
    if (K.empty() || K.rows != 3 || K.cols != 3 || K.type() != CV_64F) {
        if (!K.empty() && K.type() != CV_64F) {
            K.convertTo(K, CV_64F);
        }
    }
    if (K.empty() || K.rows != 3 || K.cols != 3) {
        res.success = false;
        return res;
    }

    cv::Mat D = inDistCoeffs.empty() ? cv::Mat::zeros(5, 1, CV_64F) : inDistCoeffs.clone();
    if (D.type() != CV_64F) {
        D.convertTo(D, CV_64F);
    }

    (void)imgWidth;
    (void)imgHeight;

    std::vector<cv::Point3f> objPoints;
    std::vector<cv::Point2f> imgPoints;

    board->matchImagePoints(charucoCorners, charucoIds, objPoints, imgPoints);

    if (objPoints.size() < 4 || imgPoints.size() < 4) {
        res.success = false;
        return res;
    }

    cv::Mat rvec = cv::Mat::zeros(3, 1, CV_64F);
    cv::Mat tvec = cv::Mat::zeros(3, 1, CV_64F);

    bool pnpSuccess = cv::solvePnP(objPoints, imgPoints, K, D, rvec, tvec, false, cv::SOLVEPNP_ITERATIVE);
    if (!pnpSuccess) {
        res.success = false;
        return res;
    }

    res.success = true;
    res.rvec = rvec;
    res.tvec = tvec;

    // Project 3D Board Axes (Origin, X, Y, Z) — UI overlay only
    float axisLength = board->getSquareLength() * 2.0f; // 2 square units
    std::vector<cv::Point3f> axisPoints3D = {
        cv::Point3f(0, 0, 0),
        cv::Point3f(axisLength, 0, 0),
        cv::Point3f(0, axisLength, 0),
        cv::Point3f(0, 0, axisLength)
    };

    cv::projectPoints(axisPoints3D, rvec, tvec, K, D, res.axes2D);

    // Pose-reprojected board outline — UI only; TARGET_COVERAGE uses observed corners.
    cv::Size boardGrid = board->getChessboardSize();
    float totalW = boardGrid.width * board->getSquareLength();
    float totalH = boardGrid.height * board->getSquareLength();

    std::vector<cv::Point3f> bbox3D = {
        cv::Point3f(0, 0, 0),
        cv::Point3f(totalW, 0, 0),
        cv::Point3f(totalW, totalH, 0),
        cv::Point3f(0, totalH, 0)
    };

    cv::projectPoints(bbox3D, rvec, tvec, K, D, res.boundingBox2D);

    return res;
}

} // namespace openprofiler
