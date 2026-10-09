import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, parentAvecEnfant } from './aides';

/** Écran 9 : à la première ouverture sur un appareil, l'introduction passe avant la connexion. */
test('la première visite montre l’introduction, les suivantes mènent droit à la connexion', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/bienvenue$/);
  await expect(page.getByRole('heading', { name: 'Sachez où est votre enfant, sans le surveiller' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-09-bienvenue.png' });
  await page.getByRole('button', { name: 'Suivant' }).click();
  await expect(page.getByRole('heading', { name: 'Un SOS au poignet' })).toBeVisible();
  await page.getByRole('button', { name: 'Suivant' }).click();
  await expect(page.getByRole('heading', { name: "Un QR pour qu'on vous prévienne" })).toBeVisible();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page.getByRole('heading', { name: 'Votre numéro de téléphone' })).toBeVisible();

  await page.goto('/');
  await expect(page).toHaveURL(/\/connexion$/);
});

/** Écrans 33 et 40 : aperçu de la page QR composé des réglages de la famille, et suivi de maintenance. */
test('un parent voit ce que montre la page QR de son enfant, et l’état de maintenance du bracelet', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = `${SERVEUR}/api/v1/enfants/${enfants[0].id}`;
  expect((await request.post(`${enfant}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  expect((await request.post(`${enfant}/contacts`, { headers: entetes, data: { lien: 'Tante', nom: 'Fatou Sawadogo', telephone: '76112233', visibleSurQr: true } })).status()).toBe(201);
  expect((await request.post(`${enfant}/contacts`, { headers: entetes, data: { lien: 'Voisin', nom: 'Paul Zida', telephone: '76112244', visibleSurQr: false } })).status()).toBe(201);
  expect(
    (
      await request.put(`${enfant}/sante`, {
        headers: entetes,
        data: { groupeSanguin: 'O+', groupeSanguinSurQr: true, elements: [{ type: 'ALLERGIE', libelle: 'Arachides', critique: true }, { type: 'TRAITEMENT', libelle: 'Suivi orthophonique', critique: false }] },
      })
    ).status(),
  ).toBe(200);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba Zongo/ }).click();
  await page.getByRole('link', { name: 'Aperçu de la page QR' }).click();

  // Écran 33 : ni nom ni numéro ; seulement ce que la famille a marqué
  await expect(page.getByRole('heading', { name: 'Ce que voit un tiers' })).toBeVisible();
  const apercu = page.getByRole('region', { name: 'Aperçu de la page publique' });
  await expect(apercu.getByText(carte.numeroSerie)).toBeVisible();
  await expect(apercu.getByText('Appeler · Tante')).toBeVisible();
  await expect(apercu.getByText('Arachides')).toBeVisible();
  await expect(apercu.getByText('O+')).toBeVisible();
  await expect(apercu.getByText('Voisin')).toHaveCount(0);
  await expect(apercu.getByText('Suivi orthophonique')).toHaveCount(0);
  await expect(apercu.getByText('Yacouba')).toHaveCount(0);
  await expect(apercu.getByText('76112233')).toHaveCount(0);
  await page.screenshot({ path: 'e2e/.etat/ecran-33-apercu-qr.png', fullPage: true });

  // L'aperçu dit vrai : la page publique montre la même chose
  const publique = await (await request.get(`${SERVEUR}/q/${carte.jetonQr}`)).text();
  expect(publique).toContain('Appeler · Tante');
  expect(publique).toContain('Arachides');
  expect(publique).not.toContain('Voisin');
  expect(publique).not.toContain('Suivi orthophonique');

  // Écran 40 : le bracelet répond, aucun ticket n'est ouvert
  await page.goto(`/enfants/${enfants[0].id}/bracelet/maintenance`);
  await expect(page.getByRole('heading', { name: 'Aucun incident en cours' })).toBeVisible();
  await expect(page.getByText('Le bracelet répond normalement')).toBeVisible();
});
