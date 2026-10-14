import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, dernierCodeSms, journeeDeTrajet, parentAvecEnfant } from './aides';

const ECOLE = { latitude: 12.3714, longitude: -1.5197 };
const MAISON = { latitude: 12.3292, longitude: -1.5110 };

/** Historique des trajets (écran 19) : frise des lieux et relecture d'une journée. */
test('le parent relit la journée de son enfant : départ, arrêt, arrivée à l’école, et rejoue le trajet', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = `${SERVEUR}/api/v1/enfants/${enfants[0].id}`;
  expect((await request.post(`${enfant}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  expect((await request.post(`${SERVEUR}/api/v1/moi/second-facteur`, { headers: entetes, data: { action: 'MODIFIER_SAFE_ZONE' } })).status()).toBe(202);
  const code = await dernierCodeSms(request, telephone);
  expect((await request.post(`${enfant}/zones`, { headers: entetes, data: {
    forme: 'CERCLE', nom: 'École Les Manguiers', categorie: 'ECOLE', centre: ECOLE, rayonM: 220, sommets: null,
    jours: [1, 2, 3, 4, 5, 6, 7], debut: '00:00', fin: '00:00', toleranceS: 300, codeSecondFacteur: code,
  } })).status()).toBe(201);
  journeeDeTrajet(carte.numeroSerie, MAISON, ECOLE);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Alertes', exact: true })).toBeVisible();
  await page.goto(`/enfants/${enfants[0].id}/trajets`);

  await expect(page.getByRole('heading', { name: 'Trajets de Yacouba' })).toBeVisible();
  const frise = page.getByRole('list', { name: 'Lieux de la journée' });
  await expect(frise.getByRole('listitem')).toHaveCount(4);
  await expect(frise.getByRole('listitem').nth(0)).toContainText('Départ');
  await expect(frise.getByRole('listitem').nth(1)).toContainText('Arrêt');
  await expect(frise.getByRole('listitem').nth(2)).toContainText('École Les ManguiersEntrée dans la zone');
  await expect(frise.getByRole('listitem').nth(3)).toContainText('École Les Manguiers (dans la zone)');
  await expect(page.getByText('Offre Intermédiaire : 30 jours conservés, puis effacés.')).toBeVisible();
  await page.waitForTimeout(800);
  await page.screenshot({ path: 'e2e/.etat/ecran-19-trajets.png' });

  // Relecture : le curseur part du début et avance seul jusqu'au dernier point.
  const curseur = page.getByRole('slider', { name: 'Moment du trajet' });
  await expect(curseur).toHaveValue('8');
  await page.getByRole('button', { name: 'Rejouer le trajet' }).click();
  await expect(page.getByRole('button', { name: 'Mettre la relecture en pause' })).toBeVisible();
  await expect(curseur).not.toHaveValue('8');
  await expect(curseur).toHaveValue('8', { timeout: 10_000 });
  await expect(page.getByRole('button', { name: 'Rejouer le trajet' })).toBeVisible();
});
