import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, dernierCodeSms, parentInscrit } from './aides';

const NOUVEAU = 'harmattan-de-janvier-2027';

/** Profil du parent (US-PAR-002) : récupération d'accès par code SMS, puis clôture du compte avec second facteur. */
test('un parent retrouve son accès par code SMS puis clôt son compte après confirmation', async ({ page, request }) => {
  const { telephone } = await parentInscrit(request);

  // Mot de passe oublié
  await page.goto('/connexion');
  await page.getByRole('link', { name: 'Mot de passe oublié ?' }).click();
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByRole('button', { name: 'Recevoir le code' }).click();
  await expect(page.getByRole('heading', { name: 'Nouveau mot de passe' })).toBeVisible();
  await page.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await page.getByLabel('Nouveau mot de passe').fill(NOUVEAU);
  await page.getByRole('button', { name: 'Changer mon mot de passe' }).click();
  await expect(page).toHaveURL(/connexion/);

  // L'ancien mot de passe ne vaut plus, le nouveau ouvre la session
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByText('Numéro ou mot de passe incorrect.')).toBeVisible();
  await page.getByLabel('Mot de passe').fill(NOUVEAU);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('heading', { name: 'Mon compte' })).toBeVisible();

  // Clôture : action sensible, confirmée par un code SMS propre à cette action
  await page.getByRole('link', { name: 'Paramètres du compte' }).click();
  await page.getByRole('button', { name: 'Clôturer le compte' }).click();
  const feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille).toBeVisible();
  await expect(feuille.getByText('supprimées sous 30 jours')).toBeVisible();
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill('000000');
  await expect(feuille.getByText(/Code incorrect/)).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page).toHaveURL(/connexion/);

  // Un compte clos ne peut plus s'authentifier
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(NOUVEAU);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByText('Numéro ou mot de passe incorrect.')).toBeVisible();
});
