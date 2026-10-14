import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RoleInterne } from './console';

/** Indicateur d'une file de travail : un décompte ou une durée, jamais une donnée personnelle. */
export interface IndicateurConsole {
  /** Identifiant stable, mis en mots par la console (« kyc.enFile »). */
  readonly code: string;
  /** Absent quand il n'y a rien à mesurer (aucune décision sur la période). */
  readonly valeur: number | null;
  /** Objectif ou seuil associé. */
  readonly repere: number | null;
  /** La valeur appelle une action. */
  readonly alerte: boolean;
}

export interface GroupeIndicateurs {
  readonly role: RoleInterne;
  readonly indicateurs: readonly IndicateurConsole[];
}

/** Tableau de bord de la console (écran 57) : les indicateurs des seules files des rôles de l'agent. */
@Injectable({ providedIn: 'root' })
export class ClientTableau {
  private readonly http = inject(HttpClient);

  indicateurs(): Observable<GroupeIndicateurs[]> {
    return this.http.get<GroupeIndicateurs[]>('/api/v1/console/tableau');
  }
}
