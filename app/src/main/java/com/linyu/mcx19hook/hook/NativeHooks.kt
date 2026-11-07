package com.linyu.mcx19hook.hook

import android.content.Context
import timber.log.Timber

object NativeHooks {

    private const val TAG = "MCX19Hook-Native"

    // Native 方法声明
    @Suppress("UNUSED_PARAMETER")
    external fun initNativeHooks()

    @Suppress("UNUSED_PARAMETER")
    external fun updateFeatures(
        esp: Boolean, aimbot: Boolean, fly: Boolean, noclip: Boolean, godmode: Boolean,
        infiniteItems: Boolean, speed: Boolean, reach: Boolean,
        aimbotFov: Float, aimbotSmooth: Float, speedMult: Float, reachDist: Float,
        espMaxDist: Int, espPlayers: Boolean, espMobs: Boolean, espItems: Boolean, espChests: Boolean
    )

    @Suppress("UNUSED_PARAMETER")
    external fun updateLocalPlayer(
        x: Float, y: Float, z: Float, yaw: Float, pitch: Float,
        screenW: Int, screenH: Int
    )

    @Suppress("UNUSED_PARAMETER")
    external fun getCachedEntities(): Array<EntityData>

    companion object {
        @Volatile private var initialized = false

        fun init(context: Context) {
            if (initialized) return
            System.loadLibrary("mcx19hook")
            initNativeHooks()
            initialized = true
            Timber.tag(TAG).i("Native hooks initialized")
        }

        fun updateFeatureFlags() {
            if (!initialized) return
            updateFeatures(
                ConfigManager.isEspEnabled(), ConfigManager.isAimbotEnabled(),
                ConfigManager.isFlyEnabled(), ConfigManager.isNoclipEnabled(),
                ConfigManager.isGodModeEnabled(), ConfigManager.isInfiniteItemsEnabled(),
                ConfigManager.isSpeedEnabled(), ConfigManager.isReachEnabled(),
                ConfigManager.getAimbotFov(), ConfigManager.getAimbotSmooth(),
                ConfigManager.getSpeedMultiplier(), ConfigManager.getReachDistance(),
                ConfigManager.getEspMaxDistance(), ConfigManager.isEspPlayersEnabled(),
                ConfigManager.isEspMobsEnabled(), ConfigManager.isEspItemsEnabled(),
                ConfigManager.isEspChestsEnabled()
            )
        }

        fun updatePlayerPos(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, w: Int, h: Int) {
            if (!initialized) return
            updateLocalPlayer(x, y, z, yaw, pitch, w, h)
        }

        fun fetchEntities(): Array<EntityData> {
            if (!initialized) return emptyArray()
            return getCachedEntities()
        }
    }
}

// 实体数据类（对应 Native 端 Entity 结构）
class EntityData(
    val address: Long,
    val x: Float, val y: Float, val z: Float,
    val yaw: Float, val pitch: Float,
    val entityType: Int,
    val health: Int,
    val isPlayer: Boolean,
    val name: String
)