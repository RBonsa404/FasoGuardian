// Séquence d'images du bracelet en 3D (rotation puis vue éclatée), prise sur la page « Le bracelet » du site
// vitrine en faisant défiler la section pas à pas. Sert de « flipbook » aux plans produit du film.
// Usage : node communication/video/film/capturer-bracelet.mjs [images=90]
import { chromium } from '../../../frontend/node_modules/@playwright/test/index.mjs';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const IMAGES = Number(process.argv[2] ?? 90);
const SORTIE = join(import.meta.dirname, 'captures', 'bracelet');
mkdirSync(SORTIE, { recursive: true });

const nav = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await nav.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1.5, colorScheme: 'dark', locale: 'fr-FR' });
await page.goto('http://localhost:4203/bracelet');
await page.waitForLoadState('networkidle');
await page.waitForTimeout(4000);
// Plage de défilement de la section : de son haut à son bas moins la hauteur de l'écran.
const plage = await page.evaluate(() => {
  let e = document.querySelector('fg-bracelet-3d');
  while (e && e.getBoundingClientRect().height < window.innerHeight * 1.5) e = e.parentElement;
  const r = e.getBoundingClientRect();
  return { debut: r.top + window.scrollY - 64, fin: r.top + window.scrollY + r.height - window.innerHeight };
});
const scene = page.locator('fg-bracelet-3d');
for (let i = 0; i < IMAGES; i++) {
  const y = plage.debut + ((plage.fin - plage.debut) * i) / (IMAGES - 1);
  await page.evaluate((y) => window.scrollTo(0, y), y);
  await page.waitForTimeout(350);
  await scene.screenshot({ path: join(SORTIE, `${String(i).padStart(3, '0')}.jpg`), type: 'jpeg', quality: 90 });
}
console.log(`${IMAGES} images, défilement ${Math.round(plage.debut)} → ${Math.round(plage.fin)} px`);
await nav.close();
