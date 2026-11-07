package com.linyu.mcx19hook.util

object AntiDetect {

    companion object {
        @JvmStatic
        external fun startProtection()

        @JvmStatic
        external fun stopProtection()

        @JvmStatic
        external fun checkEnvironment(): Boolean

        @JvmStatic
        external fun hideModule(libName: String)

        @JvmStatic
        external fun getFakeProps(): String
    }
}