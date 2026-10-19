import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, ongletReglages, onglets, parentAvecEnfant } from './aides';

/**
 * File locale des actions de configuration (FG-DOC-06 §5.4, US-SYS-002) : une modification faite sans réseau est
 * gardée sur le téléphone, annoncée en tête d'écran, puis envoyée une seule fois au retour du réseau.
 */
test('une modification faite hors ligne part au retour du réseau', async ({ page, context, request }) => {
  const { telephone } = await parentAvecEnfant(request);
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(onglets(page).getByRole('link', { name: 'Carte' })).toBeVisible();
  await ongletReglages(page);
  await page.getByRole('link', { name: 'Mes enfants' }).click();
  await page.getByRole('link', { name: /Yacouba · \d+ ans/ }).click();
  await expect(page.getByRole('heading', { name: 'Yacouba Zongo' })).toBeVisible();

  // Sans réseau, l'enregistrement est gardé et le parent sait qu'il partira plus tard.
  await context.setOffline(true);
  await page.getByRole('button', { name: 'Modifier la fiche' }).click();
  const feuille = page.getByRole('dialog', { name: 'Modifier la fiche' });
  await feuille.getByLabel('École').fill('Les Manguiers · CM1');
  await feuille.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(feuille.getByText("Pas de connexion : c'est enregistré sur ce téléphone et sera envoyé dès le retour du réseau.")).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByText("1 action sera envoyée dès le retour du réseau : fiche de l'enfant.")).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: 'e2e/.etat/ecran-49-actions-en-attente.png' });

  // Retour du réseau : l'action part d'elle-même et le bandeau disparaît.
  const envoi = page.waitForResponse((reponse) => reponse.request().method() === 'PATCH' && reponse.request().headers()['idempotency-key'] !== undefined);
  await context.setOffline(false);
  expect((await envoi).status()).toBe(200);
  await expect(page.getByText('sera envoyée dès le retour du réseau')).toHaveCount(0);
  await page.reload();
  await expect(page.getByText('Les Manguiers · CM1')).toBeVisible();

  // Consultation sans réseau : la fiche déjà lue revient de l'appareil, et le bandeau dit de quand elle date.
  await page.getByRole('link', { name: 'Tuteurs et contacts' }).click();
  await expect(page.getByRole('button', { name: 'Ajouter un contact' })).toBeVisible();
  await context.setOffline(true);
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Yacouba Zongo' })).toBeVisible();
  await expect(page.getByText('Les Manguiers · CM1')).toBeVisible();
  await expect(page.getByText(/Hors ligne · données gardées à \d{2}:\d{2}\. La fiche, les contacts et la dernière position restent consultables\./)).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: 'e2e/.etat/ecran-49-hors-ligne.png' });
  await context.setOffline(false);
});
