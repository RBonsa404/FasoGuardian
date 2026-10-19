import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, parentAvecEnfant, ongletReglages, sqlPile } from './aides';

/**
 * Module abonnements (US-PAR-015) : choix d'une offre, paiement mobile money, reçu. L'opérateur est celui du
 * bac à sable : il répond de lui-même au bout de trois secondes, et refuse un portefeuille dont le numéro se
 * termine par 00.
 */
test('un parent souscrit une offre, voit un paiement refusé, réessaie, puis télécharge son reçu', async ({ page, request }) => {
  test.setTimeout(90_000);
  const { telephone } = await parentAvecEnfant(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await ongletReglages(page);
  await page.getByRole('link', { name: 'Mon abonnement' }).click();

  // Écran 41, sans abonnement : les trois offres, l'offre École n'est pas souscriptible par une famille
  await expect(page.getByRole('heading', { name: 'Mon abonnement' })).toBeVisible();
  await expect(page.getByText('Aucun abonnement pour cet enfant.')).toBeVisible();
  await expect(page.getByRole('button', { name: /Essentiel.*1 500 FCFA/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Premium.*5 000 FCFA/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /École/ })).toHaveCount(0);
  await expect(page.getByText('SOS, détection de retrait et page QR inclus dans toutes les offres.')).toBeVisible();
  await page.getByRole('button', { name: /Intermédiaire.*2 250 FCFA/ }).click();

  // Écran 42 : un portefeuille qui refuse
  await expect(page.getByRole('heading', { name: "Payer l'abonnement" })).toBeVisible();
  await expect(page.getByText('Aucun frais FasoGuardian. Frais opérateur éventuels à votre charge.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-42-paiement.png', fullPage: true });
  await page.getByRole('button', { name: /Payer 2 250 FCFA/ }).click();
  await expect(page.getByText('Saisissez les 8 chiffres du numéro.')).toBeVisible();
  await page.getByLabel('Numéro Orange Money').fill('70123400');
  await page.getByRole('button', { name: /Payer 2 250 FCFA/ }).click();
  await expect(page.getByRole('heading', { name: 'Validez sur votre téléphone' })).toBeVisible();
  await expect(page.getByText(/Expire dans [01]:\d\d/)).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-42-attente.png', fullPage: true });
  await expect(page.getByRole('heading', { name: 'Paiement non abouti' })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("Solde insuffisant. Aucun débit n'a été effectué.")).toBeVisible();

  // Nouvel essai en un geste, avec Moov Money cette fois
  await page.getByRole('button', { name: 'Réessayer' }).click();
  await page.getByText('Moov Money', { exact: true }).click();
  await page.getByLabel('Numéro Moov Money').fill('70123456');
  await page.getByRole('button', { name: /Payer 2 250 FCFA/ }).click();
  await expect(page.getByRole('heading', { name: 'Validez sur votre téléphone' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Paiement reçu' })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText(/Abonnement Intermédiaire actif jusqu'au \d{1,2} \S+ \d{4}\. Reçu FG-R-\d{4}-\d{2}-\d{4,} envoyé par SMS\./)).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-42-recu.png', fullPage: true });

  // Écran 43 : le reçu, et son PDF
  await page.getByRole('link', { name: 'Voir le reçu' }).click();
  await expect(page.getByRole('heading', { name: 'Reçus' })).toBeVisible();
  const recu = page.getByRole('button', { name: /Intermédiaire.*Moov Money.*FG-R-.*2 250 F/ });
  await expect(recu).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-43-recus.png', fullPage: true });
  const telechargement = page.waitForEvent('download');
  await recu.click();
  expect((await telechargement).suggestedFilename()).toMatch(/^FG-R-\d{4}-\d{2}-\d{4,}\.pdf$/);

  // Retour à l'écran 41 : l'offre est active, le renouvellement automatique réglable
  await page.getByRole('link', { name: 'Retour' }).click();
  await expect(page.getByText(/2\s250 FCFA \/ mois/)).toBeVisible();
  await expect(page.getByText(/Prochaine échéance .* · renouvellement auto · Moov Money/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Intermédiaire · actuelle/ })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-41-abonnement.png', fullPage: true });
  await page.getByRole('switch', { name: 'Renouvellement automatique' }).click();
  await expect(page.getByText(/à renouveler vous-même/)).toBeVisible();

  // Écrans 44 et 45 : échéance passée de dix jours, le tableau de bord donne le dernier rappel avant suspension.
  await page.goto('/enfants');
  const fiche = await page.locator('a[href^="/enfants/"]').first().getAttribute('href');
  const enfant = fiche!.split('/')[2];
  expect(enfant).toMatch(/^[0-9a-f-]{36}$/);
  sqlPile(`UPDATE abonnements.abonnement SET statut = 'EN_RETARD', prochaine_echeance = current_date - 10 WHERE enfant_id = '${enfant}'`);
  await page.goto('/');
  const rappel = page.getByRole('region', { name: 'Abonnement' });
  await expect(rappel).toContainText('Dernier rappel avant suspension');
  await expect(rappel).toContainText("le suivi continu et l'historique étendu seront suspendus");
  await expect(rappel).toContainText('Page QR et SOS toujours actifs');
  await rappel.scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'e2e/.etat/ecran-45-rappel.png' });
  await rappel.getByRole('link', { name: 'Régler' }).click();
  await expect(page).toHaveURL(/\/abonnement\?enfant=/);
});
