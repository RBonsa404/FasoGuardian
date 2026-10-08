import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PositionConnue } from './bracelet';

export interface PointGeo {
  readonly latitude: number;
  readonly longitude: number;
}

export type FormeZone = 'CERCLE' | 'POLYGONE';
export type CategorieZone = 'ECOLE' | 'MAISON' | 'FAMILLE' | 'CULTE' | 'AUTRE';

export interface SafeZone {
  readonly id: string;
  readonly forme: FormeZone;
  readonly nom: string;
  readonly categorie: CategorieZone;
  /** Zone circulaire seulement. */
  readonly centre: PointGeo | null;
  readonly rayonM: number | null;
  /** Zone polygonale seulement. */
  readonly sommets: readonly PointGeo[] | null;
  /** 1 = lundi … 7 = dimanche. */
  readonly jours: readonly number[];
  /** Heures locales HH:mm ; début et fin égaux désignent la journée entière. */
  readonly debut: string;
  readonly fin: string;
  readonly toleranceS: number;
  readonly statut: 'ACTIVE' | 'SUSPENDUE';
  /** La zone est surveillée en ce moment. */
  readonly dansLaPlage: boolean;
  /** Une sortie a été signalée et l'enfant n'est pas revenu. */
  readonly sortieEnCours: boolean;
  /** D'après la dernière position évaluée ; `null` hors plage horaire ou sans position. */
  readonly enfantDedans: boolean | null;
}

export interface SafeZones {
  readonly zones: readonly SafeZone[];
  /** Nombre de zones permis par l'offre. */
  readonly maximum: number;
}

export interface SaisieZone {
  readonly forme: FormeZone;
  readonly nom: string;
  readonly categorie: CategorieZone;
  readonly centre: PointGeo | null;
  readonly rayonM: number | null;
  readonly sommets: readonly PointGeo[] | null;
  readonly jours: readonly number[];
  readonly debut: string;
  readonly fin: string;
  readonly toleranceS: number;
}

export interface Trajet {
  /** Date ISO (AAAA-MM-JJ). */
  readonly jour: string;
  readonly points: readonly PositionConnue[];
  readonly joursConserves: number;
}

/** Client des Safe Zones et des trajets (US-PAR-007, US-PAR-008). Second facteur MODIFIER_SAFE_ZONE. */
@Injectable({ providedIn: 'root' })
export class ClientZones {
  private readonly http = inject(HttpClient);

  zones(enfantId: string): Observable<SafeZones> {
    return this.http.get<SafeZones>(this.base(enfantId));
  }

  creer(enfantId: string, zone: SaisieZone, codeSecondFacteur: string): Observable<SafeZone> {
    return this.http.post<SafeZone>(this.base(enfantId), { ...zone, codeSecondFacteur });
  }

  modifier(enfantId: string, zoneId: string, zone: SaisieZone, codeSecondFacteur: string): Observable<SafeZone> {
    return this.http.put<SafeZone>(`${this.base(enfantId)}/${zoneId}`, { ...zone, codeSecondFacteur });
  }

  suspendre(enfantId: string, zoneId: string, codeSecondFacteur: string): Observable<SafeZone> {
    return this.http.post<SafeZone>(`${this.base(enfantId)}/${zoneId}/suspension`, { codeSecondFacteur });
  }

  reactiver(enfantId: string, zoneId: string): Observable<SafeZone> {
    return this.http.post<SafeZone>(`${this.base(enfantId)}/${zoneId}/reactivation`, {});
  }

  supprimer(enfantId: string, zoneId: string, codeSecondFacteur: string): Observable<void> {
    return this.http.post<void>(`${this.base(enfantId)}/${zoneId}/suppression`, { codeSecondFacteur });
  }

  /** Trajet d'une journée (AAAA-MM-JJ), dans la limite de la durée de conservation ; consultation journalisée. */
  trajet(enfantId: string, jour: string): Observable<Trajet> {
    return this.http.get<Trajet>(`/api/v1/enfants/${enfantId}/trajets`, { params: { jour } });
  }

  private base(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/zones`;
  }
}
