import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type StatutBracelet = 'EN_STOCK' | 'ACTIF' | 'PERDU' | 'VOLE' | 'EN_SAV' | 'REFORME';

export type MotifDeclaration = 'PERDU' | 'VOLE' | 'CASSE';

export interface Bracelet {
  /** Numéro gravé sur le bracelet (FG-2291). */
  readonly numeroSerie: string;
  readonly statut: StatutBracelet;
  readonly versionLogiciel: string;
  readonly revisionMaterielle: string;
  /** Date ISO (AAAA-MM-JJ). */
  readonly garantieJusquAu: string | null;
  readonly modeEconomie: boolean;
  /** Intervalle entre deux positions dans le mode courant, en secondes. */
  readonly intervalleS: number;
  readonly appaireLe: string;
  /** Après une déclaration de perte, le suivi continue jusqu'à cette date. */
  readonly suiviJusquAu: string | null;
}

/** Client du bracelet d'un enfant : appairage, configuration, perte, vol, désappairage (US-PAR-013, 014). */
@Injectable({ providedIn: 'root' })
export class ClientBracelet {
  private readonly http = inject(HttpClient);

  /** Échoue en RESSOURCE_INTROUVABLE si l'enfant ne porte aucun bracelet. */
  bracelet(enfantId: string): Observable<Bracelet> {
    return this.http.get<Bracelet>(this.base(enfantId));
  }

  /** Associe le bracelet dont le code figure sur la carte d'activation. */
  associer(enfantId: string, code: string): Observable<Bracelet> {
    return this.http.post<Bracelet>(`${this.base(enfantId)}/appairage`, { code });
  }

  /** Second facteur DECLARER_BRACELET requis. */
  declarer(enfantId: string, motif: MotifDeclaration, codeSecondFacteur: string): Observable<Bracelet> {
    return this.http.post<Bracelet>(`${this.base(enfantId)}/declaration`, { motif, codeSecondFacteur });
  }

  retrouver(enfantId: string): Observable<Bracelet> {
    return this.http.post<Bracelet>(`${this.base(enfantId)}/retrouve`, {});
  }

  reglerModeEconomie(enfantId: string, modeEconomie: boolean): Observable<Bracelet> {
    return this.http.put<Bracelet>(`${this.base(enfantId)}/configuration`, { modeEconomie });
  }

  desappairer(enfantId: string): Observable<void> {
    return this.http.delete<void>(this.base(enfantId));
  }

  private base(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/bracelet`;
  }
}
