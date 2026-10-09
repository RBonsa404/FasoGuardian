import type * as Three from 'three';

type Lib = typeof Three;

/** Coloris du bracelet (HANDOFF §6) : sangle, capot et pastille de choix. */
export interface Coloris {
  readonly nom: string;
  readonly sangle: number;
  readonly capot: number;
  readonly css: string;
}

export const COLORIS: readonly Coloris[] = [
  { nom: 'Bleu confiance', sangle: 0x123c7a, capot: 0x123c7a, css: '#123C7A' },
  { nom: 'Nuit', sangle: 0x1a2139, capot: 0x1a2139, css: '#1A2139' },
  { nom: 'Menthe', sangle: 0x2bbe8c, capot: 0x123c7a, css: '#2BBE8C' },
  { nom: 'Édition École', sangle: 0xe6eaf2, capot: 0x0a2246, css: '#E6EAF2' },
];

/** Bracelet construit, avec les pièces que la vue éclatée déplace. */
export interface Bracelet {
  readonly racine: Three.Group;
  readonly capot: Three.Group;
  readonly antennes: Three.Group;
  readonly carte: Three.Group;
  readonly batterie: Three.Mesh;
  readonly sangle: Three.Group;
  readonly matiereSangle: Three.MeshStandardMaterial;
  readonly matiereCapot: Three.MeshStandardMaterial;
  /** Hauteurs de repos, en mètres. */
  readonly repos: { readonly capot: number; readonly antennes: number; readonly carte: number; readonly batterie: number };
}

const mm = (valeur: number) => valeur / 1000;

/**
 * Modèle du bracelet FasoGuardian, 48 × 38 × 14 mm, construit pièce par pièce comme dans le paquet de design
 * (« Bracelet 3D ») : coque, capot gravé d'un QR, bouton SOS, LED, contacts de charge, carte, batterie,
 * antennes, sangle et fermoir de sécurité. Les nœuds portent les noms attendus par HANDOFF §6.
 */
export function construireBracelet(THREE: Lib): Bracelet {
  const matiere = (nom: string, couleur: number, rugosite = 0.55, metal = 0.05, emission?: number) => {
    const m = new THREE.MeshStandardMaterial({ color: couleur, roughness: rugosite, metalness: metal, ...(emission === undefined ? {} : { emissive: emission, emissiveIntensity: 0.6 }) });
    m.name = nom;
    return m;
  };
  const matieres = {
    coque: matiere('pc_abs_nuit', 0x161c30, 0.42),
    capot: matiere('capot_confiance', 0x123c7a, 0.35),
    gravure: matiere('gravure_laser', 0x6e93d6, 0.8),
    sangle: matiere('sangle_silicone', 0x123c7a, 0.7),
    fermoir: matiere('fermoir_inox', 0xb8c0d0, 0.3, 0.35),
    sos: matiere('bouton_sos_ambre', 0xff9f1c, 0.45),
    led: matiere('led_rgb', 0x2fa8ff, 0.2, 0.05, 0x2fa8ff),
    contact: matiere('contacts_or', 0xd8b46a, 0.3, 0.4),
    carte: matiere('carte_electronique', 0x1e6b4f, 0.6),
    composant: matiere('composants', 0x1a1a1a, 0.5),
    batterie: matiere('batterie_lipo', 0x9aa3b5, 0.4, 0.2),
    antenne: matiere('antenne_flex', 0xc98a3a, 0.5),
  };
  const piece = (nom: string, geometrie: Three.BufferGeometry, m: Three.Material) => {
    const objet = new THREE.Mesh(geometrie, m);
    objet.name = nom;
    return objet;
  };
  const rectangleArrondi = (largeur: number, hauteur: number, rayon: number) => {
    const forme = new THREE.Shape();
    const x = -largeur / 2;
    const y = -hauteur / 2;
    forme.moveTo(x + rayon, y);
    forme.lineTo(x + largeur - rayon, y);
    forme.quadraticCurveTo(x + largeur, y, x + largeur, y + rayon);
    forme.lineTo(x + largeur, y + hauteur - rayon);
    forme.quadraticCurveTo(x + largeur, y + hauteur, x + largeur - rayon, y + hauteur);
    forme.lineTo(x + rayon, y + hauteur);
    forme.quadraticCurveTo(x, y + hauteur, x, y + hauteur - rayon);
    forme.lineTo(x, y + rayon);
    forme.quadraticCurveTo(x, y, x + rayon, y);
    return forme;
  };
  const dalle = (largeur: number, profondeur: number, hauteur: number, rayon: number, chanfrein: number) => {
    const g = new THREE.ExtrudeGeometry(rectangleArrondi(largeur, profondeur, rayon), {
      depth: hauteur - 2 * chanfrein,
      bevelEnabled: chanfrein > 0,
      bevelThickness: chanfrein,
      bevelSize: chanfrein,
      bevelSegments: 6,
      curveSegments: 16,
    });
    g.rotateX(-Math.PI / 2);
    g.translate(0, chanfrein, 0);
    return g;
  };

  const racine = new THREE.Group();
  racine.name = 'bracelet';
  const boitier = new THREE.Group();
  boitier.name = 'boitier';
  racine.add(boitier);
  boitier.add(piece('coque', dalle(mm(46), mm(36), mm(10.5), mm(9), mm(1.5)), matieres.coque));

  const capot = new THREE.Group();
  capot.name = 'capot';
  capot.add(piece('capot_piece', dalle(mm(46), mm(36), mm(3.2), mm(9), mm(1.4)), matieres.capot));
  capot.position.y = mm(10.6);
  boitier.add(capot);
  capot.add(piece('qr_grave', gravureQr(THREE), matieres.gravure));
  const serie = new THREE.Group();
  serie.name = 'numero_serie';
  [2.2, 1.2, 0.8, 2.0, 2.0, 2.0, 1.4].forEach((largeur, rang) => {
    const barre = piece(`serie_${rang}`, new THREE.BoxGeometry(mm(1.4), mm(0.12), mm(largeur)), matieres.gravure);
    barre.position.set(mm(9) + rang * mm(1.9), mm(3.25), 0);
    serie.add(barre);
  });
  capot.add(serie);

  const led = piece('led', new THREE.CylinderGeometry(mm(1.1), mm(1.1), mm(0.4), 32), matieres.led);
  led.position.set(mm(14), mm(13.85), mm(-11));
  boitier.add(led);

  const sos = new THREE.Group();
  sos.name = 'bouton_sos';
  const bouton = piece('bouton_sos_piece', new THREE.CylinderGeometry(mm(4.2), mm(4.2), mm(2.2), 40), matieres.sos);
  bouton.rotation.x = Math.PI / 2;
  sos.add(bouton);
  const relief = piece('bouton_sos_relief', new THREE.TorusGeometry(mm(3), mm(0.35), 12, 40), matieres.sos);
  relief.position.z = mm(1.15);
  sos.add(relief);
  sos.position.set(mm(4), mm(6.5), mm(18.6));
  boitier.add(sos);

  [-3, 3].forEach((x, rang) => {
    const contact = piece(`contacts_charge_${rang}`, new THREE.CylinderGeometry(mm(1.6), mm(1.6), mm(0.6), 32), matieres.contact);
    contact.position.set(mm(x), mm(-0.25), 0);
    boitier.add(contact);
  });

  const internes = new THREE.Group();
  internes.name = 'internes';
  boitier.add(internes);
  const carte = new THREE.Group();
  carte.name = 'carte';
  carte.add(piece('circuit', dalle(mm(40), mm(30), mm(1.2), mm(6), 0), matieres.carte));
  [
    [-8, -5, 9, 9],
    [6, 6, 7, 5],
    [10, -7, 5, 4],
    [-12, 8, 4, 6],
  ].forEach(([x, z, largeur, profondeur], rang) => {
    const composant = piece(`composant_${rang}`, new THREE.BoxGeometry(mm(largeur), mm(1.4), mm(profondeur)), matieres.composant);
    composant.position.set(mm(x), mm(1.9), mm(z));
    carte.add(composant);
  });
  carte.position.y = mm(6.8);
  internes.add(carte);
  const batterie = piece('batterie', dalle(mm(36), mm(26), mm(4.5), mm(4), mm(0.6)), matieres.batterie);
  batterie.position.y = mm(1.8);
  internes.add(batterie);
  const antennes = new THREE.Group();
  antennes.name = 'antennes';
  (
    [
      [-15, 'lte'],
      [15, 'gnss'],
    ] as const
  ).forEach(([x, nom]) => {
    const antenne = piece(`antenne_${nom}`, new THREE.BoxGeometry(mm(10), mm(0.3), mm(28)), matieres.antenne);
    antenne.position.set(mm(x), mm(9.6), 0);
    antennes.add(antenne);
  });
  internes.add(antennes);

  const sangle = new THREE.Group();
  sangle.name = 'sangle';
  sangle.add(piece('sangle_boucle', boucle(THREE, mm(31), mm(27), mm(13), mm(21.5), mm(20), mm(2.6), 160), matieres.sangle));
  const fermoir = new THREE.Group();
  fermoir.name = 'fermoir_securite';
  fermoir.add(piece('fermoir', dalle(mm(12), mm(22), mm(4), mm(2), mm(0.8)), matieres.fermoir));
  const verrou = piece('verrou', new THREE.BoxGeometry(mm(4), mm(1), mm(14)), matieres.coque);
  verrou.position.y = mm(-0.4);
  fermoir.add(verrou);
  fermoir.position.set(0, mm(-43.4), 0);
  sangle.add(fermoir);
  racine.add(sangle);

  return {
    racine,
    capot,
    antennes,
    carte,
    batterie,
    sangle,
    matiereSangle: matieres.sangle,
    matiereCapot: matieres.capot,
    repos: { capot: capot.position.y, antennes: antennes.position.y, carte: carte.position.y, batterie: batterie.position.y },
  };
}

/** Écarte les pièces : 0 pour le bracelet assemblé, 1 pour la vue éclatée complète. */
export function eclater(bracelet: Bracelet, part: number): void {
  const e = Math.min(1, Math.max(0, part));
  bracelet.capot.position.y = bracelet.repos.capot + mm(44) * e;
  bracelet.antennes.position.y = bracelet.repos.antennes + mm(30) * e;
  bracelet.carte.position.y = bracelet.repos.carte + mm(18) * e;
  bracelet.batterie.position.y = bracelet.repos.batterie + mm(6) * e;
  bracelet.sangle.position.y = -mm(14) * e;
}

export function colorer(bracelet: Bracelet, coloris: Coloris): void {
  bracelet.matiereSangle.color.setHex(coloris.sangle);
  bracelet.matiereCapot.color.setHex(coloris.capot);
}

/** QR de démonstration (21 × 21) gravé sur le capot ; le motif est fixe et ne code aucune donnée. */
function gravureQr(THREE: Lib): Three.BufferGeometry {
  const positions: number[] = [];
  const normales: number[] = [];
  const cote = 21;
  const cellule = mm(0.62);
  let graine = 2291;
  const hasard = () => (graine = (graine * 16807) % 2147483647) / 2147483647;
  const reperes = [
    [0, 0],
    [0, cote - 7],
    [cote - 7, 0],
  ];
  const dansRepere = (i: number, j: number) => reperes.some(([a, b]) => i >= a - 1 && i < a + 8 && j >= b - 1 && j < b + 8);
  const trait = (i: number, j: number) =>
    reperes.some(([a, b]) => i >= a && i < a + 7 && j >= b && j < b + 7 && (i === a || i === a + 6 || j === b || j === b + 6 || (i >= a + 2 && i <= a + 4 && j >= b + 2 && j <= b + 4)));
  const cube = new THREE.BoxGeometry(cellule * 0.96, mm(0.12), cellule * 0.96).toNonIndexed();
  for (let i = 0; i < cote; i++) {
    for (let j = 0; j < cote; j++) {
      const plein = dansRepere(i, j) ? trait(i, j) : hasard() > 0.5;
      if (!plein) {
        continue;
      }
      const g = cube.clone();
      g.translate(mm(-5) + i * cellule - (cote * cellule) / 2, mm(3.25), -(cote * cellule) / 2 + j * cellule + cellule / 2);
      positions.push(...(g.attributes['position'].array as Float32Array));
      normales.push(...(g.attributes['normal'].array as Float32Array));
    }
  }
  const gravure = new THREE.BufferGeometry();
  gravure.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  gravure.setAttribute('normal', new THREE.Float32BufferAttribute(normales, 3));
  return gravure;
}

/** Sangle : boucle elliptique de section rectangulaire, ouverte là où elle rejoint le boîtier. */
function boucle(THREE: Lib, a: number, b: number, decalage: number, ouverture: number, largeur: number, epaisseur: number, segments: number): Three.BufferGeometry {
  const debut = Math.asin(ouverture / a);
  const fin = 2 * Math.PI - debut;
  const positions: number[] = [];
  const indices: number[] = [];
  const anneaux: number[][] = [];
  const sommet = (x: number, y: number, z: number) => {
    positions.push(x, y, z);
    return positions.length / 3 - 1;
  };
  for (let i = 0; i <= segments; i++) {
    const angle = debut + ((fin - debut) * i) / segments;
    const point = new THREE.Vector2(a * Math.sin(angle), b * Math.cos(angle) - b + decalage);
    const normale = new THREE.Vector2(Math.sin(angle) / a, Math.cos(angle) / b).normalize();
    const dehors = epaisseur / 2;
    anneaux.push([
      sommet(point.x + normale.x * dehors, point.y + normale.y * dehors, largeur / 2),
      sommet(point.x + normale.x * dehors, point.y + normale.y * dehors, -largeur / 2),
      sommet(point.x - normale.x * dehors, point.y - normale.y * dehors, -largeur / 2),
      sommet(point.x - normale.x * dehors, point.y - normale.y * dehors, largeur / 2),
    ]);
  }
  for (let i = 0; i < segments; i++) {
    for (let k = 0; k < 4; k++) {
      const a0 = anneaux[i][k];
      const a1 = anneaux[i][(k + 1) % 4];
      const b0 = anneaux[i + 1][k];
      const b1 = anneaux[i + 1][(k + 1) % 4];
      indices.push(a0, b0, a1, a1, b0, b1);
    }
  }
  const premier = anneaux[0];
  const dernier = anneaux[segments];
  indices.push(premier[0], premier[2], premier[1], premier[0], premier[3], premier[2]);
  indices.push(dernier[0], dernier[1], dernier[2], dernier[0], dernier[2], dernier[3]);
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  g.setIndex(indices);
  g.computeVertexNormals();
  return g;
}
