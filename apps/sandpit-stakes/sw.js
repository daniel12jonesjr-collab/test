// Offline-Speicher fuer die installierte App.
// Seite + version.json: immer erst frisch aus dem Netz (Updates kommen sofort an), ohne Netz aus dem Speicher.
// Icons und Schriften: erst Speicher, sonst Netz.
const CACHE = "sandpit-stakes-v3";
const FILES = ["./", "index.html", "manifest.webmanifest", "icon-180.png", "icon-512.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  const url = new URL(e.request.url);
  const fresh = e.request.mode === "navigate" || url.pathname.endsWith("version.json") || url.pathname.endsWith("index.html");
  if (fresh) {
    e.respondWith(fetch(e.request.url, { cache: "no-store" }).then(res => {
      if (e.request.mode === "navigate") { const copy = res.clone(); caches.open(CACHE).then(c => c.put("index.html", copy)); }
      return res;
    }).catch(() => caches.match("index.html")));
    return;
  }
  e.respondWith(caches.match(e.request).then(hit => hit || fetch(e.request).then(res => {
    const copy = res.clone();
    if (res.ok || res.type === "opaque") caches.open(CACHE).then(c => c.put(e.request, copy));
    return res;
  })));
});
