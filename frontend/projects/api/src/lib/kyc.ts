import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export type CanalKyc = 'EN_LIGNE' | 'POINT_INSCRIPTION';
export type NatureLien = 'PARENT' | 'TUTEUR';
export type StatutDossierKyc = 'BROUILLON' | 'DEPOSE' | 'EN_INSTRUCTION' | 'COMPLEMENT_DEMANDE' | 'APPROUVE' | 'REJETE';
export type TypePieceKyc = 'PIECE_RECTO' | 'PIECE_VERSO' | 'ACTE_NAISSANCE' | 'JUGEMENT_TUTELLE';

export interface IdentiteDeclaree {
  readonly nom: string;
  readonly prenoms: string;
  readonly typePiece: string;
  readonly numeroPiece: string;
}

export interface EnfantDeclare {
  readonly prenom: string;
  readonly nom: string;
  /** Date ISO (AAAA-MM-JJ). */
  readonly dateNaissance: string;
}

export interface PieceKyc {
  readonly id: string;
  readonly type: TypePieceKyc;
  readonly typeMime: string;
  readonly tailleOctets: number;
}

export interface DossierKycParent {
  readonly id: string;
  readonly reference: string;
  readonly statut: StatutDossierKyc;
  readonly canal: CanalKyc;
  readonly natureLien: NatureLien;
  readonly motif: string | null;
  readonly deposeLe: string | null;
  readonly decideLe: string | null;
  readonly pieces: readonly PieceKyc[];
}

export interface DemandeDossierKyc {
  readonly canal: CanalKyc;
  readonly natureLien: NatureLien;
  readonly demandeur: IdentiteDeclaree;
  readonly enfant: EnfantDeclare;
}

const BASE = '/api/v1/kyc/dossiers';

/** Client du dossier de vérification KYC du parent (US-PAR-001). */
@Injectable({ providedIn: 'root' })
export class ClientKyc {
  private readonly http = inject(HttpClient);

  ouvrir(demande: DemandeDossierKyc): Observable<DossierKycParent> {
    return this.http.post<DossierKycParent>(BASE, demande);
  }

  /** Dossier le plus récent du parent, ou `null` s'il n'en a pas encore. */
  courant(): Observable<DossierKycParent | null> {
    return this.http
      .get<DossierKycParent>(`${BASE}/courant`, { observe: 'response' })
      .pipe(map((reponse) => (reponse.status === 204 ? null : reponse.body)));
  }

  ajouterPiece(dossierId: string, type: TypePieceKyc, fichier: Blob): Observable<PieceKyc> {
    const corps = new FormData();
    corps.append('type', type);
    corps.append('fichier', fichier);
    return this.http.post<PieceKyc>(`${BASE}/${dossierId}/pieces`, corps);
  }

  deposer(dossierId: string): Observable<DossierKycParent> {
    return this.http.post<DossierKycParent>(`${BASE}/${dossierId}/depot`, null);
  }
}
