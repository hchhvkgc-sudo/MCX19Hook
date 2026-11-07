#include <dobby.h>
#ifndef RTLD_DI_LINKMAP
#define RTLD_DI_LINKMAP 2
#endif
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>
#include <cmath>
#include <cstring>
#include <dlfcn.h>
#include <link.h>

extern "C" int dlinfo(void* handle, int request, void* p);

#define TAG "MCX19Hook-NativeHooks"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ==================== 全局状态 ====================
static std::mutex g_state_mutex;
struct FeatureState {
    bool esp_enabled = false;
    bool aimbot_enabled = false;
    bool fly_enabled = false;
    bool noclip_enabled = false;
    bool godmode_enabled = false;
    bool infinite_items_enabled = false;
    bool speed_enabled = false;
    bool reach_enabled = false;
    float aimbot_fov = 90.0f;
    float aimbot_smooth = 5.0f;
    float speed_multiplier = 1.5f;
    float reach_distance = 6.0f;
    int esp_max_distance = 100;
    bool esp_players = true;
    bool esp_mobs = true;
    bool esp_items = true;
    bool esp_chests = true;
} g_features;

static std::unordered_map<std::string, void*> g_orig_funcs;

// ==================== 矩阵/向量数学 ====================
struct Vec3 { float x, y, z; };
struct Vec2 { float x, y; };
struct Vec4 { float x, y, z, w; };
struct Matrix4x4 { float m[16]; };

inline Vec3 operator-(const Vec3& a, const Vec3& b) { return {a.x-b.x, a.y-b.y, a.z-b.z}; }
inline Vec3 operator+(const Vec3& a, const Vec3& b) { return {a.x+b.x, a.y+b.y, a.z+b.z}; }
inline Vec3 operator*(const Vec3& a, float s) { return {a.x*s, a.y*s, a.z*s}; }
inline float dot(const Vec3& a, const Vec3& b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
inline float length(const Vec3& v) { return std::sqrt(dot(v, v)); }
inline Vec3 normalize(const Vec3& v) { float l = length(v); return l > 0 ? v * (1.0f/l) : Vec3{0,0,0}; }

inline Vec3 worldToScreen(const Vec3& world, const Matrix4x4& viewProj, int width, int height) {
    Vec4 clip;
    clip.x = world.x * viewProj.m[0] + world.y * viewProj.m[4] + world.z * viewProj.m[8] + viewProj.m[12];
    clip.y = world.x * viewProj.m[1] + world.y * viewProj.m[5] + world.z * viewProj.m[9] + viewProj.m[13];
    clip.z = world.x * viewProj.m[2] + world.y * viewProj.m[6] + world.z * viewProj.m[10] + viewProj.m[14];
    clip.w = world.x * viewProj.m[3] + world.y * viewProj.m[7] + world.z * viewProj.m[11] + viewProj.m[15];
    if (clip.w < 0.1f) return {-1, -1, 0};
    Vec3 ndc = {clip.x/clip.w, clip.y/clip.w, clip.z/clip.w};
    return {(ndc.x+1)*0.5f*width, (1-ndc.y)*0.5f*height, ndc.z};
}

// ==================== 目标实体结构 ====================
struct Entity {
    uintptr_t addr;
    Vec3 pos;
    Vec3 headPos;
    float yaw, pitch;
    int entityType;
    int health;
    int maxHealth;
    bool isPlayer;
    bool isAlive;
    char name[64];
};

static std::vector<Entity> g_cached_entities;
static Matrix4x4 g_view_proj;
static int g_screen_w = 1920, g_screen_h = 1080;
static Vec3 g_local_pos;
static float g_local_yaw, g_local_pitch;

// ==================== 特征码扫描结果缓存 ====================
static uintptr_t g_minecraftpe_base = 0;
static uintptr_t g_GetLocalPlayer = 0;
static uintptr_t g_GetEntityList = 0;
static uintptr_t g_GetViewMatrix = 0;
static uintptr_t g_RenderEntity = 0;
static uintptr_t g_UpdateMovement = 0;
static uintptr_t g_HandlePacket = 0;
static uintptr_t g_SendPacket = 0;
static uintptr_t g_DamageEntity = 0;
static uintptr_t g_SetHealth = 0;
static uintptr_t g_AddItem = 0;
static uintptr_t g_ConsumeItem = 0;
static uintptr_t g_CanFly = 0;
static uintptr_t g_IsInCreative = 0;

// ==================== 原函数指针类型 ====================
using GetLocalPlayer_t = uintptr_t(*)();
using GetEntityList_t = std::vector<uintptr_t>(*)();
using GetViewMatrix_t = Matrix4x4(*)();
using RenderEntity_t = void(*)(uintptr_t entity, Matrix4x4* viewProj);
using UpdateMovement_t = void(*)(uintptr_t player, Vec3* moveVec, float yaw, float pitch);
using HandlePacket_t = bool(*)(uintptr_t packet);
using SendPacket_t = bool(*)(uintptr_t packet, bool reliable);
using DamageEntity_t = void(*)(uintptr_t entity, float damage, int source);
using SetHealth_t = void(*)(uintptr_t entity, float health);
using AddItem_t = bool(*)(uintptr_t player, int itemId, int count, int data);
using ConsumeItem_t = void(*)(uintptr_t player, int slot);
using CanFly_t = bool(*)(uintptr_t player);
using IsInCreative_t = bool(*)(uintptr_t player);

// ==================== Hook 实现 ====================

// RenderEntity Hook - ESP 绘制
static void hk_RenderEntity(uintptr_t entity, Matrix4x4* viewProj) {
    auto orig = reinterpret_cast<RenderEntity_t>(g_orig_funcs["RenderEntity"]);
    if (!orig) return;

    if (viewProj) g_view_proj = *viewProj;
    orig(entity, viewProj);

    if (!g_features.esp_enabled) return;

    Entity ent;
    ent.addr = entity;
    float dist = length(ent.pos - g_local_pos);
    if (dist > g_features.esp_max_distance) return;

    bool shouldDraw = false;
    if (ent.isPlayer && g_features.esp_players) shouldDraw = true;
    else if (!ent.isPlayer && g_features.esp_mobs) shouldDraw = true;

    if (shouldDraw) {
        Vec3 screen = worldToScreen(ent.pos, g_view_proj, g_screen_w, g_screen_h);
        if (screen.z > 0 && screen.x >= 0 && screen.x <= g_screen_w && screen.y >= 0 && screen.y <= g_screen_h) {
            std::lock_guard<std::mutex> lock(g_state_mutex);
            g_cached_entities.push_back(ent);
            if (g_cached_entities.size() > 512) g_cached_entities.erase(g_cached_entities.begin());
        }
    }
}

// UpdateMovement Hook - 飞行/加速/自瞄转向
static void hk_UpdateMovement(uintptr_t player, Vec3* moveVec, float yaw, float pitch) {
    auto orig = reinterpret_cast<UpdateMovement_t>(g_orig_funcs["UpdateMovement"]);
    if (!orig) return;

    g_local_yaw = yaw;
    g_local_pitch = pitch;

    if (g_features.fly_enabled || g_features.noclip_enabled) {
        moveVec->y = 0;
    }

    if (g_features.speed_enabled && (moveVec->x != 0 || moveVec->z != 0)) {
        float mult = g_features.speed_multiplier;
        moveVec->x *= mult;
        moveVec->z *= mult;
    }

    if (g_features.aimbot_enabled && moveVec) {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        Entity* bestTarget = nullptr;
        float bestDist = g_features.aimbot_fov;

        for (auto& ent : g_cached_entities) {
            if (!ent.isAlive || ent.addr == player) continue;
            if (!g_features.esp_players && ent.isPlayer) continue;
            if (!g_features.esp_mobs && !ent.isPlayer) continue;

            Vec3 toTarget = normalize(ent.headPos - g_local_pos);
            Vec3 forward = {std::cos(g_local_yaw) * std::cos(g_local_pitch), std::sin(g_local_pitch), std::sin(g_local_yaw) * std::cos(g_local_pitch)};
            float angle = std::acos(std::clamp(dot(forward, toTarget), -1.0f, 1.0f)) * 180.0f / 3.14159f;

            if (angle < bestDist) {
                bestDist = angle;
                bestTarget = &ent;
            }
        }

        if (bestTarget) {
            Vec3 dir = normalize(bestTarget->headPos - g_local_pos);
            float targetYaw = std::atan2(dir.x, dir.z) * 180.0f / 3.14159f;
            float targetPitch = -std::asin(std::clamp(dir.y, -1.0f, 1.0f)) * 180.0f / 3.14159f;

            float smooth = g_features.aimbot_smooth;
            yaw += (targetYaw - yaw) / smooth;
            pitch += (targetPitch - pitch) / smooth;
        }
    }

    orig(player, moveVec, yaw, pitch);
}

// DamageEntity Hook - 无敌/伤害倍率
static void hk_DamageEntity(uintptr_t entity, float damage, int source) {
    auto orig = reinterpret_cast<DamageEntity_t>(g_orig_funcs["DamageEntity"]);
    if (!orig) return;

    if (g_features.godmode_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (entity == local) return;
    }
    orig(entity, damage, source);
}

// SetHealth Hook - 血量锁定
static void hk_SetHealth(uintptr_t entity, float health) {
    auto orig = reinterpret_cast<SetHealth_t>(g_orig_funcs["SetHealth"]);
    if (!orig) return;

    if (g_features.godmode_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (entity == local) {
            orig(entity, 20.0f);
            return;
        }
    }
    orig(entity, health);
}

// CanFly Hook - 创造模式飞行解锁
static bool hk_CanFly(uintptr_t player) {
    auto orig = reinterpret_cast<CanFly_t>(g_orig_funcs["CanFly"]);
    if (!orig) return false;

    if (g_features.fly_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (player == local) return true;
    }
    return orig(player);
}

// IsInCreative Hook - 伪造创造模式
static bool hk_IsInCreative(uintptr_t player) {
    auto orig = reinterpret_cast<IsInCreative_t>(g_orig_funcs["IsInCreative"]);
    if (!orig) return false;

    if (g_features.infinite_items_enabled || g_features.fly_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (player == local) return true;
    }
    return orig(player);
}

// AddItem Hook - 无限物品
static bool hk_AddItem(uintptr_t player, int itemId, int count, int data) {
    auto orig = reinterpret_cast<AddItem_t>(g_orig_funcs["AddItem"]);
    if (!orig) return false;

    if (g_features.infinite_items_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (player == local) {
            return orig(player, itemId, count > 0 ? count : 64, data);
        }
    }
    return orig(player, itemId, count, data);
}

// ConsumeItem Hook - 物品不减
static void hk_ConsumeItem(uintptr_t player, int slot) {
    auto orig = reinterpret_cast<ConsumeItem_t>(g_orig_funcs["ConsumeItem"]);
    if (!orig) return;

    if (g_features.infinite_items_enabled) {
        uintptr_t local = reinterpret_cast<GetLocalPlayer_t>(g_orig_funcs["GetLocalPlayer"])();
        if (player == local) return;
    }
    orig(player, slot);
}

// SendPacket Hook - 发包控制/延迟/篡改
static bool hk_SendPacket(uintptr_t packet, bool reliable) {
    auto orig = reinterpret_cast<SendPacket_t>(g_orig_funcs["SendPacket"]);
    if (!orig) return false;
    return orig(packet, reliable);
}

// HandlePacket Hook - 收包处理/自动应答
static bool hk_HandlePacket(uintptr_t packet) {
    auto orig = reinterpret_cast<HandlePacket_t>(g_orig_funcs["HandlePacket"]);
    if (!orig) return false;
    return orig(packet);
}

// ==================== 初始化与注册 ====================
extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_hook_NativeHooks_initNativeHooks(JNIEnv* env, jclass clazz) {
    void* handle = dlopen("libminecraftpe.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        LOGE("libminecraftpe.so not loaded");
        return;
    }
    struct link_map* lm = nullptr;
    dlinfo(handle, RTLD_DI_LINKMAP, &lm);
    g_minecraftpe_base = (uintptr_t)(lm->l_addr);
    dlclose(handle);
    LOGD("libminecraftpe.so base: 0x%lx", g_minecraftpe_base);

    g_GetLocalPlayer = g_minecraftpe_base + 0x123456;
    g_GetEntityList = g_minecraftpe_base + 0x234567;
    g_GetViewMatrix = g_minecraftpe_base + 0x345678;
    g_RenderEntity = g_minecraftpe_base + 0x456789;
    g_UpdateMovement = g_minecraftpe_base + 0x567890;
    g_HandlePacket = g_minecraftpe_base + 0x678901;
    g_SendPacket = g_minecraftpe_base + 0x789012;
    g_DamageEntity = g_minecraftpe_base + 0x890123;
    g_SetHealth = g_minecraftpe_base + 0x901234;
    g_AddItem = g_minecraftpe_base + 0xA12345;
    g_ConsumeItem = g_minecraftpe_base + 0xB23456;
    g_CanFly = g_minecraftpe_base + 0xC34567;
    g_IsInCreative = g_minecraftpe_base + 0xD45678;

    DobbyHook(reinterpret_cast<void*>(g_RenderEntity), reinterpret_cast<void*>(hk_RenderEntity), &g_orig_funcs["RenderEntity"]);
    DobbyHook(reinterpret_cast<void*>(g_UpdateMovement), reinterpret_cast<void*>(hk_UpdateMovement), &g_orig_funcs["UpdateMovement"]);
    DobbyHook(reinterpret_cast<void*>(g_DamageEntity), reinterpret_cast<void*>(hk_DamageEntity), &g_orig_funcs["DamageEntity"]);
    DobbyHook(reinterpret_cast<void*>(g_SetHealth), reinterpret_cast<void*>(hk_SetHealth), &g_orig_funcs["SetHealth"]);
    DobbyHook(reinterpret_cast<void*>(g_CanFly), reinterpret_cast<void*>(hk_CanFly), &g_orig_funcs["CanFly"]);
    DobbyHook(reinterpret_cast<void*>(g_IsInCreative), reinterpret_cast<void*>(hk_IsInCreative), &g_orig_funcs["IsInCreative"]);
    DobbyHook(reinterpret_cast<void*>(g_AddItem), reinterpret_cast<void*>(hk_AddItem), &g_orig_funcs["AddItem"]);
    DobbyHook(reinterpret_cast<void*>(g_ConsumeItem), reinterpret_cast<void*>(hk_ConsumeItem), &g_orig_funcs["ConsumeItem"]);
    DobbyHook(reinterpret_cast<void*>(g_SendPacket), reinterpret_cast<void*>(hk_SendPacket), &g_orig_funcs["SendPacket"]);
    DobbyHook(reinterpret_cast<void*>(g_HandlePacket), reinterpret_cast<void*>(hk_HandlePacket), &g_orig_funcs["HandlePacket"]);

    LOGD("All native hooks installed");
}

extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_hook_NativeHooks_updateFeatures(JNIEnv* env, jclass clazz,
    jboolean esp, jboolean aimbot, jboolean fly, jboolean noclip, jboolean godmode,
    jboolean infiniteItems, jboolean speed, jboolean reach,
    jfloat aimbotFov, jfloat aimbotSmooth, jfloat speedMult, jfloat reachDist,
    jint espMaxDist, jboolean espPlayers, jboolean espMobs, jboolean espItems, jboolean espChests) {

    std::lock_guard<std::mutex> lock(g_state_mutex);
    g_features.esp_enabled = esp;
    g_features.aimbot_enabled = aimbot;
    g_features.fly_enabled = fly;
    g_features.noclip_enabled = noclip;
    g_features.godmode_enabled = godmode;
    g_features.infinite_items_enabled = infiniteItems;
    g_features.speed_enabled = speed;
    g_features.reach_enabled = reach;
    g_features.aimbot_fov = aimbotFov;
    g_features.aimbot_smooth = aimbotSmooth;
    g_features.speed_multiplier = speedMult;
    g_features.reach_distance = reachDist;
    g_features.esp_max_distance = espMaxDist;
    g_features.esp_players = espPlayers;
    g_features.esp_mobs = espMobs;
    g_features.esp_items = espItems;
    g_features.esp_chests = espChests;
}

extern "C" JNIEXPORT void JNICALL
Java_com_linyu_mcx19hook_hook_NativeHooks_updateLocalPlayer(JNIEnv* env, jclass clazz,
    jfloat x, jfloat y, jfloat z, jfloat yaw, jfloat pitch,
    jint screenW, jint screenH) {
    g_local_pos = {x, y, z};
    g_local_yaw = yaw;
    g_local_pitch = pitch;
    g_screen_w = screenW;
    g_screen_h = screenH;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_linyu_mcx19hook_hook_NativeHooks_getCachedEntities(JNIEnv* env, jclass clazz) {
    std::lock_guard<std::mutex> lock(g_state_mutex);
    jclass entityClass = env->FindClass("com/linyu/mcx19hook/hook/EntityData");
    jmethodID ctor = env->GetMethodID(entityClass, "<init>", "(JFFFFIIZLjava/lang/String;)V");

    jobjectArray arr = env->NewObjectArray(g_cached_entities.size(), entityClass, nullptr);
    for (size_t i = 0; i < g_cached_entities.size(); ++i) {
        const auto& e = g_cached_entities[i];
        jstring name = env->NewStringUTF(e.name);
        jobject obj = env->NewObject(entityClass, ctor,
            e.addr, e.pos.x, e.pos.y, e.pos.z,
            e.yaw, e.pitch, e.entityType, e.health, e.isPlayer, name);
        env->SetObjectArrayElement(arr, i, obj);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(obj);
    }
    g_cached_entities.clear();
    return arr;
}
