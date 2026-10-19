import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, agentKyc, dernierCodeSms, parentAvecEnfant, parentInscrit } from './aides';

const PARENTS = process.env['FG_E2E_PARENTS'] ?? 'http://localhost:4201';

/**
 * Second tuteur (US-PAR-003, écran 34) : le tuteur invite un adulte par son numéro, confirmé par code SMS ;
 * l'invité passe sa propre vérification et n'accède à l'enfant qu'une fois approuvé.
 */
test('un tuteur invite un second tuteur, qui accède à l’enfant après sa propre vérification', async ({ page, browser, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const invite = await parentInscrit(request, '74');
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: { Authorization: `Bearer ${session.jetonAcces}` } })).json();
  const enfant = enfants[0].id as string;

  // Le tuteur envoie l'invitation, confirmée par code SMS
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/connexion'));
  await page.goto(`/enfants/${enfant}/contacts`);
  await expect(page.getByRole('heading', { name: 'Tuteurs et contacts' })).toBeVisible();
  await expect(page.getByText('Tuteur principal')).toBeVisible();
  await page.getByRole('button', { name: 'Inviter un second tuteur' }).click();
  const feuille = page.getByRole('dialog', { name: 'Inviter un second tuteur' });
  await feuille.getByLabel('Numéro mobile du second tuteur').fill(invite.telephone);
  await feuille.getByRole('button', { name: 'Continuer' }).click();
  const code = page.getByRole('dialog', { name: 'Confirmez avec le code SMS' });
  // Le minuteur de renvoi n'apparaît qu'une fois le code envoyé.
  await expect(code.getByText('Renvoyer le code dans')).toBeVisible();
  await code.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page.getByText('Invitation envoyée par SMS')).toBeVisible();
  await expect(page.getByText(/Invitation valable jusqu'au/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Inviter un second tuteur' })).toBeHidden();
  await page.screenshot({ path: 'e2e/.etat/ecran-34-tuteurs.png', fullPage: true });

  // L'invité répond à l'invitation dans son parcours de vérification
  const contexte = await browser.newContext({ baseURL: PARENTS, viewport: { width: 360, height: 800 }, locale: 'fr-FR' });
  const invitePage = await contexte.newPage();
  await invitePage.goto('/connexion');
  await invitePage.getByLabel('Numéro mobile').fill(invite.telephone);
  await invitePage.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await invitePage.getByRole('button', { name: 'Se connecter' }).click();
  await invitePage.waitForURL((url) => !url.pathname.startsWith('/connexion'));
  await invitePage.goto('/verification/mode');
  await expect(invitePage.getByRole('radio', { name: /vous invite comme second tuteur/ })).toBeChecked();
  await invitePage.getByRole('radio', { name: /En point d'inscription/ }).click();
  await invitePage.getByRole('button', { name: 'Continuer avec un agent' }).click();
  await invitePage.getByLabel('Nom', { exact: true }).fill('Ouédraogo');
  await invitePage.getByLabel('Prénoms').fill('Ibrahim');
  await invitePage.getByLabel('Numéro de la pièce').fill('B87654321');
  await invitePage.getByRole('button', { name: 'Continuer' }).click();
  await expect(invitePage.getByText(/l'agent le retrouve à partir de l'invitation/)).toBeVisible();
  await invitePage.getByRole('button', { name: 'Continuer' }).click();
  await expect(invitePage.getByText(/Sur invitation de/)).toBeVisible();
  await invitePage.getByRole('button', { name: 'Envoyer mon dossier' }).click();
  // Dossier déposé en point d'inscription : la référence à donner et les points où se présenter (écran 11d).
  await expect(invitePage.getByRole('heading', { name: 'Présentez-vous avec vos originaux' })).toBeVisible();
  await expect(invitePage.getByRole('listitem').filter({ hasText: 'Point FasoGuardian · Dassasgho' })).toBeVisible();
  const reference = (await invitePage.getByText(/Donnez la référence KYC-\d+/).textContent())!.match(/KYC-\d+/)![0];

  // Avant l'approbation, l'invité n'accède pas à l'enfant ; le tuteur voit la vérification en cours
  const jetonInvite = (await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone: invite.telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json()).jetonAcces;
  expect((await request.get(`${SERVEUR}/api/v1/enfants/${enfant}`, { headers: { Authorization: `Bearer ${jetonInvite}` } })).status()).toBe(404);
  await page.reload();
  await expect(page.getByText("Vérification d'identité en cours")).toBeVisible();

  // L'agent KYC voit qu'il s'agit d'un second tuteur et approuve
  const entetes = { Authorization: `Bearer ${await agentKyc(request)}` };
  const file = await (await request.get(`${SERVEUR}/api/v1/console/kyc/dossiers?size=100`, { headers: entetes })).json();
  const dossier = file.elements.find((element: { reference: string }) => element.reference === reference);
  expect(dossier.secondTuteur).toBe(true);
  const base = `${SERVEUR}/api/v1/console/kyc/dossiers/${dossier.id}`;
  expect((await request.post(`${base}/prise-en-charge`, { headers: entetes })).status()).toBe(200);
  expect((await request.post(`${base}/decision`, { headers: entetes, data: { decision: 'APPROUVER' } })).status()).toBe(200);

  // L'invité suit désormais l'enfant ; le tuteur voit deux tuteurs
  expect((await request.get(`${SERVEUR}/api/v1/enfants/${enfant}`, { headers: { Authorization: `Bearer ${jetonInvite}` } })).status()).toBe(200);
  await invitePage.goto(`/enfants/${enfant}/contacts`);
  await expect(invitePage.getByText('Ibrahim Ouédraogo (vous)')).toBeVisible();
  await expect(invitePage.getByText('Second tuteur', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('Second tuteur', { exact: true })).toBeVisible();
  await expect(page.getByText('Ibrahim Ouédraogo', { exact: true })).toBeVisible();
  await contexte.close();
});
