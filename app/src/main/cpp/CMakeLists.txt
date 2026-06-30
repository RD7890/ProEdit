cmake_minimum_required(VERSION 3.22.1)
project("proedit-native")

add_library(
    proedit-native
    SHARED
    image_processor.cpp
    masking.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

target_link_libraries(
    proedit-native
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
)

target_compile_options(proedit-native PRIVATE
    -std=c++17
    -O3
    -ffast-math
    -Wall
)
