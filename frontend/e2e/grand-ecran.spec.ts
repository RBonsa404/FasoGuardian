import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant } from './aides';

/**
 * Espace parent sur un poste de travail : la même application, avec une barre latérale de navigation à la
 * place des liens du tableau de bord, et des écrans qui occupent la largeur disponible.
 */
test('sur grand écran, le parent navigue par la barre latérale ; sur téléphone, rien ne change', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  const navigation = page.getByRole('navigation', { name: 'Navigation principale' });
  await expect(navigation.getByRole('link')).toHaveText(['Tableau de bord', 'Alertes', 'Mes enfants', 'Mon abonnement', 'Aide et support', 'Paramètres du compte']);
  // Les liens du tableau de bord font double emploi avec la barre : un seul lien « Alertes » reste visible.
  await expect(page.getByRole('link', { name: 'Alertes', exact: true })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toHaveCount(1);
  await expect(page.getByRole('heading', { name: /Associez le bracelet de Yacouba/ })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-tableau-de-bord.png' });

  await navigation.getByRole('link', { name: 'Mes enfants' }).click();
  await expect(page.getByRole('heading', { name: 'Mes enfants' })).toBeVisible();
  await expect(navigation.getByRole('link', { name: 'Mes enfants' })).toHaveClass(/text-accent/);
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-enfants.png' });
  // L'écran occupe plus que la colonne d'un téléphone, sans défilement horizontal.
  const largeur = await page.locator('main#contenu h1').first().evaluate((titre) => (titre.closest('[class*="max-w"]') as HTMLElement).clientWidth);
  expect(largeur).toBeGreaterThan(500);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await navigation.getByRole('link', { name: 'Mon abonnement' }).click();
  await expect(page.getByRole('heading', { name: /abonnement/i }).first()).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-abonnement.png' });
  await navigation.getByRole('link', { name: 'Alertes' }).click();
  await expect(page.getByRole('heading', { name: 'Alertes', exact: true })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-alertes.png' });

  // Téléphone : la barre disparaît, les liens du tableau de bord reviennent.
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('/');
  await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Mes enfants' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Se déconnecter' })).toHaveCount(1);

  // La déconnexion depuis la barre latérale ramène à la connexion.
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.getByRole('button', { name: 'Se déconnecter' }).click();
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
});

test('sur grand écran, la carte occupe la fenêtre : tableau de bord, position, tracé d’une zone, trajets', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  braceletEmet(carte.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 1, seq: 1, lat: 12.3714, lon: -1.5197, acc: 8, src: 'gnss', bat: 82, rssi: -71, net: '4g' });

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Tableau de bord : la carte à gauche, plus large que la colonne des mesures et des raccourcis
  await expect(page.getByText('Précise ± 8 m')).toBeVisible();
  const cadreCarte = await page.getByRole('application').boundingBox();
  const raccourcis = await page.getByRole('navigation', { name: 'Raccourcis pour Yacouba' }).boundingBox();
  expect(cadreCarte!.width).toBeGreaterThan(600);
  expect(raccourcis!.x).toBeGreaterThan(cadreCarte!.x + cadreCarte!.width);
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-tableau-avec-carte.png' });

  // Carte de l'enfant : plein cadre à droite de la barre latérale
  await page.getByRole('link', { name: 'Agrandir' }).click();
  await expect(page.getByText('Précise ± 8 m')).toBeVisible();
  expect((await page.getByRole('application').boundingBox())!.width).toBeGreaterThan(1100);
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-carte.png' });

  // Nouvelle zone : le formulaire à gauche, la carte où tracer sur le reste de la fenêtre
  await page.goto(`/enfants/${enfants[0].id}/zones`);
  await page.getByRole('button', { name: 'Ajouter une zone' }).click();
  await expect(page.getByRole('heading', { name: 'Nouvelle zone' })).toBeVisible();
  const trace = await page.getByRole('application').boundingBox();
  const formulaire = await page.locator('form').boundingBox();
  expect(trace!.width).toBeGreaterThan(700);
  expect(trace!.height).toBeGreaterThan(800);
  expect(formulaire!.x + formulaire!.width).toBeLessThanOrEqual(trace!.x + 1);
  await page.getByRole('application').click({ position: { x: 380, y: 400 } });
  await expect(page.getByText('Touchez la carte pour déplacer le centre.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-edition-zone.png' });

  // Trajets : les jours et le bilan à gauche, la carte du trajet à droite
  await page.goto(`/enfants/${enfants[0].id}/trajets`);
  await expect(page.getByRole('heading', { name: 'Trajets' })).toBeVisible();
  await expect(page.getByText('1 position(s) enregistrée(s) ce jour-là.')).toBeVisible();
  const trajet = await page.getByRole('application').boundingBox();
  expect(trajet!.width).toBeGreaterThan(700);
  expect(trajet!.height).toBeGreaterThan(800);
  await page.screenshot({ path: 'e2e/.etat/grand-ecran-trajets.png' });
});
