package com.linyu.mcx19hook.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class ConfigChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "com.linyu.mcx19hook.CONFIG_CHANGED") {
            val key = intent.getStringExtra("key") ?: "all"
            val value = intent.getSerializableExtra("value")
            Timber.tag("MCX19Hook-Config").d("Config changed via broadcast: $key = $value")
            // 触发热重载
            ConfigManager.notifyChange(key, value)
        }
    }
}