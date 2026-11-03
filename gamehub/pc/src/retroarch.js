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

let root = '', running = null;
const dir = (...a) => path.join(root, ...a);
const exe = () => dir('app', 'retroarch.exe');
function init(userData) { root = path.join(userData, 'retroarch'); }

async function download(name, onPct) {
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

function writeConfig(mode, lang) {
  const cfg = {
    libretro_directory: dir('cores'), libretro_info_path: dir('app', 'info'), assets_directory: dir('app', 'assets'),
    joypad_autoconfig_dir: dir('app', 'autoconfig'), system_directory: dir('system'), savefile_directory: dir('saves'),
    savestate_directory: dir('states'), screenshot_directory: dir('screenshots'), log_dir: dir('logs'),
    sort_savefiles_enable: false, sort_savestates_enable: false, sort_savefiles_by_content_enable: false,
    sort_savestates_by_content_enable: false, savefiles_in_content_dir: false, savestates_in_content_dir: false,
    // "Continuar jogando": guarda o ponto exato ao sair e só volta nele quando a pessoa escolhe Continuar.
    savestate_auto_save: true, savestate_auto_load: mode === 'resume', savestate_thumbnail_enable: true,
    video_driver: process.env.COBALT_RA_TEST ? 'null' : 'gl', audio_driver: process.env.COBALT_RA_TEST ? 'null' : 'xaudio',
    video_fullscreen: true, video_windowed_fullscreen: true, pause_nonactive: false,
    menu_driver: 'ozone', user_language: lang === 'es' ? 3 : 7,   // 7 = português do Brasil, 3 = espanhol
    // Select + Start abre o menu (como no GameHub); Esc fecha o jogo e volta ao GameHub.
    input_menu_toggle_gamepad_combo: 4, input_exit_emulator: 'escape', quit_press_twice: false,
    menu_show_online_updater: false, menu_show_core_updater: false, config_save_on_exit: false,
  };
  fs.mkdirSync(root, { recursive: true });
  for (const d of ['saves', 'states', 'screenshots', 'logs', 'system']) fs.mkdirSync(dir(d), { recursive: true });
  const q = v => '"' + String(v).replace(/"/g, '') + '"';
  fs.writeFileSync(dir('retroarch.cfg'), Object.entries(cfg).map(([k, v]) => k + ' = ' + q(v)).join('\n') + '\n');
  return dir('retroarch.cfg');
}

/**
 * Abre o jogo. hooks: progress(etapa, %), open(), done(segundos jogados), error(mensagem, bios?).
 * Devolve false se já houver um jogo do RetroArch aberto.
 */
function launch(c, rom, mode, lang, hooks) {
  if (running) return false;
  running = { started: 0 };
  (async () => {
    try {
      if (!hasBios(c)) { running = null; openBios(c); hooks.error('bios'); return; }
      const dll = await ensure(c.core, hooks.progress);
      const cfg = writeConfig(mode, lang);
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

module.exports = { ENGINE, init, launch, states, biosDir, openBios, isRunning: () => !!running };
