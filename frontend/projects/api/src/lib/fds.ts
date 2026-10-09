import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Ce que les forces de sécurité voient d'un signalement désigné par sa référence (US-FDS-001). */
export interface ConstatSignalement {
  /** FG-SIG-000412. */
  readonly reference: string;
  /** Nature de l'alerte à l'origine du signalement, en clair. */
  readonly nature: string;
  readonly etabliLe: string;
  readonly accuseLe: string | null;
  /** Vrai si le dossier a été transmis par la passerelle convenue et n'est pas encore effacé. */
  readonly dossierConsultable: boolean;
}

const SIGNALEMENTS = '/api/v1/console/fds/signalements';

/** Client de l'espace des forces de sécurité. Un signalement se retrouve par sa référence exacte, jamais par liste. */
@Injectable({ providedIn: 'root' })
export class ClientFds {
  private readonly http = inject(HttpClient);

  /** Consultation journalisée. */
  constat(reference: string): Observable<ConstatSignalement> {
    return this.http.get<ConstatSignalement>(`${SIGNALEMENTS}/${encodeURIComponent(reference)}`);
  }

  /** Accusé horodaté, notifié aux parents ; le premier accusé fait foi. */
  accuser(reference: string): Observable<ConstatSignalement> {
    return this.http.post<ConstatSignalement>(`${SIGNALEMENTS}/${encodeURIComponent(reference)}/accuse`, {});
  }

  /** Dossier PDF transmis par la passerelle ; téléchargement journalisé. */
  dossier(reference: string): Observable<Blob> {
    return this.http.get(`${SIGNALEMENTS}/${encodeURIComponent(reference)}/dossier`, { responseType: 'blob' });
  }
}
