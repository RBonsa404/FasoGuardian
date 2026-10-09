import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { RoleInterne } from './console';

export interface AgentInterne {
  readonly id: string;
  readonly identifiant: string;
  readonly roles: readonly RoleInterne[];
  /** Faux tant que l'agent n'a pas activé son second facteur à sa première connexion. */
  readonly secondFacteurActif: boolean;
  readonly suspendu: boolean;
}

const AGENTS = '/api/v1/admin/agents';

/** Client de la gestion des agents et de leurs rôles (US-ADM-001), réservé aux administrateurs. */
@Injectable({ providedIn: 'root' })
export class ClientAgents {
  private readonly http = inject(HttpClient);

  agents(): Observable<AgentInterne[]> {
    return this.http.get<AgentInterne[]>(AGENTS);
  }

  /** Le mot de passe est provisoire : l'agent active son second facteur à sa première connexion. */
  creer(identifiant: string, motDePasseProvisoire: string, roles: readonly RoleInterne[]): Observable<AgentInterne> {
    return this.http.post<AgentInterne>(AGENTS, { identifiant, motDePasseProvisoire, roles });
  }

  /** Les sessions de l'agent sont fermées : le nouveau périmètre vaut dès sa prochaine connexion. */
  attribuer(agentId: string, roles: readonly RoleInterne[]): Observable<AgentInterne> {
    return this.http.put<AgentInterne>(`${AGENTS}/${agentId}/roles`, { roles });
  }

  suspendre(agentId: string): Observable<AgentInterne> {
    return this.http.post<AgentInterne>(`${AGENTS}/${agentId}/suspension`, null);
  }

  retablir(agentId: string): Observable<AgentInterne> {
    return this.http.post<AgentInterne>(`${AGENTS}/${agentId}/retablissement`, null);
  }
}
