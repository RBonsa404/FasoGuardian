import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { Session } from './authentification';

/** Actions d'un parent soumises à un second facteur par code SMS. */
export type ActionSensible =
  | 'AUTORISER_RETRAIT'
  | 'MODIFIER_SAFE_ZONE'
  | 'ESCALADER_FORCES_SECURITE'
  | 'AJOUTER_TUTEUR'
  | 'CLORE_COMPTE';

/** Client du profil du parent : récupération d'accès, coordonnées, second facteur, clôture (US-PAR-002). */
@Injectable({ providedIn: 'root' })
export class ClientProfil {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  demanderReinitialisation(telephone: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/mot-de-passe/code', { telephone });
  }

  reinitialiser(telephone: string, code: string, motDePasse: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/mot-de-passe/reinitialiser', { telephone, code, motDePasse });
  }

  changerMotDePasse(actuel: string, nouveau: string): Observable<void> {
    return this.http.post<void>('/api/v1/moi/mot-de-passe', { actuel, nouveau });
  }

  demanderCodeNouveauTelephone(telephone: string): Observable<void> {
    return this.http.post<void>('/api/v1/moi/telephone/code', { telephone });
  }

  changerTelephone(telephone: string, code: string, motDePasse: string): Observable<void> {
    return this.http.post<void>('/api/v1/moi/telephone', { telephone, code, motDePasse });
  }

  /** Demande l'envoi par SMS du code de confirmation de l'action. */
  demanderSecondFacteur(action: ActionSensible): Observable<void> {
    return this.http.post<void>('/api/v1/moi/second-facteur', { action });
  }

  clore(codeSecondFacteur: string): Observable<void> {
    return this.http
      .post<void>('/api/v1/moi/cloture', { codeSecondFacteur }, { withCredentials: true })
      .pipe(tap(() => this.session.fermer()));
  }
}
