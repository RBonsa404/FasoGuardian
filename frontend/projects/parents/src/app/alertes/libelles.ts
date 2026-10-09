import { ActionAlerte, Alerte, StatutAlerte, TypeAlerte } from 'api';
import { NomIcone } from 'ui';

const ICONES: Record<TypeAlerte, NomIcone> = {
  SOS: 'sos',
  RETRAIT: 'retrait',
  SIGNALEMENT: 'cloche',
  SORTIE_ZONE: 'sortie-zone',
  BATTERIE_CRITIQUE: 'batterie-faible',
  CHUTE: 'info',
};

const ETIQUETTES: Record<TypeAlerte, string> = {
  SOS: 'SOS',
  RETRAIT: $localize`:@@alerte.etiquette.retrait:RET`,
  SIGNALEMENT: $localize`:@@alerte.etiquette.signalement:SIG`,
  SORTIE_ZONE: $localize`:@@alerte.etiquette.zone:ZONE`,
  BATTERIE_CRITIQUE: $localize`:@@alerte.etiquette.batterie:BAT`,
  CHUTE: $localize`:@@alerte.etiquette.chute:CHUTE`,
};

export function iconeAlerte(alerte: Alerte): NomIcone {
  return ICONES[alerte.type];
}

/** Sigle de trois ou quatre lettres affiché dans le journal. */
export function etiquetteAlerte(alerte: Alerte): string {
  return ETIQUETTES[alerte.type];
}

/** Titre court d'une alerte dans une liste : « SOS », « Sortie de zone · École ». */
export function titreCourt(alerte: Alerte): string {
  switch (alerte.type) {
    case 'SOS':
      return $localize`:@@alerte.court.sos:SOS · appui long`;
    case 'RETRAIT':
      return $localize`:@@alerte.court.retrait:Retrait non autorisé`;
    case 'SIGNALEMENT':
      return $localize`:@@alerte.court.signalement:Signalement`;
    case 'SORTIE_ZONE':
      return alerte.libelle ? $localize`:@@alerte.court.zone:Sortie de zone · ${alerte.libelle}:zone:` : $localize`:@@alerte.court.zoneSans:Sortie de zone`;
    case 'BATTERIE_CRITIQUE':
      return $localize`:@@alerte.court.batterie:Batterie critique`;
    case 'CHUTE':
      return $localize`:@@alerte.court.chute:Chute détectée`;
  }
}

/** Phrase complète de l'alerte plein écran : « Le bracelet d'Awa a été retiré ». */
export function titreComplet(alerte: Alerte, prenom: string): string {
  switch (alerte.type) {
    case 'SOS':
      return $localize`:@@alerte.titre.sos:${prenom}:prenom: a déclenché le SOS`;
    case 'RETRAIT':
      return $localize`:@@alerte.titre.retrait:Le bracelet de ${prenom}:prenom: a été retiré`;
    case 'SIGNALEMENT':
      return $localize`:@@alerte.titre.signalement:Signalement ouvert pour ${prenom}:prenom:`;
    case 'SORTIE_ZONE':
      return $localize`:@@alerte.titre.zone:${prenom}:prenom: a quitté « ${alerte.libelle ?? ''}:zone: »`;
    case 'BATTERIE_CRITIQUE':
      return $localize`:@@alerte.titre.batterie:La batterie du bracelet de ${prenom}:prenom: est critique`;
    case 'CHUTE':
      return $localize`:@@alerte.titre.chute:Le bracelet de ${prenom}:prenom: a détecté une chute`;
  }
}

/** Ce que le parent doit savoir pour décider, selon la nature de l'alerte. */
export function explication(alerte: Alerte): string {
  switch (alerte.type) {
    case 'SOS':
      return $localize`:@@alerte.explication.sos:Le bouton SOS a été tenu plus de trois secondes. Le bracelet a vibré pour le confirmer à l'enfant.`;
    case 'RETRAIT':
      return $localize`:@@alerte.explication.retrait:Aucune autorisation de retrait n'était active. Si vous avez coupé la sangle pour une urgence médicale, indiquez-le en levant l'alerte.`;
    case 'SIGNALEMENT':
      return $localize`:@@alerte.explication.signalement:Vous avez ouvert ce signalement. Vous restez seul à décider de la suite.`;
    case 'SORTIE_ZONE':
      return $localize`:@@alerte.explication.zone:La sortie a duré plus longtemps que le délai prévu pour cette zone. L'alerte se fermera d'elle-même au retour dans la zone.`;
    case 'BATTERIE_CRITIQUE':
      return $localize`:@@alerte.explication.batterie:Le bracelet s'éteindra bientôt. Pensez à le recharger ; l'alerte se fermera à la mise en charge.`;
    case 'CHUTE':
      return $localize`:@@alerte.explication.chute:L'accéléromètre a détecté un choc suivi d'une immobilité.`;
  }
}

const STATUTS: Record<StatutAlerte, string> = {
  OUVERTE: $localize`:@@alerte.statut.ouverte:En attente`,
  ACQUITTEE: $localize`:@@alerte.statut.acquittee:Prise en charge`,
  ESCALADEE: $localize`:@@alerte.statut.escaladee:Transmise`,
  LEVEE: $localize`:@@alerte.statut.levee:Levée`,
  FAUSSE_ALERTE: $localize`:@@alerte.statut.fausse:Fausse alerte`,
};

export function statutLisible(statut: StatutAlerte): string {
  return STATUTS[statut];
}

/** « Prise en charge · par vous », « Levée automatiquement · Retour dans la zone ». */
export function actionLisible(action: ActionAlerte): string {
  const quoi = {
    OUVERTURE: $localize`:@@alerte.action.ouverture:Déclenchée`,
    ACQUITTEMENT: $localize`:@@alerte.action.acquittement:Prise en charge`,
    ESCALADE: $localize`:@@alerte.action.escalade:Transmise aux forces de sécurité`,
    LEVEE: $localize`:@@alerte.action.levee:Levée`,
    FAUSSE_ALERTE: $localize`:@@alerte.action.fausse:Classée fausse alerte`,
    RESOLUTION: $localize`:@@alerte.action.resolution:Levée automatiquement`,
  }[action.type];
  const qui = action.auteur === 'VOUS' ? $localize`:@@alerte.auteur.vous:par vous` : action.auteur === 'AUTRE_TUTEUR' ? $localize`:@@alerte.auteur.autre:par un autre tuteur` : '';
  return [quoi, qui, action.motif].filter(Boolean).join(' · ');
}

export function enCours(alerte: Alerte): boolean {
  return alerte.statut === 'OUVERTE' || alerte.statut === 'ACQUITTEE' || alerte.statut === 'ESCALADEE';
}

/** Les alertes critiques d'abord, puis les plus anciennes : c'est l'ordre dans lequel décider (US-PAR-018). */
export function parGravite(alertes: readonly Alerte[]): Alerte[] {
  const rang: Record<TypeAlerte, number> = { SOS: 0, RETRAIT: 1, SIGNALEMENT: 2, SORTIE_ZONE: 3, CHUTE: 4, BATTERIE_CRITIQUE: 5 };
  return [...alertes].sort((a, b) => rang[a.type] - rang[b.type] || a.ouverteLe.localeCompare(b.ouverteLe));
}
