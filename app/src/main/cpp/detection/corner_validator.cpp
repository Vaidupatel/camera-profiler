#include "corner_validator.h"
#include <cmath>
#include <algorithm>

namespace openprofiler {

ValidationResult CornerValidator::validate(
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
) {
    ValidationResult res;

    // 1. Minimum marker count check
    if (detectedMarkerCount < minMarkers) {
        res.isValid = false;
        res.rejectionReason = "Insufficient markers detected (" +
            std::to_string(detectedMarkerCount) + " < " + std::to_string(minMarkers) + ")";
        res.confidence = 0.0f;
        return res;
    }

    // 2. Minimum ChArUco corner count check
    if (detectedCornerCount < minCorners) {
        res.isValid = false;
        res.rejectionReason = "Insufficient ChArUco corners detected (" +
            std::to_string(detectedCornerCount) + " < " + std::to_string(minCorners) + ")";
        res.confidence = 0.0f;
        return res;
    }

    // 3. Duplicate marker rejection
    std::set<int> uniqueMarkerIds(markerIds.begin(), markerIds.end());
    if (uniqueMarkerIds.size() != markerIds.size()) {
        res.isValid = false;
        res.rejectionReason = "Duplicate marker IDs detected";
        res.confidence = 0.0f;
        return res;
    }

    // 4. Corner ID uniqueness
    std::set<int> uniqueCornerIds(charucoIds.begin(), charucoIds.end());
    if (uniqueCornerIds.size() != charucoIds.size()) {
        res.isValid = false;
        res.rejectionReason = "Duplicate ChArUco corner IDs detected";
        res.confidence = 0.0f;
        return res;
    }

    // 5. Board bounds check (ensure corners lie within image boundaries with 2px margin)
    constexpr float margin = 2.0f;
    for (const auto& pt : charucoCorners) {
        if (pt.x < margin || pt.x >= (imgWidth - margin) ||
            pt.y < margin || pt.y >= (imgHeight - margin)) {
            res.isValid = false;
            res.rejectionReason = "Corners detected outside valid image boundary margin";
            res.confidence = 0.0f;
            return res;
        }
    }

    // 6. Corner spatial uniqueness check (no two corners identical within < 1.0 px distance)
    for (size_t i = 0; i < charucoCorners.size(); ++i) {
        for (size_t j = i + 1; j < charucoCorners.size(); ++j) {
            float dx = charucoCorners[i].x - charucoCorners[j].x;
            float dy = charucoCorners[i].y - charucoCorners[j].y;
            if (std::sqrt(dx * dx + dy * dy) < 1.0f) {
                res.isValid = false;
                res.rejectionReason = "Overlapping duplicate corner coordinates detected";
                res.confidence = 0.0f;
                return res;
            }
        }
    }

    // Calculate detection confidence score
    float markerRatio = totalExpectedMarkers > 0 ?
        static_cast<float>(detectedMarkerCount) / totalExpectedMarkers : 1.0f;
    float cornerRatio = totalExpectedCorners > 0 ?
        static_cast<float>(detectedCornerCount) / totalExpectedCorners : 1.0f;

    res.confidence = std::min(1.0f, (markerRatio * 0.4f + cornerRatio * 0.6f));

    if (res.confidence < minConfidenceThreshold) {
        res.isValid = false;
        res.rejectionReason = "Detection confidence below threshold (" +
            std::to_string(res.confidence) + " < " + std::to_string(minConfidenceThreshold) + ")";
        return res;
    }

    res.isValid = true;
    res.rejectionReason = "";
    return res;
}

} // namespace openprofiler
