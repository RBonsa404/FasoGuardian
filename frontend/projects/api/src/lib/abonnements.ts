import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CodeOffre = 'ESSENTIEL' | 'INTERMEDIAIRE' | 'PREMIUM' | 'ECOLE';
export type MoyenPaiement = 'ORANGE_MONEY' | 'MOOV_MONEY';
export type StatutAbonnement = 'ACTIF' | 'EN_RETARD' | 'RESTREINT';
export type StatutPaiement = 'INITIE' | 'CONFIRME' | 'ECHOUE' | 'EXPIRE';

export interface Offre {
  readonly code: CodeOffre;
  readonly libelle: string;
  readonly prixFcfa: number;
  /** Intervalle entre deux positions, en secondes. */
  readonly intervalleS: number;
  readonly zonesMaximum: number;
  readonly historiqueJours: number;
}

/** Ce que l'abonnement ouvre en ce moment. SOS, détection de retrait et page QR n'en dépendent pas. */
export interface Droits {
  readonly offre: string;
  readonly zonesMaximum: number;
  readonly historiqueJours: number;
  readonly intervalleS: number;
  /** Faux quand un impayé a suspendu la géolocalisation continue. */
  readonly suiviContinu: boolean;
}

export interface Paiement {
  readonly id: string;
  readonly statut: StatutPaiement;
  readonly offre: CodeOffre;
  readonly montantFcfa: number;
  readonly moyen: MoyenPaiement;
  /** Fin du délai laissé pour valider sur le téléphone (date ISO). */
  readonly expireLe: string;
  readonly motifEchec: string | null;
  /** Numéro du reçu, une fois le paiement confirmé. */
  readonly recu: string | null;
}

export interface Abonnement {
  /** `null` tant qu'aucun paiement n'a été confirmé pour cet enfant. */
  readonly statut: StatutAbonnement | null;
  readonly offre: CodeOffre | null;
  readonly libelle: string | null;
  readonly prixFcfa: number | null;
  /** Date ISO (AAAA-MM-JJ). */
  readonly prochaineEcheance: string | null;
  /** Jour où les fonctions avancées seront suspendues si l'échéance reste impayée. */
  readonly restrictionLe: string | null;
  readonly renouvellementAuto: boolean;
  readonly moyen: MoyenPaiement | null;
  /** Portefeuille retenu pour le renouvellement, en partie masqué. */
  readonly numeroMasque: string | null;
  readonly droits: Droits;
  readonly paiementEnCours: Paiement | null;
}

export interface SaisiePaiement {
  readonly offre: CodeOffre;
  readonly moyen: MoyenPaiement;
  /** Les 8 chiffres du portefeuille à solliciter. */
  readonly numero: string;
  readonly renouvellementAuto: boolean;
}

export interface Recu {
  /** FG-R-2026-10-0214. */
  readonly numero: string;
  readonly offre: string;
  readonly montantFcfa: number;
  readonly moyen: MoyenPaiement;
  readonly numeroMasque: string;
  readonly periodeDebut: string;
  /** Fin de période exclue. */
  readonly periodeFin: string;
  readonly emisLe: string;
}

/** Client des abonnements, du paiement mobile money et des reçus (US-PAR-015). */
@Injectable({ providedIn: 'root' })
export class ClientAbonnements {
  private readonly http = inject(HttpClient);

  offres(): Observable<Offre[]> {
    return this.http.get<Offre[]>('/api/v1/offres');
  }

  abonnement(enfantId: string): Observable<Abonnement> {
    return this.http.get<Abonnement>(this.base(enfantId));
  }

  /**
   * Demande un paiement. La clé d'idempotence est tirée une fois par tentative : la même demande rejouée
   * sur un réseau instable ne sollicite pas le portefeuille une seconde fois.
   */
  payer(enfantId: string, saisie: SaisiePaiement, cleIdempotence: string): Observable<Paiement> {
    return this.http.post<Paiement>(`${this.base(enfantId)}/paiements`, saisie, { headers: { 'Idempotency-Key': cleIdempotence } });
  }

  paiement(id: string): Observable<Paiement> {
    return this.http.get<Paiement>(`/api/v1/paiements/${id}`);
  }

  choisirRenouvellement(enfantId: string, automatique: boolean): Observable<Abonnement> {
    return this.http.put<Abonnement>(`${this.base(enfantId)}/renouvellement`, { automatique });
  }

  recus(): Observable<Recu[]> {
    return this.http.get<Recu[]>('/api/v1/recus');
  }

  recuPdf(numero: string): Observable<Blob> {
    return this.http.get(`/api/v1/recus/${encodeURIComponent(numero)}/pdf`, { responseType: 'blob' });
  }

  private base(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/abonnement`;
  }
}
