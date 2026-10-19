/*
 * Service worker de l'application Parents : ouverture hors ligne et notifications push.
 *
 * Hors ligne : seuls les fichiers de l'application (page, scripts, styles, polices, images) et les tuiles de la
 * carte sont mis en cache, jamais une réponse de l'API. Les données d'un enfant ne passent donc pas par ce cache ;
 * ce qui reste lisible sans réseau est la copie locale tenue par l'application elle-même.
 *
 * Tuiles : cache borné à environ 300 tuiles (FG-DOC-06, tableau 8), pour limiter la consommation de données ;
 * les plus anciennes sortent les premières.
 *
 * Push : le contenu arrive chiffré pour ce seul navigateur ; il ne porte ni position ni donnée de santé,
 * seulement un titre, une phrase et le chemin de l'application à ouvrir.
 */

const CACHE = 'fasoguardian-parents-v1';
const CACHE_TUILES = 'fasoguardian-tuiles-v1';
const TUILES_MAXIMUM = 300;
/** Adresse d'une tuile de fond de carte : …/{z}/{x}/{y}.png. */
const TUILE = /\/\d+\/\d+\/\d+\.png$/;

self.addEventListener('install', (evenement) => {
  evenement.waitUntil(garder().catch(() => undefined));
  self.skipWaiting();
});

/**
 * Garde les fichiers de l'application listés à la construction (precache.json), puis écarte ceux d'une version
 * précédente. Sans liste (serveur de développement), seule la page d'entrée est gardée.
 */
async function garder() {
  const cache = await caches.open(CACHE);
  let fichiers = ['/index.html'];
  try {
    const liste = await fetch('/precache.json', { cache: 'no-store' });
    if (liste.ok) {
      fichiers = await liste.json();
    }
  } catch {
    // Pas de liste : on s'en tient à la page d'entrée.
  }
  await cache.addAll(fichiers);
  const gardes = new Set(fichiers.map((fichier) => new URL(fichier, self.location.origin).href));
  for (const requete of await cache.keys()) {
    if (!gardes.has(requete.url)) {
      await cache.delete(requete);
    }
  }
}

self.addEventListener('activate', (evenement) => {
  evenement.waitUntil(
    caches
      .keys()
      .then((noms) => Promise.all(noms.filter((nom) => nom !== CACHE && nom !== CACHE_TUILES).map((nom) => caches.delete(nom))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evenement) => {
  const requete = evenement.request;
  const adresse = new URL(requete.url);
  if (requete.method === 'GET' && adresse.origin !== self.location.origin && TUILE.test(adresse.pathname)) {
    evenement.respondWith(tuile(requete));
    return;
  }
  if (requete.method !== 'GET' || adresse.origin !== self.location.origin || adresse.pathname.startsWith('/api/') || adresse.pathname === '/sw.js') {
    return;
  }
  if (requete.mode === 'navigate') {
    // Réseau d'abord, pour toujours servir la dernière version ; sans réseau, la page gardée.
    evenement.respondWith(
      fetch(requete)
        .then((reponse) => {
          const copie = reponse.clone();
          void caches.open(CACHE).then((cache) => cache.put('/index.html', copie));
          return reponse;
        })
        .catch(() => caches.match('/index.html').then((gardee) => gardee || Response.error())),
    );
    return;
  }
  // Fichiers de l'application : leur nom change avec leur contenu, la copie gardée est donc toujours la bonne.
  evenement.respondWith(
    caches.match(requete).then(
      (gardee) =>
        gardee ||
        fetch(requete).then((reponse) => {
          if (reponse.ok) {
            const copie = reponse.clone();
            void caches.open(CACHE).then((cache) => cache.put(requete, copie));
          }
          return reponse;
        }),
    ),
  );
});

/** Tuile gardée si elle l'est ; sinon le réseau, puis la tuile rejoint le cache, dont les plus anciennes sortent. */
async function tuile(requete) {
  const cache = await caches.open(CACHE_TUILES);
  const gardee = await cache.match(requete);
  if (gardee) {
    return gardee;
  }
  const reponse = await fetch(requete);
  if (reponse.ok || reponse.type === 'opaque') {
    await cache.put(requete, reponse.clone());
    const cles = await cache.keys();
    await Promise.all(cles.slice(0, Math.max(0, cles.length - TUILES_MAXIMUM)).map((cle) => cache.delete(cle)));
  }
  return reponse;
}

self.addEventListener('push', (evenement) => {
  let message = {};
  try {
    message = evenement.data ? evenement.data.json() : {};
  } catch {
    // Contenu illisible : une notification générique vaut mieux que le silence.
  }
  const affichage = self.registration.showNotification(message.titre || 'FasoGuardian', {
    body: message.texte || "Ouvrez l'application.",
    tag: message.id,
    icon: '/icone-192.png',
    badge: '/icone-192.png',
    data: { lien: message.lien || '/' },
    // Une alerte critique reste à l'écran jusqu'à ce que le parent y touche.
    requireInteraction: message.critique === true,
  });
  // Accusé de livraison : sans lui, le serveur double la notification par SMS au bout de 60 secondes.
  const accuse = message.id
    ? fetch(`/api/v1/public/notifications/${encodeURIComponent(message.id)}/accuse`, { method: 'POST', keepalive: true }).catch(() => undefined)
    : Promise.resolve();
  evenement.waitUntil(Promise.all([affichage, accuse]));
});

self.addEventListener('notificationclick', (evenement) => {
  evenement.notification.close();
  // Seuls les chemins de l'application sont suivis, jamais une adresse externe.
  const lien = typeof evenement.notification.data?.lien === 'string' && evenement.notification.data.lien.startsWith('/') ? evenement.notification.data.lien : '/';
  evenement.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((fenetres) => {
      const ouverte = fenetres.find((fenetre) => 'focus' in fenetre);
      if (ouverte) {
        return ouverte.navigate(lien).then((fenetre) => (fenetre || ouverte).focus());
      }
      return self.clients.openWindow(lien);
    }),
  );
});
