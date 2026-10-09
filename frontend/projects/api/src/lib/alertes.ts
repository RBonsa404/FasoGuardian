import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type TypeAlerte = 'SOS' | 'RETRAIT' | 'SIGNALEMENT' | 'SORTIE_ZONE' | 'BATTERIE_CRITIQUE' | 'CHUTE';
export type GraviteAlerte = 'CRITIQUE' | 'IMPORTANTE';
export type StatutAlerte = 'OUVERTE' | 'ACQUITTEE' | 'ESCALADEE' | 'LEVEE' | 'FAUSSE_ALERTE';
/** Les deux dernières valeurs sont les étapes de la cascade : un tiers a été sollicité faute de réponse (US-SYS-005). */
export type TypeActionAlerte = 'OUVERTURE' | 'ACQUITTEMENT' | 'ESCALADE' | 'LEVEE' | 'FAUSSE_ALERTE' | 'RESOLUTION' | 'CONTACT_SOLLICITE' | 'INSTITUTION_SOLLICITEE';

/** Ligne du journal d'acquittement. L'identité d'un autre tuteur n'est jamais transmise. */
export interface ActionAlerte {
  readonly type: TypeActionAlerte;
  readonly auteur: 'VOUS' | 'AUTRE_TUTEUR' | 'SYSTEME';
  readonly motif: string | null;
  readonly effectueeLe: string;
}

export interface Alerte {
  readonly id: string;
  readonly enfantId: string;
  readonly type: TypeAlerte;
  readonly gravite: GraviteAlerte;
  readonly statut: StatutAlerte;
  readonly ouverteLe: string;
  readonly closeLe: string | null;
  /** Nom de la zone pour une sortie de zone. */
  readonly libelle: string | null;
  /** Dernière position connue au déclenchement, si le bracelet l'a jointe. */
  readonly latitude: number | null;
  readonly longitude: number | null;
  readonly actions: readonly ActionAlerte[];
}

export type MotifRetrait = 'TOILETTE' | 'RECHARGE' | 'NUIT' | 'AUTRE';

export interface AutorisationRetrait {
  readonly id: string;
  readonly motif: MotifRetrait;
  readonly debut: string;
  readonly fin: string;
  /** Le bracelet a été retiré pendant la fenêtre et n'a pas encore été remis. */
  readonly retire: boolean;
}

/** Ce que le dossier de signalement contiendra, montré au parent avant qu'il confirme. */
export interface ApercuSignalement {
  readonly enfant: {
    readonly prenom: string;
    readonly nom: string;
    /** Date ISO (AAAA-MM-JJ). */
    readonly dateNaissance: string;
    readonly tailleCm: number | null;
    readonly signesDistinctifs: string | null;
    readonly ecole: string | null;
    readonly quartier: string | null;
    /** Seulement les informations marquées critiques par le parent. */
    readonly informationsMedicales: readonly string[];
  };
  /** Nombre de positions des deux dernières heures jointes au dossier. */
  readonly positionsDuTrajet: number;
  readonly dernierePosition: { readonly latitude: number; readonly longitude: number; readonly precisionM: number; readonly mesureeLe: string } | null;
  /** Faux tant qu'aucune convention n'est signée : le dossier est alors remis par le parent. */
  readonly conventionActive: boolean;
}

export interface Signalement {
  /** Référence du dossier (FG-SIG-000412). */
  readonly reference: string;
  readonly canal: 'REMISE_PAR_LE_PARENT' | 'PASSERELLE';
  readonly creeLe: string;
  readonly disponibleJusquAu: string;
  /** Faux une fois le dossier effacé, au bout de 30 jours. */
  readonly dossierDisponible: boolean;
  readonly empreinteDossier: string;
  /** Accusé de réception des forces de sécurité, s'il a été donné. */
  readonly accuseLe: string | null;
}

/** Client des alertes et de l'autorisation de retrait (US-ENF-001, US-PAR-008, 010, 012). */
@Injectable({ providedIn: 'root' })
export class ClientAlertes {
  private readonly http = inject(HttpClient);

  /** Alertes de mes enfants, les plus récentes d'abord. */
  mesAlertes(enCours: boolean): Observable<Alerte[]> {
    return this.http.get<Alerte[]>('/api/v1/alertes', { params: { enCours } });
  }

  alerte(id: string): Observable<Alerte> {
    return this.http.get<Alerte>(`/api/v1/alertes/${id}`);
  }

  acquitter(id: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/acquittement`, {});
  }

  /** Une seule prise en charge pour toutes les alertes ouvertes de l'enfant. */
  prendreEnCharge(enfantId: string): Observable<Alerte[]> {
    return this.http.post<Alerte[]>(`/api/v1/enfants/${enfantId}/alertes/prise-en-charge`, {});
  }

  lever(id: string, motif: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/levee`, { motif });
  }

  classerFausseAlerte(id: string, motif: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/alertes/${id}/fausse-alerte`, { motif });
  }

  signaler(enfantId: string): Observable<Alerte> {
    return this.http.post<Alerte>(`/api/v1/enfants/${enfantId}/signalement`, {});
  }

  /** Aperçu du dossier de signalement ; consultation journalisée. */
  apercuSignalement(id: string): Observable<ApercuSignalement> {
    return this.http.get<ApercuSignalement>(`/api/v1/alertes/${id}/signalement/apercu`);
  }

  /** Escalade vers les forces de sécurité ; second facteur ESCALADER_FORCES_SECURITE requis. */
  escalader(id: string, codeSecondFacteur: string): Observable<Signalement> {
    return this.http.post<Signalement>(`/api/v1/alertes/${id}/escalade`, { codeSecondFacteur });
  }

  /** Échoue en RESSOURCE_INTROUVABLE si l'alerte n'a pas été escaladée. */
  signalement(id: string): Observable<Signalement> {
    return this.http.get<Signalement>(`/api/v1/alertes/${id}/signalement`);
  }

  /** Dossier PDF à remettre aux autorités ; téléchargement journalisé. */
  dossierSignalement(id: string): Observable<Blob> {
    return this.http.get(`/api/v1/alertes/${id}/signalement/dossier`, { responseType: 'blob' });
  }

  /** Journal des alertes de l'enfant ; consultation journalisée. */
  journal(enfantId: string): Observable<Alerte[]> {
    return this.http.get<Alerte[]>(`/api/v1/enfants/${enfantId}/journal`);
  }

  /** Échoue en RESSOURCE_INTROUVABLE si aucun retrait n'est autorisé. */
  retraitEnCours(enfantId: string): Observable<AutorisationRetrait> {
    return this.http.get<AutorisationRetrait>(this.retrait(enfantId));
  }

  /** Second facteur AUTORISER_RETRAIT requis. */
  autoriserRetrait(enfantId: string, motif: MotifRetrait, dureeMinutes: number, codeSecondFacteur: string): Observable<AutorisationRetrait> {
    return this.http.post<AutorisationRetrait>(this.retrait(enfantId), { motif, dureeMinutes, codeSecondFacteur });
  }

  prolongerRetrait(enfantId: string, minutes: number, codeSecondFacteur: string): Observable<AutorisationRetrait> {
    return this.http.post<AutorisationRetrait>(`${this.retrait(enfantId)}/prolongation`, { minutes, codeSecondFacteur });
  }

  /** Bracelet remis : la surveillance du retrait reprend. */
  terminerRetrait(enfantId: string): Observable<void> {
    return this.http.delete<void>(this.retrait(enfantId));
  }

  private retrait(enfantId: string): string {
    return `/api/v1/enfants/${enfantId}/bracelet/retrait`;
  }
}
