import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface EntreeAudit {
  readonly id: number;
  readonly horodatage: string;
  /** Compte à l'origine de l'action ; `null` pour le système. */
  readonly acteurId: string | null;
  readonly role: string;
  readonly action: string;
  readonly typeCible: string;
  readonly cibleId: string | null;
  readonly resultat: 'SUCCES' | 'REFUS';
  /** Empreinte abrégée (début…fin). */
  readonly empreinte: string;
}

/** État de la chaîne d'empreintes d'après le dernier contrôle, quotidien ou demandé. */
export interface ChaineAudit {
  readonly integre: boolean;
  readonly verifieeLe: string | null;
  readonly entreeAlteree: number | null;
}

export interface PageAudit {
  readonly entrees: readonly EntreeAudit[];
  readonly total: number;
  readonly page: number;
  readonly taille: number;
  readonly chaine: ChaineAudit;
}

export interface FiltreAudit {
  readonly action?: string;
  readonly role?: string;
  readonly resultat?: 'SUCCES' | 'REFUS';
}

export interface EtatAipd {
  readonly documentee: boolean;
  readonly reference: string | null;
  readonly valideeLe: string | null;
  readonly delegue: string | null;
}

export interface DureeConservation {
  readonly donnee: string;
  readonly duree: string;
  readonly mecanisme: string;
}

export interface PurgeExecutee {
  readonly traitement: string;
  readonly derniereExecution: string;
  readonly executions: number;
  readonly elements: number;
}

export interface TableauConformite {
  readonly aipd: EtatAipd;
  readonly conservation: readonly DureeConservation[];
  /** Décomptes du mois en cours. */
  readonly demandesAcces: number;
  readonly demandesEffacement: number;
  readonly effacementsEnAttente: number;
  /** Jours du mois où les purges ont tourné, sur `joursEcoules`. */
  readonly joursDePurge: number;
  readonly joursEcoules: number;
  readonly purges: readonly PurgeExecutee[];
}

export interface DemandeEffacement {
  readonly id: string;
  /** EFF-000012. */
  readonly reference: string;
  readonly statut: 'RECUE' | 'TRAITEE';
  readonly recueLe: string;
  readonly echeanceLe: string;
  readonly traiteeLe: string | null;
  readonly traiteeParLeSysteme: boolean;
}

/** Client du journal d'audit et de la conformité (US-ADM-002, US-ADM-003) ; droit d'accès du parent. */
@Injectable({ providedIn: 'root' })
export class ClientConformite {
  private readonly http = inject(HttpClient);

  journal(filtre: FiltreAudit, page: number, taille = 50): Observable<PageAudit> {
    const params: Record<string, string | number> = { page, taille };
    for (const [cle, valeur] of Object.entries(filtre)) {
      if (valeur) {
        params[cle] = valeur;
      }
    }
    return this.http.get<PageAudit>('/api/v1/console/audit', { params });
  }

  verifierLaChaine(): Observable<ChaineAudit> {
    return this.http.post<ChaineAudit>('/api/v1/console/audit/verification', null);
  }

  tableau(): Observable<TableauConformite> {
    return this.http.get<TableauConformite>('/api/v1/console/conformite');
  }

  demandes(): Observable<DemandeEffacement[]> {
    return this.http.get<DemandeEffacement[]>('/api/v1/console/conformite/demandes');
  }

  executer(demandeId: string): Observable<DemandeEffacement> {
    return this.http.post<DemandeEffacement>(`/api/v1/console/conformite/demandes/${demandeId}/execution`, null);
  }

  /** Rapport du mois (AAAA-MM) en PDF. */
  rapport(mois: string): Observable<Blob> {
    return this.http.get('/api/v1/console/conformite/rapport', { params: { mois }, responseType: 'blob' });
  }

  /** Droit d'accès : tout ce que la plateforme détient sur le parent connecté et ses enfants. */
  mesDonnees(): Observable<Blob> {
    return this.http.get('/api/v1/moi/donnees', { responseType: 'blob' });
  }
}
