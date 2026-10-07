// Importe les ressources du paquet de design dans la bibliothèque ui :
// jetons Tailwind, logos et icônes (métadonnées de provenance retirées pour tenir les budgets de poids).
// Usage : node tools/importer-design.mjs <chemin vers Design/project/handoff>
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const source = process.argv[2];
if (!source) {
  console.error('Usage : node tools/importer-design.mjs <chemin vers handoff>');
  process.exit(1);
}

const racine = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ui = join(racine, 'projects', 'ui');

const nettoyer = (svg) =>
  svg
    .replace(/<metadata>[\s\S]*?<\/metadata>/g, '')
    .replace(/\s+xmlns:c2pa="[^"]*"/g, '')
    .trim();

const ecrire = (chemin, contenu) => {
  mkdirSync(dirname(chemin), { recursive: true });
  writeFileSync(chemin, contenu.endsWith('\n') ? contenu : contenu + '\n', 'utf8');
};

// Jetons : repris à la lettre du paquet de transmission.
ecrire(join(ui, 'src', 'styles', 'tokens.css'), readFileSync(join(source, 'tokens.css'), 'utf8').replace(/\r\n/g, '\n'));

// Logos et icône PWA.
for (const dossier of ['logo', 'pwa']) {
  for (const fichier of readdirSync(join(source, 'assets', dossier))) {
    ecrire(
      join(ui, 'assets', dossier, fichier),
      nettoyer(readFileSync(join(source, 'assets', dossier, fichier), 'utf8')),
    );
  }
}

// Icônes : registre TypeScript (contenu interne de chaque SVG 24 px, trait 1,75).
const dossierIcones = join(source, 'assets', 'icons');
const icones = {};
for (const fichier of readdirSync(dossierIcones).sort()) {
  const svg = nettoyer(readFileSync(join(dossierIcones, fichier), 'utf8'));
  const interieur = svg.slice(svg.indexOf('>') + 1, svg.lastIndexOf('</svg>')).trim();
  icones[basename(fichier, '.svg')] = interieur;
  ecrire(join(ui, 'assets', 'icones', fichier), svg);
}

icones.info = '<circle cx="12" cy="12" r="9"></circle><path d="M12 8v5M12 16v.5"></path>';

const lignes = Object.entries(icones)
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([nom, contenu]) => `  '${nom}': ${JSON.stringify(contenu)},`)
  .join('\n');
ecrire(
  join(ui, 'src', 'lib', 'icone', 'icones.ts'),
  `// Fichier généré par tools/importer-design.mjs à partir du paquet de design. Ne pas modifier à la main.
export const ICONES = {
${lignes}
} as const;

export type NomIcone = keyof typeof ICONES;
`,
);

console.log(`Design importé : jetons, logos et ${Object.keys(icones).length} icônes.`);
