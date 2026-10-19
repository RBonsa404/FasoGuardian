import { expect, test } from '@playwright/test';

import { codeTotp, creerAgent, parentAvecDossierDepose } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

test.use({ viewport: { width: 1440, height: 900 } });

/** Console interne (US-ADM-001, US-PAR-001) : enrôlement TOTP, instruction d'un dossier, cloisonnement des rôles. */
test('un agent KYC active son second facteur, instruit un dossier et le valide', async ({ page, request }) => {
  const { reference } = await parentAvecDossierDepose(request);
  const agent = await creerAgent(request, ['KYC']);

  await page.goto(`${CONSOLE}/`);
  await expect(page).toHaveURL(/connexion/);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  await expect(page.getByRole('region', { name: 'Premier enrôlement' })).toBeVisible();
  await expect(page.getByRole('img', { name: "QR code d'enrôlement" })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-55-enrolement.png' });
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await page.getByRole('navigation').getByRole('link', { name: 'File KYC' }).click();
  const ligne = page.getByRole('row').filter({ hasText: reference });
  await expect(ligne).toContainText('Zongo Aminata → Yacouba');
  await expect(ligne).toContainText('Non attribué');
  await page.screenshot({ path: 'e2e/.etat/ecran-59-file-kyc.png' });
  await ligne.click();

  await page.getByRole('button', { name: 'Prendre en charge' }).click();
  await expect(page.getByText('B76543210')).toBeVisible();
  await expect(page.getByText('né le 2 mai 2017')).toBeVisible();
  await page.getByRole('button', { name: "Pièce d'identité · recto" }).click();
  await expect(page.getByRole('img', { name: "Pièce d'identité · recto" })).toBeVisible();
  const outils = page.getByRole('toolbar', { name: 'Affichage de la pièce' });
  await outils.getByRole('button', { name: 'Agrandir' }).click();
  await expect(outils).toContainText('125 %');
  await outils.getByRole('button', { name: 'Réduire' }).click();
  await page.screenshot({ path: 'e2e/.etat/ecran-60-instruction.png' });

  await page.getByRole('radio', { name: 'Valider' }).click();
  await page.getByRole('button', { name: 'Confirmer : valider' }).click();
  await expect(page.getByText('Cochez tous les contrôles')).toBeVisible();
  for (const controle of await page.getByRole('checkbox').all()) {
    await controle.check();
  }
  await page.getByRole('button', { name: 'Confirmer : valider' }).click();
  await expect(page.getByText('Compte activé. SMS envoyé au parent.')).toBeVisible();

  await page.getByRole('link', { name: 'Revenir à la file' }).click();
  await expect(page.getByRole('heading', { name: 'Dossiers KYC' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: reference })).toHaveCount(0);
});

test("un opérateur support ne voit pas la file KYC et s'en voit refuser l'accès", async ({ page, request }) => {
  const agent = await creerAgent(request, ['SUPPORT']);

  await page.goto(`${CONSOLE}/connexion`);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  await expect(page.getByRole('navigation').getByRole('link', { name: 'File KYC' })).toHaveCount(0);
  await page.goto(`${CONSOLE}/kyc`);
  await expect(page.getByRole('heading', { name: 'Accès refusé' })).toBeVisible();
  await expect(page.getByText(/Votre rôle « Opérateur support »/)).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-58-refuse.png' });
  await page.getByRole('button', { name: 'Retour à mon tableau de bord' }).click();
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
});
