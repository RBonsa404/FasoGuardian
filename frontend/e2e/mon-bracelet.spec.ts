import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant } from './aides';

/** Mon bracelet (écran 37) : autonomie estimée, logiciel à jour, aperçu 3D chargé à la demande, remplacement. */
test('le parent voit l’état de son bracelet, l’ouvre en 3D et demande un remplacement', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  braceletEmet(carte.numeroSerie, 'telemetry', { t: Math.floor(Date.now() / 1000) + 1, seq: 1, lat: 12.3714, lon: -1.5197, acc: 8, src: 'gnss', bat: 50, net: '4g' });

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Alertes', exact: true })).toBeVisible();
  await page.goto(`/enfants/${enfants[0].id}/bracelet`);

  // 50 % de batterie à une position toutes les 5 minutes en 4G : la moitié de 104 h ; en économie, la moitié de 175 h (FG-DOC-08 tableau 9).
  await expect(page.getByText('≈ 2 j restantes')).toBeVisible();
  // Une campagne lancée par un autre essai peut viser une version plus récente : le bracelet le dit alors.
  await expect(page.getByText(/^(à jour|mise à jour disponible)$/)).toBeVisible();
  await expect(page.getByText('+36 h en mode économie', { exact: false })).toBeVisible();

  // three.js n'est chargé qu'au clic.
  expect(await page.evaluate(() => performance.getEntriesByType('resource').some((r) => /three/.test(r.name)))).toBe(false);
  await page.getByRole('button', { name: 'Aperçu 3D · chargé à la demande' }).click();
  await expect(page.getByRole('img', { name: /en trois dimensions/ })).not.toHaveClass(/opacity-0/, { timeout: 15_000 });
  // La scène occupe tout le bandeau : une hauteur nulle la laisserait invisible.
  expect((await page.getByRole('img', { name: /en trois dimensions/ }).boundingBox())!.height).toBeGreaterThan(200);
  await page.waitForTimeout(800);
  await page.screenshot({ path: 'e2e/.etat/ecran-37-mon-bracelet.png' });

  await page.getByRole('link', { name: 'Demander un remplacement' }).click();
  const feuille = page.getByRole('dialog', { name: 'Écrire au support' });
  await expect(feuille.getByLabel('Objet')).toHaveValue(`Remplacement du bracelet ${carte.numeroSerie}`);
});
