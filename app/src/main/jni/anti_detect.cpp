#include <jni.h>
#include <android/log.h>
#include <sys/ptrace.h>
#include <sys/prctl.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <vector>
#include <link.h>
#include <dlfcn.h>
#include <sys/system_properties.h>

#ifndef RTLD_DI_LINKMAP
#define RTLD_DI_LINKMAP 2
#endif
#include <dirent.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

extern "C" int dlinfo(void* handle, int request, void* p);

#define TAG "MCX19Hook-AntiDetect"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static pthread_t g_monitor_thread = 0;
static volatile bool g_monitor_running = false;

// ==================== 反调试 ====================
static void anti_ptrace() {
    // PTRACE_TRACEME 只能在未被调试时成功
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        // 已被调试，尝试分离
        ptrace(PTRACE_DETACH, 0, 0, 0);
    }
}

static void anti_prctl() {
    // 禁止 ptrace attach
    prctl(PR_SET_DUMPABLE, 0);
}

// 隐藏线程名
static void hide_thread_name(const char* fake_name) {
    pthread_setname_np(pthread_self(), fake_name);
}

// ==================== 反注入检测 ====================
static bool check_suspicious_libs() {
    const char* suspicious[] = {
        "frida", "xposed", "substrate", "dobby", "epic", "whale",
        "hook", "inject", "debug", "trace", "monitor"
    };
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return false;
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        for (const char* s : suspicious) {
            if (strcasestr(line, s)) {
                fclose(fp);
                return true;
            }
        }
    }
    fclose(fp);
    return false;
}

static bool check_frida_port() {
    // Frida 默认监听 27042
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return false;
    struct sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    int ret = connect(sock, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr));
    close(sock);
    return ret == 0;
}

// ==================== 反完整性校验 ====================
static uint32_t crc32(const uint8_t* data, size_t len) {
    uint32_t crc = 0xFFFFFFFF;
    static uint32_t table[256] = {0};
    static bool init = false;
    if (!init) {
        for (uint32_t i = 0; i < 256; ++i) {
            uint32_t c = i;
            for (int j = 0; j < 8; ++j) c = (c & 1) ? 0xEDB88320 ^ (c >> 1) : c >> 1;
            table[i] = c;
        }
        init = true;
    }
    for (size_t i = 0; i < len; ++i) crc = table[(crc ^ data[i]) & 0xFF] ^ (crc >> 8);
    return ~crc;
}

static void* get_self_base() {
    struct link_map* lm = nullptr;
    dlinfo(dlopen(nullptr, RTLD_NOW), RTLD_DI_LINKMAP, &lm);
    return lm ? (void*)(lm->l_addr) : nullptr;
}

static bool verify_self_integrity() {
    void* base = get_self_base();
    if (!base) return true;
    // 读取 ELF 头获取代码段范围
    // 简化：仅检查 .text 段前 4KB
    uint32_t expected = 0xDEADBEEF; // 构建时注入的真实 CRC
    uint32_t actual = crc32(static_cast<const uint8_t*>(base), 4096);
    return actual == expected;
}

// ==================== 隐藏模块 ====================
static void hide_from_maps() {
    // 将 libmcx19hook.so 从 /proc/self/maps 中隐藏
    // 方法：unlink 后重新映射，或使用 memfd_create
    // 这里仅演示思路，实际需配合 Java 端在加载前处理
}

static void fake_build_props() {
    // 伪造 ro.build.tags 等属性
    __system_property_add("ro.build.tags", strlen("ro.build.tags"), "release-keys", strlen("release-keys"));
    __system_property_add("ro.debuggable", strlen("ro.debuggable"), "0", strlen("0"));
    __system_property_add("ro.secure", strlen("ro.secure"), "1", strlen("1"));
}

// ==================== 监控线程 ====================
static void* monitor_loop(void* arg) {
    g_monitor_running = true;
    while (g_monitor_running) {
        // 1. 检查可疑库
        if (check_suspicious_libs()) {
            LOGE("Suspicious library detected!");
            // 可选择：自毁、上报、假死、静默禁用功能
        }
        // 2. 检查 Frida 端口
        if (check_frida_port()) {
            LOGE("Frida server detected!");
        }
        // 3. 检查完整性
        if (!verify_self_integrity()) {
            LOGE("Self integrity check failed!");
        }
        // 4. 检查调试器
        if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
            LOGE("Debugger attached!");
        }
        sleep(5);
    }
    return nullptr;
}

// ==================== JNI 接口 ====================
extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_util_AntiDetect_startProtection(JNIEnv* env, jclass clazz) {
    anti_ptrace();
    anti_prctl();
    hide_thread_name("MCX19Hook.Worker");
    fake_build_props();

    if (g_monitor_thread == 0) {
        pthread_create(&g_monitor_thread, nullptr, monitor_loop, nullptr);
        pthread_detach(g_monitor_thread);
    }
    LOGD("Anti-detect protection started");
}

extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_util_AntiDetect_stopProtection(JNIEnv* env, jclass clazz) {
    g_monitor_running = false;
    if (g_monitor_thread) {
        pthread_join(g_monitor_thread, nullptr);
        g_monitor_thread = 0;
    }
    LOGD("Anti-detect protection stopped");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_linyu_mcx19hook_util_AntiDetect_checkEnvironment(JNIEnv* env, jclass clazz) {
    bool safe = true;
    if (check_suspicious_libs()) { LOGD("Env: suspicious libs"); safe = false; }
    if (check_frida_port()) { LOGD("Env: frida port"); safe = false; }
    if (!verify_self_integrity()) { LOGD("Env: integrity fail"); safe = false; }
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) { LOGD("Env: debugger"); safe = false; }
    return safe ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_util_AntiDetect_hideModule(JNIEnv* env, jclass clazz, jstring libName) {
    // 由 Java 端在 System.loadLibrary 前调用
    // 实现：memfd_create + fexecve 或 dlopen 隐藏路径
    const char* name = env->GetStringUTFChars(libName, nullptr);
    LOGD("Hide module requested: %s", name);
    env->ReleaseStringUTFChars(libName, name);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linyu_mcx19hook_util_AntiDetect_getFakeProps(JNIEnv* env, jclass clazz) {
    // 返回伪造的 build.prop 片段供 Java 端替换
    return env->NewStringUTF("ro.build.tags=release-keys\nro.debuggable=0\nro.secure=1\n");
}