/* RetroAchievements no motor (cheevos.c): o que o retro.c chama, sempre na thread GL. */
#ifndef ONIX_CHEEVOS_H
#define ONIX_CHEEVOS_H
#include <stdbool.h>
#include <stddef.h>
#include "libretro.h"

void cheevos_set_mmap(const struct retro_memory_map *m);   /* RETRO_ENVIRONMENT_SET_MEMORY_MAPS */
void cheevos_loaded(const char *path, const void *data, size_t size);   /* o núcleo aceitou o jogo */
void cheevos_frame(void);           /* depois de cada quadro de verdade (também no avanço rápido) */
void cheevos_idle(double now);      /* pausado: até uma vez por segundo */
void cheevos_reset(void);
void cheevos_state_loaded(void);    /* carregou um estado salvo */
void cheevos_unload(void);
bool cheevos_active(void);
#endif
