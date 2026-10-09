import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, codeTotp, creerAgent, parentAvecEnfant } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';
const FOND_CLAIR = 'rgb(245, 247, 251)';
const FOND_SOMBRE = 'rgb(10, 14, 26)';

test.use({ colorScheme: 'light' });

const fond = (page: import('@playwright/test').Page) => page.evaluate(() => getComputedStyle(document.documentElement).backgroundColor);

/** Thème clair : il suit le système, se choisit dans les paramètres et reste appliqué après rechargement. */
test('l’espace parent suit l’apparence du système, puis le choix de la personne', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  braceletEmet(carte.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 1, seq: 1, lat: 12.3714, lon: -1.5197, acc: 8, src: 'gnss', bat: 82, rssi: -71, net: '4g' });

  // Système en clair : la connexion s'affiche en clair, avec le logo fait pour un fond clair
  await page.goto('/connexion');
  expect(await fond(page)).toBe(FOND_CLAIR);
  await expect(page.locator('img[src="logo-clair.svg"]')).toBeVisible();
  await expect(page.locator('img[src="logo-sombre.svg"]')).toBeHidden();
  await page.screenshot({ path: 'e2e/.etat/clair-12-connexion.png' });
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByText('Précise ± 8 m')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-16-tableau-de-bord.png', fullPage: true });
  await page.getByRole('link', { name: 'Mon abonnement' }).click();
  await expect(page.getByRole('heading', { name: 'Mon abonnement' })).toBeVisible();
  await expect(page.getByText(/FCFA/).first()).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-41-abonnement.png', fullPage: true });
  await page.goto(`/enfants/${enfants[0].id}`);
  await expect(page.getByRole('link', { name: 'Fiche médicale' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-31-fiche-enfant.png', fullPage: true });

  // Paramètres : le choix de la personne l'emporte sur le système et survit au rechargement
  await page.goto('/reglages');
  const apparence = page.getByRole('group', { name: 'Apparence' });
  await expect(apparence.getByRole('button', { name: 'Système' })).toHaveAttribute('aria-pressed', 'true');
  await page.screenshot({ path: 'e2e/.etat/clair-48-parametres.png', fullPage: true });
  await apparence.getByRole('button', { name: 'Sombre' }).click();
  expect(await fond(page)).toBe(FOND_SOMBRE);
  await page.reload();
  expect(await fond(page)).toBe(FOND_SOMBRE);
  await expect(page.getByRole('group', { name: 'Apparence' }).getByRole('button', { name: 'Sombre' })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('group', { name: 'Apparence' }).getByRole('button', { name: 'Système' }).click();
  expect(await fond(page)).toBe(FOND_CLAIR);
});

test('la console se lit en clair', async ({ page, request }) => {
  const agent = await creerAgent(request, ['ADMIN', 'SAV', 'KYC', 'SUPPORT']);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  expect(await fond(page)).toBe(FOND_CLAIR);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  const navigation = page.getByRole('navigation', { name: 'Navigation principale' });
  await expect(navigation.getByRole('link', { name: 'Tableau de bord' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-57-tableau.png' });
  await navigation.getByRole('link', { name: 'Parc de bracelets' }).click();
  await expect(page.getByRole('heading', { name: 'Parc de bracelets' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-64-parc.png' });
  await navigation.getByRole('link', { name: 'Supervision' }).click();
  await expect(page.getByRole('heading', { name: 'Supervision' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/clair-72-supervision.png' });

  await page.getByRole('group', { name: 'Apparence' }).getByRole('button', { name: 'Sombre' }).click();
  expect(await fond(page)).toBe(FOND_SOMBRE);
});
