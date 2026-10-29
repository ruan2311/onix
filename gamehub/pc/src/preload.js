// Ponte entre as telas e o processo principal, no lugar das pontes Java do Android.
//  - hub.html: window.OnixHub, com as mesmas funções do HubBridge.java.
//  - game.html: window.OnixPC, usada pelo game.js (que faz o papel do GameActivity e do MenuBridge).
// Os eventos chegam como no Android: window.onHub(tipo, dados) na página.
// Controles: a Gamepad API do Chromium. No hub, o controle anda pelos menus como as setas.
'use strict';
const { contextBridge, ipcRenderer, webFrame } = require('electron');

const hub = (name, ...args) => ipcRenderer.sendSync('onix:hub', name, args);
const isGame = location.pathname === '/game.html';

/* Eventos do processo principal: chegam na página como no WebView (Web.emit). */
function emit(type, data) {
  webFrame.executeJavaScript('window.onHub&&window.onHub(' + JSON.stringify(type) + ',' + JSON.stringify(data === undefined ? null : data) + ')');
}
ipcRenderer.on('onix:event', (_, type, data) => emit(type, data));

/* ---------------- Teclas e botões ----------------
   O que fica guardado em "Trocar botões" (remap.<código>) usa os códigos de tecla do Android,
   os mesmos da TV: botão do controle e tecla do teclado viram um número só. */
const PAD_CODES = [96, 97, 99, 100, 102, 103, 104, 105, 109, 108, 106, 107, 19, 20, 21, 22, 110];
const PAD_NAMES = ['A', 'B', 'X', 'Y', 'L1', 'R1', 'L2', 'R2', 'SELECT', 'START', 'L3', 'R3', '▲', '▼', '◀', '▶', 'MODE'];
const KEY_CODES = {
  ArrowUp: 19, ArrowDown: 20, ArrowLeft: 21, ArrowRight: 22, Enter: 66, Space: 62, Backspace: 67, Tab: 61,
  ShiftLeft: 59, ShiftRight: 60, ControlLeft: 113, ControlRight: 114, AltLeft: 57, AltRight: 58,
  Comma: 55, Period: 56, Slash: 76, Semicolon: 74, Quote: 75, BracketLeft: 71, BracketRight: 72, Minus: 69, Equal: 70,
  Numpad0: 144, Numpad1: 145, Numpad2: 146, Numpad3: 147, Numpad4: 148, Numpad5: 149, Numpad6: 150, Numpad7: 151, Numpad8: 152, Numpad9: 153,
};
for (let i = 0; i < 26; i++) KEY_CODES['Key' + String.fromCharCode(65 + i)] = 29 + i;
for (let i = 0; i < 10; i++) KEY_CODES['Digit' + i] = 7 + i;
const keyCode = code => KEY_CODES[code] || 0;
const keyName = (code, key) => (code.startsWith('Key') ? code.slice(3) : code.startsWith('Digit') ? code.slice(5)
  : { ArrowUp: '▲', ArrowDown: '▼', ArrowLeft: '◀', ArrowRight: '▶', Space: 'Espaço', ShiftLeft: 'Shift', ShiftRight: 'Shift dir.', ControlLeft: 'Ctrl', ControlRight: 'Ctrl dir.', AltLeft: 'Alt', AltRight: 'Alt Gr' }[code] || key.toUpperCase());

/* Controles de videogame: os que o Windows mostra com o mapeamento padrão (Xbox, PlayStation,
   8BitDo…). O nome sem o "(STANDARD GAMEPAD Vendor: …)" que o Chromium acrescenta. */
const padName = g => g.id.replace(/\s*\((STANDARD GAMEPAD|Vendor:)[^)]*\)\s*/gi, ' ').replace(/\s+/g, ' ').trim() || 'Controle';
const connected = () => Array.from(navigator.getGamepads ? navigator.getGamepads() : []).filter(g => g && g.connected && g.mapping === 'standard');
function padList() {
  const saved = JSON.parse(hub('playersSaved') || '{}');
  return connected().map(g => {
    const o = { id: g.index, name: padName(g) };
    for (const n of ['1', '2']) if (saved[n] && saved[n].d === g.id) o.player = Number(n);
    return o;
  });
}

/* ---------------- Hub ---------------- */
let captureKey = false, capturePlayer = 0;

if (!isGame) {
  const Hub = {};
  for (const name of ['library', 'states', 'play', 'setFavorite', 'rename', 'setKids', 'remove', 'deleteState', 'config', 'effective', 'setConfig',
    'coreOptions', 'cheats', 'setCheats', 'storage', 'uploadStart', 'uploadStop', 'editTouch', 'pickGames', 'folders', 'removeFolder', 'rescan',
    'clearPad', 'openBluetooth', 'update', 'checkUpdate', 'installUpdate', 'fullscreen', 'exit']) {
    Hub[name] = (...args) => hub(name, ...args);
  }
  Hub.init = () => JSON.stringify(Object.assign(JSON.parse(hub('init')), { gamepads: padList() }));
  Hub.gamepads = () => JSON.stringify(padList());
  Hub.players = () => {
    const saved = JSON.parse(hub('playersSaved') || '{}'), on = connected().map(g => g.id), out = {};
    for (const n of ['1', '2']) if (saved[n]) out[n] = { name: saved[n].n, connected: on.includes(saved[n].d) };
    return JSON.stringify(out);
  };
  Hub.capturePad = player => { capturePlayer = player >= 1 && player <= 2 ? player : 0; };
  Hub.captureKey = on => { captureKey = !!on; };
  // Com outro nome: o que o contextBridge cria não pode ser redeclarado pela página (const Hub).
  contextBridge.exposeInMainWorld('OnixHub', Hub);

  // Trocar botões pelo teclado: a próxima tecla vai para a página (evento "key").
  window.addEventListener('keydown', e => {
    if (!captureKey || e.repeat) return;
    const code = keyCode(e.code);
    if (!code || e.code === 'Escape') return;
    e.preventDefault();
    e.stopImmediatePropagation();
    captureKey = false;
    emit('key', { code, name: keyName(e.code, e.key) });
  }, true);

  /* Controle no hub: A/Start confirmam, B volta, LB/RB trocam de aba, direcional e analógico
     andam (segurando, repete). Também define o controle 1/2 e captura botões. */
  const NAV = { 0: 'Enter', 9: 'Enter', 1: 'Escape', 4: 'PageUp', 5: 'PageDown', 12: 'ArrowUp', 13: 'ArrowDown', 14: 'ArrowLeft', 15: 'ArrowRight' };
  const held = {};
  const key = (k, repeat) => document.dispatchEvent(new KeyboardEvent('keydown', { key: k, code: k, bubbles: true, cancelable: true, repeat }));
  let known = '';
  function poll() {
    const pads = connected();
    const ids = pads.map(g => g.index + g.id).join('|');
    if (ids !== known) {
      known = ids;
      emit('gamepads', padList());
    }
    const now = performance.now();
    for (const g of pads) {
      const dirs = { ArrowLeft: g.axes[0] < -0.6, ArrowRight: g.axes[0] > 0.6, ArrowUp: g.axes[1] < -0.6, ArrowDown: g.axes[1] > 0.6 };
      g.buttons.forEach((b, i) => {
        const k = g.index + ':' + i, down = b.pressed;
        if (down && !held[k]) {
          held[k] = { at: now, next: now + 400 };
          if (capturePlayer) {
            const p = capturePlayer;
            capturePlayer = 0;
            const okSet = hub('setPlayer', p, g.id, padName(g));
            emit('padSet', { player: p, name: padName(g), ok: okSet });
            return;
          }
          if (captureKey && PAD_CODES[i]) { captureKey = false; emit('key', { code: PAD_CODES[i], name: PAD_NAMES[i] }); return; }
          if (NAV[i] && document.hasFocus()) key(NAV[i], false);
        } else if (down && held[k] && /^Arrow/.test(NAV[i] || '') && now >= held[k].next) {
          held[k].next = now + 110;
          key(NAV[i], true);
        } else if (!down) delete held[k];
      });
      for (const [d, on] of Object.entries(dirs)) {
        const k = g.index + ':' + d;
        if (on && !held[k]) { held[k] = { next: now + 400 }; if (document.hasFocus() && !captureKey && !capturePlayer) key(d, false); }
        else if (on && now >= held[k].next) { held[k].next = now + 110; key(d, true); }
        else if (!on) delete held[k];
      }
    }
    requestAnimationFrame(poll);
  }
  window.addEventListener('DOMContentLoaded', () => requestAnimationFrame(poll));
}

/* ---------------- Jogo ---------------- */
if (isGame) {
  contextBridge.exposeInMainWorld('OnixPC', {
    call: (name, ...args) => ipcRenderer.sendSync('onix:game', name, args),
    async: (name, ...args) => ipcRenderer.invoke('onix:gameAsync', name, args),
    hub: (name, ...args) => hub(name, ...args),
    log: msg => ipcRenderer.send('onix:log', msg),
    keyCode, keyName, padName, padCodes: PAD_CODES, padNames: PAD_NAMES,
  });
}
