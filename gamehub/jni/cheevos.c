/*
 * RetroAchievements no motor: liga a biblioteca oficial (rcheevos, jni/rcheevos, licença MIT) ao jogo.
 *
 *  - Memória: o rc_libretro acha a memória certa de cada console pelo mapa do núcleo
 *    (RETRO_ENVIRONMENT_SET_MEMORY_MAPS) ou pelo retro_get_memory_data.
 *  - Internet: o motor não faz pedidos. Cada pedido vai para uma fila que o Java lê (raPoll), faz por
 *    HTTPS e devolve a resposta (raResponse) na thread GL.
 *  - Avisos: conquista desbloqueada, jogo reconhecido, jogo completo… também vão pela fila, como JSON.
 *
 * Tudo que mexe na biblioteca roda na thread GL (o Java manda por queueEvent), menos o raPoll, que só lê
 * a fila (com trava).
 */
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "libretro.h"
#include "rc_client.h"
#include "rc_libretro.h"
#include "cheevos.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "OnixRA", __VA_ARGS__)

static rc_client_t *client;
static uint32_t console_id;
static rc_libretro_memory_regions_t regions;
static bool regions_ready;
static struct retro_memory_descriptor *mmap_desc;
static struct retro_memory_map mmap_copy;
static bool has_mmap;
static double idle_at;

/* ---------------- Fila para o Java ---------------- */
typedef struct { char *s; size_t n, cap; } buf_t;
static buf_t outbox;
static pthread_mutex_t out_lock = PTHREAD_MUTEX_INITIALIZER;

static void b_add(buf_t *b, const char *s, size_t n) {
    if (b->n + n + 1 > b->cap) {
        size_t cap = (b->n + n + 1) * 2;
        char *p = realloc(b->s, cap);
        if (!p) return;
        b->s = p; b->cap = cap;
    }
    memcpy(b->s + b->n, s, n);
    b->n += n;
    b->s[b->n] = 0;
}
static void b_str(buf_t *b, const char *s) { b_add(b, s, strlen(s)); }
static void b_json(buf_t *b, const char *s) {   /* texto entre aspas, com escape */
    b_add(b, "\"", 1);
    for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
        char e[8];
        if (*p == '"' || *p == '\\') { e[0] = '\\'; e[1] = (char)*p; b_add(b, e, 2); }
        else if (*p < 0x20) { snprintf(e, sizeof(e), "\\u%04x", *p); b_add(b, e, 6); }
        else b_add(b, (const char *)p, 1);
    }
    b_add(b, "\"", 1);
}
static void b_num(buf_t *b, double v) { char t[32]; snprintf(t, sizeof(t), "%.10g", v); b_str(b, t); }
/* Cada item da fila é um objeto JSON numa linha. */
static void push(buf_t *item) {
    pthread_mutex_lock(&out_lock);
    b_add(&outbox, item->s ? item->s : "{}", item->n);
    b_add(&outbox, "\n", 1);
    pthread_mutex_unlock(&out_lock);
    free(item->s);
}

/* ---------------- Pedidos à internet ---------------- */
typedef struct { int id; rc_client_server_callback_t cb; void *data; } pending_t;
#define MAX_PENDING 64
static pending_t pending[MAX_PENDING];
static int next_id = 1;

static void server_call(const rc_api_request_t *req, rc_client_server_callback_t cb, void *data, rc_client_t *c) {
    int slot = -1;
    for (int i = 0; i < MAX_PENDING; i++) if (!pending[i].id) { slot = i; break; }
    if (slot < 0) {   /* fila cheia: responde erro na hora (a biblioteca tenta de novo depois) */
        rc_api_server_response_t r = { "fila cheia", 10, RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR };
        cb(&r, data);
        return;
    }
    pending[slot] = (pending_t){ next_id++, cb, data };
    buf_t b = {0};
    b_str(&b, "{\"t\":\"http\",\"id\":"); b_num(&b, pending[slot].id);
    b_str(&b, ",\"url\":"); b_json(&b, req->url);
    if (req->post_data) { b_str(&b, ",\"post\":"); b_json(&b, req->post_data); }
    if (req->content_type) { b_str(&b, ",\"type\":"); b_json(&b, req->content_type); }
    b_str(&b, "}");
    push(&b);
}

/* ---------------- Memória ---------------- */
extern void *(*cheevos_core_memory_data)(unsigned);
extern size_t (*cheevos_core_memory_size)(unsigned);

static void core_memory(uint32_t id, rc_libretro_core_memory_info_t *info) {
    info->data = cheevos_core_memory_data ? cheevos_core_memory_data(id) : NULL;
    info->size = info->data && cheevos_core_memory_size ? cheevos_core_memory_size(id) : 0;
}
static void regions_init(void) {
    if (regions_ready) rc_libretro_memory_destroy(&regions);
    regions_ready = rc_libretro_memory_init(&regions, has_mmap ? &mmap_copy : NULL, core_memory, console_id) != 0;
}
static uint32_t read_memory(uint32_t address, uint8_t *buffer, uint32_t n, rc_client_t *c) {
    if (!regions_ready || !regions.total_size) regions_init();   /* alguns núcleos só mostram a memória depois do 1º quadro */
    return regions_ready ? rc_libretro_memory_read(&regions, address, buffer, n) : 0;
}

void cheevos_set_mmap(const struct retro_memory_map *m) {
    free(mmap_desc); mmap_desc = NULL; has_mmap = false;
    if (!m || !m->num_descriptors || !m->descriptors) return;
    mmap_desc = malloc(sizeof(*mmap_desc) * m->num_descriptors);
    if (!mmap_desc) return;
    memcpy(mmap_desc, m->descriptors, sizeof(*mmap_desc) * m->num_descriptors);
    mmap_copy.descriptors = mmap_desc;
    mmap_copy.num_descriptors = m->num_descriptors;
    has_mmap = true;
}

/* ---------------- Avisos ---------------- */
static void ach_json(buf_t *b, const rc_client_achievement_t *a) {
    char url[256] = "";
    rc_client_achievement_get_image_url(a, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED, url, sizeof(url));
    b_str(b, "{\"id\":"); b_num(b, a->id);
    b_str(b, ",\"title\":"); b_json(b, a->title);
    b_str(b, ",\"desc\":"); b_json(b, a->description);
    b_str(b, ",\"pts\":"); b_num(b, a->points);
    b_str(b, ",\"rarity\":"); b_num(b, a->rarity);
    b_str(b, ",\"badge\":"); b_json(b, url);
    b_str(b, ",\"at\":"); b_num(b, a->unlocked ? (double)a->unlock_time : 0);
    b_str(b, ",\"progress\":"); b_json(b, a->measured_progress);
    b_str(b, "}");
}

/* O jogo e a lista de conquistas dele (para a aba Conquistas guardar e mostrar fora do jogo). */
static void push_game(void) {
    const rc_client_game_t *g = rc_client_get_game_info(client);
    if (!g || !g->id) return;
    char url[256] = "";
    rc_client_game_get_image_url(g, url, sizeof(url));
    rc_client_user_game_summary_t sum;
    memset(&sum, 0, sizeof(sum));
    rc_client_get_user_game_summary(client, &sum);
    buf_t b = {0};
    b_str(&b, "{\"t\":\"game\",\"id\":"); b_num(&b, g->id);
    b_str(&b, ",\"title\":"); b_json(&b, g->title);
    b_str(&b, ",\"badge\":"); b_json(&b, url);
    b_str(&b, ",\"total\":"); b_num(&b, sum.num_core_achievements);
    b_str(&b, ",\"got\":"); b_num(&b, sum.num_unlocked_achievements);
    b_str(&b, ",\"points\":"); b_num(&b, sum.points_core);
    b_str(&b, ",\"gotPoints\":"); b_num(&b, sum.points_unlocked);
    b_str(&b, ",\"list\":[");
    rc_client_achievement_list_t *list = rc_client_create_achievement_list(client, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
            RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
    bool first = true;
    for (uint32_t i = 0; list && i < list->num_buckets; i++)
        for (uint32_t j = 0; j < list->buckets[i].num_achievements; j++) {
            /* O aviso "Unknown Emulator" que a biblioteca põe na lista (o Cobalt ainda não foi validado) não é conquista. */
            if (list->buckets[i].achievements[j]->id == 101000001) continue;
            if (!first) b_str(&b, ",");
            first = false;
            ach_json(&b, list->buckets[i].achievements[j]);
        }
    if (list) rc_client_destroy_achievement_list(list);
    b_str(&b, "]}");
    push(&b);
}

static void user_json(buf_t *b, const rc_client_user_t *u);
static void event_handler(const rc_client_event_t *e, rc_client_t *c) {
    buf_t b = {0};
    switch (e->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED:
            b_str(&b, "{\"t\":\"unlock\",\"a\":"); ach_json(&b, e->achievement); b_str(&b, "}");
            push(&b);
            push_game();
            {
                const rc_client_user_t *u = rc_client_get_user_info(client);   /* a biblioteca já somou os pontos */
                if (u) { buf_t ub = {0}; user_json(&ub, u); push(&ub); }
            }
            break;
        case RC_CLIENT_EVENT_GAME_COMPLETED: {
            const rc_client_game_t *g = rc_client_get_game_info(client);
            char url[256] = "";
            if (g) rc_client_game_get_image_url(g, url, sizeof(url));
            b_str(&b, "{\"t\":\"complete\",\"title\":"); b_json(&b, g ? g->title : "");
            b_str(&b, ",\"badge\":"); b_json(&b, url); b_str(&b, "}");
            push(&b);
            break;
        }
        case RC_CLIENT_EVENT_RESET:
            /* Só acontece ao ligar o modo difícil com o jogo aberto (aqui ele fica desligado). */
            rc_client_reset(client);
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR:
            b_str(&b, "{\"t\":\"error\",\"msg\":"); b_json(&b, e->server_error ? e->server_error->error_message : "");
            b_str(&b, "}");
            push(&b);
            break;
        case RC_CLIENT_EVENT_DISCONNECTED: b_str(&b, "{\"t\":\"offline\"}"); push(&b); break;
        case RC_CLIENT_EVENT_RECONNECTED: b_str(&b, "{\"t\":\"online\"}"); push(&b); break;
        default: break;
    }
}

static void log_message(const char *msg, const rc_client_t *c) { LOGI("%s", msg); }

static void loaded_cb(int result, const char *error, rc_client_t *c, void *ud) {
    buf_t b = {0};
    if (result == RC_OK) {
        push_game();
        return;
    }
    /* RC_NO_GAME_LOADED: o RetroAchievements não conhece este arquivo (jogo sem conquistas ou outra versão). */
    b_str(&b, "{\"t\":\"nogame\",\"code\":"); b_num(&b, result);
    b_str(&b, ",\"msg\":"); b_json(&b, error); b_str(&b, "}");
    push(&b);
}
/* A conta (nome, pontos e foto): no login e a cada conquista, para o troféu do hub mostrar os pontos novos. */
static void user_json(buf_t *b, const rc_client_user_t *u) {
    b_str(b, "{\"t\":\"login\",\"user\":"); b_json(b, u->display_name);
    b_str(b, ",\"score\":"); b_num(b, u->score_softcore + u->score);
    b_str(b, ",\"avatar\":"); b_json(b, u->avatar_url); b_str(b, "}");
}
static void login_cb(int result, const char *error, rc_client_t *c, void *ud) {
    buf_t b = {0};
    const rc_client_user_t *u = result == RC_OK ? rc_client_get_user_info(client) : NULL;
    if (u) {
        user_json(&b, u);
    } else {
        /* RC_INVALID_CREDENTIALS / RC_EXPIRED_TOKEN: o Java pede para entrar de novo. */
        b_str(&b, "{\"t\":\"loginfail\",\"code\":"); b_num(&b, result);
        b_str(&b, ",\"msg\":"); b_json(&b, error); b_str(&b, "}");
    }
    push(&b);
}

/* ---------------- Ciclo do jogo (chamado pelo retro.c, na thread GL) ---------------- */
void cheevos_loaded(const char *path, const void *data, size_t size) {
    if (!client) return;
    regions_ready = false;
    regions_init();
    rc_client_begin_identify_and_load_game(client, console_id, path, data, size, loaded_cb, NULL);
}
void cheevos_frame(void) { if (client) rc_client_do_frame(client); }
void cheevos_idle(double now) {
    if (!client || now - idle_at < 1.0) return;
    idle_at = now;
    rc_client_idle(client);
}
void cheevos_reset(void) { if (client) rc_client_reset(client); }
void cheevos_state_loaded(void) { if (client) rc_client_deserialize_progress(client, NULL); }
void cheevos_unload(void) {
    if (client) { rc_client_destroy(client); client = NULL; }
    if (regions_ready) rc_libretro_memory_destroy(&regions);
    regions_ready = false;
    free(mmap_desc); mmap_desc = NULL; has_mmap = false;
    memset(pending, 0, sizeof(pending));
}
bool cheevos_active(void) { return client != NULL; }

/* ---------------- Java ---------------- */
#define JNI(ret, name) JNIEXPORT ret JNICALL Java_com_onix_gamehub_Native_##name

static char *jcopy(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    char *r = c ? strdup(c) : NULL;
    if (c) (*env)->ReleaseStringUTFChars(env, s, c);
    return r;
}

/** Antes do load(): liga o RetroAchievements para este jogo (conta já conectada: usuário + token). */
JNI(void, raStart)(JNIEnv *env, jclass c, jstring juser, jstring jtoken, jint console) {
    cheevos_unload();
    char *user = jcopy(env, juser), *token = jcopy(env, jtoken);
    console_id = (uint32_t)console;
    client = rc_client_create(read_memory, server_call);
    if (client) {
        rc_client_enable_logging(client, RC_CLIENT_LOG_LEVEL_WARN, log_message);
        rc_client_set_event_handler(client, event_handler);
        /* Softcore: enquanto o Cobalt não for validado pelo RetroAchievements, o servidor conta tudo como
           softcore de qualquer jeito; assim os estados salvos, o voltar no tempo e os códigos continuam valendo. */
        rc_client_set_hardcore_enabled(client, 0);
        rc_client_begin_login_with_token(client, user, token, login_cb, NULL);
    }
    free(user); free(token);
}

/** O "rcheevos/x.y" que vai no fim do User-Agent dos pedidos. */
JNI(jstring, raAgent)(JNIEnv *env, jclass c) {
    char buf[64] = "";
    rc_client_t *tmp = client ? client : rc_client_create(read_memory, server_call);
    if (tmp) rc_client_get_user_agent_clause(tmp, buf, sizeof(buf));
    if (tmp && tmp != client) rc_client_destroy(tmp);
    return (*env)->NewStringUTF(env, buf);
}

/** Tudo o que está na fila (uma linha JSON por item), ou null. Pode ser chamado de qualquer thread. */
JNI(jstring, raPoll)(JNIEnv *env, jclass c) {
    pthread_mutex_lock(&out_lock);
    jstring r = outbox.n ? (*env)->NewStringUTF(env, outbox.s) : NULL;
    outbox.n = 0;
    if (outbox.s) outbox.s[0] = 0;
    pthread_mutex_unlock(&out_lock);
    return r;
}

/** A resposta de um pedido (na thread GL). status 0 = sem internet. */
JNI(void, raResponse)(JNIEnv *env, jclass c, jint id, jint status, jbyteArray jbody) {
    for (int i = 0; i < MAX_PENDING; i++) {
        if (pending[i].id != id) continue;
        pending_t p = pending[i];
        pending[i].id = 0;
        jsize n = jbody ? (*env)->GetArrayLength(env, jbody) : 0;
        char *body = malloc((size_t)n + 1);
        if (!body) return;
        if (n) (*env)->GetByteArrayRegion(env, jbody, 0, n, (jbyte *)body);
        body[n] = 0;
        rc_api_server_response_t r = { body, (size_t)n, status > 0 ? status : RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR };
        p.cb(&r, p.data);
        free(body);
        return;
    }
}
