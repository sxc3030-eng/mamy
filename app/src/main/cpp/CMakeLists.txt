cmake_minimum_required(VERSION 3.22.1)
project("mamy_whisper")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper-cpp)

# ggml + whisper sources (paths follow whisper.cpp v1.7.x layout)
file(GLOB GGML_SRC
    "${WHISPER_DIR}/ggml/src/ggml.c"
    "${WHISPER_DIR}/ggml/src/ggml-aarch64.c"
    "${WHISPER_DIR}/ggml/src/ggml-alloc.c"
    "${WHISPER_DIR}/ggml/src/ggml-backend.cpp"
    "${WHISPER_DIR}/ggml/src/ggml-quants.c"
    "${WHISPER_DIR}/ggml/src/ggml-cpu.c"
)
file(GLOB WHISPER_SRC "${WHISPER_DIR}/src/whisper.cpp")

add_library(mamy_whisper SHARED
    whisper_jni.cpp
    ${GGML_SRC}
    ${WHISPER_SRC}
)

target_include_directories(mamy_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/src
)

target_compile_definitions(mamy_whisper PRIVATE
    _GNU_SOURCE
)

# Per-ABI flags (mirrors upstream whisper.cpp/ggml CMakeLists)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(mamy_whisper PRIVATE
        __ARM_NEON
        __ARM_FEATURE_FMA
        __ARM_FEATURE_FP16_VECTOR_ARITHMETIC
    )
    target_compile_options(mamy_whisper PRIVATE
        -march=armv8.2-a+fp16+dotprod
    )
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_definitions(mamy_whisper PRIVATE __ARM_NEON)
    target_compile_options(mamy_whisper PRIVATE
        -mfpu=neon-vfpv4
        -mfloat-abi=softfp
    )
endif()

find_library(log-lib log)
target_link_libraries(mamy_whisper ${log-lib})
