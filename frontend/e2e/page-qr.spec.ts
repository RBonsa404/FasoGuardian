import { expect, test } from '@playwright/test';

import { SERVEUR } from './aides';

const BUDGET_OCTETS = 60_000;

test.use({ javaScriptEnabled: false, viewport: { width: 360, height: 800 } });

/**
 * Page publique QR (US-TRS-001, US-SYS-010) : rendue par le serveur, utilisable sans JavaScript, une seule
 * requête, moins de 60 Ko. Les états « enfant trouvé » et « bracelet désactivé » sont couverts par
 * PagePubliqueQrIT côté serveur ; ils rejoindront ce fichier avec l'appairage des bracelets.
 */
test('un code inconnu affiche la page générique, sans script, en une seule requête de moins de 60 Ko', async ({ page }) => {
  const requetes: string[] = [];
  page.on('request', (requete) => requetes.push(requete.url()));

  const reponse = await page.goto(`${SERVEUR}/q/AAAAAAAAAAAAAAAAAAAAAA`);

  expect(reponse!.status()).toBe(200);
  expect(reponse!.headers()['content-security-policy']).toContain("default-src 'none'");
  expect(reponse!.headers()['cache-control']).toBe('no-store');
  await expect(page.getByRole('heading', { name: "Ce code n'est pas reconnu" })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Appeler le 17' })).toHaveAttribute('href', 'tel:17');
  await expect(page.locator('script')).toHaveCount(0);
  await expect(page.getByText(/Vous avez trouvé un enfant/)).toHaveCount(0);

  const corps = await reponse!.body();
  expect(corps.length).toBeLessThan(BUDGET_OCTETS);
  expect(requetes).toHaveLength(1);

  // Le style en ligne est bien appliqué : fond du thème et bouton d'appel à la couleur principale.
  const bouton = page.getByRole('link', { name: 'Appeler le 17' });
  expect(await bouton.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe('rgba(0, 0, 0, 0)');
  expect((await bouton.boundingBox())!.height).toBeGreaterThanOrEqual(44);
});
