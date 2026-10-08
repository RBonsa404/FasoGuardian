import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ProfilEnfant {
  readonly ecole: string | null;
  readonly quartier: string | null;
  readonly tailleCm: number | null;
  readonly signesDistinctifs: string | null;
}

export interface FicheEnfant {
  readonly id: string;
  readonly prenom: string;
  readonly nom: string;
  /** Date ISO (AAAA-MM-JJ). */
  readonly dateNaissance: string;
  readonly profil: ProfilEnfant;
  readonly modifieLe: string;
}

export interface RevisionEnfant {
  readonly champ: string;
  readonly modifieLe: string;
}

export type TypeElementMedical = 'ALLERGIE' | 'PATHOLOGIE' | 'TRAITEMENT' | 'AUTRE';

export interface ElementMedical {
  readonly type: TypeElementMedical;
  readonly libelle: string;
  readonly critique: boolean;
}

export interface FicheSante {
  readonly groupeSanguin: string | null;
  /** Le groupe sanguin n'apparaît sur la page publique QR que si le parent le demande. */
  readonly groupeSanguinSurQr: boolean;
  readonly elements: readonly ElementMedical[];
  readonly modifieLe: string | null;
}

export interface RevisionSante {
  readonly nombreElements: number;
  readonly nombreCritiques: number;
  readonly modifieLe: string;
}

export interface ContactUrgence {
  readonly id: string;
  readonly lien: string;
  readonly nom: string;
  readonly telephone: string;
  readonly visibleSurQr: boolean;
  readonly rang: number;
}

export interface SaisieContact {
  readonly lien: string;
  readonly nom: string;
  readonly telephone: string;
  readonly visibleSurQr: boolean;
}

const BASE = '/api/v1/enfants';

/** Client du module famille : fiches enfants, fiche médicale, contacts d'urgence (US-PAR-003, 004, 011). */
@Injectable({ providedIn: 'root' })
export class ClientFamille {
  private readonly http = inject(HttpClient);

  mesEnfants(): Observable<FicheEnfant[]> {
    return this.http.get<FicheEnfant[]>(BASE);
  }

  enfant(id: string): Observable<FicheEnfant> {
    return this.http.get<FicheEnfant>(`${BASE}/${id}`);
  }

  modifierEnfant(id: string, modification: { prenom?: string; nom?: string; profil?: ProfilEnfant }): Observable<FicheEnfant> {
    return this.http.patch<FicheEnfant>(`${BASE}/${id}`, modification);
  }

  historique(id: string): Observable<RevisionEnfant[]> {
    return this.http.get<RevisionEnfant[]>(`${BASE}/${id}/historique`);
  }

  sante(id: string): Observable<FicheSante> {
    return this.http.get<FicheSante>(`${BASE}/${id}/sante`);
  }

  enregistrerSante(
    id: string,
    fiche: { groupeSanguin: string | null; groupeSanguinSurQr: boolean; elements: readonly ElementMedical[] },
  ): Observable<FicheSante> {
    return this.http.put<FicheSante>(`${BASE}/${id}/sante`, fiche);
  }

  revisionsSante(id: string): Observable<RevisionSante[]> {
    return this.http.get<RevisionSante[]>(`${BASE}/${id}/sante/revisions`);
  }

  contacts(id: string): Observable<ContactUrgence[]> {
    return this.http.get<ContactUrgence[]>(`${BASE}/${id}/contacts`);
  }

  ajouterContact(id: string, contact: SaisieContact): Observable<ContactUrgence> {
    return this.http.post<ContactUrgence>(`${BASE}/${id}/contacts`, contact);
  }

  modifierContact(id: string, contactId: string, contact: SaisieContact): Observable<ContactUrgence> {
    return this.http.put<ContactUrgence>(`${BASE}/${id}/contacts/${contactId}`, contact);
  }

  supprimerContact(id: string, contactId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}/contacts/${contactId}`);
  }
}
