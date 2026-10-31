// Tela do jogo no PC: o papel do GameActivity.java e do MenuBridge.java da TV.
// Carrega o núcleo (WebAssembly, pc/core/web.c), desenha com WebGL 2 (filtros e ajustes de
// imagem), toca o som por um AudioWorklet (audio.js), lê teclado e controles (Gamepad API) e
// liga o menu do GameHub (menu.html, num iframe por cima), que fala com window.OnixMenu.
'use strict';
const PC = window.OnixPC;
const qs = new URLSearchParams(location.search);
const ID = qs.get('id'), MODE = qs.get('mode') || 'new';
const $ = s => document.querySelector(s);
const json = (s, d) => { try { return s ? JSON.parse(s) : d; } catch (_) { return d; } };

let INFO = json(PC.call('info', ID), null);
let S = INFO ? INFO.settings : {};
const ES = INFO && INFO.lang === 'es';
const tr = (pt, es) => (ES ? es : pt);
let M = null, loaded = false, failed = false, menuOpen = false, quitting = false;
let started = Date.now(), playedMs = 0;

/* RetroPad (libretro.h) */
const RB = { B: 0, Y: 1, SELECT: 2, START: 3, UP: 4, DOWN: 5, LEFT: 6, RIGHT: 7, A: 8, X: 9, L: 10, R: 11, L2: 12, R2: 13, L3: 14, R3: 15 };
const CONSOLE_BUTTONS = { nes: [RB.A, RB.B, RB.SELECT, RB.START], snes: [RB.A, RB.B, RB.X, RB.Y, RB.L, RB.R, RB.SELECT, RB.START],
  gba: [RB.A, RB.B, RB.L, RB.R, RB.SELECT, RB.START], md: [RB.B, RB.A, RB.Y, RB.X, RB.L, RB.R, RB.START, RB.SELECT] };

/* ---------------- Avisos ---------------- */
let toastTimer = 0;
function toast(text) {
  const el = $('#toast');
  el.textContent = text;
  el.hidden = false;
  el.style.animation = 'none'; void el.offsetWidth; el.style.animation = '';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2600);
}

/* ---------------- Imagem (WebGL 2) ----------------
   Um programa com os filtros da TV e os do PC; o quadro do núcleo (RGBA) vira uma textura. O
   "fundo ambiente" (a própria imagem, borrada, atrás do jogo) sai de reduções em etapas para
   64, 32 e 16 pixels. Sem mipmaps: no SwiftShader (PC sem placa de vídeo), generateMipmap
   estraga a imagem. */
const canvas = $('#screen');
const gl = canvas.getContext('webgl2', { alpha: true, antialias: false, premultipliedAlpha: true, preserveDrawingBuffer: false, powerPreference: 'high-performance' });
const FILTERS = { sharp: 0, pixel: 1, smooth: 2, crt: 3, crtcurve: 4, scanlines: 5, lcd: 6 };
const VS = `#version 300 es
in vec2 aPos; in vec2 aTex; out vec2 vTex;
void main(){ vTex = aTex; gl_Position = vec4(aPos, 0.0, 1.0); }`;
const FS = `#version 300 es
precision highp float;
in vec2 vTex; out vec4 o;
uniform sampler2D uTex, uPrev;
uniform vec2 uTexSize, uScale, uOut;
uniform int uMode, uBlend, uAmbient;   // uAmbient: 0 jogo, 1 fundo ambiente, 2 só copiar (redução)
uniform float uBright, uSat, uScan, uCurve, uVignette;
vec3 px(sampler2D t, vec2 uv){ return texture(t, uv).rgb; }
vec3 src(vec2 uv){
  vec3 c = px(uTex, uv);
  if (uBlend == 1) c = mix(c, px(uPrev, uv), 0.5);
  return c;
}
vec2 sharpUv(vec2 uv){
  vec2 t = uv * uTexSize; vec2 f = fract(t) - 0.5; vec2 r = 0.5 - 0.5 / uScale;
  vec2 q = (f - clamp(f, -r, r)) * uScale + 0.5; return (floor(t) + q) / uTexSize;
}
vec2 nearest(vec2 uv){ return (floor(uv * uTexSize) + 0.5) / uTexSize; }
vec3 crt(vec2 uv, bool mask){
  vec2 t = uv * uTexSize; vec2 s = vec2(uv.x, (floor(t.y) + 0.5) / uTexSize.y);
  vec3 c = src(s);
  float w = sin(3.14159 * fract(t.y)); float line = mix(1.0 - uScan, 1.0, w * w);
  vec3 m = vec3(1.0);
  if (mask) { float k = mod(floor(gl_FragCoord.x), 3.0); m = k < 1.0 ? vec3(1.0,.82,.82) : k < 2.0 ? vec3(.82,1.0,.82) : vec3(.82,.82,1.0); }
  vec2 g = 1.5 / uTexSize;
  vec3 glow = (px(uTex, uv + vec2(g.x, 0.0)) + px(uTex, uv - vec2(g.x, 0.0)) + px(uTex, uv + vec2(0.0, g.y)) + px(uTex, uv - vec2(0.0, g.y))) * 0.045;
  return (c * line * m + glow) * (1.0 + uScan * 0.5);
}
void main(){
  vec2 uv = vTex;
  if (uAmbient == 2) { o = vec4(px(uTex, uv), 1.0); return; }
  if (uAmbient == 1) {
    // As 3 reduções (framebuffers) deixam a imagem de cabeça para baixo; 5 amostras tiram os blocos.
    vec2 f = vec2(uv.x, 1.0 - uv.y), k = vec2(0.035);
    vec3 a = (px(uTex, f) * 2.0 + px(uTex, f + k) + px(uTex, f - k) + px(uTex, f + vec2(k.x, -k.y)) + px(uTex, f - vec2(k.x, -k.y))) / 6.0;
    float v = 1.0 - smoothstep(0.35, 0.95, length(uv - 0.5));
    o = vec4(a * (0.32 + 0.18 * v), 1.0); return;
  }
  vec3 c;
  if (uMode == 4) {
    vec2 d = uv * 2.0 - 1.0; float k = uCurve * 0.035;
    d *= 1.0 + k * dot(d, d);
    uv = d * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) { o = vec4(0.0, 0.0, 0.0, 1.0); return; }
    vec2 e = smoothstep(0.0, 0.012, uv) * smoothstep(0.0, 0.012, 1.0 - uv);
    c = crt(uv, true) * e.x * e.y;
  } else if (uMode == 3) c = crt(uv, true);
  else if (uMode == 5) c = crt(sharpUv(uv), false);
  else if (uMode == 6) {
    vec2 t = uv * uTexSize; c = src(nearest(uv));
    vec2 f = fract(t); float e = 1.0 / max(uScale.x, 1.0); vec2 g = step(e, f) * step(f, vec2(1.0 - e * 0.5));
    c *= mix(0.72, 1.0, g.x * g.y) * 1.08;
  }
  else if (uMode == 1) c = src(nearest(uv));
  else if (uMode == 2) c = src(uv);
  else c = src(sharpUv(uv));
  float l = dot(c, vec3(0.299, 0.587, 0.114));
  c = mix(vec3(l), c, uSat) * uBright;
  if (uVignette > 0.0) c *= 1.0 - uVignette * smoothstep(0.45, 0.9, length(vTex - 0.5));
  o = vec4(c, 1.0);
}`;
let prog, vbo, tex = [], texW = 0, texH = 0, cur = 0, lastSerial = -1, U = {}, amb = [], ambDirty = true;
const AMB = [64, 32, 16];
/* Placa de vídeo reiniciada (driver atualizado, outra tela): o WebGL volta sozinho. */
canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); PC.log('WebGL perdido'); });
canvas.addEventListener('webglcontextrestored', () => { glSetup(); texW = texH = 0; lastSerial = -1; });
function glSetup() {
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) PC.log('shader: ' + gl.getShaderInfoLog(s)); return s; };
  prog = gl.createProgram();
  gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS));
  gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS));
  gl.bindAttribLocation(prog, 0, 'aPos');
  gl.bindAttribLocation(prog, 1, 'aTex');
  gl.linkProgram(prog);
  for (const n of ['uTex', 'uPrev', 'uTexSize', 'uScale', 'uOut', 'uMode', 'uBlend', 'uAmbient', 'uBright', 'uSat', 'uScan', 'uCurve', 'uVignette']) U[n] = gl.getUniformLocation(prog, n);
  vbo = gl.createBuffer();
  tex = [gl.createTexture(), gl.createTexture()];
  amb = AMB.map(n => {
    const t = gl.createTexture(), fb = gl.createFramebuffer();
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, n, n, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    for (const [k, v] of [[gl.TEXTURE_MIN_FILTER, gl.LINEAR], [gl.TEXTURE_MAG_FILTER, gl.LINEAR], [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, k, v);
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
    return { n, t, fb };
  });
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
}
/* Aquece o programa: o Windows só compila o shader (para DirectX) no primeiro desenho, e isso
   chegava a congelar a imagem por quase 3 s logo depois de o jogo abrir. Desenha uma vez, com
   uma textura de 1 pixel, ainda na tela "carregando" (que gira sozinha), e espera terminar. */
function glWarmup() {
  const t = gl.createTexture();
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4));
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.useProgram(prog);
  gl.uniform1i(U.uTex, 0); gl.uniform1i(U.uPrev, 1);
  gl.uniform2f(U.uTexSize, 1, 1); gl.uniform2f(U.uScale, 1, 1); gl.uniform2f(U.uOut, 1, 1);
  gl.uniform1i(U.uAmbient, 0); gl.uniform1i(U.uMode, 0); gl.uniform1i(U.uBlend, 0);
  for (const fb of [amb[amb.length - 1].fb, null]) {   // na textura do fundo ambiente e na tela
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
    gl.viewport(0, 0, 1, 1);
    quad(1, 1);
  }
  gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4));   // espera a placa
  gl.clearColor(0, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, null);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, null);
  gl.deleteTexture(t);
}
/* Fundo ambiente: o quadro reduzido em etapas (cada uma com filtro bilinear) até 16 x 16. */
function ambient() {
  let src = tex[cur];
  gl.uniform1i(U.uAmbient, 2);
  gl.uniform1i(U.uBlend, 0);
  for (const a of amb) {
    gl.bindFramebuffer(gl.FRAMEBUFFER, a.fb);
    gl.viewport(0, 0, a.n, a.n);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, src);
    quad(1, 1);
    src = a.t;
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  ambDirty = false;
}
function upload() {
  const serial = M._ox_frame_serial();
  const w = M._ox_frame_w(), h = M._ox_frame_h(), ptr = M._ox_frame();
  if (serial === lastSerial || !w || !h || !ptr) return;
  lastSerial = serial;
  if (S.frameBlend) cur ^= 1;
  const data = new Uint8Array(M.HEAPU8.buffer, ptr, w * h * 4);
  gl.bindTexture(gl.TEXTURE_2D, tex[cur]);
  if (w !== texW || h !== texH) {
    for (const t of tex) {
      gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, data);
    }
    texW = w; texH = h;
  } else {
    gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, data);
  }
  ambDirty = true;
}
function quad(x, y) {
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-x, -y, 0, 1, x, -y, 1, 1, -x, y, 0, 0, x, y, 1, 0]), gl.STREAM_DRAW);
  gl.enableVertexAttribArray(0);
  gl.enableVertexAttribArray(1);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 16, 0);
  gl.vertexAttribPointer(1, 2, gl.FLOAT, false, 16, 8);
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
}
function draw() {
  const dpr = window.devicePixelRatio || 1;
  const vw = Math.round(canvas.clientWidth * dpr), vh = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== vw || canvas.height !== vh) { canvas.width = vw; canvas.height = vh; }
  if (!texW) {
    gl.viewport(0, 0, vw, vh);
    gl.clearColor(0, 0, 0, S.backdrop === 'glass' ? 0 : 1);
    gl.clear(gl.COLOR_BUFFER_BIT);
    return;
  }
  const mode = FILTERS[S.filter] !== undefined ? FILTERS[S.filter] : 0;
  const smoothTex = mode === 0 || mode === 2 || mode === 5 || mode === 3 || mode === 4;
  for (const t of tex) {
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, smoothTex ? gl.LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, smoothTex ? gl.LINEAR : gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  }
  gl.useProgram(prog);
  gl.uniform1i(U.uTex, 0);
  gl.uniform1i(U.uPrev, 1);
  gl.uniform2f(U.uTexSize, texW, texH);
  const useAmbient = S.backdrop === 'ambient';
  if (useAmbient && ambDirty) ambient();
  gl.viewport(0, 0, vw, vh);
  gl.clearColor(0, 0, 0, S.backdrop === 'glass' ? 0 : 1);
  gl.clear(gl.COLOR_BUFFER_BIT);
  // Proporção (as mesmas da TV)
  const core = M._ox_aspect();
  const aspect = S.aspect === '4:3' ? 4 / 3 : S.aspect === '16:9' ? 16 / 9 : S.aspect === 'pixel' ? texW / texH : (core > 0 ? core : texW / texH);
  let w, h;
  if (S.scale === 'stretch') { w = vw; h = vh; } else {
    h = vh; w = h * aspect;
    if (w > vw) { w = vw; h = w / aspect; }
    if (S.scale === 'integer') { const k = Math.floor(h / texH); if (k >= 1) { h = k * texH; w = h * aspect; } if (w > vw) { w = vw; h = w / aspect; } }
  }
  if (useAmbient && (w < vw - 2 || h < vh - 2)) {
    gl.uniform1i(U.uAmbient, 1);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, amb[amb.length - 1].t);
    // Atrás: a imagem cobrindo a janela toda (cortada, sem esticar), bem borrada e escura.
    const cover = Math.max(vw / w, vh / h);
    quad(w * cover / vw, h * cover / vh);
  }
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, tex[cur]);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, tex[cur ^ 1]);
  gl.uniform1i(U.uAmbient, 0);
  gl.uniform1i(U.uMode, mode);
  gl.uniform1i(U.uBlend, S.frameBlend ? 1 : 0);
  gl.uniform2f(U.uScale, Math.max(1, Math.floor(w / texW)), Math.max(1, Math.floor(h / texH)));
  gl.uniform2f(U.uOut, w, h);
  gl.uniform1f(U.uBright, (S.brightness || 100) / 100);
  gl.uniform1f(U.uSat, (S.saturation === undefined ? 100 : S.saturation) / 100);
  gl.uniform1f(U.uScan, (S.scanStrength || 40) / 100);
  gl.uniform1f(U.uCurve, S.curvature === undefined ? 2 : S.curvature);
  gl.uniform1f(U.uVignette, S.vignette ? 0.45 : 0);
  quad(w / vw, h / vh);
}

/* ---------------- Som ---------------- */
let actx = null, out = null, audioBuf = 0, audioView = null;
async function audioStart() {
  try {
    // iPhone: o som vem pronto da página de fora, criado pelo toque em Jogar.
    actx = (PC.audioContext && PC.audioContext()) || new AudioContext({ latencyHint: 'interactive' });
    await actx.audioWorklet.addModule('audio.js');
    out = new (PC.AudioNode || AudioWorkletNode)(actx, 'onix-out', { numberOfInputs: 0, outputChannelCount: [2] });
    out.port.onmessage = e => M && M._ox_audio_queued(e.data);
    out.connect(actx.destination);
    M._ox_audio_config(actx.sampleRate, S.latency || 64);
    audioBuf = M._malloc(8192 * 4);
  } catch (e) { PC.log('som: ' + e); }
}
function audioPump() {
  if (!out || !audioBuf) return;
  const n = M._ox_audio_take(audioBuf, 8192);
  if (!n) return;
  const s = new Int16Array(M.HEAP16.buffer, audioBuf, n * 2), f = new Float32Array(n * 2);
  for (let i = 0; i < f.length; i++) f[i] = s[i] / 32768;
  out.port.postMessage(f, [f.buffer]);
}

/* ---------------- Controles ----------------
   Teclado = jogador 1. Cada controle vira um jogador: o definido em Configurações → Controles
   fica sempre na mesma vaga; os outros entram na primeira livre. R2 segurado acelera e L2
   rebobina (nenhum dos 4 consoles usa esses dois). Select + Start ou o botão do meio: menu. */
const PAD_DEFAULT = [RB.B, RB.A, RB.Y, RB.X, RB.L, RB.R, RB.L2, RB.R2, RB.SELECT, RB.START, RB.L3, RB.R3, RB.UP, RB.DOWN, RB.LEFT, RB.RIGHT];
const KEY_DEFAULT = { ArrowUp: RB.UP, ArrowDown: RB.DOWN, ArrowLeft: RB.LEFT, ArrowRight: RB.RIGHT, KeyX: RB.A, KeyZ: RB.B, KeyS: RB.X, KeyA: RB.Y,
  KeyQ: RB.L, KeyW: RB.R, Enter: RB.START, ShiftRight: RB.SELECT, Space: RB.SELECT, Tab: RB.R2, Backspace: RB.L2 };
let remap = {}, players = {}, ports = new Map();
const keyBits = [0, 0, 0, 0], padBits = [0, 0, 0, 0];
let ffHold = false, rwHold = false, capture = false;
let touchBits = 0, touchFast = false, pad = null, editing = false;   // controle na tela (iPhone)

function readRemap() { remap = S.remap || {}; players = json(PC.call('playersSaved'), {}); }
function mapped(code, def) {
  if (code && Object.prototype.hasOwnProperty.call(remap, code)) return remap[code];
  return def;
}
function portFor(g) {
  if (ports.has(g.index)) return ports.get(g.index);
  let fixed = -1;
  for (const n of ['1', '2']) if (players[n] && players[n].d === g.id) fixed = Number(n) - 1;
  const used = new Set(ports.values());
  for (const n of ['1', '2']) if (players[n] && players[n].d !== g.id && navigator.getGamepads().some(o => o && o.id === players[n].d)) used.add(Number(n) - 1);
  let p = fixed;
  if (p < 0) { p = 0; while (p < 3 && used.has(p)) p++; }
  for (const [k, v] of ports) if (v === p) ports.delete(k);
  ports.set(g.index, p);
  if (p > 0) toast(tr('Jogador ', 'Jugador ') + (p + 1) + ': ' + PC.padName(g));
  return p;
}
function push() {
  if (!M || !loaded) return;
  for (let p = 0; p < 4; p++) M._ox_set_buttons(p, keyBits[p] | padBits[p] | (p === 0 ? touchBits : 0));
  M._ox_set_hotkeys(ffHold ? 1 : 0, rwHold ? 1 : 0);
}
const usesTrigger = b => (CONSOLE_BUTTONS[INFO.console] || []).includes(b);
let keyFf = false, keyRw = false, padFf = false, padRw = false;
function hotkeys() {
  ffHold = keyFf || padFf || touchFast;
  const want = keyRw || padRw;
  if (want && !rwHold && !S.rewind) toast(tr('Rebobinar está desligado (Configurações → Jogo).', 'Rebobinar está apagado (Configuración → Juego).'));
  rwHold = want && !!S.rewind;
}

window.addEventListener('keydown', e => {
  if (menuOpen) return;
  if (e.code === 'Escape' || e.code === 'F1') { e.preventDefault(); openMenu(''); return; }
  if (e.code === 'F11') return;
  e.preventDefault();
  if (e.repeat) return;
  const b = mapped(PC.keyCode(e.code), KEY_DEFAULT[e.code] === undefined ? -1 : KEY_DEFAULT[e.code]);
  if (b === RB.R2 && !usesTrigger(RB.R2)) { keyFf = true; hotkeys(); push(); return; }
  if (b === RB.L2 && !usesTrigger(RB.L2)) { keyRw = true; hotkeys(); push(); return; }
  if (b >= 0) { keyBits[0] |= 1 << b; push(); }
});
window.addEventListener('keyup', e => {
  if (menuOpen) return;
  const b = mapped(PC.keyCode(e.code), KEY_DEFAULT[e.code] === undefined ? -1 : KEY_DEFAULT[e.code]);
  if (b === RB.R2 && !usesTrigger(RB.R2)) { keyFf = false; hotkeys(); push(); return; }
  if (b === RB.L2 && !usesTrigger(RB.L2)) { keyRw = false; hotkeys(); push(); return; }
  if (b >= 0) { keyBits[0] &= ~(1 << b); push(); }
});
window.addEventListener('blur', releaseAll);

const pads = () => Array.from(navigator.getGamepads()).filter(g => g && g.connected && g.mapping === 'standard');
let padsKnown = '', menuHeld = {};
const axis = (v, dz) => (Math.abs(v) < dz ? 0 : Math.max(-32767, Math.min(32767, Math.round(v * 32767))));
function pollPads() {
  const list = pads();
  const ids = list.map(g => g.index + g.id).join('|');
  if (ids !== padsKnown) {
    const before = padsKnown ? padsKnown.split('|').length : 0;
    padsKnown = ids;
    for (const k of Array.from(ports.keys())) if (!list.some(g => g.index === k)) {
      ports.delete(k);
      if (!menuOpen && loaded) openMenu('disconnected');
    }
    if (list.length > before && list.length) toast(tr('Controle conectado: ', 'Control conectado: ') + PC.padName(list[list.length - 1]));
    menuEmit('gamepads', padList());
    arrangePad();
  }
  if (menuOpen) { menuPads(list); return; }
  const dz = (S.deadzone || 25) / 100;
  padBits.fill(0);
  padFf = padRw = false;
  let selStart = false;
  for (const g of list) {
    let bits = 0, any = false;
    g.buttons.forEach((btn, i) => {
      if (!btn.pressed) return;
      any = true;
      if (i === 16) { selStart = true; return; }
      const b = mapped(PC.padCodes[i], PAD_DEFAULT[i] === undefined ? -1 : PAD_DEFAULT[i]);
      if (b === RB.R2 && !usesTrigger(RB.R2)) padFf = true;
      else if (b === RB.L2 && !usesTrigger(RB.L2)) padRw = true;
      else if (b >= 0) bits |= 1 << b;
    });
    if ((bits & (1 << RB.SELECT)) && (bits & (1 << RB.START))) selStart = true;
    const [x = 0, y = 0, rx = 0, ry = 0] = g.axes;
    if (!any && !bits && Math.abs(x) < 0.5 && Math.abs(y) < 0.5 && !ports.has(g.index)) continue;
    const port = portFor(g);
    if (S.stickDpad !== false) {
      const t = Math.max(0.5, dz);
      if (x < -t) bits |= 1 << RB.LEFT;
      if (x > t) bits |= 1 << RB.RIGHT;
      if (y < -t) bits |= 1 << RB.UP;
      if (y > t) bits |= 1 << RB.DOWN;
    }
    M._ox_set_analog(port, 0, axis(x, dz), axis(y, dz));
    M._ox_set_analog(port, 1, axis(rx, dz), axis(ry, dz));
    padBits[port] |= bits;
  }
  if (selStart) { padBits.fill(0); openMenu(''); return; }
  hotkeys();
  push();
}
function releaseAll() {
  keyBits.fill(0); padBits.fill(0);
  keyFf = keyRw = padFf = padRw = ffHold = rwHold = touchFast = false;
  touchBits = 0;
  if (pad) pad.release();
  if (M && loaded) { for (let p = 0; p < 4; p++) { M._ox_set_analog(p, 0, 0, 0); M._ox_set_analog(p, 1, 0, 0); } push(); }
}
function padList() {
  return pads().map(g => {
    const o = { id: g.index, name: PC.padName(g) };
    for (const n of ['1', '2']) if (players[n] && players[n].d === g.id) o.player = Number(n);
    return o;
  });
}

/* ---------------- Ciclo ----------------
   Um passo por quadro da tela. A taxa da tela é medida (60, 75, 144 Hz…): se for diferente do
   jogo, o núcleo segue o relógio. */
let hz = 60, last = 0, intervals = [], frames = 0, fpsAt = 0, sramTick = 0;
function loop(t) {
  requestAnimationFrame(loop);
  if (last) {
    intervals.push(t - last);
    if (intervals.length >= 120) {
      const s = intervals.slice().sort((a, b) => a - b);
      hz = 1000 / s[60];
      intervals = [];
    }
  }
  last = t;
  pollPads();
  if (!loaded || menuOpen || editing) return;
  frames += M._ox_step(t / 1000, hz) ? 1 : 0;
  audioPump();
  upload();
  draw();
  if (++sramTick >= 600) { sramTick = 0; saveSram(); }
  if (t - fpsAt >= 1000) {
    if (S.showFps) { $('#fps').hidden = false; $('#fps').textContent = frames + ' FPS'; } else $('#fps').hidden = true;
    frames = 0; fpsAt = t;
  }
}

/* ---------------- Arquivos ---------------- */
let sramLast = null;
async function loadSram() {
  const data = await PC.async('readSram', ID);
  const size = M._ox_sram_size(), ptr = M._ox_sram();
  if (!size || !ptr) return;
  if (data) M.HEAPU8.set(new Uint8Array(data).subarray(0, size), ptr);
  sramLast = M.HEAPU8.slice(ptr, ptr + size);
}
/* Grava a memória do cartucho só quando ela mudou. */
async function saveSram() {
  if (!M || !loaded) return;
  const size = M._ox_sram_size(), ptr = M._ox_sram();
  if (!size || !ptr) return;
  const now = M.HEAPU8.slice(ptr, ptr + size);
  if (sramLast && sramLast.length === now.length && sramLast.every((v, i) => v === now[i])) return;
  sramLast = now;
  await PC.async('writeSram', ID, now);
}
function snapshot() {
  return new Promise(res => {
    const w = M._ox_frame_w(), h = M._ox_frame_h(), ptr = M._ox_frame();
    if (!w || !h || !ptr) return res(null);
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    const ctx = c.getContext('2d');
    ctx.putImageData(new ImageData(new Uint8ClampedArray(M.HEAPU8.slice(ptr, ptr + w * h * 4).buffer), w, h), 0, 0);
    const t = document.createElement('canvas');
    t.width = 320; t.height = Math.max(1, Math.round(h * 320 / w));
    const tc = t.getContext('2d');
    tc.imageSmoothingQuality = 'high';
    tc.drawImage(c, 0, 0, t.width, t.height);
    t.toBlob(b => (b ? b.arrayBuffer().then(a => res(new Uint8Array(a))) : res(null)), 'image/jpeg', 0.82);
  });
}
async function saveState(slot) {
  const ptr = M._ox_serialize(), n = M._ox_serialize_len();
  if (!ptr || !n) return false;
  const data = M.HEAPU8.slice(ptr, ptr + n);
  return PC.async('writeState', ID, slot, data, await snapshot());
}
async function loadState(slot) {
  const data = await PC.async('readState', ID, slot);
  if (!data) return false;
  const bytes = new Uint8Array(data), ptr = M._malloc(bytes.length);
  M.HEAPU8.set(bytes, ptr);
  const done = M._ox_unserialize(ptr, bytes.length) === 1;
  M._free(ptr);
  return done;
}

/* ---------------- Configurações ---------------- */
function setOption(k, v) {
  const a = M.stringToNewUTF8(k), b = M.stringToNewUTF8(String(v));
  M._ox_set_option(a, b);
  M._free(a); M._free(b);
}
function applySettings() {
  M._ox_configure(S.ffspeed || 3, S.runahead || 0, S.rewind ? 1 : 0, 64);
  M._ox_set_volume((S.volume === undefined ? 100 : S.volume) / 100);
  M._ox_set_turbo(S.turbo || 0, S.turboRate || 3);
  document.body.classList.toggle('glass', S.backdrop === 'glass');
  if (!S.showFps) $('#fps').hidden = true;
}
function applyCheats() {
  M._ox_cheat_reset();
  let n = 0;
  for (const c of json(PC.call('cheats', ID), [])) {
    if (!c.on) continue;
    const p = M.stringToNewUTF8(c.code);
    M._ox_cheat_set(n++, 1, p);
    M._free(p);
  }
}
function reloadSettings() {
  S = json(PC.call('effective', ID), S);
  readRemap();
  applySettings();
  for (const [k, v] of Object.entries(S.core || {})) setOption(k, v);
}

/* ---------------- Carregar ---------------- */
async function start() {
  if (!INFO) { PC.call('quit', ID, 0); return; }
  $('#loadName').textContent = INFO.name;
  readRemap();
  try {
    if (!gl) throw new Error('sem WebGL 2');
    glSetup();
    try { glWarmup(); } catch (e) { PC.log('aquecer: ' + e); }
    await new Promise((ok, fail) => { const s = document.createElement('script'); s.src = 'cores/' + INFO.core + '.js'; s.onload = ok; s.onerror = fail; document.head.append(s); });
    M = await window.OnixCore({ locateFile: f => 'cores/' + f, print: () => { }, printErr: m => PC.log(INFO.core + ': ' + m) });
    const rom = await PC.async('rom', ID);
    if (!rom) throw new Error('jogo não encontrado');
    for (const d of ['/system', '/saves']) M.FS.mkdir(d);
    const file = '/game.' + (INFO.ext || 'bin');
    M.FS.writeFile(file, new Uint8Array(rom));
    for (const [k, v] of Object.entries(S.core || {})) setOption(k, v);
    const p = M.stringToNewUTF8(file);
    const okLoad = M._ox_load(p, ES ? 3 : 7);
    M._free(p);
    if (!okLoad) throw new Error('o núcleo recusou o jogo');
    PC.call('saveOptions', INFO.console, M.UTF8ToString(M._ox_options()));
    await loadSram();
    applySettings();
    applyCheats();
    if (MODE === 'resume') await loadState('auto');
    else if (/^[1-4]$/.test(MODE)) await loadState(MODE);
    await audioStart();
    loaded = true;
    $('#loading').classList.add('gone');
    if (!pads().length && MODE !== 'resume' && !PC.mobile) toast(tr('Teclado: setas, X = A, Z = B, Enter = Start. Esc abre o menu.', 'Teclado: flechas, X = A, Z = B, Enter = Start. Esc abre el menú.'));
  } catch (e) {
    PC.log('carregar: ' + (e && e.stack || e));
    failed = true;
    $('#loading').classList.add('gone');
    openMenu('error');
  }
}

/* ---------------- Menu (MenuBridge.java) ---------------- */
const frame = $('#menu');
let OPTIONS = '{"categories":[],"options":[]}';
function menuEmit(type, data) {
  const w = frame.contentWindow;
  if (w && w.onHub) w.onHub(type, data === undefined ? null : data);
}
function statesList() {
  const have = json(PC.call('states', ID), []);
  return ['auto', '1', '2', '3', '4'].map(slot => Object.assign({ slot }, have.find(s => s.slot === slot) || {}));
}
window.OnixMenu = {
  info: () => JSON.stringify({
    id: ID, name: INFO.name, console: INFO.console, profile: 'default', kids: false, lang: INFO.lang, loaded, failed,
    ff: M && loaded ? !!M._ox_fast_forward() : false, settings: S, gamepads: padList(), sounds: true, mobile: !!PC.mobile, pc: !PC.mobile, motion: true,
    states: statesList(), cover: INFO.cover,
  }),
  resume: () => setTimeout(closeMenu, 0),
  quit: () => setTimeout(quit, 0),
  saveState: slot => {
    if (!/^[1-4]$/.test(slot) || !loaded) return false;
    saveState(slot).then(done => {
      if (done) toast(tr('Estado salvo no espaço ' + slot, 'Estado guardado en el espacio ' + slot));
      menuEmit('saved', { slot, ok: !!done, states: statesList() });
    });
    return true;
  },
  loadState: slot => {
    if (!/^(auto|[1-4])$/.test(slot) || !loaded) return false;
    loadState(slot).then(done => {
      menuEmit('loaded', { slot, ok: done });
      if (done) { toast(tr('Estado carregado', 'Estado cargado')); closeMenu(); }
    });
    return true;
  },
  reset: () => { if (loaded) { M._ox_reset(); setTimeout(closeMenu, 0); } },
  setFastForward: on => { if (loaded) M._ox_set_fast_forward(on ? 1 : 0); },
  setConfig: (scope, key, value) => {
    const done = PC.call('setConfig', ID, scope, key, value);
    if (done && loaded) {
      if (value === null && key.startsWith('core.')) {
        // Voltou ao padrão: o núcleo precisa receber o valor padrão de novo.
        const o = (json(OPTIONS, {}).options || []).find(x => x.key === key.slice(5));
        if (o) setOption(o.key, o.default);
      }
      reloadSettings();
    }
    return done;
  },
  editTouch: () => { if (pad) setTimeout(editPad, 0); return !!pad; },
  captureKey: on => { capture = !!on; },
  effective: () => JSON.stringify(S),
  coreOptions: () => { if (loaded) OPTIONS = M.UTF8ToString(M._ox_options()); setTimeout(() => menuEmit('options', json(OPTIONS, {})), 0); },
  cheats: () => PC.call('cheats', ID),
  setCheats: s => { const done = PC.call('setCheats', ID, s); if (done && loaded) applyCheats(); return done; },
  gamepads: () => JSON.stringify(padList()),
  openBluetooth: () => PC.hub('openBluetooth'),
  fullscreen: () => PC.call('fullscreen'),
  setGameFullscreen: on => { const done = PC.call('setGameFullscreen', !!on); S = json(PC.call('effective', ID), S); return done; },
};
function openMenu(reason) {
  if (quitting || menuOpen) return;
  menuOpen = true;
  playedMs += Date.now() - started;
  releaseAll();
  if (actx) actx.suspend();
  if (out) out.port.postMessage('clear');
  saveSram();
  document.body.classList.add('menu');
  frame.hidden = false;
  frame.focus();
  frame.contentWindow.focus();
  menuEmit('open', reason || '');
}
function closeMenu() {
  if (failed) { quit(); return; }
  if (!menuOpen) return;
  menuOpen = false;
  capture = false;
  frame.hidden = true;
  document.body.classList.remove('menu');
  window.focus();
  started = Date.now();
  if (M && loaded) M._ox_resync();
  if (actx) actx.resume();
}
async function quit() {
  if (quitting) return;
  quitting = true;
  if (!menuOpen) playedMs += Date.now() - started;
  if (loaded) {
    await saveSram();
    if (S.autosave !== false) await saveState('auto');
    M._ox_unload();
    loaded = false;
  }
  if (actx) actx.close();
  PC.call('quit', ID, playedMs / 1000);
}

/* No menu: controle anda pelas opções (como no hub), teclas e botões para "Atribuir botões". */
const NAV = { 0: 'Enter', 9: 'Enter', 1: 'Escape', 4: 'PageUp', 5: 'PageDown', 12: 'ArrowUp', 13: 'ArrowDown', 14: 'ArrowLeft', 15: 'ArrowRight' };
function menuKey(k, repeat) {
  const doc = frame.contentDocument;
  if (doc) doc.dispatchEvent(new (frame.contentWindow.KeyboardEvent)('keydown', { key: k, code: k, bubbles: true, cancelable: true, repeat }));
}
function menuPads(list) {
  const now = performance.now();
  for (const g of list) {
    g.buttons.forEach((b, i) => {
      const k = g.index + ':' + i;
      if (b.pressed && !menuHeld[k]) {
        menuHeld[k] = { next: now + 400 };
        if (capture && PC.padCodes[i]) { capture = false; menuEmit('key', { code: PC.padCodes[i], name: PC.padNames[i] }); return; }
        if (i === 16) { menuEmit('back'); return; }
        if (NAV[i]) menuKey(NAV[i], false);
      } else if (b.pressed && /^Arrow/.test(NAV[i] || '') && now >= menuHeld[k].next) {
        menuHeld[k].next = now + 110;
        menuKey(NAV[i], true);
      } else if (!b.pressed) delete menuHeld[k];
    });
    const dirs = { ArrowLeft: g.axes[0] < -0.6, ArrowRight: g.axes[0] > 0.6, ArrowUp: g.axes[1] < -0.6, ArrowDown: g.axes[1] > 0.6 };
    for (const [d, on] of Object.entries(dirs)) {
      const k = g.index + ':' + d;
      if (on && !menuHeld[k]) { menuHeld[k] = { next: now + 400 }; menuKey(d, false); }
      else if (on && now >= menuHeld[k].next) { menuHeld[k].next = now + 110; menuKey(d, true); }
      else if (!on) delete menuHeld[k];
    }
  }
}
frame.addEventListener('load', () => {
  // Teclado no menu: "Atribuir botões" pega a próxima tecla.
  frame.contentWindow.addEventListener('keydown', e => {
    if (!capture || e.repeat || e.code === 'Escape') return;
    const code = PC.keyCode(e.code);
    if (!code) return;
    e.preventDefault(); e.stopImmediatePropagation();
    capture = false;
    menuEmit('key', { code, name: PC.keyName(e.code, e.key) });
  }, true);
});

/* ---------------- Controle na tela (iPhone) ----------------
   Como no celular Android: em pé, o jogo em cima e o controle embaixo; deitado, o controle por
   cima do jogo. Com um controle Bluetooth, ele some e o jogo ocupa a tela; aí um toque abre o menu. */
function arrangePad() {
  if (!pad) return;
  const el = $('#pad'), w = innerWidth, h = innerHeight, show = !pads().length;
  el.hidden = !show;
  if (!show) pad.release();
  if (show && h > w) {
    const gh = Math.min(w * 3 / 4, h / 2), top = Math.max(0, (h / 2 - gh) / 3);
    canvas.style.top = top + 'px'; canvas.style.height = gh + 'px';
    el.style.top = (gh + top) + 'px'; el.style.height = (h - gh - top) + 'px';
    pad.resize(w, h - gh - top);
  } else {
    canvas.style.top = '0'; canvas.style.height = '100%';
    el.style.top = '0'; el.style.height = '100%';
    if (show) pad.resize(w, h);
  }
}
function editPad() {
  closeMenu();
  if (actx) actx.suspend();
  editing = true;
  pad.setEditing(true);
}
let wake = null;
function keepAwake() { if (navigator.wakeLock && !document.hidden) navigator.wakeLock.request('screen').then(l => { wake = l; }).catch(() => { }); }
if (window.OnixPad) {
  pad = new window.OnixPad.Pad($('#pad'), INFO ? INFO.console : 'nes', {
    buttons: bits => { touchBits = bits; push(); },
    fast: on => { touchFast = on; hotkeys(); push(); },
    menu: () => openMenu(''),
    save: PC.touchSave,
    done: () => {
      editing = false;
      started = Date.now();
      if (M && loaded) M._ox_resync();
      if (actx) actx.resume();
    },
  }, PC.touchLayout(), ES);
  addEventListener('resize', arrangePad);
  window.addEventListener('orientationchange', () => {
    setTimeout(arrangePad, 60);
    setTimeout(arrangePad, 220);
  });
  arrangePad();
  keepAwake();
  canvas.addEventListener('pointerup', () => { if ($('#pad').hidden && loaded && !menuOpen) openMenu(''); });
  // O iOS pausa o som numa ligação ou ao voltar para o app: o próximo toque traz de volta.
  document.addEventListener('pointerdown', () => { if (actx && actx.state !== 'running' && actx.state !== 'closed' && !menuOpen && !editing) actx.resume(); });
}

// Janela minimizada: pausa no menu, como o botão Início da TV. No iPhone o sistema pode fechar o
// app em segundo plano, então já guarda onde parou.
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) { if (pad) keepAwake(); return; }
  if (loaded && !menuOpen) openMenu('paused');
  if (PC.mobile && loaded && S.autosave !== false) saveState('auto');
});
window.addEventListener('error', e => PC.log('erro: ' + e.message));

requestAnimationFrame(loop);
start();
