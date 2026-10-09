import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, SERVEUR, parentAvecEnfant } from './aides';

/**
 * Récupération de session (US-PAR-019) : quand le jeton de rafraîchissement n'est plus valable, une
 * réauthentification complète est demandée ; la fiche de l'enfant gardée sur l'appareil reste consultable, et
 * la déconnexion l'efface.
 */
test('session expirée : la fiche de l’enfant reste consultable, puis le parent se reconnecte', async ({ page, context, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  const session = await (await request.post(`${SERVEUR}/api/v1/auth/connexion`, { data: { telephone, motDePasse: MOT_DE_PASSE_PARENT } })).json();
  const entetes = { Authorization: `Bearer ${session.jetonAcces}` };
  const enfants = await (await request.get(`${SERVEUR}/api/v1/enfants`, { headers: entetes })).json();
  expect((await request.put(`${SERVEUR}/api/v1/enfants/${enfants[0].id}/sante`, {
    headers: entetes,
    data: { groupeSanguin: 'O+', groupeSanguinSurQr: false, elements: [{ type: 'ALLERGIE', libelle: 'Arachides (sévère)', critique: true }, { type: 'TRAITEMENT', libelle: 'Vitamine D', critique: false }] },
  })).status()).toBe(200);

  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Mes enfants' })).toBeVisible();

  // Le tableau de bord copie la fiche sur l'appareil
  await expect.poll(() => page.evaluate(() => new Promise<number>((resoudre) => {
    const ouverture = indexedDB.open('fasoguardian');
    ouverture.onerror = () => resoudre(0);
    ouverture.onsuccess = () => {
      const base = ouverture.result;
      if (!base.objectStoreNames.contains('fiches')) {
        base.close();
        resoudre(0);
        return;
      }
      const compte = base.transaction('fiches').objectStore('fiches').count();
      compte.onsuccess = () => {
        base.close();
        resoudre(compte.result);
      };
    };
  }))).toBe(1);

  // Le jeton de rafraîchissement disparaît (révocation, expiration) : au rechargement, la session ne se rétablit pas
  await context.clearCookies();
  await page.reload();
  await expect(page).toHaveURL(/\/session$/);
  await expect(page.getByRole('heading', { name: 'Votre session a expiré' })).toBeVisible();
  await expect(page.getByText('La fiche de Yacouba reste consultable.')).toBeVisible();
  const fiche = page.getByRole('region', { name: /Yacouba/ });
  await expect(fiche.getByText('O+', { exact: true })).toBeVisible();
  await expect(fiche.getByText('Arachides (sévère)')).toBeVisible();
  // Seuls les éléments critiques sont gardés sur l'appareil.
  await expect(page.getByText('Vitamine D')).toHaveCount(0);
  await expect(fiche.getByText(/Copie locale du \d{1,2} \S+ à \d\d:\d\d/)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Appeler le 17' })).toHaveAttribute('href', 'tel:17');
  await page.screenshot({ path: 'e2e/.etat/ecran-14-session-expiree.png', fullPage: true });

  // Réauthentification complète
  await page.getByRole('link', { name: 'Se reconnecter' }).click();
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('link', { name: 'Mes enfants' })).toBeVisible();

  // La déconnexion efface la copie : sans session, il n'y a plus de fiche à montrer
  await page.getByRole('button', { name: /Se déconnecter/ }).click();
  await expect(page).toHaveURL(/\/connexion/);
  await page.goto('/');
  await expect(page).toHaveURL(/\/connexion/);
});
