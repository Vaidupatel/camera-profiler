#ifndef CAMERA_PROFILER_CORNER_VALIDATOR_H
#define CAMERA_PROFILER_CORNER_VALIDATOR_H

#include <vector>
#include <string>
#include <set>
#include <opencv2/core.hpp>

namespace openprofiler {

struct ValidationResult {
    bool isValid = false;
    std::string rejectionReason = "";
    float confidence = 0.0f;
};

class CornerValidator {
public:
    static ValidationResult validate(
        int detectedMarkerCount,
        int detectedCornerCount,
        const std::vector<int>& markerIds,
        const std::vector<int>& charucoIds,
        const std::vector<cv::Point2f>& charucoCorners,
        int imgWidth,
        int imgHeight,
        int minMarkers,
        int minCorners,
        int totalExpectedMarkers,
        int totalExpectedCorners,
        float minConfidenceThreshold
    );
};

} // namespace openprofiler

#endif // CAMERA_PROFILER_CORNER_VALIDATOR_H
