/*
  Adaptador da LG (webOS) para a ponte comum (tv/bridge.js). Os serviços da TV (Luna) são chamados
  pelo PalmServiceBridge, o mesmo que a biblioteca webOSTV.js usa por baixo, então o app não precisa
  de nenhum arquivo da LG. O build (webos/build.sh) junta este arquivo com a ponte comum.

  Nem todo serviço responde para apps instalados fora da loja: se a TV negar a lista de apps ou de
  entradas, o Onix usa a lista conhecida abaixo (os apps mais comuns e TV + HDMI 1 a 4).
*/
const P = (() => {
if (!window.PalmSystem || typeof window.PalmServiceBridge !== 'function') return null;

const SELF = String(PalmSystem.identifier || '').split(' ')[0];
const SYSTEM_RE = /^(com\.webos\.|com\.palm\.|com\.lge\.|com\.lg\.)/;

/** Chamada Luna: done(resposta) ou fail(). Com subscribe, done roda a cada atualização. */
const bridges = [];   // assinaturas ficam vivas enquanto o app estiver aberto
function luna(uri, params, done, fail, subscribe) {
  let b;
  try { b = new PalmServiceBridge(); } catch (_) { if (fail) fail(); return; }
  let first = true;
  b.onservicecallback = msg => {
    let r = null;
    try { r = JSON.parse(msg); } catch (_) {}
    if (!r || r.returnValue === false) { if (first && fail) fail(r); first = false; return; }
    first = false;
    done(r);
  };
  if (subscribe) bridges.push(b);
  try { b.call(uri, JSON.stringify(Object.assign({}, params, subscribe ? { subscribe: true } : {}))); }
  catch (_) { if (fail) fail(); }
}
function launchApp(id, params, ok, fail) {
  luna('luna://com.webos.applicationManager/launch', { id, params: params || {} }, () => { if (ok) ok(); }, () => { if (fail) fail(); });
}

/* ---------------- Apps ----------------
   listLaunchPoints traz os apps com ícone na barra da LG. Se a TV não deixar, a lista conhecida
   abaixo aparece no lugar (um app que não estiver instalado avisa ao abrir). */
const KNOWN_APPS = [
  { id: 'netflix', name: 'Netflix' },
  { id: 'youtube.leanback.v4', name: 'YouTube' },
  { id: 'amazon', name: 'Prime Video' },
  { id: 'com.disney.disneyplus-prod', name: 'Disney+' },
  { id: 'spotify-beehive', name: 'Spotify' },
  { id: 'com.apple.appletv', name: 'Apple TV' },
  { id: 'com.webos.app.browser', name: 'Navegador', system: true },
  { id: 'com.palm.app.settings', name: 'Configurações', system: true },
];
// Atalhos de sistema que viram entradas (ou não abrem nada útil) ficam fora da grade de apps.
const HIDDEN_RE = /^com\.webos\.app\.(hdmi\d|livetv|externalinput\.|inputcommon|tvhotkey|channelsetting|softwareupdate|discovery$)/;
let appsWatcher = false;
function toApps(r) {
  return (r.launchPoints || []).filter(l => l && typeof l.id === 'string' && !HIDDEN_RE.test(l.id))
    .map(l => ({ id: l.id, name: l.title, icon: l.largeIcon || l.icon, system: !!l.systemApp || SYSTEM_RE.test(l.id) }));
}

/* ---------------- Entradas ----------------
   Na LG, abrir o app da entrada (com.webos.app.hdmi1…) troca a TV inteira para ela, como o
   botão de entradas do controle. A lista vem do serviço de entradas; sem permissão, TV + HDMI 1 a 4. */
const INPUT_APPS = {};
const FALLBACK_INPUTS = [{ id: 'TV', label: 'TV', kind: 'tv', app: 'com.webos.app.livetv' }]
  .concat([1, 2, 3, 4].map(n => ({ id: 'HDMI_' + n, label: 'HDMI ' + n, app: 'com.webos.app.hdmi' + n })));
function keepInputs(list) {
  for (const i of list) INPUT_APPS[i.id] = i.app;
  return list.map(i => i.kind ? { id: i.id, label: i.label, kind: i.kind } : { id: i.id, label: i.label });
}

return {
  storage: 'onix.lg.',
  name: 'LG TV · webOS',
  self: SELF,
  version: '@VERSION@',   // o build troca pela versão do APK
  updateHint: 'Na LG, baixe o onix-webos.ipk no GitHub e instale de novo pelo webOS Dev Manager ou pelo Homebrew Channel.',
  serviceIds: { netflix: ['netflix'], disney: ['com.disney.disneyplus-prod'], globoplay: ['com.globo.globoplay'] },
  keys: { back: 461, exit: -1 },

  scanApps(done) {
    luna('luna://com.webos.applicationManager/listLaunchPoints', {}, r => {
      const list = toApps(r);
      if (list.length) done(list);
    }, () => done(KNOWN_APPS));
  },
  // Assinatura do listLaunchPoints: a TV avisa quando um app é instalado ou removido.
  watchApps(cb) {
    if (appsWatcher) return;
    appsWatcher = true;
    let first = true;
    luna('luna://com.webos.applicationManager/listLaunchPoints', {}, r => {
      if (first) { first = false; return; }   // a primeira resposta é a lista que o scanApps já leu
      if (r.change || r.launchPoint) cb();
    }, null, true);
  },
  launch(id, fail) {
    launchApp(id, null, null, fail);
  },
  // Disney+, HBO Max e Globoplay: o link do título vai como contentTarget (o app decide se usa).
  openLink(appId, link, ok, fail) {
    launchApp(appId, { contentTarget: link }, ok, fail);
  },
  // Deep link do app da Netflix da LG: o mesmo formato que o botão Netflix das TVs usa.
  openNetflix(appId, titleId, ok, fail) {
    launchApp(appId, { contentTarget: 'm=https://api-global.netflix.com/catalog/titles/movie/' + titleId + '&source_type=4' }, ok, fail);
  },

  scanInputs(done) {
    luna('luna://com.webos.service.eim/getAllInputStatus', {}, r => {
      const devs = (r.devices || []).filter(d => d && typeof d.id === 'string' && /^HDMI_\d$/.test(d.id) && typeof d.appId === 'string');
      if (!devs.length) { done(keepInputs(FALLBACK_INPUTS)); return; }
      devs.sort((a, b) => a.id.localeCompare(b.id));
      done(keepInputs([FALLBACK_INPUTS[0]].concat(devs.map(d => ({ id: d.id, label: 'HDMI ' + d.id.slice(5), app: d.appId })))));
    }, () => done(keepInputs(FALLBACK_INPUTS)));
  },
  showInput(id, fail) {
    const app = INPUT_APPS[id];
    if (!app) return false;
    launchApp(app, null, null, fail);
    return true;
  },
  exit() {
    // Voltar na raiz: volta para a tela de antes do Onix (barra de apps da LG).
    try { if (typeof PalmSystem.platformBack === 'function') { PalmSystem.platformBack(); return; } } catch (_) {}
    try { window.close(); } catch (_) {}
  },
  openSettings: () => { launchApp('com.palm.app.settings', null); return true; },
  openPictureSettings: () => { launchApp('com.palm.app.settings', { target: 'picture' }, null, () => launchApp('com.palm.app.settings', null)); return true; },
};
})();
