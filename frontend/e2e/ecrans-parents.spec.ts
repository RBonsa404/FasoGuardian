import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, braceletAuParc, braceletEmet, parentAvecEnfant, ongletReglages, sqlPile } from './aides';

/** Écran 9 : à la première ouverture sur un appareil, l'introduction passe avant la connexion. */
test('la première visite montre l’introduction, les suivantes mènent droit à la connexion', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/bienvenue$/);
  await expect(page.getByRole('heading', { name: 'Sachez où est votre enfant, sans le surveiller' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-09-bienvenue.png' });
  await page.getByRole('button', { name: 'Suivant' }).click();
  await expect(page.getByRole('heading', { name: 'Un SOS au poignet' })).toBeVisible();
  await page.getByRole('button', { name: 'Suivant' }).click();
  await expect(page.getByRole('heading', { name: "Un QR pour qu'on vous prévienne" })).toBeVisible();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page.getByRole('heading', { name: 'Votre numéro de téléphone' })).toBeVisible();

  await page.goto('/');
  await expect(page).toHaveURL(/\/connexion$/);
});

/** Écrans 33 et 40 : aperçu de la page QR composé des réglages de la famille, et suivi de maintenance. */
test('un parent voit ce que montre la page QR de son enfant, et l’état de maintenance du bracelet', async ({ page, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const carte = await braceletAuParc(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  const enfant = `${SERVEUR}/api/v1/enfants/${enfants[0].id}`;
  expect((await request.post(`${enfant}/bracelet/appairage`, { headers: entetes, data: { code: carte.codeAppairage } })).status()).toBe(200);
  expect((await request.post(`${enfant}/contacts`, { headers: entetes, data: { lien: 'Tante', nom: 'Fatou Sawadogo', telephone: '76112233', visibleSurQr: true } })).status()).toBe(201);
  expect((await request.post(`${enfant}/contacts`, { headers: entetes, data: { lien: 'Voisin', nom: 'Paul Zida', telephone: '76112244', visibleSurQr: false } })).status()).toBe(201);
  expect(
    (
      await request.put(`${enfant}/sante`, {
        headers: entetes,
        data: { groupeSanguin: 'O+', groupeSanguinSurQr: true, elements: [{ type: 'ALLERGIE', libelle: 'Arachides', critique: true }, { type: 'TRAITEMENT', libelle: 'Suivi orthophonique', critique: false }] },
      })
    ).status(),
  ).toBe(200);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await ongletReglages(page);
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba · \d+ ans/ }).click();
  await page.getByRole('link', { name: 'Aperçu de la page QR' }).click();

  // Écran 33 : ni nom ni numéro ; seulement ce que la famille a marqué
  await expect(page.getByRole('heading', { name: 'Ce que voit un tiers' })).toBeVisible();
  const apercu = page.getByRole('region', { name: 'Aperçu de la page publique' });
  await expect(apercu.getByText(carte.numeroSerie)).toBeVisible();
  await expect(apercu.getByText('Appeler · Tante')).toBeVisible();
  await expect(apercu.getByText('Arachides')).toBeVisible();
  await expect(apercu.getByText('O+')).toBeVisible();
  await expect(apercu.getByText('Voisin')).toHaveCount(0);
  await expect(apercu.getByText('Suivi orthophonique')).toHaveCount(0);
  await expect(apercu.getByText('Yacouba')).toHaveCount(0);
  await expect(apercu.getByText('76112233')).toHaveCount(0);
  await page.screenshot({ path: 'e2e/.etat/ecran-33-apercu-qr.png', fullPage: true });

  // L'aperçu dit vrai : la page publique montre la même chose
  const publique = await (await request.get(`${SERVEUR}/q/${carte.jetonQr}`)).text();
  expect(publique).toContain('Appeler · Tante');
  expect(publique).toContain('Arachides');
  expect(publique).not.toContain('Voisin');
  expect(publique).not.toContain('Suivi orthophonique');

  // Écran 40 : le bracelet répond, aucun ticket n'est ouvert
  await page.goto(`/enfants/${enfants[0].id}/bracelet/maintenance`);
  await expect(page.getByRole('heading', { name: 'Aucun incident en cours' })).toBeVisible();
  await expect(page.getByText('Le bracelet répond normalement')).toBeVisible();

  // Écran 16 : bracelet muet. L'ouverture du ticket par la supervision est couverte par MaintenanceIT ; ici, le
  // silence est daté de trois heures (au-delà de trois intervalles de toute offre) et le ticket posé directement.
  const serie = carte.numeroSerie;
  sqlPile(`UPDATE dispositifs.appairage SET debut = now() - INTERVAL '1 day' WHERE fin IS NULL AND bracelet_id = (SELECT id FROM dispositifs.bracelet WHERE numero_serie = '${serie}')`);
  braceletEmet(serie, 'telemetry', { t: Math.floor(Date.now() / 1000) - 3 * 3600, seq: 1, lat: 12.3586, lon: -1.5354, acc: 9, src: 'gnss', bat: 64, net: '2g' });
  await expect.poll(async () => (await (await request.get(`${enfant}/position`, { headers: entetes })).json()).position !== null).toBe(true);
  sqlPile(`INSERT INTO dispositifs.ticket_maintenance (id, reference, bracelet_id, motif, statut, ouvert_le, dernier_contact, batterie, reseau) `
    + `SELECT gen_random_uuid(), 'SAV-' || lpad(nextval('dispositifs.reference_ticket')::text, 6, '0'), id, 'MUET', 'OUVERT', now(), `
    + `now() - INTERVAL '3 hours', 64, '2G' FROM dispositifs.bracelet WHERE numero_serie = '${serie}'; `
    + `UPDATE telemetrie.etat_bracelet SET dernier_contact = now() - INTERVAL '3 hours' WHERE bracelet_id = (SELECT id FROM dispositifs.bracelet WHERE numero_serie = '${serie}')`);
  await page.goto('/');
  const muet = page.getByRole('status').filter({ hasText: 'Pas de nouvelles depuis 3 h' });
  await expect(muet).toBeVisible();
  await expect(muet).toContainText(/Dernière position \d\d:\d\d\. Un ticket SAV a été ouvert\./);
  await page.screenshot({ path: 'e2e/.etat/ecran-16-muet.png' });
  await muet.click();
  await expect(page).toHaveURL(/\/bracelet\/maintenance$/);
  await expect(page.getByText(/SAV-\d{6}/).first()).toBeVisible();
});
