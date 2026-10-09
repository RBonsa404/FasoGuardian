import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type DecisionLitige = 'LIEN_MAINTENU' | 'LIEN_RETIRE';
export type FondementLitige = 'DECISION_DE_JUSTICE' | 'ACCORD_ECRIT';

/** Litige de filiation sur un compte actif (US-KYC-001), vu par l'agent KYC. */
export interface Litige {
  readonly id: string;
  /** LIT-000031. */
  readonly reference: string;
  /** Dossier KYC qui a établi le lien contesté. */
  readonly dossierKyc: string | null;
  /** Identités vérifiées du dossier : le tuteur contesté et le prénom de l'enfant. */
  readonly tuteur: string | null;
  readonly enfant: string | null;
  readonly motif: string;
  readonly statut: 'OUVERT' | 'CLOS';
  readonly geolocalisationSuspendue: boolean;
  readonly ouvertLe: string;
  readonly echeanceLe: string;
  readonly decision: DecisionLitige | null;
  readonly fondement: FondementLitige | null;
  readonly referenceDuFondement: string | null;
  readonly decideLe: string | null;
}

const LITIGES = '/api/v1/console/kyc/litiges';

/** Client de l'instruction des litiges de filiation, réservé aux agents KYC. */
@Injectable({ providedIn: 'root' })
export class ClientLitiges {
  private readonly http = inject(HttpClient);

  litiges(): Observable<Litige[]> {
    return this.http.get<Litige[]>(LITIGES);
  }

  /** Consultation journalisée. */
  litige(id: string): Observable<Litige> {
    return this.http.get<Litige>(`${LITIGES}/${id}`);
  }

  /** Gèle les réglages du tuteur contesté et prévient les parties. */
  ouvrir(dossierKyc: string, motif: string, suspendreLaGeolocalisation: boolean): Observable<Litige> {
    return this.http.post<Litige>(LITIGES, { dossierKyc, motif, suspendreLaGeolocalisation });
  }

  suspendreLaGeolocalisation(id: string, suspendue: boolean): Observable<Litige> {
    return this.http.put<Litige>(`${LITIGES}/${id}/geolocalisation`, { suspendue });
  }

  /** La décision est appliquée, journalisée et notifiée aux parties. */
  decider(id: string, decision: DecisionLitige, fondement: FondementLitige, referenceDuFondement: string): Observable<Litige> {
    return this.http.post<Litige>(`${LITIGES}/${id}/decision`, { decision, fondement, referenceDuFondement: referenceDuFondement || null });
  }
}
