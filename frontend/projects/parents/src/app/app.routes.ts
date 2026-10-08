import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { ClientAuthentification, Session } from 'api';

/**
 * Réserve une route aux parents connectés. Le jeton d'accès ne vit qu'en mémoire : après un rechargement,
 * la session est rétablie à partir du cookie de rafraîchissement avant de décider.
 */
export const sessionRequise: CanActivateFn = () => {
  const router = inject(Router);
  if (inject(Session).ouverte()) {
    return true;
  }
  return inject(ClientAuthentification)
    .rafraichir()
    .pipe(
      map(() => true),
      catchError(() => of(router.createUrlTree(['/connexion']))),
    );
};

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [sessionRequise],
    loadComponent: () => import('./accueil/accueil').then((m) => m.Accueil),
  },
  { path: 'connexion', loadComponent: () => import('./connexion/connexion').then((m) => m.Connexion) },
  {
    path: 'mot-de-passe-oublie',
    loadComponent: () => import('./compte/mot-de-passe-oublie').then((m) => m.MotDePasseOublie),
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
