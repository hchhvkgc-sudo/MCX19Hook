#!/system/bin/sh
set -e

JAVA_HOME=/data/user/0/app.dsh.mobile/files/engine/extensions/openjdk-17/lib/jvm/java-17-openjdk
ANDROID_NDK_HOME=/storage/emulated/0/工具/android-ndk-r30-linux/android-ndk-r30
PROJECT_DIR=/storage/emulated/0/DeepSeek工作目录/MCX19Hook

export PATH=$JAVA_HOME/bin:$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH

CC=aarch64-linux-android21-clang
CXX=aarch64-linux-android21-clang++
AR=llvm-ar
STRIP=llvm-strip

SYSROOT=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot
INCLUDES="-I$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include -I$PROJECT_DIR/app/src/main/jni/dobby/include"

CFLAGS="-std=c++20 -O3 -fvisibility=hidden -DDOBBY_EXPORT -DDISABLE_LOGGING -fstack-protector-strong -D_FORTIFY_SOURCE=2 -Wno-unused-parameter -Wno-write-strings -fPIC"
LDFLAGS="-shared -llog -ldl -landroid -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,-z,relro,-z,now -Wl,--build-id=sha1"

# 创建输出目录
mkdir -p $PROJECT_DIR/app/build/intermediates/jniLibs/arm64-v8a/Release

echo "Compiling dobby_wrapper.cpp..."
$CXX $CFLAGS $INCLUDES -c $PROJECT_DIR/app/src/main/jni/dobby_wrapper.cpp -o /tmp/dobby_wrapper.o

echo "Compiling native_hooks.cpp..."
$CXX $CFLAGS $INCLUDES -c $PROJECT_DIR/app/src/main/jni/native_hooks.cpp -o /tmp/native_hooks.o

echo "Compiling sigscan.cpp..."
$CXX $CFLAGS $INCLUDES -c $PROJECT_DIR/app/src/main/jni/sigscan.cpp -o /tmp/sigscan.o

echo "Compiling mem.cpp..."
$CXX $CFLAGS $INCLUDES -c $PROJECT_DIR/app/src/main/jni/mem.cpp -o /tmp/mem.o

echo "Compiling anti_detect.cpp..."
$CXX $CFLAGS $INCLUDES -c $PROJECT_DIR/app/src/main/jni/anti_detect.cpp -o /tmp/anti_detect.o

# 复制 dobby 静态库
cp $PROJECT_DIR/app/src/main/jni/dobby/arm64-v8a/libdobby.a /tmp/libdobby.a

echo "Linking libmcx19hook.so..."
$CXX $LDFLAGS -o $PROJECT_DIR/app/build/intermediates/jniLibs/arm64-v8a/Release/libmcx19hook.so \
    /tmp/dobby_wrapper.o /tmp/native_hooks.o /tmp/sigscan.o /tmp/mem.o /tmp/anti_detect.o \
    /tmp/libdobby.a

echo "Stripping..."
$STRIP $PROJECT_DIR/app/build/intermediates/jniLibs/arm64-v8a/Release/libmcx19hook.so

echo "Done! libmcx19hook.so created at:"
ls -lh $PROJECT_DIR/app/build/intermediates/jniLibs/arm64-v8a/Release/libmcx19hook.so
