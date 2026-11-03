// Controle do Cobalt GameHub para iOS (iPhone / iPad)
// O Safari não deixa esta página (https) falar com a TV (http na rede de casa). Por isso ela só
// guarda o endereço da TV e abre o controle que o próprio GameHub serve na porta 8772.
'use strict';

const $ = s => document.querySelector(s);
const PORT = 8772;

// Espanhol: a chave é o texto em português
const ES = (navigator.language || '').toLowerCase().startsWith('es') ? {
  'Controle do Cobalt GameHub': 'Control de Cobalt GameHub',
  'CONTROLE DO COBALT GAMEHUB': 'CONTROL DE COBALT GAMEHUB',
  'para iPhone e iPad': 'para iPhone y iPad',
  'Jogue na TV com o iPhone': 'Juega en la TV con el iPhone',
  'Na TV, abra o ': 'En la TV, abre ',
  ' e escolha ': ' y elige ',
  'Usar celular como controle': 'Usar celular como control',
  '. Aparece um QR code e um endereço.': '. Aparece un código QR y una dirección.',
  'Aponte a câmera do iPhone para o QR code. O controle abre no Safari.': 'Apunta la cámara del iPhone al código QR. El control se abre en Safari.',
  'Sem câmera? Digite aqui o endereço que aparece na TV:': '¿Sin cámara? Escribe aquí la dirección que aparece en la TV:',
  'Endereço da TV': 'Dirección de la TV',
  'Abrir controle': 'Abrir control',
  'Digite o endereço que aparece na TV, como 192.168.1.75.': 'Escribe la dirección que aparece en la TV, como 192.168.1.75.',
  'TVs usadas aqui': 'TVs usadas aquí',
  'Dicas': 'Consejos',
  'Tela cheia:': 'Pantalla completa:',
  ' com o controle aberto, toque em ': ' con el control abierto, toca ',
  'Compartilhar': 'Compartir',
  ' no Safari e em ': ' en Safari y ',
  'Adicionar à Tela de Início': 'Agregar a pantalla de inicio',
  '. Ele vira um app no iPhone.': '. Se convierte en una app en el iPhone.',
  'Mesmo Wi-Fi:': 'Mismo Wi-Fi:',
  ' o iPhone e a TV precisam estar na mesma rede, com o GameHub aberto na TV.': ' el iPhone y la TV deben estar en la misma red, con GameHub abierto en la TV.',
  'Jogador 2:': 'Jugador 2:',
  ' com um controle ligado na TV, o iPhone entra como o próximo jogador (P2, P3…).': ' con un control conectado a la TV, el iPhone entra como el siguiente jugador (P2, P3…).',
  'Se o Safari pedir': 'Si Safari lo pide',
  ' acesso à rede local, toque em ': ' acceso a la red local, toca ',
  'Permitir': 'Permitir',
  'Parte do ': 'Parte de ',
  'Abrindo…': 'Abriendo…',
  'Abrir': 'Abrir',
} : null;
const t = s => (ES && ES[s]) || s;

function translate() {
  if (!ES) return;
  document.documentElement.lang = 'es';
  document.title = t(document.title);
  const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  for (let n; (n = walk.nextNode());) {
    const v = n.nodeValue, k = ES[v] ? v : v.trim();
    if (ES[k]) n.nodeValue = v.replace(k, ES[k]);
  }
  document.querySelectorAll('[placeholder],[aria-label]').forEach(e => {
    if (e.placeholder && ES[e.placeholder]) e.placeholder = ES[e.placeholder];
    const a = e.getAttribute('aria-label');
    if (a && ES[a]) e.setAttribute('aria-label', ES[a]);
  });
}

const store = {
  get: (k, def) => { try { const v = localStorage.getItem(k); return v != null ? JSON.parse(v) : def; } catch (_) { return def; } },
  set: (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (_) {} }
};

// Endereço digitado -> só o IP (ou nome) da TV, sem http://, porta ou caminho
function clean(v) {
  v = String(v || '').trim().replace(/^[a-z]+:\/\//i, '').replace(/[/?#].*$/, '').replace(/:\d+$/, '');
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(v) && v.split('.').every(n => +n <= 255)) return v;
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)*$/i.test(v) && /[a-z]/i.test(v)) return v;
  return '';
}

// Últimas TVs (a mais recente primeiro). Quem usava a versão antiga já tem a TV salva.
function recent() {
  let list = store.get('gamehub.tvs', null);
  if (!Array.isArray(list)) {
    const old = clean(store.get('onix.tv.host', ''));
    list = old ? [old] : [];
  }
  return list.filter(clean).slice(0, 3);
}

function open(host) {
  host = clean(host);
  if (!host) {
    $('#tvErr').hidden = false;
    return;
  }
  store.set('gamehub.tvs', [host].concat(recent().filter(h => h !== host)).slice(0, 3));
  $('#tvGo').textContent = t('Abrindo…');
  location.href = 'http://' + host + ':' + PORT + '/';
}

function drawRecent() {
  const list = recent();
  $('#recentBox').hidden = !list.length;
  const box = $('#recentList');
  box.textContent = '';
  list.forEach(host => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'tv';
    b.append(host);
    const s = document.createElement('span');
    s.textContent = t('Abrir') + ' →';
    b.append(s);
    b.addEventListener('click', () => open(host));
    box.append(b);
  });
}

document.addEventListener('DOMContentLoaded', () => {
  translate();
  const input = $('#tvIp');
  input.value = recent()[0] || '';
  input.addEventListener('input', () => { $('#tvErr').hidden = true; });
  $('#tvForm').addEventListener('submit', e => {
    e.preventDefault();
    input.blur();
    open(input.value);
  });
  drawRecent();

  // Link pronto vindo da TV: ?tv=192.168.1.75 abre o controle direto
  const tv = new URLSearchParams(location.search).get('tv');
  if (tv && clean(tv)) open(tv);

  // Voltou para cá pelo "voltar" do Safari: o botão volta ao normal
  window.addEventListener('pageshow', () => { $('#tvGo').textContent = t('Abrir controle'); });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
});
