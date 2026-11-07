/*
 * Minimal Dobby header for MCX19Hook
 * Based on Dobby v2024.05.17
 */
#ifndef DOBBY_H
#define DOBBY_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    RS_SUCCESS = 0,
    RS_FAILED = -1,
    RS_UNALIGNED = -2,
    RS_NOT_SUPPORTED = -3,
    RS_INVALID_ARGUMENT = -4,
    RS_OUT_OF_MEMORY = -5,
} DobbyStatus;

typedef struct _DobbyHookEntry DobbyHookEntry;

int DobbyHook(void *target, void *replacement, void **original);
int DobbyHookEx(void *target, void *replacement, void **original, void *arg);
int DobbyDestroy(DobbyHookEntry *entry);
int DobbyUnHook(void *target);
void *DobbyGetHookEntry(void *target);

void dobby_enable_near_branch_trampoline();
void dobby_disable_near_branch_trampoline();

#ifdef __cplusplus
}
#endif

#endif // DOBBY_H
