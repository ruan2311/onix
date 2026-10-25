/*
  Adaptador da Samsung (Tizen) para a ponte comum (tv/bridge.js): tudo que usa as APIs web do
  Tizen (tizen.application, tizen.systeminfo, tizen.tvwindow) fica aqui. O build junta os dois.
*/
const P = (() => {
if (!window.tizen || !tizen.application) return null;

const SYSTEM_RE = /^(org\.tizen\.|com\.samsung\.|org\.volt\.)/;
const appInfo = (() => { try { return tizen.application.getCurrentApplication().appInfo; } catch (_) { return null; } })();

/* ---------------- Entradas HDMI (TV Window) ----------------
   O Tizen não troca a entrada da TV inteira para um app comum: o Onix mostra a HDMI em tela cheia
   numa "janela de TV" dele mesmo. Voltar (ou OK) fecha a janela e volta para o Onix. */
const sources = {};
let watching = false;
const tvStyle = document.createElement('style');
tvStyle.textContent = 'html.onix-tv,html.onix-tv body{background:transparent!important}html.onix-tv body>*{visibility:hidden!important}';
document.head.appendChild(tvStyle);

function stopInput() {
  watching = false;
  document.documentElement.classList.remove('onix-tv');
  try { tizen.tvwindow.hide(() => {}, () => {}, 'MAIN'); } catch (_) {}
}

return {
  storage: 'onix.tz.',
  name: 'Samsung TV',
  self: appInfo ? appInfo.id : '',
  version: appInfo ? appInfo.version || '' : '',
  updateHint: 'Na Samsung, baixe o onix-tizen.zip no GitHub e repita o passo 5 do tutorial.',
  serviceIds: { netflix: ['3201907018807', '11101200001', 'org.tizen.netflix-app'] },
  keys: { back: 10009, exit: 10182 },

  scanApps(done) {
    try {
      tizen.application.getAppsInfo(list => done(list.filter(a => a && a.show)
        .map(a => ({ id: a.id, name: a.name, icon: a.iconPath, system: SYSTEM_RE.test(a.id) }))),
      e => console.warn('getAppsInfo falhou', e));
    } catch (e) { console.warn('getAppsInfo indisponível', e); }
  },
  watchApps(cb) {
    try { tizen.application.addAppInfoEventListener({ oninstalled: cb, onupdated: cb, onuninstalled: cb }); } catch (_) {}
  },
  launch(id, fail) {
    tizen.application.launch(id, null, fail);
  },
  openLink(appId, link, ok, fail) {
    const ctl = new tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', link, null, null, null);
    tizen.application.launchAppControl(ctl, appId, ok, fail);
  },
  // Deep link do app da Netflix da Samsung.
  openNetflix(appId, titleId, ok, fail) {
    const payload = JSON.stringify({ values: 'm=https://www.netflix.com/title/' + titleId + '&source_type=4' });
    const ctl = new tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', null, null, null,
      [new tizen.ApplicationControlData('PAYLOAD', [payload])]);
    tizen.application.launchAppControl(ctl, appId, ok, fail);
  },

  scanInputs(done) {
    try {
      tizen.systeminfo.getPropertyValue('VIDEOSOURCE', v => {
        const all = [].concat(v.connected || [], v.disconnected || []).filter(s => s && s.number > 0);
        // TV (antena e cabo) primeiro, depois as HDMI em ordem; uma entrada por ID.
        const tv = all.filter(s => s.type === 'TV').slice(0, 1);
        const hdmi = all.filter(s => s.type === 'HDMI').sort((a, b) => a.number - b.number);
        const out = [], seen = {};
        for (const s of tv.concat(hdmi)) {
          const id = s.type + ':' + s.number;
          if (seen[id]) continue;
          seen[id] = true;
          sources[id] = s;
          out.push(s.type === 'TV' ? { id, label: 'TV', kind: 'tv' } : { id, label: 'HDMI ' + s.number });
        }
        done(out);
      }, e => console.warn('VIDEOSOURCE falhou', e));
    } catch (e) { console.warn('systeminfo indisponível', e); }
  },
  showInput(id, fail) {
    const src = sources[id];
    if (!src || !tizen.tvwindow) return false;
    const failed = () => { stopInput(); fail(); };
    try {
      tizen.tvwindow.setSource(src, () => {
        tizen.tvwindow.show(() => {
          watching = true;
          document.documentElement.classList.add('onix-tv');
        }, failed, ['0px', '0px', '100%', '100%'], 'MAIN');
      }, failed, 'MAIN');
      return true;
    } catch (_) { return false; }
  },
  watching: () => watching,
  stopInput,
  exit() {
    try { tizen.application.getCurrentApplication().hide(); } catch (_) {}
  },
};
})();
