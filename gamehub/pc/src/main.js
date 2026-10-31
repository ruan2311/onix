// Onix GameHub para PC: processo principal (janela, arquivos, ponte das telas).
// As telas são as mesmas da TV (gamehub/assets: hub.html, menu.html, common.*), servidas em
// app://hub/ como no Android (Web.java). O jogo abre numa camada por cima do hub (game.html),
// com o núcleo em WebAssembly; ao sair, a camada some e o hub continua onde estava.
'use strict';
const { app, BrowserWindow, WebContentsView, ipcMain, dialog, protocol, shell, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const store = require('./store');
const covers = require('./covers');
const updater = require('./updater');

protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } },
]);
if (process.platform === 'win32') app.setAppUserModelId('Cobalt GameHub');
// O jogo não pode desacelerar com a janela atrás de outra (o som estalaria).
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
app.commandLine.appendSwitch('enable-gpu-rasterization');
// PC sem placa de vídeo (ou máquina virtual): o WebGL do jogo cai no SwiftShader, por software.
app.commandLine.appendSwitch('enable-unsafe-swiftshader');
// O som do jogo começa sem esperar um clique.
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

const HERE = path.join(__dirname, '..');
const pick = (...dirs) => dirs.find(d => fs.existsSync(d));
// Empacotado, tudo fica dentro do app; no desenvolvimento, vem do repositório.
const ASSETS = pick(path.join(HERE, 'assets'), path.join(HERE, '..', 'assets'));
const WEB = path.join(HERE, 'web');
const CORES = pick(path.join(HERE, 'cores'), path.join(HERE, 'build', 'cores'));
const VERSION = require(path.join(HERE, 'package.json')).version;

let win = null, game = null, logFile = null;
// Tela cheia ao jogar: true quando foi o jogo que pôs a janela em tela cheia (sai dela ao fechar).
let gameMadeFull = false;
function log(...parts) {
  const line = new Date().toISOString() + ' ' + parts.map(x => (x instanceof Error ? x.stack : String(x))).join(' ') + '\n';
  try { fs.appendFileSync(logFile, line); } catch { /* sem disco */ }
}
process.on('uncaughtException', e => log('[main]', e));
process.on('unhandledRejection', e => log('[main]', e));

const lang = () => (app.getLocale() || '').toLowerCase().startsWith('es') ? 'es' : 'pt';
const ok = id => typeof id === 'string' && store.ID.test(id);
const emit = (type, data, target) => {
  const wc = target || (win && win.webContents);
  if (wc && !wc.isDestroyed()) wc.send('onix:event', type, data === undefined ? null : data);
};

/* ---------------- app://hub/ ----------------
   Só o que está nas listas abaixo; o resto é 404. */
const TYPES = { html: 'text/html', js: 'text/javascript', css: 'text/css', txt: 'text/plain', wasm: 'application/wasm', jpg: 'image/jpeg', png: 'image/png' };
const ROUTES = [
  [/^\/(hub\.html|menu\.html|common\.js|common\.css|licenses\/[a-z0-9_]{1,40}\.txt)$/, m => path.join(ASSETS, m[1])],
  [/^\/(game\.html|game\.js|audio\.js|pc\.css)$/, m => path.join(WEB, m[1])],
  [/^\/cores\/(fceumm|snes9x|mgba|gpgx)\.(js|wasm)$/, m => path.join(CORES, m[1] + '.' + m[2])],
  [/^\/cover\/([0-9a-f]{16})\.jpg$/, m => store.coverFile(m[1])],
  [/^\/shot\/default\/([0-9a-f]{16})\/(auto|[1-4])\.jpg$/, m => path.join(store.statesDir(m[1]), m[2] + '.jpg')],
];
async function serve(request) {
  const url = new URL(request.url);
  if (url.hostname !== 'hub') return new Response('', { status: 404 });
  for (const [re, file] of ROUTES) {
    const m = re.exec(decodeURIComponent(url.pathname));
    if (!m) continue;
    try {
      const data = await fs.promises.readFile(file(m));
      const ext = path.extname(m[0]).slice(1);
      return new Response(data, { headers: { 'Content-Type': TYPES[ext] || 'application/octet-stream', 'Cache-Control': ext === 'jpg' ? 'no-cache' : 'no-store' } });
    } catch { break; }
  }
  return new Response('', { status: 404 });
}

/* ---------------- Ponte do hub (HubBridge.java) ----------------
   Chamadas síncronas, como no Android: a página recebe texto JSON ou true/false. */
const HUB = {
  init: () => JSON.stringify({
    profile: 'default', profileName: '', kids: false, lang: lang(), open: '', fromOnix: false, version: VERSION,
    onix: false, onixSound: true, onixMotion: true, mobile: false, pc: true, folders: store.pcData().folders,
  }),
  library: () => JSON.stringify(store.list()),
  states: id => JSON.stringify(ok(id) ? store.states(id) : []),
  play: (id, mode) => ok(id) && /^(resume|new|[1-4])$/.test(mode) && openGame(id, mode),
  setFavorite: (id, fav) => ok(id) && store.setFavorite(id, !!fav),
  rename: (id, name) => {
    if (!ok(id) || typeof name !== 'string') return false;
    name = name.replace(/[\u0000-\u001f\u007f]/g, '').trim();
    return name.length > 0 && name.length <= 80 && store.update(id, { name });
  },
  setKids: (id, allowed) => ok(id) && store.update(id, { kids: !!allowed }),
  remove: id => ok(id) && store.remove(id),
  deleteState: (id, slot) => {
    if (!ok(id) || !store.SLOT.test(slot)) return false;
    fs.rmSync(path.join(store.statesDir(id), slot + '.jpg'), { force: true });
    try { fs.unlinkSync(path.join(store.statesDir(id), slot + '.state')); return true; } catch { return false; }
  },
  config: () => JSON.stringify(store.config()),
  effective: (c, id) => JSON.stringify(store.consoleById(c) ? store.effective(c, ok(id) ? id : null) : {}),
  setConfig: (scope, key, value) => {
    const r = store.setConfig(scope, key, value);
    if (r && key === 'fullscreen' && scope === 'global' && win) win.setFullScreen(store.effective('snes').fullscreen);
    return r;
  },
  coreOptions: c => {
    if (!store.consoleById(c)) return '{}';
    // Os núcleos só contam as opções com um jogo aberto: usa as da última partida desse console.
    return store.read(store.p('options-' + c + '-' + lang() + '.json')) || '{"categories":[],"options":[],"later":true}';
  },
  cheats: id => (ok(id) && store.read(store.cheatsFile(id))) || '[]',
  setCheats: (id, json) => ok(id) && saveCheats(id, json),
  storage: () => JSON.stringify(store.storage()),
  uploadStart: () => '{}',
  uploadStop: () => true,
  editTouch: () => false,
  pickGames: folder => { pickGames(!!folder); return true; },
  folders: () => JSON.stringify(store.pcData().folders),
  removeFolder: dir => typeof dir === 'string' && store.removeFolder(dir),
  rescan: () => { importFiles(store.newFiles(store.pcData().folders), true); return true; },
  playersSaved: () => JSON.stringify(store.players()),
  setPlayer: (player, padId, name) => store.setPlayer(player, String(padId || '').slice(0, 200), String(name || '').slice(0, 80)),
  clearPad: player => store.clearPlayer(player),
  openBluetooth: () => { shell.openExternal('ms-settings:bluetooth').catch(() => { }); return true; },
  update: () => updater.json(),
  checkUpdate: () => { updater.check(true); return true; },
  installUpdate: () => { updater.open(); return true; },
  fullscreen: () => { if (win) win.setFullScreen(!win.isFullScreen()); return true; },
  refreshCovers: () => covers.refreshAll(),
  exit: () => false,   // no PC, sair é fechar a janela
};

function saveCheats(id, json) {
  if (typeof json !== 'string' || json.length > 64 * 1024) return false;
  try {
    const out = [];
    for (const o of JSON.parse(json).slice(0, 100)) {
      const code = String(o.code || '').trim(), desc = String(o.desc || '').trim();
      if (!code || code.length > 200 || desc.length > 80) continue;
      out.push({ code, desc, on: !!o.on });
    }
    return store.writeJson(store.cheatsFile(id), out);
  } catch { return false; }
}

/* ---------------- Adicionar jogos ----------------
   Pasta (fica guardada e é olhada de novo a cada abertura) ou arquivos soltos. Os eventos são os
   mesmos do celular (FolderImport.java): scan, start/done de cada arquivo e finished. */
let importing = false;
async function pickGames(folder) {
  const r = await dialog.showOpenDialog(win, folder
    ? { title: 'Pasta dos jogos', properties: ['openDirectory'] }
    : { title: 'Jogos', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Jogos', extensions: ['nes', 'sfc', 'smc', 'gba', 'md', 'gen', 'bin', 'smd', 'zip', 'fds', 'unf', 'swc', 'fig'] }] });
  if (r.canceled || !r.filePaths.length) return;
  if (folder) {
    const dir = store.addFolder(r.filePaths[0]);
    emit('folders', store.pcData().folders);
    importFiles(store.newFiles([dir], true), false);
  } else {
    importFiles(r.filePaths, false);
  }
}
/* quiet: a olhada nas pastas ao abrir, sem avisos (só a biblioteca atualiza se algo entrou). */
async function importFiles(files, quiet) {
  if (importing) return;
  importing = true;
  try {
    if (!quiet) emit('upload', { type: 'scan', count: files.length });
    let added = 0;
    for (const f of files) {
      const name = path.basename(f);
      if (!quiet) emit('upload', { type: 'start', file: name, name, size: 1 });
      const res = store.addFile(f);
      if (!quiet) emit('upload', Object.assign({ type: 'done', file: name }, res));
      if (res.ok && !res.dup) { added++; covers.later(res.id); }
      await new Promise(r => setImmediate(r));
    }
    if (!quiet) emit('upload', { type: 'finished', count: files.length });
    if (added) emit('library');
  } finally {
    importing = false;
  }
}

/* ---------------- Jogo ----------------
   Uma camada (WebContentsView) por partida: o núcleo começa limpo a cada jogo, como o processo
   ":game" da TV. */
function openGame(id, mode) {
  if (game || !store.game(id)) return false;
  game = new WebContentsView({ webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, backgroundThrottling: false } });
  game.setBackgroundColor('#000000');
  win.contentView.addChildView(game);
  layout();
  game.webContents.loadURL('app://hub/game.html?id=' + id + '&mode=' + mode);
  game.webContents.on('render-process-gone', (_, d) => { log('[jogo]', d.reason); closeGame(); });
  game.webContents.on('console-message', (e) => { if (e.level === 'error') log('[jogo]', e.message); });
  game.webContents.focus();
  if (store.effective('snes').gameFullscreen) enterGameFullscreen();
  return true;
}
function enterGameFullscreen() {
  if (!win || win.isFullScreen()) return;
  gameMadeFull = true;
  win.setFullScreen(true);
}
function leaveGameFullscreen() {
  if (gameMadeFull && win && !store.effective('snes').fullscreen) win.setFullScreen(false);
  gameMadeFull = false;
}
function closeGame() {
  if (!game) return;
  const g = game;
  game = null;
  win.contentView.removeChildView(g);
  g.webContents.close();
  leaveGameFullscreen();
  win.webContents.focus();
  emit('library');
}
function layout() {
  if (!game || !win) return;
  const [w, h] = win.getContentSize();
  game.setBounds({ x: 0, y: 0, width: w, height: h });
}

/* Ponte do jogo: o menu (MenuBridge.java) roda dentro do game.js; aqui ficam só os arquivos. */
const GAME = {
  info: id => {
    const g = ok(id) && store.game(id);
    if (!g) return null;
    const c = store.consoleById(g.console);
    return JSON.stringify({
      id, name: g.name, console: c.id, core: c.core, lang: lang(), profile: 'default', kids: false, sounds: true, motion: true,
      settings: store.effective(c.id, id), cover: fs.existsSync(store.coverFile(id)), ext: path.extname(store.romPath(g)).slice(1).toLowerCase(),
    });
  },
  effective: id => { const g = ok(id) && store.game(id); return g ? JSON.stringify(store.effective(g.console, id)) : '{}'; },
  states: id => JSON.stringify(ok(id) ? store.states(id) : []),
  setConfig: (id, scope, key, value) => {
    const g = ok(id) && store.game(id);
    if (!g || (scope !== 'global' && scope !== 'console:' + g.console && scope !== 'game:' + id)) return false;
    return store.setConfig(scope, key, value);
  },
  cheats: id => (ok(id) && store.read(store.cheatsFile(id))) || '[]',
  setCheats: (id, json) => ok(id) && saveCheats(id, json),
  saveOptions: (c, json) => !!store.consoleById(c) && typeof json === 'string' && json.length < 512 * 1024 && store.write(store.p('options-' + c + '-' + lang() + '.json'), json),
  playersSaved: () => JSON.stringify(store.players()),
  fullscreen: () => { if (win) win.setFullScreen(!win.isFullScreen()); return true; },
  setGameFullscreen: on => {
    const done = store.setConfig('global', 'gameFullscreen', JSON.stringify(!!on));
    if (done && on) enterGameFullscreen();
    else if (done) leaveGameFullscreen();
    return done;
  },
  quit: (id, seconds) => { if (ok(id) && seconds > 5) store.recordPlay(id, seconds); setImmediate(closeGame); return true; },
};
// Bytes (ROM, estados, memória do cartucho): pedidos assíncronos.
const GAME_ASYNC = {
  rom: async id => { const g = ok(id) && store.game(id); return g ? fs.promises.readFile(store.romPath(g)) : null; },
  readState: async (id, slot) => (ok(id) && store.SLOT.test(slot) ? fs.promises.readFile(path.join(store.statesDir(id), slot + '.state')).catch(() => null) : null),
  writeState: async (id, slot, data, shot) => {
    if (!ok(id) || !store.SLOT.test(slot) || !(data instanceof Uint8Array)) return false;
    const done = store.write(path.join(store.statesDir(id), slot + '.state'), Buffer.from(data));
    if (done && shot instanceof Uint8Array && shot.length < 2 * 1024 * 1024) store.write(path.join(store.statesDir(id), slot + '.jpg'), Buffer.from(shot));
    return done;
  },
  readSram: async id => (ok(id) ? fs.promises.readFile(store.sramFile(id)).catch(() => null) : null),
  writeSram: async (id, data) => ok(id) && data instanceof Uint8Array && store.write(store.sramFile(id), Buffer.from(data)),
};

/* ---------------- Janela ---------------- */
function createWindow() {
  win = new BrowserWindow({
    width: 1600, height: 900, minWidth: 960, minHeight: 540, show: false, backgroundColor: '#07040f', title: 'Cobalt GameHub',
    icon: path.join(HERE, 'icon.png'), autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, backgroundThrottling: false },
  });
  Menu.setApplicationMenu(null);
  win.on('resize', layout);
  win.on('enter-full-screen', layout);
  win.on('leave-full-screen', layout);
  win.once('ready-to-show', () => {
    win.maximize();
    win.show();
    if (store.effective('snes').fullscreen) win.setFullScreen(true);
  });
  // F11 ou Alt+Enter: tela cheia, nas duas camadas.
  const keys = (e, input) => {
    if (input.type === 'keyDown' && (input.key === 'F11' || (input.key === 'Enter' && input.alt))) { e.preventDefault(); win.setFullScreen(!win.isFullScreen()); }
  };
  win.webContents.on('before-input-event', keys);
  app.on('web-contents-created', (_, wc) => {
    wc.on('before-input-event', keys);
    wc.setWindowOpenHandler(() => ({ action: 'deny' }));
    wc.on('will-navigate', (e, url) => { if (!url.startsWith('app://hub/')) e.preventDefault(); });
  });
  win.loadURL('app://hub/hub.html');
  win.on('closed', () => { win = null; });
}

app.whenReady().then(() => {
  const userData = app.getPath('userData');
  const oldUserData = path.join(path.dirname(userData), 'Onix GameHub');
  if (!fs.existsSync(path.join(userData, 'library.json')) && fs.existsSync(path.join(oldUserData, 'library.json'))) {
    try { fs.cpSync(oldUserData, userData, { recursive: true }); } catch (_) { }
  }
  store.init(userData, ASSETS);
  logFile = path.join(userData, 'gamehub.log');
  protocol.handle('app', serve);

  ipcMain.on('onix:hub', (e, name, args) => {
    try { e.returnValue = Object.prototype.hasOwnProperty.call(HUB, name) ? HUB[name](...(Array.isArray(args) ? args : [])) : null; }
    catch (err) { log('[hub]', name, err); e.returnValue = null; }
  });
  ipcMain.on('onix:game', (e, name, args) => {
    try { e.returnValue = Object.prototype.hasOwnProperty.call(GAME, name) ? GAME[name](...(Array.isArray(args) ? args : [])) : null; }
    catch (err) { log('[jogo]', name, err); e.returnValue = null; }
  });
  ipcMain.handle('onix:gameAsync', (_, name, args) => (Object.prototype.hasOwnProperty.call(GAME_ASYNC, name) ? GAME_ASYNC[name](...(Array.isArray(args) ? args : [])) : null));
  ipcMain.on('onix:log', (_, msg) => log('[página]', String(msg).slice(0, 2000)));

  covers.setListener(id => emit('cover', id), r => emit('covers', r));
  updater.init(VERSION, store, state => emit('update', state));
  createWindow();
  win.webContents.once('did-finish-load', () => {
    covers.fetchMissing();
    importFiles(store.newFiles(store.pcData().folders), true);
    setTimeout(() => updater.check(false), 8000);
  });
});
app.on('window-all-closed', () => app.quit());
