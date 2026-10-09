import { expect, test } from '@playwright/test';

import { SERVEUR, agentKyc, codeTotp, creerAgent } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/**
 * Administration de la console (US-ADM-001, US-SYS-010) : agents et rôles, matrice des habilitations,
 * accès refusés visibles de l'administrateur.
 */
test('un administrateur crée un agent, change son périmètre, le suspend et voit les accès refusés', async ({ page, request }) => {
  // Un agent KYC tente d'ouvrir le parc : le refus doit apparaître à l'écran de sécurité.
  const jetonKyc = await agentKyc(request);
  expect((await request.get(`${SERVEUR}/api/v1/console/parc`, { headers: { Authorization: `Bearer ${jetonKyc}` } })).status()).toBe(403);

  const admin = await creerAgent(request, ['ADMIN']);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  await page.getByLabel('Identifiant').fill(admin.identifiant);
  await page.getByLabel('Mot de passe').fill(admin.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  const navigation = page.getByRole('navigation', { name: 'Navigation principale' });

  // Écran 69 : matrice, création d'un agent
  await navigation.getByRole('link', { name: 'Agents et rôles' }).click();
  await expect(page.getByRole('heading', { name: 'Agents et rôles' })).toBeVisible();
  await expect(page.getByText("Aucun rôle interne ne voit la position ni la fiche santé d'un enfant.")).toBeVisible();
  const moi = page.getByRole('row').filter({ hasText: admin.identifiant });
  await expect(moi.getByText('Votre compte')).toBeVisible();
  await expect(moi.getByRole('button')).toHaveCount(0);

  const identifiant = `issa.sav.${Date.now()}`;
  await page.getByRole('button', { name: 'Ajouter un agent' }).click();
  const feuille = page.getByRole('dialog', { name: 'Ajouter un agent' });
  await feuille.getByLabel('Identifiant').fill(identifiant);
  await feuille.getByLabel('Mot de passe provisoire').fill('phrase-de-passe-provisoire-2026');
  await feuille.getByRole('button', { name: "Créer l'agent" }).click();
  await expect(feuille.getByText('Attribuez au moins un rôle.')).toBeVisible();
  await feuille.getByText('Agent SAV', { exact: true }).click();
  await feuille.getByRole('button', { name: "Créer l'agent" }).click();
  const ligne = page.getByRole('row').filter({ hasText: identifiant });
  await expect(ligne.getByText('Second facteur à activer')).toBeVisible();
  await expect(ligne.getByText('Agent SAV')).toBeVisible();

  // Changement de périmètre, puis suspension
  await ligne.getByRole('button', { name: 'Rôles' }).click();
  const roles = page.getByRole('dialog', { name: `Rôles de ${identifiant}` });
  await roles.getByText('Agent SAV', { exact: true }).click();
  await roles.getByText('Opérateur support', { exact: true }).click();
  await roles.getByRole('button', { name: 'Enregistrer les rôles' }).click();
  await expect(ligne.getByText('Opérateur support')).toBeVisible();
  await expect(ligne.getByText('Agent SAV')).toHaveCount(0);
  await ligne.getByRole('button', { name: 'Suspendre' }).click();
  await expect(ligne.getByText('Suspendu')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-69-agents.png', fullPage: true });
  const refuse = await request.post(`${SERVEUR}/api/v1/auth/agents/connexion`, { data: { identifiant, motDePasse: 'phrase-de-passe-provisoire-2026' } });
  expect(refuse.status()).toBe(401);
  await ligne.getByRole('button', { name: 'Rétablir' }).click();
  await expect(ligne.getByText('Second facteur à activer')).toBeVisible();

  // Écran 73 : le refus opposé à l'agent KYC est visible
  await navigation.getByRole('link', { name: 'Sécurité' }).click();
  await expect(page.getByRole('heading', { name: 'Sécurité', exact: true })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Accès refusés' }).getByRole('row').filter({ hasText: 'GET /api/v1/console/parc' }).first()).toBeVisible();
  await expect(page.getByText(/Tentatives d'énumération de QR/)).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-73-securite.png', fullPage: true });
});
