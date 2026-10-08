import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { JetonAcces, Session } from './authentification';
import { CanalKyc, EnfantDeclare, IdentiteDeclaree, NatureLien, PieceKyc, StatutDossierKyc } from './kyc';

export type RoleInterne = 'KYC' | 'SUPPORT' | 'SAV' | 'ADMIN' | 'FDS';

export interface DossierKycFile {
  readonly id: string;
  readonly reference: string;
  readonly statut: StatutDossierKyc;
  readonly canal: CanalKyc;
  readonly natureLien: NatureLien;
  readonly demandeur: string;
  readonly enfantPrenom: string;
  readonly enfantAge: number;
  readonly deposeLe: string;
  readonly prisEnCharge: boolean;
  readonly prisEnChargeParMoi: boolean;
}

export interface DossierKycInstruction {
  readonly id: string;
  readonly reference: string;
  readonly statut: StatutDossierKyc;
  readonly canal: CanalKyc;
  readonly natureLien: NatureLien;
  readonly demandeur: IdentiteDeclaree;
  readonly enfant: EnfantDeclare;
  readonly motif: string | null;
  readonly deposeLe: string;
  readonly pieces: readonly PieceKyc[];
}

export interface PageResultats<T> {
  readonly elements: readonly T[];
  readonly page: number;
  readonly taille: number;
  readonly total: number;
}

export type DecisionKyc = 'APPROUVER' | 'REJETER' | 'DEMANDER_COMPLEMENT';

const KYC = '/api/v1/console/kyc/dossiers';

/** Client de la console interne : connexion des agents et instruction des dossiers KYC. */
@Injectable({ providedIn: 'root' })
export class ClientConsole {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);

  /**
   * Connexion d'un agent. Sans second facteur actif, le serveur répond 403 TOTP_A_ACTIVER avec le secret
   * à enregistrer ; avec un second facteur actif mais sans code, 401 CODE_TOTP_REQUIS.
   */
  connecter(identifiant: string, motDePasse: string, codeTotp?: string): Observable<JetonAcces> {
    return this.http
      .post<JetonAcces>('/api/v1/auth/agents/connexion', { identifiant, motDePasse, codeTotp }, { withCredentials: true })
      .pipe(tap((jeton) => this.session.ouvrir(jeton)));
  }

  fileKyc(page = 0, taille = 50): Observable<PageResultats<DossierKycFile>> {
    return this.http.get<PageResultats<DossierKycFile>>(KYC, { params: { page, size: taille } });
  }

  dossierKyc(id: string): Observable<DossierKycInstruction> {
    return this.http.get<DossierKycInstruction>(`${KYC}/${id}`);
  }

  prendreEnCharge(id: string): Observable<DossierKycInstruction> {
    return this.http.post<DossierKycInstruction>(`${KYC}/${id}/prise-en-charge`, null);
  }

  /** Contenu déchiffré d'une pièce ; chaque appel est journalisé par le serveur. */
  pieceKyc(dossierId: string, pieceId: string): Observable<Blob> {
    return this.http.get(`${KYC}/${dossierId}/pieces/${pieceId}`, { responseType: 'blob' });
  }

  decider(id: string, decision: DecisionKyc, motif?: string): Observable<DossierKycInstruction> {
    return this.http.post<DossierKycInstruction>(`${KYC}/${id}/decision`, { decision, motif });
  }
}
