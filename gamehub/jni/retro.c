/*
 * Frontend libretro do Onix Game Hub: carrega um núcleo (.so), roda os quadros na thread do
 * GLSurfaceView, desenha com OpenGL ES 2, toca o som pelo OpenSL ES e recebe os botões do Java.
 *
 * Threads:
 *  - GL (GLSurfaceView): tudo que fala com o núcleo (carregar, rodar, estados, opções, desenhar).
 *    O Java manda os comandos por queueEvent, então nada aqui precisa de trava para o núcleo.
 *  - Áudio (callback do OpenSL): só lê o anel de amostras (uma escrita, uma leitura, sem trava).
 *  - UI do Java: só os botões (atômicos).
 *
 * Ritmo: um quadro por vsync quando a tela e o jogo têm quase o mesmo ritmo (60 Hz x 60,09 do
 * SNES); a diferença é absorvida pelo controle dinâmico da taxa do som (o mesmo do RetroArch),
 * que estica ou encolhe o som em até 0,5% para o anel ficar sempre no meio. Se o ritmo for muito
 * diferente (TV em 50 Hz), os quadros seguem o relógio.
 */
#define _POSIX_C_SOURCE 200809L   /* strdup, strndup, strtok_r com -std=c11 */
#include <jni.h>
#include <dlfcn.h>
#include <errno.h>
#include <math.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <zlib.h>
#include <GLES2/gl2.h>
#ifndef ONIX_HOST_TEST
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#endif
#include <android/log.h>

#include "libretro.h"

#define TAG "OnixRetro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ---------------- Núcleo ---------------- */

static struct {
    void *handle;
    void (*set_environment)(retro_environment_t);
    void (*set_video_refresh)(retro_video_refresh_t);
    void (*set_audio_sample)(retro_audio_sample_t);
    void (*set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*set_input_poll)(retro_input_poll_t);
    void (*set_input_state)(retro_input_state_t);
    void (*init)(void);
    void (*deinit)(void);
    void (*get_system_info)(struct retro_system_info *);
    void (*get_system_av_info)(struct retro_system_av_info *);
    void (*set_controller_port_device)(unsigned, unsigned);
    void (*reset)(void);
    void (*run)(void);
    size_t (*serialize_size)(void);
    bool (*serialize)(void *, size_t);
    bool (*unserialize)(const void *, size_t);
    void (*cheat_reset)(void);
    void (*cheat_set)(unsigned, bool, const char *);
    bool (*load_game)(const struct retro_game_info *);
    void (*unload_game)(void);
    void *(*get_memory_data)(unsigned);
    size_t (*get_memory_size)(unsigned);
} core;

static bool loaded, game_loaded, probing;
static char system_dir[512], save_dir[512];
static unsigned language = RETRO_LANGUAGE_PORTUGUESE_BRAZIL;
static void *rom_data;
static size_t rom_size;
static struct retro_system_av_info av;
static enum retro_pixel_format pixfmt = RETRO_PIXEL_FORMAT_0RGB1555;

/* ---------------- Opções do núcleo ----------------
   Guardamos a definição (para a tela de configurações) e o valor atual de cada uma. */

#define MAX_OPTS 256
#define MAX_VALS 128
typedef struct {
    char *key, *desc, *info, *category, *def, *value;
    char *vals[MAX_VALS], *labels[MAX_VALS];
    int nvals;
    bool visible;
} option_t;
static option_t opts[MAX_OPTS];
static int nopts;
static char *categories[64][2];   /* chave, nome */
static int ncategories;
static bool opts_updated;
/* Valores vindos do Java antes de o núcleo declarar as opções. */
static char *pending_keys[MAX_OPTS], *pending_vals[MAX_OPTS];
static int npending;

static char *sdup(const char *s) { return s ? strdup(s) : NULL; }

static void free_options(void) {
    for (int i = 0; i < nopts; i++) {
        option_t *o = &opts[i];
        free(o->key); free(o->desc); free(o->info); free(o->category); free(o->def); free(o->value);
        for (int j = 0; j < o->nvals; j++) { free(o->vals[j]); free(o->labels[j]); }
    }
    memset(opts, 0, sizeof(opts));
    nopts = 0;
    for (int i = 0; i < ncategories; i++) { free(categories[i][0]); free(categories[i][1]); }
    ncategories = 0;
}

static option_t *find_option(const char *key) {
    for (int i = 0; i < nopts; i++) if (!strcmp(opts[i].key, key)) return &opts[i];
    return NULL;
}

static bool valid_value(option_t *o, const char *v) {
    for (int j = 0; j < o->nvals; j++) if (!strcmp(o->vals[j], v)) return true;
    return false;
}

static option_t *add_option(const char *key) {
    option_t *o = find_option(key);
    if (o) return o;
    if (nopts >= MAX_OPTS) return NULL;
    o = &opts[nopts++];
    o->key = strdup(key);
    o->visible = true;
    return o;
}

/* Depois de declarar as opções: aplica o que o Java já tinha mandado. */
static void apply_pending(void) {
    for (int i = 0; i < npending; i++) {
        option_t *o = find_option(pending_keys[i]);
        if (o && valid_value(o, pending_vals[i])) { free(o->value); o->value = strdup(pending_vals[i]); }
    }
    for (int i = 0; i < nopts; i++) {
        if (!opts[i].value) opts[i].value = sdup(opts[i].def ? opts[i].def : (opts[i].nvals ? opts[i].vals[0] : ""));
    }
}

static void set_variables_legacy(const struct retro_variable *v) {
    free_options();
    for (; v && v->key; v++) {
        option_t *o = add_option(v->key);
        if (!o || !v->value) continue;
        /* "Descrição; valor1|valor2|..." (o primeiro é o padrão) */
        const char *semi = strchr(v->value, ';');
        if (!semi) continue;
        o->desc = strndup(v->value, (size_t)(semi - v->value));
        const char *p = semi + 1;
        while (*p == ' ') p++;
        char *list = strdup(p), *save = NULL;
        for (char *t = strtok_r(list, "|", &save); t && o->nvals < MAX_VALS; t = strtok_r(NULL, "|", &save)) {
            o->vals[o->nvals] = strdup(t);
            o->labels[o->nvals] = strdup(t);
            o->nvals++;
        }
        free(list);
        if (o->nvals) o->def = strdup(o->vals[0]);
    }
    apply_pending();
}

static void add_definition(const struct retro_core_option_v2_definition *d, const struct retro_core_option_v2_definition *local) {
    option_t *o = add_option(d->key);
    if (!o) return;
    o->desc = sdup(local && local->desc ? local->desc : d->desc);
    o->info = sdup(local && local->info ? local->info : d->info);
    o->category = sdup(d->category_key);
    o->def = sdup(d->default_value);
    for (int j = 0; j < RETRO_NUM_CORE_OPTION_VALUES_MAX && d->values[j].value && o->nvals < MAX_VALS; j++) {
        const char *label = d->values[j].label;
        if (local) {
            for (int k = 0; k < RETRO_NUM_CORE_OPTION_VALUES_MAX && local->values[k].value; k++) {
                if (!strcmp(local->values[k].value, d->values[j].value) && local->values[k].label) { label = local->values[k].label; break; }
            }
        }
        o->vals[o->nvals] = strdup(d->values[j].value);
        o->labels[o->nvals] = strdup(label ? label : d->values[j].value);
        o->nvals++;
    }
    if (!o->def && o->nvals) o->def = strdup(o->vals[0]);
}

static const struct retro_core_option_v2_definition *local_def(const struct retro_core_options_v2 *local, const char *key) {
    if (!local || !local->definitions) return NULL;
    for (const struct retro_core_option_v2_definition *d = local->definitions; d->key; d++) if (!strcmp(d->key, key)) return d;
    return NULL;
}

static void set_options_v2(const struct retro_core_options_v2 *us, const struct retro_core_options_v2 *local) {
    free_options();
    if (!us || !us->definitions) return;
    if (us->categories) {
        for (const struct retro_core_option_v2_category *c = us->categories; c->key && ncategories < 64; c++) {
            const char *desc = c->desc;
            if (local && local->categories) {
                for (const struct retro_core_option_v2_category *l = local->categories; l->key; l++)
                    if (!strcmp(l->key, c->key) && l->desc) { desc = l->desc; break; }
            }
            categories[ncategories][0] = strdup(c->key);
            categories[ncategories][1] = sdup(desc ? desc : c->key);
            ncategories++;
        }
    }
    for (const struct retro_core_option_v2_definition *d = us->definitions; d->key; d++) add_definition(d, local_def(local, d->key));
    apply_pending();
}

/* v1 tem a mesma forma do v2 sem categoria: converte uma definição por vez. */
static void set_options_v1(const struct retro_core_option_definition *us, const struct retro_core_option_definition *local) {
    free_options();
    for (const struct retro_core_option_definition *d = us; d && d->key; d++) {
        struct retro_core_option_v2_definition v2, l2;
        memset(&v2, 0, sizeof(v2));
        memset(&l2, 0, sizeof(l2));
        v2.key = d->key; v2.desc = d->desc; v2.info = d->info; v2.default_value = d->default_value;
        memcpy(v2.values, d->values, sizeof(v2.values));
        const struct retro_core_option_definition *l = NULL;
        for (const struct retro_core_option_definition *x = local; x && x->key; x++) if (!strcmp(x->key, d->key)) { l = x; break; }
        if (l) {
            l2.key = l->key; l2.desc = l->desc; l2.info = l->info;
            memcpy(l2.values, l->values, sizeof(l2.values));
        }
        add_definition(&v2, l ? &l2 : NULL);
    }
    apply_pending();
}

/* ---------------- Vídeo (estado compartilhado com o desenho) ---------------- */

static uint16_t *frame;            /* último quadro em RGB565, sem sobra de linha */
static unsigned frame_w, frame_h, frame_cap;
static bool frame_dirty;
static int av_enable = 3;          /* bit 0 vídeo, bit 1 áudio (run-ahead e avanço rápido desligam) */

/* ---------------- Áudio ---------------- */

#define RING_FRAMES 16384
static int16_t ring[RING_FRAMES * 2];
static atomic_uint ring_w, ring_r;     /* em quadros (L+R), crescem sem parar; índice = % RING_FRAMES */
static unsigned out_rate = 48000, out_burst = 256, target_frames = 2400;
static double res_pos;
static int16_t res_prev[2];
static float volume = 1.0f;
static atomic_bool muted;

static unsigned ring_fill(void) { return atomic_load(&ring_w) - atomic_load(&ring_r); }

static void ring_push(int16_t l, int16_t r) {
    unsigned w = atomic_load_explicit(&ring_w, memory_order_relaxed);
    if (w - atomic_load(&ring_r) >= RING_FRAMES - 1) return;   /* cheio: descarta */
    ring[(w % RING_FRAMES) * 2] = l;
    ring[(w % RING_FRAMES) * 2 + 1] = r;
    atomic_store_explicit(&ring_w, w + 1, memory_order_release);
}

#ifndef ONIX_HOST_TEST
static SLObjectItf sl_engine_obj, sl_mix_obj, sl_player_obj;
static SLEngineItf sl_engine;
static SLPlayItf sl_play;
static SLAndroidSimpleBufferQueueItf sl_queue;
static int16_t *sl_buf[3];
static int sl_next;

static void sl_callback(SLAndroidSimpleBufferQueueItf q, void *ctx) {
    (void)ctx;
    int16_t *buf = sl_buf[sl_next];
    sl_next = (sl_next + 1) % 3;
    unsigned r = atomic_load_explicit(&ring_r, memory_order_relaxed);
    unsigned avail = atomic_load_explicit(&ring_w, memory_order_acquire) - r;
    unsigned n = avail < out_burst ? avail : out_burst;
    bool mute = atomic_load(&muted);
    for (unsigned i = 0; i < n; i++) {
        buf[i * 2] = mute ? 0 : ring[((r + i) % RING_FRAMES) * 2];
        buf[i * 2 + 1] = mute ? 0 : ring[((r + i) % RING_FRAMES) * 2 + 1];
    }
    if (n < out_burst) memset(buf + n * 2, 0, (out_burst - n) * 4);   /* faltou som: silêncio */
    atomic_store_explicit(&ring_r, r + n, memory_order_release);
    (*q)->Enqueue(q, buf, out_burst * 4);
}

static void audio_stop(void) {
    if (sl_player_obj) { (*sl_player_obj)->Destroy(sl_player_obj); sl_player_obj = NULL; }
    if (sl_mix_obj) { (*sl_mix_obj)->Destroy(sl_mix_obj); sl_mix_obj = NULL; }
    if (sl_engine_obj) { (*sl_engine_obj)->Destroy(sl_engine_obj); sl_engine_obj = NULL; }
    for (int i = 0; i < 3; i++) { free(sl_buf[i]); sl_buf[i] = NULL; }
    sl_play = NULL; sl_queue = NULL;
}

static bool audio_start(void) {
    audio_stop();
    if (slCreateEngine(&sl_engine_obj, 0, NULL, 0, NULL, NULL) != SL_RESULT_SUCCESS) return false;
    if ((*sl_engine_obj)->Realize(sl_engine_obj, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return false;
    (*sl_engine_obj)->GetInterface(sl_engine_obj, SL_IID_ENGINE, &sl_engine);
    if ((*sl_engine)->CreateOutputMix(sl_engine, &sl_mix_obj, 0, NULL, NULL) != SL_RESULT_SUCCESS) return false;
    if ((*sl_mix_obj)->Realize(sl_mix_obj, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return false;

    SLDataLocator_AndroidSimpleBufferQueue loc = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 3};
    SLDataFormat_PCM pcm = {SL_DATAFORMAT_PCM, 2, out_rate * 1000, SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource src = {&loc, &pcm};
    SLDataLocator_OutputMix out = {SL_DATALOCATOR_OUTPUTMIX, sl_mix_obj};
    SLDataSink sink = {&out, NULL};
    const SLInterfaceID ids[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean req[] = {SL_BOOLEAN_TRUE};
    if ((*sl_engine)->CreateAudioPlayer(sl_engine, &sl_player_obj, &src, &sink, 1, ids, req) != SL_RESULT_SUCCESS) return false;
    if ((*sl_player_obj)->Realize(sl_player_obj, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return false;
    (*sl_player_obj)->GetInterface(sl_player_obj, SL_IID_PLAY, &sl_play);
    (*sl_player_obj)->GetInterface(sl_player_obj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &sl_queue);
    (*sl_queue)->RegisterCallback(sl_queue, sl_callback, NULL);
    for (int i = 0; i < 3; i++) sl_buf[i] = calloc(out_burst, 4);
    sl_next = 0;
    for (int i = 0; i < 2; i++) sl_callback(sl_queue, NULL);
    (*sl_play)->SetPlayState(sl_play, SL_PLAYSTATE_PLAYING);
    return true;
}

#else
/* Teste no PC: sem saída de som (o anel só enche e descarta). */
static void audio_stop(void) {}
static bool audio_start(void) { return false; }
#endif

/* Reamostragem linear do ritmo do núcleo para o da TV, com o ajuste dinâmico. */
static void audio_write(const int16_t *data, size_t frames) {
    if (!(av_enable & 2) || atomic_load(&muted)) return;
    double fill = (double)ring_fill();
    double delta = (target_frames - fill) / (double)target_frames;   /* >0: anel baixo, faz mais som */
    if (delta > 1) delta = 1;
    if (delta < -1) delta = -1;
    double step = (av.timing.sample_rate > 0 ? av.timing.sample_rate : 32000) / (double)out_rate / (1.0 + 0.005 * delta);
    for (size_t i = 0; i < frames; i++) {
        int16_t l = data[i * 2], r = data[i * 2 + 1];
        while (res_pos < 1.0) {
            double t = res_pos;
            int sl = (int)(res_prev[0] + (l - res_prev[0]) * t);
            int sr = (int)(res_prev[1] + (r - res_prev[1]) * t);
            ring_push((int16_t)(sl * volume), (int16_t)(sr * volume));
            res_pos += step;
        }
        res_pos -= 1.0;
        res_prev[0] = l;
        res_prev[1] = r;
    }
}

/* ---------------- Entrada ---------------- */

static atomic_uint pad[4];             /* bits RETRO_DEVICE_ID_JOYPAD_* por jogador */
static atomic_int analog[4][2][2];     /* [porta][esq/dir][x/y] */

static atomic_uint turbo_mask;         /* botões que, segurados, apertam e soltam sozinhos */
static unsigned turbo_period = 3, frame_count;

static void input_poll(void) {}

static int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    if (device == RETRO_DEVICE_JOYPAD) {
        unsigned bits = atomic_load(&pad[port]);
        unsigned turbo = atomic_load(&turbo_mask);
        if (turbo && (frame_count / turbo_period) % 2) bits &= ~turbo;
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)bits;
        return (bits >> id) & 1;
    }
    if (device == RETRO_DEVICE_ANALOG && index < 2 && id < 2) return (int16_t)atomic_load(&analog[port][index][id]);
    return 0;
}

/* ---------------- Callbacks do núcleo ---------------- */

static void video_refresh(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (!data || !(av_enable & 1)) return;   /* NULL = repetir o quadro anterior */
    if ((size_t)w * h > frame_cap) {
        free(frame);
        frame_cap = w * h;
        frame = malloc(frame_cap * 2);
        if (!frame) { frame_cap = 0; return; }
    }
    frame_w = w;
    frame_h = h;
    if (pixfmt == RETRO_PIXEL_FORMAT_RGB565) {
        for (unsigned y = 0; y < h; y++) memcpy(frame + y * w, (const uint8_t *)data + y * pitch, w * 2);
    } else if (pixfmt == RETRO_PIXEL_FORMAT_XRGB8888) {
        for (unsigned y = 0; y < h; y++) {
            const uint32_t *s = (const uint32_t *)((const uint8_t *)data + y * pitch);
            uint16_t *d = frame + y * w;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                d[x] = (uint16_t)(((p >> 8) & 0xF800) | ((p >> 5) & 0x07E0) | ((p >> 3) & 0x001F));
            }
        }
    } else {
        for (unsigned y = 0; y < h; y++) {
            const uint16_t *s = (const uint16_t *)((const uint8_t *)data + y * pitch);
            uint16_t *d = frame + y * w;
            for (unsigned x = 0; x < w; x++) {
                uint16_t p = s[x];
                d[x] = (uint16_t)(((p & 0x7C00) << 1) | ((p & 0x03E0) << 1) | ((p >> 4) & 0x20) | (p & 0x1F));
            }
        }
    }
    frame_dirty = true;
}

static void audio_sample(int16_t l, int16_t r) {
    int16_t s[2] = {l, r};
    audio_write(s, 1);
}

static size_t audio_batch(const int16_t *data, size_t frames) {
    audio_write(data, frames);
    return frames;
}

static void core_log(enum retro_log_level level, const char *fmt, ...) {
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    __android_log_print(level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR : level == RETRO_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO, TAG, "core: %s", buf);
}

static bool fast_forwarding;

static bool environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            ((struct retro_log_callback *)data)->log = core_log;
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *)data = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            enum retro_pixel_format f = *(const enum retro_pixel_format *)data;
            if (f > RETRO_PIXEL_FORMAT_RGB565) return false;
            pixfmt = f;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char **)data = system_dir;
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = save_dir;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *(unsigned *)data = 2;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES:
            set_variables_legacy((const struct retro_variable *)data);
            return true;
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
            set_options_v1((const struct retro_core_option_definition *)data, NULL);
            return true;
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: {
            const struct retro_core_options_intl *i = data;
            set_options_v1(i->us, i->local);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
            set_options_v2((const struct retro_core_options_v2 *)data, NULL);
            return true;
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: {
            const struct retro_core_options_v2_intl *i = data;
            set_options_v2(i->us, i->local);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY: {
            const struct retro_core_option_display *d = data;
            option_t *o = d && d->key ? find_option(d->key) : NULL;
            if (o) o->visible = d->visible;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            struct retro_variable *v = data;
            option_t *o = v && v->key ? find_option(v->key) : NULL;
            if (!o) return false;
            v->value = o->value;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool *)data = opts_updated;
            opts_updated = false;
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *)data = language;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            av.geometry = *(const struct retro_game_geometry *)data;
            return true;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            av = *(const struct retro_system_av_info *)data;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            *(int *)data = av_enable;
            return true;
        case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
            *(bool *)data = fast_forwarding;
            return true;
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE:
        case RETRO_ENVIRONMENT_SET_ROTATION:
            return true;
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            const struct retro_message *m = data;
            if (m && m->msg) LOGI("core msg: %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT: {
            const struct retro_message_ext *m = data;
            if (m && m->msg) LOGI("core msg: %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:
            *(unsigned *)data = 1;
            return true;
        default:
            return false;
    }
}

/* ---------------- Carregar ---------------- */

#define SYM(name) do { *(void **)&core.name = dlsym(core.handle, "retro_" #name); if (!core.name) { LOGE("sem retro_" #name); return false; } } while (0)

static bool open_core(const char *path) {
    memset(&core, 0, sizeof(core));
    core.handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!core.handle) { LOGE("dlopen: %s", dlerror()); return false; }
    SYM(set_environment); SYM(set_video_refresh); SYM(set_audio_sample); SYM(set_audio_sample_batch);
    SYM(set_input_poll); SYM(set_input_state); SYM(init); SYM(deinit); SYM(get_system_info);
    SYM(get_system_av_info); SYM(set_controller_port_device); SYM(reset); SYM(run); SYM(serialize_size);
    SYM(serialize); SYM(unserialize); SYM(cheat_reset); SYM(cheat_set); SYM(load_game); SYM(unload_game);
    SYM(get_memory_data); SYM(get_memory_size);
    return true;
}

static char *jstr(JNIEnv *env, jstring s) {
    if (!s) return strdup("");
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    char *r = strdup(c ? c : "");
    if (c) (*env)->ReleaseStringUTFChars(env, s, c);
    return r;
}

static bool read_file(const char *path, void **out, size_t *size) {
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n < 0 || n > 128 * 1024 * 1024) { fclose(f); return false; }
    void *buf = malloc(n ? (size_t)n : 1);
    if (!buf || fread(buf, 1, (size_t)n, f) != (size_t)n) { free(buf); fclose(f); return false; }
    fclose(f);
    *out = buf;
    *size = (size_t)n;
    return true;
}

static bool write_file(const char *path, const void *data, size_t size) {
    char tmp[1024];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    FILE *f = fopen(tmp, "wb");
    if (!f) return false;
    bool ok = fwrite(data, 1, size, f) == size;
    ok = (fflush(f) == 0) && ok;
    fsync(fileno(f));
    fclose(f);
    if (!ok || rename(tmp, path) != 0) { unlink(tmp); return false; }
    return true;
}

/* ---------------- Rebobinar ---------------- */

static uint8_t *rw_buf;          /* anel de estados de tamanho fixo */
static size_t rw_state, rw_slots, rw_head, rw_count;
static int rw_interval = 2, rw_tick;
static bool rewind_on, rewinding;

static void rewind_free(void) {
    free(rw_buf);
    rw_buf = NULL;
    rw_slots = rw_count = rw_head = 0;
}

static void rewind_setup(size_t budget_mb) {
    rewind_free();
    if (!game_loaded) return;
    rw_state = core.serialize_size();
    if (!rw_state) return;
    rw_slots = budget_mb * 1024 * 1024 / rw_state;
    if (rw_slots < 8) { rw_slots = 0; return; }
    rw_buf = malloc(rw_slots * rw_state);
    if (!rw_buf) rw_slots = 0;
}

static void rewind_push(void) {
    if (!rw_buf || ++rw_tick < rw_interval) return;
    rw_tick = 0;
    if (core.serialize_size() != rw_state) return;
    if (core.serialize(rw_buf + rw_head * rw_state, rw_state)) {
        rw_head = (rw_head + 1) % rw_slots;
        if (rw_count < rw_slots) rw_count++;
    }
}

static bool rewind_pop(void) {
    if (!rw_buf || !rw_count) return false;
    rw_head = (rw_head + rw_slots - 1) % rw_slots;
    rw_count--;
    return core.unserialize(rw_buf + rw_head * rw_state, rw_state);
}

/* ---------------- Rodar ---------------- */

static atomic_bool paused;
static int ff_speed = 1, runahead;
static atomic_bool ff_hold, rewind_hold;
static bool ff_toggle;
static double display_hz = 60.0, acc;
static struct timespec last_ts;
static uint8_t *ra_buf;
static size_t ra_size;

static void run_one(bool video, bool audio) {
    frame_count++;
    if (runahead > 0 && !fast_forwarding) {
        /* Run-ahead: roda o quadro de verdade (com som), guarda, adianta N quadros só para mostrar
           a imagem do futuro e volta. A resposta aos botões fica N quadros mais rápida. */
        size_t need = core.serialize_size();
        if (need && need != ra_size) { free(ra_buf); ra_buf = malloc(need); ra_size = ra_buf ? need : 0; }
        if (ra_buf) {
            av_enable = audio ? 2 : 0;
            core.run();
            if (core.serialize(ra_buf, ra_size)) {
                for (int i = 1; i <= runahead; i++) {
                    av_enable = (video && i == runahead) ? 1 : 0;
                    core.run();
                }
                core.unserialize(ra_buf, ra_size);
                av_enable = 3;
                return;
            }
            av_enable = 3;
            return;
        }
    }
    av_enable = (video ? 1 : 0) | (audio ? 2 : 0);
    core.run();
    av_enable = 3;
}

static double now_s(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec / 1e9;
}

static double last_time;

static void step(void) {
    if (!game_loaded || atomic_load(&paused)) { last_time = 0; return; }
    double fps = av.timing.fps > 1 ? av.timing.fps : 60.0;
    int frames = 1;
    if (fabs(display_hz - fps) / fps > 0.02) {
        double t = now_s();
        if (last_time > 0) acc += (t - last_time) * fps;
        last_time = t;
        frames = (int)acc;
        if (frames > 3) { frames = 3; acc = 0; }
        acc -= frames;
        if (frames <= 0) return;
    }
    if (rewinding || atomic_load(&rewind_hold)) {
        if (rewind_on && rewind_pop()) {
            fast_forwarding = false;
            av_enable = 1;
            core.run();          /* mostra o quadro do estado voltado, sem som */
            av_enable = 3;
            rw_tick = 0;
            return;
        }
    }
    fast_forwarding = ff_toggle || atomic_load(&ff_hold);
    int n = fast_forwarding ? frames * (ff_speed > 1 ? ff_speed : 2) : frames;
    for (int i = 0; i < n; i++) {
        run_one(i == n - 1, !fast_forwarding);
        if (rewind_on) rewind_push();
    }
}

/* ---------------- Desenho ---------------- */

static GLuint prog[5], tex, vbo;
static int filter_mode = 0;   /* 0 nítido (sharp bilinear), 1 pixel puro, 2 suave, 3 CRT, 4 LCD */
static int scale_mode = 0;    /* 0 ajustar, 1 inteiro, 2 esticar */
static int aspect_mode = 0;   /* 0 original, 1 4:3, 2 pixel quadrado (8:7 no SNES), 3 16:9 */
static int view_w = 1920, view_h = 1080;
static unsigned tex_w, tex_h;

static const char *VS =
    "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex;\n"
    "void main(){ vTex = aTex; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

static const char *FS[5] = {
    /* nítido: pixel grande e quadrado, com só a borda suavizada (escala não inteira sem serrilhado) */
    "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; uniform vec2 uTexSize; uniform vec2 uScale;\n"
    "void main(){ vec2 t = vTex * uTexSize; vec2 f = fract(t) - 0.5; vec2 r = 0.5 - 0.5 / uScale;\n"
    " vec2 o = (f - clamp(f, -r, r)) * uScale + 0.5; gl_FragColor = texture2D(uTex, (floor(t) + o) / uTexSize); }\n",
    "precision mediump float; varying vec2 vTex; uniform sampler2D uTex;\n"
    "void main(){ gl_FragColor = texture2D(uTex, vTex); }\n",
    "precision mediump float; varying vec2 vTex; uniform sampler2D uTex;\n"
    "void main(){ gl_FragColor = texture2D(uTex, vTex); }\n",
    /* CRT: linhas de varredura e uma máscara leve de fósforo */
    "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; uniform vec2 uTexSize; uniform vec2 uOut;\n"
    "void main(){ vec2 t = vTex * uTexSize; vec2 uv = (floor(t) + 0.5) / uTexSize; uv.x = vTex.x;\n"
    " vec3 c = texture2D(uTex, uv).rgb; float s = sin(3.14159 * fract(t.y)); float line = mix(0.55, 1.0, s * s);\n"
    " float m = mod(floor(gl_FragCoord.x), 3.0); vec3 mask = m < 1.0 ? vec3(1.0,0.82,0.82) : m < 2.0 ? vec3(0.82,1.0,0.82) : vec3(0.82,0.82,1.0);\n"
    " gl_FragColor = vec4(c * line * mask * 1.22, 1.0); }\n",
    /* LCD: grade fina entre os pixels, como a tela do portátil */
    "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; uniform vec2 uTexSize; uniform vec2 uScale;\n"
    "void main(){ vec2 t = vTex * uTexSize; vec3 c = texture2D(uTex, (floor(t) + 0.5) / uTexSize).rgb;\n"
    " vec2 f = fract(t); float e = 1.0 / max(uScale.x, 1.0); vec2 g = step(e, f) * step(f, 1.0 - e * 0.5);\n"
    " float k = mix(0.72, 1.0, g.x * g.y); gl_FragColor = vec4(c * k * 1.08, 1.0); }\n",
};

static GLuint compile(GLenum type, const char *src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof(log), NULL, log);
        LOGE("shader: %s", log);
    }
    return s;
}

static void gl_setup(void) {
    GLuint vs = compile(GL_VERTEX_SHADER, VS);
    for (int i = 0; i < 5; i++) {
        GLuint fs = compile(GL_FRAGMENT_SHADER, FS[i]);
        prog[i] = glCreateProgram();
        glAttachShader(prog[i], vs);
        glAttachShader(prog[i], fs);
        glBindAttribLocation(prog[i], 0, "aPos");
        glBindAttribLocation(prog[i], 1, "aTex");
        glLinkProgram(prog[i]);
        glDeleteShader(fs);
    }
    glDeleteShader(vs);
    glGenTextures(1, &tex);
    glGenBuffers(1, &vbo);
    tex_w = tex_h = 0;
    frame_dirty = true;
}

static void draw(void) {
    glViewport(0, 0, view_w, view_h);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!frame || !frame_w || !frame_h) return;

    glBindTexture(GL_TEXTURE_2D, tex);
    if (frame_w != tex_w || frame_h != tex_h) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, frame_w, frame_h, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, frame);
        tex_w = frame_w;
        tex_h = frame_h;
        frame_dirty = false;
    } else if (frame_dirty) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frame_w, frame_h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, frame);
        frame_dirty = false;
    }
    GLint f = (filter_mode == 1 || filter_mode == 3 || filter_mode == 4) ? GL_NEAREST : GL_LINEAR;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    /* Proporção da imagem */
    double aspect;
    switch (aspect_mode) {
        case 1: aspect = 4.0 / 3.0; break;
        case 2: aspect = (double)frame_w / frame_h; break;
        case 3: aspect = 16.0 / 9.0; break;
        default: aspect = av.geometry.aspect_ratio > 0 ? av.geometry.aspect_ratio : (double)frame_w / frame_h;
    }
    double w, h;
    if (scale_mode == 2) {
        w = view_w; h = view_h;
    } else {
        h = view_h;
        w = h * aspect;
        if (w > view_w) { w = view_w; h = w / aspect; }
        if (scale_mode == 1) {
            /* Inteiro na altura (a largura segue a proporção) */
            int k = (int)(h / frame_h);
            if (k >= 1) { h = k * (double)frame_h; w = h * aspect; }
            if (w > view_w) { w = view_w; h = w / aspect; }
        }
    }
    float x = (float)(w / view_w), y = (float)(h / view_h);
    float quad[] = {-x, -y, 0, 1,  x, -y, 1, 1,  -x, y, 0, 0,  x, y, 1, 0};
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STREAM_DRAW);

    GLuint p = prog[filter_mode < 0 || filter_mode > 4 ? 0 : filter_mode];
    glUseProgram(p);
    glUniform1i(glGetUniformLocation(p, "uTex"), 0);
    GLint loc = glGetUniformLocation(p, "uTexSize");
    if (loc >= 0) glUniform2f(loc, (float)frame_w, (float)frame_h);
    loc = glGetUniformLocation(p, "uScale");
    if (loc >= 0) glUniform2f(loc, fmaxf(1.0f, floorf((float)(w / frame_w))), fmaxf(1.0f, floorf((float)(h / frame_h))));
    loc = glGetUniformLocation(p, "uOut");
    if (loc >= 0) glUniform2f(loc, (float)w, (float)h);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 16, (void *)0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 16, (void *)8);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

/* ---------------- JSON das opções ---------------- */

typedef struct { char *s; size_t n, cap; } sbuf;

static void sb_add(sbuf *b, const char *s, size_t n) {
    if (b->n + n + 1 > b->cap) {
        b->cap = (b->n + n + 1) * 2;
        b->s = realloc(b->s, b->cap);
    }
    memcpy(b->s + b->n, s, n);
    b->n += n;
    b->s[b->n] = 0;
}

static void sb_str(sbuf *b, const char *s) {
    sb_add(b, "\"", 1);
    for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
        char esc[8];
        if (*p == '"' || *p == '\\') { esc[0] = '\\'; esc[1] = (char)*p; sb_add(b, esc, 2); }
        else if (*p < 0x20) { snprintf(esc, sizeof(esc), "\\u%04x", *p); sb_add(b, esc, 6); }
        else sb_add(b, (const char *)p, 1);
    }
    sb_add(b, "\"", 1);
}

static jstring options_json(JNIEnv *env) {
    sbuf b = {0};
    sb_add(&b, "{\"categories\":[", 15);
    for (int i = 0; i < ncategories; i++) {
        if (i) sb_add(&b, ",", 1);
        sb_add(&b, "[", 1); sb_str(&b, categories[i][0]); sb_add(&b, ",", 1); sb_str(&b, categories[i][1]); sb_add(&b, "]", 1);
    }
    sb_add(&b, "],\"options\":[", 13);
    for (int i = 0; i < nopts; i++) {
        option_t *o = &opts[i];
        if (i) sb_add(&b, ",", 1);
        sb_add(&b, "{\"key\":", 7); sb_str(&b, o->key);
        sb_add(&b, ",\"desc\":", 8); sb_str(&b, o->desc ? o->desc : o->key);
        sb_add(&b, ",\"info\":", 8); sb_str(&b, o->info);
        sb_add(&b, ",\"category\":", 12); sb_str(&b, o->category);
        sb_add(&b, ",\"default\":", 11); sb_str(&b, o->def);
        sb_add(&b, ",\"value\":", 9); sb_str(&b, o->value);
        sb_add(&b, o->visible ? ",\"visible\":true" : ",\"visible\":false", o->visible ? 15 : 16);
        sb_add(&b, ",\"values\":[", 11);
        for (int j = 0; j < o->nvals; j++) {
            if (j) sb_add(&b, ",", 1);
            sb_add(&b, "[", 1); sb_str(&b, o->vals[j]); sb_add(&b, ",", 1); sb_str(&b, o->labels[j]); sb_add(&b, "]", 1);
        }
        sb_add(&b, "]}", 2);
    }
    sb_add(&b, "]}", 2);
    jstring r = (*env)->NewStringUTF(env, b.s);
    free(b.s);
    return r;
}

/* ---------------- JNI ---------------- */

#define JNI(ret, name) JNIEXPORT ret JNICALL Java_com_onix_gamehub_Native_##name

static void clear_pending(void) {
    for (int i = 0; i < npending; i++) { free(pending_keys[i]); free(pending_vals[i]); }
    npending = 0;
}

JNI(void, setOption)(JNIEnv *env, jclass c, jstring jk, jstring jv) {
    char *k = jstr(env, jk), *v = jstr(env, jv);
    option_t *o = find_option(k);
    if (o) {
        if (valid_value(o, v) && strcmp(o->value ? o->value : "", v)) {
            free(o->value);
            o->value = strdup(v);
            opts_updated = true;
        }
    }
    bool replaced = false;
    for (int i = 0; i < npending; i++) if (!strcmp(pending_keys[i], k)) { free(pending_vals[i]); pending_vals[i] = strdup(v); replaced = true; break; }
    if (!replaced && npending < MAX_OPTS) { pending_keys[npending] = strdup(k); pending_vals[npending] = strdup(v); npending++; }
    free(k);
    free(v);
}

JNI(void, clearOptions)(JNIEnv *env, jclass c) {
    clear_pending();
}

/* Lê as opções do núcleo sem carregar jogo (tela de configurações do console). */
JNI(jstring, probe)(JNIEnv *env, jclass c, jstring jcore, jint lang) {
    if (loaded) return options_json(env);
    char *path = jstr(env, jcore);
    jstring r = NULL;
    language = (unsigned)lang;
    probing = true;
    if (open_core(path)) {
        core.set_environment(environment);
        r = options_json(env);
        dlclose(core.handle);
        memset(&core, 0, sizeof(core));
    }
    probing = false;
    free_options();
    free(path);
    return r ? r : (*env)->NewStringUTF(env, "{\"categories\":[],\"options\":[]}");
}

JNI(jboolean, load)(JNIEnv *env, jclass c, jstring jcore, jstring jrom, jstring jsys, jstring jsave, jint lang) {
    if (loaded) return JNI_FALSE;
    char *path = jstr(env, jcore), *rom = jstr(env, jrom), *sys = jstr(env, jsys), *sav = jstr(env, jsave);
    snprintf(system_dir, sizeof(system_dir), "%s", sys);
    snprintf(save_dir, sizeof(save_dir), "%s", sav);
    language = (unsigned)lang;
    bool ok = false;
    if (open_core(path)) {
        loaded = true;
        core.set_environment(environment);
        core.set_video_refresh(video_refresh);
        core.set_audio_sample(audio_sample);
        core.set_audio_sample_batch(audio_batch);
        core.set_input_poll(input_poll);
        core.set_input_state(input_state);
        core.init();
        if (!nopts) apply_pending();
        struct retro_system_info si;
        memset(&si, 0, sizeof(si));
        core.get_system_info(&si);
        struct retro_game_info gi = {rom, NULL, 0, NULL};
        if (!si.need_fullpath) {
            if (read_file(rom, &rom_data, &rom_size)) { gi.data = rom_data; gi.size = rom_size; }
        }
        if (si.need_fullpath || gi.data) ok = core.load_game(&gi);
        if (ok) {
            game_loaded = true;
            core.get_system_av_info(&av);
            for (unsigned p = 0; p < 2; p++) core.set_controller_port_device(p, RETRO_DEVICE_JOYPAD);
            LOGI("jogo carregado: %ux%u %.3f fps %.0f Hz", av.geometry.base_width, av.geometry.base_height, av.timing.fps, av.timing.sample_rate);
        } else {
            LOGE("o núcleo recusou o jogo");
        }
    }
    free(path); free(rom); free(sys); free(sav);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNI(void, unload)(JNIEnv *env, jclass c) {
    audio_stop();
    if (game_loaded) core.unload_game();
    if (loaded) core.deinit();
    game_loaded = loaded = false;
    rewind_free();
    free(ra_buf); ra_buf = NULL; ra_size = 0;
    free(rom_data); rom_data = NULL; rom_size = 0;
    /* Não fecha o .so: alguns núcleos não gostam de reabrir no mesmo processo (o jogo roda num
       processo só dele, que termina ao sair). */
}

JNI(jdouble, fps)(JNIEnv *env, jclass c) { return av.timing.fps; }

JNI(void, startAudio)(JNIEnv *env, jclass c, jint rate, jint burst, jint latency_ms) {
    out_rate = rate > 8000 ? (unsigned)rate : 48000;
    out_burst = burst >= 64 && burst <= 4096 ? (unsigned)burst : 256;
    target_frames = (unsigned)(out_rate * (latency_ms > 0 ? latency_ms : 64) / 1000);
    if (target_frames < out_burst * 2) target_frames = out_burst * 2;
    if (target_frames > RING_FRAMES / 3) target_frames = RING_FRAMES / 3;
    atomic_store(&ring_w, 0);
    atomic_store(&ring_r, 0);
    res_pos = 0;
    if (!audio_start()) { LOGE("sem áudio (OpenSL)"); audio_stop(); }
}

JNI(void, setVolume)(JNIEnv *env, jclass c, jfloat v) { volume = v < 0 ? 0 : v > 1 ? 1 : v; }

JNI(void, setMuted)(JNIEnv *env, jclass c, jboolean m) { atomic_store(&muted, m); }

JNI(void, setPaused)(JNIEnv *env, jclass c, jboolean p) {
    atomic_store(&paused, p);
#ifndef ONIX_HOST_TEST
    if (sl_play) (*sl_play)->SetPlayState(sl_play, p ? SL_PLAYSTATE_PAUSED : SL_PLAYSTATE_PLAYING);
#endif
}

JNI(void, setButtons)(JNIEnv *env, jclass c, jint port, jint bits) {
    if (port >= 0 && port < 4) atomic_store(&pad[port], (unsigned)bits);
}

JNI(void, setAnalog)(JNIEnv *env, jclass c, jint port, jint stick, jint x, jint y) {
    if (port < 0 || port >= 4 || stick < 0 || stick > 1) return;
    atomic_store(&analog[port][stick][0], x);
    atomic_store(&analog[port][stick][1], y);
}

JNI(void, setHotkeys)(JNIEnv *env, jclass c, jboolean ff, jboolean rw) {
    atomic_store(&ff_hold, ff);
    atomic_store(&rewind_hold, rw);
}

JNI(void, configure)(JNIEnv *env, jclass c, jint filter, jint scale, jint aspect, jint ffspeed, jint ra, jboolean rw, jint rwmb, jdouble hz) {
    filter_mode = filter;
    scale_mode = scale;
    aspect_mode = aspect;
    ff_speed = ffspeed;
    runahead = ra < 0 ? 0 : ra > 2 ? 2 : ra;
    display_hz = hz > 20 ? hz : 60.0;
    if (rw != rewind_on || (rw && !rw_buf)) {
        rewind_on = rw;
        if (rw) rewind_setup((size_t)(rwmb > 0 ? rwmb : 48)); else rewind_free();
    }
}

JNI(void, setTurbo)(JNIEnv *env, jclass c, jint mask, jint period) {
    atomic_store(&turbo_mask, (unsigned)mask);
    turbo_period = period >= 1 && period <= 30 ? (unsigned)period : 3;
}

JNI(void, setFastForward)(JNIEnv *env, jclass c, jboolean on) { ff_toggle = on; }

JNI(jboolean, fastForward)(JNIEnv *env, jclass c) { return ff_toggle; }

JNI(void, surfaceCreated)(JNIEnv *env, jclass c) { gl_setup(); }

JNI(void, surfaceChanged)(JNIEnv *env, jclass c, jint w, jint h) { view_w = w; view_h = h; }

JNI(void, frame)(JNIEnv *env, jclass c) {
    step();
    draw();
}

JNI(void, reset)(JNIEnv *env, jclass c) { if (game_loaded) core.reset(); }

JNI(jboolean, saveState)(JNIEnv *env, jclass c, jstring jpath) {
    if (!game_loaded) return JNI_FALSE;
    size_t n = core.serialize_size();
    if (!n) return JNI_FALSE;
    void *buf = malloc(n);
    bool ok = buf && core.serialize(buf, n);
    if (ok) {
        char *path = jstr(env, jpath);
        ok = write_file(path, buf, n);
        free(path);
    }
    free(buf);
    return ok;
}

JNI(jboolean, loadState)(JNIEnv *env, jclass c, jstring jpath) {
    if (!game_loaded) return JNI_FALSE;
    char *path = jstr(env, jpath);
    void *buf = NULL;
    size_t n = 0;
    bool ok = read_file(path, &buf, &n) && core.unserialize(buf, n);
    free(buf);
    free(path);
    if (ok) rw_count = 0;   /* o rebobinar não volta para antes do estado carregado */
    return ok;
}

static uLong sram_crc;

JNI(jboolean, loadSram)(JNIEnv *env, jclass c, jstring jpath) {
    if (!game_loaded) return JNI_FALSE;
    size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *mem = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!size || !mem) return JNI_FALSE;
    char *path = jstr(env, jpath);
    void *buf = NULL;
    size_t n = 0;
    bool ok = read_file(path, &buf, &n);
    if (ok) memcpy(mem, buf, n < size ? n : size);
    free(buf);
    free(path);
    sram_crc = crc32(0L, mem, (uInt)size);
    return ok;
}

/* Grava a memória do cartucho só quando ela mudou. */
JNI(jboolean, saveSram)(JNIEnv *env, jclass c, jstring jpath) {
    if (!game_loaded) return JNI_FALSE;
    size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *mem = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (!size || !mem) return JNI_FALSE;
    uLong crc = crc32(0L, mem, (uInt)size);
    if (crc == sram_crc) return JNI_TRUE;
    char *path = jstr(env, jpath);
    bool ok = write_file(path, mem, size);
    free(path);
    if (ok) sram_crc = crc;
    return ok;
}

JNI(void, cheatReset)(JNIEnv *env, jclass c) { if (game_loaded) core.cheat_reset(); }

JNI(void, cheatSet)(JNIEnv *env, jclass c, jint index, jboolean on, jstring jcode) {
    if (!game_loaded) return;
    char *code = jstr(env, jcode);
    core.cheat_set((unsigned)index, on, code);
    free(code);
}

JNI(jstring, options)(JNIEnv *env, jclass c) { return options_json(env); }

/* Último quadro em ARGB para a miniatura dos estados salvos. Retorna (w << 16) | h. */
JNI(jint, snapshot)(JNIEnv *env, jclass c, jintArray out) {
    if (!frame || !frame_w || !frame_h) return 0;
    jsize len = (*env)->GetArrayLength(env, out);
    if ((size_t)len < (size_t)frame_w * frame_h) return 0;
    jint *d = (*env)->GetIntArrayElements(env, out, NULL);
    for (size_t i = 0; i < (size_t)frame_w * frame_h; i++) {
        uint16_t p = frame[i];
        unsigned r = (p >> 11) & 31, g = (p >> 5) & 63, b = p & 31;
        d[i] = (jint)(0xFF000000u | ((r << 3 | r >> 2) << 16) | ((g << 2 | g >> 4) << 8) | (b << 3 | b >> 2));
    }
    (*env)->ReleaseIntArrayElements(env, out, d, 0);
    return (jint)((frame_w << 16) | frame_h);
}
