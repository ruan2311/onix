// RetroAchievements no PC: o papel do RetroAchievements.java e do Traducao.java do Android.
// A conta fica em profiles/default/retroachievements.json = { user, token, score, avatar, at } (a senha nunca é guardada);
// o que cada jogo tem, em profiles/default/ra-jogos.json (o mesmo formato do Android, lido pela aba Conquistas).
// Os pedidos à internet do motor (jni/cheevos.c no WebAssembly) passam por aqui: o game.js manda e devolve a resposta.
// Os textos das conquistas só existem em inglês: traduzidos pelo MyMemory e guardados em ra-traducao-<pt|es>.json.
'use strict';
const { net } = require('electron');
const path = require('path');

const LOGIN_URL = 'https://retroachievements.org/dorequest.php';
const CONTACT = 'escliburh@gmail.com';   // e-mail de contato do MyMemory: 50.000 letras por dia (sem ele, 5.000)
let store = null, version = '0', userData = '', lastRefresh = 0;

function init(s, v, dir) { store = s; version = v; userData = dir; }
const prof = (...a) => path.join(store.p('profiles', 'default'), ...a);
const agent = clause => 'CobaltGameHub/' + version + ' (Windows)' + (clause ? ' ' + clause : '');
let clause = '';
/** O "rcheevos/x.y" do motor (o game.js manda quando abre). */
const setClause = c => { if (typeof c === 'string' && c.length < 64) clause = c; };

function account() {
  const a = store.readJson(prof('retroachievements.json'), null);
  return a && a.user && a.token ? a : null;
}
const connected = () => !!account();
function logout() { try { require('fs').unlinkSync(prof('retroachievements.json')); } catch { } }
function updateAccount(o) {
  const a = account();
  if (!a) return false;
  const before = a.score + '|' + a.avatar;
  if (o.score != null) a.score = Number(o.score) || 0;
  if (o.avatar) a.avatar = o.avatar;
  store.writeJson(prof('retroachievements.json'), a);
  return before !== a.score + '|' + a.avatar;
}

/** Um pedido HTTPS. status 0 = sem internet. */
async function http(url, post, type) {
  try {
    const opt = { headers: { 'User-Agent': agent(clause) }, redirect: 'follow' };
    if (post != null) Object.assign(opt, { method: 'POST', body: post, headers: Object.assign(opt.headers, { 'Content-Type': type || 'application/x-www-form-urlencoded' }) });
    const r = await net.fetch(url, opt);
    return { status: r.status, body: await r.text() };
  } catch {
    return { status: 0, body: '' };
  }
}
/** Só para o servidor do RetroAchievements (o motor não pede outros endereços). */
const allowed = url => /^https:\/\/retroachievements\.org\//.test(String(url));

/** Usuário e senha (só nesta hora a senha passa pelo app). Devolve {ok, user, score} ou {ok:false, error}. */
async function login(user, password) {
  const body = 'r=login2&u=' + encodeURIComponent(String(user || '').trim()) + '&p=' + encodeURIComponent(String(password || ''));
  const r = await http(LOGIN_URL, body);
  let o = null;
  try { o = JSON.parse(r.body); } catch { }
  if (o && o.Success && o.Token) {
    const a = { user: o.User || String(user).trim(), token: o.Token, score: (o.Score || 0) + (o.SoftcoreScore || 0), avatar: o.AvatarUrl || '', at: Date.now() };
    store.writeJson(prof('retroachievements.json'), a);
    return { ok: true, user: a.user, score: a.score };
  }
  let err = (o && o.Error) || '';
  if (r.status === 0) err = 'offline';
  else if (r.status === 401 || r.status === 403 || (o && o.Code === 'invalid_credentials')) err = 'senha';
  return { ok: false, error: err || 'erro' };
}

/** A pontuação no site, com o token: pega o que saiu no RetroArch (N64, DS…) e em outros aparelhos. Uma vez por minuto. */
async function refresh() {
  const a = account();
  if (!a || Date.now() - lastRefresh < 60000) return false;
  lastRefresh = Date.now();
  const r = await http(LOGIN_URL, 'r=login2&u=' + encodeURIComponent(a.user) + '&t=' + encodeURIComponent(a.token));
  try {
    const o = JSON.parse(r.body);
    return !!(o.Success && updateAccount({ score: (o.Score || 0) + (o.SoftcoreScore || 0), avatar: o.AvatarUrl || '' }));
  } catch { return false; }
}

function saveGame(game) {
  if (!game || !game.id) return;
  const all = store.readJson(prof('ra-jogos.json'), {});
  game.at = Date.now();
  all[game.id] = game;
  store.writeJson(prof('ra-jogos.json'), all);
}
const games = () => store.readJson(prof('ra-jogos.json'), {});

/** O número do console no RetroAchievements (rc_consoles.h), ou 0 se ele não tiver conquistas. */
const CONSOLE = { nes: 7, snes: 3, gba: 5, gbc: 6, md: 1, ps1: 12, arcade: 27 };
const consoleId = c => CONSOLE[c] || 0;

/* ---------------- Tradução (Traducao.java) ---------------- */
const tfile = l => path.join(userData, 'ra-traducao-' + l + '.json');
const today = () => new Date().toISOString().slice(0, 10);
const cap = t => (t ? t.charAt(0).toUpperCase() + t.slice(1) : t);
const cache = l => store.readJson(tfile(l), {});
const cached = (c, en) => (en && c[en] ? cap(c[en]) : en);
let tchain = Promise.resolve();

/** Traduz e guarda (um pedido por vez). Devolve o texto traduzido, ou o original. */
function translate(l, en) {
  if (!en || !en.trim() || (l !== 'pt' && l !== 'es')) return Promise.resolve(en);
  const run = async () => {
    let c = cache(l);
    if (c[en]) return cap(c[en]);
    if (c._cheio === today()) return en;
    let out = null, full = false;
    try {
      const url = 'https://api.mymemory.translated.net/get?q=' + encodeURIComponent(en.slice(0, 450)) + '&langpair=' + encodeURIComponent('en|' + (l === 'es' ? 'es' : 'pt-BR'))
        + (CONTACT ? '&de=' + encodeURIComponent(CONTACT) : '');
      const r = await net.fetch(url);
      if (r.status === 200) {
        const o = await r.json(), t = String((o.responseData && o.responseData.translatedText) || '').trim();
        full = !!o.quotaFinished || /^MYMEMORY WARNING/i.test(t);
        if (!full && o.responseStatus === 200 && t) out = t;
      } else if (r.status === 429 || r.status === 403) full = true;
    } catch { }
    if (out == null && !full) return en;
    c = cache(l);
    if (out != null) c[en] = out;
    if (full) c._cheio = today();
    store.writeJson(tfile(l), c);
    return out != null ? cap(out) : en;
  };
  const p = tchain.then(run, run);
  tchain = p.catch(() => { });
  return p;
}

/** Traduz o que falta de todos os jogos; progress() a cada 10 novas. */
let translating = false;
async function translateAll(l, progress) {
  if (translating || (l !== 'pt' && l !== 'es')) return;
  translating = true;
  let n = 0;
  try {
    for (const g of Object.values(games())) {
      for (const a of g.list || []) {
        for (const en of [a.title, a.desc]) {
          const c = cache(l);
          if (!en || c[en]) continue;
          if (c._cheio === today()) return;
          await translate(l, en);
          if (++n % 10 === 0) progress();
          await new Promise(r => setTimeout(r, 250));
        }
      }
    }
  } finally {
    translating = false;
    if (n % 10) progress();
  }
}

/** A lista do hub com os textos que já têm tradução (o original fica em titleEn e descEn). */
function gamesTranslated(l) {
  const all = games();
  if (l !== 'pt' && l !== 'es') return all;
  const c = cache(l);
  for (const g of Object.values(all)) for (const a of g.list || []) {
    a.titleEn = a.title; a.descEn = a.desc;
    a.title = cached(c, a.title); a.desc = cached(c, a.desc);
  }
  return all;
}

module.exports = { init, setClause, account, connected, logout, updateAccount, http, allowed, login, refresh, saveGame, games, gamesTranslated,
  consoleId, translate, translateAll };
