/**
 * Autonomie d'une batterie pleine selon le rythme d'émission et le réseau, d'après FG-DOC-08 tableau 9
 * (batterie de 600 mAh). Ce sont des ordres de grandeur, que le prototype permettra d'affiner : l'écran les
 * présente comme une estimation (« ≈ »).
 */
export function autonomiePleineH(intervalleS: number, reseau: string | null, modeAlerte: boolean): number {
  const deuxG = reseau === '2G';
  if (modeAlerte || intervalleS <= 60) {
    return 31;
  }
  if (intervalleS >= 900) {
    return 175;
  }
  if (intervalleS <= 180) {
    return deuxG ? 47 : 74;
  }
  return deuxG ? 70 : 104;
}

/** Heures restantes estimées pour le niveau de batterie donné. */
export function autonomieRestanteH(batterie: number, intervalleS: number, reseau: string | null, modeAlerte = false): number {
  return Math.round((autonomiePleineH(intervalleS, reseau, modeAlerte) * Math.max(0, Math.min(100, batterie))) / 100);
}

/** « ≈ 41 h » sous deux jours, « ≈ 3 j » au-delà. */
export function duree(heures: number): string {
  return heures < 48 ? `≈ ${heures} h` : `≈ ${Math.round(heures / 24)} j`;
}
