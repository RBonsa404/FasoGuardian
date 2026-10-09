import { createHash, randomBytes, sign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, codeTotp, creerAgent, parentAvecEnfant, simulerBracelet } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/** Signe le manifeste d'une image comme le fait le poste de publication : ECDSA P-256, R‖S en base64url. */
function signerManifeste(version: string, taille: number, sha256: string): string {
  const cle = readFileSync(join(__dirname, '.etat', 'ota-privee.pem'), 'utf8');
  return sign('sha256', Buffer.from(`FG-OTA|${version}|${taille}|${sha256}`), { key: cle, dsaEncoding: 'ieee-p1363' }).toString('base64url');
}

/**
 * Mise à jour du logiciel embarqué (US-PAR-013) : une image dont la signature ne se vérifie pas est refusée ;
 * une image signée est déployée par vagues, le bracelet vérifie lui-même le manifeste avant de redémarrer sur
 * la nouvelle version, et le parent en est informé.
 */
test('une image signée est déployée par vagues jusqu’au bracelet, qui la vérifie ; une image non signée est refusée', async ({ page, browser, request }) => {
  test.setTimeout(120_000);
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);

  const version = `8.${Math.floor(Math.random() * 900) + 100}.${Math.floor(Math.random() * 900) + 100}`;
  const taille = 421_888;
  const sha256 = createHash('sha256').update(randomBytes(64)).digest('hex');
  const bracelet = simulerBracelet(carte.numeroSerie);
  const contexteConsole = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const console_ = await contexteConsole.newPage();
  try {
    await expect.poll(() => bracelet.sortie(), { timeout: 20_000 }).toContain('commandes vérifiées');

    const agent = await creerAgent(request, ['SAV']);
    await console_.goto(`${CONSOLE}/`);
    await console_.getByLabel('Identifiant').fill(agent.identifiant);
    await console_.getByLabel('Mot de passe').fill(agent.motDePasse);
    await console_.getByRole('button', { name: 'Continuer' }).click();
    const secret = (await console_.locator('[data-secret]').textContent())!.trim();
    await console_.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
    await console_.getByRole('button', { name: 'Se connecter' }).click();
    await console_.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Campagnes OTA' }).click();
    await expect(console_.getByRole('heading', { name: 'Campagnes OTA' })).toBeVisible();

    // Écran 68 : le manifeste d'une autre version ne signe pas cette image
    await console_.getByRole('button', { name: 'Nouvelle image' }).click();
    const feuille = console_.getByRole('dialog', { name: 'Nouvelle image' });
    await feuille.getByLabel('Version').fill(version);
    await feuille.getByLabel('Taille (octets)').fill(String(taille));
    await feuille.getByLabel("Adresse de l'image (https)").fill(`https://mises-a-jour.fasoguardian.test/fg-${version}.bin`);
    await feuille.getByLabel('Empreinte SHA-256').fill(sha256);
    await feuille.getByLabel('Signature du manifeste').fill(signerManifeste('9.9.9', taille, sha256));
    await feuille.getByLabel('Objet de la mise à jour').fill("Correctif d'autonomie en 2G");
    await feuille.getByRole('button', { name: 'Vérifier et enregistrer' }).click();
    await expect(feuille.getByText("La signature de l'image ne correspond pas à la clé de publication : l'image est refusée.")).toBeVisible();

    await feuille.getByLabel('Signature du manifeste').fill(signerManifeste(version, taille, sha256));
    await feuille.getByRole('button', { name: 'Vérifier et enregistrer' }).click();
    const campagne = console_.getByRole('region', { name: `Campagne OTA ${version}` });
    await expect(campagne.getByText('Préparée')).toBeVisible();
    await expect(campagne.getByText("Image signée (ECDSA P-256) · 412 Ko · Correctif d'autonomie en 2G.")).toBeVisible();

    // Les vagues se lancent une à une ; la dernière vise tout le parc, dont le bracelet de l'essai
    for (const vague of ['1 (1 %)', '2 (10 %)', '3 (25 %)', '4 (100 %)']) {
      await campagne.getByRole('button', { name: `Lancer la vague ${vague}` }).click();
      await expect(campagne.getByRole('button', { name: `Lancer la vague ${vague}` })).toHaveCount(0);
    }
    await expect(campagne.getByText('En cours', { exact: true })).toBeVisible();
    await console_.screenshot({ path: 'e2e/.etat/ecran-68-ota.png', fullPage: true });

    // Le bracelet vérifie le manifeste avec la clé de publication, puis redémarre sur la nouvelle version
    await expect.poll(() => bracelet.sortie(), { timeout: 30_000 }).toContain(`image ${version} vérifiée, redémarrage sur la nouvelle version`);
    await expect
      .poll(async () => (await (await request.get(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/bracelet`, { headers: entetes })).json()).versionLogiciel, { timeout: 15_000 })
      .toBe(version);

    // La pause arrête les relances ; elle est proposée tant que la campagne est en cours
    await campagne.getByRole('button', { name: 'Mettre en pause' }).click();
    await expect(campagne.getByText('En pause')).toBeVisible();

    // Le parent a été informé avant l'installation, et voit la version installée
    await page.goto('/connexion');
    await page.getByLabel('Numéro mobile').fill(telephone);
    await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await page.getByRole('link', { name: 'Alertes', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Informations' })).toBeVisible();
    await expect(page.getByText(`Une mise à jour (${version}) va être installée sur le bracelet ${carte.numeroSerie}`)).toBeVisible();
    await page.goto(`/enfants/${enfants[0].id}/bracelet`);
    await expect(page.getByText(version, { exact: true })).toBeVisible();
  } finally {
    bracelet.arreter();
    await contexteConsole.close();
  }
});
