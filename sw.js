// Valiu service worker: the app opens even without internet.
const CACHE = 'valiu-72c706a43b';
const ASSETS = ['./', './index.html', './config.js', './manifest.webmanifest', './vendor/chart.umd.js', './vendor/supabase.js',
                './icons/icon-192.png', './icons/icon-512.png', './icons/apple-touch-icon.png', './privacidad.html'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => {
    e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
    const req = e.request, url = new URL(req.url);
    if (req.method !== 'GET' || url.origin !== self.location.origin) return; // Supabase and fonts go straight to the network
    if (req.mode === 'navigate') {
        e.respondWith(fetch(req).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put('./index.html', copy)); return r; })
            .catch(() => caches.match('./index.html')));
        return;
    }
    e.respondWith(caches.match(req).then(hit => {
        const net = fetch(req).then(r => { if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)); } return r; }).catch(() => hit);
        return hit || net;
    }));
});

// Payment reminders sent by the "recordatorios" Edge Function
self.addEventListener('push', e => {
    let p = {};
    try { p = e.data ? e.data.json() : {}; } catch (err) { p = { body: e.data ? e.data.text() : '' }; }
    e.waitUntil(self.registration.showNotification(p.title || 'Valiu', {
        body: p.body || '', tag: p.tag || 'valiu', renotify: true,
        icon: './icons/icon-192.png', badge: './icons/icon-192.png', data: { url: p.url || './' }
    }));
});
self.addEventListener('notificationclick', e => {
    e.notification.close();
    const target = new URL((e.notification.data && e.notification.data.url) || './', self.registration.scope).href;
    e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
        for (const c of list) { if (c.url.startsWith(self.registration.scope) && 'focus' in c) return c.focus(); }
        return self.clients.openWindow(target);
    }));
});
