import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';

export type TypeConsentement =
  | 'CONDITIONS_GENERALES'
  | 'DONNEES_ENFANT'
  | 'PARTAGE_FORCES_SECURITE'
  | 'COMMUNICATION_SMS';

export interface JetonAcces {
  readonly jetonAcces: string;
  readonly expireDansSecondes: number;
}

export interface Compte {
  readonly id: string;
  readonly statut: 'EN_INSTRUCTION' | 'ACTIF' | 'SUSPENDU' | 'CLOS';
  readonly telephoneMasque: string | null;
  readonly roles: readonly string[];
}

/** En-tête exigé par le serveur sur les points qui lisent le cookie de rafraîchissement. */
export const ENTETE_ANTI_CSRF = 'X-FG-Requete';

const BASE = '/api/v1/auth';
const AVEC_COOKIE = { withCredentials: true, headers: { [ENTETE_ANTI_CSRF]: '1' } };

/**
 * Session de l'utilisateur. Le jeton d'accès ne vit qu'en mémoire : il n'est jamais écrit dans le
 * stockage du navigateur. Le jeton de rafraîchissement est un cookie HttpOnly que le script ne voit pas.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly jeton = signal<string | null>(null);
  private readonly expiree = signal(false);

  readonly jetonAcces = this.jeton.asReadonly();
  readonly ouverte = computed(() => this.jeton() !== null);
  /** Vrai lorsque le rafraîchissement a échoué : une réauthentification complète est demandée (US-PAR-019). */
  readonly reauthentificationRequise = this.expiree.asReadonly();

  ouvrir(jeton: JetonAcces): void {
    this.jeton.set(jeton.jetonAcces);
    this.expiree.set(false);
  }

  fermer(): void {
    this.jeton.set(null);
  }

  signalerExpiration(): void {
    this.jeton.set(null);
    this.expiree.set(true);
  }
}

/** Client des points d'inscription et de session. */
@Injectable({ providedIn: 'root' })
export class ClientAuthentification {
  private readonly http = inject(HttpClient);
  private readonly session = inject(Session);
  private rafraichissementEnCours: Observable<JetonAcces> | null = null;

  demanderCode(telephone: string): Observable<void> {
    return this.http.post<void>(`${BASE}/inscription/numero`, { telephone });
  }

  verifierCode(telephone: string, code: string): Observable<string> {
    return this.http
      .post<{ preuve: string }>(`${BASE}/inscription/code`, { telephone, code })
      .pipe(map((reponse) => reponse.preuve));
  }

  terminerInscription(preuve: string, motDePasse: string, consentements: TypeConsentement[]): Observable<JetonAcces> {
    return this.http
      .post<JetonAcces>(`${BASE}/inscription/terminer`, { preuve, motDePasse, consentements }, { withCredentials: true })
      .pipe(tap((jeton) => this.session.ouvrir(jeton)));
  }

  connecter(telephone: string, motDePasse: string): Observable<JetonAcces> {
    return this.http
      .post<JetonAcces>(`${BASE}/connexion`, { telephone, motDePasse }, { withCredentials: true })
      .pipe(tap((jeton) => this.session.ouvrir(jeton)));
  }

  /** Un seul échange à la fois : les requêtes concurrentes en échec partagent le même rafraîchissement. */
  rafraichir(): Observable<JetonAcces> {
    this.rafraichissementEnCours ??= this.http.post<JetonAcces>(`${BASE}/rafraichir`, null, AVEC_COOKIE).pipe(
      tap({
        next: (jeton) => this.session.ouvrir(jeton),
        error: () => this.session.signalerExpiration(),
      }),
      finalize(() => (this.rafraichissementEnCours = null)),
      shareReplay(1),
    );
    return this.rafraichissementEnCours;
  }

  deconnecter(): Observable<void> {
    return this.http
      .post<void>(`${BASE}/deconnexion`, null, AVEC_COOKIE)
      .pipe(finalize(() => this.session.fermer()));
  }

  moi(): Observable<Compte> {
    return this.http.get<Compte>('/api/v1/moi');
  }
}
