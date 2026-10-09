import { readFileSync } from 'node:fs';

import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, dernierCodeSms, parentAvecEnfant } from './aides';

/** Escalade (US-PAR-010) : le parent signale une disparition et obtient le dossier à remettre aux autorités. */
test('un parent signale une disparition, confirme par code SMS et télécharge le dossier', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('link', { name: 'Alertes', exact: true }).click();

  // Le parent ouvre lui-même le signalement : il est horodaté et pris en charge par lui
  await page.getByRole('button', { name: 'Signaler une disparition' }).click();
  await page.getByRole('dialog', { name: 'Signaler une disparition' }).getByRole('button', { name: 'Signaler pour Yacouba' }).click();
  await expect(page.getByRole('heading', { name: 'Signalement ouvert pour Yacouba' })).toBeVisible();
  await expect(page.getByText('Prise en charge · par vous')).toBeVisible();

  // Aperçu de ce que contiendra le dossier, puis confirmation par code SMS
  await page.getByRole('link', { name: 'Signaler une disparition' }).click();
  await expect(page.getByText("Aucun commissariat n'est encore relié à FasoGuardian")).toBeVisible();
  await expect(page.getByText(/Yacouba Zongo · \d+ ans/)).toBeVisible();
  await expect(page.getByText('Aucune information marquée critique')).toBeVisible();
  await expect(page.getByText('Un faux signalement est puni par la loi.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-29-signalement.png', fullPage: true });
  await page.getByRole('button', { name: 'Confirmer par code SMS' }).click();
  const code = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(code.getByText('Renvoyer le code dans')).toBeVisible();
  await code.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));

  // Dossier prêt : référence, durée de disponibilité, téléchargement du PDF
  await expect(page.getByRole('heading', { name: /Dossier FG-SIG-\d{6}/ })).toBeVisible();
  await expect(page.getByText('Remettez ce dossier au commissariat')).toBeVisible();
  const attente = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Télécharger le PDF' }).click();
  const telechargement = await attente;
  expect(telechargement.suggestedFilename()).toMatch(/^FG-SIG-\d{6}\.pdf$/);
  await telechargement.saveAs('e2e/.etat/dossier-signalement.pdf');
  const contenu = readFileSync('e2e/.etat/dossier-signalement.pdf');
  expect(contenu.subarray(0, 5).toString('ascii')).toBe('%PDF-');
  expect(contenu.length).toBeGreaterThan(1500);

  // L'alerte porte l'escalade à son journal et se lève encore
  await page.getByRole('link', { name: 'Retour' }).click();
  await expect(page.getByText('Signalée aux forces de sécurité · par vous')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Dossier de signalement' })).toBeVisible();
  await expect(page.getByRole('button', { name: "C'est une fausse alerte" })).toHaveCount(0);
  await page.getByRole('button', { name: "Lever l'alerte" }).click();
  const feuille = page.getByRole('dialog', { name: "Lever l'alerte" });
  await feuille.getByText('Enfant retrouvé').click();
  await feuille.getByRole('button', { name: 'Confirmer' }).click();
  await expect(page.getByText('Levée · par vous · Enfant retrouvé')).toBeVisible();
});
