/*
  Onix para Samsung (Tizen): implementa o mesmo contrato do AndroidBridge (ver o comentário no
  início do script do index.html) com as APIs web do Tizen. O build (tizen/build.sh) coloca este
  arquivo dentro do index.html, antes do script da página, porque ela lê a ponte ao abrir.

  A página chama a ponte de forma síncrona, mas no Tizen quase tudo é assíncrono. Por isso a ponte
  responde na hora com o que está salvo (localStorage) e, quando a TV ou a internet respondem,
  avisa a página com Launcher.onAppsChanged(), onPicksChanged(), onNewsChanged() e onInputsChanged().

  As indicações e as notícias são o porte de WeeklyPicks.java e GameNews.java: mesmas fontes,
  mesmos filtros e o mesmo formato de JSON. A rede só acessa os hosts liberados na CSP e no config.xml.
*/
(function () {
'use strict';
if (!window.tizen || !tizen.application) return;   // fora da TV: a página fica no modo navegador

const SEED = /*ONIX_SEED*/null;   // indicações embutidas (android/assets/picks-seed.json), postas pelo build
const SELF = (() => { try { return tizen.application.getCurrentApplication().appInfo.id; } catch (_) { return ''; } })();

/* ---------------- Utilidades ---------------- */
function call(name, ...args) {
  try { if (window.Launcher && typeof Launcher[name] === 'function') return Launcher[name](...args); } catch (e) { console.warn(name + ' falhou', e); }
  return undefined;
}
function load(key) {
  try { return JSON.parse(localStorage.getItem('onix.tz.' + key) || 'null'); } catch (_) { return null; }
}
function save(key, value) {
  try { localStorage.setItem('onix.tz.' + key, JSON.stringify(value)); return true; } catch (_) { return false; }
}

const TIMEOUT = 15000;
function request(method, url, body, type, max) {
  return new Promise((resolve, reject) => {
    const x = new XMLHttpRequest();
    x.open(method, url, true);
    x.timeout = TIMEOUT;
    x.responseType = type;
    if (body != null) x.setRequestHeader('Content-Type', 'application/json');
    x.onload = () => {
      if (x.status !== 200) { reject(new Error('HTTP ' + x.status)); return; }
      const r = x.response;
      const size = type === 'arraybuffer' ? (r ? r.byteLength : 0) : String(r || '').length;
      if (!r || size > max) { reject(new Error('resposta vazia ou grande demais')); return; }
      resolve(r);
    };
    x.onerror = () => reject(new Error('rede'));
    x.ontimeout = () => reject(new Error('tempo esgotado'));
    x.send(body);
  });
}
const getText = (url, max) => request('GET', url, null, 'text', max);
const getBytes = (url, max) => request('GET', url, null, 'arraybuffer', max).then(b => new Uint8Array(b));

function base64(bytes) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(s);
}
function imageType(b) {
  if (b.length >= 3 && b[0] === 0xFF && b[1] === 0xD8) return 'jpeg';
  if (b.length >= 8 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47) return 'png';
  if (b.length >= 12 && b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46
      && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50) return 'webp';
  return null;
}
function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('imagem inválida'));
    img.src = src;
  });
}
/** Cor média da capa (para tingir o fundo do destaque), como o averageColor do Android. */
async function averageColor(dataUrl) {
  try {
    const img = await loadImage(dataUrl);
    const c = document.createElement('canvas');
    c.width = c.height = 1;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0, 1, 1);
    const p = g.getImageData(0, 0, 1, 1).data;
    return '#' + ((1 << 24) | (p[0] << 16) | (p[1] << 8) | p[2]).toString(16).slice(1);
  } catch (_) { return null; }
}
/** Miniatura da notícia em JPEG menor: o localStorage da TV tem só alguns MB. */
async function shrink(dataUrl, maxW) {
  try {
    const img = await loadImage(dataUrl);
    const w = Math.min(maxW, img.naturalWidth), h = Math.round(img.naturalHeight * w / img.naturalWidth);
    if (!w || !h) return null;
    const c = document.createElement('canvas');
    c.width = w; c.height = h;
    c.getContext('2d').drawImage(img, 0, 0, w, h);
    const out = c.toDataURL('image/jpeg', 0.75);
    return out.length < dataUrl.length ? out : dataUrl;
  } catch (_) { return null; }
}

function trimWords(s, max) {
  if (s.length <= max) return s;
  const cut = s.lastIndexOf(' ', max - 1);
  return s.slice(0, cut > max / 2 ? cut : max - 1) + '…';
}
function trimSentence(s, max) {
  if (s.length <= max) return s;
  let cut = s.lastIndexOf('. ', max - 1);
  if (cut > max / 2) return s.slice(0, cut + 1);
  cut = s.lastIndexOf(' ', max - 1);
  return s.slice(0, cut > max / 2 ? cut : max - 1) + '…';
}

/* ---------------- Apps ---------------- */
const APP_ID_RE = /^[a-zA-Z0-9][a-zA-Z0-9_-]*(\.[a-zA-Z0-9_-]+)*$/;
const ICON_FILE_RE = /^file:\/\/\/[A-Za-z0-9._\/-]{1,300}\.(png|jpe?g|webp)$/i;
const SYSTEM_RE = /^(org\.tizen\.|com\.samsung\.|org\.volt\.)/;
let appList = load('apps') || [];

function iconUrl(path) {
  if (typeof path !== 'string' || !path) return null;
  const u = path.charAt(0) === '/' ? 'file://' + path : path;
  return ICON_FILE_RE.test(u) ? u : null;
}
function scanApps() {
  try {
    tizen.application.getAppsInfo(list => {
      const out = [], seen = {};
      for (const a of list) {
        if (!a || !a.show || a.id === SELF || seen[a.id] || !APP_ID_RE.test(a.id) || a.id.length > 150) continue;
        seen[a.id] = true;
        out.push({ label: a.name || a.id, package: a.id, icon: iconUrl(a.iconPath), system: SYSTEM_RE.test(a.id) });
      }
      if (JSON.stringify(out) === JSON.stringify(appList)) return;
      appList = out;
      save('apps', out);
      call('onAppsChanged');
    }, e => console.warn('getAppsInfo falhou', e));
  } catch (e) { console.warn('getAppsInfo indisponível', e); }
}
function knownApp(id) {
  for (const a of appList) if (a.package === id) return a;
  return null;
}
function launch(id) {
  try {
    tizen.application.launch(id, null, () => call('onMessage', 'A TV não deixou abrir este app'));
    return true;
  } catch (_) { return false; }
}

/* ---------------- Netflix ---------------- */
// O ID da Netflix muda conforme o ano da TV; o nome é o jeito mais seguro de achá-la.
const NETFLIX_IDS = ['3201907018807', '11101200001', 'org.tizen.netflix-app'];
function netflixApp() {
  for (const a of appList) if (NETFLIX_IDS.indexOf(a.package) >= 0 || /^netflix$/i.test(a.label)) return a;
  return null;
}
/** Abre a Netflix direto no título (deep link do app da Samsung); se a TV recusar, abre só a Netflix. */
function openNetflixTitle(appId, titleId) {
  try {
    const payload = JSON.stringify({ values: 'm=https://www.netflix.com/title/' + titleId + '&source_type=4' });
    const ctl = new tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', null, null, null,
      [new tizen.ApplicationControlData('PAYLOAD', [payload])]);
    tizen.application.launchAppControl(ctl, appId, null, () => launch(appId));
  } catch (_) {
    launch(appId);
  }
}

/* ---------------- Indicações da semana (porte de WeeklyPicks.java) ---------------- */
const FEED = 'https://itunes.apple.com/br/rss/topmovies/limit=50/json';
const JUSTWATCH = 'https://apis.justwatch.com/graphql';
const JW_IMAGES = 'https://images.justwatch.com';
const JW_QUERY =
  'query($c:Country!,$l:Language!,$f:TitleFilter){popularTitles(country:$c,first:40,filter:$f,sortBy:POPULAR)'
  + '{edges{node{objectType content(country:$c,language:$l){title originalReleaseYear shortDescription posterUrl genres{shortName}}'
  + ' offers(country:$c,platform:WEB,filter:{packages:["nfx"]}){standardWebURL}}}}}';
const POSTER = /^https:\/\/[a-z0-9-]+\.mzstatic\.com\/image\/thumb\/[^?#\s"]+\/\d+x\d+bb\.(png|jpg)$/;
const JW_POSTER = /^\/poster\/\d{1,12}\/\{profile\}\/[a-z0-9-]{1,120}\.\{format\}$/;
const NETFLIX_URL = /^https:\/\/www\.netflix\.com\/(?:[a-z]{2}(?:-[a-z]{2})?\/)?title\/(\d{1,12})\/?$/;
const PICKS_FORMAT = 3, PICKS_COUNT = 8;
const MAX_FEED = 2 * 1024 * 1024, MAX_POSTER = 300 * 1024;
const PICKS_RETRY = 30 * 60 * 1000;
const GENRES = {
  act: 'Ação', ani: 'Animação', cmy: 'Comédia', crm: 'Crime', doc: 'Documentário', drm: 'Drama', fnt: 'Fantasia',
  hst: 'História', hrr: 'Terror', fml: 'Família', msc: 'Música', trl: 'Suspense', rma: 'Romance',
  scf: 'Ficção científica', spt: 'Esporte', war: 'Guerra', wsn: 'Faroeste', rly: 'Reality',
};

/** Semana ISO 8601 (segunda a domingo), ex.: "2026-W43". */
function currentWeek() {
  const d = new Date();
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  t.setUTCDate(t.getUTCDate() + 3 - (t.getUTCDay() + 6) % 7);   // quinta-feira da mesma semana
  const jan4 = new Date(Date.UTC(t.getUTCFullYear(), 0, 4));
  const week = 1 + Math.round(((t - jan4) / 86400000 - 3 + (jan4.getUTCDay() + 6) % 7) / 7);
  return t.getUTCFullYear() + '-W' + week;
}
/** Sorteio reproduzível com semente = semana (mulberry32). */
function seededRandom(text) {
  let a = 0;
  for (let i = 0; i < text.length; i++) a = (Math.imul(a, 31) + text.charCodeAt(i)) | 0;
  return () => {
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
/** Mais peso para o topo do ranking, mas embaralhado pela semana. */
function weightedOrder(n, rnd, spread) {
  const score = [], order = [];
  for (let i = 0; i < n; i++) { score[i] = i + rnd() * spread; order.push(i); }
  return order.sort((a, b) => score[a] - score[b]);
}
function cleanTitle(t) {
  return String(t).replace(/\s*\[[^\]]*\]/g, '')
    .replace(/\s*"vers[aã]o[^"]*"/gi, '')
    .replace(/\s*\(vers[aã]o[^)]*\)/gi, '')
    .trim();
}
/** Evita a semana inteira de uma franquia só (ex.: vários Harry Potter). */
function franchise(title) {
  const head = title.split(/:| - | – /)[0].toLowerCase();
  const key = [];
  for (const w of head.split(/\s+/)) {
    if (/^(o|a|os|as|um|uma|the)$/.test(w)) continue;
    key.push(w);
    if (key.length === 2) break;
  }
  return key.join(' ');
}
async function putPoster(it, jpg) {
  it.poster = 'data:image/jpeg;base64,' + base64(jpg);
  const color = await averageColor(it.poster);
  if (color) it.color = color;
}

async function fetchNetflix(rnd, out, franchises) {
  const body = JSON.stringify({
    query: JW_QUERY,
    // "pt" devolve nomes de Portugal (ex.: "Profundo" em vez de "Profundezas")
    variables: { c: 'BR', l: 'pt-BR', f: { packages: ['nfx'], objectTypes: ['MOVIE', 'SHOW'] } },
  });
  const res = JSON.parse(await request('POST', JUSTWATCH, body, 'text', MAX_FEED));
  const edges = res.data.popularTitles.edges;
  for (const idx of weightedOrder(edges.length, rnd, 15)) {
    if (out.length >= PICKS_COUNT) break;
    try {
      const node = edges[idx].node, c = node.content;
      const title = cleanTitle(c.title);
      if (!title || title.length > 120) continue;
      let id = null;
      for (const o of node.offers || []) {
        const m = NETFLIX_URL.exec(o && o.standardWebURL || '');
        if (m) { id = m[1]; break; }
      }
      if (!id) continue;
      const path = c.posterUrl || '';
      if (!JW_POSTER.test(path) || franchises.has(franchise(title))) continue;
      franchises.add(franchise(title));
      const jpg = await getBytes(JW_IMAGES + path.replace('{profile}', 's332').replace('{format}', 'jpg'), MAX_POSTER);
      if (imageType(jpg) !== 'jpeg') continue;
      const it = { title, provider: 'netflix', netflixId: id, kind: node.objectType === 'SHOW' ? 'Série' : 'Filme' };
      const g = c.genres && c.genres[0] && GENRES[c.genres[0].shortName];
      if (g) it.genre = g;
      const year = c.originalReleaseYear;
      if (year > 1900 && year < 3000) it.year = String(year);
      it.summary = trimWords(String(c.shortDescription || '').trim(), 260);
      await putPoster(it, jpg);
      out.push(it);
    } catch (_) {
      // Entrada incompleta: pula para a próxima.
    }
  }
}

async function fetchItunes(rnd, out, franchises) {
  const entries = JSON.parse(await getText(FEED, MAX_FEED)).feed.entry;
  for (const idx of weightedOrder(entries.length, rnd, 25)) {
    if (out.length >= PICKS_COUNT) break;
    try {
      const e = entries[idx];
      const title = cleanTitle(e['im:name'].label);
      if (!title || franchises.has(franchise(title))) continue;
      franchises.add(franchise(title));
      const imgs = e['im:image'];
      let src = imgs[imgs.length - 1].label;
      if (!POSTER.test(src)) continue;
      src = src.replace(/\/\d+x\d+bb\.(png|jpg)$/, '/300x450bb.jpg');
      const jpg = await getBytes(src, MAX_POSTER);
      if (imageType(jpg) !== 'jpeg') continue;
      const it = { title, genre: e.category.attributes.label || '' };
      const date = e['im:releaseDate'] && e['im:releaseDate'].label || '';
      if (date.length >= 4) it.year = date.slice(0, 4);
      it.summary = trimWords(e.summary && e.summary.label || '', 260);
      await putPoster(it, jpg);
      out.push(it);
    } catch (_) {
      // Entrada incompleta: pula para a próxima.
    }
  }
}

async function fetchPicks(week) {
  const out = [], franchises = new Set(), rnd = seededRandom(week);
  let netflixError = null;
  try { await fetchNetflix(rnd, out, franchises); } catch (e) { netflixError = e; }   // JustWatch fora do ar: segue só com a iTunes
  if (out.length < PICKS_COUNT) {
    try { await fetchItunes(rnd, out, franchises); } catch (e) { if (!out.length) throw netflixError || e; }
  }
  return out;
}

const picks = { week: null, items: [], ids: {}, lastAttempt: 0, inFlight: false };
function applyPicks(week, items) {
  picks.week = week;
  picks.items = items;
  picks.ids = {};
  for (const it of items) if (it && typeof it.title === 'string') picks.ids[it.title] = /^\d{1,12}$/.test(it.netflixId || '') ? it.netflixId : null;
}
(() => {
  const o = load('picks');
  // Cache de formato antigo: mostra enquanto baixa o novo (semana "vencida").
  if (o && Array.isArray(o.items)) applyPicks(o.v === PICKS_FORMAT ? o.week : null, o.items);
  else if (SEED && Array.isArray(SEED.items)) applyPicks(null, SEED.items);
})();

/** Baixa a seleção nova se a semana virou. force = botão Atualizar; networkBack = a internet voltou. */
async function refreshPicks(networkBack, force) {
  const now = currentWeek();
  if (picks.inFlight || (!force && now === picks.week)) return false;
  const t = Date.now();
  if (!force && !networkBack && picks.lastAttempt && t - picks.lastAttempt < PICKS_RETRY) return false;
  picks.lastAttempt = t;
  picks.inFlight = true;
  let items;
  try { items = await fetchPicks(now); } catch (_) { return false; } finally { picks.inFlight = false; }
  if (!items.length) return false;
  save('picks', { v: PICKS_FORMAT, week: now, items });
  applyPicks(now, items);
  return true;
}

/* ---------------- Notícias dos games (porte de GameNews.java) ---------------- */
const FEEDS = [
  ['Adrenaline', 'https://www.adrenaline.com.br/feed/', 'Games'],
  ['Eurogamer.pt', 'https://www.eurogamer.pt/feed', null],
];
const LINK = /^https:\/\/www\.(adrenaline\.com\.br|eurogamer\.pt)\/[^\s"'<>]{1,400}$/;
const ADR_IMG = /src="(https:\/\/www\.adrenaline\.com\.br\/wp-content\/uploads\/[\w/.-]{1,200}?)-\d+x\d+\.(webp|jpg|jpeg|png)"/;
const ADR_SRCSET = /srcset="([^"]{1,4000})"/;
const ADR_SRC = /(https:\/\/www\.adrenaline\.com\.br\/wp-content\/uploads\/[\w/.-]{1,200}?\.(?:webp|jpg|jpeg|png)) (\d{2,4})w/g;
const IMG_W = 1100;
const EG_IMG = /^(https:\/\/assetsio\.gnwcdn\.com\/[\w/.-]{1,200})(\?.*)?$/;
const PARA = /<p[^>]*>([\s\S]*?)<\/p>/gi;
const SKIP = /^(leia (também|mais)|veja (também|mais)|ler mais|link de compra|confira|via:|fonte:)/i;
const PER_FEED = 8, NEWS_COUNT = 12, MAX_IMG = 400 * 1024;
const NEWS_FRESH = 3 * 60 * 60 * 1000, NEWS_RETRY = 15 * 60 * 1000;

const htmlParser = new DOMParser();
function text(html) {
  const s = String(html)
    .replace(/<(script|style|figure|figcaption)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<img[^>]*>/gi, ' ')
    .replace(/<\/?(p|br|div|li|h\d)[^>]*>/gi, ' ');
  // Documento inerte: nada dele é executado nem baixado, só o texto é lido.
  const t = htmlParser.parseFromString('<body>' + s, 'text/html').body.textContent || '';
  return t.replace(/ /g, ' ').replace(/￼/g, '').replace(/[ \t\x0B\f\r]+/g, ' ')
    .replace(/\s*\n\s*/g, ' ').trim();
}
/** Primeiros parágrafos do texto (até ~1000 caracteres), sem "leia também" e links de compra. */
function summary(html) {
  if (html == null) return '';
  const parts = [];
  let len = 0, m;
  PARA.lastIndex = 0;
  while (len < 700 && (m = PARA.exec(html))) {
    const t = text(m[1]);
    if (t.length < 40 || SKIP.test(t) || t.indexOf('Link de compra') >= 0) continue;
    parts.push(t);
    len += t.length + 2;
  }
  const out = parts.length ? parts.join('\n\n') : text(html).replace(/\s*Ler mais$/, '');
  return trimSentence(out, 1100);
}
/** Endereço da imagem na maior versão até IMG_W px (nítida no leitor sem pesar no download). */
function imageUrl(desc, media) {
  if (desc) {
    const set = ADR_SRCSET.exec(desc);
    if (set) {
      let best = null, bestW = 0, m;
      ADR_SRC.lastIndex = 0;
      while ((m = ADR_SRC.exec(set[1]))) {
        const w = Number(m[2]);
        if (w <= IMG_W && w > bestW) { bestW = w; best = m[1]; }
      }
      if (best) return best;
    }
    const m = ADR_IMG.exec(desc);
    if (m) return m[1] + '-768x480.' + m[2];
  }
  if (media) {
    const m = EG_IMG.exec(media);
    if (m) return m[1] + '?width=' + IMG_W + '&quality=75&format=jpg';
  }
  return null;
}
function childText(el, tag) {
  const c = el.getElementsByTagName(tag)[0];
  return c ? c.textContent : null;
}
function parseFeed(source, xml, category) {
  const doc = new DOMParser().parseFromString(xml, 'text/xml');
  const out = [];
  for (const item of Array.from(doc.getElementsByTagName('item'))) {
    if (out.length >= PER_FEED) break;
    const cats = Array.from(item.getElementsByTagName('category')).map(c => c.textContent.trim());
    // Adrenaline: só Games (o feed mistura hardware). Eurogamer: sem categoria costuma ser cinema/TV.
    if (category ? !cats.some(c => c.toLowerCase() === category.toLowerCase()) : !cats.length) continue;
    const title = childText(item, 'title'), link = (childText(item, 'link') || '').trim();
    if (title == null || !LINK.test(link)) continue;
    const desc = childText(item, 'description'), content = childText(item, 'content:encoded');
    let sum = summary(content != null ? content : desc);
    if (sum.length < 80 && content != null) sum = summary(desc);
    const it = { source, title: trimSentence(text(title), 160), link, summary: sum };
    if (!it.title || !it.summary) continue;
    it.date = Date.parse((childText(item, 'pubDate') || '').trim()) || 0;
    const media = item.getElementsByTagName('media:content')[0];
    it.image = imageUrl(desc, media ? media.getAttribute('url') : null);
    out.push(it);
  }
  return out;
}
async function thumbnail(url) {
  try {
    const b = await getBytes(url, MAX_IMG);
    const type = imageType(b);
    if (!type) return null;
    return await shrink('data:image/' + type + ';base64,' + base64(b), 960);
  } catch (_) {
    return null;   // sem miniatura a notícia aparece mesmo assim
  }
}
async function fetchNews() {
  let all = [], error = null;
  for (const f of FEEDS) {
    try { all = all.concat(parseFeed(f[0], await getText(f[1], MAX_FEED), f[2])); } catch (e) { error = e; }   // um site fora do ar não derruba o outro
  }
  if (!all.length && error) throw error;
  all.sort((a, b) => b.date - a.date);
  const out = [];
  for (const it of all.slice(0, NEWS_COUNT)) {
    const o = { source: it.source, title: it.title, link: it.link, date: it.date, summary: it.summary };
    const img = it.image ? await thumbnail(it.image) : null;
    if (img) o.image = img;
    out.push(o);
  }
  return out;
}

const news = { fetched: 0, items: [], lastAttempt: 0, inFlight: false };
(() => {
  const o = load('news');
  if (o && Array.isArray(o.items)) { news.fetched = Number(o.fetched) || 0; news.items = o.items; }
})();

async function refreshNews(networkBack, force) {
  const now = Date.now();
  if (news.inFlight || (!force && now - news.fetched >= 0 && now - news.fetched < NEWS_FRESH)) return false;
  if (!force && !networkBack && news.lastAttempt && now - news.lastAttempt < NEWS_RETRY) return false;
  news.lastAttempt = now;
  news.inFlight = true;
  let items;
  try { items = await fetchNews(); } catch (_) { return false; } finally { news.inFlight = false; }
  if (!items.length) return false;
  // Sem espaço para as miniaturas: guarda só o texto (a lista da sessão continua com elas).
  if (!save('news', { fetched: now, items })) save('news', { fetched: now, items: items.map(i => Object.assign({}, i, { image: undefined })) });
  news.fetched = now;
  news.items = items;
  return true;
}

/* ---------------- Entradas HDMI (TV Window) ---------------- */
// O Tizen não troca a entrada da TV inteira para um app comum: o Onix mostra a HDMI em tela cheia
// numa "janela de TV" dele mesmo. Voltar (ou OK) fecha a janela e volta para o Onix.
let inputList = load('inputs') || [];
const sources = {};
let watching = false;

function scanInputs() {
  try {
    tizen.systeminfo.getPropertyValue('VIDEOSOURCE', v => {
      const all = [].concat(v.connected || [], v.disconnected || []).filter(s => s && s.type === 'HDMI' && s.number > 0);
      all.sort((a, b) => a.number - b.number);
      const out = [];
      for (const s of all) {
        const id = 'HDMI:' + s.number;
        if (sources[id]) continue;
        sources[id] = s;
        out.push({ id, label: 'HDMI ' + s.number });
      }
      if (JSON.stringify(out) === JSON.stringify(inputList)) return;
      inputList = out;
      save('inputs', out);
      call('onInputsChanged');
    }, e => console.warn('VIDEOSOURCE falhou', e));
  } catch (e) { console.warn('systeminfo indisponível', e); }
}

const tvStyle = document.createElement('style');
tvStyle.textContent = 'html.onix-tv,html.onix-tv body{background:transparent!important}html.onix-tv body>*{visibility:hidden!important}';
document.head.appendChild(tvStyle);

function showInput(id) {
  const src = sources[id];
  if (!src || !tizen.tvwindow) return false;
  const fail = () => { stopWatching(); call('onMessage', 'A TV não deixou mostrar a ' + id.replace(':', ' ')); };
  try {
    tizen.tvwindow.setSource(src, () => {
      tizen.tvwindow.show(() => {
        watching = true;
        document.documentElement.classList.add('onix-tv');
      }, fail, ['0px', '0px', '100%', '100%'], 'MAIN');
    }, fail, 'MAIN');
    return true;
  } catch (_) { return false; }
}
function stopWatching() {
  const was = watching;
  watching = false;
  document.documentElement.classList.remove('onix-tv');
  try { tizen.tvwindow.hide(() => {}, () => {}, 'MAIN'); } catch (_) {}
  if (was) call('onResume');   // zera a contagem da tela de descanso
}

/* ---------------- Controle remoto ---------------- */
const KEY_BACK = 10009, KEY_EXIT = 10182;
// Fase de captura na window: roda antes de qualquer tecla chegar à página.
window.addEventListener('keydown', e => {
  if (watching) {
    // Vendo a HDMI: só Voltar, OK e Sair fecham; volume e canais ficam com a TV.
    e.preventDefault(); e.stopPropagation();
    if (e.keyCode === KEY_BACK || e.keyCode === 13 || e.keyCode === KEY_EXIT) stopWatching();
    return;
  }
  if (e.keyCode !== KEY_BACK) return;
  e.preventDefault(); e.stopPropagation();
  // Na raiz (Início sem nada aberto), Voltar deixa a TV como estava antes do Onix.
  if (call('onBack') !== true) try { tizen.application.getCurrentApplication().hide(); } catch (_) {}
}, true);
window.addEventListener('keyup', e => { if (watching || e.keyCode === KEY_BACK) { e.preventDefault(); e.stopPropagation(); } }, true);

/* ---------------- Ciclo de vida ---------------- */
async function backgroundRefresh(networkBack) {
  if (await refreshPicks(networkBack, false)) call('onPicksChanged');
  if (await refreshNews(networkBack, false)) call('onNewsChanged');
}
let refreshingAll = false;
async function refreshAll() {
  refreshingAll = true;
  scanApps();
  const p = await refreshPicks(true, true);
  const n = await refreshNews(true, true);
  refreshingAll = false;
  call('onRefreshed', n, p);
}

function start() {
  call('onNetwork', navigator.onLine !== false);
  scanApps();
  scanInputs();
  try {
    tizen.application.addAppInfoEventListener({ oninstalled: scanApps, onupdated: scanApps, onuninstalled: scanApps });
  } catch (_) {}
  window.addEventListener('online', () => { call('onNetwork', true); backgroundRefresh(true); });
  window.addEventListener('offline', () => call('onNetwork', false));
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stopWatching(); return; }
    scanApps();
    backgroundRefresh(false);
  });
  // Como no Android: indicações conferidas de hora em hora, notícias a cada 3 h, só com o Onix aberto.
  setInterval(() => { if (!document.hidden && !refreshingAll) backgroundRefresh(false); }, 10 * 60 * 1000);
  backgroundRefresh(false);
}
// O script da página roda (e cria window.Launcher) antes do DOMContentLoaded.
document.addEventListener('DOMContentLoaded', start);

/* ---------------- O contrato ---------------- */
window.AndroidBridge = Object.freeze({
  platform: () => 'Samsung TV',
  getInstalledApps: () => JSON.stringify(appList),
  launchApp(id) {
    if (typeof id !== 'string' || id === SELF || !knownApp(id)) return false;
    return launch(id);
  },
  getWeeklyPicks: () => JSON.stringify(picks.items),
  getGameNews: () => JSON.stringify(news.items),
  // "title" | "app" | "" — o ID da Netflix nunca vem da página, só da seleção baixada aqui.
  openPick(title) {
    if (typeof title !== 'string' || !(title in picks.ids)) return '';
    const nf = netflixApp();
    if (!nf) return '';
    const id = picks.ids[title];
    if (id) { openNetflixTitle(nf.package, id); return 'title'; }
    return launch(nf.package) ? 'app' : '';
  },
  getTvInputs: () => JSON.stringify(inputList),
  openInput: id => typeof id === 'string' && showInput(id),
  refreshAll() {
    if (refreshingAll) return false;
    refreshAll();
    return true;
  },
  // Sem openSettings, openPictureSettings e powerOff: o Tizen não deixa apps comuns abrirem
  // os menus da TV nem desligá-la, então a página esconde esses botões.
});
})();
