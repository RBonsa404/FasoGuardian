import { SafeZone, Situation } from 'api';

import { minutesDepuis } from '../commun/temps';
import { PositionSurCarte } from './fond';

/** Au-delà, la position n'est plus présentée comme actuelle (trois intervalles normaux de 5 minutes). */
export const MINUTES_AVANT_ATTENUATION = 15;

/** Où se trouve l'enfant, d'après les Safe Zones : « Dans « École » », « Hors de « École » », sinon son prénom. */
export function titrePosition(zones: readonly SafeZone[], prenom: string | undefined): string {
  const sortie = zones.find((zone) => zone.sortieEnCours);
  if (sortie) {
    return $localize`:@@carte.titre.sortie:Hors de « ${sortie.nom}:zone: »`;
  }
  const dedans = zones.find((zone) => zone.enfantDedans);
  if (dedans) {
    return $localize`:@@carte.titre.dedans:Dans « ${dedans.nom}:zone: »`;
  }
  return prenom ? $localize`:@@carte.titre.position:Position de ${prenom}:prenom:` : $localize`:@@carte.titre.defaut:Dernière position`;
}

export function positionAncienne(situation: Situation | null, maintenant: Date): boolean {
  const position = situation?.position;
  return !!position && minutesDepuis(position.mesureeLe, maintenant) > MINUTES_AVANT_ATTENUATION;
}

/** Repère de l'enfant : atténué si la position est ancienne ou approximative. */
export function repereDe(situation: Situation | null, prenom: string | undefined, maintenant: Date): PositionSurCarte | null {
  const position = situation?.position;
  return position
    ? {
        latitude: position.latitude,
        longitude: position.longitude,
        precisionM: position.precisionM,
        initiale: prenom?.charAt(0) ?? '',
        attenuee: positionAncienne(situation, maintenant) || position.source !== 'GNSS',
      }
    : null;
}

/** Force du signal à partir de sa puissance reçue, en dBm. */
export function forceDuSignal(signalDbm: number): string {
  if (signalDbm >= -85) {
    return $localize`:@@bracelet.signal.fort:fort`;
  }
  return signalDbm >= -100 ? $localize`:@@bracelet.signal.moyen:moyen` : $localize`:@@bracelet.signal.faible:faible`;
}
