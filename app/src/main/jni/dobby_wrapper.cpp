#include <dobby.h>
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#define TAG "MCX19Hook-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_hook_mutex;
static std::unordered_map<std::string, void*> g_original_functions;
static std::unordered_map<std::string, DobbyHookEntry*> g_hook_entries;

// ==================== 通用Inline Hook封装 ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_DobbyWrapper_inlineHook(
    JNIEnv* env, jclass clazz,
    jstring targetLib, jstring targetSymbol,
    jlong replacementPtr, jobjectArray outOriginalPtrArray) {

    const char* libName = env->GetStringUTFChars(targetLib, nullptr);
    const char* symName = env->GetStringUTFChars(targetSymbol, nullptr);
    void* replacement = reinterpret_cast<void*>(replacementPtr);
    void* original = nullptr;

    std::lock_guard<std::mutex> lock(g_hook_mutex);
    std::string key = std::string(libName) + "!" + symName;

    if (g_hook_entries.find(key) != g_hook_entries.end()) {
        if (outOriginalPtrArray != nullptr) {
            jlong orig = reinterpret_cast<jlong>(g_original_functions[key]);
            jclass longClass = env->FindClass("java/lang/Long");
            jmethodID ctor = env->GetMethodID(longClass, "<init>", "(J)V");
            env->SetObjectArrayElement(outOriginalPtrArray, 0, env->NewObject(longClass, ctor, orig));
        }
        env->ReleaseStringUTFChars(targetLib, libName);
        env->ReleaseStringUTFChars(targetSymbol, symName);
        return JNI_TRUE;
    }

    void* handle = dlopen(libName, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        LOGE("dlopen(%s) failed: %s", libName, dlerror());
        env->ReleaseStringUTFChars(targetLib, libName);
        env->ReleaseStringUTFChars(targetSymbol, symName);
        return JNI_FALSE;
    }

    void* target = dlsym(handle, symName);
    if (!target) {
        LOGE("dlsym(%s) failed: %s", symName, dlerror());
        dlclose(handle);
        env->ReleaseStringUTFChars(targetLib, libName);
        env->ReleaseStringUTFChars(targetSymbol, symName);
        return JNI_FALSE;
    }
    dlclose(handle);

    DobbyHookEntry* entry = nullptr;
    int ret = DobbyHook(target, replacement, &original);
    if (ret != RS_SUCCESS) {
        LOGE("DobbyHook(%s) failed: %d", key.c_str(), ret);
        env->ReleaseStringUTFChars(targetLib, libName);
        env->ReleaseStringUTFChars(targetSymbol, symName);
        return JNI_FALSE;
    }

    g_original_functions[key] = original;
    g_hook_entries[key] = entry;
    LOGD("Hooked: %s -> %p (orig: %p)", key.c_str(), replacement, original);

    if (outOriginalPtrArray != nullptr) {
        jlong orig = reinterpret_cast<jlong>(original);
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID ctor = env->GetMethodID(longClass, "<init>", "(J)V");
        env->SetObjectArrayElement(outOriginalPtrArray, 0, env->NewObject(longClass, ctor, orig));
    }

    env->ReleaseStringUTFChars(targetLib, libName);
    env->ReleaseStringUTFChars(targetSymbol, symName);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_DobbyWrapper_unhook(
    JNIEnv* env, jclass clazz,
    jstring targetLib, jstring targetSymbol) {

    const char* libName = env->GetStringUTFChars(targetLib, nullptr);
    const char* symName = env->GetStringUTFChars(targetSymbol, nullptr);
    std::string key = std::string(libName) + "!" + symName;

    std::lock_guard<std::mutex> lock(g_hook_mutex);
    auto it = g_hook_entries.find(key);
    if (it == g_hook_entries.end()) {
        env->ReleaseStringUTFChars(targetLib, libName);
        env->ReleaseStringUTFChars(targetSymbol, symName);
        return JNI_FALSE;
    }

    int ret = DobbyDestroy(it->second);
    if (ret == RS_SUCCESS) {
        g_hook_entries.erase(it);
        g_original_functions.erase(key);
        LOGD("Unhooked: %s", key.c_str());
    }

    env->ReleaseStringUTFChars(targetLib, libName);
    env->ReleaseStringUTFChars(targetSymbol, symName);
    return ret == RS_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_linyu_mcx19hook_util_DobbyWrapper_getOriginal(
    JNIEnv* env, jclass clazz,
    jstring targetLib, jstring targetSymbol) {

    const char* libName = env->GetStringUTFChars(targetLib, nullptr);
    const char* symName = env->GetStringUTFChars(targetSymbol, nullptr);
    std::string key = std::string(libName) + "!" + symName;

    std::lock_guard<std::mutex> lock(g_hook_mutex);
    auto it = g_original_functions.find(key);
    jlong result = it != g_original_functions.end() ? reinterpret_cast<jlong>(it->second) : 0;

    env->ReleaseStringUTFChars(targetLib, libName);
    env->ReleaseStringUTFChars(targetSymbol, symName);
    return result;
}

// ==================== 内存读写 ====================
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_linyu_mcx19hook_util_Memory_readMemory(
    JNIEnv* env, jclass clazz,
    jlong address, jint size) {

    if (address == 0 || size <= 0 || size > 1024 * 1024) {
        return env->NewByteArray(0);
    }

    jbyteArray result = env->NewByteArray(size);
    if (!result) return nullptr;

    if (msync(reinterpret_cast<void*>(address & ~0xFFF), 0x1000, MS_ASYNC) != 0) {
        return env->NewByteArray(0);
    }

    env->SetByteArrayRegion(result, 0, size, reinterpret_cast<jbyte*>(address));
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_Memory_writeMemory(
    JNIEnv* env, jclass clazz,
    jlong address, jbyteArray data) {

    if (address == 0 || !data) return JNI_FALSE;
    jsize size = env->GetArrayLength(data);
    if (size <= 0 || size > 1024 * 1024) return JNI_FALSE;

    void* pageStart = reinterpret_cast<void*>(address & ~0xFFF);
    if (mprotect(pageStart, 0x1000, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        return JNI_FALSE;
    }

    env->GetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(address));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_Memory_protectMemory(
    JNIEnv* env, jclass clazz,
    jlong address, jint size, jint prot) {

    if (address == 0 || size <= 0) return JNI_FALSE;
    void* pageStart = reinterpret_cast<void*>(address & ~0xFFF);
    size_t pageSize = ((size + 0xFFF) / 0x1000) * 0x1000;
    return mprotect(pageStart, pageSize, prot) == 0 ? JNI_TRUE : JNI_FALSE;
}