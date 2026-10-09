import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { catchError, from, map } from 'rxjs';

import { ClientAuthentification, Session } from 'api';

import { CopieLocale } from './commun/copie-locale';
import { entreeSansSession, noterIntroductionVue } from './commun/introduction';

/**
 * Réserve une route aux parents connectés. Le jeton d'accès ne vit qu'en mémoire : après un rechargement,
 * la session est rétablie à partir du cookie de rafraîchissement avant de décider.
 */
export const sessionRequise: CanActivateFn = () => {
  const router = inject(Router);
  const copie = inject(CopieLocale);
  if (inject(Session).ouverte()) {
    // Un appareil sur lequel un parent s'est connecté n'a plus à montrer l'introduction.
    noterIntroductionVue();
    return true;
  }
  return inject(ClientAuthentification)
    .rafraichir()
    .pipe(
      map(() => {
        noterIntroductionVue();
        return true;
      }),
      // Sans session : la fiche gardée sur l'appareil reste consultable en attendant la reconnexion (US-PAR-019).
      catchError(() =>
        from(copie.fiches()).pipe(
          // Première ouverture sur cet appareil : l'introduction passe avant la connexion (écran 9).
          map((fiches) =>
            router.createUrlTree([fiches.length > 0 ? '/session' : entreeSansSession()]),
          ),
        ),
      ),
    );
};

export const routes: Routes = [
  {
    path: 'session',
    loadComponent: () => import('./connexion/session-expiree').then((m) => m.SessionExpiree),
  },
  {
    path: 'bienvenue',
    loadComponent: () => import('./connexion/bienvenue').then((m) => m.Bienvenue),
  },
  {
    path: 'connexion',
    loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion),
  },
  {
    path: 'mot-de-passe-oublie',
    loadComponent: () => import('./compte/mot-de-passe-oublie').then((m) => m.MotDePasseOublie),
  },
  // Vue du contact secondaire : sans compte, le jeton du lien reçu par SMS tient lieu de preuve.
  {
    path: 'p/:jeton',
    loadComponent: () => import('./partage/vue-contact').then((m) => m.VueContact),
  },
  { path: 'inscription', pathMatch: 'full', redirectTo: 'inscription/numero' },
  {
    path: 'inscription/:etape',
    loadComponent: () => import('./inscription/inscription').then((m) => m.Inscription),
  },
  { path: 'verification', pathMatch: 'full', redirectTo: 'verification/instruction' },
  // Écrans du parent connecté, dans la coque qui porte la navigation des grands écrans. La session est
  // contrôlée à l'entrée et à chaque changement d'écran.
  {
    path: '',
    canActivate: [sessionRequise],
    canActivateChild: [sessionRequise],
    loadComponent: () => import('./structure/coque').then((m) => m.Coque),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./accueil/accueil').then((m) => m.Accueil),
      },
      {
        path: 'installer',
        loadComponent: () => import('./compte/installer').then((m) => m.Installer),
      },
      {
        path: 'reglages',
        loadComponent: () => import('./compte/reglages').then((m) => m.Reglages),
      },
      { path: 'enfants', loadComponent: () => import('./enfants/enfants').then((m) => m.Enfants) },
      {
        path: 'enfants/:id',
        loadComponent: () => import('./enfants/enfants').then((m) => m.FicheEnfantEcran),
      },
      {
        path: 'enfants/:id/qr',
        loadComponent: () => import('./enfants/apercu-qr').then((m) => m.ApercuQr),
      },
      {
        path: 'enfants/:id/medical',
        loadComponent: () => import('./enfants/medical').then((m) => m.Medical),
      },
      {
        path: 'enfants/:id/contacts',
        loadComponent: () => import('./enfants/contacts').then((m) => m.Contacts),
      },
      {
        path: 'enfants/:id/carte',
        loadComponent: () => import('./carte/carte-enfant').then((m) => m.CarteEnfant),
      },
      {
        path: 'enfants/:id/zones',
        loadComponent: () => import('./zones/zones').then((m) => m.Zones),
      },
      {
        path: 'enfants/:id/zones/:zid',
        loadComponent: () => import('./zones/edition-zone').then((m) => m.EditionZone),
      },
      {
        path: 'enfants/:id/trajets',
        loadComponent: () => import('./carte/trajets').then((m) => m.Trajets),
      },
      {
        path: 'alertes',
        loadComponent: () => import('./alertes/centre').then((m) => m.CentreAlertes),
      },
      {
        path: 'alertes/:aid/signalement',
        loadComponent: () => import('./alertes/signalement').then((m) => m.EcranSignalement),
      },
      {
        path: 'alertes/:aid',
        loadComponent: () => import('./alertes/alerte').then((m) => m.EcranAlerte),
      },
      {
        path: 'enfants/:id/journal',
        loadComponent: () => import('./alertes/journal').then((m) => m.JournalAlertes),
      },
      {
        path: 'enfants/:id/bracelet/maintenance',
        loadComponent: () => import('./bracelet/maintenance').then((m) => m.MaintenanceBracelet),
      },
      {
        path: 'enfants/:id/bracelet/retrait',
        loadComponent: () => import('./bracelet/retrait').then((m) => m.Retrait),
      },
      {
        path: 'enfants/:id/bracelet',
        loadComponent: () => import('./bracelet/mon-bracelet').then((m) => m.MonBracelet),
      },
      {
        path: 'enfants/:id/bracelet/perte',
        loadComponent: () => import('./bracelet/perte').then((m) => m.PerteBracelet),
      },
      {
        path: 'bracelet/associer',
        loadComponent: () => import('./bracelet/associer').then((m) => m.AssocierBracelet),
      },
      {
        path: 'enfants/:id/partage',
        loadComponent: () => import('./partage/partage').then((m) => m.Partage),
      },
      { path: 'aide', loadComponent: () => import('./aide/aide').then((m) => m.Aide) },
      {
        path: 'aide/demandes',
        loadComponent: () => import('./aide/demandes').then((m) => m.DemandesSupport),
      },
      {
        path: 'aide/demandes/:tid',
        loadComponent: () => import('./aide/demandes').then((m) => m.FilDemande),
      },
      {
        path: 'aide/:slug',
        loadComponent: () => import('./aide/aide').then((m) => m.ArticleDAide),
      },
      {
        path: 'abonnement',
        loadComponent: () => import('./abonnement/abonnement').then((m) => m.EcranAbonnement),
      },
      {
        path: 'abonnement/paiement',
        loadComponent: () => import('./abonnement/paiement').then((m) => m.EcranPaiement),
      },
      {
        path: 'abonnement/recus',
        loadComponent: () => import('./abonnement/recus').then((m) => m.Recus),
      },
      {
        path: 'verification/:etape',
        loadComponent: () => import('./verification/verification').then((m) => m.Verification),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
