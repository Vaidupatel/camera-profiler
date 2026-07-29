cmake_minimum_required(VERSION 3.22.1)

project("camera_profiler_native" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enforce 16 KB page alignment for compatibility with 16 KB & 4 KB Android devices
add_link_options("-Wl,-z,max-page-size=16384")

# OpenCV will be configured here in a future phase
# find_package(OpenCV REQUIRED)

add_library(
    camera_profiler_native
    SHARED
    jni/native_bridge.cpp
)

target_include_directories(
    camera_profiler_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/common
)

target_link_options(
    camera_profiler_native
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

find_library(log-lib log)

target_link_libraries(
    camera_profiler_native
    ${log-lib}
)

