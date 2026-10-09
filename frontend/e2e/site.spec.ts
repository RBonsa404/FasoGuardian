import { expect, test } from '@playwright/test';

const SITE = process.env['FG_E2E_SITE'] ?? 'http://localhost:4203';

/** Site vitrine (écrans 1 à 8) : rendu côté serveur, navigation, bracelet en trois dimensions, repli. */
test('le site se lit sans script, puis présente le bracelet, les offres et les pages d’aide', async ({ page, request }) => {
  // Rendu côté serveur : le contenu est dans la page avant tout script
  const accueil = await (await request.get(`${SITE}/`)).text();
  expect(accueil).toContain("Toujours près d'eux. Jamais sur leur dos.");
  expect(accueil).toContain('Trois étapes, une seule fois.');
  expect(await (await request.get(`${SITE}/confidentialite`)).text()).toContain('Journal des alertes : 5 ans.');

  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${SITE}/`);
  await expect(page.getByRole('heading', { level: 1, name: "Toujours près d'eux. Jamais sur leur dos." })).toBeVisible();
  const navigation = page.getByRole('banner').getByRole('navigation', { name: 'Navigation principale' });
  await expect(navigation.getByRole('link')).toHaveText(['Le bracelet', 'Fonctionnement', 'Offres', 'Écoles', 'Points relais', 'Espace parents']);
  // three.js n'est chargé qu'à l'apparition de la scène : le bracelet remplace alors l'illustration fixe
  const scene = page.getByRole('img', { name: /Bracelet FasoGuardian en trois dimensions/ });
  await expect(scene).toBeVisible();
  await expect(scene).not.toHaveClass(/opacity-0/, { timeout: 15_000 });
  await page.screenshot({ path: 'e2e/.etat/site-01-accueil.png' });
  await page.screenshot({ path: 'e2e/.etat/site-01-accueil-entier.png', fullPage: true });

  // Écran 2 : le défilement ouvre la vue éclatée et met en avant la pièce correspondante
  await navigation.getByRole('link', { name: 'Le bracelet' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Robuste, simple, sans écran.' })).toBeVisible();
  await expect(page.getByRole('img', { name: /Vue éclatée du bracelet/ })).not.toHaveClass(/opacity-0/, { timeout: 15_000 });
  await page.mouse.wheel(0, 700);
  await expect(page.locator('ol li.bg-accent-soft')).toHaveCount(1);
  await page.screenshot({ path: 'e2e/.etat/site-02-bracelet.png' });
  await page.getByRole('group', { name: 'Coloris du bracelet' }).getByRole('button', { name: 'Menthe' }).click();
  await expect(page.getByRole('button', { name: 'Menthe' })).toHaveAttribute('aria-pressed', 'true');

  // Écrans 4 à 8
  await page.goto(`${SITE}/offres`);
  await expect(page.getByRole('columnheader', { name: 'Intermédiaire' })).toBeVisible();
  await page.screenshot({ path: 'e2e/.etat/site-04-offres.png', fullPage: true });
  await page.goto(`${SITE}/faq`);
  await page.getByRole('button', { name: 'Qui peut voir la position ?' }).click();
  await expect(page.getByText("Aucun agent de FasoGuardian ne voit la position d'un enfant.")).toBeVisible();
  await page.goto(`${SITE}/une-page-qui-n-existe-pas`);
  await expect(page.getByRole('heading', { name: "Cette page n'existe pas." })).toBeVisible();
  await page.getByRole('link', { name: "Retour à l'accueil" }).click();
  await expect(page).toHaveURL(`${SITE}/`);
});

test('sur téléphone la navigation se replie, et sans animations le bracelet reste une illustration', async ({ browser }) => {
  const contexte = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce', colorScheme: 'light' });
  const page = await contexte.newPage();
  await page.goto(`${SITE}/`);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  // Animations réduites : three.js n'est pas chargé, l'illustration fixe tient la place
  await page.waitForTimeout(1500);
  await expect(page.getByRole('img', { name: /Bracelet FasoGuardian en trois dimensions/ })).toHaveClass(/opacity-0/);
  expect(await page.evaluate(() => performance.getEntriesByType('resource').some((r) => /three/.test(r.name)))).toBe(false);
  await page.screenshot({ path: 'e2e/.etat/site-01-accueil-telephone-clair.png', fullPage: true });

  await page.getByRole('button', { name: 'Menu' }).click();
  await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Offres' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'SOS, retrait et page QR inclus partout.' })).toBeVisible();
  await contexte.close();
});
