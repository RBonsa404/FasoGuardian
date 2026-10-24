/**
 * Scénario du film de présentation : source unique du minutage. La page animée (film.html), le texte de voix off
 * (voix-off.md) et les sous-titres (SRT, VTT) en sont dérivés par produire.mjs, ce qui garantit leur synchronisation.
 *
 * Chaque plan : `duree` en secondes, `voix` (texte dit, chiffres en toutes lettres), `ecran` (texte affiché quand il
 * diffère de la voix pour les sous-titres, sinon la voix est reprise), `intention` (consigne de jeu pour la voix).
 * Chaque phrase correspond à un fait publié sur le site vitrine (frontend/projects/site/src/app/contenu.ts).
 */
export const DEBIT = 2.5; // mots par seconde, voix posée en français
export const AMORCE = 0.4; // la voix démarre 0,4 s après le début du plan

export const PLANS = [
  {
    id: 'ouverture',
    duree: 6,
    voix: "Seize heures trente. À Ouagadougou, des milliers d'enfants sortent de l'école.",
    ecran: "16 h 30. À Ouagadougou, des milliers d'enfants sortent de l'école.",
    intention: 'Calme, posé, presque murmuré. Une image du quotidien.',
  },
  {
    id: 'question',
    duree: 6.5,
    voix: 'Et pour chaque parent, la même question : où est mon enfant, en ce moment ?',
    intention: 'Proche, sincère. Légère tension sur « en ce moment », sans dramatiser.',
  },
  {
    id: 'revelation',
    duree: 8,
    voix: 'FasoGuardian. Un bracelet simple au poignet de votre enfant, et une application dans votre téléphone.',
    intention: 'La voix s’ouvre, sourire audible. Marquer une courte pause après « FasoGuardian ».',
  },
  {
    id: 'bracelet',
    duree: 9,
    voix: "Pas d'écran, pas de caméra, pas de micro. Un bracelet robuste et étanche, qui tient deux jours et plus.",
    ecran: "Pas d'écran, pas de caméra, pas de micro. Un bracelet robuste et étanche, qui tient 2 jours et plus.",
    intention: 'Rythme net sur les trois « pas de », puis assurance tranquille.',
  },
  {
    id: 'position',
    duree: 7.5,
    voix: "D'un coup d'œil, vous voyez où il est, à quelle heure, et avec quelle précision.",
    intention: 'Simple, concret, comme on montre l’écran à une amie.',
  },
  {
    id: 'zones',
    duree: 9.5,
    voix: "Créez vos Safe Zones : l'école, la maison, chez grand-mère. S'il en sort pendant les horaires prévus, vous êtes prévenu.",
    intention: 'Énumération chaleureuse (« chez grand-mère » avec un sourire), puis rassurant.',
  },
  {
    id: 'sos',
    duree: 11,
    voix: 'En cas de danger, il appuie trois secondes sur le bouton SOS. Le bracelet vibre pour le rassurer, et vous êtes alerté en quelques secondes.',
    ecran: 'En cas de danger, il appuie 3 secondes sur le bouton SOS. Le bracelet vibre pour le rassurer, et vous êtes alerté en quelques secondes.',
    intention: 'Plus grave sur « en cas de danger », puis la voix se détend : l’aide arrive.',
  },
  {
    id: 'reseau',
    duree: 7,
    voix: 'Plus de 4G ? Le bracelet passe en 2G, puis envoie ses alertes par SMS.',
    intention: 'Léger, presque malicieux sur la question. Dire « quatre G », « deux G », « S M S ».',
  },
  {
    id: 'qr',
    duree: 9.5,
    voix: "S'il se perd, celui qui le trouve scanne le QR code gravé et vous appelle, sans voir son nom ni sa position.",
    intention: 'Récit, ton de confiance. Appuyer doucement « sans voir ».',
  },
  {
    id: 'confidentialite',
    duree: 6.5,
    voix: 'Seuls vous et les proches que vous choisissez voient où il se trouve.',
    intention: 'Ferme et bienveillant, une promesse tenue.',
  },
  {
    id: 'offres',
    duree: 10,
    voix: "Le bracelet coûte trente mille francs CFA. L'abonnement commence à mille cinq cents francs par mois, sans engagement, payé par mobile money.",
    ecran: "Le bracelet coûte 30 000 FCFA. L'abonnement commence à 1 500 FCFA par mois, sans engagement, payé par mobile money.",
    intention: 'Clair, articulé, aucune précipitation sur les chiffres.',
  },
  {
    id: 'fin',
    duree: 9,
    voix: "FasoGuardian, conçu à Ouagadougou pour les familles du Burkina. Toujours près d'eux. Jamais sur leur dos.",
    intention: 'Fierté tranquille sur « Ouagadougou ». Le slogan en deux temps, pause entre les deux phrases.',
  },
];

/** Début de chaque plan, en secondes depuis le début du film. */
export function debuts() {
  let t = 0;
  return PLANS.map((p) => {
    const d = t;
    t += p.duree;
    return d;
  });
}

export const DUREE_TOTALE = PLANS.reduce((s, p) => s + p.duree, 0);
