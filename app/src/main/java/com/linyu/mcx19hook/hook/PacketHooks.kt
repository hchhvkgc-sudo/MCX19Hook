package com.linyu.mcx19hook.hook

import timber.log.Timber

object PacketHooks {

    private const val TAG = "MCX19Hook-Packet"

    // 数据包 ID 常量（需根据版本动态获取）
    private object PacketId {
        const val PLAYER_POSITION = 0x0B
        const val PLAYER_ROTATION = 0x0C
        const val PLAYER_POSITION_ROTATION = 0x0D
        const val PLAYER_ACTION = 0x13
        const val BLOCK_BREAK = 0x14
        const val BLOCK_PLACE = 0x15
        const val INVENTORY_ACTION = 0x17
        const val CREATIVE_INVENTORY_ACTION = 0x18
        const val PLAYER_ABILITIES = 0x2C
        const val CHAT = 0x03
        const val KEEP_ALIVE = 0x21
        const val RESOURCE_PACK_STACK = 0x3A
        const val CLIENT_STATUS = 0x16
        const val SET_SLOT = 0x2F
        const val CONTAINER_SET_CONTENT = 0x30
    }

    // 拦截发出的包
    fun shouldBlockOutgoing(packet: Any, loader: ClassLoader): Boolean {
        if (!ConfigManager.isAntiCheatBypassEnabled()) return false

        val packetId = getPacketId(packet, loader)
        return when (packetId) {
            // 屏蔽举报包、心跳包篡改检测等
            PacketId.RESOURCE_PACK_STACK -> true
            PacketId.CLIENT_STATUS -> isSuspiciousClientStatus(packet, loader)
            else -> false
        }
    }

    // 修改发出的包
    fun shouldModifyOutgoing(packet: Any, loader: ClassLoader): Boolean {
        if (!ConfigManager.isPacketModificationEnabled()) return false

        val packetId = getPacketId(packet, loader)
        return when (packetId) {
            PacketId.PLAYER_POSITION_ROTATION -> ConfigManager.isFlyEnabled() || ConfigManager.isSpeedEnabled()
            PacketId.PLAYER_ACTION -> ConfigManager.isAutoActionEnabled()
            PacketId.BLOCK_BREAK -> ConfigManager.isInstantMineEnabled()
            PacketId.PLAYER_ABILITIES -> ConfigManager.isFlyEnabled() || ConfigManager.isGodModeEnabled()
            else -> false
        }
    }

    fun modifyOutgoing(packet: Any, loader: ClassLoader) {
        val packetId = getPacketId(packet, loader)
        when (packetId) {
            PacketId.PLAYER_POSITION_ROTATION -> modifyPositionRotation(packet, loader)
            PacketId.PLAYER_ACTION -> modifyPlayerAction(packet, loader)
            PacketId.BLOCK_BREAK -> modifyBlockBreak(packet, loader)
            PacketId.PLAYER_ABILITIES -> modifyPlayerAbilities(packet, loader)
        }
    }

    // 处理接收的包
    fun shouldHandleIncoming(packet: Any, loader: ClassLoader): Boolean {
        val packetId = getPacketId(packet, loader)
        return when (packetId) {
            PacketId.KEEP_ALIVE -> ConfigManager.isAutoKeepAliveEnabled()
            PacketId.SET_SLOT -> ConfigManager.isInfiniteItemsEnabled()
            PacketId.CONTAINER_SET_CONTENT -> ConfigManager.isAutoSortEnabled()
            else -> false
        }
    }

    fun handleIncoming(packet: Any, loader: ClassLoader) {
        val packetId = getPacketId(packet, loader)
        when (packetId) {
            PacketId.KEEP_ALIVE -> autoRespondKeepAlive(packet, loader)
            PacketId.SET_SLOT -> fixItemCount(packet, loader)
            PacketId.CONTAINER_SET_CONTENT -> autoSortInventory(packet, loader)
        }
    }

    // ==================== 具体包处理实现 ====================

    private fun getPacketId(packet: Any, loader: ClassLoader): Int {
        try {
            val packetClass = loader.loadClass("net.minecraft.network.protocol.Packet")
            val idField = packetClass.getDeclaredField("id").apply { isAccessible = true }
            return idField.getInt(packet)
        } catch (e: Exception) {
            return -1
        }
    }

    private fun isSuspiciousClientStatus(packet: Any, loader: ClassLoader): Boolean {
        try {
            val statusClass = loader.loadClass("net.minecraft.network.protocol.game.ClientStatusPacket")
            val statusField = statusClass.getDeclaredField("status").apply { isAccessible = true }
            val status = statusField.getInt(packet)
            // 屏蔽 RESPAWN 等可疑状态
            return status == 1 // RESPAWN
        } catch (e: Exception) { return false }
    }

    private fun modifyPositionRotation(packet: Any, loader: ClassLoader) {
        try {
            val posRotClass = loader.loadClass("net.minecraft.network.protocol.game.PlayerPositionRotationPacket")
            if (ConfigManager.isFlyEnabled()) {
                // 修正 Y 坐标防止飞行踢出
                val yField = posRotClass.getDeclaredField("y").apply { isAccessible = true }
                val y = yField.getDouble(packet)
                // 保持服务端认为在合理高度
                if (y > 256) yField.setDouble(packet, 255.0)
            }
            if (ConfigManager.isSpeedEnabled()) {
                // 平滑移动包防检测
                val xField = posRotClass.getDeclaredField("x").apply { isAccessible = true }
                val zField = posRotClass.getDeclaredField("z").apply { isAccessible = true }
                // 可添加抖动
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "modifyPositionRotation failed")
        }
    }

    private fun modifyPlayerAction(packet: Any, loader: ClassLoader) {
        try {
            val actionClass = loader.loadClass("net.minecraft.network.protocol.game.PlayerActionPacket")
            val actionField = actionClass.getDeclaredField("action").apply { isAccessible = true }
            val action = actionField.getInt(packet)
            if (ConfigManager.isAutoActionEnabled()) {
                // 自动完成动作（如自动吃食、自动格挡）
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "modifyPlayerAction failed")
        }
    }

    private fun modifyBlockBreak(packet: Any, loader: ClassLoader) {
        if (ConfigManager.isInstantMineEnabled()) {
            try {
                val breakClass = loader.loadClass("net.minecraft.network.protocol.game.BlockBreakPacket")
                val stageField = breakClass.getDeclaredField("stage").apply { isAccessible = true }
                // 直接发送完成阶段
                stageField.setInt(packet, 2) // FINISHED
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "modifyBlockBreak failed")
            }
        }
    }

    private fun modifyPlayerAbilities(packet: Any, loader: ClassLoader) {
        try {
            val abilitiesClass = loader.loadClass("net.minecraft.network.protocol.game.PlayerAbilitiesPacket")
            if (ConfigManager.isFlyEnabled()) {
                val flyingField = abilitiesClass.getDeclaredField("flying").apply { isAccessible = true }
                flyingField.setBoolean(packet, true)
                val mayFlyField = abilitiesClass.getDeclaredField("mayFly").apply { isAccessible = true }
                mayFlyField.setBoolean(packet, true)
            }
            if (ConfigManager.isGodModeEnabled()) {
                val invulnerableField = abilitiesClass.getDeclaredField("invulnerable").apply { isAccessible = true }
                invulnerableField.setBoolean(packet, true)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "modifyPlayerAbilities failed")
        }
    }

    private fun autoRespondKeepAlive(packet: Any, loader: ClassLoader) {
        try {
            val keepAliveClass = loader.loadClass("net.minecraft.network.protocol.game.KeepAlivePacket")
            val idField = keepAliveClass.getDeclaredField("id").apply { isAccessible = true }
            val keepAliveId = idField.getLong(packet)
            // 立即回复相同 ID
            // 需获取 Connection 发送回复包
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "autoRespondKeepAlive failed")
        }
    }

    private fun fixItemCount(packet: Any, loader: ClassLoader) {
        if (ConfigManager.isInfiniteItemsEnabled()) {
            try {
                val setSlotClass = loader.loadClass("net.minecraft.network.protocol.game.SetSlotPacket")
                val stackField = setSlotClass.getDeclaredField("stack").apply { isAccessible = true }
                val stack = stackField.get(packet)
                if (stack != null) {
                    val itemStackClass = loader.loadClass("net.minecraft.world.item.ItemStack")
                    val countField = itemStackClass.getDeclaredField("count").apply { isAccessible = true }
                    val count = countField.getInt(stack)
                    if (count < 64) countField.setInt(stack, 64)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "fixItemCount failed")
            }
        }
    }

    private fun autoSortInventory(packet: Any, loader: ClassLoader) {
        if (ConfigManager.isAutoSortEnabled()) {
            // 自动整理背包逻辑
        }
    }
}