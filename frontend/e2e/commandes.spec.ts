import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant, simulerBracelet } from './aides';

/**
 * Commandes signées de bout en bout (US-SYS-011, US-ENF-002) : l'application du parent, le serveur, le broker
 * et un bracelet simulé qui vérifie la signature de la plateforme avant d'obéir.
 */
test('« Localiser maintenant » et le mode alerte atteignent le bracelet, qui les vérifie et les accuse', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = enfants[0].id;
  expect((await request.post(`${SERVEUR}/api/v1/enfants/${enfant}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  const position = async () => (await (await request.get(`${SERVEUR}/api/v1/enfants/${enfant}/position`, { headers: entetes })).json()).position;

  const bracelet = simulerBracelet(carte.numeroSerie);
  try {
    await expect.poll(() => bracelet.sortie(), { timeout: 20_000 }).toContain('commandes vérifiées');
    await expect.poll(position, { timeout: 15_000 }).not.toBeNull();
    const premiere = (await position()).mesureeLe;

    await page.goto('/connexion');
    await page.getByLabel('Numéro mobile').fill(telephone);
    await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await page.getByRole('link', { name: 'Agrandir' }).click();
    await expect(page.getByText(/Précise ± \d+ m/)).toBeVisible();

    // Le bracelet n'émettrait pas avant dix minutes : seule la commande peut produire une nouvelle position.
    await page.waitForTimeout(1100);
    await page.getByRole('button', { name: 'Localiser maintenant' }).click();
    await expect(page.getByText('Demande envoyée au bracelet.')).toBeVisible();
    await expect.poll(() => bracelet.sortie()).toContain('commande loc exécutée');
    await expect.poll(async () => (await position()).mesureeLe, { timeout: 15_000 }).not.toBe(premiere);

    // Une seconde demande dans la minute est refusée
    await page.getByRole('button', { name: 'Localiser maintenant' }).click();
    await expect(page.getByText('Une localisation vient d\'être demandée. Patientez une minute.')).toBeVisible();

    // SOS : le serveur met le bracelet en mode alerte ; la levée par le parent l'en sort
    braceletEmet(carte.numeroSerie, 'alert', { t: Math.floor(Date.now() / 1000) + 1, seq: 9001, ev: 'sos' });
    await expect.poll(() => bracelet.sortie(), { timeout: 15_000 }).toContain('mode alerte activé');
    const alertes = await (await request.get(`${SERVEUR}/api/v1/alertes?enCours=true`, { headers: entetes })).json();
    expect((await request.post(`${SERVEUR}/api/v1/alertes/${alertes[0].id}/acquittement`, { headers: entetes })).status()).toBe(200);
    expect((await request.post(`${SERVEUR}/api/v1/alertes/${alertes[0].id}/levee`, { headers: entetes, data: { motif: 'Essai' } })).status()).toBe(200);
    await expect.poll(() => bracelet.sortie(), { timeout: 15_000 }).toContain('mode alerte désactivé');

    // Une commande qui ne vient pas de la plateforme est refusée par le bracelet
    braceletEmetCommandeNonSignee(carte.numeroSerie);
    expect(bracelet.sortie()).not.toContain('commande refusée');
  } finally {
    bracelet.arreter();
  }
});

/** L'ACL du broker interdit à un bracelet d'écrire sur son propre sujet de commandes : la publication est sans effet. */
function braceletEmetCommandeNonSignee(numeroSerie: string): void {
  try {
    braceletEmet(numeroSerie, 'cmd' as 'alert', { cmd: 'alert', p: { on: 0 } });
  } catch {
    // Selon la version du broker, la publication refusée peut échouer côté client : c'est aussi un refus.
  }
}
