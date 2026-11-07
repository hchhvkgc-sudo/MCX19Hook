#include <dobby.h>
#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <unordered_map>
#include <dlfcn.h>
#include <sys/mman.h>
#include <cstring>
#include <cstdint>

#define TAG "MCX19Hook-DobbyImpl"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ==================== Minimal Dobby Implementation ====================
struct _DobbyHookEntry {
    void* target;
    void* replacement;
    void* original;
    uint8_t backup[16];
    size_t backup_size;
};

typedef struct _DobbyHookEntry DobbyHookEntry;

static std::mutex g_hook_mutex;
static std::unordered_map<void*, DobbyHookEntry*> g_hooks_by_target;

// 简化的指令长度计算 (ARM64 固定 4 字节)
static size_t get_instruction_size(void* addr) {
    return 4;
}

extern "C" {

int DobbyHook(void* target, void* replacement, void** original) {
    if (!target || !replacement) return RS_INVALID_ARGUMENT;
    
    std::lock_guard<std::mutex> lock(g_hook_mutex);
    
    if (g_hooks_by_target.find(target) != g_hooks_by_target.end()) {
        if (original) *original = g_hooks_by_target[target]->original;
        return RS_SUCCESS;
    }
    
    size_t backup_size = 16;
    size_t instr_size = 4; // ARM64 fixed 4 bytes
    size_t num_instrs = (backup_size + instr_size - 1) / instr_size;
    size_t real_backup_size = num_instrs * instr_size;
    
    void* page_start = (void*)((uintptr_t)target & ~0xFFF);
    if (mprotect(page_start, 0x1000, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        return RS_FAILED;
    }
    
    DobbyHookEntry* entry = new DobbyHookEntry();
    entry->target = target;
    entry->replacement = replacement;
    entry->backup_size = real_backup_size;
    std::memcpy(entry->backup, target, real_backup_size);
    entry->original = (uint8_t*)target + real_backup_size;
    
    // ARM64 B 指令: 0x14000000 | (offset & 0x3FFFFFF)
    int64_t offset = (int64_t)replacement - (int64_t)target;
    offset >>= 2;
    if (offset > 0x1FFFFFF || offset < -0x2000000) {
        delete entry;
        return RS_FAILED;
    }
    
    uint32_t branch_instr = 0x14000000 | (offset & 0x3FFFFFF);
    std::memcpy(target, &branch_instr, 4);
    
    for (size_t i = 4; i < real_backup_size; i += 4) {
        uint32_t nop = 0xD503201F; // ARM64 NOP
        std::memcpy((uint8_t*)target + i, &nop, 4);
    }
    
    g_hooks_by_target[target] = entry;
    
    if (original) *original = entry->original;
    
    return RS_SUCCESS;
}

int DobbyHookEx(void* target, void* replacement, void** original, void* arg) {
    (void)arg;
    return DobbyHook(target, replacement, original);
}

int DobbyDestroy(DobbyHookEntry* entry) {
    if (!entry) return RS_INVALID_ARGUMENT;
    
    std::lock_guard<std::mutex> lock(g_hook_mutex);
    
    if (entry->target && entry->backup_size > 0) {
        void* page_start = (void*)((uintptr_t)entry->target & ~0xFFF);
        if (mprotect(page_start, 0x1000, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
            std::memcpy(entry->target, entry->backup, entry->backup_size);
        }
    }
    
    g_hooks_by_target.erase(entry->target);
    delete entry;
    return RS_SUCCESS;
}

int DobbyUnHook(void* target) {
    std::lock_guard<std::mutex> lock(g_hook_mutex);
    auto it = g_hooks_by_target.find(target);
    if (it != g_hooks_by_target.end()) {
        int ret = DobbyDestroy(it->second);
        return ret;
    }
    return RS_FAILED;
}

void* DobbyGetHookEntry(void* target) {
    std::lock_guard<std::mutex> lock(g_hook_mutex);
    auto it = g_hooks_by_target.find(target);
    return it != g_hooks_by_target.end() ? it->second : nullptr;
}

void dobby_enable_near_branch_trampoline() {}
void dobby_disable_near_branch_trampoline() {}

} // extern "C"