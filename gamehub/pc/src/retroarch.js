// Consoles pesados (N64, DS, PSP, GameCube, Wii e PS2) pelo RetroArch: os emuladores deles desenham
// pela placa de vídeo, o que o motor WebAssembly do GameHub não faz. O GameHub continua sendo a tela
// de tudo (biblioteca, capas, perfis); ao abrir um desses jogos ele chama o RetroArch em tela cheia,
// direto no jogo, e volta quando o jogo fecha.
// O RetroArch (enxuto) e cada emulador são baixados só na primeira vez, de uma release fixa do
// Cobalt GameHub (motor-retroarch-N): versões travadas, que não mudam da noite para o dia.
// Tudo fica em %APPDATA%\Cobalt GameHub\retroarch (app, cores, system, saves, states).
'use strict';
const { net, shell } = require('electron');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const zip = require('./zip');

const ENGINE = 'motor-retroarch-1';
const BASE = process.env.COBALT_ENGINE_BASE || 'https://github.com/ruan2311/cobalt-gamehub/releases/download/' + ENGINE + '/';
// Arquivos de sistema que alguns emuladores precisam (fontes, configurações de jogos).
const SYSTEM = { dolphin: 'system-Dolphin.zip', ppsspp: 'system-PPSSPP.zip' };
const MAX_DOWNLOAD = 400 * 1024 * 1024;

let root = '', running = null, cheevosAccount = () => null;
/** A conta do RetroAchievements ({user, token}) para o RetroArch entrar sozinho; null = desligado. */
const setCheevos = f => { cheevosAccount = f; };
const dir = (...a) => path.join(root, ...a);
const exe = () => dir('app', 'CobaltMotor.exe');   // o RetroArch com o nome e o ícone do GameHub (build-motor.js)
function init(userData) { root = path.join(userData, 'retroarch'); }

async function download(name, onPct) {
  // Motor que já veio junto com o GameHub (pasta motor-retroarch-N ao lado do .exe): sem internet.
  const local = path.join(path.dirname(process.execPath), ENGINE, name);
  if (fs.existsSync(local)) { onPct(100); return fs.readFileSync(local); }
  if (BASE.startsWith('file:')) { onPct(100); return fs.readFileSync(require('url').fileURLToPath(BASE + name)); }
  const r = await net.fetch(BASE + name);
  if (r.status !== 200) throw new Error('download ' + name + ': ' + r.status);
  const total = Number(r.headers.get('content-length')) || 0;
  const reader = r.body.getReader(), parts = [];
  let got = 0, shown = -1;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    got += value.length;
    if (got > MAX_DOWNLOAD) throw new Error('download grande demais: ' + name);
    parts.push(Buffer.from(value));
    const pct = total ? Math.floor(got / total * 100) : 0;
    if (pct >= shown + 5) { shown = pct; onPct(pct); }
  }
  return Buffer.concat(parts);
}
function unzipTo(buf, dest) {
  const base = path.resolve(dest);
  for (const e of zip.entries(buf)) {
    if (e.name.endsWith('/')) continue;
    const out = path.resolve(base, e.name);
    if (out !== base && !out.startsWith(base + path.sep)) continue;   // nada fora da pasta
    const data = zip.extract(buf, e);
    if (!data) throw new Error('não deu para abrir ' + e.name);
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, data);
  }
}

/** Garante o RetroArch, o emulador e os arquivos de sistema. progress(etapa, %). */
async function ensure(core, progress) {
  if (!fs.existsSync(exe())) {
    const buf = await download('retroarch-win64.zip', p => progress('motor', p));
    fs.rmSync(dir('app'), { recursive: true, force: true });
    unzipTo(buf, dir('app'));
  }
  const dll = dir('cores', core + '_libretro.dll');
  if (!fs.existsSync(dll)) unzipTo(await download(core + '_libretro.dll.zip', p => progress('core', p)), dir('cores'));
  const mark = dir('system', '.' + core);
  if (SYSTEM[core] && !fs.existsSync(mark)) {
    unzipTo(await download(SYSTEM[core], p => progress('core', p)), dir('system'));
    fs.writeFileSync(mark, ENGINE);
  }
  return dll;
}

/** Pasta do BIOS do console (o do PS2 vem do console da própria pessoa) e se já tem um lá. */
function biosDir(c) { return c.bios ? dir('system', ...c.bios.split('/')) : ''; }
function hasBios(c) {
  if (!c.bios) return true;
  try { return fs.readdirSync(biosDir(c)).some(f => fs.statSync(path.join(biosDir(c), f)).size >= 1024 * 1024); } catch { return false; }
}
function openBios(c) { fs.mkdirSync(biosDir(c), { recursive: true }); if (!process.env.COBALT_RA_TEST) shell.openPath(biosDir(c)); }

/* O "onde parou" do RetroArch: um estado automático por jogo, com o nome do arquivo. */
const romBase = rom => path.basename(rom).replace(/\.[^.]+$/, '');
function states(rom) {
  try { const st = fs.statSync(dir('states', romBase(rom) + '.state.auto')); return [{ slot: 'auto', time: st.mtimeMs, shot: false }]; } catch { return []; }
}

/* ---------------- Configurações antes de abrir ----------------
   O que a pessoa escolhe no GameHub (ou o automático, pela faixa do PC em hardware.js) vira opção do
   emulador e driver de vídeo do RetroArch. As opções ficam num arquivo só (global_core_options): as
   chaves de cada emulador têm o prefixo dele, então não se misturam. */
// Resolução automática por faixa do PC (básico, intermediário, forte): 1 = nativa.
const AUTO_RES = { n64: [1, 2, 4], ds: [1, 2, 4], psp: [1, 2, 4], gc: [1, 2, 3], wii: [1, 2, 3], ps2: [1, 2, 2] };
// Onde dá para escolher entre OpenGL e Vulkan (os outros só têm OpenGL). No automático, Vulkan a
// partir do PC intermediário: no OpenGL o Dolphin deixa o menu com quadrados pretos no lugar dos ícones.
const VULKAN = new Set(['psp', 'gc', 'wii', 'ps2']);
const PSP_RES = ['480x272', '960x544', '1440x816', '1920x1088'];
const PS2_RES = { 1: '1x (Native)', 2: '2x', 3: '2x', 4: '4x' };

/** O que vale para o console: resolução (1 a 4), renderizador e widescreen, já com o automático resolvido. */
function resolve(c, eff, tier) {
  const res = eff.raRes && eff.raRes !== 'auto' ? Number(eff.raRes) : AUTO_RES[c.id][tier];
  let renderer = VULKAN.has(c.id) && eff.raRenderer && eff.raRenderer !== 'auto' ? eff.raRenderer : (VULKAN.has(c.id) && tier >= 1 && !vulkanBad() ? 'vulkan' : 'gl');
  return { res, renderer, rendererAuto: !eff.raRenderer || eff.raRenderer === 'auto', wide: !!eff.raWide, pad: eff.pad || {}, kbd: eff.kbd || {}, joy: eff.joy || {} };
}
function coreOptions(c, o) {
  switch (c.core) {
    case 'dolphin': return Object.assign({ dolphin_efb_scale: String(o.res) }, c.id === 'gc' ? { dolphin_widescreen_hack: o.wide ? 'enabled' : 'disabled' } : {});
    case 'ppsspp': return { ppsspp_internal_resolution: PSP_RES[o.res - 1] };
    case 'mupen64plus_next': return { 'mupen64plus-EnableNativeResFactor': String(o.res) };
    // Nativa no DS é o desenho por software (o mais fiel); acima disso, OpenGL na resolução escolhida.
    case 'melondsds': return o.res > 1 ? { melonds_render_mode: 'opengl', melonds_opengl_resolution: String(o.res) } : { melonds_render_mode: 'software' };
    case 'pcsx2': return { pcsx2_upscale_multiplier: PS2_RES[o.res], pcsx2_widescreen_hint: o.wide ? 'enabled (16:9)' : 'disabled' };
  }
  return {};
}

/* Arquivo de config do RetroArch: chave = "valor", uma por linha. */
const q = v => '"' + String(v).replace(/"/g, '') + '"';
function readCfg(file) {
  const out = {};
  try { for (const m of fs.readFileSync(file, 'utf8').matchAll(/^\s*([\w.+-]+)\s*=\s*"([^"]*)"/gm)) out[m[1]] = m[2]; } catch { }
  return out;
}
const writeCfg = (file, obj, head) => {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, (head ? head + '\n' : '') + Object.entries(obj).map(([k, v]) => k + ' = ' + q(v)).join('\n') + '\n');
};

/* Trocar botões: o arquivo de remapeamento do emulador (pasta com o nome que o núcleo dá a si mesmo).
   Chave = botão do controle (posição no RetroPad), valor = o botão que o console recebe. */
const LIBRARY = { dolphin: 'dolphin-emu', mupen64plus_next: 'Mupen64Plus-Next', ppsspp: 'PPSSPP', melondsds: 'melonDS DS', pcsx2: 'PCSX2' };
const PAD = ['b', 'y', 'select', 'start', 'up', 'down', 'left', 'right', 'a', 'x', 'l', 'r', 'l2', 'r2', 'l3', 'r3'];
const MARK = '# Cobalt GameHub: troca de botões feita no GameHub (some quando ela volta ao padrão).';
function writeRemap(c, pad) {
  const lib = LIBRARY[c.core];
  if (!lib) return;
  const file = dir('config', 'remaps', lib, lib + '.rmp');
  const keys = Object.keys(pad).filter(k => /^\d+$/.test(k) && Number(k) < PAD.length);
  if (!keys.length) {
    // Só apaga a troca que o próprio GameHub escreveu; a feita no menu do RetroArch fica.
    try { if (fs.readFileSync(file, 'utf8').startsWith(MARK)) fs.rmSync(file); } catch { }
    return;
  }
  const out = {};
  PAD.forEach((name, i) => { out['input_player1_btn_' + name] = Object.prototype.hasOwnProperty.call(pad, i) ? (pad[i] < 0 ? 1024 : pad[i]) : i; });   // 1024 = sem função
  writeCfg(file, out, MARK);
}

/* Teclas e botões escolhidos no GameHub para cada botão do console (controle 1). Teclado: o padrão do
   RetroArch com as trocas por cima, e a tecla que ficou repetida sai do botão antigo. Controle: só
   quando a pessoa escolheu algum botão, aí vale o desenho do XInput (controle padrão Xbox) inteiro,
   para o botão novo não continuar fazendo também o que fazia antes. */
const KEY_DEFAULT = { 0: 'z', 8: 'x', 1: 'a', 9: 's', 3: 'enter', 2: 'rshift', 10: 'q', 11: 'w', 4: 'up', 5: 'down', 6: 'left', 7: 'right' };
const JOY_DEFAULT = { 0: '0', 8: '1', 1: '2', 9: '3', 10: '4', 11: '5', 2: '6', 3: '7', 14: '8', 15: '9', 12: '+4', 13: '+5', 4: 'h0up', 5: 'h0down', 6: 'h0left', 7: 'h0right' };
const STICK = { 'lx-': 'l_x_minus', 'lx+': 'l_x_plus', 'ly-': 'l_y_minus', 'ly+': 'l_y_plus' };
const bindName = id => STICK[id] || PAD[id];
// Atalhos de teclado do RetroArch que viriam por padrão (letras e teclas F): ficam desligados, para
// nenhuma tecla escolhida para o jogo pausar, rebobinar ou reiniciar sem querer. Tab e Esc continuam.
const HOTKEYS = ['toggle_fast_forward', 'hold_fast_forward', 'hold_slowmotion', 'toggle_slowmotion', 'rewind', 'pause_toggle', 'frame_advance',
  'reset', 'shader_next', 'shader_prev', 'toggle_fullscreen', 'screenshot', 'save_state', 'load_state', 'state_slot_increase', 'state_slot_decrease',
  'audio_mute', 'netplay_game_watch', 'netplay_ping_toggle', 'osk_toggle', 'movie_record', 'grab_mouse_toggle', 'game_focus_toggle', 'volume_up',
  'volume_down', 'fps_toggle', 'statistics_toggle', 'desktop_menu_toggle', 'cheat_toggle', 'cheat_index_plus', 'cheat_index_minus', 'ai_service',
  'record_replay', 'play_replay', 'halt_replay', 'replay_slot_increase', 'replay_slot_decrease', 'disk_eject_toggle', 'disk_next', 'disk_prev',
  'runahead_toggle', 'preempt_toggle', 'toggle_vrr_runloop', 'overlay_next', 'close_content', 'send_debug_info', 'streaming_toggle', 'recording_toggle'];
function binds(o) {
  const cfg = {};
  for (const h of HOTKEYS) cfg['input_' + h] = 'nul';
  const keys = Object.assign({}, KEY_DEFAULT);
  for (const [id, k] of Object.entries(o.kbd)) {
    for (const other of Object.keys(keys)) if (keys[other] === k && !Object.prototype.hasOwnProperty.call(o.kbd, other)) delete keys[other];
    keys[id] = k;
  }
  for (const id of [...PAD.keys(), ...Object.keys(STICK)]) cfg['input_player1_' + bindName(id)] = keys[id] || 'nul';
  if (Object.keys(o.joy).length) {
    const joy = Object.assign({}, JOY_DEFAULT);
    for (const [id, j] of Object.entries(o.joy)) {
      for (const other of Object.keys(joy)) if (joy[other] === j && !Object.prototype.hasOwnProperty.call(o.joy, other)) delete joy[other];
      joy[id] = j;
    }
    for (const id of PAD.keys()) {
      const j = joy[id], axis = /^[+-]/.test(j || '');
      cfg['input_player1_' + PAD[id] + '_btn'] = j && !axis ? j : 'nul';
      cfg['input_player1_' + PAD[id] + '_axis'] = axis ? j : 'nul';
    }
  }
  return cfg;
}

/* Vulkan que não abriu neste PC (o jogo fechou na hora): o automático volta para o OpenGL. */
const vulkanBad = () => fs.existsSync(dir('vulkan-falhou'));
function markVulkanBad() { try { fs.mkdirSync(root, { recursive: true }); fs.writeFileSync(dir('vulkan-falhou'), new Date().toISOString()); } catch { } }

/* RetroAchievements: o RetroArch já tem o seu; entra com o token da conta do GameHub (sem senha), no modo normal. */
function cheevosCfg() {
  const a = cheevosAccount();
  if (!a) return { cheevos_enable: false, cheevos_username: '', cheevos_token: '', cheevos_password: '' };
  return { cheevos_enable: true, cheevos_username: a.user, cheevos_token: a.token, cheevos_password: '', cheevos_hardcore_mode_enable: false,
    cheevos_richpresence_enable: true, cheevos_badges_enable: true, cheevos_unlock_sound_enable: true };
}

function writeConfig(c, mode, lang, o) {
  const test = !!process.env.COBALT_RA_TEST;
  const cfg = {
    libretro_directory: dir('cores'), libretro_info_path: dir('app', 'info'), assets_directory: dir('app', 'assets'),
    joypad_autoconfig_dir: dir('app', 'autoconfig'), system_directory: dir('system'), savefile_directory: dir('saves'),
    savestate_directory: dir('states'), screenshot_directory: dir('screenshots'), log_dir: dir('logs'),
    // Configurações dos emuladores fora da pasta app/ (ela é trocada quando o motor muda de versão).
    rgui_config_directory: dir('config'), input_remapping_directory: dir('config', 'remaps'),
    global_core_options: true, core_options_path: dir('config', 'opcoes.cfg'),
    sort_savefiles_enable: false, sort_savestates_enable: false, sort_savefiles_by_content_enable: false,
    sort_savestates_by_content_enable: false, savefiles_in_content_dir: false, savestates_in_content_dir: false,
    // "Continuar jogando": guarda o ponto exato ao sair e só volta nele quando a pessoa escolhe Continuar.
    savestate_auto_save: true, savestate_auto_load: mode === 'resume', savestate_thumbnail_enable: true,
    video_driver: test ? 'null' : o.renderer, audio_driver: test ? 'null' : 'xaudio',
    video_fullscreen: true, video_windowed_fullscreen: true, pause_nonactive: false,
    menu_driver: 'ozone', user_language: lang === 'es' ? 3 : 7,   // 7 = português do Brasil, 3 = espanhol
    // Tab (teclado) ou Select + Start (controle) abrem o menu; Esc fecha o jogo e volta ao GameHub.
    input_menu_toggle: 'tab', input_menu_toggle_gamepad_combo: 4, input_exit_emulator: 'escape', quit_press_twice: false,
    menu_show_online_updater: false, menu_show_core_updater: false, config_save_on_exit: false,
    ...binds(o),
    ...cheevosCfg(),
  };
  fs.mkdirSync(root, { recursive: true });
  for (const d of ['saves', 'states', 'screenshots', 'logs', 'system', 'config']) fs.mkdirSync(dir(d), { recursive: true });
  // As opções do emulador: as que o GameHub cuida por cima das que a pessoa mexeu no menu do RetroArch.
  const opts = dir('config', 'opcoes.cfg');
  writeCfg(opts, Object.assign(readCfg(opts), coreOptions(c, o)));
  writeRemap(c, o.pad);
  writeCfg(dir('retroarch.cfg'), cfg);
  return dir('retroarch.cfg');
}

/**
 * Abre o jogo. hooks: progress(etapa, %), open(), done(segundos jogados, código), error(mensagem ou 'bios').
 * o = resolve(): resolução, renderizador, widescreen e troca de botões. Devolve false se já houver um
 * jogo do RetroArch aberto.
 */
function launch(c, rom, mode, lang, o, hooks) {
  if (running) return false;
  running = { started: 0 };
  (async () => {
    try {
      if (!hasBios(c)) { running = null; openBios(c); hooks.error('bios'); return; }
      const dll = await ensure(c.core, hooks.progress);
      const cfg = writeConfig(c, mode, lang, o);
      const args = ['-L', dll, '--config', cfg, '--log-file', dir('logs', 'retroarch.log'), '--verbose'];
      if (process.env.COBALT_RA_TEST) args.push('--max-frames=60');
      args.push(rom);
      const child = spawn(exe(), args, { cwd: dir('app'), stdio: 'ignore', windowsHide: false });
      running.started = Date.now();
      hooks.open();
      child.on('error', e => { running = null; hooks.error(String(e && e.message || e)); });
      child.on('exit', code => {
        const secs = Math.round((Date.now() - running.started) / 1000);
        running = null;
        hooks.done(secs, code);
      });
    } catch (e) {
      running = null;
      hooks.error(String(e && e.message || e));
    }
  })();
  return true;
}

module.exports = { ENGINE, init, setCheevos, launch, resolve, markVulkanBad, AUTO_RES, VULKAN, states, biosDir, openBios, isRunning: () => !!running };
