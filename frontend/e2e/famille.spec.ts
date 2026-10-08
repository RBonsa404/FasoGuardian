import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, parentAvecEnfant } from './aides';

/** Module famille (US-PAR-003, 004, 011) : fiche enfant, fiche médicale et contacts d'urgence, côté parent. */
test('un parent complète la fiche de son enfant, sa fiche médicale et ses contacts', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByText('Actif', { exact: true })).toBeVisible();

  // Fiche enfant : créée par l'approbation du KYC, puis complétée
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba Zongo/ }).click();
  await expect(page.getByRole('heading', { name: 'Yacouba Zongo' })).toBeVisible();
  await expect(page.getByText('02/05/2017')).toBeVisible();
  await page.getByRole('button', { name: 'Modifier la fiche' }).click();
  const feuille = page.getByRole('dialog', { name: 'Modifier la fiche' });
  await feuille.getByLabel('École').fill('Les Manguiers · CE2');
  await feuille.getByLabel('Taille (cm)').fill('128');
  await feuille.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Les Manguiers · CE2')).toBeVisible();
  await expect(page.getByText('128 cm')).toBeVisible();
  await expect(page.getByText(/école, quartier ou signes distinctifs/)).toBeVisible();

  // Fiche médicale : un élément critique, un élément privé
  await page.getByRole('link', { name: 'Fiche médicale' }).click();
  await page.locator('select#groupe').selectOption('O+');
  await page.getByRole('button', { name: 'Ajouter une information' }).click();
  await page.getByLabel('Information', { exact: true }).fill('Arachide');
  await page.getByRole('switch', { name: 'Critique · visible sur la page QR' }).click();
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Fiche médicale enregistrée.')).toBeVisible();
  await expect(page.getByText('1 information(s), dont 1 critique(s)')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Information', { exact: true })).toHaveValue('Arachide');
  await expect(page.getByRole('switch', { name: 'Critique · visible sur la page QR' })).toBeChecked();

  // Contacts d'urgence
  await page.getByRole('link', { name: 'Retour' }).click();
  await page.getByRole('link', { name: "Contacts d'urgence" }).click();
  await expect(page.getByText('Aucun contact pour l\'instant.')).toBeVisible();
  await page.getByRole('button', { name: 'Ajouter un contact' }).click();
  const contact = page.getByRole('dialog', { name: 'Ajouter un contact' });
  await contact.getByLabel('Nom', { exact: true }).fill('Fatou Zongo');
  await contact.getByLabel("Lien avec l'enfant").fill('Tante');
  await contact.getByLabel('Numéro mobile').fill('76112233');
  await contact.getByRole('switch', { name: 'Joignable depuis la page QR' }).click();
  await contact.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByRole('button', { name: /Fatou Zongo/ })).toContainText('Tante · +22676112233');
  await expect(page.getByRole('button', { name: /Fatou Zongo/ })).toContainText('Page QR');
});
