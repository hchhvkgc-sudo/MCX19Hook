package com.linyu.mcx19hook

import android.app.Application
import android.content.Context
import android.os.Build
import org.lsposed.lspd.LspdHook
import org.lsposed.lspd.OnHookLoadPackageListener
import org.lsposed.lspd.annotation.HookEntry
import org.lsposed.lspd.callback.HookParam
import timber.log.Timber

class HookApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.IS_DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

@HookEntry(packageName = "com.netease.x19")
class MainHook : OnHookLoadPackageListener {

    companion object {
        private const val TAG = "MCX19Hook-Main"
        @Volatile var isHooked = false
    }

    override fun onHookLoadPackage(param: HookParam) {
        if (isHooked) return
        isHooked = true

        Timber.tag(TAG).i("Loading hooks for com.netease.x19")

        // 初始化配置
        ConfigManager.init(param.applicationContext)

        // 启动反检测
        AntiDetect.startProtection()

        // 加载 Native 库
        System.loadLibrary("mcx19hook")

        // 执行各层 Hook
        try {
            JavaHooks.hookAll(param)
            NativeHooks.initNativeHooks()
            PacketHooks.hookAll(param)
            AntiAntiCheat.hookAll(param)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Hook failed")
        }

        // 启动悬浮窗
        FloatingWindowService.startIfEnabled(param.applicationContext)

        Timber.tag(TAG).i("All hooks installed")
    }
}