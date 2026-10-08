import { expect, test } from '@playwright/test';

import { SERVEUR, agentKyc, dernierCodeSms } from './aides';

const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.from('photo de test')]);
const PDF = Buffer.from('%PDF-1.7 acte de naissance de test');

/** Parcours critique « inscription + KYC » (US-PAR-001) : du numéro de téléphone au compte activé. */
test("un parent s'inscrit, dépose son dossier, et son compte est activé après validation par un agent KYC", async ({
  page,
  request,
}) => {
  const telephone = `70${String(Date.now()).slice(-6)}`;

  // Inscription
  await page.goto('/inscription/numero');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByRole('button', { name: 'Recevoir le code' }).click();
  await expect(page).toHaveURL(/inscription\/code/);
  await page.getByLabel('Code à 6 chiffres').fill(await dernierCodeSms(request, telephone));
  await expect(page).toHaveURL(/inscription\/mot-de-passe/);
  await page.getByLabel('Mot de passe').fill('soleil-de-ouaga-2026');
  await expect(page.getByText('Robuste')).toBeVisible();
  await page.getByRole('button', { name: 'Continuer' }).click();
  await expect(page.getByRole('switch', { name: 'Conditions générales (obligatoire)' })).toBeDisabled();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();

  // Dossier de vérification
  await expect(page).toHaveURL(/verification\/mode/);
  await expect(page.getByRole('radio', { name: /En ligne, maintenant/ })).toBeChecked();
  await page.getByRole('button', { name: 'Continuer en ligne' }).click();
  await page.getByLabel('Nom', { exact: true }).fill('Ouédraogo');
  await page.getByLabel('Prénoms').fill('Mariam');
  await page.getByLabel('Numéro de la pièce').fill('B12345678');
  await page.getByRole('button', { name: 'Continuer' }).click();
  await page.locator('input[data-piece=PIECE_RECTO]').setInputFiles({ name: 'recto.jpg', mimeType: 'image/jpeg', buffer: JPEG });
  await expect(page.getByText('Photo jointe')).toBeVisible();
  await page.getByRole('button', { name: 'Continuer' }).click();
  await page.getByLabel("Prénom de l'enfant").fill('Awa');
  await page.getByLabel("Nom de l'enfant", { exact: true }).fill('Ouédraogo');
  await page.getByLabel('Date de naissance').fill('2018-03-14');
  await page.locator('input[data-piece=ACTE_NAISSANCE]').setInputFiles({ name: 'acte.pdf', mimeType: 'application/pdf', buffer: PDF });
  await page.getByRole('button', { name: 'Continuer' }).click();
  await expect(page.getByText('Ouédraogo Mariam')).toBeVisible();
  await page.getByRole('button', { name: 'Envoyer mon dossier' }).click();
  await expect(page.getByRole('heading', { name: 'Dossier en instruction' })).toBeVisible();
  const reference = (await page.getByText(/Réf\. KYC-\d+/).textContent())!.match(/KYC-\d+/)![0];

  // Le compte n'est pas actif tant que le dossier n'est pas validé
  await page.getByRole('button', { name: "Découvrir l'application" }).click();
  await expect(page.getByText('En instruction')).toBeVisible();

  // Instruction par un agent KYC
  const jeton = await agentKyc(request);
  const entetes = { Authorization: `Bearer ${jeton}` };
  const file = await (await request.get(`${SERVEUR}/api/v1/console/kyc/dossiers?size=100`, { headers: entetes })).json();
  const dossier = file.elements.find((element: { reference: string }) => element.reference === reference);
  expect(dossier, `dossier ${reference} dans la file d'instruction`).toBeTruthy();
  const base = `${SERVEUR}/api/v1/console/kyc/dossiers/${dossier.id}`;
  const instruction = await (await request.post(`${base}/prise-en-charge`, { headers: entetes })).json();
  expect(instruction.demandeur.nom).toBe('Ouédraogo');
  expect(instruction.pieces).toHaveLength(2);
  expect((await request.post(`${base}/decision`, { headers: entetes, data: { decision: 'APPROUVER' } })).status()).toBe(200);

  // Le parent est informé et son compte est actif
  await page.goto('/verification/instruction');
  await expect(page.getByRole('heading', { name: 'Votre compte est activé' })).toBeVisible();
  await page.getByRole('button', { name: 'Continuer' }).click();
  await expect(page.getByText('Actif', { exact: true })).toBeVisible();
});
