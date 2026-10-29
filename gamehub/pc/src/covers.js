// Capas (Covers.java): a imagem da caixa do repositório do libretro, pelo nome No-Intro do jogo,
// montada na moldura 3:4 do GameHub e guardada em covers/<id>.jpg.
// Caixa quase 3:4: preenche e corta a sobra. Caixa deitada: inteira no meio, sobre ela mesma
// ampliada, desfocada e escurecida. Feito com o nativeImage do Electron, sem bibliotecas.
'use strict';
const { nativeImage, net } = require('electron');
const store = require('./store');

const HOST = 'https://thumbnails.libretro.com/';
const MAX_BYTES = 3 * 1024 * 1024, HEIGHT = 420, WIDTH = HEIGHT * 3 / 4, TRIES = 3, FIT = 2;
const RETRY_MS = 12 * 60 * 60 * 1000;

let queue = Promise.resolve();
let onReady = () => { }, onRefreshed = () => { }, refreshing = false;
const setListener = (fn, done) => { onReady = fn; if (done) onRefreshed = done; };

/** O repositório troca &*\/:`<>?\| por _ nos nomes dos arquivos. */
const safe = title => title.replace(/[&*/:`<>?\\|"]/g, '_');

// redirect "error": a resposta vem sempre do próprio thumbnails.libretro.com. (Conferir r.url não
// serve: no net.fetch do Electron ele vem vazio, e isso fazia toda capa falhar.)
async function download(url, max) {
  const r = await net.fetch(url, { headers: { 'User-Agent': 'Onix-GameHub' }, redirect: 'error' });
  if (r.status !== 200) return null;
  const b = Buffer.from(await r.arrayBuffer());
  return b.length <= (max || MAX_BYTES) ? b : null;
}

/* Sem capa com o nome exato (outra região, nome fora do No-Intro, jogo renomeado): procura na lista
   de capas do console pelo nome sem região e versão. A lista vem uma vez por abertura. */
const LIST_BYTES = 8 * 1024 * 1024;
const lists = {};
function listOf(c) {
  if (!lists[c.id]) {
    lists[c.id] = download(HOST + encodeURIComponent(c.thumbs) + '/Named_Boxarts/', LIST_BYTES).then(html => {
      if (!html) throw new Error('sem lista');
      const names = [];
      for (const m of html.toString('utf8').matchAll(/href="([^"?/]+)\.png"/g)) {
        try { names.push(decodeURIComponent(m[1].replace(/&amp;/g, '&'))); } catch { }
      }
      return names;
    }).catch(() => { delete lists[c.id]; return null; });
  }
  return lists[c.id];
}
/** O nome para comparar: sem (região), [versão], acentos, "The" e pontuação. */
const base = s => s.replace(/\s*[([][^)\]]*[)\]]/g, '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
  .replace(/^the\s+|,\s*the\b/g, '').replace(/[^a-z0-9]/g, '');
const BAD = /\((?:[^)]*\b)?(beta|proto|demo|sample|hack|pirate|unl|kiosk|program|aftermarket)\b/i;
const REGIONS = ['usa', 'world', 'europe', 'brazil', 'japan'];
const region = n => ((n.match(/\(([^)]*)\)/) || [])[1] || '').toLowerCase();
/** A melhor capa com o mesmo nome: a mesma região do jogo, depois EUA, Mundo, Europa, Brasil, Japão. */
async function findByName(c, g) {
  const names = await listOf(c);
  if (!names) return null;
  const keys = new Set([base(g.title || ''), base(g.name || '')].filter(Boolean));
  const want = region(g.title || '');
  let best = null, bestScore = Infinity;
  for (const n of names) {
    if (!keys.has(base(n))) continue;
    const r = region(n), i = REGIONS.findIndex(x => r.includes(x));
    const score = (BAD.test(n) ? 100 : 0) + (want && r === want ? 0 : 2 + (i < 0 ? REGIONS.length : i) * 2) + n.length / 1000;
    if (score < bestScore) { bestScore = score; best = n; }
  }
  return best;
}

/* Pixels BGRA (toBitmap): escurece com a cor do fundo do GameHub e cola a capa por cima. */
function darken(px, a, rgb) {
  for (let i = 0; i < px.length; i += 4) {
    px[i] = px[i] * (1 - a) + rgb[2] * a;
    px[i + 1] = px[i + 1] * (1 - a) + rgb[1] * a;
    px[i + 2] = px[i + 2] * (1 - a) + rgb[0] * a;
    px[i + 3] = 255;
  }
}
function paste(dst, dw, src, sw, sh, x0, y0) {
  for (let y = 0; y < sh; y++) {
    const dy = y0 + y;
    if (dy < 0 || dy >= HEIGHT) continue;
    for (let x = 0; x < sw; x++) {
      const dx = x0 + x;
      if (dx < 0 || dx >= dw) continue;
      const s = (y * sw + x) * 4, d = (dy * dw + dx) * 4, al = src[s + 3] / 255;
      for (let k = 0; k < 3; k++) dst[d + k] = src[s + k] * al + dst[d + k] * (1 - al);
      dst[d + 3] = 255;
    }
  }
}
function shade(dst, dw, x0, y0, w, h) {
  for (let y = Math.max(0, y0); y < Math.min(HEIGHT, y0 + h); y++) {
    for (let x = Math.max(0, x0); x < Math.min(dw, x0 + w); x++) {
      const d = (y * dw + x) * 4;
      for (let k = 0; k < 3; k++) dst[d + k] *= 0.6;
    }
  }
}

function fit34(png) {
  const img = nativeImage.createFromBuffer(png);
  const { width: w, height: h } = img.getSize();
  if (!w || !h || w > 4096 || h > 4096) return null;
  const ratio = (w / h) / (WIDTH / HEIGHT);
  const cover = Math.max(WIDTH / w, HEIGHT / h);
  const cw = Math.round(w * cover), ch = Math.round(h * cover);
  if (ratio > 0.9 && ratio < 1.1) {
    return img.resize({ width: cw, height: ch, quality: 'best' })
      .crop({ x: Math.max(0, (cw - WIDTH) >> 1), y: Math.max(0, (ch - HEIGHT) >> 1), width: WIDTH, height: HEIGHT });
  }
  // Fundo: reduz a ~1/16 e amplia de volta (bilinear, suave), corta no centro e escurece.
  const small = img.resize({ width: Math.max(1, Math.round(w / 16)), height: Math.max(1, Math.round(h / 16)), quality: 'good' });
  const back = small.resize({ width: cw, height: ch, quality: 'best' })
    .crop({ x: Math.max(0, (cw - WIDTH) >> 1), y: Math.max(0, (ch - HEIGHT) >> 1), width: WIDTH, height: HEIGHT });
  const px = Buffer.from(back.toBitmap());
  darken(px, 0x8C / 255, [0x0E, 0x08, 0x20]);
  const fit = Math.min(WIDTH / w, HEIGHT / h);
  const fw = Math.round(w * fit), fh = Math.round(h * fit), fx = (WIDTH - fw) >> 1, fy = (HEIGHT - fh) >> 1;
  shade(px, WIDTH, fx, fy + 4, fw, fh + 4);
  paste(px, WIDTH, img.resize({ width: fw, height: fh, quality: 'best' }).toBitmap(), fw, fh, fx, fy);
  return nativeImage.createFromBitmap(px, { width: WIDTH, height: HEIGHT });
}

/** Baixa a caixa do jogo (pelo nome exato ou, sem ela, pelo nome parecido), monta na moldura e grava. */
async function grab(g, c) {
  try {
    const dir = HOST + encodeURIComponent(c.thumbs) + '/Named_Boxarts/';
    let png = await download(dir + encodeURIComponent(safe(g.title)) + '.png');
    if (!png) {
      const other = await findByName(c, g);
      if (other) png = await download(dir + encodeURIComponent(other) + '.png');
    }
    const img = png && fit34(png);
    return !!img && store.write(store.coverFile(g.id), img.toJPEG(86));
  } catch { return false; }
}

async function fetchCover(id, refit) {
  const g = store.game(id);
  const c = g && store.consoleById(g.console);
  if (!c || (g.cover && !refit)) return;
  const ok = await grab(g, c);
  if (refit) {
    if (ok) store.update(id, { coverFit: FIT, coverTried: Date.now() });
  } else {
    store.update(id, Object.assign({ cover: ok, coverTries: (g.coverTries || 0) + 1, coverTried: Date.now() }, ok ? { coverFit: FIT } : {}));
  }
  if (ok) onReady(id);
}

const later = (id, refit) => { queue = queue.then(() => fetchCover(id, refit)).catch(() => { }); };

/** Ao abrir: tenta de novo as capas que faltaram (sem internet na hora, por exemplo). */
function fetchMissing() {
  const now = Date.now();
  for (const g of store.games()) {
    if (g.cover && (g.coverFit || 0) < FIT) later(g.id, true);
    if (g.cover || (g.coverTries || 0) >= TRIES) continue;
    if (now - (g.coverTried || 0) < RETRY_MS && (g.coverTries || 0) > 0) continue;
    later(g.id, false);
  }
}

/**
 * Botão "Atualizar capas": baixa de novo a capa de todos os jogos, inclusive as que já tinham
 * desistido (3 tentativas). Quem não achar capa fica com a que tinha. Devolve quantos jogos entram
 * (-1 se já estiver atualizando); no fim avisa { updated, total }.
 */
function refreshAll() {
  if (refreshing) return -1;
  const ids = store.games().map(g => g.id);
  if (!ids.length) return 0;
  refreshing = true;
  let updated = 0;
  for (const id of ids) {
    queue = queue.then(async () => {
      const g = store.game(id);
      const c = g && store.consoleById(g.console);
      if (c && await grab(g, c)) {
        updated++;
        store.update(id, { cover: true, coverFit: FIT, coverTries: 0, coverTried: Date.now() });
      }
    }).catch(() => { });
  }
  queue = queue.then(() => { refreshing = false; onRefreshed({ updated, total: ids.length }); });
  return ids.length;
}

module.exports = { later: id => later(id, false), fetchMissing, refreshAll, setListener };
