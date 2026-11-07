#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cctype>
#include <sys/mman.h>
#include <link.h>
#include <dirent.h>
#include <unistd.h>
#include <cstring>

#define TAG "MCX19Hook-SigScan"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct MemoryRegion {
    uintptr_t start;
    uintptr_t end;
    char perms[8];
    char path[256];
};

struct Signature {
    std::vector<uint8_t> bytes;
    std::vector<bool> mask; // true = must match, false = wildcard
    std::string name;
    int offset = 0; // 相对偏移
    bool relative = false; // 是否为 RIP 相对
};

static std::vector<MemoryRegion> g_regions;
static std::vector<Signature> g_signatures;
static uintptr_t g_lib_base = 0;

// ==================== 内存区域枚举 ====================
static void scanMemoryMaps() {
    g_regions.clear();
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return;

    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        MemoryRegion r{};
        uintptr_t start, end;
        char perms[8], path[256] = "";
        int offset, dev, inode;

        int matched = sscanf(line, "%lx-%lx %7s %x %x:%x %d %255s",
            &start, &end, perms, &offset, &dev, &inode, &inode, path);
        if (matched >= 6) {
            r.start = start;
            r.end = end;
            strncpy(r.perms, perms, 7);
            r.perms[7] = '\0';
            if (matched >= 7) {
                strncpy(r.path, path, 255);
                r.path[255] = '\0';
            }
            // 只扫描可执行且属于目标库的区域
            if ((strstr(r.perms, "x") || strstr(r.perms, "r")) &&
                (strstr(r.path, "libminecraftpe.so") || strstr(r.path, "libcocos2dcpp.so"))) {
                g_regions.push_back(r);
                if (g_lib_base == 0 && strstr(r.path, "libminecraftpe.so")) {
                    g_lib_base = r.start;
                }
            }
        }
    }
    fclose(fp);
    LOGD("Scanned %zu memory regions, lib base: 0x%lx", g_regions.size(), g_lib_base);
}

// ==================== 解析 IDA 风格特征码 ====================
// 格式: "48 89 5C 24 ? 48 89 74 24 ? 57 48 83 EC 20"
static Signature parseSignature(const std::string& name, const std::string& pattern, int offset = 0, bool relative = false) {
    Signature sig;
    sig.name = name;
    sig.offset = offset;
    sig.relative = relative;

    std::istringstream iss(pattern);
    std::string token;
    while (iss >> token) {
        if (token == "?" || token == "??") {
            sig.bytes.push_back(0);
            sig.mask.push_back(false);
        } else {
            uint8_t byte = static_cast<uint8_t>(std::stoul(token, nullptr, 16));
            sig.bytes.push_back(byte);
            sig.mask.push_back(true);
        }
    }
    return sig;
}

// ==================== 内存比对 ====================
static bool matchBytes(const uint8_t* mem, const Signature& sig) {
    if (sig.bytes.size() != sig.mask.size()) return false;
    for (size_t i = 0; i < sig.bytes.size(); ++i) {
        if (sig.mask[i] && mem[i] != sig.bytes[i]) return false;
    }
    return true;
}

// ==================== 扫描单个区域 ====================
static uintptr_t scanRegion(const MemoryRegion& region, const Signature& sig) {
    if (region.end <= region.start) return 0;
    size_t size = region.end - region.start;
    if (size < sig.bytes.size()) return 0;

    const uint8_t* start = reinterpret_cast<const uint8_t*>(region.start);
    const uint8_t* end = start + size - sig.bytes.size();

    for (const uint8_t* p = start; p <= end; ++p) {
        if (matchBytes(p, sig)) {
            uintptr_t found = reinterpret_cast<uintptr_t>(p);
            // 处理相对偏移
            if (sig.relative && sig.offset != 0) {
                // RIP 相对：地址 = 当前位置 + 偏移 + 指令长度
                // 假设是 call/jmp 指令 (5字节) 或 lea (7字节)
                int32_t rel = *reinterpret_cast<const int32_t*>(p + sig.offset);
                found = found + sig.offset + 4 + rel;
            } else if (sig.offset != 0) {
                found += sig.offset;
            }
            return found;
        }
    }
    return 0;
}

// ==================== 公开扫描接口 ====================
extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_SigScan_scanSignature(JNIEnv* env, jclass clazz,
    jstring name, jstring pattern, jint offset, jboolean relative) {

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    const char* patternStr = env->GetStringUTFChars(pattern, nullptr);

    Signature sig = parseSignature(nameStr, patternStr, offset, relative);

    if (g_regions.empty()) scanMemoryMaps();

    for (const auto& region : g_regions) {
        uintptr_t result = scanRegion(region, sig);
        if (result != 0) {
            LOGD("Found %s at 0x%lx (region: %s)", nameStr, result, region.path);
            env->ReleaseStringUTFChars(name, nameStr);
            env->ReleaseStringUTFChars(pattern, patternStr);
            return static_cast<jlong>(result);
        }
    }

    LOGE("Signature %s not found", nameStr);
    env->ReleaseStringUTFChars(name, nameStr);
    env->ReleaseStringUTFChars(pattern, patternStr);
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_SigScan_scanMultiple(JNIEnv* env, jclass clazz,
    jobjectArray names, jobjectArray patterns, jintArray offsets, jbooleanArray relatives) {

    int count = env->GetArrayLength(names);
    jint* offArr = env->GetIntArrayElements(offsets, nullptr);
    jboolean* relArr = env->GetBooleanArrayElements(relatives, nullptr);

    if (g_regions.empty()) scanMemoryMaps();

    for (int i = 0; i < count; ++i) {
        jstring name = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        jstring pattern = static_cast<jstring>(env->GetObjectArrayElement(patterns, i));
        const char* nameStr = env->GetStringUTFChars(name, nullptr);
        const char* patternStr = env->GetStringUTFChars(pattern, nullptr);

        Signature sig = parseSignature(nameStr, patternStr, offArr[i], relArr[i]);

        for (const auto& region : g_regions) {
            uintptr_t result = scanRegion(region, sig);
            if (result != 0) {
                LOGD("Found %s at 0x%lx", nameStr, result);
                env->ReleaseStringUTFChars(name, nameStr);
                env->ReleaseStringUTFChars(pattern, patternStr);
                env->ReleaseIntArrayElements(offsets, offArr, 0);
                env->ReleaseBooleanArrayElements(relatives, relArr, 0);
                return static_cast<jlong>(result);
            }
        }

        env->ReleaseStringUTFChars(name, nameStr);
        env->ReleaseStringUTFChars(pattern, patternStr);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(pattern);
    }

    env->ReleaseIntArrayElements(offsets, offArr, 0);
    env->ReleaseBooleanArrayElements(relatives, relArr, 0);
    return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_SigScan_getLibBase(JNIEnv* env, jclass clazz) {
    if (g_lib_base == 0) scanMemoryMaps();
    return static_cast<jlong>(g_lib_base);
}

extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_util_SigScan_loadSignaturesFromAssets(JNIEnv* env, jclass clazz,
    jobject assetManager, jstring fileName) {

    // 通过 AAssetManager 读取 assets/signatures.json
    // 解析 JSON 并填充 g_signatures
    // 这里仅留接口，实际解析在 Java 端完成后调用 scanSignature
    LOGD("loadSignaturesFromAssets called");
}