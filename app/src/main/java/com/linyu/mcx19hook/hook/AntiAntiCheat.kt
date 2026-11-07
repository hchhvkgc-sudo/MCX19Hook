package com.linyu.mcx19hook.hook

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object AntiAntiCheat {

    private const val TAG = "MCX19Hook-AntiAC"

    @JvmStatic
    fun hookAll(param: HookParam) {
        val loader = param.classLoader
        Timber.tag(TAG).i("Installing anti-anti-cheat hooks")

        // 1. 隐藏 LSPosed/Xposed 特征
        hideXposedTraces(loader)

        // 2. 绕过网易加固完整性校验
        bypassNeteaseIntegrityChecks(loader)

        // 3. 绕过 libtersafe / libddog 检测
        bypassTersafeAndDdog(loader)

        // 4. 绕过 Java 层完整性校验
        bypassJavaIntegrityChecks(loader)

        // 5. 绕过 Native 层完整性校验
        bypassNativeIntegrityChecks(loader)

        // 6. 反调试/反注入检测对抗
        antiDebugAntiInject(loader)

        // 7. 伪造设备指纹/环境
        spoofEnvironment(loader)

        Timber.tag(TAG).i("Anti-anti-cheat hooks installed")
    }

    // ------------------------------------------------------------------------
    // 1. 隐藏 Xposed/LSPosed 痕迹
    // ------------------------------------------------------------------------
    private fun hideXposedTraces(loader: ClassLoader) {
        try {
            // 隐藏 XposedBridge 类
            val xposedBridgeClass = Class.forName("de.robv.android.xposed.XposedBridge", false, loader)
            val fields = xposedBridgeClass.declaredFields
            fields.forEach { it.isAccessible = true }

            // 修改关键字段
            val disabledField = xposedBridgeClass.getDeclaredField("disabled").apply { isAccessible = true }
            disabledField.setBoolean(null, true)

            // 隐藏 XposedHelpers
            val xposedHelpersClass = Class.forName("de.robv.android.xposed.XposedHelpers", false, loader)
            val cacheField = xposedHelpersClass.getDeclaredField("cache").apply { isAccessible = true }
            cacheField.set(null, java.util.Collections.emptyMap())

            // LSPosed 特有
            val lspdHookClass = Class.forName("org.lsposed.lspd.LspdHook", false, loader)
            val hookedClassesField = lspdHookClass.getDeclaredField("hookedClasses").apply { isAccessible = true }
            hookedClassesField.set(null, java.util.Collections.emptySet())

        } catch (e: ClassNotFoundException) {
            // Xposed 未加载，忽略
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "hideXposedTraces failed")
        }
    }

    // ------------------------------------------------------------------------
    // 2. 绕过网易加固完整性校验
    // ------------------------------------------------------------------------
    private fun bypassNeteaseIntegrityChecks(loader: ClassLoader) {
        try {
            // 网易加固通常在 Application/Activity 加载时校验
            // Hook ClassLoader.loadClass 过滤校验类
            val classLoaderClass = Class.forName("java.lang.ClassLoader", false, loader)
            val loadClassMethod = classLoaderClass.getDeclaredMethod("loadClass", String::class.java, Boolean::class.javaPrimitiveType)
            loadClassMethod.isAccessible = true

            // 这里用 LSPosed Hook 替换 loadClass
            // 实际在 LSPosed 中用 hookMethod 实现
            // 此处仅演示逻辑

            // 常见校验类名关键词
            val suspiciousClasses = setOf(
                "com.netease.nis", "com.netease.antiaddiction", "com.netease.security",
                "com.tersafe", "com.ddog", "com.netease.tss", "com.netease.ylog",
                "com.netease.crash", "com.netease.monitor", "com.netease.check"
            )

            // Hook 逻辑：拦截可疑类加载，返回空实现或抛出 ClassNotFoundException
            // 具体实现见 LSPosed hookMethod 回调

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "bypassNeteaseIntegrityChecks setup failed")
        }
    }

    // ------------------------------------------------------------------------
    // 3. 绕过 libtersafe / libddog
    // ------------------------------------------------------------------------
    private fun bypassTersafeAndDdog(loader: ClassLoader) {
        // 这些是 Native 层保护，主要通过 Native Hook 对抗
        // Java 层可 Hook System.loadLibrary 防止加载，或 Hook JNI_OnLoad
        try {
            val systemClass = Class.forName("java.lang.System", false, loader)
            val loadLibraryMethod = systemClass.getDeclaredMethod("loadLibrary", String::class.java)
            loadLibraryMethod.isAccessible = true

            // Hook loadLibrary 拦截可疑库
            val suspiciousLibs = setOf("tersafe", "ddog", "ylog", "tss", "nesec")

            // LSPosed hook 逻辑：
            // if (suspiciousLibs.any { name.contains(it, ignoreCase = true) }) {
            //     return // 不加载
            // }
            // 但游戏可能依赖这些库初始化，需更细粒度控制

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "bypassTersafeAndDdog setup failed")
        }
    }

    // ------------------------------------------------------------------------
    // 4. 绕过 Java 层完整性校验
    // ------------------------------------------------------------------------
    private fun bypassJavaIntegrityChecks(loader: ClassLoader) {
        try {
            // 常见校验点：
            // 1. PackageManager.getPackageInfo -> signatures 校验
            // 2. ApplicationInfo.flags -> DEBUGGABLE 检查
            // 3. Class.getDeclaredMethods/Fields -> 反射检测 Hook
            // 4. StackTraceElement -> 调用栈检测
            // 5. Process.getElapsedCpuTime -> 时间检测

            // Hook PackageManager
            val pmClass = Class.forName("android.content.pm.PackageManager", false, loader)
            val getPackageInfoMethod = pmClass.getDeclaredMethod("getPackageInfo", String::class.java, Int::class.javaPrimitiveType)
            getPackageInfoMethod.isAccessible = true

            // Hook ApplicationInfo
            val appInfoClass = Class.forName("android.content.pm.ApplicationInfo", false, loader)
            val flagsField = appInfoClass.getDeclaredField("flags").apply { isAccessible = true }

            // Hook StackTraceElement
            val stackTraceClass = Class.forName("java.lang.StackTraceElement", false, loader)

            // 具体 Hook 由 LSPosed 回调实现，此处略

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "bypassJavaIntegrityChecks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 5. 绕过 Native 层完整性校验
    // ------------------------------------------------------------------------
    private fun bypassNativeIntegrityChecks(loader: ClassLoader) {
        // Native 校验通常检查：
        // 1. /proc/self/maps 中是否有可疑库
        // 2. 代码段 CRC32
        // 3. 关键函数指针是否被修改
        // 4. ptrace 状态
        // 5. 硬件断点 (DR0-DR7)
        //
        // 对抗在 Native 层 (anti_detect.cpp) 实现
        // Java 层仅需启动保护
        NativeHooks.init(param.applicationContext)
        AntiDetect.startProtection()
    }

    // ------------------------------------------------------------------------
    // 6. 反调试/反注入对抗
    // ------------------------------------------------------------------------
    private fun antiDebugAntiInject(loader: ClassLoader) {
        try {
            // Hook Debug.isDebuggerConnected
            val debugClass = Class.forName("android.os.Debug", false, loader)
            val isDebuggerConnectedMethod = debugClass.getDeclaredMethod("isDebuggerConnected")
            isDebuggerConnectedMethod.isAccessible = true

            // Hook Debug.getThreadCpuTimeNanos
            val getThreadCpuTimeMethod = debugClass.getDeclaredMethod("getThreadCpuTimeNanos", Long::class.javaPrimitiveType)

            // Hook Runtime.exec / ProcessBuilder -> 防止启动调试器
            val runtimeClass = Class.forName("java.lang.Runtime", false, loader)
            val execMethods = runtimeClass.declaredMethods.filter { it.name == "exec" }

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "antiDebugAntiInject failed")
        }
    }

    // ------------------------------------------------------------------------
    // 7. 伪造环境指纹
    // ------------------------------------------------------------------------
    private fun spoofEnvironment(loader: ClassLoader) {
        try {
            // 伪造 Build.PROP
            val buildClass = Class.forName("android.os.Build", false, loader)
            val tagsField = buildClass.getDeclaredField("TAGS").apply { isAccessible = true }
            tagsField.set(null, "release-keys")

            val fingerprintField = buildClass.getDeclaredField("FINGERPRINT").apply { isAccessible = true }
            fingerprintField.set(null, "google/redfin/redfin:14/UQ1A.240105.004/11208141:user/release-keys")

            // 伪造 ro.build.type, ro.debuggable 等
            // 通过 SystemProperties 或反射修改

            // 伪造设备 ID (Android ID, Serial, IMEI)
            val settingsSecureClass = Class.forName("android.provider.Settings$Secure", false, loader)
            val getStringMethod = settingsSecureClass.getDeclaredMethod("getString", 
                Class.forName("android.content.ContentResolver"), String::class.java)
            getStringMethod.isAccessible = true

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "spoofEnvironment failed")
        }
    }
}

// 需要导入 HookParam
import org.lsposed.lspd.callback.HookParam