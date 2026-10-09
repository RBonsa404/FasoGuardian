import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Partage temporaire de la position, vu par le parent. */
export interface PartagePosition {
  readonly id: string;
  /** Lien du contact avec l'enfant (« Oncle »). */
  readonly lien: string;
  /** Numéro du contact, masqué. */
  readonly destinataire: string;
  readonly debut: string;
  readonly fin: string;
  readonly ouvertures: number;
  readonly derniereOuverture: string | null;
}

export interface PositionPartagee {
  readonly latitude: number;
  readonly longitude: number;
  readonly precisionM: number;
  readonly approximative: boolean;
  readonly mesureeLe: string;
}

/** Ce que voit le contact qui ouvre son lien : ni le nom de l'enfant ni son historique. */
export interface VuePartagee {
  /** Prénom du parent qui partage, s'il est connu. */
  readonly partagePar: string | null;
  readonly fin: string;
  /** `null` si le bracelet n'a pas donné de position depuis deux heures. */
  readonly position: PositionPartagee | null;
}

/** Client du partage temporaire de la position (US-SEC-001). */
@Injectable({ providedIn: 'root' })
export class ClientPartages {
  private readonly http = inject(HttpClient);

  /** Répond 404 quand aucun partage n'est en cours. */
  enCours(enfantId: string): Observable<PartagePosition> {
    return this.http.get<PartagePosition>(this.base(enfantId));
  }

  /** Le lien part par SMS au contact ; un partage déjà en cours est révoqué. */
  partager(enfantId: string, contactId: string, dureeMinutes: number): Observable<PartagePosition> {
    return this.http.post<PartagePosition>(this.base(enfantId), { contactId, dureeMinutes });
  }

  revoquer(enfantId: string): Observable<void> {
    return this.http.delete<void>(this.base(enfantId));
  }

  /** Appel sans session : le jeton du lien tient lieu de preuve. Répond 404 quand le partage est terminé. */
  consulter(jeton: string): Observable<VuePartagee> {
    return this.http.get<VuePartagee>(`/api/v1/public/partages/${encodeURIComponent(jeton)}`);
  }

  private base(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/partage`;
  }
}
