/** Heure locale « 14:30 » d'un instant ISO. */
export function heure(instantIso: string): string {
  return new Date(instantIso).toLocaleTimeString('fr', { hour: '2-digit', minute: '2-digit' });
}

/** Ancienneté lisible d'un instant passé : « à l'instant », « il y a 12 min », « il y a 3 h », « il y a 2 j ». */
export function ilYA(instantIso: string, maintenant = new Date()): string {
  const minutes = minutesDepuis(instantIso, maintenant);
  if (minutes < 1) {
    return $localize`:@@duree.instant:à l'instant`;
  }
  if (minutes < 60) {
    return $localize`:@@duree.minutes:il y a ${minutes}:minutes: min`;
  }
  const heures = Math.floor(minutes / 60);
  return heures < 24 ? $localize`:@@duree.heures:il y a ${heures}:heures: h` : $localize`:@@duree.jours:il y a ${Math.floor(heures / 24)}:jours: j`;
}

export function minutesDepuis(instantIso: string, maintenant = new Date()): number {
  return Math.max(0, Math.floor((maintenant.getTime() - new Date(instantIso).getTime()) / 60_000));
}
