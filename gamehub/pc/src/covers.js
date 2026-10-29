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
let onReady = () => { };
const setListener = fn => { onReady = fn; };

/** O repositório troca &*\/:`<>?\| por _ nos nomes dos arquivos. */
const safe = title => title.replace(/[&*/:`<>?\\|"]/g, '_');

async function download(url) {
  const r = await net.fetch(url, { headers: { 'User-Agent': 'Onix-GameHub' } });
  if (r.status !== 200 || new URL(r.url).hostname !== 'thumbnails.libretro.com') return null;
  const b = Buffer.from(await r.arrayBuffer());
  return b.length <= MAX_BYTES ? b : null;
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

async function fetchCover(id, refit) {
  const g = store.game(id);
  const c = g && store.consoleById(g.console);
  if (!c || (g.cover && !refit)) return;
  let ok = false;
  try {
    const png = await download(HOST + encodeURIComponent(c.thumbs) + '/Named_Boxarts/' + encodeURIComponent(safe(g.title)) + '.png');
    const img = png && fit34(png);
    ok = !!img && store.write(store.coverFile(id), img.toJPEG(86));
  } catch { /* sem internet: tenta depois */ }
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

module.exports = { later: id => later(id, false), fetchMissing, setListener };
