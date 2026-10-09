import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, codeTotp, creerAgent, dernierCodeSms, parentAvecEnfant } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/**
 * Accusé de réception (US-FDS-001) : le parent a signalé une disparition ; l'agent des forces de sécurité
 * retrouve le signalement par sa référence et en accuse réception ; le parent le voit sur son dossier.
 */
test('un agent des forces de sécurité accuse réception d’un signalement, et le parent en est informé', async ({ page, browser, request }) => {
  test.setTimeout(90_000);
  const { telephone } = await parentAvecEnfant(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const alerte = await (await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/signalement`, { headers: entetes, data: {} })).json();
  expect((await request.post(`${SERVEUR}/api/v1/moi/second-facteur`, { headers: entetes, data: { action: 'ESCALADER_FORCES_SECURITE' } })).status()).toBe(202);
  const escalade = await request.post(`${SERVEUR}/api/v1/alertes/${alerte.id}/escalade`, {
    headers: entetes,
    data: { codeSecondFacteur: await dernierCodeSms(request, telephone) },
  });
  expect(escalade.status()).toBe(201);
  const { reference } = await escalade.json();

  // Console : l'agent ne voit que l'espace de son rôle
  const agent = await creerAgent(request, ['FDS']);
  const contexteConsole = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const console_ = await contexteConsole.newPage();
  await console_.goto(`${CONSOLE}/`);
  await console_.getByLabel('Identifiant').fill(agent.identifiant);
  await console_.getByLabel('Mot de passe').fill(agent.motDePasse);
  await console_.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await console_.locator('[data-secret]').textContent())!.trim();
  await console_.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await console_.getByRole('button', { name: 'Se connecter' }).click();
  const navigation = console_.getByRole('navigation', { name: 'Navigation principale' });
  await expect(navigation.getByRole('link')).toHaveText(['Tableau de bord', 'Signalements']);
  await navigation.getByRole('link', { name: 'Signalements' }).click();
  await expect(console_.getByRole('heading', { name: 'Espace forces de sécurité' })).toBeVisible();

  // Écran 75 : rien sans référence ; une référence inconnue ne révèle rien
  await console_.getByLabel('Référence du signalement').fill('FG-SIG-999999');
  await console_.getByRole('button', { name: 'Retrouver' }).click();
  await expect(console_.getByText('Aucun signalement ne porte cette référence.')).toBeVisible();

  await console_.getByLabel('Référence du signalement').fill(reference);
  await console_.getByRole('button', { name: 'Retrouver' }).click();
  await expect(console_.getByRole('heading', { name: reference })).toBeVisible();
  await expect(console_.getByText('Disparition signalée par un tuteur')).toBeVisible();
  await expect(console_.getByText('Remis en main propre par le parent')).toBeVisible();
  await expect(console_.getByText('Accès limité à 30 jours · chaque consultation est tracée.')).toBeVisible();
  await expect(console_.getByText('Yacouba')).toHaveCount(0);
  await console_.screenshot({ path: 'e2e/.etat/ecran-75-fds.png', fullPage: true });
  await console_.getByRole('button', { name: `Accuser réception · agent ${agent.identifiant}` }).click();
  await expect(console_.getByText('Les parents en ont été informés.')).toBeVisible();
  await expect(console_.getByRole('button', { name: /Accuser réception/ })).toHaveCount(0);

  // Parent : l'accusé figure sur son dossier de signalement
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Alertes', exact: true })).toBeVisible();
  await page.goto(`/alertes/${alerte.id}/signalement`);
  await expect(page.getByRole('heading', { name: `Dossier ${reference}` })).toBeVisible();
  await expect(page.getByText('Les forces de sécurité ont accusé réception le')).toBeVisible();
});
