# Dobby 静态库获取指南

## 版本
使用 Dobby v2024.05.17 (或更新版本)

## 下载地址
- GitHub Releases: https://github.com/jmpews/Dobby/releases
- 或从 Termux 仓库获取预编译版本

## 所需架构
- arm64-v8a (主要)
- armeabi-v7a (兼容旧设备)
- x86_64 (模拟器)

## 文件放置位置
将下载的静态库放入对应目录：
```
app/src/main/jni/dobby/
├── include/
│   └── dobby.h
├── arm64-v8a/
│   └── libdobby.a
├── armeabi-v7a/
│   └── libdobby.a
└── x86_64/
    └── libdobby.a
```

## 自行编译 (可选)
```bash
git clone https://github.com/jmpews/Dobby.git
cd Dobby
mkdir build && cd build
cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-23 \
      -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)
cp libdobby.a /path/to/MCX19Hook/app/src/main/jni/dobby/arm64-v8a/
cp ../include/dobby.h /path/to/MCX19Hook/app/src/main/jni/dobby/include/
```

## 验证
编译工程时 CMake 会自动链接 `libdobby.a`，确保路径正确。