import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant } from './aides';

/**
 * Partage temporaire de la position (US-SEC-001) : le parent envoie un lien à un contact d'urgence ; le
 * contact, sans compte, voit la position ; la révocation lui retire l'accès aussitôt.
 */
test('un parent partage la position avec un contact, qui la voit par son lien jusqu’à la révocation', async ({ page, browser, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = enfants[0].id as string;
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfant}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  const numeroDuContact = `76${String(Date.now()).slice(-6)}`;
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfant}/contacts`, { headers: entetes, data: { lien: 'Oncle', nom: 'Issouf Ouédraogo', telephone: numeroDuContact, visibleSurQr: false } })).status()).toBe(201);
  // Une mesure datée d'avant l'appairage serait écartée : la seconde entamée est arrondie vers le haut.
  braceletEmet(carte.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 1, seq: 1, lat: 12.3714, lon: -1.5197, acc: 20, src: 'gnss', bat: 80 });

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('navigation', { name: 'Raccourcis pour Yacouba' }).getByRole('link', { name: 'Partager' }).click();

  // Écran 35 : choix du contact et de la durée
  await expect(page.getByRole('heading', { name: 'Partager la position' })).toBeVisible();
  await expect(page.getByText('Oncle · Issouf Ouédraogo')).toBeVisible();
  await page.getByText('2 h', { exact: true }).click();
  await page.getByRole('button', { name: 'Envoyer le lien par SMS' }).click();
  await expect(page.getByRole('heading', { name: 'Partage en cours' })).toBeVisible();
  await expect(page.getByText(/Oncle voit la position de Yacouba jusqu'à \d\d:\d\d\./)).toBeVisible();
  await expect(page.getByRole('timer')).toHaveText(/1:59:\d\d\s*restant/);
  await expect(page.getByText('SMS · lien unique')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-35-partage.png', fullPage: true });

  // Le contact ouvre le lien reçu par SMS, sans session
  const sms = await (await request.get(`${SERVEUR}/api/v1/dev/sms`)).json();
  const texte = sms.filter((message: { destinataire: string }) => message.destinataire === `+226${numeroDuContact}`).at(-1).texte as string;
  const lien = /\/p\/([A-Za-z0-9_-]{22})/.exec(texte)![1];
  const contexteDuContact = await browser.newContext({ viewport: { width: 360, height: 800 } });
  const contact = await contexteDuContact.newPage();
  await contact.goto(`/p/${lien}`);
  await expect(contact.getByRole('heading', { name: /Position partagée par / })).toBeVisible();
  await expect(contact.getByText('Lien personnel · ne le transférez pas')).toBeVisible();
  await expect(contact.getByText('Position précise ± 20 m')).toBeVisible();
  await expect(contact.getByRole('application', { name: 'Carte de la position partagée' })).toBeVisible();
  // Rien sur l'enfant : ni son prénom, ni un lien vers le reste de l'application.
  await expect(contact.getByText('Yacouba')).toHaveCount(0);
  await expect(contact.getByRole('link')).toHaveCount(0);
  await contact.screenshot({ path: 'e2e/.etat/ecran-50-vue-contact.png', fullPage: true });

  // Le parent voit que le lien a été ouvert, puis révoque
  await page.reload();
  await expect(page.getByText(/1 fois · dernier \d\d:\d\d/)).toBeVisible();
  await page.getByRole('button', { name: 'Révoquer maintenant' }).click();
  await expect(page.getByRole('heading', { name: 'Partager la position' })).toBeVisible();

  await contact.reload();
  await expect(contact.getByRole('heading', { name: 'Ce partage est terminé.' })).toBeVisible();
  await expect(contact.getByRole('application')).toHaveCount(0);
  await contexteDuContact.close();
});
