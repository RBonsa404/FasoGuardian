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

/** Litige ouvert sur un lien du parent connecté : l'enfant concerné et les mesures en vigueur, sans le motif. */
export interface LitigeDuTuteur {
  readonly enfant: string;
  readonly geolocalisationSuspendue: boolean;
  readonly ouvertLe: string;
}

/** Côté parent : savoir que les réglages d'un enfant sont gelés, et pourquoi (US-KYC-001). */
@Injectable({ providedIn: 'root' })
export class ClientMesLitiges {
  private readonly http = inject(HttpClient);

  mesLitiges(): Observable<LitigeDuTuteur[]> {
    return this.http.get<LitigeDuTuteur[]>('/api/v1/moi/litiges');
  }
}
