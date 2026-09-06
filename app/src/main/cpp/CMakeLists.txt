# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project(lame-mp3-utils C CXX)

# Paths in this file are relative to app/src/main/cpp/ (where this file lives).
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/lamemp3)

# All LAME C sources (the vector/ subdir is x86/SSE-only, excluded).
file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/lamemp3/*.c)

add_library(lame-mp3-utils SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/lame-mp3-utils.cpp
        ${LAME_SOURCES})

# Disable the obnoxious warnings from old C and pull in standard headers
# (machine.h gates `#include <string.h>` behind STDC_HEADERS).
target_compile_options(lame-mp3-utils PRIVATE -O2 -w -DSTDC_HEADERS)

find_library(log-lib log)

target_link_libraries(lame-mp3-utils ${log-lib})