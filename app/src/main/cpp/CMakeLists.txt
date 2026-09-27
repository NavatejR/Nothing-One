# Nothing One native module: whisper.cpp (vendored, MIT) compiled for
# on-device dictation, exposed to Kotlin through a thin JNI wrapper.
#
# The whisper.cpp tree (ggml/, src/, include/) is vendored unmodified from
# https://github.com/ggml-org/whisper.cpp (v1.9.4). This file builds ggml as
# static libs, links whisper.cpp on top, and packs everything into a single
# libnothingone_whisper.so so the APK ships one native object per ABI.
cmake_minimum_required(VERSION 3.22)
project(nothingone_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# CPU-only ggml: no CUDA/Vulkan/Metal/OpenMP on Android. GGML_NATIVE=OFF keeps
# the build portable across the arm64 / x86_64 ABIs we ship.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Vendored whisper.cpp top-level CMake (reference copy, not included here) —
# we add its subdirectories directly instead.
add_subdirectory(${WHISPER_DIR}/ggml ggml_build)

find_library(LOG_LIB log)

add_library(nothingone_whisper SHARED
    ${WHISPER_DIR}/nothingone_whisper.cpp
    ${WHISPER_DIR}/src/whisper.cpp
)

target_include_directories(nothingone_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)

target_compile_definitions(nothingone_whisper PRIVATE
    WHISPER_VERSION="1.9.4"
    GGML_USE_CPU
)

target_link_libraries(nothingone_whisper PRIVATE ggml ${LOG_LIB})

# Release builds: hide symbols, shrink sections, enable LTO like the official
# whisper.android example does.
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(nothingone_whisper PRIVATE
        -O3
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
    )
    target_link_options(nothingone_whisper PRIVATE
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        -flto
    )
endif()
