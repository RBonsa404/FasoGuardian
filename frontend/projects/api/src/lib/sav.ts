import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type StatutBraceletParc = 'EN_STOCK' | 'ACTIF' | 'PERDU' | 'VOLE' | 'EN_SAV' | 'REFORME';

export interface BraceletParc {
  readonly numeroSerie: string;
  readonly statut: StatutBraceletParc;
  readonly revisionMaterielle: string;
  readonly versionLogiciel: string;
  readonly certificatRevoque: boolean;
  /** Porté par un enfant en ce moment ; le service après-vente ne sait pas lequel. */
  readonly appaire: boolean;
  /** Date ISO (AAAA-MM-JJ). */
  readonly garantieJusquAu: string | null;
  readonly modifieLe: string;
}

/** État technique d'un bracelet en service, depuis son appairage en cours ; chaque mesure peut manquer. */
export interface EtatTechniqueBracelet {
  readonly numeroSerie: string;
  readonly batterie: number | null;
  readonly reseau: string | null;
  readonly dernierContact: string;
}

export interface PeriodeAppairage {
  readonly debut: string;
  readonly fin: string | null;
  readonly motifFin: 'DESAPPAIRAGE' | 'PERTE' | 'VOL' | 'PANNE' | 'REMPLACEMENT' | null;
}

export interface FicheBraceletParc {
  readonly bracelet: BraceletParc;
  readonly imeiMasque: string;
  readonly empreinteCertificat: string;
  readonly appairages: readonly PeriodeAppairage[];
}

/** Remise une seule fois : seules les empreintes de ces deux secrets sont conservées. */
export interface CarteActivationParc {
  readonly numeroSerie: string;
  readonly codeAppairage: string;
  /** Absent à la remise en stock : le QR gravé sur le bracelet ne change pas. */
  readonly jetonQr: string | null;
}

export type ResolutionTicket = 'REPRISE_SPONTANEE' | 'RECHARGE' | 'ECHANGE' | 'RETOUR_ATELIER' | 'SANS_SUITE';

export interface TicketMaintenance {
  readonly id: string;
  /** SAV-000123. */
  readonly reference: string;
  readonly numeroSerie: string;
  readonly motif: 'MUET';
  readonly statut: 'OUVERT' | 'EN_COURS' | 'RESOLU';
  readonly ouvertLe: string;
  /** État du bracelet relevé à l'ouverture. */
  readonly dernierContact: string | null;
  readonly batterie: number | null;
  readonly reseau: string | null;
  readonly prisEnCharge: boolean;
  readonly prisEnChargeParMoi: boolean;
  readonly resolution: ResolutionTicket | null;
  readonly note: string | null;
  readonly resoluLe: string | null;
  /** Sondage du bracelet par une localisation signée. */
  readonly diagnosticLe: string | null;
  readonly diagnostic: 'EMISE' | 'ACCUSEE' | 'REFUSEE' | 'EXPIREE' | null;
  readonly pointRelais: string | null;
  readonly rendezVousLe: string | null;
  /** Unité réservée pour l'échange ; son appairage par le parent clôt le ticket. */
  readonly remplacement: string | null;
  /** Autres bracelets muets en ce moment. */
  readonly autresMuets: number;
}

const PARC = '/api/v1/console/parc';
const TICKETS = '/api/v1/console/sav/tickets';

/** Client du service après-vente : parc de bracelets (US-SAV-002) et tickets de maintenance (US-SAV-001). */
@Injectable({ providedIn: 'root' })
export class ClientSav {
  private readonly http = inject(HttpClient);

  parc(statut?: StatutBraceletParc): Observable<BraceletParc[]> {
    return this.http.get<BraceletParc[]>(PARC, statut ? { params: { statut } } : {});
  }

  /** Consultation journalisée. */
  /** Batterie, réseau et dernier contact des bracelets en service, sans l'enfant ni la position. */
  etatsParc(): Observable<EtatTechniqueBracelet[]> {
    return this.http.get<EtatTechniqueBracelet[]>(`${PARC}/etats`);
  }

  fiche(numeroSerie: string): Observable<FicheBraceletParc> {
    return this.http.get<FicheBraceletParc>(`${PARC}/${encodeURIComponent(numeroSerie)}`);
  }

  retourner(numeroSerie: string): Observable<BraceletParc> {
    return this.http.post<BraceletParc>(`${PARC}/${encodeURIComponent(numeroSerie)}/retour`, null);
  }

  remettreEnStock(numeroSerie: string): Observable<CarteActivationParc> {
    return this.http.post<CarteActivationParc>(`${PARC}/${encodeURIComponent(numeroSerie)}/remise-en-stock`, null);
  }

  reformer(numeroSerie: string): Observable<BraceletParc> {
    return this.http.post<BraceletParc>(`${PARC}/${encodeURIComponent(numeroSerie)}/reforme`, null);
  }

  tickets(resolus = false): Observable<TicketMaintenance[]> {
    return this.http.get<TicketMaintenance[]>(TICKETS, { params: { resolus } });
  }

  prendreEnCharge(ticketId: string): Observable<TicketMaintenance> {
    return this.http.post<TicketMaintenance>(`${TICKETS}/${ticketId}/prise-en-charge`, null);
  }

  diagnostiquer(ticketId: string): Observable<TicketMaintenance> {
    return this.http.post<TicketMaintenance>(`${TICKETS}/${ticketId}/diagnostic`, null);
  }

  /** Le rendez-vous est transmis au parent par la plateforme : le service après-vente ne le connaît pas. */
  fixerRendezVous(ticketId: string, pointRelais: string, le: string): Observable<TicketMaintenance> {
    return this.http.post<TicketMaintenance>(`${TICKETS}/${ticketId}/rendez-vous`, { pointRelais, le });
  }

  reserverRemplacement(ticketId: string, numeroSerie: string): Observable<TicketMaintenance> {
    return this.http.post<TicketMaintenance>(`${TICKETS}/${ticketId}/remplacement`, { numeroSerie });
  }

  resoudre(ticketId: string, resolution: ResolutionTicket, note: string): Observable<TicketMaintenance> {
    return this.http.post<TicketMaintenance>(`${TICKETS}/${ticketId}/resolution`, { resolution, note: note || null });
  }
}
