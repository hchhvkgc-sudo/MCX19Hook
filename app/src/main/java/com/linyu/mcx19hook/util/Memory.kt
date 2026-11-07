package com.linyu.mcx19hook.util

object Memory {

    companion object {
        @JvmStatic
        external fun nativeRead(address: Long, size: Int): ByteArray?

        @JvmStatic
        external fun nativeWrite(address: Long, data: ByteArray): Boolean

        @JvmStatic
        external fun nativeProtect(address: Long, size: Int, prot: Int): Boolean

        @JvmStatic
        external fun nativeAllocExec(size: Int): Long

        @JvmStatic
        external fun nativeFree(address: Long, size: Int): Boolean

        @JvmStatic
        external fun nativeFindPattern(pattern: ByteArray, mask: ByteArray, startAddr: Long, endAddr: Long): LongArray?

        @JvmStatic
        external fun nativeReadPointerChain(base: Long, offsets: LongArray): Long

        // 便捷方法
        fun readBytes(address: Long, size: Int): ByteArray? = nativeRead(address, size)
        fun writeBytes(address: Long, data: ByteArray): Boolean = nativeWrite(address, data)
        fun readInt(address: Long): Int? = nativeRead(address, 4)?.let { java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).int }
        fun writeInt(address: Long, value: Int): Boolean = nativeWrite(address, java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun readLong(address: Long): Long? = nativeRead(address, 8)?.let { java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).long }
        fun writeLong(address: Long, value: Long): Boolean = nativeWrite(address, java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(value).array())
        fun readFloat(address: Long): Float? = nativeRead(address, 4)?.let { java.nio.ByteBuffer.wrap(it).order(java.nio.ByteOrder.LITTLE_ENDIAN).float }
        fun writeFloat(address: Long, value: Float): Boolean = nativeWrite(address, java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(value).array())
    }
}