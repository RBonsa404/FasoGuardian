import { RoleInterne } from 'api';
import { NomIcone } from 'ui';

/** Écrans de la console ; ceux d'un rôle ne sont proposés qu'à ses agents. */
export interface Entree {
  readonly libelle: string;
  readonly icone: NomIcone;
  readonly lien: string;
  readonly role?: RoleInterne;
}

export const ENTREES: readonly Entree[] = [
  { libelle: $localize`:@@nav.tableau:Tableau de bord`, icone: 'maison', lien: '/' },
  { libelle: $localize`:@@nav.kyc:File KYC`, icone: 'kyc-piece', lien: '/kyc', role: 'KYC' },
  { libelle: $localize`:@@nav.litiges:Litiges de filiation`, icone: 'filiation', lien: '/kyc/litiges', role: 'KYC' },
  { libelle: $localize`:@@nav.tickets:Tickets support`, icone: 'support', lien: '/support/tickets', role: 'SUPPORT' },
  { libelle: $localize`:@@nav.faq:Base de connaissances`, icone: 'info', lien: '/support/faq', role: 'SUPPORT' },
  { libelle: $localize`:@@nav.fds:Signalements`, icone: 'bouclier', lien: '/fds', role: 'FDS' },
  { libelle: $localize`:@@nav.parc:Parc de bracelets`, icone: 'bracelet', lien: '/sav/parc', role: 'SAV' },
  { libelle: $localize`:@@nav.muets:Bracelets muets`, icone: 'hors-ligne', lien: '/sav/muets', role: 'SAV' },
  { libelle: $localize`:@@nav.ota:Campagnes OTA`, icone: 'charge', lien: '/sav/ota', role: 'SAV' },
  { libelle: $localize`:@@nav.agents:Agents et rôles`, icone: 'agent', lien: '/admin/agents', role: 'ADMIN' },
  { libelle: $localize`:@@nav.audit:Journal d'audit`, icone: 'audit', lien: '/admin/audit', role: 'ADMIN' },
  { libelle: $localize`:@@nav.conformite:Conformité CIL`, icone: 'cadenas', lien: '/conformite', role: 'ADMIN' },
  { libelle: $localize`:@@nav.supervision:Supervision`, icone: 'historique', lien: '/admin/supervision', role: 'ADMIN' },
  { libelle: $localize`:@@nav.securite:Sécurité`, icone: 'bouclier', lien: '/admin/securite', role: 'ADMIN' },
  { libelle: $localize`:@@nav.parametres:Paramétrage`, icone: 'reglages', lien: '/admin/parametres', role: 'ADMIN' },
];
