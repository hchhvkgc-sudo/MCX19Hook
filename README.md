# MCX19Hook - 网易我的世界 LSPosed 模块

针对 **网易我的世界 (包名: `com.netease.x19`)** 的 LSPosed 功能模块，支持 **v1.20.x - v1.21.x**，架构 **arm64-v8a / armeabi-v7a / x86_64**。

## 功能列表

### 视觉类
- **ESP (透视)**: 玩家/生物/箱子/物品 透视，支持距离、血量、名称显示
- **X-Ray**: 方块透视，可自定义过滤方块类型
- **实体发光**: 目标实体轮廓高亮
- **GUI 覆盖**: 准星、状态显示、坐标、FPS

### 战斗类
- **Aimbot (自瞄)**: 可调 FOV、平滑度、目标选择(玩家/生物)、骨骼优先级
- **TriggerBot**: 自动攻击（跨准星时自动点击）
- **Reach (长臂)**: 可调攻击/建筑距离 (默认 6 格)
- **AutoCrit**: 自动跳劈
- **AntiKB**: 防击退

### 移动类
- **Fly (飞行)**: 创造模式飞行/生存模式飞行（绕过检测）
- **Noclip (穿墙)**: 无碰撞穿透方块
- **Speed (加速)**: 可调倍率 (1.0x - 5.0x)
- **Jesus**: 水面行走
- **Step**: 自动跳台阶
- **Sprint**: 自动冲刺

### 玩家类
- **GodMode (无敌)**: 完全免疫伤害/饥饿/摔落
- **Infinite Items (无限物品)**: 物品不减、最大堆叠、创造模式物品获取
- **AutoEat**: 自动进食
- **AutoArmor**: 自动穿戴最佳护甲
- **AutoTool**: 自动切换最佳工具
- **ChestStealer**: 快速移动箱子物品
- **InventoryManager**: 背包自动整理/压缩

### 世界类
- **InstantMine**: 瞬间破坏方块
- **FastPlace**: 快速放置
- **Scaffold**: 自动搭路
- **LiquidBounce**: 水/岩浆特殊处理

### 网络/防检测
- **Packet Modification**: 移动包修正、能力包伪造
- **Anti-Cheat Bypass**: 绕过网易加固、libtersafe、libddog、YD 反作弊
- **Anti-Report**: 屏蔽举报包
- **AutoKeepAlive**: 自动心跳回复
- **Fake Lag**: 伪造延迟
- **Blink**: 瞬移回溯

## 编译要求

- **Android Gradle Plugin 8.4.0+**
- **Kotlin 2.0.0+**
- **NDK r27 (27.0.12077973)**
- **JDK 17+**
- **Dobby 静态库** (见 `app/src/main/jni/dobby/README.md`)

## 编译步骤

### 1. 准备 Dobby 库
```bash
# 下载预编译或自行编译
# 放置到 app/src/main/jni/dobby/{arch}/libdobby.a
# 头文件放置到 app/src/main/jni/dobby/include/dobby.h
```

### 2. 配置签名 (release)
```bash
# 生成 keystore
keytool -genkeypair -alias mcx19hook -keyalg RSA -keysize 2048 -validity 10000 -keystore keystore/release.keystore

# 配置 signingConfig (在 local.properties 或 gradle.properties 中)
# MCX19HOOK_STORE_FILE=../keystore/release.keystore
# MCX19HOOK_STORE_PASSWORD=your_password
# MCX19HOOK_KEY_ALIAS=mcx19hook
# MCX19HOOK_KEY_PASSWORD=your_password
```

### 3. 编译
```bash
# Debug 包 (无混淆，可调试)
./gradlew assembleDebug

# Release 包 (混淆、压缩、签名)
./gradlew assembleRelease
```

### 4. 输出
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 安装使用

1. **安装 LSPosed** (Magisk/KernelSU/APatch)
2. **安装模块 APK**
3. **在 LSPosed 管理器中勾选 `com.netease.x19` 作用域**
4. **重启游戏**
5. **游戏内悬浮按钮展开菜单，开启所需功能**

## 配置文件

- 默认配置: `assets/features.json`
- 运行时配置: `SharedPreferences` (mcx19hook_config.xml)
- 支持导入/导出 JSON 配置文件

## 兼容性

| 游戏版本 | 架构 | 状态 |
|----------|------|------|
| 1.21.x | arm64-v8a | ✅ 测试通过 |
| 1.21.x | armeabi-v7a | ✅ 兼容 |
| 1.21.x | x86_64 | ✅ 模拟器可用 |
| 1.20.x | arm64-v8a | ✅ 兼容 |
| 1.19.x | arm64-v8a | ⚠️ 部分需更新特征码 |

## 反作弊对抗

- **网易加固**: Hook ClassLoader.loadClass 过滤校验类
- **libtersafe**: Native 层隐藏、CRC 绕过、心跳包伪造
- **libddog**: 反调试对抗、内存扫描规避
- **YD/YDPro**: 行为分析对抗、特征码动态获取
- **完整性校验**: Java/Native 双层绕过
- **模块隐藏**: 从 /proc/self/maps 隐藏、伪造 Build.PROP

## 注意事项

⚠️ **仅供学习研究使用，请勿用于破坏游戏平衡或商业用途**
⚠️ **使用前请备份游戏数据，封号风险自负**
⚠️ **网易 MC 更新频繁，特征码可能失效，需定期更新 `assets/signatures.json`**
⚠️ **部分功能需游戏特定版本支持，建议锁定版本使用**

## 目录结构

```
MCX19Hook/
├── app/
│   ├── src/main/
│   │   ├── java/com/linyu/mcx19hook/
│   │   │   ├── MainHook.kt              # LSPosed 入口
│   │   │   ├── hook/
│   │   │   │   ├── JavaHooks.kt         # Java层核心Hook
│   │   │   │   ├── NativeHooks.kt       # Native桥接
│   │   │   │   ├── PacketHooks.kt       # 网络包控制
│   │   │   │   └── AntiAntiCheat.kt     # 反反作弊
│   │   │   ├── config/
│   │   │   │   ├── ConfigManager.kt     # 配置管理(热重载)
│   │   │   │   ├── FeatureFlags.kt      # 功能标志常量
│   │   │   │   └── ConfigChangeReceiver.kt
│   │   │   ├── ui/
│   │   │   │   └── FloatingWindowService.kt # 悬浮窗菜单+ESP绘制
│   │   │   └── util/
│   │   │       ├── DobbyWrapper.kt      # Inline Hook桥接
│   │   │       ├── Memory.kt            # 内存读写
│   │   │       ├── SigScan.kt           # 特征码扫描
│   │   │       ├── AntiDetect.kt        # 反检测桥接
│   │   │       └── Logger.kt            # 加密日志
│   │   ├── jni/
│   │   │   ├── CMakeLists.txt
│   │   │   ├── dobby/                   # Dobby静态库(需自行放置)
│   │   │   ├── native_hooks.cpp         # 核心游戏逻辑Hook
│   │   │   ├── sigscan.cpp              # 运行时特征码扫描
│   │   │   ├── mem.cpp                  # 内存操作/分配
│   │   │   ├── anti_detect.cpp          # 反调试/反注入/完整性校验
│   │   │   └── dobby_wrapper.cpp        # JNI桥接实现
│   │   └── assets/
│   │       ├── features.json            # 默认功能配置
│   │       └── signatures.json          # 版本特征码库
│   └── build.gradle.kts
├── obfuscation/
│   ├── proguard-rules.pro               # 混淆规则
│   └── obfuscation_dict.txt             # 混淆字典
├── gradle/wrapper/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── README.md
```

## 更新特征码

1. 使用 IDA Pro / Ghidra 打开 `libminecraftpe.so`
2. 定位目标函数 (如 `GameRenderer::renderLevel`)
3. 复制特征码 (IDA: `Edit > Copy > Bytes as C array`)
4. 更新 `assets/signatures.json` 对应条目
5. 重新编译模块

## 许可证

MIT License - 仅供学习交流

## 交流反馈

TG 群: https://t.me/+sPldBoRlmw8wZDg1