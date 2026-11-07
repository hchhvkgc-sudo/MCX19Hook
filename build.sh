#!/bin/bash
# MCX19Hook 自动编译脚本
# 用法: ./build.sh [debug|release] [clean]

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

BUILD_TYPE="${1:-debug}"
DO_CLEAN="${2:-}"

echo "=========================================="
echo "  MCX19Hook Build Script"
echo "  Target: com.netease.x19 (网易我的世界)"
echo "  Build Type: $BUILD_TYPE"
echo "=========================================="

# 检查环境
check_env() {
    echo "[*] Checking environment..."

    if ! command -v java &> /dev/null; then
        echo "[!] Java not found. Install OpenJDK 17+"
        exit 1
    fi

    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        echo "[!] ANDROID_HOME not set"
        exit 1
    fi

    # 检查 NDK
    NDK_VERSION="27.0.12077973"
    NDK_PATH="$ANDROID_HOME/ndk/$NDK_VERSION"
    if [ ! -d "$NDK_PATH" ]; then
        echo "[!] NDK $NDK_VERSION not found at $NDK_PATH"
        echo "    Install via SDK Manager or set NDK path"
        exit 1
    fi
    export ANDROID_NDK_HOME="$NDK_PATH"
    echo "[+] NDK found: $NDK_PATH"

    # 检查 Dobby
    if [ ! -f "app/src/main/jni/dobby/arm64-v8a/libdobby.a" ]; then
        echo "[!] Dobby static library not found!"
        echo "    Please read app/src/main/jni/dobby/README.md and place libdobby.a"
        exit 1
    fi
    echo "[+] Dobby library found"
}

# 清理
do_clean() {
    echo "[*] Cleaning..."
    ./gradlew clean
    rm -rf app/build/intermediates app/build/outputs
    echo "[+] Clean done"
}

# 编译
do_build() {
    echo "[*] Building $BUILD_TYPE..."

    case "$BUILD_TYPE" in
        debug)
            ./gradlew assembleDebug --no-daemon -x lint
            APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
            ;;
        release)
            ./gradlew assembleRelease --no-daemon -x lint
            APK_PATH="app/build/outputs/apk/release/app-release.apk"
            ;;
        *)
            echo "[!] Unknown build type: $BUILD_TYPE (debug|release)"
            exit 1
            ;;
    esac

    if [ -f "$APK_PATH" ]; then
        echo "[+] Build successful!"
        echo "    APK: $APK_PATH"
        ls -lh "$APK_PATH"
    else
        echo "[!] Build failed - APK not found"
        exit 1
    fi
}

# 验证 APK
verify_apk() {
    echo "[*] Verifying APK..."
    
    if command -v aapt &> /dev/null; then
        aapt dump badging "$APK_PATH" | grep -E "package:|application-label:|uses-permission:|native-code:"
    fi

    # 检查架构
    if command -v unzip &> /dev/null; then
        echo "[*] Native libraries:"
        unzip -l "$APK_PATH" "lib/*/libmcx19hook.so" "lib/*/libdobby.so"
    fi
}

# 主流程
main() {
    check_env

    if [ "$DO_CLEAN" = "clean" ]; then
        do_clean
    fi

    do_build
    verify_apk

    echo "=========================================="
    echo "  Build completed successfully!"
    echo "  Install with: adb install -r $APK_PATH"
    echo "  Then enable in LSPosed for com.netease.x19"
    echo "=========================================="
}

main "$@"