import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, parentInscrit } from './aides';

/**
 * Notifications push côté navigateur (écran 15, US-ENF-001). Le message est remis au service worker par le
 * protocole de débogage de Chromium, comme le ferait le service de push : on vérifie qu'il le traite et accuse
 * réception auprès du serveur.
 */
test('le service worker reçoit une notification poussée et en accuse réception auprès du serveur', async ({ page, context, request }) => {
  const { telephone } = await parentInscrit(request);
  await context.grantPermissions(['notifications']);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('link', { name: 'Paramètres du compte' }).click();
  await page.getByRole('link', { name: 'Notifications et installation' }).click();

  await expect(page.getByRole('heading', { name: 'Gardez FasoGuardian à portée de main' })).toBeVisible();
  await expect(page.getByRole('switch', { name: "Notifications d'alerte" })).toBeVisible();
  await expect(page.getByText('Indispensables pour le SOS. Doublées par SMS.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-15-installer.png', fullPage: true });

  // Le service worker est celui que l'application enregistre à l'activation des notifications.
  const debogage = await context.newCDPSession(page);
  const enregistrements: { registrationId: string; scopeURL: string }[] = [];
  debogage.on('ServiceWorker.workerRegistrationUpdated', (evenement) => enregistrements.push(...evenement.registrations));
  await debogage.send('ServiceWorker.enable');
  await page.evaluate(async () => {
    await navigator.serviceWorker.register('/sw.js');
    await navigator.serviceWorker.ready;
  });
  await expect.poll(() => enregistrements.length).toBeGreaterThan(0);

  const accuses: string[] = [];
  context.on('request', (requete) => {
    if (requete.url().includes('/api/v1/public/notifications/')) {
      accuses.push(`${requete.method()} ${new URL(requete.url()).pathname}`);
    }
  });
  const id = '3f0c2b9e-4f55-4c0e-9d3a-0e8a1b2c3d4e';
  await debogage.send('ServiceWorker.deliverPushMessage', {
    origin: new URL(page.url()).origin,
    registrationId: enregistrements[0].registrationId,
    data: JSON.stringify({ id, titre: 'Alerte SOS', texte: 'ALERTE SOS. Le bouton SOS du bracelet de votre enfant a été déclenché.', lien: '/alertes/abc', critique: true }),
  });

  // Le navigateur sans affichage des essais refuse toujours l'autorisation de notifier : l'affichage lui-même se
  // vérifie à la main sur un téléphone (docs/essais). Ici, la preuve que le service worker a traité le message
  // est l'accusé de livraison qu'il envoie au serveur, et qui annule le repli SMS.
  await expect.poll(() => accuses).toEqual([`POST /api/v1/public/notifications/${id}/accuse`]);
});
