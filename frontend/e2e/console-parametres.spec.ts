import { createHmac } from 'node:crypto';

import { expect, test } from '@playwright/test';

import { SERVEUR, codeTotp, creerAgent } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/** Sceau du serveur de réseau LoRaWAN : `t=<secondes>,v1=<HMAC-SHA256 de "t.corps">`. */
function sceau(corps: string): string {
  const t = Math.floor(Date.now() / 1000);
  return `t=${t},v1=${createHmac('sha256', process.env['FG_LORAWAN_SECRET'] ?? '').update(`${t}.${corps}`).digest('hex')}`;
}

/**
 * Paramétrage (US-SYS-004) : l'administrateur lit les tarifs, enregistre la passerelle LoRaWAN d'une école,
 * la voit passer en ligne dès que le serveur de réseau donne signe de vie, puis la retire.
 */
test('un administrateur enregistre une passerelle LoRaWAN, la voit en ligne, puis la retire', async ({ page, request }) => {
  const admin = await creerAgent(request, ['ADMIN']);
  const eui = [...crypto.getRandomValues(new Uint8Array(8))].map((o) => o.toString(16).padStart(2, '0')).join('').toUpperCase();
  const ecole = `École Les Manguiers ${eui.slice(-4)}`;
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${CONSOLE}/`);
  await page.getByLabel('Identifiant').fill(admin.identifiant);
  await page.getByLabel('Mot de passe').fill(admin.motDePasse);
  await page.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await page.locator('[data-secret]').textContent())!.trim();
  await page.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Paramétrage' }).click();

  // Écran 74 : tarifs et gabarit du SMS, en lecture
  await expect(page.getByRole('heading', { name: 'Paramétrage' })).toBeVisible();
  await expect(page.getByText(/1\s500 FCFA/)).toBeVisible();
  await expect(page.getByText('FasoGuardian : ALERTE SOS.')).toBeVisible();
  await expect(page.getByText('aucune donnée de santé ni coordonnée GPS autorisée dans un SMS')).toBeVisible();

  // Enregistrement d'une passerelle, saisie contrôlée
  await page.getByRole('button', { name: 'Enregistrer une passerelle' }).click();
  const feuille = page.getByRole('dialog', { name: 'Enregistrer une passerelle' });
  await feuille.getByLabel('Établissement').fill(ecole);
  await feuille.getByLabel('Identifiant de la passerelle (EUI)').fill('A840');
  await feuille.getByRole('button', { name: 'Enregistrer', exact: true }).click();
  await expect(feuille.getByText("L'identifiant compte 16 chiffres hexadécimaux")).toBeVisible();
  await feuille.getByLabel('Identifiant de la passerelle (EUI)').fill(eui);
  await feuille.getByLabel('Latitude').fill('12,3642');
  await feuille.getByLabel('Longitude').fill('-1.5331');
  await feuille.getByRole('button', { name: 'Enregistrer', exact: true }).click();
  const ligne = page.getByRole('listitem').filter({ hasText: ecole });
  await expect(ligne.getByText(`${eui} · rayon 150 m`)).toBeVisible();
  await expect(ligne.getByText('Hors ligne')).toBeVisible();

  // Le serveur de réseau donne signe de vie : sans sceau il est refusé, avec son sceau la passerelle passe en ligne
  const corps = JSON.stringify({ passerelle: eui });
  const entetes = { 'Content-Type': 'application/json' };
  expect((await request.post(`${SERVEUR}/api/v1/public/lorawan/trames`, { headers: entetes, data: corps })).status()).toBe(403);
  expect((await request.post(`${SERVEUR}/api/v1/public/lorawan/trames`, { headers: { ...entetes, 'X-FG-Signature': sceau(corps) }, data: corps })).status()).toBe(204);
  await page.reload();
  await expect(ligne.getByText('En ligne')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-74-parametres.png', fullPage: true });

  await ligne.getByRole('button', { name: 'Retirer' }).click();
  await expect(ligne).toHaveCount(0);
});
