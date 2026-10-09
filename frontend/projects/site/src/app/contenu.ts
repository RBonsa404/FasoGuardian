import { NomIcone } from 'ui';

/**
 * Textes et données du site vitrine. Ils décrivent le service tel qu'il fonctionne : les chiffres des offres
 * sont ceux du catalogue de la plateforme, et rien n'est promis que le produit ne fait pas (ADR 0029).
 */

/** Adresse de l'application des parents ; relative par défaut, le site et l'application partageant le même domaine. */
export const ESPACE_PARENTS = '/app/';

export const PRIX_BRACELET = '30 000 FCFA';

export const NAVIGATION = [
  { libelle: 'Le bracelet', lien: '/bracelet' },
  { libelle: 'Fonctionnement', lien: '/fonctionnement' },
  { libelle: 'Offres', lien: '/offres' },
  { libelle: 'Écoles', lien: '/ecoles' },
  { libelle: 'Points relais', lien: '/points-relais' },
] as const;

export const ETAPES = [
  { numero: '01', titre: 'Inscrivez-vous', texte: "En ligne ou en point d'inscription. Nous vérifions votre lien avec l'enfant.", icone: 'bouclier' },
  { numero: '02', titre: 'Associez le bracelet', texte: "Saisissez le code de la carte d'activation. Le bracelet est prêt en une minute.", icone: 'bracelet' },
  { numero: '03', titre: 'Vivez tranquille', texte: "Safe Zones pour l'école et la maison, SOS au poignet, alertes doublées par SMS.", icone: 'position' },
] as const satisfies readonly { numero: string; titre: string; texte: string; icone: NomIcone }[];

export const ATOUTS = [
  { titre: 'SOS au poignet', texte: 'Appui long de 3 s. Vous êtes prévenu en quelques secondes, même en 2G.', icone: 'cloche', ton: 'alerte' },
  { titre: 'Position fiable', texte: 'Heure, âge et précision toujours affichés. Alerte par SMS quand les données manquent.', icone: 'position', ton: 'accent' },
  { titre: 'Safe Zones', texte: "École, maison, grand-mère : alerte seulement si l'enfant sort pendant les horaires prévus.", icone: 'bouclier', ton: 'succes' },
  { titre: 'QR code gravé', texte: "Qui trouve l'enfant peut appeler la famille sans voir son nom ni sa position.", icone: 'qr', ton: 'accent' },
] as const satisfies readonly { titre: string; texte: string; icone: NomIcone; ton: 'alerte' | 'accent' | 'succes' }[];

/** Situations illustratives : lieux réels, personnages fictifs. */
export const SCENARIOS = [
  { lieu: 'Gounghin', titre: 'Awa se perd au marché', texte: "Une commerçante scanne le bracelet et appelle la famille en un geste. La position d'Awa ne lui est jamais montrée." },
  { lieu: 'Ouaga 2000', titre: "Moussa ne rentre pas à l'heure", texte: "Sa mère reçoit une sortie de zone après 5 min de tolérance, voit qu'il est chez un camarade, et lève l'alerte." },
  { lieu: 'Bobo-Dioulasso', titre: 'Le réseau 4G tombe', texte: "Le bracelet passe en 2G, puis envoie ses alertes par SMS. Elles continuent d'arriver." },
] as const;

export interface OffrePublique {
  readonly nom: string;
  readonly prix: string;
  readonly etiquette: string;
  readonly points: readonly string[];
  readonly miseEnAvant?: boolean;
}

export const OFFRES: readonly OffrePublique[] = [
  { nom: 'Essentiel', prix: '1 500', etiquette: '', points: ['Position toutes les 15 min', '1 Safe Zone', 'Historique 24 h'] },
  { nom: 'Intermédiaire', prix: '2 250', etiquette: 'Le plus choisi', points: ['Position toutes les 5 min', '3 Safe Zones', 'Historique 30 jours'], miseEnAvant: true },
  { nom: 'Premium', prix: '5 000', etiquette: '', points: ['Position toutes les 5 min', '3 Safe Zones', 'Historique 90 jours'] },
  { nom: 'École', prix: '4 000', etiquette: 'Par élève', points: ["Bracelet fourni par l'école", 'Sur convention avec un établissement', 'Inscription accompagnée'] },
];

export const COMPARATIF: readonly { critere: string; valeurs: readonly [string, string, string, string] }[] = [
  { critere: 'Prix mensuel', valeurs: ['1 500 F', '2 250 F', '5 000 F', '4 000 F'] },
  { critere: 'Fréquence de position', valeurs: ['15 min', '5 min', '5 min', '5 min'] },
  { critere: 'En alerte', valeurs: ['60 s', '60 s', '60 s', '60 s'] },
  { critere: 'Safe Zones', valeurs: ['1', '3', '3', '3'] },
  { critere: 'Historique des trajets', valeurs: ['24 h', '30 jours', '90 jours', '30 jours'] },
  { critere: 'SOS · retrait · page QR', valeurs: ['Inclus', 'Inclus', 'Inclus', 'Inclus'] },
  { critere: 'Bracelet', valeurs: ['30 000 F', '30 000 F', '30 000 F', 'Fourni'] },
];

export const QUESTIONS_TARIFS = [
  { question: 'Y a-t-il un engagement ?', reponse: "Non. Vous payez mois par mois et changez d'offre quand vous voulez." },
  { question: 'Que se passe-t-il si je ne paie pas ?', reponse: 'Après 15 jours et deux rappels, le suivi continu est suspendu. La page QR et le SOS restent toujours actifs.' },
  { question: 'Comment payer ?', reponse: "Par mobile money, depuis l'application." },
  { question: 'Le bracelet est-il garanti ?', reponse: '12 mois. En cas de panne, le service après-vente vous oriente vers un point relais.' },
] as const;

export const CARACTERISTIQUES = [
  { valeur: 'IP67', cle: 'Poussière et immersion' },
  { valeur: '48 h +', cle: 'Autonomie typique' },
  { valeur: '4G · 2G · SMS', cle: 'Repli automatique' },
  { valeur: '3 s', cle: 'Appui long SOS' },
  { valeur: 'Fermoir', cle: 'Détection de retrait' },
  { valeur: 'Magnétique', cle: 'Charge sans port ouvert' },
  { valeur: '48 × 38 × 14', cle: 'mm · 34 g' },
  { valeur: '0', cle: 'écran, caméra ou biométrie' },
] as const;

export const FONCTIONNEMENT = [
  { surtitre: 'LOCALISATION', titre: 'Quand vous en avez besoin', texte: "Toutes les 5 ou 15 minutes selon l'offre, toutes les 60 secondes pendant une alerte.", ton: 'accent' },
  { surtitre: 'SAFE ZONES', titre: 'Des lieux de confiance', texte: 'Cercle ou polygone, plages horaires, délai de tolérance.', ton: 'succes' },
  { surtitre: 'ALERTES', titre: 'Ce qui compte, rien de plus', texte: 'SOS, retrait, sortie de zone, batterie, bracelet muet.', ton: 'alerte' },
  { surtitre: 'PAGE QR', titre: 'Un inconnu peut aider', texte: 'Numéro du bracelet, informations médicales que vous marquez critiques, appel des contacts que vous choisissez.', ton: 'accent' },
  { surtitre: 'VIE PRIVÉE', titre: "Personne d'autre ne voit", texte: "Ni la position, ni le nom. Chaque accès interne est inscrit dans un journal scellé.", ton: 'neutre' },
] as const;

export const ETAPES_ECOLE = [
  { numero: '1', titre: 'Convention et analyse d\'impact', texte: "Nous documentons l'analyse d'impact sur la vie privée avec la direction." },
  { numero: '2', titre: 'Préparation', texte: 'Les bracelets sont fournis et enregistrés pour l\'établissement.' },
  { numero: '3', titre: "Journée d'inscription", texte: 'Nos agents accompagnent les familles sur place : chaque parent garde son propre compte.' },
  { numero: '4', titre: 'Suivi', texte: "L'établissement ne voit ni la position ni la fiche d'un élève : ces données restent aux parents." },
] as const;

export const ENGAGEMENTS = [
  { titre: 'Aucune biométrie', texte: 'Ni empreinte, ni reconnaissance faciale, ni caméra, ni micro.' },
  { titre: 'Chiffrement', texte: "En transit et au repos. Les pièces d'identité sont isolées et réservées à l'équipe de vérification." },
  { titre: 'Loi n° 001-2021/AN', texte: 'Durées de conservation publiées ci-dessous et appliquées par des purges automatiques.' },
  { titre: 'Accès tracé', texte: 'Chaque consultation interne est inscrite dans un journal scellé, vérifiable.' },
  { titre: 'Forces de sécurité', texte: 'Seulement sur votre signalement, confirmé par un code SMS.' },
  { titre: 'Vos droits', texte: "Export de vos données et clôture du compte depuis l'application." },
] as const;

/** Durées appliquées par la plateforme (FG-DOC-06, tableau 18). */
export const CONSERVATION = [
  { donnee: 'Positions', duree: "24 h, 30 jours ou 90 jours selon l'offre" },
  { donnee: 'Journal des alertes', duree: '5 ans' },
  { donnee: "Pièces de vérification d'identité", duree: 'Durée du contrat, plus un an' },
  { donnee: 'Consultations de la page QR', duree: '12 mois, adresse du visiteur pseudonymisée' },
  { donnee: 'Dossier de signalement', duree: '30 jours' },
] as const;

/**
 * Points d'inscription et relais. Liste indicative tant que le réseau du pilote n'est pas ouvert : les lieux
 * sont des quartiers réels, les enseignes sont fictives.
 */
export const POINTS_RELAIS = [
  { ville: 'Ouagadougou', nom: 'Point FasoGuardian · Dassasgho', services: 'Inscription · service après-vente', repere: 'Près du marché' },
  { ville: 'Ouagadougou', nom: 'Pharmacie relais · Pissy', services: 'Remise de bracelets', repere: 'Avenue principale' },
  { ville: 'Ouagadougou', nom: 'Boutique relais · Gounghin', services: 'Échange de sangles', repere: 'Face au marché' },
  { ville: 'Ouagadougou', nom: 'Point FasoGuardian · Tampouy', services: 'Inscription accompagnée', repere: 'Rond-point de Tampouy' },
  { ville: 'Ouagadougou', nom: 'Point relais · Ouaga 2000', services: 'Remise · service après-vente', repere: 'Zone commerciale' },
  { ville: 'Bobo-Dioulasso', nom: 'Point FasoGuardian · Centre', services: 'Inscription · service après-vente', repere: 'Près de la gare' },
] as const;

export const QUESTIONS = [
  { question: 'Le bracelet a-t-il un écran ou une caméra ?', reponse: "Non. Il n'a ni écran, ni caméra, ni micro. Juste un bouton SOS, une LED et un vibreur." },
  { question: "Mon enfant peut-il l'enlever ?", reponse: "Le fermoir de sécurité détecte le retrait : vous êtes alerté en moins d'une minute. Pour la toilette ou la recharge, vous autorisez le retrait depuis l'application, pour une durée limitée." },
  { question: 'Fonctionne-t-il sans 4G ?', reponse: 'Oui. Le bracelet passe en 2G, et quand les données manquent il envoie ses alertes par SMS.' },
  { question: 'Qui peut voir la position ?', reponse: "Vous, les tuteurs dont le lien avec l'enfant a été vérifié, et la personne à qui vous ouvrez un partage temporaire. Aucun agent de FasoGuardian ne voit la position d'un enfant." },
  { question: "Puis-je l'utiliser à Bobo-Dioulasso ?", reponse: 'Oui, partout où un réseau mobile est disponible au Burkina Faso.' },
] as const;

export const URGENCE = "Urgence concernant un enfant ? Appelez le 17 (Police) ou le 18 (Pompiers).";
