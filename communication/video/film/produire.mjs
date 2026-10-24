// Production du film de présentation à partir de scenario.js :
//   1. contrôle du minutage (chaque réplique doit tenir dans son plan) ;
//   2. sous-titres SRT et VTT, texte de voix off minuté, consigne pour l'outil de synthèse vocale ;
//   3. rendu image par image de film.html (120 i/s par défaut), réparti sur plusieurs navigateurs ; chacun encode son
//      segment par ffmpeg au fil de l'eau (aucune image sur disque), puis les segments sont mis bout à bout ;
//   4. versions à sous-titres incrustés (120 i/s, et 60 i/s pour les réseaux sociaux, plafonnés à 60) et vignette.
// Usage : node communication/video/film/produire.mjs [textes|film|apercu] [--ips=120] [--rendeurs=4]
//   textes : étapes 1 et 2 seulement ; apercu : planche d'une image par plan (une par plan) ; film (défaut) : tout ;
//   soustitres : étape 4 seule, sur le film déjà rendu.
//   Le corps du film est gardé dans sortie/corps-<ips>ips.mp4 : seul le générique est rendu à nouveau, sauf avec
//   --refaire (à passer après toute modification d'un autre plan que le générique).
// Prérequis : ffmpeg dans le PATH, dépendances du frontend installées (Playwright).
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { AMORCE, DEBIT, DUREE_TOTALE, PLANS, debuts } from './scenario.js';

const ICI = import.meta.dirname;
const VIDEO = join(ICI, '..');
const SORTIE = join(VIDEO, 'sortie');
const FRONTEND = join(ICI, '../../../frontend');
const option = (nom, defaut) => Number(process.argv.find((a) => a.startsWith(`--${nom}=`))?.split('=')[1] ?? defaut);
const IPS = option('ips', 120);
const RENDEURS = option('rendeurs', 4);
const LIGNE = 42; // caractères par ligne de sous-titre
const mode = process.argv.slice(2).find((a) => !a.startsWith('--')) ?? 'film';

// ---------------------------------------------------------------- 1. Minutage

const DEBUTS = debuts();
const mots = (texte) => texte.split(/\s+/).filter((m) => /[\p{L}\d]/u.test(m)).length;
const parole = (plan) => mots(plan.voix) / DEBIT;
const PARLES = PLANS.filter((p) => p.voix);
for (const plan of PARLES) {
  const besoin = AMORCE + parole(plan) + 0.2;
  if (besoin > plan.duree) throw new Error(`Plan « ${plan.id} » : ${besoin.toFixed(1)} s de voix pour ${plan.duree} s de plan.`);
}
const DUREE_PARLEE = PARLES.reduce((s, p) => s + p.duree, 0);
if (DUREE_PARLEE < 80 || DUREE_PARLEE > 100) throw new Error(`Film sans le générique : ${DUREE_PARLEE} s, hors de la plage 80 à 100 s.`);

// ---------------------------------------------------------------- 2. Textes

/** Découpe une réplique en sous-titres d'au plus deux lignes de 42 caractères. */
function decouper(texte) {
  const phrases = fusionner(texte.split(/(?<=[.?!])\s+/));
  const blocs = [];
  for (const phrase of phrases) {
    if (phrase.length <= 2 * LIGNE) {
      blocs.push(phrase);
      continue;
    }
    // Coupure à la virgule la plus proche du milieu, sinon à l'espace.
    const milieu = phrase.length / 2;
    const coupures = [...phrase.matchAll(/, /g)].map((m) => m.index + 1);
    const espaces = [...phrase.matchAll(/ /g)].map((m) => m.index);
    const i = (coupures.length ? coupures : espaces).reduce((a, b) => (Math.abs(b - milieu) < Math.abs(a - milieu) ? b : a));
    blocs.push(...decouper(phrase.slice(0, i).trim()), ...decouper(phrase.slice(i).trim()));
  }
  return blocs;
}

/** Rattache une phrase très courte (trois mots au plus, « FasoGuardian. ») à la suivante quand le tout tient sur deux lignes. */
function fusionner(phrases) {
  const res = [];
  for (const p of phrases) {
    const prec = res.at(-1);
    if (prec !== undefined && prec.split(" ").length <= 3 && prec.length + 1 + p.length <= 2 * LIGNE) res[res.length - 1] = `${prec} ${p}`;
    else res.push(p);
  }
  return res;
}

/** Répartit un bloc sur deux lignes équilibrées. */
function lignes(bloc) {
  if (bloc.length <= LIGNE) return bloc;
  const milieu = bloc.length / 2;
  const espaces = [...bloc.matchAll(/ /g)].map((m) => m.index);
  const i = espaces.reduce((a, b) => (Math.abs(b - milieu) < Math.abs(a - milieu) ? b : a));
  return `${bloc.slice(0, i)}\n${bloc.slice(i + 1)}`;
}

const repliques = PLANS.flatMap((plan, n) => {
  if (!plan.voix) return [];
  // Le minutage suit les mots dits, phrase par phrase ; une phrase affichée trop longue est coupée et sa durée
  // répartie au prorata des caractères.
  const phrases = (texte) => fusionner(texte.split(/(?<=[.?!])\s+/));
  const dites = phrases(plan.voix);
  const affichees = plan.ecran ? phrases(plan.ecran) : dites;
  if (affichees.length !== dites.length) throw new Error(`Plan « ${plan.id} » : la voix et le texte affiché n'ont pas les mêmes phrases.`);
  const blocs = affichees.flatMap((phrase, k) => {
    const morceaux = decouper(phrase);
    const total = morceaux.reduce((s, m) => s + m.length, 0);
    return morceaux.map((m) => ({ texte: m, duree: ((mots(dites[k]) / DEBIT) * m.length) / total }));
  });
  let t = DEBUTS[n] + AMORCE;
  return blocs.map(({ texte: bloc, duree }, k) => {
    const debut = t;
    t += mots(bloc) <= 2 ? Math.max(duree, 1.0) : duree; // un mot isolé reste lisible (la voix marque une pause)
    const fin = k === blocs.length - 1 ? Math.min(DEBUTS[n] + plan.duree - 0.1, t + 0.6) : t;
    return { debut, fin, texte: lignes(bloc) };
  });
});

const horodatage = (s, sep) => {
  const ms = Math.round(s * 1000);
  const h = String(Math.floor(ms / 3_600_000)).padStart(2, '0');
  const m = String(Math.floor(ms / 60_000) % 60).padStart(2, '0');
  const sec = String(Math.floor(ms / 1000) % 60).padStart(2, '0');
  return `${h}:${m}:${sec}${sep}${String(ms % 1000).padStart(3, '0')}`;
};
/** Nombre à la française (virgule décimale), pour les documents. */
const fr = (x, d = 1) => x.toFixed(d).replace('.', ',');
const court = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${(s % 60).toFixed(1).padStart(4, '0').replace('.', ',')}`;

for (const r of repliques) {
  if (r.texte.split('\n').some((l) => l.length > LIGNE + 4)) throw new Error(`Sous-titre trop long : ${r.texte}`);
}
writeFileSync(
  join(VIDEO, 'sous-titres.fr.srt'),
  repliques.map((r, i) => `${i + 1}\n${horodatage(r.debut, ',')} --> ${horodatage(r.fin, ',')}\n${r.texte}\n`).join('\n'),
);
writeFileSync(
  join(VIDEO, 'sous-titres.fr.vtt'),
  'WEBVTT\n\n' + repliques.map((r) => `${horodatage(r.debut, '.')} --> ${horodatage(r.fin, '.')}\n${r.texte}\n`).join('\n'),
);

// Même texte au format ASS, avec le style de l'incrustation : police système Segoe UI (libass ne lit pas les
// polices web), boîte sombre semi-opaque aux couleurs de la marque, bas de l'image.
const assTemps = (s) => {
  const cs = Math.round(s * 100);
  return `${Math.floor(cs / 360000)}:${String(Math.floor(cs / 6000) % 60).padStart(2, '0')}:${String(Math.floor(cs / 100) % 60).padStart(2, '0')}.${String(cs % 100).padStart(2, '0')}`;
};
writeFileSync(
  join(VIDEO, 'sous-titres.fr.ass'),
  `[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
WrapStyle: 2

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: FasoGuardian,Segoe UI Semibold,46,&H00FAF5F3,&H00FAF5F3,&H331A0E0A,&H331A0E0A,0,0,0,0,100,100,0,0,3,14,0,2,120,120,56,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
${repliques.map((r) => `Dialogue: 0,${assTemps(r.debut)},${assTemps(r.fin)},FasoGuardian,,0,0,0,,${r.texte.replace(/\n/g, '\\N')}`).join('\n')}
`,
);

const TITRES = {
  ouverture: 'Ouverture', question: 'La question', revelation: 'Révélation', bracelet: 'Le bracelet', position: 'La position',
  zones: 'Les Safe Zones', sos: 'Le SOS', reseau: 'Le réseau', qr: 'La page QR', confidentialite: 'La confidentialité',
  offres: 'Les offres', fin: 'Signature', generique: 'Générique',
};

writeFileSync(
  join(VIDEO, 'voix-off.md'),
  `# Voix off — film de présentation FasoGuardian

Texte généré depuis \`film/scenario.js\` (ne pas modifier à la main : changer le scénario puis relancer
\`node communication/video/film/produire.mjs textes\`). Durée du film : ${String(DUREE_TOTALE).replace(".", ",")} s. Débit visé : ${String(DEBIT).replace(".", ",")} mots par
seconde, soit une voix posée de publicité. Chaque réplique commence ${fr(AMORCE)} s après le début de son plan.

## Réplique par réplique

| # | Plan | Entrée | Sortie | Voix | Budget |
|---|---|---|---|---|---|
${PLANS.map((p, n) => p.voix && `| ${n + 1} | ${TITRES[p.id]} | ${court(DEBUTS[n] + AMORCE)} | ${court(DEBUTS[n] + p.duree)} | ${p.voix} | ${mots(p.voix)} mots en ${fr(p.duree - AMORCE - 0.2)} s au plus |`).filter(Boolean).join('\n')}

Le plan ${PLANS.length}, ${TITRES[PLANS.at(-1).id].toLowerCase()} (${court(DEBUTS.at(-1))} à ${court(DUREE_TOTALE)}), est sans voix : la
musique seule le porte.

## Intentions de jeu

${PLANS.map((p, n) => `${n + 1}. **${TITRES[p.id]}** (${court(DEBUTS[n])}) — ${p.intention}`).join('\n')}

## Prononciation

| Écrit | Dire |
|---|---|
| FasoGuardian | « Fa-so-gar-dianne », accent sur « Fa-so » |
| Ouagadougou | « Wa-ga-dou-gou », quatre syllabes égales |
| Safe Zones | « Sèf zônes » |
| SOS | « S-O-S », lettre par lettre |
| QR code | « Q-R code » (« ku-èr code ») |
| 4G, 2G, SMS | « quatre-G », « deux-G », « S-M-S » |
| FCFA | « francs CFA » (« cé-èf-a ») |

## Montage

1. Générer une piste par réplique (12 fichiers) plutôt qu'une prise unique : chacune se pose à son « Entrée »
   ci-dessus, ce qui garde la synchronisation même si l'outil parle un peu plus vite ou plus lentement.
2. Si une réplique dépasse son budget, la régénérer avec une vitesse de 1,05 à 1,1 plutôt que de la couper.
3. Musique : nappe acoustique sans parole (kora ou balafon discret, puis pulsation légère à partir du plan 3), à −18 dB
   sous la voix, remontée à −10 dB sur la signature (plan 12). Vérifier la licence de diffusion commerciale.
4. Mixage : voix à −14 LUFS intégrés pour les réseaux sociaux, crête à −1 dBTP.
5. Les sous-titres \`sous-titres.fr.srt\` sont calés sur ce minutage : réimporter le SRT si une réplique est décalée.
`,
);

const avecPauses = PARLES.map((p, n) => {
  const reste = p.duree - AMORCE - parole(p);
  return `${p.voix}${n < PARLES.length - 1 ? ` <break time="${Math.min(3, reste + AMORCE).toFixed(1)}s" />` : ''}`;
}).join('\n\n');

writeFileSync(
  join(VIDEO, 'consigne-voix.md'),
  `# Consigne pour l'outil de synthèse vocale

Texte généré depuis \`film/scenario.js\`. Coller la **consigne de voix** dans le champ de description (ou de « prompt »)
de l'outil, puis le **texte** réplique par réplique.

## Consigne de voix (à coller telle quelle)

> Voix off d'une publicité pour FasoGuardian, un bracelet de sécurité pour enfants conçu à Ouagadougou. Locutrice ou
> locuteur burkinabè de 30 à 40 ans, français d'Afrique de l'Ouest à l'accent naturel et léger, diction claire. Ton
> chaleureux, rassurant et confiant, comme un parent qui conseille un autre parent : jamais alarmiste, jamais
> commercial ou criard. Débit posé d'environ ${String(DEBIT).replace(".", ",")} mots par seconde, phrases courtes, respirations audibles entre les
> phrases. Sourire dans la voix sur les passages positifs, gravité retenue sur « en cas de danger ». Les chiffres sont
> écrits en toutes lettres et doivent être dits tels quels. Prononcer « Ouagadougou » « Wa-ga-dou-gou », « SOS » et
> « SMS » lettre par lettre, « Safe Zones » « sèf zônes », « FCFA » « francs CFA ».

## Réglages conseillés

| Réglage | Valeur | Pourquoi |
|---|---|---|
| Stabilité | 45 à 55 % | Assez de variation pour sonner humain, sans dérive d'une réplique à l'autre |
| Similarité ou clarté | 75 % | Garde le timbre choisi sur les 12 répliques |
| Style ou exagération | 15 à 25 % | Chaleur sans emphase publicitaire |
| Vitesse | 1,0 (1,05 si une réplique dépasse son budget) | Le minutage du film suppose ${String(DEBIT).replace(".", ",")} mots par seconde |
| Format | WAV 48 kHz, ou MP3 320 kb/s | Qualité de montage |

## Texte, réplique par réplique (méthode conseillée)

${PLANS.map((p, n) => p.voix && `${n + 1}. *(${court(DEBUTS[n] + AMORCE)}, ${fr(p.duree - AMORCE - 0.2)} s au plus, ${p.intention.charAt(0).toLowerCase() + p.intention.slice(1)})*\n   ${p.voix}`).filter(Boolean).join('\n')}

## Texte en une seule prise (outils acceptant les pauses SSML)

Les pauses reproduisent les silences du film entre deux répliques.

\`\`\`
${avecPauses}
\`\`\`

## Contrôle après génération

- Chaque réplique tient dans son budget (colonne « Budget » de \`voix-off.md\`).
- Aucun mot ajouté ni retiré par l'outil : le texte est relu et validé pour ne rien promettre que le produit ne fait pas.
- Écoute au casque puis sur haut-parleur de téléphone (public principal).
`,
);
console.log(`textes : ${repliques.length} sous-titres, voix off et consigne écrits (${DUREE_TOTALE} s)`);
if (mode === 'textes') process.exit(0);

// ---------------------------------------------------------------- 3. Rendu

/** Serveur local : le film, les polices et les icônes de la marque, sans copie. */
const RACINES = {
  '/polices/': join(FRONTEND, 'node_modules/@fontsource-variable'),
  '/icones/': join(FRONTEND, 'projects/ui/assets/icones'),
  '/': ICI,
};
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const serveur = createServer((req, res) => {
  const chemin = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const [prefixe, racine] = Object.entries(RACINES).find(([p]) => chemin.startsWith(p));
  const fichier = normalize(join(racine, chemin.slice(prefixe.length)));
  if (!fichier.startsWith(racine) || !existsSync(fichier) || statSync(fichier).isDirectory()) {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, { 'content-type': TYPES[extname(fichier)] ?? 'application/octet-stream' });
  createReadStream(fichier).pipe(res);
});
await new Promise((r) => serveur.listen(0, '127.0.0.1', r));
const adresse = `http://127.0.0.1:${serveur.address().port}/film.html`;

// Playwright n'est chargé que pour le rendu : les textes se régénèrent sans les dépendances du frontend.
const { chromium } = await import('../../../frontend/node_modules/@playwright/test/index.mjs');
mkdirSync(SORTIE, { recursive: true });
const nav = await chromium.launch();

/** Ouvre une page du film prête à rendre ; échoue si une ressource manque. */
async function ouvrir() {
  const page = await nav.newPage({ viewport: { width: 1920, height: 1080 } });
  const erreurs = [];
  page.on('pageerror', (e) => erreurs.push(e.message));
  page.on('requestfailed', (r) => erreurs.push(`échec ${r.url()}`));
  page.on('response', (r) => r.status() >= 400 && erreurs.push(`${r.status()} ${r.url()}`));
  await page.goto(adresse);
  await page.waitForFunction(() => window.pret !== undefined);
  await page.evaluate(() => window.pret);
  if (erreurs.length) throw new Error(`Page du film : ${erreurs.join(' ; ')}`);
  return async (t) => {
    await page.evaluate((t) => window.rendre(t), t);
    return page.screenshot({ type: 'jpeg', quality: 94 });
  };
}

function ffmpeg(args) {
  const p = spawn('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', ...args], { cwd: VIDEO, stdio: ['pipe', 'inherit', 'inherit'] });
  const fini = new Promise((ok, ko) => p.on('close', (code) => (code === 0 ? ok() : ko(new Error(`ffmpeg a échoué (${code})`)))));
  return { entree: p.stdin, fini };
}
const H264 = ['-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p'];

const film = `sortie/fasoguardian-presentation-${IPS}ips.mp4`;
if (mode === 'apercu') {
  const image = await ouvrir();
  const { entree, fini } = ffmpeg(['-f', 'image2pipe', '-i', '-', '-vf', 'scale=640:-1,tile=4x4', '-frames:v', '1', 'sortie/apercu.jpg']);
  for (let n = 0; n < PLANS.length; n++) entree.write(await image(DEBUTS[n] + Math.min(PLANS[n].duree - 0.6, PLANS[n].duree * 0.8)));
  entree.end();
  await fini;
  console.log('aperçu : sortie/apercu.jpg');
} else if (mode !== 'soustitres') {
  const total = Math.round(DUREE_TOTALE * IPS);
  const coupe = Math.round(DEBUTS[PLANS.findIndex((p) => p.id === 'generique')] * IPS);
  const depart = Date.now();

  /** Rend les images [de, a) sur plusieurs navigateurs, chacun dans son segment, puis les met bout à bout. */
  async function rendrePlage(de, a, nom) {
    const n = Math.max(1, Math.min(RENDEURS, Math.floor((a - de) / IPS)));
    let faites = 0;
    let annonce = 0;
    const segments = await Promise.all(
      Array.from({ length: n }, async (_, k) => {
        const [d, f] = [de + Math.floor(((a - de) * k) / n), de + Math.floor(((a - de) * (k + 1)) / n)];
        const image = await ouvrir();
        const segment = `sortie/segment-${k}.mp4`;
        const { entree, fini } = ffmpeg(['-f', 'image2pipe', '-framerate', String(IPS), '-i', '-', ...H264, '-g', String(IPS * 2), segment]);
        for (let i = d; i < f; i++) {
          if (!entree.write(await image(i / IPS))) await new Promise((r) => entree.once('drain', r));
          if (++faites >= annonce) {
            console.log(`${nom} : ${Math.round((100 * faites) / (a - de))} % (${Math.round((Date.now() - depart) / 1000)} s)`);
            annonce += (a - de) / 20;
          }
        }
        entree.end();
        await fini;
        return segment;
      }),
    );
    await assembler(segments, nom);
    for (const s of segments) rmSync(join(VIDEO, s));
  }

  async function assembler(morceaux, nom) {
    writeFileSync(join(SORTIE, 'morceaux.txt'), morceaux.map((s) => `file '${s.replace('sortie/', '')}'`).join('\n'));
    await ffmpeg(['-f', 'concat', '-safe', '0', '-i', 'sortie/morceaux.txt', '-c', 'copy', '-movflags', '+faststart', nom]).fini;
    rmSync(join(SORTIE, 'morceaux.txt'));
  }

  // Le corps du film (tout sauf le générique) n'est rendu qu'une fois et gardé dans sortie/ : changer les
  // coordonnées du générique ne refait que ses quelques secondes. --refaire impose le rendu complet.
  const corps = `sortie/corps-${IPS}ips.mp4`;
  if (!existsSync(join(VIDEO, corps)) || process.argv.includes('--refaire')) await rendrePlage(0, coupe, corps);
  const generique = `sortie/generique-${IPS}ips.mp4`;
  await rendrePlage(coupe, total, generique);
  await assembler([corps, generique], film);
  rmSync(join(VIDEO, generique));
  console.log(`film : ${film} (${total} images, ${IPS} i/s, ${Math.round((Date.now() - depart) / 1000)} s de rendu)`);

  // Vignette : l'image de la révélation.
  writeFileSync(join(SORTIE, 'vignette.jpg'), await (await ouvrir())(DEBUTS[2] + 2.5));

}
if (mode === 'film' || mode === 'soustitres') {
  // Versions sous-titrées : au rythme du film, et à 60 i/s pour les réseaux sociaux (plafonnés à 60).
  const soustitres = 'ass=sous-titres.fr.ass';
  const versions = [[IPS, soustitres], ...(IPS > 60 ? [[60, `fps=60,${soustitres}`]] : [])];
  for (const [ips, filtre] of versions) {
    const nom = `sortie/fasoguardian-presentation-sous-titree-${ips}ips.mp4`;
    await ffmpeg(['-i', film, '-vf', filtre, ...H264, '-movflags', '+faststart', nom]).fini;
    console.log(`film sous-titré : ${nom}`);
  }
}
await nav.close();
serveur.close();
