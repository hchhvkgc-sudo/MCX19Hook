package com.linyu.mcx19hook.util

import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.InputStreamReader
import java.lang.reflect.Type

object SigScan {

    private const val TAG = "MCX19Hook-SigScan"

    data class Signature(
        val name: String,
        val pattern: String,      // IDA 风格: "48 89 5C 24 ? 48 89 74 24 ?"
        val offset: Int = 0,      // 相对偏移
        val relative: Boolean = false // RIP 相对
    )

    data class ScanResult(
        val name: String,
        val address: Long,
        val library: String
    )

    companion object {
        @JvmStatic
        external fun scanSignature(name: String, pattern: String, offset: Int, relative: Boolean): Long

        @JvmStatic
        external fun scanMultiple(names: Array<String>, patterns: Array<String>, offsets: IntArray, relatives: BooleanArray): Long

        @JvmStatic
        external fun getLibBase(): Long

        @JvmStatic
        external fun loadSignaturesFromAssets(assetManager: AssetManager, fileName: String)

        // 高级封装
        fun scan(sig: Signature): Long? {
            val addr = scanSignature(sig.name, sig.pattern, sig.offset, sig.relative)
            return if (addr != 0L) addr else null
        }

        fun scanAll(sigs: List<Signature>): Map<String, Long> {
            if (sigs.isEmpty()) return emptyMap()
            val names = sigs.map { it.name }.toTypedArray()
            val patterns = sigs.map { it.pattern }.toTypedArray()
            val offsets = sigs.map { it.offset }.toIntArray()
            val relatives = sigs.map { it.relative }.toBooleanArray()

            val firstAddr = scanMultiple(names, patterns, offsets, relatives)
            val result = mutableMapOf<String, Long>()
            if (firstAddr != 0L) {
                // 仅返回第一个找到的，完整实现需多次调用或扩展 Native 接口
                result[names[0]] = firstAddr
            }
            return result
        }

        fun loadFromAssets(context: android.content.Context, fileName: String = "signatures.json") {
            loadSignaturesFromAssets(context.assets, fileName)
        }

        fun parseSignaturesFromJson(json: String): List<Signature> {
            val gson = Gson()
            val type = object : TypeToken<List<Signature>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }

        fun parseSignaturesFromAssets(context: android.content.Context, fileName: String = "signatures.json"): List<Signature> {
            return try {
                val input = context.assets.open(fileName)
                InputStreamReader(input).use { reader ->
                    val gson = Gson()
                    val type = object : TypeToken<List<Signature>>() {}.type
                    gson.fromJson(reader, type) ?: emptyList()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to parse signatures from assets")
                emptyList()
            }
        }
    }
}