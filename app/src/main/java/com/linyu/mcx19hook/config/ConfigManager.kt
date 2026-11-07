package com.linyu.mcx19hook.config

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

object ConfigManager {

    private const val PREFS_NAME = "mcx19hook_config"
    private const val FEATURES_FILE = "features.json"
    private var prefs: SharedPreferences? = null
    private var context: Context? = null
    private val listeners = mutableListOf<OnConfigChangeListener>()
    private val featuresCache = ConcurrentHashMap<String, Any>()

    interface OnConfigChangeListener {
        fun onConfigChanged(key: String, value: Any)
    }

    @JvmStatic
    fun init(appContext: Context) {
        context = appContext.applicationContext
        prefs = context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFeaturesFromAssets()
        startFileWatcher()
        Timber.tag("MCX19Hook-Config").i("ConfigManager initialized")
    }

    private fun loadFeaturesFromAssets() {
        try {
            val input = context?.assets?.open(FEATURES_FILE)
            input?.use { stream ->
                val json = stream.readBytes().decodeToString()
                val gson = Gson()
                val type = object : TypeToken<ConcurrentHashMap<String, Any>>() {}.type
                val map = gson.fromJson<ConcurrentHashMap<String, Any>>(json, type)
                map?.forEach { (k, v) -> featuresCache[k] = v }
            }
        } catch (e: Exception) {
            Timber.tag("MCX19Hook-Config").w(e, "Failed to load features from assets, using defaults")
            loadDefaults()
        }
        syncToNative()
    }

    private fun loadDefaults() {
        featuresCache.apply {
            put("esp_enabled", false)
            put("aimbot_enabled", false)
            put("fly_enabled", false)
            put("noclip_enabled", false)
            put("godmode_enabled", false)
            put("infinite_items_enabled", false)
            put("speed_enabled", false)
            put("reach_enabled", false)
            put("xray_enabled", false)
            put("entity_glow_enabled", false)
            put("gui_overlay_enabled", true)
            put("instant_mine_enabled", false)
            put("auto_sort_enabled", false)
            put("auto_action_enabled", false)
            put("anti_cheat_bypass_enabled", true)
            put("packet_modification_enabled", true)
            put("auto_keep_alive_enabled", true)
            put("aimbot_fov", 90f)
            put("aimbot_smooth", 5f)
            put("speed_multiplier", 1.5f)
            put("reach_distance", 6f)
            put("esp_max_distance", 100)
            put("esp_players_enabled", true)
            put("esp_mobs_enabled", true)
            put("esp_items_enabled", true)
            put("esp_chests_enabled", true)
        }
    }

    private fun startFileWatcher() {
        // 监听 assets/features.json 变化（热重载）
        // 实际可用 FileObserver 监听内部存储配置文件
    }

    fun addListener(listener: OnConfigChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnConfigChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChange(key: String, value: Any) {
        listeners.forEach { it.onConfigChanged(key, value) }
    }

    // ==================== Getter / Setter ====================

    inline fun <reified T> get(key: String, default: T): T {
        return (prefs?.getString(key, null)?.let { Gson().fromJson(it, object : TypeToken<T>() {}.type) }
            ?? featuresCache[key] as? T
            ?? default) ?: default
    }

    inline fun <reified T> set(key: String, value: T) {
        prefs?.edit()?.putString(key, Gson().toJson(value))?.apply()
        featuresCache[key] = value
        notifyChange(key, value)
        if (key.endsWith("_enabled") || key.endsWith("_fov") || key.endsWith("_smooth") 
            || key.endsWith("_multiplier") || key.endsWith("_distance") || key.endsWith("_max_distance")) {
            syncToNative()
        }
    }

    // 功能开关
    fun isEspEnabled() = get("esp_enabled", false)
    fun setEspEnabled(v: Boolean) { set("esp_enabled", v) }

    fun isAimbotEnabled() = get("aimbot_enabled", false)
    fun setAimbotEnabled(v: Boolean) { set("aimbot_enabled", v) }

    fun isFlyEnabled() = get("fly_enabled", false)
    fun setFlyEnabled(v: Boolean) { set("fly_enabled", v) }

    fun isNoclipEnabled() = get("noclip_enabled", false)
    fun setNoclipEnabled(v: Boolean) { set("noclip_enabled", v) }

    fun isGodModeEnabled() = get("godmode_enabled", false)
    fun setGodModeEnabled(v: Boolean) { set("godmode_enabled", v) }

    fun isInfiniteItemsEnabled() = get("infinite_items_enabled", false)
    fun setInfiniteItemsEnabled(v: Boolean) { set("infinite_items_enabled", v) }

    fun isSpeedEnabled() = get("speed_enabled", false)
    fun setSpeedEnabled(v: Boolean) { set("speed_enabled", v) }

    fun isReachEnabled() = get("reach_enabled", false)
    fun setReachEnabled(v: Boolean) { set("reach_enabled", v) }

    fun isXrayEnabled() = get("xray_enabled", false)
    fun setXrayEnabled(v: Boolean) { set("xray_enabled", v) }

    fun isEntityGlowEnabled() = get("entity_glow_enabled", false)
    fun setEntityGlowEnabled(v: Boolean) { set("entity_glow_enabled", v) }

    fun isGuiOverlayEnabled() = get("gui_overlay_enabled", true)
    fun setGuiOverlayEnabled(v: Boolean) { set("gui_overlay_enabled", v) }

    fun isInstantMineEnabled() = get("instant_mine_enabled", false)
    fun setInstantMineEnabled(v: Boolean) { set("instant_mine_enabled", v) }

    fun isAutoSortEnabled() = get("auto_sort_enabled", false)
    fun setAutoSortEnabled(v: Boolean) { set("auto_sort_enabled", v) }

    fun isAutoActionEnabled() = get("auto_action_enabled", false)
    fun setAutoActionEnabled(v: Boolean) { set("auto_action_enabled", v) }

    fun isAntiCheatBypassEnabled() = get("anti_cheat_bypass_enabled", true)
    fun setAntiCheatBypassEnabled(v: Boolean) { set("anti_cheat_bypass_enabled", v) }

    fun isPacketModificationEnabled() = get("packet_modification_enabled", true)
    fun setPacketModificationEnabled(v: Boolean) { set("packet_modification_enabled", v) }

    fun isAutoKeepAliveEnabled() = get("auto_keep_alive_enabled", true)
    fun setAutoKeepAliveEnabled(v: Boolean) { set("auto_keep_alive_enabled", v) }

    // 数值配置
    fun getAimbotFov() = get("aimbot_fov", 90f)
    fun setAimbotFov(v: Float) { set("aimbot_fov", v) }

    fun getAimbotSmooth() = get("aimbot_smooth", 5f)
    fun setAimbotSmooth(v: Float) { set("aimbot_smooth", v) }

    fun getSpeedMultiplier() = get("speed_multiplier", 1.5f)
    fun setSpeedMultiplier(v: Float) { set("speed_multiplier", v) }

    fun getReachDistance() = get("reach_distance", 6f)
    fun setReachDistance(v: Float) { set("reach_distance", v) }

    fun getEspMaxDistance() = get("esp_max_distance", 100)
    fun setEspMaxDistance(v: Int) { set("esp_max_distance", v) }

    fun isEspPlayersEnabled() = get("esp_players_enabled", true)
    fun setEspPlayersEnabled(v: Boolean) { set("esp_players_enabled", v) }

    fun isEspMobsEnabled() = get("esp_mobs_enabled", true)
    fun setEspMobsEnabled(v: Boolean) { set("esp_mobs_enabled", v) }

    fun isEspItemsEnabled() = get("esp_items_enabled", true)
    fun setEspItemsEnabled(v: Boolean) { set("esp_items_enabled", v) }

    fun isEspChestsEnabled() = get("esp_chests_enabled", true)
    fun setEspChestsEnabled(v: Boolean) { set("esp_chests_enabled", v) }

    // 同步到 Native 层
    private fun syncToNative() {
        try {
            NativeHooks.updateFeatureFlags()
        } catch (e: Exception) {
            Timber.tag("MCX19Hook-Config").e(e, "syncToNative failed")
        }
    }

    // 导出/导入配置
    fun exportConfig(file: File): Boolean {
        return try {
            val map = featuresCache.toMap()
            val json = Gson().toJson(map)
            file.writeText(json)
            true
        } catch (e: Exception) {
            Timber.tag("MCX19Hook-Config").e(e, "exportConfig failed")
            false
        }
    }

    fun importConfig(file: File): Boolean {
        return try {
            val json = file.readText()
            val gson = Gson()
            val type = object : TypeToken<ConcurrentHashMap<String, Any>>() {}.type
            val map = gson.fromJson<ConcurrentHashMap<String, Any>>(json, type)
            map?.forEach { (k, v) ->
                prefs?.edit()?.putString(k, gson.toJson(v))?.apply()
                featuresCache[k] = v
            }
            syncToNative()
            notifyChange("all", map)
            true
        } catch (e: Exception) {
            Timber.tag("MCX19Hook-Config").e(e, "importConfig failed")
            false
        }
    }

    fun resetToDefaults() {
        prefs?.edit()?.clear()?.apply()
        featuresCache.clear()
        loadDefaults()
        notifyChange("all", featuresCache)
    }
}