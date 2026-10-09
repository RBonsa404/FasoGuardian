import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, dernierCodeSms, parentAvecEnfant } from './aides';

/** Module alertes (US-ENF-001, US-ENF-002, US-PAR-008, 010, 012) : du bouton SOS du bracelet à la levée par le parent. */
test('un SOS du bracelet arrive au parent, qui le prend en charge puis le lève ; il autorise ensuite un retrait', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);

  // Le bracelet émet un SOS avec sa dernière position, en MQTT sous son propre certificat
  const maintenant = Math.floor(Date.now() / 1000) + 1;
  braceletEmet(carte.numeroSerie, 'alert', { t: maintenant, seq: 1, ev: 'sos', lat: 12.3714, lon: -1.5197, acc: 15 });
  await expect.poll(async () => (await (await request.get(`${SERVEUR}/api/v1/alertes?enCours=true`, { headers: entetes })).json()).length).toBe(1);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Tableau de bord : le bandeau d'alerte mène droit à l'alerte
  const bandeau = page.getByRole('alert').filter({ hasText: '1 alerte en cours' });
  await expect(bandeau).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: 'e2e/.etat/ecran-16-alerte-en-cours.png' });
  await bandeau.click();

  await expect(page.getByRole('heading', { name: 'Yacouba a déclenché le SOS' })).toBeVisible();
  await expect(page.getByText('Alerte critique')).toBeVisible();
  await expect(page.getByRole('application', { name: "Carte de la position au déclenchement de l'alerte" })).toBeVisible();
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: 'e2e/.etat/ecran-25-alerte-critique.png', fullPage: true });
  await page.getByRole('button', { name: "J'ai pris en charge" }).click();
  await expect(page.getByText('Prise en charge · par vous')).toBeVisible();

  // Levée : motif obligatoire, inscrit au journal
  await page.getByRole('button', { name: "Lever l'alerte" }).click();
  const feuille = page.getByRole('dialog', { name: "Lever l'alerte" });
  await feuille.getByRole('button', { name: 'Confirmer' }).click();
  await expect(feuille.getByText('Choisissez un motif ou décrivez ce qui s\'est passé.')).toBeVisible();
  await feuille.getByText('Enfant retrouvé').click();
  await feuille.getByLabel('Précision (facultatif)').fill('à la sortie de l\'école');
  await feuille.getByRole('button', { name: 'Confirmer' }).click();
  await expect(page.getByText("Levée · par vous · Enfant retrouvé · à la sortie de l'école")).toBeVisible();
  await expect(page.getByRole('button', { name: "Lever l'alerte" })).toHaveCount(0);

  // Centre des alertes : plus rien en cours, l'alerte reste dans « Toutes » ; journal de l'enfant
  await page.getByRole('link', { name: 'Retour' }).click();
  await expect(page.getByText('Aucune alerte en cours.')).toBeVisible();
  await page.getByRole('button', { name: 'Toutes' }).click();
  await expect(page.getByRole('link', { name: /SOS · appui long · Yacouba/ })).toContainText('Levée');
  await page.goto(`/enfants/${enfants[0].id}/journal`);
  await expect(page.getByRole('heading', { name: 'SOS · appui long' })).toBeVisible();
  await expect(page.getByRole('listitem')).toHaveCount(3);
  await expect(page.getByText('Entrées scellées : aucune ne peut être modifiée ni supprimée.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-20-journal.png', fullPage: true });

  // Autorisation de retrait : 2 h pour la recharge, confirmée par code SMS
  await page.goto(`/enfants/${enfants[0].id}/bracelet`);
  await page.getByRole('link', { name: 'Autoriser un retrait' }).click();
  await expect(page.getByRole('heading', { name: 'Autoriser un retrait' })).toBeVisible();
  await page.getByText('Recharge', { exact: true }).click();
  await page.screenshot({ path: 'e2e/.etat/ecran-38-retrait.png', fullPage: true });
  await page.getByRole('button', { name: 'Autoriser 2 h · code SMS' }).click();
  let code = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(code.getByText('Renvoyer le code dans')).toBeVisible();
  await code.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page.getByRole('heading', { name: 'Retrait autorisé' })).toBeVisible();
  await expect(page.getByRole('timer')).toContainText(/1:59:\d\d/);
  await expect(page.getByText('Le bracelet est toujours porté.')).toBeVisible();

  // Le fermoir s'ouvre pendant la fenêtre : aucune alerte, l'écran le signale
  braceletEmet(carte.numeroSerie, 'alert', { t: Math.floor(Date.now() / 1000) + 1, seq: 2, ev: 'strap' });
  await expect.poll(async () => (await (await request.get(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/retrait`, { headers: entetes })).json()).retire).toBe(true);
  expect((await (await request.get(`${SERVEUR}/api/v1/alertes?enCours=true`, { headers: entetes })).json()).length).toBe(0);
  await page.reload();
  await expect(page.getByText('Le bracelet a été retiré. Remettez-le à Yacouba avant la fin')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-38-retrait-actif.png', fullPage: true });

  // Prolongation sous code, puis remise du bracelet
  await page.getByRole('button', { name: '+ 30 min · code SMS' }).click();
  code = page.getByRole('dialog', { name: 'Confirmez par code SMS' });
  await expect(code.getByText('Renvoyer le code dans')).toBeVisible();
  await code.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page.getByRole('timer')).toContainText(/2:2\d:\d\d/);
  await page.getByRole('button', { name: 'Bracelet remis' }).click();
  await expect(page.getByText('Bracelet remis. La surveillance du retrait a repris.')).toBeVisible();
});
