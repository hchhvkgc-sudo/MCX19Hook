package com.linyu.mcx19hook.util

import android.util.Log
import timber.log.Timber

object Logger {

    private const val TAG_PREFIX = "MCX19Hook"
    private var isDebug = BuildConfig.IS_DEBUG

    @JvmStatic
    fun init(debug: Boolean) {
        isDebug = debug
        if (debug) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    @JvmStatic
    fun v(tag: String, msg: String, throwable: Throwable? = null) {
        if (isDebug) Timber.tag("$TAG_PREFIX-$tag").v(throwable, msg)
    }

    @JvmStatic
    fun d(tag: String, msg: String, throwable: Throwable? = null) {
        if (isDebug) Timber.tag("$TAG_PREFIX-$tag").d(throwable, msg)
    }

    @JvmStatic
    fun i(tag: String, msg: String, throwable: Throwable? = null) {
        Timber.tag("$TAG_PREFIX-$tag").i(throwable, msg)
    }

    @JvmStatic
    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        Timber.tag("$TAG_PREFIX-$tag").w(throwable, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Timber.tag("$TAG_PREFIX-$tag").e(throwable, msg)
    }

    @JvmStatic
    fun wtf(tag: String, msg: String, throwable: Throwable? = null) {
        Timber.tag("$TAG_PREFIX-$tag").wtf(throwable, msg)
    }

    // 加密日志（生产环境）
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
            if (priority < Log.INFO) return
            // 可添加加密/上传逻辑
            val encrypted = encrypt(message)
            Log.println(priority, tag, encrypted)
            throwable?.printStackTrace()
        }

        private fun encrypt(msg: String): String {
            // 简单 XOR 混淆
            return msg.chars().map { (it xor 0x5A).toChar() }.joinToString("")
        }
    }
}