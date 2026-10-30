// Service worker do GameHub no iPhone: deixa o app abrir sem internet e entrega às telas as capas
// e miniaturas, que ficam no IndexedDB. O build.sh preenche a lista de arquivos e a versão.
'use strict';
const CACHE = 'gamehub-__HASH__';
const FILES = __FILES__;

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith('gamehub-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const JPG = /\/(?:cover\/([0-9a-f]{16})\.jpg|shot\/default\/([0-9a-f]{16})\/(auto|[1-4])\.jpg)$/;
function fileFromDb(key) {
  return new Promise(res => {
    const open = indexedDB.open('onix-gamehub', 1);
    open.onupgradeneeded = () => { open.result.createObjectStore('kv'); open.result.createObjectStore('files'); };
    open.onerror = () => res(null);
    open.onsuccess = () => {
      const get = open.result.transaction('files').objectStore('files').get(key);
      get.onerror = () => { open.result.close(); res(null); };
      get.onsuccess = () => { open.result.close(); res(get.result || null); };
    };
  });
}
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (url.origin !== location.origin || e.request.method !== 'GET') return;
  const m = JPG.exec(url.pathname);
  if (m) {
    const key = m[1] ? 'cover/' + m[1] + '.jpg' : 'shot/' + m[2] + '/' + m[3] + '.jpg';
    e.respondWith(fileFromDb(key).then(data => (data ? new Response(data, { headers: { 'Content-Type': 'image/jpeg', 'Cache-Control': 'no-store' } }) : new Response('', { status: 404 }))));
    return;
  }
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then(hit => hit || fetch(e.request)));
});
