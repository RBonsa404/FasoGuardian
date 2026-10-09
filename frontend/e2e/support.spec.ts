import { expect, test } from '@playwright/test';

import { MOT_DE_PASSE_PARENT, codeTotp, creerAgent, parentInscrit } from './aides';

const CONSOLE = process.env['FG_E2E_CONSOLE'] ?? 'http://localhost:4202';

/**
 * Support (US-PAR-017, US-SUP-001) : l'opérateur publie une réponse à une question fréquente, que le parent
 * trouve dans l'aide ; le parent ouvre une demande, l'opérateur y répond, le parent lit la réponse dans son compte.
 */
test('un opérateur publie un article et répond à la demande d’un parent, qui suit le tout depuis son compte', async ({ page, browser, request }) => {
  test.setTimeout(90_000);
  const { telephone } = await parentInscrit(request);
  const agent = await creerAgent(request, ['SUPPORT']);
  const marque = String(Date.now()).slice(-6);
  const titre = `Que faire si la sangle s'ouvre seule ? (${marque})`;

  // Console : l'opérateur se connecte, sur un poste de travail
  const contexteConsole = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const console_ = await contexteConsole.newPage();
  await console_.goto(`${CONSOLE}/`);
  await console_.getByLabel('Identifiant').fill(agent.identifiant);
  await console_.getByLabel('Mot de passe').fill(agent.motDePasse);
  await console_.getByRole('button', { name: 'Continuer' }).click();
  const secret = (await console_.locator('[data-secret]').textContent())!.trim();
  await console_.getByLabel('Code à 6 chiffres').fill(codeTotp(secret));
  await console_.getByRole('button', { name: 'Se connecter' }).click();
  const navigation = console_.getByRole('navigation', { name: 'Navigation principale' });
  await expect(navigation.getByRole('link')).toHaveText(['Tableau de bord', 'Tickets support', 'Base de connaissances']);

  // Écran 63 : rédaction, aperçu, publication
  await navigation.getByRole('link', { name: 'Base de connaissances' }).click();
  await console_.getByLabel('Titre').fill(titre);
  await console_.getByLabel('Contenu').fill('Le fermoir de sécurité doit produire un clic net.\n\nRendez-vous dans un point relais : la sangle est échangée gratuitement.');
  await expect(console_.getByRole('region', { name: 'Aperçu mobile' }).getByText('Rendez-vous dans un point relais')).toBeVisible();
  await console_.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(console_.getByRole('button', { name: 'Publier' })).toBeVisible();
  await console_.screenshot({ path: 'e2e/.etat/ecran-63-faq.png', fullPage: true });
  await console_.getByRole('button', { name: 'Publier' }).click();
  await expect(console_.getByRole('button', { name: 'Retirer de la publication' })).toBeVisible();

  // Parent : il trouve l'article dans l'aide
  await page.goto('/connexion');
  await page.getByLabel('Numéro mobile').fill(telephone);
  await page.getByLabel('Mot de passe').fill(MOT_DE_PASSE_PARENT);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.getByRole('link', { name: 'Aide et support' }).click();
  await expect(page.getByRole('heading', { name: 'Aide', exact: true })).toBeVisible();
  await page.getByRole('searchbox', { name: "Rechercher dans l'aide" }).fill(marque);
  await page.getByRole('searchbox', { name: "Rechercher dans l'aide" }).press('Enter');
  await page.screenshot({ path: 'e2e/.etat/ecran-46-aide.png', fullPage: true });
  await page.getByRole('link', { name: titre }).click();
  await expect(page.getByRole('heading', { name: titre })).toBeVisible();
  await expect(page.getByText('la sangle est échangée gratuitement')).toBeVisible();

  // Le parent écrit au support
  await page.getByRole('link', { name: 'Cela ne résout pas mon problème' }).click();
  await expect(page.getByText("Vous n'avez encore rien demandé au support.")).toBeVisible();
  await page.getByRole('button', { name: 'Écrire au support' }).click();
  const feuille = page.getByRole('dialog', { name: 'Écrire au support' });
  await feuille.getByLabel('Objet').fill(`La sangle se détache (${marque})`);
  await feuille.getByLabel('Votre message').fill('Depuis hier la sangle s’ouvre quand elle joue. Est-ce normal ?');
  await feuille.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByText(/En cours · SUP-\d{6}/)).toBeVisible();

  // Écran 62 : l'opérateur lit la demande et répond
  await navigation.getByRole('link', { name: 'Tickets support' }).click();
  await console_.getByRole('button', { name: new RegExp(`La sangle se détache \\(${marque}\\)`) }).click();
  await expect(console_.getByText(`+226${telephone}`)).toBeVisible();
  await expect(console_.getByText('Est-ce normal ?')).toBeVisible();
  await console_.getByLabel('Réponse au parent').fill('Ce n’est pas normal. Échange gratuit de sangle au point relais de Dassasgho.');
  await console_.screenshot({ path: 'e2e/.etat/ecran-62-tickets.png', fullPage: true });
  await console_.getByRole('button', { name: 'Répondre et attendre le parent' }).click();
  await expect(console_.getByRole('button', { name: new RegExp(`\\(${marque}\\)`) })).toHaveCount(0);

  // Écran 47 : le parent lit la réponse dans son compte et y répond
  await page.reload();
  await expect(page.getByText(/Votre réponse est attendue · SUP-\d{6}/)).toBeVisible();
  await expect(page.getByText('Échange gratuit de sangle au point relais de Dassasgho.')).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/ecran-47-demande.png', fullPage: true });
  await page.getByLabel('Votre réponse').fill('Merci, j’y passe demain.');
  await page.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByText(/En cours · SUP-\d{6}/)).toBeVisible();
  await expect(page.getByRole('listitem')).toHaveCount(3);
  await contexteConsole.close();
});
