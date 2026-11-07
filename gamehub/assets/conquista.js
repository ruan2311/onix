'use strict';
/* Conquistas: o aviso por cima do jogo (normal, rara e platina), com som e animação.
   DEMONSTRAÇÃO (branch demo-salto-de-fe): só a conquista ilustrativa do Super Mario World. */
// Textos em espanhol das conquistas (vão junto para o dicionário ES do app).
Object.assign(ES, {
  'Conquistas': 'Logros', 'conquistas': 'logros', 'pontos': 'puntos', 'código': 'código', 'RetroAchievements': 'RetroAchievements',
  'Conquista desbloqueada': 'Logro desbloqueado', 'Conquista rara · 3,2% dos jogadores': 'Logro raro · 3,2% de los jugadores',
  'Platina · 100% completo': 'Platino · 100% completado', 'Conquista do Cobalt': 'Logro de Cobalt', 'Conquistas do Cobalt': 'Logros de Cobalt',
  'Conta conectada': 'Cuenta conectada', 'Conta do RetroAchievements': 'Cuenta de RetroAchievements', 'Olá, ruan2311!': '¡Hola, ruan2311!',
  '1.240 pontos · conquistas ligadas': '1.240 puntos · logros activados', 'Desbloqueadas recentemente': 'Desbloqueados recientemente',
  'Seus jogos': 'Tus juegos', 'conquistas de cada jogo': 'logros de cada juego', 'jogos completos': 'juegos completos', 'do Cobalt': 'de Cobalt',
  'só neste aparelho, sem conta': 'solo en este dispositivo, sin cuenta', 'Ver no celular': 'Ver en el celular', 'Sair da conta': 'Cerrar sesión',
  'Ganhe conquistas nos seus jogos': 'Gana logros en tus juegos', 'Entre pelo celular': 'Entra desde el celular', 'Entrar': 'Entrar',
  'Entre com sua conta grátis do RetroAchievements pelo celular. Suas conquistas e pontos ficam salvos na nuvem.':
    'Entra con tu cuenta gratis de RetroAchievements desde el celular. Tus logros y puntos quedan guardados en la nube.',
  'Aponte a câmera do celular para o QR code.': 'Apunta la cámara del celular al código QR.',
  'Ou digite no navegador: {u}': 'O escribe en el navegador: {u}',
  'Digite seu usuário e senha do RetroAchievements na página que abrir.': 'Escribe tu usuario y contraseña de RetroAchievements en la página que se abra.',
  'Pronto: a TV entra sozinha na sua conta.': 'Listo: la TV entra sola en tu cuenta.',
  'Não tem conta? Crie grátis em retroachievements.org. O celular precisa estar no mesmo Wi-Fi da TV.':
    '¿No tienes cuenta? Créala gratis en retroachievements.org. El celular tiene que estar en el mismo Wi-Fi que la TV.',
  'Esperando o celular…': 'Esperando el celular…', 'Digitar aqui na TV': 'Escribir aquí en la TV', 'Agora não': 'Ahora no', 'Cancelar': 'Cancelar',
});
ICON.trophy = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 3h10v2h3v3a4 4 0 0 1-4 4h-.3A5 5 0 0 1 13 14.9V18h3v3H8v-3h3v-3.1A5 5 0 0 1 8.3 12H8a4 4 0 0 1-4-4V5h3zm0 4H6v1a2 2 0 0 0 1.2 1.8A5 5 0 0 1 7 8.5zm10 0v1.5c0 .5 0 .9-.2 1.3A2 2 0 0 0 18 8V7z"/></svg>';
ICON.crown = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 7l4.5 4L12 4l4.5 7L21 7l-2 12H5z"/></svg>';
const PIX = {
  coin:   ['..####..','.#YYYY#.','#YY##YY#','#Y#YY#Y#','#Y#YY#Y#','#YY##YY#','.#YYYY#.','..####..'],
  star:   ['...##...','...YY...','..#YY#..','########','.#YYYY#.','..#YY#..','.#Y##Y#.','.#....#.'],
  sword:  ['......##','.....#W#','....#W#.','.#.#W#..','..#W#...','..##....','.#..#...','#.......'],
  heart:  ['........','.##..##.','#RR##RR#','#RRRRRR#','.#RRRR#.','..#RR#..','...##...','........'],
  flag:   ['.#......','.#RRRR..','.#RRRRR.','.#RRRR..','.#......','.#......','.#......','###.....'],
  key:    ['.###....','#Y.Y#...','#Y.Y####','.###.Y.Y','.....#.#','........','........','........'],
  clock:  ['..####..','.#WWWW#.','#WW#WWW#','#WW#WWW#','#WW###W#','#WWWWWW#','.#WWWW#.','..####..'],
  moon:   ['...###..','..#WW...','.#WW....','.#WW....','.#WW....','.#WWW...','..#WWW#.','...###..'],
  globe:  ['..####..','.#CGGC#.','#CGGCCC#','#CCGGGC#','#GCCGGC#','#CCCCGC#','.#CCCC#.','..####..'],
  rewind: ['........','...#..#.','..##.##.','.###.###','..##.##.','...#..#.','........','........'],
  gem:    ['.######.','#CWCCCC#','#CCCCCC#','.#CCCC#.','..#CC#..','...##...','........','........'],
  pad:    ['........','.######.','#W#WW#W#','###WW#R#','#W#WW#W#','.######.','........','........'],
};
PIX.trophy = ['#WWWWWW#','#WCWWCW#','.#WWWW#.','..#WW#..','...##...','..#WW#..','.######.','........'];
const PCOL = { '#': '#14082c', Y: '#ffd27a', W: '#f1edff', R: '#ff5a6e', C: '#3de1ff', G: '#4ade80' };
function badge(kind, bg) {
  const m = PIX[kind]; let r = '';
  m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch !== '.') r += `<rect x="${x + 2}" y="${y + 2}" width="1" height="1" fill="${PCOL[ch]}"/>`; }));
  return svg(`<svg viewBox="0 0 12 12" shape-rendering="crispEdges"><defs><linearGradient id="g${kind}${bg.slice(1)}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${bg}"/><stop offset="1" stop-color="#1b0f3d"/></linearGradient></defs><rect width="12" height="12" fill="url(#g${kind}${bg.slice(1)})"/>${r}</svg>`);
}
const SCENE = new URLSearchParams(location.search).get('scene') || 'home';
const RA_ON = SCENE === 'ok' || SCENE === 'ach';

function raBanner() {
  if (RA_ON) return null;
  return h('div', { class: 'ra-banner' },
    h('div', { class: 'trophy' }, icon('trophy')),
    h('div', { class: 'txt' }, h('b', { text: t('Ganhe conquistas nos seus jogos') }),
      h('p', { text: t('Entre com sua conta grátis do RetroAchievements pelo celular. Suas conquistas e pontos ficam salvos na nuvem.') })),
    h('div', { class: 'acts' }, h('button', { class: 'f btn primary', act: raLogin }, icon('trophy'), t('Entrar')), h('button', { class: 'f btn', text: t('Agora não') })));
}
const ALIEN = [
  ['..#..#..', '.######.', '##.##.##', '########', '.#.##.#.', '#......#'],
  ['..#..#..', '.######.', '##.##.##', '########', '#..##..#', '.#....#.'],
];
const ALIEN2 = [
  ['...##...', '.######.', '#.####.#', '########', '..#..#..', '.#.##.#.'],
  ['...##...', '.######.', '#.####.#', '########', '.#.##.#.', '#......#'],
];
const UFO = ['...######...', '.##########.', '##.##.##.###', '############', '..##....##..'];
function sprite(m, color) {
  let r = '';
  m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') r += `<rect x="${x}" y="${y}" width="1" height="1"/>`; }));
  return `<svg viewBox="0 0 ${m[0].length} ${m.length}" shape-rendering="crispEdges" fill="${color}" style="color:${color}">${r}</svg>`;
}
// Sons das conquistas: chiptune feito na hora pelo navegador (sem arquivo de áudio). Melodias próprias.
// raSoundTo(ctx, tier) agenda tudo num AudioContext (o de tocar ou um OfflineAudioContext, para gravar).
function raSoundTo(ctx, tier) {
  const t0 = ctx.currentTime + 0.05;
  const master = ctx.createGain(); master.gain.value = { rare: 0.55, plat: 0.48 }[tier] || 0.95;   // volumes parecidos entre os três
  const comp = ctx.createDynamicsCompressor(), low = ctx.createBiquadFilter();   // corta o grave que a TV não toca
  low.type = 'highpass'; low.frequency.value = 40; master.connect(low); low.connect(comp); comp.connect(ctx.destination);
  let out = master;
  if (tier === 'rare' || tier === 'plat') {   // eco curto, para soar "maior"
    const dry = ctx.createGain(), d = ctx.createDelay(1), fb = ctx.createGain(), wet = ctx.createGain();
    d.delayTime.value = tier === 'plat' ? 0.19 : 0.15; fb.gain.value = 0.32; wet.gain.value = 0.38;
    dry.connect(master); dry.connect(d); d.connect(fb); fb.connect(d); d.connect(wet); wet.connect(master);
    out = dry;
  }
  const F = n => 440 * Math.pow(2, (n - 69) / 12);   // número MIDI → frequência
  const note = (type, n, at, dur, vol, o = {}) => {
    const osc = ctx.createOscillator(), g = ctx.createGain(), s = t0 + at;
    osc.type = type; osc.frequency.setValueAtTime(F(n), s);
    if (o.slide) osc.frequency.exponentialRampToValueAtTime(F(n + o.slide), s + dur);
    if (o.vib) {
      const lfo = ctx.createOscillator(), lg = ctx.createGain();
      lfo.frequency.value = 6; lg.gain.setValueAtTime(0, s); lg.gain.linearRampToValueAtTime(F(n) * 0.012, s + Math.min(0.3, dur / 2));
      lfo.connect(lg); lg.connect(osc.frequency); lfo.start(s); lfo.stop(s + dur + 0.05);
    }
    g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + 0.008);
    if (o.hold) g.gain.setValueAtTime(vol, s + dur * o.hold);
    g.gain.exponentialRampToValueAtTime(0.0001, s + dur);
    let last = g;
    if (o.pan) { const pn = ctx.createStereoPanner(); pn.pan.value = o.pan; g.connect(pn); last = pn; }
    osc.connect(g); last.connect(out); osc.start(s); osc.stop(s + dur + 0.02);
  };
  let noiseBuf = null;
  const noise = (at, dur, vol, hp, o = {}) => {   // chiado: brilho, caixa e prato
    if (!noiseBuf) {
      noiseBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
      const ch = noiseBuf.getChannelData(0); let x = 1;
      for (let i = 0; i < ch.length; i++) { x = (x * 16807) % 2147483647; ch[i] = x / 1073741823.5 - 1; }
    }
    const src = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain(), s = t0 + at;
    src.buffer = noiseBuf; f.type = 'highpass'; f.frequency.value = hp;
    g.gain.setValueAtTime(o.from || 0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + (o.attack || 0.004));
    g.gain.exponentialRampToValueAtTime(0.0001, s + dur);
    src.connect(f); f.connect(g); g.connect(out); src.start(s); src.stop(s + dur + 0.02);
  };
  if (tier === 'plat') {
    // Platina (~7 s): suspense subindo → pancada → fanfarra de metais → cadência de vitória (lá♭–si♭–dó) → acorde final enorme.
    const voice = (n, at, dur, vol, o = {}) => {   // "metal": dois serrotes desafinados de leve, com filtro que abre no ataque
      const s = t0 + at, g = ctx.createGain(), f = ctx.createBiquadFilter();
      f.type = 'lowpass'; f.Q.value = 1.2;
      f.frequency.setValueAtTime(500, s); f.frequency.exponentialRampToValueAtTime(o.bright || 3800, s + 0.05);
      f.frequency.exponentialRampToValueAtTime((o.bright || 3800) * 0.55, s + Math.min(dur, 0.4));
      g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(vol, s + 0.02);
      g.gain.setValueAtTime(vol * 0.85, s + dur * (o.hold || 0.7)); g.gain.exponentialRampToValueAtTime(0.0001, s + dur);
      let last = g;
      if (o.pan) { const pn = ctx.createStereoPanner(); pn.pan.value = o.pan; g.connect(pn); last = pn; }
      f.connect(g); last.connect(out);
      for (const [type, cents] of [['sawtooth', -7], ['sawtooth', 7], ['square', 0]]) {
        const osc = ctx.createOscillator(); osc.type = type; osc.frequency.value = F(n); osc.detune.value = cents;
        if (o.vib) {
          const lfo = ctx.createOscillator(), lg = ctx.createGain(); lfo.frequency.value = 5.5;
          lg.gain.setValueAtTime(0, s); lg.gain.linearRampToValueAtTime(18, s + 0.6); lfo.connect(lg); lg.connect(osc.detune); lfo.start(s); lfo.stop(s + dur);
        }
        const og = ctx.createGain(); og.gain.value = type === 'square' ? 0.5 : 0.35;
        osc.connect(og); og.connect(f); osc.start(s); osc.stop(s + dur + 0.02);
      }
    };
    const kick = (at, vol = 0.9) => {
      const osc = ctx.createOscillator(), g = ctx.createGain(), s = t0 + at;
      osc.frequency.setValueAtTime(150, s); osc.frequency.exponentialRampToValueAtTime(42, s + 0.18);
      g.gain.setValueAtTime(vol, s); g.gain.exponentialRampToValueAtTime(0.0001, s + 0.35);
      osc.connect(g); g.connect(master); osc.start(s); osc.stop(s + 0.4);
    };
    const snare = (at, vol = 0.18) => { noise(at, 0.13, vol, 1500); note('triangle', 50, at, 0.07, vol * 1.4); };
    const chord = (ns, at, dur, vol, o) => ns.forEach((n, i) => voice(n, at, dur, vol, { ...o, pan: o && o.spread ? (i / (ns.length - 1) - 0.5) * 0.7 : 0 }));
    // 1) Suspense: chiado que sobe, nota subindo e rufar acelerando
    { const src = ctx.createBufferSource(), f = ctx.createBiquadFilter(), g = ctx.createGain(), s = t0;
      noise(0, 0.01, 0.0002, 100);   // cria o chiado
      src.buffer = noiseBuf; src.loop = true; f.type = 'bandpass'; f.Q.value = 2;
      f.frequency.setValueAtTime(400, s); f.frequency.exponentialRampToValueAtTime(9000, s + 0.8);
      g.gain.setValueAtTime(0.0001, s); g.gain.exponentialRampToValueAtTime(0.25, s + 0.78); g.gain.exponentialRampToValueAtTime(0.0001, s + 0.84);
      src.connect(f); f.connect(g); g.connect(out); src.start(s); src.stop(s + 0.86); }
    note('square', 60, 0, 0.8, 0.07, { slide: 24, hold: 0.95 });
    for (let i = 0, at = 0; at < 0.78; i++, at += 0.09 - i * 0.004) noise(at, 0.05, 0.04 + at * 0.22, 2200);
    // 2) Pancada
    kick(0.8, 1); noise(0.8, 1.2, 0.28, 4500);
    chord([48, 55, 60, 64, 67], 0.8, 0.32, 0.07, { bright: 5000, spread: 1 });
    // 3) Fanfarra (melodia própria) com baixo e bateria
    const mel = [[67, 1.10, .09], [67, 1.20, .09], [67, 1.30, .09], [72, 1.40, .42], [76, 1.85, .18], [79, 2.05, .18], [76, 2.25, .14], [79, 2.40, .50]];
    mel.forEach(([n, at, d]) => { voice(n, at, d, 0.11, { bright: 4500 }); voice(n - 12, at, d, 0.07); });
    [[48, 1.40], [55, 1.62], [48, 1.85], [55, 2.05], [52, 2.25], [55, 2.40], [48, 2.62]].forEach(([n, at]) => note('triangle', n - 12, at, 0.2, 0.32, { hold: 0.7 }));
    chord([60, 64, 67], 1.40, 0.42, 0.035); chord([60, 64, 67], 1.85, 0.5, 0.035); chord([59, 62, 67], 2.40, 0.5, 0.035);
    [1.40, 1.85, 2.40].forEach(at => kick(at, 0.75)); [1.62, 2.05, 2.25, 2.62].forEach(at => snare(at));
    // 4) Cadência de vitória: lá♭ → si♭ → DÓ, cada degrau com pancada e virada de caixa no fim
    chord([44, 56, 60, 63, 68], 2.95, 0.42, 0.06, { spread: 1 }); voice(80, 2.95, 0.42, 0.12, { bright: 5000 }); note('triangle', 44, 2.95, 0.42, 0.3); kick(2.95);
    chord([46, 58, 62, 65, 70], 3.40, 0.42, 0.06, { spread: 1 }); voice(82, 3.40, 0.42, 0.12, { bright: 5000 }); note('triangle', 46, 3.40, 0.42, 0.3); kick(3.40);
    for (let i = 0; i < 6; i++) snare(3.55 + i * 0.05, 0.06 + i * 0.03);
    // 5) DÓ final: enorme, longo, com vibrato, prato, bumbo duplo e brilhos de um lado para o outro
    const END = 3.85;
    kick(END, 1.1); kick(END + 0.12, 0.6); noise(END, 2.4, 0.3, 4000); noise(END, 0.25, 0.2, 800);
    chord([36, 48, 55, 60, 64, 67, 72], END, 2.9, 0.055, { spread: 1, vib: 1, hold: 0.55, bright: 4200 });
    voice(84, END, 2.9, 0.13, { vib: 1, hold: 0.55, bright: 6000 }); voice(72, END, 2.9, 0.08, { vib: 1, hold: 0.55 });
    note('triangle', 36, END, 2.6, 0.3, { hold: 0.5 });
    for (let i = 0; i < 6; i++) note('triangle', 36, END + 0.25 + i * 0.07, 0.09, 0.12 + i * 0.03);   // rufar de tímpano
    const sp = [96, 100, 103, 108, 112, 108, 103, 100, 103, 108, 112, 115];
    sp.forEach((n, i) => note('triangle', n, END + 0.35 + i * 0.085, 0.16, 0.06, { pan: i % 2 ? 0.7 : -0.7 }));
    [[96, 5.3], [108, 5.3], [115, 5.3]].forEach(([n, at], i) => note('sine', n, at, 1.6, [0.12, 0.06, 0.03][i]));   // sino no fim
  } else if (tier === 'rare') {
    // Rara (~1,6 s): arpejo subindo rápido, acorde brilhante com vibrato e faíscas.
    [62, 66, 69, 74, 78, 81].forEach((n, i) => note('square', n, i * 0.055, .14, .12));
    note('triangle', 86, .33, 1.0, .16, { vib: 1, hold: .3 });
    note('square', 81, .33, .9, .05, { vib: 1, hold: .3 });
    note('triangle', 50, .33, .7, .22, { hold: .5 });
    [.45, .62, .74, .9, 1.05].forEach((at, i) => note('triangle', 98 + (i % 3) * 3, at, .07, .05));
    noise(.33, .5, .06, 7000);
  } else {
    // Normal (~0,6 s): "plim-plim" curto e alegre.
    note('square', 81, 0, .09, .12);
    note('square', 88, .085, .5, .12, { hold: .15 });
    note('triangle', 93, .085, .45, .07);
  }
  return tier === 'plat' ? 7.2 : tier === 'rare' ? 2.4 : 1.2;   // segundos até o som acabar (com eco)
}
// O som é gravado uma vez (OfflineAudioContext, fora da hora de tocar) e depois só toca o áudio pronto.
// Montado na hora, a platina criava centenas de peças de áudio e estalava na TV enquanto a animação pesava.
let raAudio = null;
const raBuffers = {};
function raPrepareSound(tier) {
  if (!raBuffers[tier]) {
    const rate = (raAudio && raAudio.sampleRate) || 48000, len = { plat: 7.5, rare: 2.7 }[tier] || 1.5;
    const ctx = new OfflineAudioContext(2, Math.ceil(rate * len), rate);
    raSoundTo(ctx, tier);
    raBuffers[tier] = ctx.startRendering();
  }
  return raBuffers[tier];
}
function raSound(tier) {
  try {
    raAudio = raAudio || new (window.AudioContext || window.webkitAudioContext)();
    if (raAudio.state === 'suspended') raAudio.resume();
    raPrepareSound(tier).then(buf => { const s = raAudio.createBufferSource(); s.buffer = buf; s.connect(raAudio.destination); s.start(); }).catch(() => {});
  } catch (e) { /* sem som não é motivo para não mostrar a conquista */ }
}
// Aquecimento: na primeira vez, o navegador gasta até 2 s preparando os desenhos da platina e a tela trava.
// raWarm() monta a platina e a rara quase invisíveis (1%), sem som, e apaga; depois disso elas aparecem sem pausa.
// No app: chamar quando o jogo está abrindo (tela preta de carregamento), com a página do menu visível.
function raWarm() {
  try { ['plat', 'rare', 'normal'].forEach(raPrepareSound); } catch (e) {}   // grava os sons já, fora do jogo
  raPop('trophy', '', '', '+400 pts', 'plat', true);
  setTimeout(() => raPop('sword', '', '', '50 pts', 'rare', true), 2300);
}
// Cada pose vira uma imagem pronta, já com o brilho dentro (desenhado uma vez). Na TV, o brilho por CSS (drop-shadow)
// era refeito pela placa de vídeo nos 24 bichinhos a cada quadro e derrubava a platina para ~22 fps.
const spriteArt = {};
function spriteTile(m, color, unit) {   // { cv: canvas pronto no tamanho certo, w, h, ready }
  const pad = 2.5, key = m.join('/') + color + unit;
  let s = spriteArt[key];
  if (!s) {
    const w = m[0].length, hh = m.length;
    s = spriteArt[key] = { ready: false, w: (w + pad * 2) * unit, h: (hh + pad * 2) * unit, cv: null, wait: [] };
    let r = '';
    m.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') r += `<rect x="${x}" y="${y}" width="1" height="1"/>`; }));
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${-pad} ${-pad} ${w + pad * 2} ${hh + pad * 2}" width="${(w + pad * 2) * 8}" height="${(hh + pad * 2) * 8}" fill="${color}">`
      + `<defs><filter id="g" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation=".85"/></filter></defs>`
      + `<g filter="url(#g)" opacity=".9">${r}</g><g shape-rendering="crispEdges">${r}</g></svg>`;
    const img = new Image();
    img.onload = () => {
      const d = window.devicePixelRatio || 1, cv = document.createElement('canvas');
      cv.width = Math.ceil(s.w * d); cv.height = Math.ceil(s.h * d);
      cv.getContext('2d').drawImage(img, 0, 0, cv.width, cv.height);
      s.cv = cv; s.ready = true; s.wait.splice(0).forEach(f => f());
    };
    img.src = 'data:image/svg+xml,' + encodeURIComponent(svg);
  }
  return s;
}
const raQueue = [];
let RA_SIZE = 0.85;   // a tela da conquista pode ser menor (metade da resolução): as medidas em px acompanham   // tamanho dos avisos (o CSS do aviso já está nessa escala)
function raPop(kind, title, sub, pts, cls, warm) {
  RA_SIZE = 0.85 * Math.min(1, (innerWidth || 960) / 960);
  if (warm && document.querySelector('.ra-pop:not(.ra-warm)')) return;   // nunca atrapalha uma conquista de verdade
  // Fila: se já tem uma conquista na tela, a nova espera ela sair (ex.: a última conquista do jogo e a platina juntas).
  if (!warm && document.querySelector('.ra-pop:not(.ra-warm)') && !/[?&](t|enter)=/.test(location.search)) { raQueue.push([kind, title, sub, pts, cls]); return; }
  document.querySelectorAll('.ra-pop').forEach(e => e.remove());
  const tier = cls || 'normal';
  const kicker = tier === 'ok' ? t('Conta conectada') : tier === 'local' ? t('Conquista do Cobalt') : tier === 'rare' ? t('Conquista rara · 3,2% dos jogadores')
    : tier === 'plat' ? t('Platina · 100% completo') : t('Conquista desbloqueada');
  const bg = { ok: '#4ade80', local: '#3de1ff', rare: '#4ade80', plat: '#c9f1ff' }[tier] || '#ffb347';
  const fx = h('div', { class: 'fx' }), spin = h('i'), ring = h('div', { class: 'ring' }, spin), shine = h('div', { class: 'shine' }, h('i'));
  const glow = tier === 'plat' ? h('div', { class: 'glow' }) : null;
  const el = h('div', { class: 'ra-pop ' + tier + (warm ? ' ra-warm' : '') }, h('div', { class: 'bgp' }), ring, shine, glow,
    h('div', { class: 'badge' }, badge(kind, bg)),
    h('div', { class: 't' }, h('small', null, icon(tier === 'ok' ? 'star' : 'trophy'), kicker), h('b', { text: title }), h('span', { text: sub })),
    pts ? h('div', { class: 'pts', text: pts }) : null, fx);
  document.body.append(el);
  // Tudo anda por um relógio só (T em ms), com volta de 3 s: assim o GIF fecha o ciclo sem pulo.
  const LOOP = 3000;
  // Mede uma vez só: ler o tamanho no meio do quadro obrigava a página a se recalcular dezenas de vezes.
  // E tudo se mexe por transform/opacity, que a placa de vídeo faz sem redesenhar nem recalcular a página.
  const W = el.offsetWidth, H = el.offsetHeight, D = Math.ceil(Math.hypot(W, H));
  Object.assign(spin.style, { width: D + 'px', height: D + 'px', margin: (-D / 2) + 'px 0 0 ' + (-D / 2) + 'px' });
  if (glow) {
    const b = el.querySelector('.badge');
    Object.assign(glow.style, { left: b.offsetLeft + 'px', top: b.offsetTop + 'px', width: b.offsetWidth + 'px', height: b.offsetHeight + 'px' });
  }
  const move = (e, x, y, extra) => { e.style.transform = `translate3d(${x}px,${y}px,0)` + (extra || ''); };
  const along = (d, out) => {   // ponto na borda, a distância d do canto de cima à esquerda (sentido horário)
    const w = W + out * 2, hh = H + out * 2, P = 2 * (w + hh); d = ((d % P) + P) % P;
    if (d < w) return [d - out, -out];
    if (d < w + hh) return [w - out, d - w - out];
    if (d < 2 * w + hh) return [w - (d - w - hh) - out, hh - out];
    return [-out, hh - (d - 2 * w - hh) - out];
  };
  const rnd = s => { const x = Math.sin(s * 999.7) * 43758.5453; return x - Math.floor(x); };
  // Bichinhos, faíscas e disco voador: tudo desenhado numa tela de desenho (canvas) só. Eram ~65 peças soltas,
  // e a placa de vídeo da TV tinha que juntar todas a cada quadro (a platina ficava em ~36 fps na TV).
  const rem = (parseFloat(getComputedStyle(document.documentElement).fontSize) || 16) * RA_SIZE, dpr = window.devicePixelRatio || 1;
  let g = null, M = 0, aliens = [], px = [], ufo = null, entered = false, lastT = 0;
  if (tier === 'rare' || tier === 'plat') {
    M = Math.ceil(Math.max(160 * RA_SIZE, H * 0.3 + 100 * RA_SIZE));   // folga em volta do aviso, onde os efeitos andam
    const cv = document.createElement('canvas');
    cv.width = Math.ceil((W + 2 * M) * dpr); cv.height = Math.ceil((H + 2 * M) * dpr);
    Object.assign(cv.style, { position: 'absolute', left: -M + 'px', top: -M + 'px', right: 'auto', bottom: 'auto', width: W + 2 * M + 'px', height: H + 2 * M + 'px',
      transform: 'none', aspectRatio: 'auto', imageRendering: 'auto', margin: '0' });   // imune a regras gerais de canvas da página
    fx.append(cv); g = cv.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, M * dpr, M * dpr);   // (0,0) = canto do aviso
    const tile = (m, color) => {   // mesmo tamanho dos bichinhos de antes: 1,9 × 1,5 rem
      const s = spriteTile(m, color, Math.min(1.9 / m[0].length, 1.5 / m.length) * rem);
      if (!s.ready) s.wait.push(() => frame(lastT));
      return s;
    };
    const lanes = tier === 'plat' ? [[0, 12, '#9fe8ff', ALIEN], [22 * RA_SIZE, 12, '#ff9ad5', ALIEN2]] : [[4 * RA_SIZE, 10, '#4ade80', ALIEN]];
    for (const [out, n, color, art] of lanes) for (let i = 0; i < n; i++) {
      const c = i % 2 && tier === 'rare' ? '#3de1ff' : color;
      aliens.push({ poses: art.map(m => tile(m, c)), i, gap: 2 * (W + H + out * 4) / n, out, dir: out ? -1 : 1 });
    }
    const cols = ['#ffffff', '#9fe8ff', '#ff9ad5', '#ffd27a'];
    for (let i = 0; i < (tier === 'plat' ? 40 : 10); i++) {
      const burst = i % 4, j = Math.floor(i / 4);
      if (tier === 'rare') {   // estrelinhas piscando em volta: o lugar não muda, só o brilho
        const [x, y] = along(rnd(i) * 2 * (W + H), (18 + rnd(i + 9) * 14) * RA_SIZE);
        px.push({ x, y });
      } else {                 // fogos de pixel: 4 explosões, cada uma volta a cada 1,5 s
        px.push({ burst, ang: j / 10 * Math.PI * 2, col: cols[(burst + j) % 4], cx: [0.08, 0.35, 0.7, 0.95][burst] * W, cy: [-0.25, 1.25, -0.3, 1.2][burst] * H });
      }
    }
    if (tier === 'plat') ufo = tile(UFO, '#ff3d9a');
  }
  const put = (s, x, y) => { if (s.ready) g.drawImage(s.cv, x - s.w / 2, y - s.h / 2, s.w, s.h); };
  function frame(T) {
    lastT = T;
    const k = (T % LOOP) / LOOP;
    const speed = tier === 'plat' ? 2 : 1;
    spin.style.transform = `rotate(${k * 360 * speed}deg)`;
    move(shine.firstChild, (-0.4 + ((T % 1500) / 1500) * 1.8) * W, 0, ' skewX(-20deg)');
    if (g) {
      g.clearRect(-M, -M, W + 2 * M, H + 2 * M);
      const f = Math.floor(Math.max(0, T) / 250) % 2;
      for (const a of aliens) { const [x, y] = along(a.dir * k * a.gap + a.i * a.gap, a.out); put(a.poses[f], x, y); }
      px.forEach((p, i) => {
        if (tier === 'rare') {
          g.globalAlpha = ((T / 375 + i * 1.7) % 4) < 1.4 ? 1 : .15; g.fillStyle = '#a3ff5a';
          g.fillRect(p.x, p.y, .35 * rem, .35 * rem);
        } else {
          const ph = ((T + p.burst * 375) % 1500) / 1500, r = ph * 70 * RA_SIZE, s = .5 * rem * (1 - ph * .5);
          g.globalAlpha = ph < .85 ? 1 - ph * .9 : 0; g.fillStyle = p.col;
          g.fillRect(p.cx + Math.cos(p.ang) * r, p.cy + Math.sin(p.ang) * r, s, s);
        }
      });
      g.globalAlpha = 1;
      if (ufo) put(ufo, (-60 + k * (W / RA_SIZE + 120)) * RA_SIZE, -40 * RA_SIZE);
    }
    // O brilho do troféu só acende depois que ele termina de girar na entrada (antes parecia um buraco escuro).
    if (glow) glow.style.opacity = (1.6 + Math.sin(k * Math.PI * 4) * .9) / 2.5 * (entered ? Math.min(1, Math.max(0, (T - 1600) / 450)) : 1);
  }
  // Entrada: normal desliza pela lateral, rara pipoca, platina cai como carimbo na pancada do som (0,85 s).
  // Só animações de CSS em transform/opacity: quem anda é a placa de vídeo, sem pesar no jogo.
  const enter = keep => {
    entered = true;
    el.classList.add('in');
    const extras = [];
    const add = (cls, style) => { const e = h('div', { class: 'ra-in ' + cls + (warm ? ' ra-warm' : ''), style }); document.body.append(e); extras.push(e); return e; };
    const box = { left: el.offsetLeft + 'px', top: el.offsetTop + 'px', width: W + 'px', height: H + 'px' };
    if (tier === 'rare') add('burst rare', box);
    if (tier === 'plat') {
      const c = Math.max(W, H) * 0.6;   // pequena: cresce pelo transform, sem pintar uma área enorme
      add('charge', { left: el.offsetLeft + W / 2 - c / 2 + 'px', top: el.offsetTop + H / 2 - c / 2 + 'px', width: c + 'px', height: c + 'px' });
      add('burst plat', box); add('burst plat late', box); add('flash');
    }
    if (!keep) setTimeout(() => extras.forEach(e => e.remove()), 2200);
  };
  const q = new URLSearchParams(location.search), fixed = q.get('t');
  if (q.has('enter')) {   // para gravar a entrada quadro a quadro: tudo parado, e raSeek(T) leva ao instante T
    enter(true);
    window.raSeek = T => { document.getAnimations().forEach(a => { a.pause(); a.currentTime = T; }); frame(T); };
    window.raSeek(0);
  } else if (fixed != null) frame(+fixed);
  else {
    enter(); const t0 = performance.now(); let last = -1e9; (function loop(now) { if (!el.isConnected) return; requestAnimationFrame(loop); if (now - last < 28) return; last = now; frame(Math.max(0, now - t0)); })(t0);   // 30 quadros por segundo: sobra mais para o jogo   // o quadro pode vir marcado um instante antes de t0
    if (warm) { setTimeout(() => el.remove(), 2200); return; }
    raSound(tier);
    // Tempo na tela: normal 5 s, rara 10 s, platina 1 min (dá tempo de tirar foto da TV).
    const stay = { rare: 10000, plat: 60000 }[tier] || 5000;
    setTimeout(() => { el.classList.add('out'); setTimeout(() => { el.remove(); const next = raQueue.shift(); if (next) setTimeout(() => raPop(...next), 300); }, 450); }, stay);
  }
}
