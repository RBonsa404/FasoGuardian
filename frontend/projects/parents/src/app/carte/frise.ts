import { PointGeo, PositionConnue, SafeZone } from 'api';

/** Un arrêt compte à partir de cinq minutes passées dans un rayon de soixante mètres. */
export const ARRET_MINIMAL_MS = 5 * 60_000;
export const RAYON_ARRET_M = 60;

export type NatureEtape = 'depart' | 'arret' | 'entree' | 'sortie';

/** Une ligne de la frise des lieux (écran 19). */
export interface EtapeTrajet {
  readonly nature: NatureEtape;
  /** Nom de la zone concernée, ou null hors zone. */
  readonly zone: string | null;
  readonly debut: string;
  /** Durée de l'arrêt en minutes, pour un arrêt. */
  readonly minutes: number | null;
}

const RAYON_TERRE_M = 6_371_008.8;

export function distanceM(a: PointGeo, b: PointGeo): number {
  const radians = (degres: number) => (degres * Math.PI) / 180;
  const dPhi = radians(b.latitude - a.latitude);
  const dLambda = radians(b.longitude - a.longitude);
  const h = Math.sin(dPhi / 2) ** 2 + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude)) * Math.sin(dLambda / 2) ** 2;
  return 2 * RAYON_TERRE_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Le point est-il dans la zone ? Cercle : distance au centre ; polygone : règle du lancer de rayon. */
export function dansZone(point: PointGeo, zone: SafeZone): boolean {
  if (zone.centre && zone.rayonM) {
    return distanceM(point, zone.centre) <= zone.rayonM;
  }
  const sommets = zone.sommets ?? [];
  let dedans = false;
  for (let i = 0, j = sommets.length - 1; i < sommets.length; j = i++) {
    const a = sommets[i];
    const b = sommets[j];
    const traverse = a.latitude > point.latitude !== b.latitude > point.latitude;
    if (traverse && point.longitude < ((b.longitude - a.longitude) * (point.latitude - a.latitude)) / (b.latitude - a.latitude) + a.longitude) {
      dedans = !dedans;
    }
  }
  return dedans;
}

function zoneDe(point: PointGeo, zones: readonly SafeZone[]): SafeZone | null {
  return zones.find((zone) => dansZone(point, zone)) ?? null;
}

/**
 * Frise des lieux d'une journée : le départ, puis chaque entrée dans une Safe Zone, chaque sortie, et chaque
 * arrêt de cinq minutes au moins. Les lieux ne sont nommés que par les zones du parent : la plateforme ne
 * convertit pas une position en adresse.
 */
export function frise(points: readonly PositionConnue[], zones: readonly SafeZone[]): EtapeTrajet[] {
  if (points.length === 0) {
    return [];
  }
  const tries = [...points].sort((a, b) => Date.parse(a.mesureeLe) - Date.parse(b.mesureeLe));
  const etapes: EtapeTrajet[] = [];
  let zone = zoneDe(tries[0], zones);
  etapes.push({ nature: 'depart', zone: zone?.nom ?? null, debut: tries[0].mesureeLe, minutes: null });

  let ancre = 0;
  const cloreArret = (fin: number) => {
    const duree = Date.parse(tries[fin].mesureeLe) - Date.parse(tries[ancre].mesureeLe);
    // Le premier séjour est déjà dit par le départ.
    if (duree >= ARRET_MINIMAL_MS && ancre > 0) {
      etapes.push({ nature: 'arret', zone: zoneDe(tries[ancre], zones)?.nom ?? null, debut: tries[ancre].mesureeLe, minutes: Math.round(duree / 60_000) });
    }
  };
  for (let i = 1; i < tries.length; i++) {
    const point = tries[i];
    const ici = zoneDe(point, zones);
    if (ici?.id !== zone?.id) {
      if (zone) {
        etapes.push({ nature: 'sortie', zone: zone.nom, debut: point.mesureeLe, minutes: null });
      }
      if (ici) {
        etapes.push({ nature: 'entree', zone: ici.nom, debut: point.mesureeLe, minutes: null });
      }
      zone = ici;
    }
    if (distanceM(tries[ancre], point) > RAYON_ARRET_M) {
      cloreArret(i - 1);
      ancre = i;
    }
  }
  cloreArret(tries.length - 1);
  return etapes.sort((a, b) => Date.parse(a.debut) - Date.parse(b.debut));
}
