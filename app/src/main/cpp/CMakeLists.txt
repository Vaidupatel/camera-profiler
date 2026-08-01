cmake_minimum_required(VERSION 3.22.1)

project("camera_profiler_native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enforce 64 KB page alignment for maximum compatibility across 4 KB, 16 KB & 64 KB Android devices
add_link_options("-Wl,-z,max-page-size=65536")

# Import prebuilt shared OpenCV library (libopencv_java4.so)
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(
    opencv_java4
    PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_java4.so"
)

add_library(
    camera_profiler_native
    SHARED
    jni/native_bridge.cpp
    jni/detection_jni.cpp
    jni/quality_jni.cpp
    common/image_buffer_utils.cpp
    detection/corner_validator.cpp
    detection/board_pose_estimator.cpp
    detection/charuco_detector_engine.cpp
    quality/image_quality_evaluator.cpp
)

target_include_directories(
    camera_profiler_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/common
    ${CMAKE_CURRENT_SOURCE_DIR}/detection
    ${CMAKE_CURRENT_SOURCE_DIR}/quality
    ${CMAKE_CURRENT_SOURCE_DIR}/opencv/sdk/native/jni/include
)

target_link_options(
    camera_profiler_native
    PRIVATE
    "-Wl,-z,max-page-size=65536"
)

find_library(log-lib log)

target_link_libraries(
    camera_profiler_native
    ${log-lib}
    opencv_java4
)
