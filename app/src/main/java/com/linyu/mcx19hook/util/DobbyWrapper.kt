package com.linyu.mcx19hook.util

import android.util.Log

object DobbyWrapper {

    companion object {
        private const val TAG = "MCX19Hook-Dobby"

        // Inline Hook
        @JvmStatic
        external fun inlineHook(targetLib: String, targetSymbol: String, replacement: Long, outOriginal: LongArray): Boolean

        @JvmStatic
        external fun unhook(targetLib: String, targetSymbol: String): Boolean

        @JvmStatic
        external fun getOriginal(targetLib: String, targetSymbol: String): Long
    }
}