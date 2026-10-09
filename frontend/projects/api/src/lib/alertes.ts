import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type TypeAlerte = 'SOS' | 'RETRAIT' | 'SIGNALEMENT' | 'SORTIE_ZONE' | 'BATTERIE_CRITIQUE' | 'CHUTE';
export type GraviteAlerte = 'CRITIQUE' | 'IMPORTANTE';
export type StatutAlerte = 'OUVERTE' | 'ACQUITTEE' | 'ESCALADEE' | 'LEVEE' | 'FAUSSE_ALERTE';
export type TypeActionAlerte = 'OUVERTURE' | 'ACQUITTEMENT' | 'ESCALADE' | 'LEVEE' | 'FAUSSE_ALERTE' | 'RESOLUTION';

/** Ligne du journal d'acquittement. L'identité d'un autre tuteur n'est jamais transmise. */
export interface ActionAlerte {
  readonly type: TypeActionAlerte;
  readonly auteur: 'VOUS' | 'AUTRE_TUTEUR' | 'SYSTEME';
  readonly motif: string | null;
  readonly effectueeLe: string;
}

export interface Alerte {
  readonly id: string;
  readonly enfantId: string;
  readonly type: TypeAlerte;
  readonly gravite: GraviteAlerte;
  readonly statut: StatutAlerte;
  readonly ouverteLe: string;
  readonly closeLe: string | null;
  /** Nom de la zone pour une sortie de zone. */
  readonly libelle: string | null;
  /** Dernière position connue au déclenchement, si le bracelet l'a jointe. */
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly actions: readonly ActionAlerte[];
}

export type MotifRetrait = 'TOILETTE' | 'RECHARGE' | 'NUIT' | 'AUTRE';

export interface AutorisationRetrait {
  readonly id: string;
  readonly motif: MotifRetrait;
  readonly debut: string;
  readonly fin: string;
  /** Le bracelet a été retiré pendant la fenêtre et n'a pas encore été remis. */
  readonly retire: boolean;
}

/** Client des alertes et de l'autorisation de retrait (US-ENF-001, US-PAR-008, 010, 012). */
@Injectable({ providedIn: 'root' })
export class ClientAlertes {
  private readonly http = inject(HttpClient);

  /** Alertes de mes enfants, les plus récentes d'abord. */
  mesAlertes(enCours: boolean): Observable<Alerte[]> {
    return this.http.get<Alerte[]>('/api/v1/alertes', { params: { enCours } });
  }

  alerte(id: string): Observable<Alerte> {
    return this.http.get<Alerte>(`/api/v1/alertes/${id}`);
  }

  acquitter(id: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/acquittement`, {});
  }

  /** Une seule prise en charge pour toutes les alertes ouvertes de l'enfant. */
  prendreEnCharge(enfantId: string): Observable<Alerte[]> {
    return this.http.post<Alerte[]>(`/api/v1/enfants/${enfantId}/alertes/prise-en-charge`, {});
  }

  lever(id: string, motif: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/levee`, { motif });
  }

  classerFausseAlerte(id: string, motif: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/fausse-alerte`, { motif });
  }

  signaler(enfantId: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/enfants/${enfantId}/signalement`, {});
  }

  /** Journal des alertes de l'enfant ; consultation journalisée. */
  journal(enfantId: string): Observable<Alerte[]> {
    return this.http.get<Alerte[]>(`/api/v1/enfants/${enfantId}/journal`);
  }

  /** Échoue en RESSOURCE_INTROUVABLE si aucun retrait n'est autorisé. */
  retraitEnCours(enfantId: string): Observable<AutorisationRetrait> {
    return this.http.get<AutorisationRetrait>(this.retrait(enfantId));
  }

  /** Second facteur AUTORISER_RETRAIT requis. */
  autoriserRetrait(enfantId: string, motif: MotifRetrait, dureeMinutes: number, codeSecondFacteur: string): Observable<AutorisationRetrait> {
    return this.http.post<AutorisationRetrait>(this.retrait(enfantId), { motif, dureeMinutes, codeSecondFacteur });
  }

  prolongerRetrait(enfantId: string, minutes: number, codeSecondFacteur: string): Observable<AutorisationRetrait> {
    return this.http.post<AutorisationRetrait>(`${this.retrait(enfantId)}/prolongation`, { minutes, codeSecondFacteur });
  }

  /** Bracelet remis : la surveillance du retrait reprend. */
  terminerRetrait(enfantId: string): Observable<void> {
    return this.http.delete<void>(this.retrait(enfantId));
  }

  private retrait(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/bracelet/retrait`;
  }
}
