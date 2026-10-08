import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { ClientAuthentification, Session } from './authentification';

const SANS_JETON = ['/api/v1/auth/', '/api/v1/public/'];

const estApiProtegee = (url: string): boolean =>
  url.startsWith('/api/') && !SANS_JETON.some((prefixe) => url.startsWith(prefixe));

const avecJeton = (requete: HttpRequest<unknown>, jeton: string): HttpRequest<unknown> =>
  requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } });

/**
 * Ajoute le jeton d'accès aux appels d'API et, sur un 401, tente un rafraîchissement transparent
 * puis rejoue la requête une seule fois. Si le rafraîchissement échoue, la session est marquée
 * expirée et l'erreur d'origine est propagée.
 */
export const intercepteurJeton: HttpInterceptorFn = (requete, suivant) => {
  if (!estApiProtegee(requete.url)) {
    return suivant(requete);
  }
  const session = inject(Session);
  const client = inject(ClientAuthentification);
  const jeton = session.jetonAcces();

  return suivant(jeton ? avecJeton(requete, jeton) : requete).pipe(
    catchError((erreur: unknown) => {
      if (!(erreur instanceof HttpErrorResponse) || erreur.status !== 401) {
        return throwError(() => erreur);
      }
      return client.rafraichir().pipe(
        catchError(() => throwError(() => erreur)),
        switchMap((nouveau) => suivant(avecJeton(requete, nouveau.jetonAcces))),
      );
    }),
  );
};
