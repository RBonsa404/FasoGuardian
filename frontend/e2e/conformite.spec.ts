import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, codeTotp, creerAgent, dernierCodeSms, parentAvecEnfant } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/**
 * Conformité (US-ADM-002, US-ADM-003) : le parent télécharge ses données puis clôt son compte ;
 * l'administrateur exécute la demande d'effacement, consulte le journal d'audit et vérifie sa chaîne.
 */
test('un parent exporte ses données et clôt son compte, un administrateur exécute l’effacement', async ({ page, request }) => {
  test.setTimeout(90_000);
  const { telephone } = await parentAvecEnfant(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('link', { name: 'Paramètres du compte' }).click();

  // Droit d'accès : un fichier lisible, servi aussitôt
  const telechargement = page.waitForEvent('download');
  await page.getByRole('button', { name: /Télécharger mes données/ }).click();
  const fichier = await telechargement;
  expect(fichier.suggestedFilename()).toBe('mes-donnees-fasoguardian.json');
  const donnees = JSON.parse((await (await fichier.createReadStream()).toArray()).join(''));
  expect(donnees.reference).toMatch(/^ACC-\d{6}$/);
  expect(donnees.compte.telephone).toBe(`+226${telephone}`);
  expect(donnees.enfants.fiches).toHaveLength(1);
  await expect(page.getByText('Vos données ont été téléchargées.')).toBeVisible();

  // Clôture : elle vaut demande d'effacement
  await page.getByRole('button', { name: 'Clôturer le compte' }).click();
  const feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page).toHaveURL(/connexion/);
  const sms = await (await request.get(`${SERVEUR}/api/v1/dev/sms`)).json();
  const accuse = sms.filter((message: { destinataire: string }) => message.destinataire === `+226${telephone}`).at(-1).texte as string;
  const reference = /\((EFF-\d{6})\)/.exec(accuse)![1];

  // Console : un administrateur, second facteur activé à la première connexion
  const agent = await creerAgent(request, ['ADMIN']);
  // La console est un outil de bureau : ses écrans sont conçus pour un poste de travail.
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible();

  // Écran 71 : AIPD non documentée, durées, demande à exécuter
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Conformité CIL' }).click();
  await expect(page.getByRole('heading', { name: 'Conformité CIL' })).toBeVisible();
  await expect(page.getByRole('alert').getByText('Mise en production bloquée')).toBeVisible();
  await expect(page.getByText('Durée de la relation + 1 an')).toBeVisible();
  const ligne = page.getByRole('row').filter({ hasText: reference });
  await expect(ligne.getByText('À exécuter')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-71-conformite.png', fullPage: true });
  await ligne.getByRole('button', { name: 'Exécuter' }).click();
  const confirmation = page.getByRole('dialog', { name: `Exécuter la demande ${reference} ?` });
  await expect(confirmation.getByText('Cette action est définitive et journalisée.')).toBeVisible();
  await confirmation.getByRole('button', { name: "Exécuter l'effacement" }).click();
  await expect(ligne.getByText(/Exécutée le/)).toBeVisible();

  // L'accusé est parti, et le numéro peut de nouveau s'inscrire
  const apres = await (await request.get(`${SERVEUR}/api/v1/dev/sms`)).json();
  expect(apres.some((message: { destinataire: string; texte: string }) => message.destinataire === `+226${telephone}` && message.texte.includes('vos données ont été supprimées') && message.texte.includes(reference))).toBe(true);
  expect((await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).status()).toBe(401);

  // Rapport mensuel
  const rapport = page.waitForEvent('download');
  await page.getByRole('button', { name: /Exporter le rapport de .* \(PDF\)/ }).click();
  expect((await rapport).suggestedFilename()).toMatch(/^rapport-conformite-\d{4}-\d{2}\.pdf$/);

  // Écran 70 : l'effacement est au journal, la chaîne est vérifiée à la demande
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: "Journal d'audit" }).click();
  await expect(page.getByRole('heading', { name: "Journal d'audit" })).toBeVisible();
  await page.getByLabel('Action').fill('effacement_execute');
  await page.getByRole('button', { name: 'Filtrer' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'EFFACEMENT_EXECUTE' }).filter({ hasText: reference })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'EFFACEMENT_DEMANDE' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Vérifier la chaîne' }).click();
  await expect(page.getByRole('status').getByText(/Chaîne intègre · vérifiée/)).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-70-journal-audit.png', fullPage: true });
});
