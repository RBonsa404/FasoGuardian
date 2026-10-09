import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, codeTotp, creerAgent, parentAvecEnfant } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';
const CONTACT = { lien: 'Tante', nom: 'Fatou Sawadogo', telephone: '76112233', visibleSurQr: false };

/**
 * Litige de filiation (US-KYC-001) : l'agent KYC ouvre un litige sur un compte actif, ce qui gèle les réglages
 * du tuteur contesté et suspend sa vue de la position ; la décision lève les mesures et est notifiée.
 */
test('un agent KYC ouvre un litige qui gèle le compte, puis enregistre la décision', async ({ page, request }) => {
  const { telephone, reference } = await parentAvecEnfant(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = `${SERVEUR}/api/v1/enfants/${enfants[0].id}`;

  const agent = await creerAgent(request, ['KYC']);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  await page.getByLabel('Identifiant').fill(agent.identifiant);
  await page.getByLabel('Mot de passe').fill(agent.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Litiges de filiation' }).click();
  await expect(page.getByRole('heading', { name: 'Litiges de filiation' })).toBeVisible();

  // Ouverture : dossier KYC du lien contesté, signalement, suspension conservatoire de la position
  await page.getByRole('button', { name: 'Ouvrir un litige' }).click();
  const ouverture = page.getByRole('dialog', { name: 'Ouvrir un litige' });
  await ouverture.getByLabel('Dossier KYC du lien contesté').fill(reference);
  await ouverture.getByLabel('Signalement reçu').fill("Jugement de garde exclusive produit par l'autre parent.");
  await ouverture.getByRole('switch', { name: 'Suspendre la géolocalisation' }).click();
  await ouverture.getByRole('button', { name: 'Ouvrir le litige et geler le compte' }).click();

  // Écran 61 : mesures conservatoires
  await expect(page.getByText(/Litige ouvert · LIT-\d{6} · compte gelé/)).toBeVisible();
  await expect(page.getByRole('heading', { name: /Contestation de la filiation : .* → Yacouba/ })).toBeVisible();
  await expect(page.getByText('Suspension conservatoire')).toBeVisible();
  await expect(page.getByText('SOS et page QR maintenus')).toBeVisible();
  await expect(page.getByText('décision sous 7 jours')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-61-litige.png', fullPage: true });

  // Côté parent : réglages gelés, position suspendue, consultation possible
  const gele = await request.post(`${enfant}/contacts`, { headers: entetes, data: CONTACT });
  expect(gele.status()).toBe(423);
  expect((await gele.json()).code).toBe('COMPTE_GELE');
  expect((await request.get(`${enfant}/position`, { headers: entetes })).status()).toBe(423);
  expect((await request.get(enfant, { headers: entetes })).status()).toBe(200);

  // Décision : lien maintenu sur accord écrit ; le gel est levé
  await page.getByRole('button', { name: 'Enregistrer la décision' }).click();
  const decision = page.getByRole('dialog', { name: 'Enregistrer la décision' });
  await decision.getByRole('button', { name: 'Appliquer et notifier les parties' }).click();
  await expect(decision.getByText('Choisissez la décision et ce sur quoi elle se fonde.')).toBeVisible();
  await decision.getByText('Lien maintenu', { exact: true }).click();
  await decision.getByText('Accord écrit des parties', { exact: true }).click();
  await decision.getByLabel('Référence de la pièce').fill('Accord du 9 octobre 2026');
  await decision.getByRole('button', { name: 'Appliquer et notifier les parties' }).click();
  await expect(page.getByText(/Litige clos · LIT-\d{6}/)).toBeVisible();
  await expect(page.getByText('Gel levé', { exact: true })).toBeVisible();
  await expect(page.getByText('Accord écrit des parties · Accord du 9 octobre 2026')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Enregistrer la décision' })).toHaveCount(0);

  expect((await request.post(`${enfant}/contacts`, { headers: entetes, data: CONTACT })).status()).toBe(201);
  const sms = await (await request.get(`${SERVEUR}/api/v1/dev/sms`)).json();
  const recus = sms.filter((message: { destinataire: string }) => message.destinataire === `+226${telephone}`).map((message: { texte: string }) => message.texte);
  expect(recus.some((texte: string) => texte.includes("en cours d'instruction"))).toBe(true);
  expect(recus.some((texte: string) => texte.includes('est clos') && texte.includes('de nouveau modifiables'))).toBe(true);
});
