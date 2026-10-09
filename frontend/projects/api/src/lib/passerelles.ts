import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Passerelle LoRaWAN installée autour d'un établissement partenaire (US-SYS-004). */
export interface Passerelle {
  readonly id: string;
  /** Identifiant matériel, 16 chiffres hexadécimaux. */
  readonly eui: string;
  readonly etablissement: string;
  /** Centre et rayon de l'enceinte couverte. */
  readonly latitude: number;
  readonly longitude: number;
  readonly rayonM: number;
  readonly creeeLe: string;
  readonly vueLe: string | null;
  /** Une trame ou un signe de vie a été reçu depuis moins d'un quart d'heure. */
  readonly enLigne: boolean;
}

export type InstallationPasserelle = Pick<Passerelle, 'eui' | 'etablissement' | 'latitude' | 'longitude' | 'rayonM'>;

const PASSERELLES = '/api/v1/console/passerelles';

/** Registre des passerelles LoRaWAN, réservé à l'administrateur. */
@Injectable({ providedIn: 'root' })
export class ClientPasserelles {
  private readonly http = inject(HttpClient);

  enService(): Observable<Passerelle[]> {
    return this.http.get<Passerelle[]>(PASSERELLES);
  }

  installer(installation: InstallationPasserelle): Observable<Passerelle> {
    return this.http.post<Passerelle>(PASSERELLES, installation);
  }

  /** Les trames d'une passerelle retirée ne sont plus acceptées. */
  retirer(id: string): Observable<void> {
    return this.http.delete<void>(`${PASSERELLES}/${id}`);
  }
}
