package com.linyu.mcx19hook.hook

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import org.lsposed.lspd.callback.HookParam
import org.lsposed.lspd.callback.MethodHook
import org.lsposed.lspd.callback.ReplaceHook
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object JavaHooks {

    private const val TAG = "MCX19Hook-Java"
    private val hookedMethods = ConcurrentHashMap<String, Boolean>()

    @JvmStatic
    fun hookAll(param: HookParam) {
        val loader = param.classLoader
        Timber.tag(TAG).i("Starting Java layer hooks")

        // ==================== 1. 渲染层 Hook ====================
        hookRendering(loader)

        // ==================== 2. 实体/玩家 Hook ====================
        hookEntities(loader)

        // ==================== 3. 移动/输入 Hook ====================
        hookMovement(loader)

        // ==================== 4. 网络/数据包 Hook ====================
        hookNetwork(loader)

        // ==================== 5. 物品/背包 Hook ====================
        hookInventory(loader)

        // ==================== 6. GUI/菜单 Hook ====================
        hookGui(loader)

        // ==================== 7. 世界/方块 Hook ====================
        hookWorld(loader)

        Timber.tag(TAG).i("Java layer hooks completed")
    }

    // ------------------------------------------------------------------------
    // 1. 渲染层：透视、ESP、自瞄辅助绘制
    // ------------------------------------------------------------------------
    private fun hookRendering(loader: ClassLoader) {
        try {
            // Minecraft Client 类
            val clientClass = loader.loadClass("com.mojang.minecraftpe.MainActivity")
            // 或网易特有的渲染类
            val renderClass = loader.loadClass("net.minecraft.client.renderer.GameRenderer")
            val tessellatorClass = loader.loadClass("net.minecraft.client.renderer.Tessellator")
            val bufferBuilderClass = loader.loadClass("net.minecraft.client.renderer.BufferBuilder")
            val entityRenderDispatcherClass = loader.loadClass("net.minecraft.client.renderer.entity.EntityRenderDispatcher")

            // Hook GameRenderer.renderLevel -> ESP 绘制入口
            hookMethod(renderClass, "renderLevel", arrayOf(Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Long::class.javaPrimitiveType)) { param ->
                val delta = param.getArgument(0) as Float
                val renderHand = param.getArgument(1) as Boolean
                val time = param.getArgument(2) as Long

                // 获取本地玩家、视图矩阵
                val localPlayer = getLocalPlayer(loader)
                if (localPlayer != null && ConfigManager.isEspEnabled()) {
                    val viewMatrix = getViewMatrix(loader)
                    val entities = getEntityList(loader)

                    // 触发 Native 端缓存实体
                    NativeHooks.updateLocalPlayer(
                        getPlayerX(localPlayer), getPlayerY(localPlayer), getPlayerZ(localPlayer),
                        getPlayerYaw(localPlayer), getPlayerPitch(localPlayer),
                        getScreenWidth(), getScreenHeight()
                    )

                    // 绘制 ESP（通过悬浮窗 Canvas）
                    FloatingWindowService.drawEsp(viewMatrix, entities)
                }
                param.callOrigin()
            }

            // Hook BufferBuilder.addVertex -> 材质透视（X-Ray）
            hookMethod(bufferBuilderClass, "addVertex", arrayOf(Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)) { param ->
                if (ConfigManager.isXrayEnabled()) {
                    // 修改顶点 alpha 或跳过特定方块渲染
                    // 这里仅演示，实际需判断方块类型
                }
                param.callOrigin()
            }

            // Hook EntityRenderDispatcher.render -> 实体轮廓/发光
            hookMethod(entityRenderDispatcherClass, "render", arrayOf(
                loader.loadClass("net.minecraft.world.entity.Entity"),
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, Float::class.javaPrimitiveType
            )) { param ->
                val entity = param.getArgument(0) as Any
                if (ConfigManager.isEntityGlowEnabled() && isTargetEntity(entity, loader)) {
                    // 启用发光效果
                    enableGlow(entity, loader)
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Rendering hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 2. 实体/玩家：获取列表、属性修改
    // ------------------------------------------------------------------------
    private fun hookEntities(loader: ClassLoader) {
        try {
            val entityClass = loader.loadClass("net.minecraft.world.entity.Entity")
            val livingEntityClass = loader.loadClass("net.minecraft.world.entity.LivingEntity")
            val playerClass = loader.loadClass("net.minecraft.world.entity.player.Player")
            val levelClass = loader.loadClass("net.minecraft.world.level.Level")

            // Hook Entity.isAlive -> 无敌判定绕过
            hookMethod(livingEntityClass, "isAlive", arrayOf()) { param ->
                if (ConfigManager.isGodModeEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    param.setResult(true)
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook LivingEntity.getMaxHealth -> 血量显示修改
            hookMethod(livingEntityClass, "getMaxHealth", arrayOf()) { param ->
                if (ConfigManager.isGodModeEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    param.setResult(9999f)
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook LivingEntity.hurt -> 伤害免疫
            hookMethod(livingEntityClass, "hurt", arrayOf(
                loader.loadClass("net.minecraft.world.damage.DamageSource"),
                Float::class.javaPrimitiveType
            )) { param ->
                if (ConfigManager.isGodModeEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    param.setResult(false)
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook LivingEntity.heal -> 血量锁定
            hookMethod(livingEntityClass, "heal", arrayOf(Float::class.javaPrimitiveType)) { param ->
                if (ConfigManager.isGodModeEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    param.callOrigin(20f) // 强制满血
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook Player.getAbilities -> 飞行/创造模式能力
            hookMethod(playerClass, "getAbilities", arrayOf()) { param ->
                val abilities = param.callOrigin() as Any
                if (ConfigManager.isFlyEnabled() || ConfigManager.isInfiniteItemsEnabled()) {
                    setField(abilities, "mayfly", true)
                    setField(abilities, "flying", ConfigManager.isFlyEnabled())
                    setField(abilities, "instabuild", ConfigManager.isInfiniteItemsEnabled())
                }
                param.setResult(abilities)
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Entity hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 3. 移动/输入：飞行、加速、自瞄、长臂
    // ------------------------------------------------------------------------
    private fun hookMovement(loader: ClassLoader) {
        try {
            val playerClass = loader.loadClass("net.minecraft.world.entity.player.Player")
            val inputClass = loader.loadClass("net.minecraft.client.player.Input")
            val movementInputClass = loader.loadClass("net.minecraft.client.player.KeyboardInput")
            val gameSettingsClass = loader.loadClass("net.minecraft.client.Options")

            // Hook Player.tick -> 移动修正
            hookMethod(playerClass, "tick", arrayOf()) { param ->
                if (ConfigManager.isFlyEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    // 取消重力
                    setField(param.thisObject, "fallDistance", 0f)
                    setField(param.thisObject, "onGround", true)
                }
                if (ConfigManager.isSpeedEnabled() && isLocalPlayer(param.thisObject, loader)) {
                    val mult = ConfigManager.getSpeedMultiplier()
                    // 修改移动速度字段
                    val speedField = findField(playerClass, "speedMultiplier") ?: findField(playerClass, "movementSpeed")
                    speedField?.let { it.setFloat(param.thisObject, it.getFloat(param.thisObject) * mult) }
                }
                param.callOrigin()
            }

            // Hook Input.getMoveVector -> 移动向量注入
            hookMethod(inputClass, "getMoveVector", arrayOf()) { param ->
                val vec = param.callOrigin() as Any
                if (ConfigManager.isFlyEnabled()) {
                    // 允许垂直移动
                    setField(vec, "y", getField(vec, "y")) // 保持原值，由按键控制
                }
                param.setResult(vec)
            }

            // Hook Player.reachDistance -> 长臂
            hookMethod(playerClass, "getBlockReachDistance", arrayOf()) { param ->
                if (ConfigManager.isReachEnabled()) {
                    param.setResult(ConfigManager.getReachDistance())
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook Player.attackRange -> 攻击距离
            hookMethod(playerClass, "getAttackRange", arrayOf()) { param ->
                if (ConfigManager.isReachEnabled()) {
                    param.setResult(ConfigManager.getReachDistance())
                    return@hookMethod
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Movement hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 4. 网络/数据包：发包拦截、收包处理、防封
    // ------------------------------------------------------------------------
    private fun hookNetwork(loader: ClassLoader) {
        try {
            val connectionClass = loader.loadClass("net.minecraft.network.Connection")
            val packetListenerClass = loader.loadClass("net.minecraft.network.PacketListener")
            val packetClass = loader.loadClass("net.minecraft.network.protocol.Packet")
            val clientPacketListenerClass = loader.loadClass("net.minecraft.client.multiplayer.ClientPacketListener")

            // Hook Connection.send -> 发包控制
            hookMethod(connectionClass, "send", arrayOf(packetClass)) { param ->
                val packet = param.getArgument(0) as Any
                if (PacketHooks.shouldBlockOutgoing(packet, loader)) {
                    param.setResult(Unit) // 丢弃
                    return@hookMethod
                }
                if (PacketHooks.shouldModifyOutgoing(packet, loader)) {
                    PacketHooks.modifyOutgoing(packet, loader)
                }
                param.callOrigin()
            }

            // Hook ClientPacketListener.handlePacket -> 收包处理
            hookMethod(clientPacketListenerClass, "handlePacket", arrayOf(
                packetClass, loader.loadClass("net.minecraft.network.protocol.PacketFlow")
            )) { param ->
                val packet = param.getArgument(0) as Any
                if (PacketHooks.shouldHandleIncoming(packet, loader)) {
                    PacketHooks.handleIncoming(packet, loader)
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Network hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 5. 物品/背包：无限物品、自动整理、快速移动
    // ------------------------------------------------------------------------
    private fun hookInventory(loader: ClassLoader) {
        try {
            val playerClass = loader.loadClass("net.minecraft.world.entity.player.Player")
            val inventoryClass = loader.loadClass("net.minecraft.world.inventory.Inventory")
            val itemStackClass = loader.loadClass("net.minecraft.world.item.ItemStack")
            val containerClass = loader.loadClass("net.minecraft.world.inventory.AbstractContainerMenu")
            val slotClass = loader.loadClass("net.minecraft.world.inventory.Slot")

            // Hook Inventory.add -> 无限堆叠
            hookMethod(inventoryClass, "add", arrayOf(itemStackClass)) { param ->
                val stack = param.getArgument(0) as Any
                if (ConfigManager.isInfiniteItemsEnabled()) {
                    setField(stack, "count", 64) // 最大堆叠
                }
                param.callOrigin()
            }

            // Hook ItemStack.getMaxStackSize -> 修改最大堆叠数
            hookMethod(itemStackClass, "getMaxStackSize", arrayOf()) { param ->
                if (ConfigManager.isInfiniteItemsEnabled()) {
                    param.setResult(64)
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook Slot.onTake -> 物品不减
            hookMethod(slotClass, "onTake", arrayOf(
                playerClass, itemStackClass
            )) { param ->
                if (ConfigManager.isInfiniteItemsEnabled() && isLocalPlayer(param.getArgument(0), loader)) {
                    // 不调用原函数，物品不消耗
                    param.setResult(Unit)
                    return@hookMethod
                }
                param.callOrigin()
            }

            // Hook PlayerContainer.quickMoveStack -> 快速移动物品
            hookMethod(containerClass, "quickMoveStack", arrayOf(Int::class.javaPrimitiveType)) { param ->
                if (ConfigManager.isAutoSortEnabled()) {
                    // 自定义快速移动逻辑
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Inventory hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 6. GUI/菜单：自动点击、界面穿透、物品显示
    // ------------------------------------------------------------------------
    private fun hookGui(loader: ClassLoader) {
        try {
            val screenClass = loader.loadClass("net.minecraft.client.gui.screens.Screen")
            val inventoryScreenClass = loader.loadClass("net.minecraft.client.gui.screens.inventory.InventoryScreen")
            val creativeScreenClass = loader.loadClass("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen")

            // Hook Screen.render -> GUI 绘制覆盖
            hookMethod(screenClass, "render", arrayOf(
                loader.loadClass("com.mojang.blaze3d.vertex.PoseStack"),
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )) { param ->
                if (ConfigManager.isGuiOverlayEnabled()) {
                    val poseStack = param.getArgument(0) as Any
                    val mouseX = param.getArgument(1) as Int
                    val mouseY = param.getArgument(2) as Int
                    FloatingWindowService.drawGuiOverlay(poseStack, mouseX, mouseY)
                }
                param.callOrigin()
            }

            // Hook CreativeModeInventoryScreen -> 创造模式物品获取
            hookMethod(creativeScreenClass, "handleInventoryButtonClick", arrayOf(Int::class.javaPrimitiveType)) { param ->
                if (ConfigManager.isInfiniteItemsEnabled()) {
                    // 允许在生存模式下获取创造模式物品
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "GUI hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 7. 世界/方块：透视方块、自动挖掘、防爆炸
    // ------------------------------------------------------------------------
    private fun hookWorld(loader: ClassLoader) {
        try {
            val levelClass = loader.loadClass("net.minecraft.world.level.Level")
            val blockStateClass = loader.loadClass("net.minecraft.world.level.block.state.BlockState")
            val playerClass = loader.loadClass("net.minecraft.world.entity.player.Player")

            // Hook Level.getBlockState -> X-Ray 方块透视
            hookMethod(levelClass, "getBlockState", arrayOf(
                loader.loadClass("net.minecraft.core.BlockPos")
            )) { param ->
                if (ConfigManager.isXrayEnabled()) {
                    val pos = param.getArgument(0) as Any
                    // 返回空气方块状态以穿透渲染
                    // 实际需判断目标方块类型
                }
                param.callOrigin()
            }

            // Hook PlayerGameMode.destroyBlock -> 自动挖掘/瞬间破坏
            val gameModeClass = loader.loadClass("net.minecraft.client.multiplayer.PlayerController")
            hookMethod(gameModeClass, "destroyBlock", arrayOf(
                loader.loadClass("net.minecraft.core.BlockPos")
            )) { param ->
                if (ConfigManager.isInstantMineEnabled()) {
                    // 直接破坏
                    param.callOrigin()
                    return@hookMethod
                }
                param.callOrigin()
            }

        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "World hooks failed")
        }
    }

    // ------------------------------------------------------------------------
    // 通用 Hook 封装
    // ------------------------------------------------------------------------
    private fun hookMethod(clazz: Class<*>, methodName: String, argTypes: Array<Class<*>>, handler: (HookParam) -> Unit) {
        val key = "${clazz.name}#$methodName"
        if (hookedMethods.containsKey(key)) return

        try {
            val method = clazz.getDeclaredMethod(methodName, *argTypes)
            method.isAccessible = true

            LspdHook.hookMethod(method, object : MethodHook {
                override fun beforeHook(param: HookParam) = handler(param)
                override fun afterHook(param: HookParam) {}
            })

            hookedMethods[key] = true
            Timber.tag(TAG).d("Hooked: $key")
        } catch (e: NoSuchMethodException) {
            Timber.tag(TAG).w("Method not found: $key")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Hook failed: $key")
        }
    }

    // ------------------------------------------------------------------------
    // 反射工具
    // ------------------------------------------------------------------------
    private fun getLocalPlayer(loader: ClassLoader): Any? {
        try {
            val clientClass = loader.loadClass("net.minecraft.client.Minecraft")
            val instance = clientClass.getDeclaredField("instance").apply { isAccessible = true }.get(null)
            return clientClass.getDeclaredField("player").apply { isAccessible = true }.get(instance)
        } catch (e: Exception) { return null }
    }

    private fun getEntityList(loader: ClassLoader): List<Any> {
        try {
            val levelClass = loader.loadClass("net.minecraft.world.level.Level")
            val entitiesField = levelClass.getDeclaredField("entities").apply { isAccessible = true }
            val localPlayer = getLocalPlayer(loader)
            val level = localPlayer?.let { getField(it, "level") }
            return (level?.let { entitiesField.get(it) as List<Any> } ?: emptyList())
        } catch (e: Exception) { return emptyList() }
    }

    private fun getViewMatrix(loader: ClassLoader): FloatArray {
        // 返回 16 个 float 的视图投影矩阵
        return FloatArray(16)
    }

    private fun isLocalPlayer(obj: Any, loader: ClassLoader): Boolean {
        val local = getLocalPlayer(loader)
        return obj === local
    }

    private fun isTargetEntity(entity: Any, loader: ClassLoader): Boolean {
        // 判断是否为玩家/生物/箱子等
        return true
    }

    private fun enableGlow(entity: Any, loader: ClassLoader) {
        try {
            val entityClass = loader.loadClass("net.minecraft.world.entity.Entity")
            val glowingField = entityClass.getDeclaredField("glowing").apply { isAccessible = true }
            glowingField.setBoolean(entity, true)
        } catch (e: Exception) {}
    }

    private fun findField(clazz: Class<*>, name: String) = clazz.getDeclaredField(name).apply { isAccessible = true }

    private fun getField(obj: Any, name: String): Any? {
        try {
            val field = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
            return field.get(obj)
        } catch (e: Exception) { return null }
    }

    private fun setField(obj: Any, name: String, value: Any) {
        try {
            val field = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }
            field.set(obj, value)
        } catch (e: Exception) {}
    }

    private fun getPlayerX(p: Any) = getField(p, "x") as? Float ?: 0f
    private fun getPlayerY(p: Any) = getField(p, "y") as? Float ?: 0f
    private fun getPlayerZ(p: Any) = getField(p, "z") as? Float ?: 0f
    private fun getPlayerYaw(p: Any) = getField(p, "yRot") as? Float ?: 0f
    private fun getPlayerPitch(p: Any) = getField(p, "xRot") as? Float ?: 0f
    private fun getScreenWidth() = 1920
    private fun getScreenHeight() = 1080
}