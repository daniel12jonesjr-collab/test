// Offline-Speicher: beim ersten Laden alles cachen, danach laeuft die App ohne Internet.
const CACHE = "sandpit-stakes-v1";
const FILES = ["./", "index.html", "manifest.webmanifest", "icon-180.png", "icon-512.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Erst Cache, sonst Netz (und Schriften fuer spaeter mitspeichern)
self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  e.respondWith(caches.match(e.request).then(hit => hit || fetch(e.request).then(res => {
    const copy = res.clone();
    if (res.ok || res.type === "opaque") caches.open(CACHE).then(c => c.put(e.request, copy));
    return res;
  }).catch(() => caches.match("index.html"))));
});
