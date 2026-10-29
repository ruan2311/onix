// Atualizações do GameHub para PC (Updater.java): procura nas releases do ruan2311/onix-gamehub
// a mais nova com tag gamehub-vX.Y.Z que traga o Onix-GameHub-win64.zip (a mesma release tem os
// APKs da TV e do celular; uma versão só de Android não conta). Não instala sozinho: "Instalar
// atualização" abre a página da versão no navegador, com o .zip para baixar.
'use strict';
const { net, shell } = require('electron');

const API = 'https://api.github.com/repos/ruan2311/onix-gamehub/releases?per_page=40';
const TAG = /^gamehub-v(\d{1,4})\.(\d{1,4})\.(\d{1,4})$/;
const ZIP = 'Onix-GameHub-win64.zip';
const CHECK_MS = 6 * 60 * 60 * 1000;

let current = '0.0.0', state = 'none', latest = '', page = '', error = '', checked = 0, store = null, listener = () => { };

const nums = v => { const m = /^(\d+)\.(\d+)\.(\d+)/.exec(v || ''); return m ? m.slice(1).map(Number) : null; };
function newer(a, b) {
  const x = nums(a), y = nums(b);
  if (!x || !y) return false;
  for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] > y[i];
  return false;
}

function init(version, s, onChange) {
  current = version;
  store = s;
  listener = onChange;
  checked = (store.readJson(store.p('update.json'), {}).checked) || 0;
}

function json() {
  return JSON.stringify({ current, latest: state === 'available' ? latest : '', state, error, checked });
}

async function check(force) {
  if (state === 'checking' || (!force && Date.now() - checked < CHECK_MS)) return;
  state = 'checking';
  listener(JSON.parse(json()));
  try {
    const r = await net.fetch(API, { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Onix-GameHub' } });
    if (r.status !== 200) throw new Error('HTTP ' + r.status);
    let best = null;
    for (const rel of await r.json()) {
      if (rel.draft || rel.prerelease) continue;
      const m = TAG.exec(rel.tag_name || '');
      if (!m || !(rel.assets || []).some(a => a.name === ZIP)) continue;
      const v = m.slice(1).join('.');
      if (!best || newer(v, best.v)) best = { v, url: rel.html_url };
    }
    checked = Date.now();
    store.writeJson(store.p('update.json'), { checked });
    if (best && newer(best.v, current)) { state = 'available'; latest = best.v; page = best.url; }
    else state = 'none';
    error = '';
  } catch (e) {
    state = 'error';
    error = String(e.message || e);
  }
  listener(JSON.parse(json()));
}

function open() {
  if (page && page.startsWith('https://github.com/ruan2311/onix-gamehub/')) shell.openExternal(page);
}

module.exports = { init, json, check, open };
