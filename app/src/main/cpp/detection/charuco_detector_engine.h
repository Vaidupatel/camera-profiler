#ifndef CAMERA_PROFILER_CHARUCO_DETECTOR_ENGINE_H
#define CAMERA_PROFILER_CHARUCO_DETECTOR_ENGINE_H

#include <string>
#include <vector>
#include <memory>
#include <chrono>
#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>
#include <opencv2/objdetect/charuco_detector.hpp>

#include "corner_validator.h"
#include "board_pose_estimator.h"

namespace openprofiler {

struct DetectionEngineResult {
    bool boardDetected = false;
    std::string dictionaryName = "DICT_5X5_1000";
    int markerCount = 0;
    int charucoCornerCount = 0;
    std::vector<int> markerIds;
    std::vector<int> charucoIds;
    std::vector<cv::Point2f> charucoCorners;
    std::vector<float> cornerPrecisions;
    cv::Mat rvec;
    cv::Mat tvec;
    float detectionConfidence = 0.0f;
    long processingTimeMs = 0;
    std::string rejectedReason = "";

    // Overlay rendering data
    std::vector<std::vector<cv::Point2f>> markerOutlines;
    std::vector<cv::Point2f> boardAxes2D;
    std::vector<cv::Point2f> boundingBox2D;

    // Diagnostic stage timings [total, marker_detect, charuco_interp, subpix_refine, pose_est]
    std::vector<long> stageTimingsMs;
};

class CharucoDetectorEngine {
public:
    CharucoDetectorEngine();
    ~CharucoDetectorEngine();

    bool initialize(const std::string& configJson);

    DetectionEngineResult detect(
        const cv::Mat& grayMat,
        int imgWidth,
        int imgHeight,
        const cv::Mat& cameraMatrix = cv::Mat(),
        const cv::Mat& distCoeffs = cv::Mat()
    );

    bool isInitialized() const { return initialized_; }

private:
    bool initialized_ = false;

    // Config parameters
    std::string dictionaryName_ = "DICT_5X5_1000";
    int squaresX_ = 9;
    int squaresY_ = 6;
    float squareLengthMm_ = 30.0f;
    float markerLengthMm_ = 22.0f;
    int minMarkers_ = 4;
    int minCorners_ = 4;
    float minConfidence_ = 0.5f;

    cv::aruco::Dictionary dictionary_;
    cv::Ptr<cv::aruco::CharucoBoard> board_;
    std::unique_ptr<cv::aruco::CharucoDetector> charucoDetector_;

    cv::aruco::PredefinedDictionaryType getDictionaryType(const std::string& name);
};

} // namespace openprofiler

#endif // CAMERA_PROFILER_CHARUCO_DETECTOR_ENGINE_H
