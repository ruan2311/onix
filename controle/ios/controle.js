// Onix Controle para iOS (iPhone / iPad) · Web App instalável
'use strict';

const $ = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);

// Idioma
const ES = (navigator.language || '').toLowerCase().startsWith('es');
const T = {
  'Desconectado': 'Desconectado',
  'Conectado': 'Conectado',
  'Digite o IP da TV': 'Ingresa la IP de la TV',
  'IP inválido': 'IP no válida',
  'TV salva!': '¡TV guardada!',
  'Abrir Controle Web na TV': 'Abrir Control Web en la TV',
  'Comando enviado': 'Comando enviado',
  'Aguardando conexão': 'Esperando conexión',
  'Desligar a TV?': '¿Apagar la TV?',
  'Toque de novo para confirmar': 'Toca de nuevo para confirmar',
  'TV desligada': 'TV apagada',
  'Texto enviado à TV': 'Texto enviado a la TV',
  'Digite algo para enviar': 'Escribe algo para enviar',
};
const t = s => (ES && T[s]) || s;

// Estado
const store = {
  get: (k, def) => { try { const v = localStorage.getItem(k); return v != null ? JSON.parse(v) : def; } catch (_) { return def; } },
  set: (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (_) {} }
};

let tvHost = store.get('onix.tv.host', '');
let mode = 'remote'; // 'remote' | 'gamepad'
let padType = 'dpad'; // 'dpad' | 'pad'
let powerArmed = false, powerTimer = null;
let toastTimer = null;
let gpPad = null, gpWs = null, gpConsole = store.get('onix.gp.console', 'snes');

const CONSOLES = { snes: 'SNES', nes: 'NES', gba: 'GBA', md: 'Mega Drive' };

function toast(msg) {
  const el = $('#toast');
  if (!el) return;
  el.textContent = t(msg);
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2400);
}

function vibrate(ms = 12) {
  if (navigator.vibrate) {
    try { navigator.vibrate(ms); } catch (_) {}
  }
}

// Atualiza interface do cabeçalho
function updateStatus() {
  const dot = $('#dot');
  const sub = $('#hdrSub');
  if (tvHost) {
    dot.className = 'dot on';
    sub.textContent = `${t('Conectado')} · ${tvHost}`;
    $('#tvDirectLink').href = `http://${tvHost}:8765/`;
    $('#tvDirectBox').hidden = false;
  } else {
    dot.className = 'dot';
    sub.textContent = t('Desconectado');
    $('#tvDirectBox').hidden = true;
  }
}

// Envio de comando para a TV
async function sendKey(key) {
  vibrate(14);
  if (!tvHost) {
    openTvSheet();
    toast('Defina o IP da TV primeiro');
    return;
  }
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 1200);
    const r = await fetch(`http://${tvHost}:8765/api/key`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ k: key }),
      signal: controller.signal
    });
    clearTimeout(timeout);
    if (!r.ok && r.status === 409) toast('Abra o Onix na TV primeiro');
  } catch (err) {
    // Caso o navegador do iPhone bloqueie fetch HTTP por Mixed Content
    console.warn('Erro ao enviar tecla:', err);
  }
}

// Envio de texto para a TV
async function sendText(text) {
  if (!tvHost) return;
  try {
    await fetch(`http://${tvHost}:8765/api/text`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ v: text })
    });
    toast('Texto enviado à TV');
  } catch (err) {
    console.warn('Erro ao enviar texto:', err);
  }
}

// Inicialização dos botões do D-Pad
function initDpad() {
  $$('.dir').forEach(btn => {
    btn.addEventListener('click', () => sendKey(btn.dataset.k));
  });
  const okBtn = $('#btnOk');
  if (okBtn) okBtn.addEventListener('click', () => sendKey('enter'));
}

// Inicialização do Touchpad
function initTouchpad() {
  const pad = $('#touchpad');
  if (!pad) return;

  let startX = 0, startY = 0, lastX = 0, lastY = 0, moved = false, touchTime = 0;
  const THRESHOLD = 24;

  pad.addEventListener('touchstart', e => {
    const t0 = e.touches[0];
    startX = lastX = t0.clientX;
    startY = lastY = t0.clientY;
    moved = false;
    touchTime = Date.now();

    // Efeito ripple
    const rect = pad.getBoundingClientRect();
    const rip = document.createElement('span');
    rip.className = 'rip';
    rip.style.left = (t0.clientX - rect.left) + 'px';
    rip.style.top = (t0.clientY - rect.top) + 'px';
    pad.appendChild(rip);
    setTimeout(() => rip.remove(), 400);
  }, { passive: true });

  pad.addEventListener('touchmove', e => {
    const t0 = e.touches[0];
    const dx = t0.clientX - lastX;
    const dy = t0.clientY - lastY;

    if (Math.abs(dx) > THRESHOLD || Math.abs(dy) > THRESHOLD) {
      moved = true;
      if (Math.abs(dx) > Math.abs(dy)) {
        sendKey(dx > 0 ? 'right' : 'left');
      } else {
        sendKey(dy > 0 ? 'down' : 'up');
      }
      lastX = t0.clientX;
      lastY = t0.clientY;
    }
  }, { passive: true });

  pad.addEventListener('touchend', () => {
    if (!moved && Date.now() - touchTime < 300) {
      sendKey('enter');
    }
  });
}

// Botão Power com confirmação
function initPower() {
  const btn = $('#btnPower');
  if (!btn) return;
  btn.addEventListener('click', () => {
    if (!powerArmed) {
      powerArmed = true;
      toast('Desligar a TV? Toque de novo para confirmar');
      btn.style.background = 'rgba(255, 90, 110, .35)';
      clearTimeout(powerTimer);
      powerTimer = setTimeout(() => {
        powerArmed = false;
        btn.style.background = '';
      }, 3000);
    } else {
      powerArmed = false;
      btn.style.background = '';
      sendKey('power');
      toast('TV desligada');
    }
  });
}

// Alternar entre D-Pad e Touchpad
function initModeSwitch() {
  const btnDpad = $('#tabDpad');
  const btnPad = $('#tabTouchpad');
  const dpadWrap = $('#dpadWrap');
  const touchpad = $('#touchpad');

  if (btnDpad && btnPad) {
    btnDpad.addEventListener('click', () => {
      padType = 'dpad';
      btnDpad.classList.add('cur');
      btnPad.classList.remove('cur');
      dpadWrap.hidden = false;
      touchpad.hidden = true;
    });
    btnPad.addEventListener('click', () => {
      padType = 'pad';
      btnPad.classList.add('cur');
      btnDpad.classList.remove('cur');
      dpadWrap.hidden = true;
      touchpad.hidden = false;
    });
  }
}

// Alternar entre Controle de TV e Gamepad
function initViewSwitch() {
  const btnToggle = $('#btnToggleGp');
  const viewRemote = $('#viewRemote');
  const viewGp = $('#viewGamepad');

  function setView(next) {
    mode = next;
    if (mode === 'gamepad') {
      viewRemote.hidden = true;
      viewGp.hidden = false;
      btnToggle.classList.add('active');
      initGamepad();
    } else {
      viewRemote.hidden = false;
      viewGp.hidden = true;
      btnToggle.classList.remove('active');
      destroyGamepad();
    }
  }

  if (btnToggle) btnToggle.addEventListener('click', () => setView(mode === 'remote' ? 'gamepad' : 'remote'));
  const btnBackRemote = $('#gpBackRemote');
  if (btnBackRemote) btnBackRemote.addEventListener('click', () => setView('remote'));

  if (window.location.hash === '#gamepad' || window.location.hash === '#gp') {
    setView('gamepad');
  }
  if (window.location.hash === '#tv') {
    openTvSheet();
  }
}

// Gamepad com Canvas 2D
function initGamepad() {
  const canvas = $('#gpCanvas');
  if (!canvas || !window.OnixPad) return;

  function resize() {
    const wrap = canvas.parentElement;
    if (!wrap) return;
    const w = wrap.clientWidth;
    const h = wrap.clientHeight;
    if (w > 0 && h > 0) {
      canvas.width = w * window.devicePixelRatio;
      canvas.height = h * window.devicePixelRatio;
    }
  }
  resize();
  window.addEventListener('resize', resize);

  const bitsToKeys = {
    4: 'up', 5: 'down', 6: 'left', 7: 'right',
    0: 'b', 1: 'y', 8: 'a', 9: 'x',
    2: 'select', 3: 'start', 10: 'l', 11: 'r'
  };

  gpPad = new window.OnixPad(canvas, gpConsole, {
    onPress: (bit) => {
      vibrate(12);
      const k = bitsToKeys[bit];
      if (k) sendKey(k);
    },
    onRelease: () => {}
  });

  $('#gpConsoleLbl').textContent = CONSOLES[gpConsole] || 'SNES';
}

function destroyGamepad() {
  if (gpPad) {
    try { gpPad.destroy(); } catch (_) {}
    gpPad = null;
  }
}

// Troca de console no Gamepad
function initConsoleSelector() {
  const btn = $('#gpConsoleBtn');
  if (!btn) return;
  const list = ['snes', 'gba', 'md', 'nes'];
  btn.addEventListener('click', () => {
    let idx = list.indexOf(gpConsole);
    idx = (idx + 1) % list.length;
    gpConsole = list[idx];
    store.set('onix.gp.console', gpConsole);
    $('#gpConsoleLbl').textContent = CONSOLES[gpConsole] || 'SNES';
    destroyGamepad();
    initGamepad();
    toast(`Console: ${CONSOLES[gpConsole]}`);
  });
}

// Modal de Configuração da TV
function openTvSheet() {
  const sheet = $('#tvSheet');
  const input = $('#tvIpInput');
  if (sheet) {
    sheet.classList.add('open');
    if (input) {
      input.value = tvHost;
      setTimeout(() => input.focus(), 100);
    }
  }
}

function closeTvSheet() {
  const sheet = $('#tvSheet');
  if (sheet) sheet.classList.remove('open');
}

function initTvSheet() {
  const btnTv = $('#btnTvSettings');
  const btnClose = $('#tvSheetClose');
  const btnSave = $('#tvSaveBtn');
  const input = $('#tvIpInput');
  const sheet = $('#tvSheet');

  if (btnTv) btnTv.addEventListener('click', openTvSheet);
  if (btnClose) btnClose.addEventListener('click', closeTvSheet);
  if (sheet) sheet.addEventListener('click', e => { if (e.target === sheet) closeTvSheet(); });

  if (btnSave && input) {
    btnSave.addEventListener('click', () => {
      const val = input.value.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
      if (!val) {
        tvHost = '';
        store.set('onix.tv.host', '');
        updateStatus();
        closeTvSheet();
        toast('TV desconectada');
        return;
      }
      tvHost = val;
      store.set('onix.tv.host', tvHost);
      updateStatus();
      closeTvSheet();
      toast('TV salva!');
    });
  }
}

// Modal de Digitação (Teclado)
function openKbSheet() {
  const sheet = $('#kbSheet');
  const input = $('#kbInput');
  if (sheet) {
    sheet.classList.add('open');
    if (input) setTimeout(() => input.focus(), 100);
  }
}

function closeKbSheet() {
  const sheet = $('#kbSheet');
  if (sheet) sheet.classList.remove('open');
}

function initKbSheet() {
  const btnKb = $('#btnKeyboard');
  const btnClose = $('#kbSheetClose');
  const btnSend = $('#kbSendBtn');
  const input = $('#kbInput');
  const sheet = $('#kbSheet');

  if (btnKb) btnKb.addEventListener('click', openKbSheet);
  if (btnClose) btnClose.addEventListener('click', closeKbSheet);
  if (sheet) sheet.addEventListener('click', e => { if (e.target === sheet) closeKbSheet(); });

  if (btnSend && input) {
    btnSend.addEventListener('click', () => {
      const text = input.value.trim();
      if (text) {
        sendText(text);
        input.value = '';
        closeKbSheet();
      } else {
        toast('Digite algo para enviar');
      }
    });
    input.addEventListener('keydown', e => {
      if (e.key === 'Enter') btnSend.click();
    });
  }
}

// Prompt para Adicionar à Tela de Início no Safari iOS
function initInstallPrompt() {
  const isIos = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const isStandalone = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;
  const skip = sessionStorage.getItem('onix_pwa_skip');
  const card = $('#installPrompt');

  if (card) {
    if (isIos && !isStandalone && !skip) {
      card.hidden = false;
      const btnSkip = $('#installSkip');
      if (btnSkip) {
        btnSkip.addEventListener('click', () => {
          sessionStorage.setItem('onix_pwa_skip', '1');
          card.hidden = true;
        });
      }
    } else {
      card.hidden = true;
    }
  }
}

// Teclas comuns
function initCommonKeys() {
  $$('[data-key]').forEach(el => {
    el.addEventListener('click', () => sendKey(el.dataset.key));
  });
}

// Inicialização Geral
document.addEventListener('DOMContentLoaded', () => {
  updateStatus();
  initDpad();
  initTouchpad();
  initPower();
  initModeSwitch();
  initViewSwitch();
  initConsoleSelector();
  initTvSheet();
  initKbSheet();
  initCommonKeys();
  initInstallPrompt();

  // Registra Service Worker se disponível
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
});
