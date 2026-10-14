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
  /** Prénom vérifié de l'auteur ; vide s'il n'a pas de dossier approuvé. */
  readonly auteur: string;
  /** Vrai si l'auteur est le parent connecté. */
  readonly parVous: boolean;
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
  /** Prénom vérifié de l'auteur ; vide s'il n'a pas de dossier approuvé. */
  readonly auteur: string;
  /** Vrai si l'auteur est le parent connecté. */
  readonly parVous: boolean;
}

/** Tuteur de l'enfant ; le principal est celui dont la vérification a créé la fiche. */
export interface TuteurEnfant {
  /** Premier prénom et nom, vérifiés par le KYC. */
  readonly prenom: string;
  readonly nom: string;
  readonly telephone: string | null;
  readonly principal: boolean;
  readonly vous: boolean;
  /** Lien suspendu à titre conservatoire pendant un litige. */
  readonly suspendu: boolean;
}

export interface InvitationTuteur {
  readonly telephone: string;
  /** EN_VERIFICATION : l'invité a ouvert son dossier KYC. */
  readonly statut: 'EN_ATTENTE' | 'EN_VERIFICATION';
  readonly expireLe: string;
}

export interface TuteursEnfant {
  readonly tuteurs: readonly TuteurEnfant[];
  readonly invitation: InvitationTuteur | null;
  readonly peutInviter: boolean;
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

  tuteurs(id: string): Observable<TuteursEnfant> {
    return this.http.get<TuteursEnfant>(`${BASE}/${id}/tuteurs`);
  }

  /** Invite un second tuteur ; action sensible confirmée par code SMS (AJOUTER_TUTEUR). */
  inviterTuteur(id: string, telephone: string, codeSecondFacteur: string): Observable<InvitationTuteur> {
    return this.http.post<InvitationTuteur>(`${BASE}/${id}/tuteurs/invitation`, { telephone, codeSecondFacteur });
  }

  annulerInvitation(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}/tuteurs/invitation`);
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
