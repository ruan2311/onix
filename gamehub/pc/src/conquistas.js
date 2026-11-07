// Conquistas do Cobalt no PC: as mesmas do Android (Conquistas.java), com a lista de assets/conquistas.json.
// O que é contado fica em profiles/default/conquistas.json = { stats, unlocked: { id: hora } }.
// Contadores guardados: plays, saves, rewind, ffwd, multi, night e session (maior partida seguida, em minutos).
// Calculados na hora: library, favs, minutes (total), game (o jogo mais jogado, em minutos), consoles e all (a platina).
'use strict';
const fs = require('fs');
const path = require('path');

let store = null, defs = [], listener = null, paused = () => false;
const COUNTERS = ['plays', 'saves', 'rewind', 'ffwd', 'multi', 'night', 'session'];

function init(s, assets) {
  store = s;
  try { defs = JSON.parse(fs.readFileSync(path.join(assets, 'conquistas.json'), 'utf8')).list || []; } catch { defs = []; }
}
/** Quem mostra o aviso: recebe a lista das que acabaram de sair. */
const setListener = l => { listener = l; };
/** Com a conta do RetroAchievements conectada, as do Cobalt param de contar (voltam ao sair da conta). */
const setPaused = f => { paused = f; };
const file = () => path.join(store.p('profiles', 'default'), 'conquistas.json');

/** Os valores de cada "stat", com a partida que está rolando somada (live = { id, console, seconds }). */
function values(stats, live) {
  const v = {};
  for (const k of COUNTERS) v[k] = stats[k] || 0;
  const games = store.games(), consoleOf = {};
  for (const g of games) consoleOf[g.id] = g.console;
  v.library = games.length;
  const played = store.readJson(path.join(store.p('profiles', 'default'), 'played.json'), {});
  let total = 0, best = 0, favs = 0;
  const consoles = new Set();
  for (const [id, p] of Object.entries(played)) {
    if (!p) continue;
    if (p.fav) favs++;
    let t = p.time || 0;
    if (live && id === live.id) t += live.seconds;
    total += t;
    best = Math.max(best, t);
    if (t > 0 && consoleOf[id]) consoles.add(consoleOf[id]);
  }
  if (live && live.id && !played[live.id]) { total += live.seconds; best = Math.max(best, live.seconds); }
  if (live && live.console) consoles.add(live.console);
  return Object.assign(v, { favs, minutes: Math.floor(total / 60), game: Math.floor(best / 60), consoles: consoles.size });
}

/** Conta (stat), guarda o maior valor (max) ou só confere (stat vazio). Avisa o que acabou de sair. */
function update(stat, value, max, live) {
  if (!store) return [];
  const all = store.readJson(file(), {});
  if (all.off || paused()) return [];   // desativadas pela pessoa (aba Conquistas) ou RetroAchievements conectado
  const stats = all.stats || {}, unlocked = all.unlocked || {};
  let dirty = false;
  if (stat && COUNTERS.includes(stat)) {
    const old = stats[stat] || 0, now = max ? Math.max(old, value) : old + 1;
    if (now !== old) { stats[stat] = now; dirty = true; }
  }
  const v = values(stats, live), fresh = [];
  for (let pass = 0; pass < 2; pass++) {   // a platina depende das outras que podem sair agora
    v.all = defs.every(d => d.stat === 'all' || unlocked[d.id]) ? 1 : 0;
    for (const d of defs) {
      if (unlocked[d.id] || (v[d.stat] || 0) < d.min) continue;
      unlocked[d.id] = Date.now();
      fresh.push(d);
    }
  }
  if (fresh.length) dirty = true;
  if (dirty) store.writeJson(file(), Object.assign(all, { stats, unlocked }));
  if (fresh.length && listener) listener(fresh);
  return fresh;
}
const count = (stat, live) => update(stat, 1, false, live);
const max = (stat, value, live) => update(stat, value, true, live);
const check = live => update(null, 0, false, live);

/** Para a aba Conquistas (o mesmo formato do Android). */
function json(lang) {
  const all = store.readJson(file(), {}), unlocked = all.unlocked || {};
  const v = values(all.stats || {}, null);
  v.all = defs.every(d => d.stat === 'all' || unlocked[d.id]) ? 1 : 0;
  const list = defs.map(d => ({ id: d.id, kind: d.kind, tier: d.tier, pts: d.pts, icon: d.icon, title: (lang === 'es' ? d.es : d.pt)[0], desc: (lang === 'es' ? d.es : d.pt)[1],
    min: d.min, value: Math.min(d.min, v[d.stat] || 0), at: unlocked[d.id] || 0 }));
  const got = list.filter(c => c.at);
  return JSON.stringify({ list, points: got.reduce((n, c) => n + c.pts, 0), total: list.reduce((n, c) => n + c.pts, 0), got: got.length, count: list.length, off: !!all.off });
}

/** Liga ou desliga as conquistas (o que já saiu continua guardado). */
function setOn(on) {
  const all = store.readJson(file(), {});
  all.off = !on;
  store.writeJson(file(), all);
  if (on) check();
  return true;
}

/** Ligadas no perfil (o botão Desativar conquistas vale para o RetroAchievements também). */
const enabled = () => !store.readJson(file(), {}).off;

/** 0 h às 4h59: a "Coruja". */
const night = () => new Date().getHours() < 5;

module.exports = { init, setListener, setPaused, count, max, check, json, setOn, enabled, night, COUNTERS };
