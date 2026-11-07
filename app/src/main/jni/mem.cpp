#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <vector>
#include <link.h>

#define TAG "MCX19Hook-Memory"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ==================== 进程内存映射缓存 ====================
struct MemRegion {
    uintptr_t start, end;
    int prot;
    char path[256];
};
static std::vector<MemRegion> g_mem_regions;
static std::mutex g_mem_mutex;

static void refreshMemRegions() {
    std::lock_guard<std::mutex> lock(g_mem_mutex);
    g_mem_regions.clear();
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return;
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        MemRegion r{};
        uintptr_t start, end;
        char perms[8], path[256] = "";
        sscanf(line, "%lx-%lx %7s %*x %*x:%*x %*d %255s", &start, &end, perms, path);
        r.start = start; r.end = end;
        r.prot = 0;
        if (strchr(perms, 'r')) r.prot |= PROT_READ;
        if (strchr(perms, 'w')) r.prot |= PROT_WRITE;
        if (strchr(perms, 'x')) r.prot |= PROT_EXEC;
        strncpy(r.path, path, 255);
        g_mem_regions.push_back(r);
    }
    fclose(fp);
}

static int getRegionProt(uintptr_t addr) {
    std::lock_guard<std::mutex> lock(g_mem_mutex);
    for (const auto& r : g_mem_regions) {
        if (addr >= r.start && addr < r.end) return r.prot;
    }
    return 0;
}

// ==================== 安全读取 ====================
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeRead(JNIEnv* env, jclass clazz,
    jlong address, jint size) {

    if (address == 0 || size <= 0 || size > 1024*1024) return env->NewByteArray(0);

    // 检查可读
    int prot = getRegionProt(address);
    if (!(prot & PROT_READ)) {
        // 尝试修改保护
        void* page = reinterpret_cast<void*>(address & ~0xFFF);
        if (mprotect(page, 0x1000, prot | PROT_READ) != 0) {
            return env->NewByteArray(0);
        }
    }

    jbyteArray arr = env->NewByteArray(size);
    if (!arr) return nullptr;

    // 使用 memcpy 避免 SIGSEGV 崩溃
    std::vector<uint8_t> buf(size);
    __builtin_memcpy(buf.data(), reinterpret_cast<void*>(address), size);
    env->SetByteArrayRegion(arr, 0, size, reinterpret_cast<jbyte*>(buf.data()));
    return arr;
}

// ==================== 安全写入 ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeWrite(JNIEnv* env, jclass clazz,
    jlong address, jbyteArray data) {

    if (address == 0 || !data) return JNI_FALSE;
    jsize size = env->GetArrayLength(data);
    if (size <= 0 || size > 1024*1024) return JNI_FALSE;

    int prot = getRegionProt(address);
    if (!(prot & PROT_WRITE)) {
        void* page = reinterpret_cast<void*>(address & ~0xFFF);
        if (mprotect(page, 0x1000, prot | PROT_WRITE) != 0) {
            return JNI_FALSE;
        }
    }

    std::vector<uint8_t> buf(size);
    env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(buf.data()));
    __builtin_memcpy(reinterpret_cast<void*>(address), buf.data(), size);
    return JNI_TRUE;
}

// ==================== 修改保护 ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeProtect(JNIEnv* env, jclass clazz,
    jlong address, jint size, jint prot) {

    if (address == 0 || size <= 0) return JNI_FALSE;
    void* page = reinterpret_cast<void*>(address & ~0xFFF);
    size_t pageSize = ((size + 0xFFF) / 0x1000) * 0x1000;
    return mprotect(page, pageSize, prot) == 0 ? JNI_TRUE : JNI_FALSE;
}

// ==================== 分配可执行内存 (用于 trampoline/shellcode) ====================
extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeAllocExec(JNIEnv* env, jclass clazz, jint size) {
    if (size <= 0 || size > 1024*1024) return 0;
    void* ptr = mmap(nullptr, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    return ptr == MAP_FAILED ? 0 : reinterpret_cast<jlong>(ptr);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeFree(JNIEnv* env, jclass clazz, jlong address, jint size) {
    if (address == 0 || size <= 0) return JNI_FALSE;
    return munmap(reinterpret_cast<void*>(address), size) == 0 ? JNI_TRUE : JNI_FALSE;
}

// ==================== 搜索内存 (字节模式) ====================
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeFindPattern(JNIEnv* env, jclass clazz,
    jbyteArray pattern, jbyteArray mask, jlong startAddr, jlong endAddr) {

    jsize patLen = env->GetArrayLength(pattern);
    if (patLen <= 0) return env->NewLongArray(0);

    std::vector<uint8_t> pat(patLen), msk(patLen);
    env->GetByteArrayRegion(pattern, 0, patLen, reinterpret_cast<jbyte*>(pat.data()));
    env->GetByteArrayRegion(mask, 0, patLen, reinterpret_cast<jbyte*>(msk.data()));

    uintptr_t start = startAddr > 0 ? startAddr : 0x10000000; // 默认从堆开始
    uintptr_t end = endAddr > 0 ? endAddr : 0x7FFFFFFFFFFF;

    std::vector<jlong> results;
    refreshMemRegions();

    std::lock_guard<std::mutex> lock(g_mem_mutex);
    for (const auto& r : g_mem_regions) {
        if (r.end <= start || r.start >= end) continue;
        if (!(r.prot & PROT_READ)) continue;

        uintptr_t scanStart = std::max(r.start, start);
        uintptr_t scanEnd = std::min(r.end, end);
        if (scanEnd - scanStart < patLen) continue;

        const uint8_t* p = reinterpret_cast<const uint8_t*>(scanStart);
        const uint8_t* scanEndPtr = p + (scanEnd - scanStart - patLen);

        for (; p <= scanEndPtr; ++p) {
            bool match = true;
            for (int i = 0; i < patLen; ++i) {
                if (msk[i] && p[i] != pat[i]) { match = false; break; }
            }
            if (match) results.push_back(reinterpret_cast<jlong>(p));
        }
    }

    jlongArray arr = env->NewLongArray(results.size());
    env->SetLongArrayRegion(arr, 0, results.size(), results.data());
    return arr;
}

// ==================== 读取指针链 ====================
extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_Memory_nativeReadPointerChain(JNIEnv* env, jclass clazz,
    jlong base, jlongArray offsets) {

    jsize count = env->GetArrayLength(offsets);
    jlong* offs = env->GetLongArrayElements(offsets, nullptr);

    uintptr_t addr = base;
    for (int i = 0; i < count; ++i) {
        jbyteArray ptrBytes = static_cast<jbyteArray>(
            env->CallStaticObjectMethod(
                env->FindClass("com/linyu/mcx19hook/util/Memory"),
                env->GetStaticMethodID(env->FindClass("com/linyu/mcx19hook/util/Memory"), "nativeRead", "(JI)[B"),
                addr, 8
            )
        );
        if (!ptrBytes) { addr = 0; break; }
        jbyte* bytes = env->GetByteArrayElements(ptrBytes, nullptr);
        addr = *reinterpret_cast<uintptr_t*>(bytes);
        env->ReleaseByteArrayElements(ptrBytes, bytes, 0);
        env->DeleteLocalRef(ptrBytes);
        if (addr == 0) break;
        addr += offs[i];
    }
    env->ReleaseLongArrayElements(offsets, offs, 0);
    return static_cast<jlong>(addr);
}