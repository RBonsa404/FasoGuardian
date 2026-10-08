// Transforme la page public-qr pré-rendue par Angular en gabarit HTML autonome pour le serveur (ADR 0006) :
// feuille de style en ligne, aucun script, aucun attribut technique d'Angular. Le serveur Spring remplit
// les balises fg-etat / fg-si / fg-pour et les marqueurs [[nom]] à chaque requête.
//
// Usage : node tools/gabarit-qr.mjs            écrit backend/src/main/resources/gabarits/page-qr.html
//         node tools/gabarit-qr.mjs --verifier échoue si le gabarit versionné n'est pas à jour (CI)
import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const BUDGET_KO = 60;

const racine = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = join(racine, 'dist', 'public-qr', 'browser');
const cible = resolve(racine, '..', 'backend', 'src', 'main', 'resources', 'gabarits', 'page-qr.html');

let html = readFileSync(join(dist, 'index.html'), 'utf8');
const css = readdirSync(dist)
  .filter((nom) => nom.endsWith('.css'))
  .map((nom) => readFileSync(join(dist, nom), 'utf8'))
  .join('');

html = html
  .replace(/<script\b[\s\S]*?<\/script>/g, '')
  .replace(/<style\b[\s\S]*?<\/style>/g, '')
  .replace(/<noscript>[\s\S]*?<\/noscript>/g, '')
  .replace(/<base [^>]*>/g, '')
  .replace(/<link\b[^>]*rel="(?:stylesheet|modulepreload|preload)"[^>]*>/g, '')
  .replace(/<!--[\s\S]*?-->/g, '')
  .replace(/\s(?:_ngcontent|_nghost|ng-reflect)[\w-]*(?:="[^"]*")?/g, '')
  .replace(/\s(?:ng-version|ng-server-context|ngh|ngskiphydration)(?:="[^"]*")?/g, '')
  .replace('</head>', `<style>${css}</style></head>`)
  .replace(/>\s+</g, '><')
  .trim();

// Aucun script et aucune ressource externe : la page ne déclenche que sa propre requête.
for (const interdit of ['<script', 'src="http', 'href="http', 'url(http', '@import', 'ng-version']) {
  if (html.includes(interdit)) {
    console.error(`Le gabarit contient encore « ${interdit} ».`);
    process.exit(1);
  }
}
const poidsKo = gzipSync(Buffer.from(html, 'utf8'), { level: 9 }).length / 1000;
if (poidsKo > BUDGET_KO) {
  console.error(`✗ gabarit de la page QR : ${poidsKo.toFixed(1)} Ko compressés, budget ${BUDGET_KO} Ko`);
  process.exit(1);
}

const contenu = html + '\n';
if (process.argv.includes('--verifier')) {
  let actuel = '';
  try {
    actuel = readFileSync(cible, 'utf8');
  } catch {
    // Gabarit absent : traité comme périmé.
  }
  if (actuel !== contenu) {
    console.error('✗ backend/src/main/resources/gabarits/page-qr.html n’est pas à jour : relancer npm run gabarit:qr');
    process.exit(1);
  }
} else {
  mkdirSync(dirname(cible), { recursive: true });
  writeFileSync(cible, contenu, 'utf8');
}
console.log(`✓ gabarit de la page QR : ${poidsKo.toFixed(1)} Ko compressés, aucun script (budget ${BUDGET_KO} Ko)`);
