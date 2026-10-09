// Dresse la liste des fichiers de l'application Parents que le service worker garde pour l'ouverture hors
// ligne (US-PAR-019). À lancer après « ng build parents » : les noms des fichiers changent à chaque construction.
import { readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const sortie = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist', 'parents', 'browser');
const GARDES = /\.(html|js|css|woff2|svg|png|webmanifest)$/;
// Le service worker ne se garde pas lui-même, et la liste non plus.
const EXCLUS = new Set(['sw.js', 'precache.json']);

function parcourir(dossier) {
  return readdirSync(dossier).flatMap((nom) => {
    const chemin = join(dossier, nom);
    return statSync(chemin).isDirectory() ? parcourir(chemin) : [chemin];
  });
}

const fichiers = parcourir(sortie)
  .map((chemin) => relative(sortie, chemin).replaceAll('\\', '/'))
  .filter((nom) => GARDES.test(nom) && !EXCLUS.has(nom))
  .sort()
  .map((nom) => `/${nom}`);

if (!fichiers.includes('/index.html')) {
  throw new Error(`Aucune page d'entrée dans ${sortie} : lancer « ng build parents » d'abord.`);
}
writeFileSync(join(sortie, 'precache.json'), JSON.stringify(fichiers));
console.log(`precache.json : ${fichiers.length} fichiers gardés pour l'ouverture hors ligne`);
