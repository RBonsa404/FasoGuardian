// Produit les icônes PNG de la PWA Parents (192, 512, apple-touch 180) à partir de l'icône masquable du
// paquet de design (HANDOFF §11). À relancer si le fichier source change : npm run icones:pwa
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = readFileSync(join(racine, '..', '..', '..', 'Design', 'project', 'handoff', 'assets', 'pwa', 'icon-maskable.svg'), 'utf8');
const sortie = join(racine, 'projects', 'parents', 'public');
const tailles = { 'icone-192.png': 192, 'icone-512.png': 512, 'apple-touch-icon.png': 180 };

const navigateur = await chromium.launch();
for (const [nom, taille] of Object.entries(tailles)) {
  const page = await navigateur.newPage({ viewport: { width: taille, height: taille }, deviceScaleFactor: 1 });
  await page.setContent(`<style>html,body{margin:0}svg{display:block;width:${taille}px;height:${taille}px}</style>${source}`);
  await page.screenshot({ path: join(sortie, nom), omitBackground: false });
  await page.close();
}
await navigateur.close();
console.log(`Icônes PWA générées dans ${sortie}`);
