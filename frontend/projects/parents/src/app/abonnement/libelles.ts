import { MoyenPaiement, Offre } from 'api';

const NOMBRE = new Intl.NumberFormat('fr-FR');
const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const JOUR_LONG = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const MOIS = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric', timeZone: 'UTC' });

/** « 2250 » s'écrit « 2 250 ». */
export function montant(fcfa: number): string {
  return NOMBRE.format(fcfa);
}

/** Date ISO (AAAA-MM-JJ) en « 7 nov. ». */
export function jourCourt(iso: string): string {
  return JOUR.format(new Date(iso));
}

/** Date ISO (AAAA-MM-JJ) en « 7 décembre 2026 ». */
export function jourLong(iso: string): string {
  return JOUR_LONG.format(new Date(iso));
}

/** Date ISO en « Octobre 2026 ». */
export function mois(iso: string): string {
  const texte = MOIS.format(new Date(iso));
  return texte.charAt(0).toUpperCase() + texte.slice(1);
}

export function nomMoyen(moyen: MoyenPaiement): string {
  return moyen === 'ORANGE_MONEY' ? 'Orange Money' : 'Moov Money';
}

/** Ce que l'offre ouvre, en une ligne. */
export function resumeOffre(offre: Offre): string {
  const minutes = Math.round(offre.intervalleS / 60);
  const zones =
    offre.zonesMaximum > 1
      ? $localize`:@@abonnement.offre.zones:${offre.zonesMaximum}:nombre: Safe Zones`
      : $localize`:@@abonnement.offre.zone:1 Safe Zone`;
  const historique =
    offre.historiqueJours > 1
      ? $localize`:@@abonnement.offre.historique:historique ${offre.historiqueJours}:jours: jours`
      : $localize`:@@abonnement.offre.historique24h:historique 24 h`;
  return $localize`:@@abonnement.offre.resume:Position toutes les ${minutes}:minutes: min · ${zones}:zones: · ${historique}:historique:`;
}
