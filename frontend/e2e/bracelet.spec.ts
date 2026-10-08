import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, dernierCodeSms, parentAvecEnfant } from './aides';

/** Module dispositifs (US-PAR-013, US-PAR-014) : appairage, mode économie, perte et désappairage, côté parent. */
test('un parent associe un bracelet, le déclare perdu, le retrouve puis le désappaire', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  // Tableau de bord sans bracelet : invitation à l'appairage
  await expect(page.getByRole('heading', { name: 'Associez le bracelet de Yacouba' })).toBeVisible();
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba Zongo/ }).click();

  // Aucun bracelet : l'écran propose l'appairage
  await page.getByRole('link', { name: 'Bracelet' }).click();
  await expect(page.getByText("Aucun bracelet n'est associé à cet enfant.")).toBeVisible();
  await page.getByRole('button', { name: 'Associer un bracelet' }).click();
  await expect(page.getByRole('heading', { name: 'Associer le bracelet' })).toBeVisible();

  // Un code inconnu est refusé, le code de la carte d'activation est accepté
  await page.getByLabel("Code d'appairage").fill('ZZZZ222');
  await expect(page.getByRole('alert')).toContainText("Ce code n'est pas reconnu");
  await page.getByLabel("Code d'appairage").fill(carte.codeAppairage.toLowerCase());
  await expect(page.getByText(`${carte.numeroSerie} associé à Yacouba`)).toBeVisible();

  // La page publique du QR gravé répond désormais pour cet enfant
  const pageQr = async () => (await request.get(`${SERVEUR}/q/${carte.jetonQr}`)).text();
  expect(await pageQr()).toContain('Vous avez trouvé un enfant');

  // Mon bracelet : état et mode économie
  await page.getByRole('button', { name: 'Voir le bracelet' }).click();
  await expect(page.getByText(carte.numeroSerie)).toBeVisible();
  await expect(page.getByText('en attente du premier contact')).toHaveCount(3);
  await expect(page.getByText('Position toutes les 5 min')).toBeVisible();
  await page.getByRole('switch', { name: 'Mode économie' }).click();
  await expect(page.getByText('Position toutes les 15 min')).toBeVisible();
  await page.reload();
  await expect(page.getByRole('switch', { name: 'Mode économie' })).toBeChecked();

  // Déclaration de perte, confirmée par code SMS
  await page.getByRole('link', { name: 'Déclarer perdu ou volé' }).click();
  await expect(page.getByRole('heading', { name: `Déclarer ${carte.numeroSerie} perdu ou volé` })).toBeVisible();
  await page.getByText('Volé', { exact: true }).click();
  await expect(page.getByText('il est bloqué aussitôt')).toBeVisible();
  await page.getByText('Perdu', { exact: true }).click();
  await expect(page.getByText('Le suivi continue 72 h')).toBeVisible();
  await page.getByRole('button', { name: 'Déclarer · code SMS' }).click();
  const feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page.getByText(/Déclaré perdu\. La page QR est désactivée/)).toBeVisible();
  expect(await pageQr()).toContain("Ce bracelet n'est plus actif");

  // Retrouvé pendant le suivi
  await page.getByRole('button', { name: "Je l'ai retrouvé" }).click();
  await expect(page.getByText(/Déclaré perdu/)).toHaveCount(0);
  expect(await pageQr()).toContain('Vous avez trouvé un enfant');

  // Désappairage sans déclaration, après confirmation
  await page.getByRole('link', { name: 'Désappairer' }).click();
  const confirmation = page.getByRole('dialog', { name: 'Désappairer ce bracelet ?' });
  await confirmation.getByRole('button', { name: 'Désappairer' }).click();
  await expect(page.getByText("Aucun bracelet n'est associé à cet enfant.")).toBeVisible();
  expect(await pageQr()).toContain("Ce code n'est pas reconnu");
});
