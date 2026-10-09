import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, codeTotp, creerAgent, parentAvecEnfant } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/**
 * Console du service après-vente (US-SAV-001, US-SAV-002) : parc, fiche d'un bracelet, retour et remise en
 * stock, file des bracelets muets. L'ouverture d'un ticket par la supervision est couverte par MaintenanceIT.
 */
test('un agent SAV suit un bracelet du parc, enregistre son retour puis le remet en stock', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);

  const agent = await creerAgent(request, ['SAV']);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();
  const navigation = page.getByRole('navigation', { name: 'Navigation principale' });
  // Le rôle SAV ne voit ni la file KYC ni la conformité.
  await expect(navigation.getByRole('link')).toHaveText(['Tableau de bord', 'Parc de bracelets', 'Bracelets muets']);

  // Écran 64 : le bracelet est actif et porté, sans que l'agent sache par qui
  await navigation.getByRole('link', { name: 'Parc de bracelets' }).click();
  await expect(page.getByRole('heading', { name: 'Parc de bracelets' })).toBeVisible();
  await page.getByRole('group', { name: 'Filtrer par état' }).getByRole('button', { name: /Actif/ }).click();
  const ligne = page.getByRole('row').filter({ hasText: carte.numeroSerie });
  await expect(ligne.getByText('Par un enfant')).toBeVisible();
  await expect(page.getByText('Yacouba')).toHaveCount(0);
  await page.screenshot({ path: 'e2e/.etat/ecran-64-parc.png', fullPage: true });
  await ligne.click();

  // Écran 65 : fiche, retour au SAV, remise en stock avec une nouvelle carte d'activation
  await expect(page.getByRole('heading', { name: carte.numeroSerie })).toBeVisible();
  await expect(page.getByText(/→ en cours/)).toBeVisible();
  await page.getByRole('button', { name: 'Retour au SAV' }).click();
  await page.getByRole('dialog', { name: 'Enregistrer le retour au SAV ?' }).getByRole('button', { name: 'Retour au SAV' }).click();
  await expect(page.getByText('En SAV', { exact: true })).toBeVisible();
  await expect(page.getByText('Désappairage').or(page.getByText('Panne'))).toBeVisible();
  await page.getByRole('button', { name: 'Remettre en stock' }).click();
  await page.getByRole('dialog', { name: 'Remettre en stock ?' }).getByRole('button', { name: 'Remettre en stock' }).click();
  await expect(page.getByText("Carte d'activation à imprimer — affichée une seule fois")).toBeVisible();
  const nouveauCode = (await page.locator('[data-code-appairage]').textContent())!.trim();
  expect(nouveauCode).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{3}$/);
  expect(nouveauCode).not.toBe(carte.codeAppairage);
  await expect(page.getByText('En stock', { exact: true })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-65-fiche-bracelet.png', fullPage: true });

  // Le parent n'a plus de bracelet associé
  expect((await request.get(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet`, { headers: entetes })).status()).toBe(404);

  // Écran 66 : la file des bracelets muets
  await navigation.getByRole('link', { name: 'Bracelets muets' }).click();
  await expect(page.getByRole('heading', { name: 'Bracelets muets' })).toBeVisible();
  await expect(page.getByText('> 3 intervalles sans nouvelles · ticket ouvert automatiquement')).toBeVisible();
  await page.getByRole('button', { name: 'Résolus' }).click();
  await expect(page.getByRole('button', { name: 'Résolus' })).toHaveAttribute('aria-pressed', 'true');
  await page.screenshot({ path: 'e2e/.etat/ecran-66-muets.png', fullPage: true });
});
