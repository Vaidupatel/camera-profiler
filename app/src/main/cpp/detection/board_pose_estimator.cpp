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

    // Construct intrinsic camera matrix if not provided
    cv::Mat K = inCameraMatrix.clone();
    if (K.empty() || K.rows != 3 || K.cols != 3) {
        double focal = 0.8 * std::max(imgWidth, imgHeight);
        K = (cv::Mat_<double>(3, 3) <<
            focal, 0.0, imgWidth / 2.0,
            0.0, focal, imgHeight / 2.0,
            0.0, 0.0, 1.0);
    }

    cv::Mat D = inDistCoeffs.clone();
    if (D.empty()) {
        D = cv::Mat::zeros(5, 1, CV_64F);
    }

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

    // Project 3D Board Axes (Origin, X, Y, Z)
    float axisLength = board->getSquareLength() * 2.0f; // 2 square units
    std::vector<cv::Point3f> axisPoints3D = {
        cv::Point3f(0, 0, 0),
        cv::Point3f(axisLength, 0, 0),
        cv::Point3f(0, axisLength, 0),
        cv::Point3f(0, 0, axisLength)
    };

    cv::projectPoints(axisPoints3D, rvec, tvec, K, D, res.axes2D);

    // Project Board 2D Bounding Box (4 outer corners)
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
