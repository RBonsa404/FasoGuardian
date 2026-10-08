import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { ClientAuthentification, ErreurLisible, RoleInterne, Session, lireErreur } from 'api';

export const LIBELLES_ROLES: Record<RoleInterne, string> = {
  KYC: $localize`:@@role.kyc:Agent KYC`,
  SUPPORT: $localize`:@@role.support:Opérateur support`,
  SAV: $localize`:@@role.sav:Agent SAV`,
  ADMIN: $localize`:@@role.admin:Administrateur`,
  FDS: $localize`:@@role.fds:Forces de sécurité`,
};

export function erreurLisible(erreur: unknown): ErreurLisible {
  return lireErreur(erreur, {
    horsLigne: $localize`:@@erreur.horsLigne:Pas de connexion au serveur. Vérifiez votre réseau puis réessayez.`,
    inconnue: $localize`:@@erreur.inconnue:Une erreur est survenue. Réessayez dans un instant.`,
  });
}

export const estRefus = (erreur: unknown): boolean => erreur instanceof HttpErrorResponse && erreur.status === 403;

/** Réserve la console aux agents connectés ; la session est rétablie depuis le cookie après un rechargement. */
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

/**
 * N'ouvre un écran qu'aux agents portant le rôle voulu. Simple confort d'affichage : le serveur refuse
 * et journalise de toute façon toute requête hors périmètre.
 */
export const roleRequis =
  (role: RoleInterne): CanActivateFn =>
  () =>
    inject(Session).roles().includes(role) ? true : inject(Router).createUrlTree(['/refuse'], { queryParams: { role } });
