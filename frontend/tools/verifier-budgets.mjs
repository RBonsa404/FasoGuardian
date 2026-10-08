// Vérifie les budgets de poids transféré (gzip) du chargement initial. Bloquant en intégration continue.
//   parents   : 250 Ko maximum (FG-DOC-06, tableau 9 ; REQ-SYS-009)
//   public-qr : 60 Ko maximum, tout compris (REQ-SYS-014)
// Usage : node tools/verifier-budgets.mjs [application...]   (après ng build ; toutes les applications par défaut)
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const BUDGETS_KO = { parents: 250, 'public-qr': 60 };

const racine = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const poidsGzip = (chemin) => gzipSync(readFileSync(chemin), { level: 9 }).length;

const demandees = process.argv.slice(2);
const inconnues = demandees.filter((nom) => !(nom in BUDGETS_KO));
if (inconnues.length > 0) {
  console.error(`Application sans budget défini : ${inconnues.join(', ')}`);
  process.exit(1);
}

let echec = false;
for (const [application, budgetKo] of Object.entries(BUDGETS_KO)) {
  if (demandees.length > 0 && !demandees.includes(application)) {
    continue;
  }
  const dossier = join(racine, 'dist', application, 'browser');
  const page = ['index.html', 'index.csr.html'].map((nom) => join(dossier, nom)).find(existsSync);
  if (!page) {
    console.error(`✗ ${application} : aucune page d'entrée dans ${dossier} (lancer ng build ${application})`);
    echec = true;
    continue;
  }

  const html = readFileSync(page, 'utf8');
  const ressources = new Set();
  for (const [, balise] of html.matchAll(/<(script[^>]*\ssrc="[^"]+"|link[^>]*\shref="[^"]+")[^>]*>/g)) {
    const estInitiale = balise.startsWith('script') || /rel="(stylesheet|modulepreload)"/.test(balise);
    const url = balise.match(/(?:src|href)="([^"]+)"/)[1];
    if (estInitiale && !/^(https?:)?\/\//.test(url)) {
      ressources.add(url.replace(/^\//, ''));
    }
  }

  // Les polices et images appelées par les feuilles de style initiales font partie du chargement initial.
  for (const feuille of [...ressources].filter((ressource) => ressource.endsWith('.css'))) {
    const css = readFileSync(join(dossier, feuille), 'utf8');
    for (const [, url] of css.matchAll(/url\(["']?([^"')]+)["']?\)/g)) {
      if (!url.startsWith('data:') && !/^(https?:)?\/\//.test(url)) {
        ressources.add(join(dirname(feuille), url).split(sep).join('/').replace(/^\.?\//, ''));
      }
    }
  }

  let total = poidsGzip(page);
  for (const ressource of ressources) {
    total += poidsGzip(join(dossier, ressource));
  }

  const totalKo = total / 1000;
  const ok = totalKo <= budgetKo;
  echec ||= !ok;
  console.log(
    `${ok ? '✓' : '✗'} ${application} : ${totalKo.toFixed(1)} Ko compressés au chargement initial ` +
      `(${ressources.size + 1} fichiers, budget ${budgetKo} Ko)`,
  );
}

process.exit(echec ? 1 : 0);
