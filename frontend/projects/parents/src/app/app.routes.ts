import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { catchError, from, map } from 'rxjs';

import { ClientAuthentification, Session } from 'api';

import { CopieLocale } from './commun/copie-locale';

/**
 * Réserve une route aux parents connectés. Le jeton d'accès ne vit qu'en mémoire : après un rechargement,
 * la session est rétablie à partir du cookie de rafraîchissement avant de décider.
 */
export const sessionRequise: CanActivateFn = () => {
  const router = inject(Router);
  const copie = inject(CopieLocale);
  if (inject(Session).ouverte()) {
    return true;
  }
  return inject(ClientAuthentification)
    .rafraichir()
    .pipe(
      map(() => true),
      // Sans session : la fiche gardée sur l'appareil reste consultable en attendant la reconnexion (US-PAR-019).
      catchError(() => from(copie.fiches()).pipe(map((fiches) => router.createUrlTree([fiches.length > 0 ? '/session' : '/connexion'])))),
    );
};

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [sessionRequise],
    loadComponent: () => import('./accueil/accueil').then((m) => m.Accueil),
  },
  { path: 'session', loadComponent: () => import('./connexion/session-expiree').then((m) => m.SessionExpiree) },
  { path: 'connexion', loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion) },
  {
    path: 'mot-de-passe-oublie',
    loadComponent: () => import('./compte/mot-de-passe-oublie').then((m) => m.MotDePasseOublie),
  },
  {
    path: 'installer',
    canActivate: [sessionRequise],
    loadComponent: () => import('./compte/installer').then((m) => m.Installer),
  },
  {
    path: 'reglages',
    canActivate: [sessionRequise],
    loadComponent: () => import('./compte/reglages').then((m) => m.Reglages),
  },
  { path: 'enfants', canActivate: [sessionRequise], loadComponent: () => import('./enfants/enfants').then((m) => m.Enfants) },
  {
    path: 'enfants/:id',
    canActivate: [sessionRequise],
    loadComponent: () => import('./enfants/enfants').then((m) => m.FicheEnfantEcran),
  },
  {
    path: 'enfants/:id/medical',
    canActivate: [sessionRequise],
    loadComponent: () => import('./enfants/medical').then((m) => m.Medical),
  },
  {
    path: 'enfants/:id/contacts',
    canActivate: [sessionRequise],
    loadComponent: () => import('./enfants/contacts').then((m) => m.Contacts),
  },
  {
    path: 'enfants/:id/carte',
    canActivate: [sessionRequise],
    loadComponent: () => import('./carte/carte-enfant').then((m) => m.CarteEnfant),
  },
  {
    path: 'enfants/:id/zones',
    canActivate: [sessionRequise],
    loadComponent: () => import('./zones/zones').then((m) => m.Zones),
  },
  {
    path: 'enfants/:id/zones/:zid',
    canActivate: [sessionRequise],
    loadComponent: () => import('./zones/edition-zone').then((m) => m.EditionZone),
  },
  {
    path: 'enfants/:id/trajets',
    canActivate: [sessionRequise],
    loadComponent: () => import('./carte/trajets').then((m) => m.Trajets),
  },
  { path: 'alertes', canActivate: [sessionRequise], loadComponent: () => import('./alertes/centre').then((m) => m.CentreAlertes) },
  {
    path: 'alertes/:aid/signalement',
    canActivate: [sessionRequise],
    loadComponent: () => import('./alertes/signalement').then((m) => m.EcranSignalement),
  },
  {
    path: 'alertes/:aid',
    canActivate: [sessionRequise],
    loadComponent: () => import('./alertes/alerte').then((m) => m.EcranAlerte),
  },
  {
    path: 'enfants/:id/journal',
    canActivate: [sessionRequise],
    loadComponent: () => import('./alertes/journal').then((m) => m.JournalAlertes),
  },
  {
    path: 'enfants/:id/bracelet/retrait',
    canActivate: [sessionRequise],
    loadComponent: () => import('./bracelet/retrait').then((m) => m.Retrait),
  },
  {
    path: 'enfants/:id/bracelet',
    canActivate: [sessionRequise],
    loadComponent: () => import('./bracelet/mon-bracelet').then((m) => m.MonBracelet),
  },
  {
    path: 'enfants/:id/bracelet/perte',
    canActivate: [sessionRequise],
    loadComponent: () => import('./bracelet/perte').then((m) => m.PerteBracelet),
  },
  {
    path: 'bracelet/associer',
    canActivate: [sessionRequise],
    loadComponent: () => import('./bracelet/associer').then((m) => m.AssocierBracelet),
  },
  {
    path: 'enfants/:id/partage',
    canActivate: [sessionRequise],
    loadComponent: () => import('./partage/partage').then((m) => m.Partage),
  },
  // Vue du contact secondaire : sans compte, le jeton du lien reçu par SMS tient lieu de preuve.
  { path: 'p/:jeton', loadComponent: () => import('./partage/vue-contact').then((m) => m.VueContact) },
  { path: 'aide', canActivate: [sessionRequise], loadComponent: () => import('./aide/aide').then((m) => m.Aide) },
  {
    path: 'aide/demandes',
    canActivate: [sessionRequise],
    loadComponent: () => import('./aide/demandes').then((m) => m.DemandesSupport),
  },
  {
    path: 'aide/demandes/:tid',
    canActivate: [sessionRequise],
    loadComponent: () => import('./aide/demandes').then((m) => m.FilDemande),
  },
  { path: 'aide/:slug', canActivate: [sessionRequise], loadComponent: () => import('./aide/aide').then((m) => m.ArticleDAide) },
  {
    path: 'abonnement',
    canActivate: [sessionRequise],
    loadComponent: () => import('./abonnement/abonnement').then((m) => m.EcranAbonnement),
  },
  {
    path: 'abonnement/paiement',
    canActivate: [sessionRequise],
    loadComponent: () => import('./abonnement/paiement').then((m) => m.EcranPaiement),
  },
  {
    path: 'abonnement/recus',
    canActivate: [sessionRequise],
    loadComponent: () => import('./abonnement/recus').then((m) => m.Recus),
  },
  { path: 'inscription', pathMatch: 'full', redirectTo: 'inscription/numero' },
  { path: 'inscription/:etape', loadComponent: () => import('./inscription/inscription').then((m) => m.Inscription) },
  { path: 'verification', pathMatch: 'full', redirectTo: 'verification/instruction' },
  {
    path: 'verification/:etape',
    canActivate: [sessionRequise],
    loadComponent: () => import('./verification/verification').then((m) => m.Verification),
  },
  { path: '**', redirectTo: '' },
];
