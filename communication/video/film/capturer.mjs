// Captures des écrans réels de l'application des parents, au format téléphone, sur la pile de démonstration.
// Prérequis : `sh frontend/e2e/pile.sh demarrer` puis `sh frontend/e2e/pile.sh demo`.
// Usage : node communication/video/film/capturer.mjs <theme: clair|sombre> [écrans...] (tous par défaut)
import { chromium } from '../../../frontend/node_modules/@playwright/test/index.mjs';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const [theme = 'sombre', ...choisis] = process.argv.slice(2);
const demo = JSON.parse(readFileSync(join(import.meta.dirname, '../../../frontend/e2e/.etat/demo.json'), 'utf8'));
const BASE = 'http://localhost:4201';
const SORTIE = join(import.meta.dirname, 'captures');

/** Écrans du film : nom du fichier → chemin (« :enfant » = premier enfant du compte). */
const ECRANS = {
  accueil: '/',
  carte: '/enfants/:enfant/carte',
  zones: '/enfants/:enfant/zones',
  qr: '/enfants/:enfant/qr',
  // Liste des alertes avant le SOS (sortie de la Safe Zone « Maison »), copiée en alertes-zone pour le plan 6.
  alertes: '/alertes',
  // « @ » : ouvre l'écran puis touche la première carte d'alerte en cours (le SOS, après son déclenchement).
  'detail-alerte': '@/alertes',
};

const nav = await chromium.launch();
const contexte = await nav.newContext({
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
  colorScheme: theme === 'clair' ? 'light' : 'dark',
  locale: 'fr-FR',
  timezoneId: 'Africa/Ouagadougou',
});
const page = await contexte.newPage();
await page.goto(BASE + '/connexion');
await page.locator('input[type=tel]').fill(demo.parents.actif);
await page.locator('input[type=password]').fill(demo.parents.motDePasse);
await page.keyboard.press('Enter');
await page.waitForURL(BASE + '/');
await page.goto(BASE + '/enfants');
await page.locator('a[href^="/enfants/"]').first().click();
await page.waitForURL(/\/enfants\/[0-9a-f-]{36}/);
const enfant = page.url().match(/[0-9a-f-]{36}/)[0];

for (const [nom, chemin] of Object.entries(ECRANS)) {
  if (choisis.length && !choisis.includes(nom)) continue;
  await page.goto(BASE + chemin.replace('@', '').replace(':enfant', enfant));
  await page.waitForLoadState('networkidle');
  if (chemin.startsWith('@')) {
    await page.locator('a[href^="/alertes/"]').first().click();
    await page.waitForLoadState('networkidle');
  }
  await page.waitForTimeout(4000);
  await page.screenshot({ path: join(SORTIE, `${nom}-${theme}.png`) });
  console.log('capturé', nom);
}

await nav.close();
