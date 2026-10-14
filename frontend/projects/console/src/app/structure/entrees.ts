import { RoleInterne } from 'api';

/** Écrans de la console ; ceux d'un rôle ne sont proposés qu'à ses agents. */
export interface Entree {
  readonly libelle: string;
  readonly lien: string;
  readonly role?: RoleInterne;
}

export const ENTREES: readonly Entree[] = [
  { libelle: $localize`:@@nav.tableau:Tableau de bord`, lien: '/' },
  { libelle: $localize`:@@nav.kyc:File KYC`, lien: '/kyc', role: 'KYC' },
  { libelle: $localize`:@@nav.litiges:Litiges de filiation`, lien: '/kyc/litiges', role: 'KYC' },
  { libelle: $localize`:@@nav.tickets:Tickets support`, lien: '/support/tickets', role: 'SUPPORT' },
  { libelle: $localize`:@@nav.faq:Base de connaissances`, lien: '/support/faq', role: 'SUPPORT' },
  { libelle: $localize`:@@nav.fds:Signalements`, lien: '/fds', role: 'FDS' },
  { libelle: $localize`:@@nav.parc:Parc de bracelets`, lien: '/sav/parc', role: 'SAV' },
  { libelle: $localize`:@@nav.muets:Bracelets muets`, lien: '/sav/muets', role: 'SAV' },
  { libelle: $localize`:@@nav.ota:Campagnes OTA`, lien: '/sav/ota', role: 'SAV' },
  { libelle: $localize`:@@nav.agents:Agents et rôles`, lien: '/admin/agents', role: 'ADMIN' },
  { libelle: $localize`:@@nav.audit:Journal d'audit`, lien: '/admin/audit', role: 'ADMIN' },
  { libelle: $localize`:@@nav.conformite:Conformité CIL`, lien: '/conformite', role: 'ADMIN' },
  { libelle: $localize`:@@nav.supervision:Supervision`, lien: '/admin/supervision', role: 'ADMIN' },
  { libelle: $localize`:@@nav.securite:Sécurité`, lien: '/admin/securite', role: 'ADMIN' },
  { libelle: $localize`:@@nav.parametres:Paramétrage`, lien: '/admin/parametres', role: 'ADMIN' },
];
