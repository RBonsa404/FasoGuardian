import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, PNG, parentAvecEnfant, onglets, ongletReglages } from './aides';

/** Module famille (US-PAR-003, 004, 011) : fiche enfant, fiche médicale et contacts d'urgence, côté parent. */
test('un parent complète la fiche de son enfant, sa fiche médicale et ses contacts', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  // Compte actif : la barre d'onglets propose la carte et les alertes.
  await expect(onglets(page).getByRole('link', { name: 'Carte' })).toBeVisible();

  // Fiche enfant : créée par l'approbation du KYC, puis complétée
  await ongletReglages(page);
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba · \d+ ans/ }).click();
  await expect(page.getByRole('heading', { name: 'Yacouba Zongo' })).toBeVisible();
  await expect(page.getByText('02/05/2017')).toBeVisible();
  await page.getByRole('button', { name: 'Modifier la fiche' }).click();
  const feuille = page.getByRole('dialog', { name: 'Modifier la fiche' });
  await feuille.getByLabel('École').fill('Les Manguiers · CE2');
  await feuille.getByLabel('Taille (cm)').fill('128');
  await feuille.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Les Manguiers · CE2')).toBeVisible();
  await expect(page.getByText('1,28 m')).toBeVisible();
  await expect(page.getByText(/· école modifiée/)).toBeVisible();
  await expect(page.getByText(/· taille modifiée/)).toBeVisible();
  // Photo : choisie dans la feuille de modification, affichée sur la fiche, retirable
  await page.getByRole('button', { name: 'Modifier la fiche' }).click();
  await feuille.locator('input[type=file]').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: PNG });
  await expect(page.getByRole('img', { name: 'Photo de Yacouba' })).toBeVisible();
  await expect(page.getByText(/· photo modifiée/)).toBeVisible();
  await page.keyboard.press('Escape');
  await page.screenshot({ path: 'e2e/.etat/ecran-31-fiche-enfant.png', fullPage: true });

  // Fiche médicale : un élément critique, un élément privé
  await page.getByRole('link', { name: 'Fiche médicale' }).click();
  await expect(page.getByText("Aucune information médicale pour l'instant.")).toBeVisible();
  await page.getByRole('button', { name: 'Modifier les informations' }).click();
  await page.locator('select#groupe').selectOption('O+');
  await page.getByRole('button', { name: 'Ajouter une information' }).click();
  await page.getByLabel('Information', { exact: true }).fill('Arachide');
  await page.getByRole('switch', { name: 'Critique · visible sur la page QR' }).click();
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Fiche médicale enregistrée.')).toBeVisible();
  const arachide = page.getByRole('switch', { name: 'Arachide · critique, visible sur la page QR' });
  await expect(arachide).toBeChecked();
  await expect(page.getByText(/Dernière révision : .+ par \S+/)).toBeVisible();
  // En lecture, la bascule s'enregistre aussitôt : l'information devient privée
  await arachide.click();
  await page.getByRole('button', { name: 'journal des révisions' }).click();
  await expect(page.getByText('1 information(s), dont 0 critique(s)')).toBeVisible();
  await expect(page.getByText('1 information(s), dont 1 critique(s)')).toBeVisible();
  await arachide.click();
  await expect(page.getByText('1 information(s), dont 1 critique(s)')).toHaveCount(2);
  await page.reload();
  await expect(page.getByRole('switch', { name: 'Arachide · critique, visible sur la page QR' })).toBeChecked();
  await page.screenshot({ path: 'e2e/.etat/ecran-32-fiche-medicale.png', fullPage: true });

  // Contacts d'urgence
  await page.getByRole('link', { name: 'Retour' }).click();
  await page.getByRole('link', { name: 'Tuteurs et contacts' }).click();
  await expect(page.getByText('Aucun contact pour l\'instant.')).toBeVisible();
  await page.getByRole('button', { name: 'Ajouter un contact' }).click();
  const contact = page.getByRole('dialog', { name: 'Ajouter un contact' });
  await contact.getByLabel('Nom', { exact: true }).fill('Fatou Zongo');
  await contact.getByLabel("Lien avec l'enfant").fill('Tante');
  await contact.getByLabel('Numéro mobile').fill('76112233');
  await contact.getByRole('switch', { name: 'Joignable depuis la page QR' }).click();
  await contact.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByRole('button', { name: /Fatou Zongo/ })).toContainText('Tante · +226 76 11 22 33');
  await expect(page.getByRole('button', { name: /Fatou Zongo/ })).toContainText('Page QR');
});
