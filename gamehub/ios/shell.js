// Onix GameHub no iPhone (site instalável): o papel do main.js, store.js, covers.js, zip.js e
// preload.js do PC, dentro do navegador. As telas são as mesmas (hub.html e menu.html, cada uma
// num iframe); o jogo é o game.js do PC com o controle na tela (touch.js).
//
// Tudo fica no IndexedDB do aparelho:
//   kv     textos, os mesmos arquivos do PC (library.json, config.json, players.json, played.json,
//          touch.json, cheats/<id>.json, options-<console>-<idioma>.json) e states.json (a lista
//          dos estados salvos, que as telas pedem sem esperar)
//   files  bytes: rom/<id>, cover/<id>.jpg, srm/<id>, state/<id>/<n> e shot/<id>/<n>.jpg
// As telas chamam a ponte sem esperar resposta (como a do Android): os textos são lidos para a
// memória ao abrir e gravados no banco em seguida. O sw.js entrega capas e miniaturas às telas.
'use strict';

const VERSION = '__VERSION__';
const ID = /^[0-9a-f]{16}$/, SLOT = /^(auto|[1-4])$/;
const MB = 1024 * 1024;
const LANG = (navigator.language || '').toLowerCase().startsWith('es') ? 'es' : 'pt';
const tr = (pt, es) => (LANG === 'es' ? es : pt);
const $ = s => document.querySelector(s);
const ok = id => typeof id === 'string' && ID.test(id);

/* ---------------- Banco ---------------- */
let db = null;
const KV = new Map();
const req = r => new Promise((res, rej) => { r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
function openDb() {
  return new Promise((res, rej) => {
    const r = indexedDB.open('onix-gamehub', 1);
    r.onupgradeneeded = () => { r.result.createObjectStore('kv'); r.result.createObjectStore('files'); };
    r.onsuccess = () => res(r.result);
    r.onerror = () => rej(r.error);
  });
}
const store = (name, mode) => db.transaction(name, mode || 'readonly').objectStore(name);
/** Termina só quando o aparelho confirma a gravação. */
function commit(name, fn) {
  return new Promise((res, rej) => {
    const tx = db.transaction(name, 'readwrite');
    fn(tx.objectStore(name));
    tx.oncomplete = () => res(true);
    tx.onerror = tx.onabort = () => rej(tx.error);
  });
}
const failedWrite = e => { console.error(e); emit('toast', 'Não deu para gravar: falta espaço no aparelho?'); return false; };
const read = k => (KV.has(k) ? KV.get(k) : null);
function readJson(k, def) { try { const s = read(k); return s ? JSON.parse(s) : def; } catch (_) { return def; } }
function write(k, text) { KV.set(k, text); commit('kv', s => s.put(text, k)).catch(failedWrite); return true; }
const writeJson = (k, o) => write(k, JSON.stringify(o));
function drop(k) { KV.delete(k); commit('kv', s => s.delete(k)).catch(failedWrite); }
const fileGet = k => req(store('files').get(k));
const filePut = (k, data) => commit('files', s => s.put(data, k));
const fileDel = k => commit('files', s => s.delete(k)).catch(failedWrite);
const fileDelUnder = prefix => commit('files', s => s.delete(IDBKeyRange.bound(prefix, prefix + '￿'))).catch(failedWrite);
const bytes = u => (u.byteOffset === 0 && u.byteLength === u.buffer.byteLength ? u.buffer : u.buffer.slice(u.byteOffset, u.byteOffset + u.byteLength));

/* ---------------- Consoles ---------------- */
const B = 0, Y = 1, SELECT = 2, START = 3, A = 8, X = 9, L = 10, R = 11;
const CONSOLES = [
  { id: 'nes', core: 'fceumm', thumbs: 'Nintendo - Nintendo Entertainment System', ext: ['nes', 'unf', 'unif', 'fds'], buttons: [A, B, SELECT, START], maxBytes: 4 * MB },
  { id: 'snes', core: 'snes9x', thumbs: 'Nintendo - Super Nintendo Entertainment System', ext: ['sfc', 'smc', 'swc', 'fig'], buttons: [A, B, X, Y, L, R, SELECT, START], maxBytes: 12 * MB },
  { id: 'gba', core: 'mgba', thumbs: 'Nintendo - Game Boy Advance', ext: ['gba'], buttons: [A, B, L, R, SELECT, START], maxBytes: 33 * MB },
  { id: 'md', core: 'gpgx', thumbs: 'Sega - Mega Drive - Genesis', ext: ['md', 'gen', 'smd', 'bin'], buttons: [B, A, Y, X, L, R, START, SELECT], maxBytes: 10 * MB },
];
const extension = f => { const d = String(f || '').lastIndexOf('.'); return d < 0 ? '' : f.slice(d + 1).toLowerCase(); };
const baseName = f => String(f).split(/[\\/]/).pop();
const consoleById = id => CONSOLES.find(c => c.id === id) || null;
const consoleByExt = f => { const e = extension(f); return CONSOLES.find(c => c.ext.includes(e)) || null; };

/* ---------------- Biblioteca ---------------- */
const games = () => readJson('library.json', {}).games || [];
const saveGames = g => writeJson('library.json', { version: 1, games: g });
const game = id => games().find(g => g.id === id) || null;
const played = () => readJson('played.json', {});
const statesAll = () => readJson('states.json', {});
function update(id, fields) {
  const g = games(), o = g.find(x => x.id === id);
  if (!o) return false;
  Object.assign(o, fields);
  return saveGames(g);
}
function removeGame(id) {
  const g = games(), gone = g.find(x => x.id === id);
  if (!gone) return false;
  saveGames(g.filter(x => x !== gone));
  fileDel('rom/' + id); fileDel('cover/' + id + '.jpg'); fileDel('srm/' + id);
  fileDelUnder('state/' + id + '/'); fileDelUnder('shot/' + id + '/');
  drop('cheats/' + id + '.json');
  const all = statesAll();
  if (all[id]) { delete all[id]; writeJson('states.json', all); }
  const pl = played();
  if (pl[id]) { delete pl[id]; writeJson('played.json', pl); }
  const cfg = config();
  if (cfg.game && cfg.game[id]) { delete cfg.game[id]; writeJson('config.json', cfg); }
  refreshStorage();
  return true;
}
function recordPlay(id, seconds) {
  const all = played(), o = all[id] || {};
  o.last = Date.now();
  o.time = (o.time || 0) + Math.max(0, Math.round(seconds));
  all[id] = o;
  writeJson('played.json', all);
}
function setFavorite(id, fav) {
  const all = played(), o = all[id] || {};
  o.fav = !!fav;
  all[id] = o;
  return writeJson('played.json', all);
}
function list() {
  const pl = played(), st = statesAll();
  return games().map(g => {
    const x = Object.assign({}, g), q = pl[g.id];
    if (q) { x.last = q.last || 0; x.time = q.time || 0; x.fav = !!q.fav; }
    x.resume = !!(st[g.id] && st[g.id].auto);
    x.missing = !!g.romMissing;
    return x;
  });
}
function states(id) {
  const out = [], all = statesAll()[id] || {};
  for (const slot of ['auto', '1', '2', '3', '4']) if (all[slot]) out.push(Object.assign({ slot }, all[slot]));
  return out;
}
function saveCheats(id, text) {
  if (typeof text !== 'string' || text.length > 64 * 1024) return false;
  try {
    const out = [];
    for (const o of JSON.parse(text).slice(0, 100)) {
      const code = String(o.code || '').trim(), desc = String(o.desc || '').trim();
      if (!code || code.length > 200 || desc.length > 80) continue;
      out.push({ code, desc, on: !!o.on });
    }
    return writeJson('cheats/' + id + '.json', out);
  } catch (_) { return false; }
}

/* ---------------- Configurações (as mesmas do PC) ---------------- */
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
choice('sounds', 'on', 'onix', 'on', 'off');
choice('motion', 'on', 'onix', 'on', 'off');
range('brightness', 100, 70, 140);
range('saturation', 100, 0, 150);
range('scanStrength', 40, 10, 80);
range('curvature', 2, 0, 4);
flag('vignette', false);
flag('frameBlend', false);
choice('backdrop', 'ambient', 'ambient', 'black', 'glass');
const CORE_KEY = /^[A-Za-z0-9_.-]{1,80}$/;
const config = () => readJson('config.json', {});
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
function setConfig(scope, key, value) {
  if (typeof scope !== 'string' || typeof key !== 'string') return false;
  let v = null;
  if (value !== null && value !== undefined) { try { v = JSON.parse(value); } catch (_) { return false; } }
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
  return writeJson('config.json', all);
}
function effective(consoleId, id) {
  const out = Object.assign({}, DEFAULTS, { turbo: 0 }), core = {}, remap = {}, all = config();
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

/* ---------------- Controle 1 e 2 ---------------- */
const players = () => readJson('players.json', {});
function setPlayer(player, padId, name) {
  if (player < 1 || player > 2 || !padId) return false;
  const all = players(), old = all[player];
  for (const q of ['1', '2']) if (q !== String(player) && all[q] && all[q].d === padId) { if (old) all[q] = old; else delete all[q]; }
  all[player] = { d: padId, n: name };
  return writeJson('players.json', all);
}
function clearPlayer(player) { const all = players(); delete all[player]; return writeJson('players.json', all); }

/* ---------------- Espaço ---------------- */
let storageInfo = { total: 0, free: 0, reserve: 0, available: 0, games: 0, used: 0 };
async function refreshStorage() {
  try {
    const { quota = 0, usage = 0 } = await navigator.storage.estimate();
    storageInfo = { total: quota, free: Math.max(0, quota - usage), reserve: 0, available: Math.max(0, quota - usage), games: games().reduce((t, g) => t + (g.size || 0), 0), used: usage };
  } catch (_) { /* sem informação */ }
}

/* ---------------- Zip ----------------
   Lê .zip de jogo (como o zip.js do PC) e lê e escreve o arquivo de backup dos saves. */
const CRC = new Int32Array(256).map((_, n) => { for (let k = 0; k < 8; k++) n = n & 1 ? 0xEDB88320 ^ (n >>> 1) : n >>> 1; return n; });
function crc32(u) { let c = -1; for (let i = 0; i < u.length; i++) c = CRC[(c ^ u[i]) & 255] ^ (c >>> 8); return ~c >>> 0; }
const hex8 = n => (n >>> 0).toString(16).padStart(8, '0');
function zipEntries(u) {
  const v = new DataView(u.buffer, u.byteOffset, u.byteLength);
  let eocd = -1;
  for (let i = u.length - 22; i >= Math.max(0, u.length - 22 - 65535); i--) if (v.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) return [];
  const count = v.getUint16(eocd + 10, true), out = [], dec = new TextDecoder();
  let pos = v.getUint32(eocd + 16, true);
  for (let n = 0; n < count && pos + 46 <= u.length; n++) {
    if (v.getUint32(pos, true) !== 0x02014b50) break;
    const nlen = v.getUint16(pos + 28, true), xlen = v.getUint16(pos + 30, true), clen = v.getUint16(pos + 32, true);
    out.push({ name: dec.decode(u.subarray(pos + 46, pos + 46 + nlen)), method: v.getUint16(pos + 10, true), csize: v.getUint32(pos + 20, true), usize: v.getUint32(pos + 24, true), local: v.getUint32(pos + 42, true) });
    pos += 46 + nlen + xlen + clen;
  }
  return out;
}
async function zipExtract(u, e) {
  const v = new DataView(u.buffer, u.byteOffset, u.byteLength);
  if (e.local + 30 > u.length || v.getUint32(e.local, true) !== 0x04034b50) return null;
  const start = e.local + 30 + v.getUint16(e.local + 26, true) + v.getUint16(e.local + 28, true);
  const raw = u.subarray(start, start + e.csize);
  if (e.method === 0) return raw.slice();
  if (e.method !== 8) return null;
  const s = new Blob([raw]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  return new Uint8Array(await new Response(s).arrayBuffer());
}
/** Zip sem compressão: saves e estados quase não encolhem, e assim o iPhone não trava. */
function zipBuild(items) {
  const enc = new TextEncoder(), parts = [], central = [];
  let pos = 0;
  const head = (sig, size, fill) => { const b = new Uint8Array(size), d = new DataView(b.buffer); d.setUint32(0, sig, true); fill(d); return b; };
  for (const it of items) {
    const name = enc.encode(it.name), crc = crc32(it.data), len = it.data.length, at = pos;
    const fields = (d, o) => { d.setUint16(o, 20, true); d.setUint16(o + 2, 0x0800, true); d.setUint32(o + 10, crc, true); d.setUint32(o + 14, len, true); d.setUint32(o + 18, len, true); d.setUint16(o + 22, name.length, true); };
    const local = head(0x04034b50, 30, d => fields(d, 4));
    parts.push(local, name, it.data);
    pos += local.length + name.length + len;
    central.push(head(0x02014b50, 46, d => { fields(d, 6); d.setUint32(42, at, true); }), name);
  }
  const cdSize = central.reduce((t, c) => t + c.length, 0);
  const end = head(0x06054b50, 22, d => { d.setUint16(8, items.length, true); d.setUint16(10, items.length, true); d.setUint32(12, cdSize, true); d.setUint32(16, pos, true); });
  return new Blob([...parts, ...central, end], { type: 'application/zip' });
}

/* ---------------- Importar jogos ---------------- */
const DB = {};
async function lookup(consoleId, crc) {
  if (!DB[consoleId]) {
    const m = new Map();
    try {
      const text = await (await fetch('db/' + consoleId + '.tsv')).text();
      for (const line of text.split('\n')) if (line.indexOf('\t') === 8) m.set(line.slice(0, 8), line.slice(9).trim());
    } catch (_) { /* sem a lista: o nome vem do arquivo */ }
    DB[consoleId] = m;
  }
  return DB[consoleId].get(crc) || null;
}
const TAGS = /\s*[([][^)\]]*[)\]]/g;
const ARTICLE = /^(.+?), (The|A|An|Les|Le|La|Die|Der|Das|El|Los|Las|O|Os)( - .*)?$/;
function displayName(title) {
  let s = title.replace(TAGS, '').replace(/_/g, ' ').trim();
  const m = ARTICLE.exec(s);
  if (m) s = m[2] + ' ' + m[1] + (m[3] || '');
  s = s.replace(/\s{2,}/g, ' ').trim() || title.trim();
  return s.length > 80 ? s.slice(0, 80).trim() : s;
}
async function addFile(file) {
  const r = {};
  try {
    let name = baseName(file.name), data = null;
    if (extension(name) === 'zip') {
      const u = new Uint8Array(await file.arrayBuffer());
      let best = null;
      for (const e of zipEntries(u)) {
        if (e.name.endsWith('/') || e.name.includes('..')) continue;
        const c = consoleByExt(e.name);
        if (!c || e.usize > c.maxBytes) continue;
        if (!best || e.usize > best.usize) best = e;
      }
      data = best && await zipExtract(u, best);
      if (!data || data.length !== best.usize) return Object.assign(r, { ok: false, error: 'O .zip não tem nenhum jogo de NES, SNES, GBA ou Mega Drive.' });
      name = baseName(best.name);
    }
    const c = consoleByExt(name);
    if (!c) return Object.assign(r, { ok: false, error: 'Esse tipo de arquivo não é de NES, SNES, GBA ou Mega Drive.' });
    const size = data ? data.length : file.size;
    if (size < 512 || size > c.maxBytes) return Object.assign(r, { ok: false, error: 'O arquivo não parece um jogo desse console.' });
    if (!data) data = new Uint8Array(await file.arrayBuffer());
    let skip = 0;
    if (c.id === 'nes' && data[0] === 0x4E && data[1] === 0x45 && data[2] === 0x53 && data[3] === 0x1A) skip = 16;
    if (c.id === 'snes' && data.length % 1024 === 512) skip = 512;
    const sha = new Uint8Array(await crypto.subtle.digest('SHA-1', data));
    const id = Array.from(sha.subarray(0, 8), b => b.toString(16).padStart(2, '0')).join('');
    const crcAll = hex8(crc32(data)), crcBody = skip ? hex8(crc32(data.subarray(skip))) : crcAll;
    r.console = c.id;
    const existing = game(id);
    if (existing) {
      // Veio de um backup (sem o arquivo do jogo): agora o jogo volta a abrir.
      if (existing.romMissing) { await filePut('rom/' + id, bytes(data)); update(id, { romMissing: false }); }
      return Object.assign(r, { ok: true, dup: true, id, name: existing.name });
    }
    let title = await lookup(c.id, crcAll) || (skip ? await lookup(c.id, crcBody) : null);
    const known = !!title;
    if (!title) title = name.replace(/\.[^.]+$/, '');
    await filePut('rom/' + id, bytes(data));
    const all = games();
    all.push({ id, console: c.id, name: displayName(title), title, known, size, crc: crcAll, added: Date.now(), cover: false, kids: false, ext: extension(name) });
    saveGames(all);
    return Object.assign(r, { ok: true, id, name: displayName(title) });
  } catch (e) {
    console.error(e);
    return Object.assign(r, { ok: false, error: e && e.name === 'QuotaExceededError' ? 'Não deu para guardar o jogo.' : 'Não deu para ler esse arquivo.' });
  }
}
let importing = false;
async function importFiles(files) {
  if (importing || !files.length) return;
  importing = true;
  try {
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => { });
    emit('upload', { type: 'scan', count: files.length });
    let added = 0;
    for (const f of files) {
      const name = baseName(f.name);
      emit('upload', { type: 'start', file: name, name, size: 1 });
      const res = await addFile(f);
      emit('upload', Object.assign({ type: 'done', file: name }, res));
      if (res.ok && !res.dup) { added++; coverLater(res.id); }
    }
    emit('upload', { type: 'finished', count: files.length });
    await refreshStorage();
    if (added) emit('library');
  } finally {
    importing = false;
  }
}
/** Abre o seletor do iPhone. Sem "accept": com ele, o iOS deixa cinza os .nes, .sfc e parecidos. */
function pickFiles(multiple, done) {
  const doc = hubFrame.contentDocument, input = doc.createElement('input');
  input.type = 'file';
  input.multiple = multiple;
  input.style.cssText = 'position:fixed;left:-999px;opacity:0';
  input.addEventListener('change', () => { const f = Array.from(input.files); input.remove(); done(f); });
  input.addEventListener('cancel', () => input.remove());
  doc.body.append(input);
  input.click();
}

/* ---------------- Capas ----------------
   A imagem da caixa do libretro-thumbnails, pelo GitHub (o thumbnails.libretro.com não libera
   a leitura por sites), na moldura 3:4 do GameHub. Mesma regra do covers.js do PC. */
const HEIGHT = 420, WIDTH = 315, TRIES = 3, FIT = 2, RETRY_MS = 12 * 60 * 60 * 1000;
const thumbDir = c => 'https://raw.githubusercontent.com/libretro-thumbnails/' + c.thumbs.replace(/ /g, '_') + '/master/Named_Boxarts/';
const safeName = title => title.replace(/[&*/:`<>?\\|"]/g, '_');
async function download(url) {
  const r = await fetch(url);
  return r.status === 200 ? r.blob() : null;
}
const lists = {};
function listOf(c) {
  if (!lists[c.id]) {
    lists[c.id] = fetch('https://api.github.com/repos/libretro-thumbnails/' + c.thumbs.replace(/ /g, '_') + '/git/trees/master:Named_Boxarts')
      .then(r => (r.ok ? r.json() : Promise.reject(new Error('sem lista'))))
      .then(o => o.tree.map(x => x.path).filter(p => p.endsWith('.png')).map(p => p.slice(0, -4)))
      .catch(() => { delete lists[c.id]; return null; });
  }
  return lists[c.id];
}
const baseKey = s => s.replace(/\s*[([][^)\]]*[)\]]/g, '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/^the\s+|,\s*the\b/g, '').replace(/[^a-z0-9]/g, '');
const BAD = /\((?:[^)]*\b)?(beta|proto|demo|sample|hack|pirate|unl|kiosk|program|aftermarket)\b/i;
const REGIONS = ['usa', 'world', 'europe', 'brazil', 'japan'];
const regionOf = n => ((n.match(/\(([^)]*)\)/) || [])[1] || '').toLowerCase();
async function findByName(c, g) {
  const names = await listOf(c);
  if (!names) return null;
  const keys = new Set([baseKey(g.title || ''), baseKey(g.name || '')].filter(Boolean)), want = regionOf(g.title || '');
  let best = null, bestScore = Infinity;
  for (const n of names) {
    if (!keys.has(baseKey(n))) continue;
    const r = regionOf(n), i = REGIONS.findIndex(x => r.includes(x));
    const score = (BAD.test(n) ? 100 : 0) + (want && r === want ? 0 : 2 + (i < 0 ? REGIONS.length : i) * 2) + n.length / 1000;
    if (score < bestScore) { bestScore = score; best = n; }
  }
  return best;
}
async function fit34(blob) {
  const img = await createImageBitmap(blob), w = img.width, h = img.height;
  const cv = document.createElement('canvas');
  cv.width = WIDTH; cv.height = HEIGHT;
  const g = cv.getContext('2d');
  g.imageSmoothingQuality = 'high';
  const ratio = (w / h) / (WIDTH / HEIGHT), cover = Math.max(WIDTH / w, HEIGHT / h), cw = w * cover, ch = h * cover;
  if (ratio > 0.9 && ratio < 1.1) {
    g.drawImage(img, (WIDTH - cw) / 2, (HEIGHT - ch) / 2, cw, ch);
  } else {
    const s = document.createElement('canvas');
    s.width = Math.max(1, Math.round(w / 16)); s.height = Math.max(1, Math.round(h / 16));
    s.getContext('2d').drawImage(img, 0, 0, s.width, s.height);
    g.drawImage(s, (WIDTH - cw) / 2, (HEIGHT - ch) / 2, cw, ch);
    g.fillStyle = 'rgba(14,8,32,.55)'; g.fillRect(0, 0, WIDTH, HEIGHT);
    const fit = Math.min(WIDTH / w, HEIGHT / h), fw = w * fit, fh = h * fit, fx = (WIDTH - fw) / 2, fy = (HEIGHT - fh) / 2;
    g.fillStyle = 'rgba(0,0,0,.4)'; g.fillRect(fx, fy + 4, fw, fh + 4);
    g.drawImage(img, fx, fy, fw, fh);
  }
  img.close();
  return new Promise(res => cv.toBlob(b => res(b), 'image/jpeg', 0.86));
}
async function grabCover(g, c) {
  try {
    let png = await download(thumbDir(c) + encodeURIComponent(safeName(g.title)) + '.png');
    if (!png) {
      const other = await findByName(c, g);
      if (other) png = await download(thumbDir(c) + encodeURIComponent(other) + '.png');
    }
    const jpg = png && await fit34(png);
    if (!jpg) return false;
    await filePut('cover/' + g.id + '.jpg', await jpg.arrayBuffer());
    return true;
  } catch (_) { return false; }
}
let coverQueue = Promise.resolve(), refreshing = false;
function coverLater(id) {
  coverQueue = coverQueue.then(async () => {
    const g = game(id), c = g && consoleById(g.console);
    if (!c || g.cover) return;
    const done = await grabCover(g, c);
    update(id, Object.assign({ cover: done, coverTries: (g.coverTries || 0) + 1, coverTried: Date.now() }, done ? { coverFit: FIT } : {}));
    if (done) emit('cover', id);
  }).catch(() => { });
}
function coversMissing() {
  const now = Date.now();
  for (const g of games()) {
    if (g.cover || (g.coverTries || 0) >= TRIES) continue;
    if (now - (g.coverTried || 0) < RETRY_MS && (g.coverTries || 0) > 0) continue;
    coverLater(g.id);
  }
}
function coversRefreshAll() {
  if (refreshing) return -1;
  const ids = games().map(g => g.id);
  if (!ids.length) return 0;
  refreshing = true;
  let updated = 0;
  for (const id of ids) {
    coverQueue = coverQueue.then(async () => {
      const g = game(id), c = g && consoleById(g.console);
      if (c && await grabCover(g, c)) { updated++; update(id, { cover: true, coverFit: FIT, coverTries: 0, coverTried: Date.now() }); }
    }).catch(() => { });
  }
  coverQueue = coverQueue.then(() => { refreshing = false; emit('covers', { updated, total: ids.length }); });
  return ids.length;
}

/* ---------------- Backup dos saves ----------------
   Tudo o que a pessoa não consegue recuperar: saves, estados, configurações e a biblioteca.
   Os jogos e as capas ficam de fora (ela tem os arquivos, e o iPhone tem pouco espaço). */
const BACKUP_KV = /^(library|config|players|played|states|touch)\.json$|^cheats\/[0-9a-f]{16}\.json$/;
const BACKUP_FILE = /^(srm\/[0-9a-f]{16}|state\/[0-9a-f]{16}\/(auto|[1-4])|shot\/[0-9a-f]{16}\/(auto|[1-4])\.jpg)$/;
let backup = null;
async function exportSaves() {
  try {
    emit('backup', { state: 'working' });
    const enc = new TextEncoder(), items = [];
    for (const [k, v] of KV) if (BACKUP_KV.test(k)) items.push({ name: 'kv/' + k, data: enc.encode(v) });
    const keys = await req(store('files').getAllKeys());
    for (const k of keys) if (BACKUP_FILE.test(k)) items.push({ name: 'files/' + k, data: new Uint8Array(await fileGet(k)) });
    backup = { blob: zipBuild(items), name: 'onix-gamehub-saves-' + new Date().toISOString().slice(0, 10) + '.zip' };
    emit('backup', { state: 'ready', size: backup.blob.size });
  } catch (e) {
    console.error(e);
    emit('backup', { state: 'error' });
  }
}
/** Precisa vir direto de um toque: o iOS só abre o Compartilhar assim. */
function saveBackup() {
  if (!backup) return false;
  const file = new File([backup.blob], backup.name, { type: 'application/zip' });
  const fallback = () => {
    const a = hubFrame.contentDocument.createElement('a');
    a.href = URL.createObjectURL(backup.blob);
    a.download = backup.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 60000);
  };
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    navigator.share({ files: [file] }).catch(e => { if (e.name !== 'AbortError') fallback(); });
  } else fallback();
  return true;
}
async function importSaves(file) {
  try {
    const u = new Uint8Array(await file.arrayBuffer()), dec = new TextDecoder();
    const entries = zipEntries(u).filter(e => e.name.startsWith('kv/') || e.name.startsWith('files/'));
    if (!entries.some(e => e.name === 'kv/library.json')) { emit('backup', { state: 'badfile' }); return; }
    let count = 0;
    for (const e of entries) {
      const data = await zipExtract(u, e);
      if (!data) continue;
      if (e.name.startsWith('files/')) {
        const k = e.name.slice(6);
        if (BACKUP_FILE.test(k)) { await filePut(k, bytes(data)); count++; }
        continue;
      }
      const k = e.name.slice(3), text = dec.decode(data);
      if (!BACKUP_KV.test(k)) continue;
      if (k === 'library.json') {
        // Jogos do backup que não estão aqui entram sem o arquivo: voltam ao adicionar o arquivo.
        const all = games(), have = new Set(all.map(g => g.id));
        for (const g of JSON.parse(text).games || []) {
          if (!ok(g.id) || !consoleById(g.console) || have.has(g.id)) continue;
          all.push(Object.assign(g, { romMissing: true, cover: false, coverTries: 0, coverTried: 0 }));
        }
        saveGames(all);
      } else if (k === 'states.json') {
        writeJson(k, Object.assign(statesAll(), JSON.parse(text)));
      } else write(k, text);
    }
    await refreshStorage();
    emit('library');
    coversMissing();
    emit('backup', { state: 'restored', count });
  } catch (e) {
    console.error(e);
    emit('backup', { state: 'badfile' });
  }
}

/* ---------------- Ponte do hub ---------------- */
let hubFrame = null, gameFrame = null, editFrame = null, swReg = null;
function emit(type, data) {
  const w = hubFrame && hubFrame.contentWindow;
  if (w && w.onHub) w.onHub(type, data === undefined ? null : data);
}
const PAD_CODES = [96, 97, 99, 100, 102, 103, 104, 105, 109, 108, 106, 107, 19, 20, 21, 22, 110];
const PAD_NAMES = ['A', 'B', 'X', 'Y', 'L1', 'R1', 'L2', 'R2', 'SELECT', 'START', 'L3', 'R3', '▲', '▼', '◀', '▶', 'MODE'];
const KEY_CODES = {
  ArrowUp: 19, ArrowDown: 20, ArrowLeft: 21, ArrowRight: 22, Enter: 66, Space: 62, Backspace: 67, Tab: 61,
  ShiftLeft: 59, ShiftRight: 60, ControlLeft: 113, ControlRight: 114, AltLeft: 57, AltRight: 58,
  Comma: 55, Period: 56, Slash: 76, Semicolon: 74, Quote: 75, BracketLeft: 71, BracketRight: 72, Minus: 69, Equal: 70,
};
for (let i = 0; i < 26; i++) KEY_CODES['Key' + String.fromCharCode(65 + i)] = 29 + i;
for (let i = 0; i < 10; i++) KEY_CODES['Digit' + i] = 7 + i;
const keyCode = code => KEY_CODES[code] || 0;
const keyName = (code, key) => (code.startsWith('Key') ? code.slice(3) : code.startsWith('Digit') ? code.slice(5)
  : { ArrowUp: '▲', ArrowDown: '▼', ArrowLeft: '◀', ArrowRight: '▶', Space: 'Espaço', ShiftLeft: 'Shift', ShiftRight: 'Shift dir.', ControlLeft: 'Ctrl', ControlRight: 'Ctrl dir.', AltLeft: 'Alt', AltRight: 'Alt Gr' }[code] || key.toUpperCase());
const padName = g => g.id.replace(/\s*\((STANDARD GAMEPAD|Vendor:)[^)]*\)\s*/gi, ' ').replace(/\s+/g, ' ').trim() || 'Controle';
const connected = () => Array.from(navigator.getGamepads ? navigator.getGamepads() : []).filter(g => g && g.connected && g.mapping === 'standard');
function padList() {
  const saved = players();
  return connected().map(g => {
    const o = { id: g.index, name: padName(g) };
    for (const n of ['1', '2']) if (saved[n] && saved[n].d === g.id) o.player = Number(n);
    return o;
  });
}
let captureKey = false, capturePlayer = 0;

window.OnixHub = {
  init: () => JSON.stringify({
    profile: 'default', profileName: '', kids: false, lang: LANG, open: '', fromOnix: false, version: VERSION,
    onix: false, onixSound: true, onixMotion: true, mobile: true, pc: false, web: true, folders: [], gamepads: padList(),
  }),
  library: () => JSON.stringify(list()),
  states: id => JSON.stringify(ok(id) ? states(id) : []),
  play: (id, mode) => ok(id) && /^(resume|new|[1-4])$/.test(mode) && openGame(id, mode),
  setFavorite: (id, fav) => ok(id) && setFavorite(id, !!fav),
  rename: (id, name) => {
    if (!ok(id) || typeof name !== 'string') return false;
    name = name.replace(/[\u0000-\u001f\u007f]/g, '').trim();
    return name.length > 0 && name.length <= 80 && update(id, { name });
  },
  setKids: (id, allowed) => ok(id) && update(id, { kids: !!allowed }),
  remove: id => ok(id) && removeGame(id),
  deleteState: (id, slot) => {
    if (!ok(id) || !SLOT.test(slot)) return false;
    const all = statesAll();
    if (!all[id] || !all[id][slot]) return false;
    delete all[id][slot];
    writeJson('states.json', all);
    fileDel('state/' + id + '/' + slot); fileDel('shot/' + id + '/' + slot + '.jpg');
    return true;
  },
  config: () => JSON.stringify(config()),
  effective: (c, id) => JSON.stringify(consoleById(c) ? effective(c, ok(id) ? id : null) : {}),
  setConfig,
  coreOptions: c => (consoleById(c) && read('options-' + c + '-' + LANG + '.json')) || '{"categories":[],"options":[],"later":true}',
  cheats: id => (ok(id) && read('cheats/' + id + '.json')) || '[]',
  setCheats: (id, text) => ok(id) && saveCheats(id, text),
  storage: () => { refreshStorage(); return JSON.stringify(storageInfo); },
  uploadStart: () => '{}',
  uploadStop: () => true,
  editTouch: () => { openEditor(); return true; },
  pickGames: () => { pickFiles(true, importFiles); return true; },
  folders: () => '[]',
  removeFolder: () => false,
  rescan: () => true,
  gamepads: () => JSON.stringify(padList()),
  players: () => {
    const saved = players(), on = connected().map(g => g.id), out = {};
    for (const n of ['1', '2']) if (saved[n]) out[n] = { name: saved[n].n, connected: on.includes(saved[n].d) };
    return JSON.stringify(out);
  },
  capturePad: player => { capturePlayer = player >= 1 && player <= 2 ? player : 0; },
  captureKey: on => { captureKey = !!on; },
  clearPad: player => clearPlayer(player),
  openBluetooth: () => { emit('toast', 'No iPhone: Ajustes → Bluetooth. Depois, é só voltar para o GameHub.'); return true; },
  refreshCovers: () => coversRefreshAll(),
  update: () => JSON.stringify({ state: 'none' }),
  checkUpdate: () => { if (swReg) swReg.update().catch(() => { }); return true; },
  installUpdate: () => true,
  fullscreen: () => false,
  exit: () => false,
  exportSaves: () => { exportSaves(); return true; },
  saveBackup,
  importSaves: () => { pickFiles(false, f => { if (f[0]) importSaves(f[0]); }); return true; },
};

/* ---------------- Jogo ----------------
   Um iframe por partida: o núcleo começa limpo a cada jogo. O som nasce aqui, durante o toque em
   Jogar: o iOS só libera o som criado por um toque do próprio usuário. */
let audio = null;
function newAudio() {
  try {
    if (navigator.audioSession) navigator.audioSession.type = 'playback';   // toca mesmo com a chave do silencioso
    audio = new AudioContext({ latencyHint: 'interactive' });
    audio.resume();
  } catch (_) { audio = null; }
}
function openGame(id, mode) {
  const g = game(id);
  if (gameFrame || !g) return false;
  if (g.romMissing) { emit('toast', 'Falta o arquivo deste jogo: adicione ele de novo em Adicionar jogos.'); return false; }
  newAudio();
  gameFrame = document.createElement('iframe');
  gameFrame.id = 'game';
  gameFrame.allow = 'autoplay; gamepad; screen-wake-lock';
  gameFrame.src = 'game.html?id=' + id + '&mode=' + mode;
  document.body.append(gameFrame);
  gameFrame.addEventListener('load', () => { try { gameFrame.contentWindow.focus(); } catch (_) { } });
  return true;
}
function closeGame() {
  if (!gameFrame) return;
  gameFrame.remove();
  gameFrame = null;
  audio = null;
  emit('library');
}
function openEditor() {
  if (editFrame) return;
  editFrame = document.createElement('iframe');
  editFrame.id = 'editor';
  editFrame.src = 'edit.html';
  document.body.append(editFrame);
}

const GAME = {
  info: id => {
    const g = ok(id) && game(id), c = g && consoleById(g.console);
    if (!g) return null;
    return JSON.stringify({ id, name: g.name, console: c.id, core: c.core, lang: LANG, profile: 'default', kids: false, sounds: true, motion: true, settings: effective(c.id, id), cover: !!g.cover, ext: g.ext });
  },
  effective: id => { const g = ok(id) && game(id); return g ? JSON.stringify(effective(g.console, id)) : '{}'; },
  states: id => JSON.stringify(ok(id) ? states(id) : []),
  setConfig: (id, scope, key, value) => {
    const g = ok(id) && game(id);
    if (!g || (scope !== 'global' && scope !== 'console:' + g.console && scope !== 'game:' + id)) return false;
    return setConfig(scope, key, value);
  },
  cheats: id => (ok(id) && read('cheats/' + id + '.json')) || '[]',
  setCheats: (id, text) => ok(id) && saveCheats(id, text),
  saveOptions: (c, text) => !!consoleById(c) && typeof text === 'string' && text.length < 512 * 1024 && write('options-' + c + '-' + LANG + '.json', text),
  playersSaved: () => JSON.stringify(players()),
  fullscreen: () => false,
  setGameFullscreen: () => false,
  quit: (id, seconds) => { if (ok(id) && seconds > 5) recordPlay(id, seconds); setTimeout(closeGame, 0); return true; },
};
const GAME_ASYNC = {
  rom: id => (ok(id) ? fileGet('rom/' + id) : null),
  readState: (id, slot) => (ok(id) && SLOT.test(slot) ? fileGet('state/' + id + '/' + slot) : null),
  writeState: async (id, slot, data, shot) => {
    if (!ok(id) || !SLOT.test(slot) || !data) return false;
    try {
      await filePut('state/' + id + '/' + slot, bytes(data));
      if (shot && shot.length < 2 * MB) await filePut('shot/' + id + '/' + slot + '.jpg', bytes(shot));
    } catch (e) { return failedWrite(e); }
    const all = statesAll();
    (all[id] || (all[id] = {}))[slot] = { time: Date.now(), shot: !!shot };
    writeJson('states.json', all);
    return true;
  },
  readSram: id => (ok(id) ? fileGet('srm/' + id) : null),
  writeSram: (id, data) => (ok(id) && data ? filePut('srm/' + id, bytes(data)).catch(failedWrite) : false),
};
window.OnixPC = {
  call: (name, ...args) => { try { return Object.prototype.hasOwnProperty.call(GAME, name) ? GAME[name](...args) : null; } catch (e) { console.error(e); return null; } },
  async: async (name, ...args) => (Object.prototype.hasOwnProperty.call(GAME_ASYNC, name) ? GAME_ASYNC[name](...args) : null),
  hub: (name, ...args) => window.OnixHub[name](...args),
  log: msg => console.log(msg),
  keyCode, keyName, padName, padCodes: PAD_CODES, padNames: PAD_NAMES,
  mobile: true,
  audioContext: () => audio,
  AudioNode: AudioWorkletNode,
  touchLayout: () => read('touch.json'),
  touchSave: text => write('touch.json', text),
  closeEditor: () => { if (editFrame) { editFrame.remove(); editFrame = null; } },
};

/* ---------------- Controles no hub (preload.js do PC) ---------------- */
const NAV = { 0: 'Enter', 9: 'Enter', 1: 'Escape', 4: 'PageUp', 5: 'PageDown', 12: 'ArrowUp', 13: 'ArrowDown', 14: 'ArrowLeft', 15: 'ArrowRight' };
const held = {};
let known = '';
function hubKey(k, repeat) {
  const w = hubFrame.contentWindow;
  w.document.dispatchEvent(new w.KeyboardEvent('keydown', { key: k, code: k, bubbles: true, cancelable: true, repeat }));
}
function poll() {
  requestAnimationFrame(poll);
  const pads = connected(), ids = pads.map(g => g.index + g.id).join('|');
  if (ids !== known) { known = ids; emit('gamepads', padList()); }
  if (gameFrame || editFrame) return;
  const now = performance.now();
  for (const g of pads) {
    g.buttons.forEach((b, i) => {
      const k = g.index + ':' + i, down = b.pressed;
      if (down && !held[k]) {
        held[k] = { next: now + 400 };
        if (capturePlayer) {
          const p = capturePlayer;
          capturePlayer = 0;
          emit('padSet', { player: p, name: padName(g), ok: setPlayer(p, g.id, padName(g)) });
          return;
        }
        if (captureKey && PAD_CODES[i]) { captureKey = false; emit('key', { code: PAD_CODES[i], name: PAD_NAMES[i] }); return; }
        if (NAV[i]) hubKey(NAV[i], false);
      } else if (down && /^Arrow/.test(NAV[i] || '') && now >= held[k].next) {
        held[k].next = now + 110;
        hubKey(NAV[i], true);
      } else if (!down) delete held[k];
    });
    const dirs = { ArrowLeft: g.axes[0] < -0.6, ArrowRight: g.axes[0] > 0.6, ArrowUp: g.axes[1] < -0.6, ArrowDown: g.axes[1] > 0.6 };
    for (const [d, on] of Object.entries(dirs)) {
      const k = g.index + ':' + d;
      if (on && !held[k]) { held[k] = { next: now + 400 }; if (!captureKey && !capturePlayer) hubKey(d, false); }
      else if (on && now >= held[k].next) { held[k].next = now + 110; hubKey(d, true); }
      else if (!on) delete held[k];
    }
  }
}

/* ---------------- Abertura ---------------- */
const standalone = () => window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches;
const isIos = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
/** O Safari e o ícone na tela guardam os dados separados: é preciso instalar antes de pôr os jogos. */
function installHint() {
  if (!isIos() || standalone() || sessionStorage.getItem('installSkip')) return;
  $('#install').hidden = false;
  $('#installTitle').textContent = tr('Instale antes de adicionar jogos', 'Instala antes de agregar juegos');
  $('#installText').textContent = tr('No Safari, toque em Compartilhar e depois em Adicionar à Tela de Início. Abra o GameHub pelo ícone novo: os jogos e saves ficam guardados nele, separados do Safari.',
    'En Safari, toca Compartir y luego Agregar a inicio. Abre GameHub desde el ícono nuevo: los juegos y partidas se guardan ahí, separados de Safari.');
  $('#installSkip').textContent = tr('Continuar no Safari', 'Seguir en Safari');
  $('#installSkip').onclick = () => { sessionStorage.setItem('installSkip', '1'); $('#install').hidden = true; };
}
async function boot() {
  try {
    db = await openDb();
    const [keys, values] = await Promise.all([req(store('kv').getAllKeys()), req(store('kv').getAll())]);
    keys.forEach((k, i) => KV.set(k, values[i]));
  } catch (e) {
    console.error(e);
    $('#fatal').hidden = false;
    $('#fatal').textContent = tr('Este navegador não deixou o GameHub guardar dados. Se estiver em aba privada, abra numa aba normal.', 'Este navegador no dejó que GameHub guarde datos. Si estás en una pestaña privada, abre una normal.');
    return;
  }
  await refreshStorage();
  if ('serviceWorker' in navigator) {
    try { swReg = await navigator.serviceWorker.register('sw.js'); } catch (e) { console.error(e); }
  }
  installHint();
  hubFrame = $('#hub');
  hubFrame.addEventListener('load', () => {
    const w = hubFrame.contentWindow;
    w.addEventListener('keydown', e => {
      if (!captureKey || e.repeat) return;
      const code = keyCode(e.code);
      if (!code || e.code === 'Escape') return;
      e.preventDefault(); e.stopImmediatePropagation();
      captureKey = false;
      emit('key', { code, name: keyName(e.code, e.key) });
    }, true);
    coversMissing();
    requestAnimationFrame(poll);
  }, { once: true });
  hubFrame.src = 'hub.html';
}
boot();
