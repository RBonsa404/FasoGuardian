/*
 * Service worker de l'application Parents : notifications push.
 * Le contenu arrive chiffré pour ce seul navigateur ; il ne porte ni position ni donnée de santé, seulement un
 * titre, une phrase et le chemin de l'application à ouvrir.
 */

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (evenement) => evenement.waitUntil(self.clients.claim()));

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
