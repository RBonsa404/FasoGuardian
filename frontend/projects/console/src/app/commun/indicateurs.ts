import { IndicateurConsole } from 'api';

/** Libellé et précision d'un indicateur, à partir de sa valeur et de son repère. */
type Libelle = (i: IndicateurConsole) => { libelle: string; valeur: string; detail: string };

const nombre = new Intl.NumberFormat('fr-FR');
const compte = (i: IndicateurConsole) => (i.valeur === null ? '—' : nombre.format(i.valeur));

const LIBELLES: Record<string, Libelle> = {
  'kyc.enFile': (i) => ({ libelle: $localize`:@@ind.kyc.enFile:En file`, valeur: compte(i), detail: $localize`:@@ind.kyc.enFile.d:déposés ou en instruction` }),
  'kyc.prioritaires': (i) => ({ libelle: $localize`:@@ind.kyc.prio:> ${i.repere}:heures: h`, valeur: compte(i), detail: $localize`:@@ind.kyc.prio.d:à traiter en priorité` }),
  'kyc.delaiMedian': (i) => ({
    libelle: $localize`:@@ind.kyc.delai:Délai médian`,
    valeur: i.valeur === null ? '—' : $localize`:@@ind.heures:${nombre.format(i.valeur)}:n: h`,
    detail: $localize`:@@ind.kyc.delai.d:objectif ${i.repere}:heures: h · 30 derniers jours`,
  }),
  'kyc.mesDecisions': (i) => ({ libelle: $localize`:@@ind.kyc.miennes:Mes décisions · jour`, valeur: compte(i), detail: $localize`:@@ind.depuisMinuit:depuis minuit` }),
  'support.ouvertes': (i) => ({ libelle: $localize`:@@ind.sup.ouvertes:Demandes ouvertes`, valeur: compte(i), detail: $localize`:@@ind.sup.ouvertes.d:à lire et à traiter` }),
  'support.enRetard': (i) => ({ libelle: $localize`:@@ind.sup.retard:Sans réponse > ${i.repere}:heures: h`, valeur: compte(i), detail: $localize`:@@ind.prio:à traiter en priorité` }),
  'support.attenteParent': (i) => ({ libelle: $localize`:@@ind.sup.parent:En attente du parent`, valeur: compte(i), detail: $localize`:@@ind.sup.parent.d:réponse envoyée` }),
  'support.mesReponses': (i) => ({ libelle: $localize`:@@ind.sup.miennes:Mes réponses · jour`, valeur: compte(i), detail: $localize`:@@ind.depuisMinuit:depuis minuit` }),
  'sav.muets': (i) => ({ libelle: $localize`:@@ind.sav.muets:Bracelets muets`, valeur: compte(i), detail: $localize`:@@ind.sav.muets.d:tickets en cours` }),
  'sav.sansAgent': (i) => ({ libelle: $localize`:@@ind.sav.sansAgent:Non attribués`, valeur: compte(i), detail: $localize`:@@ind.sav.sansAgent.d:à prendre en charge` }),
  'sav.auSav': (i) => ({ libelle: $localize`:@@ind.sav.auSav:Unités au SAV`, valeur: compte(i), detail: $localize`:@@ind.sav.auSav.d:retournées ou en panne` }),
  'sav.enStock': (i) => ({
    libelle: $localize`:@@ind.sav.stock:En stock`,
    valeur: compte(i),
    detail: i.alerte ? $localize`:@@ind.sav.stock.vide:stock épuisé` : $localize`:@@ind.sav.stock.d:prêtes à être remises`,
  }),
  'fds.mesAccusesJour': (i) => ({ libelle: $localize`:@@ind.fds.jour:Mes accusés · jour`, valeur: compte(i), detail: $localize`:@@ind.depuisMinuit:depuis minuit` }),
  'fds.mesAccuses30j': (i) => ({ libelle: $localize`:@@ind.fds.30j:Mes accusés · 30 jours`, valeur: compte(i), detail: $localize`:@@ind.fds.30j.d:accès fermé au-delà` }),
  'admin.refus24h': (i) => ({ libelle: $localize`:@@ind.adm.refus:Refus d'accès · 24 h`, valeur: compte(i), detail: $localize`:@@ind.adm.refus.d:détail dans le journal d'audit` }),
  'admin.demandesDroits': (i) => ({ libelle: $localize`:@@ind.adm.droits:Demandes de droits`, valeur: compte(i), detail: $localize`:@@ind.adm.droits.d:accès et effacement à traiter` }),
  'admin.echeancesProches': (i) => ({ libelle: $localize`:@@ind.adm.echeance:Échéance < ${i.repere}:jours: j`, valeur: compte(i), detail: $localize`:@@ind.adm.echeance.d:à traiter avant l'échéance` }),
};

/** Indicateur mis en mots ; un code inconnu de cette version de la console s'affiche tel quel. */
export function libeller(i: IndicateurConsole): { libelle: string; valeur: string; detail: string } {
  return (LIBELLES[i.code] ?? ((x: IndicateurConsole) => ({ libelle: x.code, valeur: compte(x), detail: '' })))(i);
}
