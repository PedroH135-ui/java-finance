/* A versão e os arquivos públicos são preenchidos durante o build. */
const CACHE = '__CACHE_VERSION__';
const ASSETS = __PUBLIC_ASSETS__;
const paths = new Set(ASSETS);
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('java-finance-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('message', event => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  // APIs, cookies de autenticação e respostas financeiras nunca entram no cache.
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(self.navigator.onLine === false
      ? caches.match('/offline.html')
      : fetch(event.request, { cache: 'no-store' }).catch(() => caches.match('/offline.html')));
  } else if (paths.has(url.pathname)) {
    event.respondWith(caches.open(CACHE).then(async cache => (await cache.match(url.pathname)) ?? fetch(event.request)));
  }
});
