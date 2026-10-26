// Imprime le guide en PDF (A4, numéros de page) avec le Chromium de Playwright installé pour le frontend.
// Usage : node materiel/carte-v1/guide/imprimer.mjs — après guide/plans.py et conception/fabriquer.py (rendus 3D).
import { chromium } from '../../../frontend/node_modules/@playwright/test/index.mjs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const ICI = import.meta.dirname;
const SORTIE = join(ICI, 'guide-commande-et-mise-en-service.pdf');
const navigateur = await chromium.launch();
const page = await navigateur.newPage();
await page.goto(pathToFileURL(join(ICI, 'guide.html')).href, { waitUntil: 'load' });
await page.pdf({
  path: SORTIE,
  format: 'A4',
  printBackground: true,
  preferCSSPageSize: true,
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate: '<div style="width:100%;font:7.5pt Segoe UI,Arial,sans-serif;color:#57606a;padding:0 15mm;display:flex;'
    + 'justify-content:space-between"><span>FasoGuardian V1 — commande et mise en service</span>'
    + '<span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
});
await navigateur.close();
console.log('guide imprimé :', SORTIE);
