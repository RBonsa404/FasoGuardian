import { PeriodeAppairage, ResolutionTicket, StatutBraceletParc } from 'api';
import { TonBadge } from 'ui';

export const STATUTS: readonly StatutBraceletParc[] = ['EN_STOCK', 'ACTIF', 'EN_SAV', 'PERDU', 'VOLE', 'REFORME'];

export const LIBELLES_STATUT: Record<StatutBraceletParc, string> = {
  EN_STOCK: $localize`:@@parc.statut.stock:En stock`,
  ACTIF: $localize`:@@parc.statut.actif:Actif`,
  EN_SAV: $localize`:@@parc.statut.sav:En SAV`,
  PERDU: $localize`:@@parc.statut.perdu:Perdu`,
  VOLE: $localize`:@@parc.statut.vole:Volé`,
  REFORME: $localize`:@@parc.statut.reforme:Réformé`,
};

export const TONS_STATUT: Record<StatutBraceletParc, TonBadge> = {
  EN_STOCK: 'neutre',
  ACTIF: 'succes',
  EN_SAV: 'accent',
  PERDU: 'attention',
  VOLE: 'attention',
  REFORME: 'neutre',
};

export const LIBELLES_MOTIF_FIN: Record<NonNullable<PeriodeAppairage['motifFin']>, string> = {
  DESAPPAIRAGE: $localize`:@@parc.fin.desappairage:Désappairage`,
  PERTE: $localize`:@@parc.fin.perte:Perte`,
  VOL: $localize`:@@parc.fin.vol:Vol`,
  PANNE: $localize`:@@parc.fin.panne:Panne`,
  REMPLACEMENT: $localize`:@@parc.fin.remplacement:Remplacement`,
};

/** Issues qu'un agent peut donner à un ticket ; la reprise spontanée est constatée par la supervision. */
export const RESOLUTIONS: readonly { code: ResolutionTicket; libelle: string }[] = [
  { code: 'RECHARGE', libelle: $localize`:@@ticket.resolution.recharge:Bracelet rechargé par la famille` },
  { code: 'ECHANGE', libelle: $localize`:@@ticket.resolution.echange:Échange en point relais` },
  { code: 'RETOUR_ATELIER', libelle: $localize`:@@ticket.resolution.atelier:Retour à l'atelier` },
  { code: 'SANS_SUITE', libelle: $localize`:@@ticket.resolution.sansSuite:Sans suite` },
];

export const LIBELLES_RESOLUTION: Record<ResolutionTicket, string> = {
  REPRISE_SPONTANEE: $localize`:@@ticket.resolution.reprise:Le bracelet a redonné des nouvelles`,
  RECHARGE: RESOLUTIONS[0].libelle,
  ECHANGE: RESOLUTIONS[1].libelle,
  RETOUR_ATELIER: RESOLUTIONS[2].libelle,
  SANS_SUITE: RESOLUTIONS[3].libelle,
};

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
const HEURE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

export function jour(iso: string | null): string {
  return iso ? JOUR.format(new Date(iso)) : '—';
}

export function jourEtHeure(iso: string | null): string {
  return iso ? HEURE.format(new Date(iso)) : '—';
}

/** Durée écoulée depuis une date, en minutes puis en heures. */
export function depuis(iso: string, maintenant = Date.now()): string {
  const minutes = Math.max(0, Math.floor((maintenant - Date.parse(iso)) / 60_000));
  return minutes < 90 ? $localize`:@@duree.minutes:${minutes}:minutes: min` : $localize`:@@duree.heures:${Math.floor(minutes / 60)}:heures: h`;
}
