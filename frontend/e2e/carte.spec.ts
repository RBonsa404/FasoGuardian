import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant } from './aides';

/**
 * Carte plein écran (écran 18) : bascule « Mode alerte · 60 s », suivi rapproché limité à trente minutes,
 * arrêt par le tuteur ; recentrage sur l'enfant.
 */
test('depuis la carte, le parent passe le bracelet en mode alerte pour trente minutes, puis l’arrête', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  braceletEmet(carte.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 1, seq: 1, lat: 12.3714, lon: -1.5197, acc: 850, src: 'cell', bat: 52, net: '2g' });

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Alertes', exact: true })).toBeVisible();
  await page.goto(`/enfants/${enfants[0].id}/carte`);

  await expect(page.getByRole('heading', { name: 'Position de Yacouba' })).toBeVisible();
  await expect(page.getByText('Approx. ± 850 m')).toBeVisible();
  const bascule = page.getByRole('switch', { name: /Mode alerte/ });
  await expect(bascule).toHaveAttribute('aria-checked', 'false');
  await page.screenshot({ path: 'e2e/.etat/ecran-18-carte-approximative.png' });

  await bascule.click();
  await expect(bascule).toHaveAttribute('aria-checked', 'true');
  await expect(page.getByText(/jusqu'à \d{2}:\d{2}/)).toBeVisible();
  await expect(page.getByText('Une position par minute pendant trente minutes.', { exact: false })).toBeVisible();
  const etat = await (await request.get(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/mode-alerte`, { headers: entetes })).json();
  expect(etat.actif).toBe(true);

  await bascule.click();
  await expect(bascule).toHaveAttribute('aria-checked', 'false');
  await page.getByRole('button', { name: "Recentrer sur l'enfant" }).click();
  await expect(page.getByRole('button', { name: 'Localiser maintenant' })).toBeVisible();
});
