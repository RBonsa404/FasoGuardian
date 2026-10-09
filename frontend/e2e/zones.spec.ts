import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, dernierCodeSms, parentAvecEnfant } from './aides';

/** Module geolocalisation (US-PAR-006, 007, 008) : carte, Safe Zones et trajets, côté parent. */
test('un parent trace une Safe Zone, la suspend, la réactive, la modifie puis la supprime', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  // Le bracelet est appairé par l'API : l'appairage a son propre parcours (bracelet.spec.ts).
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Tableau de bord : le bracelet est appairé mais n'a encore rien transmis
  await expect(page.getByRole('heading', { name: 'Pas encore de position' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Raccourcis pour Yacouba' }).getByRole('link')).toHaveCount(7);
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: 'e2e/.etat/ecran-16-tableau-de-bord.png', fullPage: true });
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba Zongo/ }).click();

  // Carte : le bracelet vient d'être appairé et n'a encore rien transmis
  await page.getByRole('link', { name: 'Carte', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Pas encore de position' })).toBeVisible();
  await expect(page.getByText(`Le bracelet ${carte.numeroSerie} n'a pas encore transmis de position.`)).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: 'e2e/.etat/ecran-18-carte.png' });
  await page.getByRole('link', { name: 'Retour' }).click();

  // Aucune zone : création d'un cercle autour d'un point touché sur la carte
  await page.getByRole('link', { name: 'Safe Zones' }).click();
  await expect(page.getByText("Aucune zone pour l'instant.")).toBeVisible();
  await expect(page.getByLabel('0 zones sur 3')).toBeVisible();
  await page.getByRole('button', { name: 'Ajouter une zone' }).click();
  await expect(page.getByRole('heading', { name: 'Nouvelle zone' })).toBeVisible();

  await page.getByRole('button', { name: 'Enregistrer · code SMS' }).click();
  await expect(page.getByText('Touchez la carte pour placer le centre de la zone.')).toBeVisible();
  await page.getByRole('application').click({ position: { x: 180, y: 140 } });
  await expect(page.getByText('Touchez la carte pour déplacer le centre.')).toBeVisible();
  await page.getByLabel('Rayon').fill('300');
  await expect(page.getByText('300 m')).toBeVisible();
  await page.getByText('Maison', { exact: true }).click();
  await expect(page.getByLabel('Nom de la zone')).toHaveValue('Maison');
  await page.getByLabel('Nom de la zone').fill('Maison de Tampouy');
  await page.getByLabel('samedi').check({ force: true });
  await page.getByLabel('Fin').fill('18:00');
  await page.getByText('10 min', { exact: true }).click();
  await page.screenshot({ path: 'e2e/.etat/ecran-22-edition-zone.png', fullPage: true });
  await page.getByRole('button', { name: 'Enregistrer · code SMS' }).click();
  let feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));

  // Liste : résumé et quota
  const zone = page.getByRole('button', { name: /Maison de Tampouy/ });
  await expect(zone).toContainText('Cercle 300 m · lun.–sam. 07:00–18:00');
  await expect(page.getByLabel('1 zones sur 3')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-21-zones.png', fullPage: true });

  // Suspension confirmée par un nouveau code, obtenu sans attendre puisque le précédent a servi
  await zone.click();
  let actions = page.getByRole('dialog', { name: 'Maison de Tampouy' });
  await actions.getByRole('button', { name: 'Suspendre · code SMS' }).click();
  feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('pour suspendre « Maison de Tampouy »')).toBeVisible();
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(zone).toContainText('Suspendue');

  // Réactivation sans code
  await zone.click();
  await actions.getByRole('button', { name: 'Réactiver' }).click();
  await expect(zone).not.toContainText('Suspendue');

  // Modification : l'éditeur retrouve la zone telle qu'elle a été enregistrée
  await zone.click();
  await actions.getByRole('button', { name: 'Modifier' }).click();
  await expect(page.getByRole('heading', { name: 'Modifier la zone' })).toBeVisible();
  await expect(page.getByLabel('Nom de la zone')).toHaveValue('Maison de Tampouy');
  await expect(page.getByLabel('samedi')).toBeChecked();
  await expect(page.getByLabel('dimanche')).not.toBeChecked();
  await expect(page.getByLabel('Fin')).toHaveValue('18:00');
  await page.getByLabel('Nom de la zone').fill('Maison');
  await page.getByRole('button', { name: 'Enregistrer · code SMS' }).click();
  feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  const renommee = page.getByRole('button', { name: /^Maison Cercle 300 m/ });
  await expect(renommee).toBeVisible();

  // Suppression confirmée par code
  await renommee.click();
  actions = page.getByRole('dialog', { name: 'Maison', exact: true });
  await actions.getByRole('button', { name: 'Supprimer la zone' }).click();
  feuille = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(feuille.getByText('pour supprimer « Maison »')).toBeVisible();
  await expect(feuille.getByText('Renvoyer le code dans')).toBeVisible();
  await feuille.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page.getByText("Aucune zone pour l'instant.")).toBeVisible();
  await page.getByRole('link', { name: 'Retour' }).click();

  // Trajets : aucune position ce jour-là, durée de conservation rappelée
  await page.getByRole('link', { name: 'Trajets' }).click();
  await expect(page.getByText('Aucune position enregistrée ce jour-là.')).toBeVisible();
  await expect(page.getByText('Les trajets sont conservés 30 jours, puis effacés.')).toBeVisible();
  await expect(page.getByRole('group', { name: 'Jour affiché' }).getByRole('button')).toHaveCount(7);
});
