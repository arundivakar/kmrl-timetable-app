const CACHE_NAME = 'kmrl-pwa-v1';
const PRECACHE_ASSETS = [
  './',
  'index.html',
  'styles.css',
  'app.js',
  'manifest.json',
  'icons/favicon.png',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/apple-touch-icon.png',
  'data/stations.json',
  'data/timetables.json',
  'data/timetables/16W070926_TPHTOFFPEAK_MRP1.json',
  'data/timetables/16W200726_5TPHT_MRP1.json',
  'data/timetables/13S010326_5TPHT_MRP1.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(PRECACHE_ASSETS);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;

  // Don't cache Firebase RTDB network calls
  if (request.url.includes('firebasedatabase.app')) {
    event.respondWith(
      fetch(request).catch(() => new Response(JSON.stringify({}), { headers: { 'Content-Type': 'application/json' } }))
    );
    return;
  }

  // Cache-first, network fallback with background cache update
  event.respondWith(
    caches.match(request).then((cachedResponse) => {
      const fetchPromise = fetch(request).then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(request, responseToCache);
          });
        }
        return networkResponse;
      }).catch(() => cachedResponse);

      return cachedResponse || fetchPromise;
    })
  );
});
