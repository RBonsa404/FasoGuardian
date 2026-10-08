import { HttpErrorResponse } from '@angular/common/http';

import { CodeErreur, estProbleme } from './probleme';

export interface ErreurLisible {
  /** Code métier stable, ou HORS_LIGNE / INCONNUE lorsque le serveur n'a pas répondu en RFC 9457. */
  readonly code: CodeErreur | 'HORS_LIGNE' | 'INCONNUE';
  /** Message à afficher à l'utilisateur. */
  readonly message: string;
}

/**
 * Traduit une erreur HTTP en message utilisateur. Le serveur fournit déjà un détail en français et sans
 * donnée personnelle ; seules la perte de réseau et les réponses inattendues sont formulées par l'application appelante.
 */
export function lireErreur(erreur: unknown, messages: { horsLigne: string; inconnue: string }): ErreurLisible {
  if (erreur instanceof HttpErrorResponse) {
    if (erreur.status === 0) {
      return { code: 'HORS_LIGNE', message: messages.horsLigne };
    }
    if (estProbleme(erreur.error) && erreur.error.detail) {
      return { code: erreur.error.code, message: erreur.error.detail };
    }
  }
  return { code: 'INCONNUE', message: messages.inconnue };
}
