import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { tap, throwError } from 'rxjs';

/** Information réseau des navigateurs qui la donnent (Chrome pour Android, WebView) ; absente ailleurs. */
interface InformationReseau extends EventTarget {
  readonly effectiveType?: 'slow-2g' | '2g' | '3g' | '4g';
  readonly saveData?: boolean;
}

/**
 * Qualité de la liaison avec le serveur (écran 49) : connexion lente, d'après ce que le navigateur en dit, et
 * service indisponible, d'après les réponses du serveur. Sur une connexion lente, les photos des enfants ne sont
 * pas chargées (FG-DOC-06 §5.5, frugalité réseau) : l'écran garde ses pictogrammes.
 */
@Injectable({ providedIn: 'root' })
export class EtatReseau {
  readonly lent = signal(false);
  readonly serveurIndisponible = signal(false);

  constructor() {
    const reseau = typeof navigator === 'undefined' ? undefined : (navigator as Navigator & { connection?: InformationReseau }).connection;
    if (reseau) {
      const lire = () => this.lent.set(reseau.saveData === true || reseau.effectiveType === 'slow-2g' || reseau.effectiveType === '2g');
      lire();
      reseau.addEventListener('change', lire);
    }
  }
}

/** Les photos des enfants sont des images à la demande : sur une connexion lente, elles ne partent pas. */
const PHOTO = /^\/api\/v1\/enfants\/[0-9a-f-]{36}\/photo$/;

export const intercepteurEtatReseau: HttpInterceptorFn = (requete, suivant) => {
  const etat = inject(EtatReseau);
  if (requete.method === 'GET' && etat.lent() && PHOTO.test(requete.url)) {
    return throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Connexion lente : photo non chargée', url: requete.url }));
  }
  return suivant(requete).pipe(
    tap({
      next: (evenement) => {
        if (evenement instanceof HttpResponse) {
          etat.serveurIndisponible.set(false);
        }
      },
      error: (erreur: unknown) => {
        if (erreur instanceof HttpErrorResponse && erreur.status >= 500) {
          etat.serveurIndisponible.set(true);
        }
      },
    }),
  );
};
