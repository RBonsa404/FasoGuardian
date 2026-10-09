import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EtatVagueOta = 'TERMINEE' | 'EN_COURS' | 'PRETE' | 'PLANIFIEE';
export type StatutCampagneOta = 'PREPAREE' | 'EN_COURS' | 'EN_PAUSE' | 'TERMINEE';

export interface VagueOta {
  readonly numero: number;
  /** Part cumulée du parc visée au terme de la vague. */
  readonly pourcentage: number;
  /** Bracelets visés ; pour une vague qui n'est pas lancée, c'est une estimation. */
  readonly cibles: number;
  readonly installes: number;
  readonly etat: EtatVagueOta;
}

/** Campagne de mise à jour du logiciel embarqué (US-PAR-013). */
export interface CampagneOta {
  readonly id: string;
  readonly version: string;
  readonly tailleOctets: number;
  readonly note: string | null;
  readonly statut: StatutCampagneOta;
  /** Dernière vague lancée, de 0 à 4. */
  readonly vagueCourante: number;
  readonly creeeLe: string;
  readonly vagues: readonly VagueOta[];
}

export interface ImageOta {
  readonly version: string;
  readonly urlImage: string;
  readonly tailleOctets: number;
  readonly sha256: string;
  /** Signature du manifeste par la clé de publication, en base64url. */
  readonly signature: string;
  readonly note: string | null;
}

const OTA = '/api/v1/console/sav/ota';

/** Client des campagnes de mise à jour, ouvertes au service après-vente et à l'administrateur. */
@Injectable({ providedIn: 'root' })
export class ClientOta {
  private readonly http = inject(HttpClient);

  campagnes(): Observable<CampagneOta[]> {
    return this.http.get<CampagneOta[]>(OTA);
  }

  /** Échoue en REQUETE_INVALIDE si la signature de l'image ne se vérifie pas. */
  preparer(image: ImageOta): Observable<CampagneOta> {
    return this.http.post<CampagneOta>(OTA, image);
  }

  lancerLaVagueSuivante(id: string): Observable<CampagneOta> {
    return this.http.post<CampagneOta>(`${OTA}/${id}/vagues`, {});
  }

  mettreEnPause(id: string): Observable<CampagneOta> {
    return this.http.post<CampagneOta>(`${OTA}/${id}/pause`, {});
  }

  reprendre(id: string): Observable<CampagneOta> {
    return this.http.post<CampagneOta>(`${OTA}/${id}/reprise`, {});
  }
}
