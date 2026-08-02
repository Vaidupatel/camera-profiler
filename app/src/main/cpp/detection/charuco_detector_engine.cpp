#include "charuco_detector_engine.h"
#include "image_buffer_utils.h"
#include <android/log.h>
#include <cmath>
#include <regex>
#include <opencv2/imgproc.hpp>

#define LOG_TAG "CharucoDetectorEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace openprofiler {

CharucoDetectorEngine::CharucoDetectorEngine() = default;
CharucoDetectorEngine::~CharucoDetectorEngine() = default;

cv::aruco::PredefinedDictionaryType CharucoDetectorEngine::getDictionaryType(const std::string& name) {
    if (name == "DICT_4X4_50") return cv::aruco::DICT_4X4_50;
    if (name == "DICT_4X4_100") return cv::aruco::DICT_4X4_100;
    if (name == "DICT_4X4_250") return cv::aruco::DICT_4X4_250;
    if (name == "DICT_4X4_1000") return cv::aruco::DICT_4X4_1000;
    if (name == "DICT_5X5_50") return cv::aruco::DICT_5X5_50;
    if (name == "DICT_5X5_100") return cv::aruco::DICT_5X5_100;
    if (name == "DICT_5X5_250") return cv::aruco::DICT_5X5_250;
    if (name == "DICT_5X5_1000") return cv::aruco::DICT_5X5_1000;
    if (name == "DICT_6X6_50") return cv::aruco::DICT_6X6_50;
    if (name == "DICT_6X6_100") return cv::aruco::DICT_6X6_100;
    if (name == "DICT_6X6_250") return cv::aruco::DICT_6X6_250;
    if (name == "DICT_6X6_1000") return cv::aruco::DICT_6X6_1000;
    if (name == "DICT_7X7_50") return cv::aruco::DICT_7X7_50;
    if (name == "DICT_7X7_100") return cv::aruco::DICT_7X7_100;
    if (name == "DICT_7X7_250") return cv::aruco::DICT_7X7_250;
    if (name == "DICT_7X7_1000") return cv::aruco::DICT_7X7_1000;
    if (name == "DICT_ARUCO_ORIGINAL") return cv::aruco::DICT_ARUCO_ORIGINAL;
    return cv::aruco::DICT_5X5_1000;
}

static std::string extractJsonValue(const std::string& json, const std::string& key) {
    std::regex regexPattern("\"" + key + "\"\\s*:\\s*\"?([^\",\\}\n]+)\"?");
    std::smatch match;
    if (std::regex_search(json, match, regexPattern)) {
        return match[1].str();
    }
    return "";
}

bool CharucoDetectorEngine::initialize(const std::string& configJson) {
    std::string dictStr = extractJsonValue(configJson, "dictionaryName");
    if (!dictStr.empty()) dictionaryName_ = dictStr;

    std::string sxStr = extractJsonValue(configJson, "squaresX");
    if (!sxStr.empty()) squaresX_ = std::stoi(sxStr);

    std::string syStr = extractJsonValue(configJson, "squaresY");
    if (!syStr.empty()) squaresY_ = std::stoi(syStr);

    std::string slStr = extractJsonValue(configJson, "squareLengthMm");
    if (!slStr.empty()) squareLengthMm_ = std::stof(slStr);

    std::string mlStr = extractJsonValue(configJson, "markerLengthMm");
    if (!mlStr.empty()) markerLengthMm_ = std::stof(mlStr);

    std::string mmStr = extractJsonValue(configJson, "minMarkers");
    if (!mmStr.empty()) minMarkers_ = std::stoi(mmStr);

    std::string mcStr = extractJsonValue(configJson, "minCorners");
    if (!mcStr.empty()) minCorners_ = std::stoi(mcStr);

    std::string confStr = extractJsonValue(configJson, "minConfidence");
    if (!confStr.empty()) minConfidence_ = std::stof(confStr);

    cv::aruco::PredefinedDictionaryType dictType = getDictionaryType(dictionaryName_);
    dictionary_ = cv::aruco::getPredefinedDictionary(dictType);

    // Board dimensions in meters for internal OpenCV mathematics
    float squareLenM = squareLengthMm_ / 1000.0f;
    float markerLenM = markerLengthMm_ / 1000.0f;

    board_ = cv::makePtr<cv::aruco::CharucoBoard>(
        cv::Size(squaresX_, squaresY_),
        squareLenM,
        markerLenM,
        dictionary_
    );

    cv::aruco::CharucoParameters charucoParams;
    cv::aruco::DetectorParameters detectorParams = cv::aruco::DetectorParameters();

    // Enable subpixel corner refinement inside detector params
    detectorParams.cornerRefinementMethod = cv::aruco::CORNER_REFINE_SUBPIX;
    detectorParams.cornerRefinementWinSize = 5;
    detectorParams.cornerRefinementMaxIterations = 30;

    cv::aruco::RefineParameters refineParams;

    charucoDetector_ = std::make_unique<cv::aruco::CharucoDetector>(
        *board_, charucoParams, detectorParams, refineParams
    );

    initialized_ = true;
    LOGD("CharucoDetectorEngine initialized: %dx%d board, dict %s", squaresX_, squaresY_, dictionaryName_.c_str());
    return true;
}

DetectionEngineResult CharucoDetectorEngine::detect(
    const cv::Mat& inputGrayMat,
    int imgWidth,
    int imgHeight,
    const cv::Mat& cameraMatrix,
    const cv::Mat& distCoeffs
) {
    auto tStart = std::chrono::high_resolution_clock::now();
    DetectionEngineResult result;
    result.dictionaryName = dictionaryName_;
    result.stageTimingsMs.resize(5, 0);

    if (!initialized_ || inputGrayMat.empty()) {
        result.boardDetected = false;
        result.rejectedReason = "Detector engine not initialized or frame empty";
        return result;
    }

    // Adaptive contrast preprocessing if necessary
    cv::Mat processedGray;
    ImageBufferUtils::applyAdaptivePreprocessing(inputGrayMat, processedGray);

    // Stage 1 & 2: ArUco marker detection & ChArUco interpolation
    auto t1 = std::chrono::high_resolution_clock::now();

    cv::Mat charucoCornersMat;
    cv::Mat charucoIdsMat;
    std::vector<std::vector<cv::Point2f>> markerCorners;
    std::vector<int> markerIdsVec;

    charucoDetector_->detectBoard(processedGray, charucoCornersMat, charucoIdsMat, markerCorners, markerIdsVec);

    auto t2 = std::chrono::high_resolution_clock::now();
    long markerDetectionMs = std::chrono::duration_cast<std::chrono::milliseconds>(t2 - t1).count();
    result.stageTimingsMs[1] = markerDetectionMs;

    // Convert ChArUco corners to vector
    std::vector<cv::Point2f> charucoCornersVec;
    std::vector<int> charucoIdsVec;

    if (!charucoCornersMat.empty() && !charucoIdsMat.empty()) {
        if (charucoCornersMat.isContinuous()) {
            charucoCornersVec.assign(
                (cv::Point2f*)charucoCornersMat.datastart,
                (cv::Point2f*)charucoCornersMat.dataend
            );
        } else {
            for (int r = 0; r < charucoCornersMat.rows; ++r) {
                charucoCornersVec.push_back(charucoCornersMat.at<cv::Point2f>(r, 0));
            }
        }
        for (int r = 0; r < charucoIdsMat.rows; ++r) {
            charucoIdsVec.push_back(charucoIdsMat.at<int>(r, 0));
        }
    }

    auto t3 = std::chrono::high_resolution_clock::now();
    long charucoInterpMs = std::chrono::duration_cast<std::chrono::milliseconds>(t3 - t2).count();
    result.stageTimingsMs[2] = charucoInterpMs;

    // Stage 3: Subpixel corner refinement + measured per-corner precision
    // Precision = 1/(1 + ||Δ||) where Δ is the cornerSubPix displacement (px).
    // Never assign a constant — blurrier / unstable corners move more ⇒ lower score.
    result.cornerPrecisions.assign(charucoCornersVec.size(), 0.0f);
    if (!charucoCornersVec.empty()) {
        std::vector<cv::Point2f> beforeRefine = charucoCornersVec;
        cv::TermCriteria criteria(cv::TermCriteria::EPS + cv::TermCriteria::COUNT, 30, 0.01);
        cv::cornerSubPix(processedGray, charucoCornersVec, cv::Size(5, 5), cv::Size(-1, -1), criteria);
        for (size_t i = 0; i < charucoCornersVec.size(); ++i) {
            float dx = charucoCornersVec[i].x - beforeRefine[i].x;
            float dy = charucoCornersVec[i].y - beforeRefine[i].y;
            float displacement = std::sqrt(dx * dx + dy * dy);
            result.cornerPrecisions[i] = 1.0f / (1.0f + displacement);
        }
    }

    auto t4 = std::chrono::high_resolution_clock::now();
    long subpixRefineMs = std::chrono::duration_cast<std::chrono::milliseconds>(t4 - t3).count();
    result.stageTimingsMs[3] = subpixRefineMs;

    // Stage 4: Corner & Board Quality Validation
    int totalExpectedMarkers = (squaresX_ * squaresY_) / 2;
    int totalExpectedCorners = (squaresX_ - 1) * (squaresY_ - 1);

    ValidationResult val = CornerValidator::validate(
        static_cast<int>(markerIdsVec.size()),
        static_cast<int>(charucoCornersVec.size()),
        markerIdsVec,
        charucoIdsVec,
        charucoCornersVec,
        imgWidth,
        imgHeight,
        minMarkers_,
        minCorners_,
        totalExpectedMarkers,
        totalExpectedCorners,
        minConfidence_
    );

    result.markerCount = static_cast<int>(markerIdsVec.size());
    result.charucoCornerCount = static_cast<int>(charucoCornersVec.size());
    result.markerIds = markerIdsVec;
    result.charucoIds = charucoIdsVec;
    result.charucoCorners = charucoCornersVec;
    result.markerOutlines = markerCorners;
    result.detectionConfidence = val.confidence;
    result.rejectedReason = val.rejectionReason;

    if (!val.isValid) {
        result.boardDetected = false;
        auto tEnd = std::chrono::high_resolution_clock::now();
        result.processingTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(tEnd - tStart).count();
        result.stageTimingsMs[0] = result.processingTimeMs;
        return result;
    }

    // Stage 5: Board Pose Estimation & UI Overlay projection
    PoseEstimationResult poseRes = BoardPoseEstimator::estimatePose(
        charucoCornersMat,
        charucoIdsMat,
        board_,
        imgWidth,
        imgHeight,
        cameraMatrix,
        distCoeffs
    );

    auto t5 = std::chrono::high_resolution_clock::now();
    long poseEstMs = std::chrono::duration_cast<std::chrono::milliseconds>(t5 - t4).count();
    result.stageTimingsMs[4] = poseEstMs;

    if (poseRes.success) {
        // Convert tvec from meters to millimeters
        result.rvec = poseRes.rvec;
        result.tvec = poseRes.tvec * 1000.0;
        result.boardAxes2D = poseRes.axes2D;
        result.boundingBox2D = poseRes.boundingBox2D;
    }

    result.boardDetected = true;
    auto tEnd = std::chrono::high_resolution_clock::now();
    result.processingTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(tEnd - tStart).count();
    result.stageTimingsMs[0] = result.processingTimeMs;

    return result;
}

} // namespace openprofiler
