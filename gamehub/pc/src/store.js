// Dados do GameHub no PC: os mesmos arquivos da TV (Files.java, Library.java, Config.java,
// Players.java, Storage.java e Importer.java), na pasta do app (%APPDATA%\Onix GameHub).
//
//   library.json                          biblioteca (nome, console, capa, Modo Infantil…)
//   config.json                           configurações (global, por console, por jogo)
//   pc.json                               pastas de jogos e os arquivos delas tirados da biblioteca
//   players.json                          controle 1 e 2 definidos
//   roms/<id>.<ext>                       só os jogos que vieram de um .zip (os outros ficam
//                                         onde estão, na pasta escolhida)
//   covers/<id>.jpg                       capas baixadas
//   cheats/<id>.json                      códigos de trapaça
//   profiles/default/played.json          última vez, tempo jogado e favoritos
//   profiles/default/saves/<id>.srm       memória do cartucho
//   profiles/default/states/<id>/<n>.*    estados salvos (auto, 1–4) e miniaturas
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const zlib = require('zlib');
const zip = require('./zip');

const ID = /^[0-9a-f]{16}$/;
const SLOT = /^(auto|[1-4])$/;
const MB = 1024 * 1024, GB = 1024 * MB;

/* ---------------- Consoles (Consoles.java) ---------------- */
const B = 0, Y = 1, SELECT = 2, START = 3, A = 8, X = 9, L = 10, R = 11;
const CONSOLES = [
  { id: 'nes', name: 'Nintendinho', core: 'fceumm', thumbs: 'Nintendo - Nintendo Entertainment System', ext: ['nes', 'unf', 'unif', 'fds'], buttons: [A, B, SELECT, START], maxBytes: 4 * MB },
  { id: 'snes', name: 'Super Nintendo', core: 'snes9x', thumbs: 'Nintendo - Super Nintendo Entertainment System', ext: ['sfc', 'smc', 'swc', 'fig'], buttons: [A, B, X, Y, L, R, SELECT, START], maxBytes: 12 * MB },
  { id: 'gba', name: 'Game Boy Advance', core: 'mgba', thumbs: 'Nintendo - Game Boy Advance', ext: ['gba'], buttons: [A, B, L, R, SELECT, START], maxBytes: 33 * MB },
  { id: 'md', name: 'Mega Drive', core: 'gpgx', thumbs: 'Sega - Mega Drive - Genesis', ext: ['md', 'gen', 'smd', 'bin'], buttons: [B, A, Y, X, L, R, START, SELECT], maxBytes: 10 * MB },
];
const extension = f => { const d = String(f || '').lastIndexOf('.'); return d < 0 ? '' : f.slice(d + 1).toLowerCase(); };
const consoleById = id => CONSOLES.find(c => c.id === id) || null;
const consoleByExt = f => { const e = extension(f); return CONSOLES.find(c => c.ext.includes(e)) || null; };

/* ---------------- Arquivos (Files.java) ---------------- */
let root = '', dbDir = '';
function init(userData, assetsDir) {
  root = userData;
  dbDir = path.join(assetsDir, 'db');
  for (const d of ['roms', 'covers', 'cheats', 'system', 'profiles/default/saves', 'profiles/default/states']) fs.mkdirSync(path.join(root, d), { recursive: true });
}
const p = (...parts) => path.join(root, ...parts);
const profileDir = () => p('profiles', 'default');
const statesDir = id => { const d = path.join(profileDir(), 'states', id); fs.mkdirSync(d, { recursive: true }); return d; };
const sramFile = id => path.join(profileDir(), 'saves', id + '.srm');
const cheatsFile = id => p('cheats', id + '.json');
const coverFile = id => p('covers', id + '.jpg');

function read(f) { try { return fs.readFileSync(f, 'utf8'); } catch { return null; } }
function readJson(f, def) { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch { return def; } }
/** Grava num .tmp e renomeia: um desligamento no meio nunca deixa o arquivo pela metade. */
function write(f, data) {
  const tmp = f + '.tmp';
  try {
    fs.mkdirSync(path.dirname(f), { recursive: true });
    const fd = fs.openSync(tmp, 'w');
    try { fs.writeSync(fd, typeof data === 'string' ? Buffer.from(data, 'utf8') : data); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    fs.renameSync(tmp, f);
    return true;
  } catch {
    try { fs.unlinkSync(tmp); } catch { /* já saiu */ }
    return false;
  }
}
const writeJson = (f, o) => write(f, JSON.stringify(o));
function size(f) {
  try {
    const s = fs.statSync(f);
    if (!s.isDirectory()) return s.size;
    return fs.readdirSync(f).reduce((t, n) => t + size(path.join(f, n)), 0);
  } catch { return 0; }
}

/* ---------------- Biblioteca (Library.java) ---------------- */
const games = () => (readJson(p('library.json'), {}).games) || [];
const saveGames = g => { if (!writeJson(p('library.json'), { version: 1, games: g })) throw new Error('sem espaço'); };
const game = id => games().find(g => g.id === id) || null;
const romPath = g => g.path || p('roms', g.file);
const played = () => readJson(path.join(profileDir(), 'played.json'), {});

function update(id, fields) {
  const g = games(), o = g.find(x => x.id === id);
  if (!o) return false;
  Object.assign(o, fields);
  try { saveGames(g); return true; } catch { return false; }
}

/** Tira o jogo da biblioteca. Só apaga o arquivo se ele for do GameHub (veio de um .zip): o da
    pasta da pessoa fica onde está, e a pasta não traz ele de volta. */
function remove(id) {
  const g = games(), gone = g.find(x => x.id === id);
  if (!gone) return false;
  try { saveGames(g.filter(x => x !== gone)); } catch { return false; }
  if (gone.file) fs.rmSync(p('roms', gone.file), { force: true });
  else { const pc = pcData(); if (!pc.removed.includes(gone.path)) { pc.removed.push(gone.path); savePc(pc); } }
  fs.rmSync(coverFile(id), { force: true });
  fs.rmSync(cheatsFile(id), { force: true });
  fs.rmSync(sramFile(id), { force: true });
  fs.rmSync(path.join(profileDir(), 'states', id), { recursive: true, force: true });
  const pl = played();
  if (pl[id]) { delete pl[id]; writeJson(path.join(profileDir(), 'played.json'), pl); }
  removeGameConfig(id);
  return true;
}

function recordPlay(id, seconds) {
  const all = played(), o = all[id] || {};
  o.last = Date.now();
  o.time = (o.time || 0) + Math.max(0, Math.round(seconds));
  all[id] = o;
  writeJson(path.join(profileDir(), 'played.json'), all);
}
function setFavorite(id, fav) {
  const all = played(), o = all[id] || {};
  o.fav = !!fav;
  all[id] = o;
  return writeJson(path.join(profileDir(), 'played.json'), all);
}

function list() {
  const pl = played();
  return games().map(g => {
    const x = Object.assign({}, g), q = pl[g.id];
    if (q) { x.last = q.last || 0; x.time = q.time || 0; x.fav = !!q.fav; }
    x.resume = fs.existsSync(path.join(profileDir(), 'states', g.id, 'auto.state'));
    x.missing = !fs.existsSync(romPath(g));
    delete x.path;   // a página não precisa dos caminhos no disco
    delete x.src;
    return x;
  });
}

function states(id) {
  const out = [], dir = path.join(profileDir(), 'states', id);
  for (const slot of ['auto', '1', '2', '3', '4']) {
    try {
      const st = fs.statSync(path.join(dir, slot + '.state'));
      out.push({ slot, time: st.mtimeMs, shot: fs.existsSync(path.join(dir, slot + '.jpg')) });
    } catch { /* espaço vazio */ }
  }
  return out;
}

/* ---------------- Configurações (Config.java) ----------------
   Os mesmos três níveis da TV; no PC entram as configurações gráficas a mais. */
const DEFAULTS = {}, CHOICES = {}, RANGES = {};
const choice = (k, def, ...v) => { DEFAULTS[k] = def; CHOICES[k] = v; };
const range = (k, def, min, max) => { DEFAULTS[k] = def; RANGES[k] = [min, max]; };
const flag = (k, def) => { DEFAULTS[k] = def; };
choice('filter', 'sharp', 'sharp', 'pixel', 'smooth', 'crt', 'crtcurve', 'scanlines', 'lcd');
choice('scale', 'fit', 'fit', 'integer', 'stretch');
choice('aspect', 'core', 'core', '4:3', 'pixel', '16:9');
range('volume', 100, 0, 100);
range('latency', 64, 32, 128);
range('runahead', 0, 0, 2);
range('ffspeed', 3, 2, 8);
range('deadzone', 25, 5, 60);
range('turboRate', 3, 1, 10);
flag('rewind', false);
flag('autosave', true);
flag('stickDpad', true);
flag('showFps', false);
// No PC não há Onix para seguir: ligados por padrão ("onix" fica aceito, vale como ligado).
choice('sounds', 'on', 'onix', 'on', 'off');
choice('motion', 'on', 'onix', 'on', 'off');
// Só no PC: ajustes de imagem feitos na placa de vídeo.
range('brightness', 100, 70, 140);
range('saturation', 100, 0, 150);
range('scanStrength', 40, 10, 80);
range('curvature', 2, 0, 4);
flag('vignette', false);
flag('frameBlend', false);
choice('backdrop', 'ambient', 'ambient', 'black', 'glass');
flag('fullscreen', false);
flag('gameFullscreen', false);   // tela cheia só enquanto o jogo está aberto
const CORE_KEY = /^[A-Za-z0-9_.-]{1,80}$/;

const config = () => readJson(p('config.json'), {});
function level(all, scope, create) {
  if (scope === 'global') { if (!all.global && create) all.global = {}; return all.global || null; }
  let kind, id;
  if (scope.startsWith('console:')) { kind = 'console'; id = scope.slice(8); if (!consoleById(id)) return null; }
  else if (scope.startsWith('game:')) { kind = 'game'; id = scope.slice(5); if (!ID.test(id)) return null; }
  else return null;
  if (!all[kind]) { if (!create) return null; all[kind] = {}; }
  if (!all[kind][id] && create) all[kind][id] = {};
  return all[kind][id] || null;
}
function valid(key, v) {
  const def = DEFAULTS[key];
  if (typeof def === 'boolean') return typeof v === 'boolean';
  if (typeof def === 'number') return Number.isInteger(v) && v >= RANGES[key][0] && v <= RANGES[key][1];
  return typeof v === 'string' && (CHOICES[key] || []).includes(v);
}
/** key = nome, "core.<opção>", "remap.<tecla>" ou "turbo"; value = JSON do valor, ou null. */
function setConfig(scope, key, value) {
  if (typeof scope !== 'string' || typeof key !== 'string') return false;
  let v = null;
  if (value !== null && value !== undefined) { try { v = JSON.parse(value); } catch { return false; } }
  const all = config(), lv = level(all, scope, true);
  if (!lv) return false;
  if (key.startsWith('core.') || key.startsWith('remap.')) {
    const core = key.startsWith('core.'), sub = key.slice(core ? 5 : 6);
    if (core ? !CORE_KEY.test(sub) : !/^\d{1,3}$/.test(sub)) return false;
    const map = lv[core ? 'core' : 'remap'] || (lv[core ? 'core' : 'remap'] = {});
    if (v === null) delete map[sub];
    else if (core && typeof v === 'string' && v.length <= 120) map[sub] = v;
    else if (!core && Number.isInteger(v) && v >= -1 && v <= 15) map[sub] = v;
    else return false;
  } else if ((key === 'remap' || key === 'core') && v === null) {
    delete lv[key];
  } else if (key === 'turbo') {
    if (v === null) delete lv.turbo;
    else if (Number.isInteger(v) && v >= 0 && v <= 0xFFFF) lv.turbo = v;
    else return false;
  } else if (key in DEFAULTS) {
    if (v === null) delete lv[key];
    else if (!valid(key, v)) return false;
    else lv[key] = v;
  } else {
    return false;
  }
  return writeJson(p('config.json'), all);
}
/** As configurações que valem para um jogo: padrão < global < console < jogo. */
function effective(consoleId, id) {
  const out = Object.assign({}, DEFAULTS, { turbo: 0 }), core = {}, remap = {};
  const all = config();
  for (const lv of [level(all, 'global'), level(all, 'console:' + consoleId), id ? level(all, 'game:' + id) : null]) {
    if (!lv) continue;
    for (const k of Object.keys(lv)) {
      if (k === 'core') Object.assign(core, lv.core);
      else if (k === 'remap') Object.assign(remap, lv.remap);
      else out[k] = lv[k];
    }
  }
  out.core = core;
  out.remap = remap;
  return out;
}
function removeGameConfig(id) {
  const all = config();
  if (all.game && all.game[id]) { delete all.game[id]; writeJson(p('config.json'), all); }
}

/* ---------------- Controle 1 e 2 (Players.java) ----------------
   No PC o aparelho é o nome que o Windows dá ao controle (Gamepad.id). */
const players = () => readJson(p('players.json'), {});
function setPlayer(player, padId, name) {
  if (player < 1 || player > 2 || !padId) return false;
  const all = players(), old = all[player];
  for (const q of ['1', '2']) if (q !== String(player) && all[q] && all[q].d === padId) { if (old) all[q] = old; else delete all[q]; }
  all[player] = { d: padId, n: name };
  return writeJson(p('players.json'), all);
}
function clearPlayer(player) { const all = players(); delete all[player]; return writeJson(p('players.json'), all); }

/* ---------------- Espaço (Storage.java) ---------------- */
function storage() {
  let total = 0, free = 0;
  try { const s = fs.statfsSync(root); total = s.blocks * s.bsize; free = s.bavail * s.bsize; } catch { /* sem informação */ }
  const reserve = Math.min(2 * GB, Math.max(768 * MB, total / 10));
  const romsBytes = games().reduce((t, g) => t + (g.size || 0), 0);
  return { total, free, reserve, available: Math.max(0, free - reserve), games: romsBytes, used: romsBytes + size(p('covers')) + size(p('profiles')) };
}

/* ---------------- Importar (Importer.java + FolderImport.java) ---------------- */
const DB = {};
function lookup(consoleId, crc) {
  if (!DB[consoleId]) {
    const m = new Map();
    for (const line of (read(path.join(dbDir, consoleId + '.tsv')) || '').split('\n')) if (line.indexOf('\t') === 8) m.set(line.slice(0, 8), line.slice(9).trim());
    DB[consoleId] = m;
  }
  return DB[consoleId].get(crc) || null;
}
const TAGS = /\s*[([][^)\]]*[)\]]/g;
const ARTICLE = /^(.+?), (The|A|An|Les|Le|La|Die|Der|Das|El|Los|Las|O|Os)( - .*)?$/;
/** "Legend of Zelda, The - A Link to the Past (USA) (Rev 1)" -> "The Legend of Zelda - A Link to the Past" */
function displayName(title) {
  let s = title.replace(TAGS, '').replace(/_/g, ' ').trim();
  const m = ARTICLE.exec(s);
  if (m) s = m[2] + ' ' + m[1] + (m[3] || '');
  s = s.replace(/\s{2,}/g, ' ').trim() || title.trim();
  return s.length > 80 ? s.slice(0, 80).trim() : s;
}
const hex8 = n => (n >>> 0).toString(16).padStart(8, '0');

/** Põe um arquivo na biblioteca: {ok, id, name, console, dup, error}. */
function addFile(file) {
  const r = {};
  try {
    let name = path.basename(file), data, fromZip = false;
    if (extension(name) === 'zip') {
      const best = zip.largest(fs.readFileSync(file), n => { const c = consoleByExt(n); return c ? c.maxBytes : 0; });
      if (!best) return Object.assign(r, { ok: false, error: 'O .zip não tem nenhum jogo de NES, SNES, GBA ou Mega Drive.' });
      name = path.basename(best.name);
      data = best.data;
      fromZip = true;
    }
    const c = consoleByExt(name);
    if (!c) return Object.assign(r, { ok: false, error: 'Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.' });
    const st = fromZip ? null : fs.statSync(file);
    const bytes = fromZip ? data.length : st.size;
    if (bytes < 512 || bytes > c.maxBytes) return Object.assign(r, { ok: false, error: 'O arquivo não parece um jogo desse console.' });
    if (!data) data = fs.readFileSync(file);
    let skip = 0;
    if (c.id === 'nes' && data[0] === 0x4E && data[1] === 0x45 && data[2] === 0x53 && data[3] === 0x1A) skip = 16;
    if (c.id === 'snes' && data.length % 1024 === 512) skip = 512;
    const id = crypto.createHash('sha1').update(data).digest('hex').slice(0, 16);
    const crcAll = hex8(zlib.crc32(data)), crcBody = skip ? hex8(zlib.crc32(data.subarray(skip))) : crcAll;
    r.console = c.id;
    const existing = game(id);
    if (existing) {
      // Jogo que a pessoa tirou da biblioteca e adicionou de novo, de propósito: volta.
      return Object.assign(r, { ok: true, dup: true, id, name: existing.name });
    }
    let title = lookup(c.id, crcAll) || (skip ? lookup(c.id, crcBody) : null);
    const known = !!title;
    if (!title) title = name.replace(/\.[^.]+$/, '');
    const g = { id, console: c.id, name: displayName(title), title, known, size: bytes, crc: crcAll, added: Date.now(), cover: false, kids: false };
    if (fromZip) {
      g.file = id + '.' + extension(name);
      g.src = path.resolve(file);   // o .zip de onde veio: a pasta não lê ele de novo
      if (!write(p('roms', g.file), data)) return Object.assign(r, { ok: false, error: 'Não deu para guardar o jogo.' });
    } else {
      g.path = path.resolve(file);
    }
    const all = games();
    all.push(g);
    saveGames(all);
    const pc = pcData();
    if (g.path && pc.removed.includes(g.path)) { pc.removed = pc.removed.filter(x => x !== g.path); savePc(pc); }
    return Object.assign(r, { ok: true, id, name: g.name });
  } catch {
    return Object.assign(r, { ok: false, error: 'Não deu para ler esse arquivo.' });
  }
}

/* ---------------- Pastas de jogos (só no PC) ----------------
   As pastas escolhidas ficam guardadas: ao abrir, o GameHub procura nelas os jogos novos. */
const pcData = () => Object.assign({ folders: [], removed: [] }, readJson(p('pc.json'), {}));
const savePc = o => writeJson(p('pc.json'), o);
const GAME_EXT = new Set(CONSOLES.flatMap(c => c.ext).concat('zip'));

/** Arquivos de jogo na pasta e nas subpastas (até 6 níveis e 5000 arquivos). */
function scan(dir, out = [], depth = 0) {
  if (depth > 6 || out.length >= 5000) return out;
  let list;
  try { list = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of list) {
    if (e.name.startsWith('.')) continue;
    const f = path.join(dir, e.name);
    if (e.isDirectory()) scan(f, out, depth + 1);
    else if (GAME_EXT.has(extension(e.name))) out.push(f);
  }
  return out;
}
function addFolder(dir) {
  const pc = pcData();
  const abs = path.resolve(dir);
  if (!pc.folders.includes(abs)) { pc.folders.push(abs); savePc(pc); }
  return abs;
}
function removeFolder(dir) {
  const pc = pcData();
  pc.folders = pc.folders.filter(f => f !== dir);
  return savePc(pc);
}
/** Arquivos das pastas que ainda não estão na biblioteca (pelo caminho; o conteúdo é conferido ao
    importar). Os que a pessoa tirou da biblioteca ficam de fora até ela adicionar de novo. */
function newFiles(dirs, withRemoved) {
  const known = new Set(games().flatMap(g => [g.path, g.src]).filter(Boolean).concat(withRemoved ? [] : pcData().removed));
  const out = [];
  for (const d of dirs) for (const f of scan(d)) if (!known.has(path.resolve(f))) out.push(f);
  return out;
}

module.exports = {
  ID, SLOT, CONSOLES, consoleById, init, p, statesDir, sramFile, cheatsFile, coverFile, read, readJson, write, writeJson,
  games, game, romPath, update, remove, recordPlay, setFavorite, list, states,
  DEFAULTS, config, setConfig, effective, players, setPlayer, clearPlayer, storage,
  addFile, pcData, addFolder, removeFolder, newFiles,
};
