cmake_minimum_required(VERSION 3.22.1)
project(mcx19hook LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 输出目录
set(LIB_OUTPUT_DIR ${CMAKE_SOURCE_DIR}/../../../build/intermediates/cmake/${ANDROID_ABI}/Release)
file(MAKE_DIRECTORY ${LIB_OUTPUT_DIR})

# ==================== Dobby 静态库 ====================
add_library(dobby STATIC IMPORTED)
set_target_properties(dobby PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/dobby/${ANDROID_ABI}/libdobby.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_SOURCE_DIR}/dobby/include
)

# ==================== 核心Hook库 ====================
add_library(mcx19hook SHARED
    native_hooks.cpp
    sigscan.cpp
    mem.cpp
    anti_detect.cpp
    dobby_wrapper.cpp
)

target_include_directories(mcx19hook PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/dobby/include
)

target_link_libraries(mcx19hook
    PRIVATE dobby
    PUBLIC log dl android m
)

target_link_options(mcx19hook PRIVATE
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,-z,relro,-z,now
    -Wl,--build-id=sha1
)

# 复制到 jniLibs
add_custom_command(TARGET mcx19hook POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy
    $<TARGET_FILE:mcx19hook>
    ${CMAKE_SOURCE_DIR}/../../../build/intermediates/jniLibs/${ANDROID_ABI}/Release/libmcx19hook.so
    COMMENT "Copy libmcx19hook.so to jniLibs"
)