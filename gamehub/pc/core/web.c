/*
 * Frontend libretro do Onix GameHub para PC: o mesmo motor do jni/retro.c da TV, compilado para
 * WebAssembly com o núcleo junto (um módulo por console, ligação estática).
 *
 * Aqui só fica o que fala com o núcleo: opções, quadros, som, botões, run-ahead, rebobinar,
 * estados e trapaças. O desenho (WebGL, com os filtros) e a saída de som (AudioWorklet) ficam no
 * JavaScript (pc/web/game.js), que lê a imagem e o som direto da memória do módulo.
 *
 * Ritmo: o JS chama ox_step() a cada quadro da tela (requestAnimationFrame). Quando a tela e o
 * jogo têm quase o mesmo ritmo, roda um quadro por vez e a diferença é absorvida pelo controle
 * dinâmico da taxa do som (o mesmo do RetroArch); se não, os quadros seguem o relógio.
 */
#include <emscripten.h>
#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libretro.h"

#define API EMSCRIPTEN_KEEPALIVE

/* ---------------- Núcleo (ligado junto) ---------------- */

static bool loaded, game_loaded;
static char system_dir[] = "/system", save_dir[] = "/saves";
static unsigned language = RETRO_LANGUAGE_PORTUGUESE_BRAZIL;
static void *rom_data;
static size_t rom_size;
static struct retro_system_av_info av;
static enum retro_pixel_format pixfmt = RETRO_PIXEL_FORMAT_0RGB1555;

/* ---------------- Opções do núcleo ---------------- */

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
static char *categories[64][2];
static int ncategories;
static bool opts_updated;
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

/* ---------------- Vídeo ----------------
   O último quadro fica em RGBA de 8 bits (bytes R, G, B, A), sem sobra de linha; o JS manda para
   a textura quando muda. Não RGB565 como na TV: no SwiftShader (PC sem placa de vídeo) atualizar
   uma textura RGB565 derruba o WebGL. */

static uint32_t *frame;
static unsigned frame_w, frame_h, frame_cap;
static int frame_serial;           /* sobe a cada quadro novo */
static int av_enable = 3;          /* bit 0 vídeo, bit 1 áudio */

/* ---------------- Áudio ----------------
   Anel de amostras já na taxa da saída do PC; o JS tira a cada quadro e manda para o AudioWorklet,
   e conta quanto som ainda está na fila de lá (ox_audio_queued) para o ajuste dinâmico. */

#define RING_FRAMES 16384
static int16_t ring[RING_FRAMES * 2];
static unsigned ring_w, ring_r;
static unsigned out_rate = 48000, target_frames = 3072, queued_out;
static double res_pos;
static int16_t res_prev[2];
static float volume = 1.0f;
static bool muted;

static void ring_push(int16_t l, int16_t r) {
    if (ring_w - ring_r >= RING_FRAMES - 1) return;
    ring[(ring_w % RING_FRAMES) * 2] = l;
    ring[(ring_w % RING_FRAMES) * 2 + 1] = r;
    ring_w++;
}

static void audio_write(const int16_t *data, size_t frames) {
    if (!(av_enable & 2) || muted) return;
    double fill = (double)(ring_w - ring_r) + queued_out;
    double delta = (target_frames - fill) / (double)target_frames;
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

static unsigned pad[4];
static int analog[4][2][2];
static unsigned turbo_mask, turbo_period = 3, frame_count;

static void input_poll(void) {}

static int16_t input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    if (device == RETRO_DEVICE_JOYPAD) {
        unsigned bits = pad[port];
        if (turbo_mask && (frame_count / turbo_period) % 2) bits &= ~turbo_mask;
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)bits;
        return (bits >> id) & 1;
    }
    if (device == RETRO_DEVICE_ANALOG && index < 2 && id < 2) return (int16_t)analog[port][index][id];
    return 0;
}

/* ---------------- Callbacks do núcleo ---------------- */

static void video_refresh(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (!data || !(av_enable & 1)) return;
    if ((size_t)w * h > frame_cap) {
        free(frame);
        frame_cap = w * h;
        frame = malloc(frame_cap * 4);
        if (!frame) { frame_cap = 0; return; }
    }
    frame_w = w;
    frame_h = h;
    /* Cada formato do núcleo vira 0xAABBGGRR (bytes R, G, B, A na memória). */
    for (unsigned y = 0; y < h; y++) {
        const uint8_t *row = (const uint8_t *)data + y * pitch;
        uint32_t *d = frame + y * w;
        if (pixfmt == RETRO_PIXEL_FORMAT_XRGB8888) {
            const uint32_t *s = (const uint32_t *)row;
            for (unsigned x = 0; x < w; x++) {
                uint32_t p = s[x];
                d[x] = 0xFF000000u | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
            }
        } else {
            const uint16_t *s = (const uint16_t *)row;
            bool rgb565 = pixfmt == RETRO_PIXEL_FORMAT_RGB565;
            for (unsigned x = 0; x < w; x++) {
                uint16_t p = s[x];
                unsigned r, g, b;
                if (rgb565) { r = (p >> 11) & 31; g = (p >> 5) & 63; b = p & 31; g = (g << 2) | (g >> 4); }
                else { r = (p >> 10) & 31; g = (p >> 5) & 31; b = p & 31; g = (g << 3) | (g >> 2); }
                d[x] = 0xFF000000u | (((b << 3) | (b >> 2)) << 16) | (g << 8) | ((r << 3) | (r >> 2));
            }
        }
    }
    frame_serial++;
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
    if (level < RETRO_LOG_WARN) return;
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    fprintf(stderr, "core: %s", buf);
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
        case RETRO_ENVIRONMENT_SET_MESSAGE:
        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
            return true;
        case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:
            *(unsigned *)data = 1;
            return true;
        default:
            return false;
    }
}

/* ---------------- Rebobinar ---------------- */

static uint8_t *rw_buf;
static size_t rw_state, rw_slots, rw_head, rw_count;
static int rw_interval = 2, rw_tick;
static bool rewind_on;

static void rewind_free(void) {
    free(rw_buf);
    rw_buf = NULL;
    rw_slots = rw_count = rw_head = 0;
}

static void rewind_setup(size_t budget_mb) {
    rewind_free();
    if (!game_loaded) return;
    rw_state = retro_serialize_size();
    if (!rw_state) return;
    rw_slots = budget_mb * 1024 * 1024 / rw_state;
    if (rw_slots < 8) { rw_slots = 0; return; }
    rw_buf = malloc(rw_slots * rw_state);
    if (!rw_buf) rw_slots = 0;
}

static void rewind_push(void) {
    if (!rw_buf || ++rw_tick < rw_interval) return;
    rw_tick = 0;
    if (retro_serialize_size() != rw_state) return;
    if (retro_serialize(rw_buf + rw_head * rw_state, rw_state)) {
        rw_head = (rw_head + 1) % rw_slots;
        if (rw_count < rw_slots) rw_count++;
    }
}

static bool rewind_pop(void) {
    if (!rw_buf || !rw_count) return false;
    rw_head = (rw_head + rw_slots - 1) % rw_slots;
    rw_count--;
    return retro_unserialize(rw_buf + rw_head * rw_state, rw_state);
}

/* ---------------- Rodar ---------------- */

static int ff_speed = 1, runahead;
static bool ff_hold, rewind_hold, ff_toggle;
static double acc, last_time;
static uint8_t *ra_buf;
static size_t ra_size;

static void run_one(bool video, bool audio) {
    frame_count++;
    if (runahead > 0 && !fast_forwarding) {
        size_t need = retro_serialize_size();
        if (need && need != ra_size) { free(ra_buf); ra_buf = malloc(need); ra_size = ra_buf ? need : 0; }
        if (ra_buf) {
            av_enable = audio ? 2 : 0;
            retro_run();
            if (retro_serialize(ra_buf, ra_size)) {
                for (int i = 1; i <= runahead; i++) {
                    av_enable = (video && i == runahead) ? 1 : 0;
                    retro_run();
                }
                retro_unserialize(ra_buf, ra_size);
            }
            av_enable = 3;
            return;
        }
    }
    av_enable = (video ? 1 : 0) | (audio ? 2 : 0);
    retro_run();
    av_enable = 3;
}

/* Um quadro da tela: now em segundos, hz = taxa da tela medida pelo JS. Retorna os quadros rodados. */
API int ox_step(double now, double hz) {
    if (!game_loaded) return 0;
    double fps = av.timing.fps > 1 ? av.timing.fps : 60.0;
    int frames = 1;
    if (fabs(hz - fps) / fps > 0.02) {
        if (last_time > 0) acc += (now - last_time) * fps;
        last_time = now;
        frames = (int)acc;
        if (frames > 3) { frames = 3; acc = 0; }
        acc -= frames;
        if (frames <= 0) return 0;
    } else {
        last_time = 0;
    }
    if (rewind_hold && rewind_on && rewind_pop()) {
        fast_forwarding = false;
        av_enable = 1;
        retro_run();
        av_enable = 3;
        rw_tick = 0;
        return 1;
    }
    fast_forwarding = ff_toggle || ff_hold;
    int n = fast_forwarding ? frames * (ff_speed > 1 ? ff_speed : 2) : frames;
    for (int i = 0; i < n; i++) {
        run_one(i == n - 1, !fast_forwarding);
        if (rewind_on) rewind_push();
    }
    return n;
}

/* Depois de uma pausa (menu): o relógio recomeça, sem correr atrás do tempo parado. */
API void ox_resync(void) { last_time = 0; acc = 0; }

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

static char *json_out;

/* O JSON fica válido até a próxima chamada. */
API const char *ox_options(void) {
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
    free(json_out);
    json_out = b.s;
    return json_out;
}

/* ---------------- API para o JS ---------------- */

API void ox_set_option(const char *k, const char *v) {
    option_t *o = find_option(k);
    if (o && valid_value(o, v) && strcmp(o->value ? o->value : "", v)) {
        free(o->value);
        o->value = strdup(v);
        opts_updated = true;
    }
    for (int i = 0; i < npending; i++) if (!strcmp(pending_keys[i], k)) { free(pending_vals[i]); pending_vals[i] = strdup(v); return; }
    if (npending < MAX_OPTS) { pending_keys[npending] = strdup(k); pending_vals[npending] = strdup(v); npending++; }
}

/* Só as opções, sem jogo (tela de configurações do console). O módulo é descartado depois. */
API const char *ox_probe(int lang) {
    language = (unsigned)lang;
    retro_set_environment(environment);
    return ox_options();
}

/* A ROM já foi escrita pelo JS no sistema de arquivos do módulo (path). */
API int ox_load(const char *path, int lang) {
    if (loaded) return 0;
    language = (unsigned)lang;
    loaded = true;
    retro_set_environment(environment);
    retro_set_video_refresh(video_refresh);
    retro_set_audio_sample(audio_sample);
    retro_set_audio_sample_batch(audio_batch);
    retro_set_input_poll(input_poll);
    retro_set_input_state(input_state);
    retro_init();
    if (!nopts) apply_pending();
    struct retro_system_info si;
    memset(&si, 0, sizeof(si));
    retro_get_system_info(&si);
    struct retro_game_info gi = {path, NULL, 0, NULL};
    if (!si.need_fullpath) {
        FILE *f = fopen(path, "rb");
        if (!f) return 0;
        fseek(f, 0, SEEK_END);
        long n = ftell(f);
        fseek(f, 0, SEEK_SET);
        rom_data = malloc(n > 0 ? (size_t)n : 1);
        rom_size = rom_data && n > 0 ? fread(rom_data, 1, (size_t)n, f) : 0;
        fclose(f);
        if (!rom_size) return 0;
        gi.data = rom_data;
        gi.size = rom_size;
    }
    if (!retro_load_game(&gi)) return 0;
    game_loaded = true;
    retro_get_system_av_info(&av);
    for (unsigned p = 0; p < 2; p++) retro_set_controller_port_device(p, RETRO_DEVICE_JOYPAD);
    return 1;
}

API void ox_unload(void) {
    if (game_loaded) retro_unload_game();
    if (loaded) retro_deinit();
    game_loaded = loaded = false;
    rewind_free();
    free(ra_buf); ra_buf = NULL; ra_size = 0;
    free(rom_data); rom_data = NULL; rom_size = 0;
}

API double ox_fps(void) { return av.timing.fps; }
API double ox_aspect(void) { return av.geometry.aspect_ratio; }

API uint32_t *ox_frame(void) { return frame; }
API int ox_frame_w(void) { return (int)frame_w; }
API int ox_frame_h(void) { return (int)frame_h; }
API int ox_frame_serial(void) { return frame_serial; }

API void ox_audio_config(int rate, int latency_ms) {
    out_rate = rate > 8000 ? (unsigned)rate : 48000;
    target_frames = (unsigned)(out_rate * (latency_ms > 0 ? latency_ms : 64) / 1000);
    if (target_frames < 512) target_frames = 512;
    if (target_frames > RING_FRAMES / 3) target_frames = RING_FRAMES / 3;
    ring_w = ring_r = 0;
    res_pos = 0;
}

/* Quanto som ainda espera no AudioWorklet (em quadros de som), contado pelo JS. */
API void ox_audio_queued(int frames) { queued_out = frames > 0 ? (unsigned)frames : 0; }

/* Copia até max quadros de som (L,R intercalados) para out e retorna quantos copiou. */
API int ox_audio_take(int16_t *out, int max) {
    unsigned n = ring_w - ring_r;
    if (n > (unsigned)max) n = (unsigned)max;
    for (unsigned i = 0; i < n; i++) {
        out[i * 2] = ring[((ring_r + i) % RING_FRAMES) * 2];
        out[i * 2 + 1] = ring[((ring_r + i) % RING_FRAMES) * 2 + 1];
    }
    ring_r += n;
    return (int)n;
}

API void ox_set_volume(double v) { volume = v < 0 ? 0 : v > 1 ? 1 : (float)v; }
API void ox_set_muted(int m) { muted = m != 0; }

API void ox_set_buttons(int port, int bits) { if (port >= 0 && port < 4) pad[port] = (unsigned)bits; }

API void ox_set_analog(int port, int stick, int x, int y) {
    if (port < 0 || port >= 4 || stick < 0 || stick > 1) return;
    analog[port][stick][0] = x;
    analog[port][stick][1] = y;
}

API void ox_set_hotkeys(int ff, int rw) { ff_hold = ff != 0; rewind_hold = rw != 0; }

API void ox_configure(int ffspeed, int ra, int rw, int rwmb) {
    ff_speed = ffspeed;
    runahead = ra < 0 ? 0 : ra > 2 ? 2 : ra;
    if ((rw != 0) != rewind_on || (rw && !rw_buf)) {
        rewind_on = rw != 0;
        if (rewind_on) rewind_setup((size_t)(rwmb > 0 ? rwmb : 64)); else rewind_free();
    }
}

API void ox_set_turbo(int mask, int period) {
    turbo_mask = (unsigned)mask;
    turbo_period = period >= 1 && period <= 30 ? (unsigned)period : 3;
}

API void ox_set_fast_forward(int on) { ff_toggle = on != 0; }
API int ox_fast_forward(void) { return ff_toggle; }

API void ox_reset(void) { if (game_loaded) retro_reset(); }

/* Estados: o JS lê e grava os bytes do buffer devolvido. */
static uint8_t *state_buf;
static size_t state_len;

API uint8_t *ox_serialize(void) {
    if (!game_loaded) return NULL;
    size_t n = retro_serialize_size();
    if (!n) return NULL;
    uint8_t *b = realloc(state_buf, n);
    if (!b) return NULL;
    state_buf = b;
    state_len = retro_serialize(state_buf, n) ? n : 0;
    return state_len ? state_buf : NULL;
}
API int ox_serialize_len(void) { return (int)state_len; }

/* O JS reserva com malloc, escreve o estado e chama; quem libera é o JS. */
API int ox_unserialize(const uint8_t *data, int len) {
    if (!game_loaded || !data || len <= 0) return 0;
    if (!retro_unserialize(data, (size_t)len)) return 0;
    rw_count = 0;   /* o rebobinar não volta para antes do estado carregado */
    return 1;
}

API uint8_t *ox_sram(void) { return game_loaded ? retro_get_memory_data(RETRO_MEMORY_SAVE_RAM) : NULL; }
API int ox_sram_size(void) { return game_loaded ? (int)retro_get_memory_size(RETRO_MEMORY_SAVE_RAM) : 0; }

API void ox_cheat_reset(void) { if (game_loaded) retro_cheat_reset(); }
API void ox_cheat_set(int index, int on, const char *code) { if (game_loaded) retro_cheat_set((unsigned)index, on != 0, code); }
