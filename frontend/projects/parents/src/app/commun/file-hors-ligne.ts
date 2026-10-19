import { HttpClient, HttpContext, HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, from, switchMap, throwError } from 'rxjs';

import { ActionMiseEnAttente, GenreActionRejouable, REJOUABLE } from 'api';

/** Écriture gardée sur l'appareil faute de réseau, avec la clé d'idempotence de son premier essai. */
export interface ActionGardee {
  readonly id?: number;
  readonly genre: GenreActionRejouable;
  readonly methode: string;
  readonly url: string;
  readonly corps: unknown;
  readonly cle: string;
  /** Date ISO. */
  readonly creeLe: string;
}

/** Table Dexie minimale dont la file a besoin ; isolée pour que les essais la remplacent. */
export interface TableDActions {
  toArray(): Promise<ActionGardee[]>;
  add(action: ActionGardee): Promise<unknown>;
  delete(id: number): Promise<void>;
  clear(): Promise<void>;
}

/** Marque les renvois de la file : l'intercepteur ne les remet pas en attente une seconde fois. */
const RENVOI = new HttpContextToken<boolean>(() => false);

/** Clé d'idempotence aléatoire, au format attendu par le serveur (16 à 80 caractères). */
function nouvelleCle(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const octets = new Uint8Array(16);
  crypto.getRandomValues(octets);
  return Array.from(octets, (octet) => octet.toString(16).padStart(2, '0')).join('');
}

/**
 * File locale des actions de configuration (FG-DOC-06 §5.4, US-SYS-002) : une écriture rejouable partie sans
 * réseau est gardée dans IndexedDB, puis renvoyée dans l'ordre au retour du réseau avec la même clé
 * d'idempotence, ce qui garantit qu'elle ne s'applique qu'une fois. Une action refusée au renvoi (règle
 * métier, droits) est retirée et comptée ; une panne ou une session expirée la laisse en attente. La file est
 * effacée à la déconnexion, comme le reste de la copie locale.
 */
@Injectable({ providedIn: 'root' })
export class FileHorsLigne {
  private readonly http = inject(HttpClient);
  protected table: Promise<TableDActions | null> | null = null;
  private envoi: Promise<void> | null = null;

  /** Actions en attente du réseau, de la plus ancienne à la plus récente. */
  readonly enAttente = signal<readonly ActionGardee[]>([]);
  /** Actions que le serveur a refusées au renvoi depuis l'ouverture de l'application. */
  readonly refusees = signal(0);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => void this.envoyer());
    }
    void this.relire().then(() => this.envoyer());
  }

  async garder(requete: HttpRequest<unknown>, genre: GenreActionRejouable, cle: string): Promise<void> {
    try {
      const table = await this.ouvrir();
      await table?.add({ genre, methode: requete.method, url: requete.urlWithParams, corps: requete.body ?? null, cle, creeLe: new Date().toISOString() });
    } catch {
      // Stockage indisponible : l'action est perdue, mais le message d'attente n'a pas menti sur l'envoi.
    }
    await this.relire();
  }

  /** Renvoie les actions dans l'ordre ; s'arrête à la première qui ne peut pas encore partir. */
  envoyer(): Promise<void> {
    this.envoi ??= (async () => {
      try {
        const table = await this.ouvrir();
        for (const action of await this.lire(table)) {
          try {
            await firstValueFrom(
              this.http.request(action.methode, action.url, {
                body: action.corps,
                headers: { 'Idempotency-Key': action.cle },
                context: new HttpContext().set(RENVOI, true),
              }),
            );
          } catch (erreur) {
            const statut = erreur instanceof HttpErrorResponse ? erreur.status : 0;
            if (statut === 0 || statut === 401 || statut >= 500) {
              break;
            }
            this.refusees.update((nombre) => nombre + 1);
          }
          await table?.delete(action.id!);
          await this.relire();
        }
      } finally {
        this.envoi = null;
      }
    })();
    return this.envoi;
  }

  async vider(): Promise<void> {
    try {
      await (await this.ouvrir())?.clear();
    } catch {
      // Rien à effacer si le stockage n'est pas disponible.
    }
    this.enAttente.set([]);
  }

  private async relire(): Promise<void> {
    this.enAttente.set(await this.lire(await this.ouvrir().catch(() => null)));
  }

  private async lire(table: TableDActions | null): Promise<ActionGardee[]> {
    try {
      return table ? (await table.toArray()).sort((a, b) => (a.id ?? 0) - (b.id ?? 0)) : [];
    } catch {
      return [];
    }
  }

  /** Base distincte de la copie des fiches : chacune évolue à son rythme. */
  protected ouvrir(): Promise<TableDActions | null> {
    this.table ??= (async () => {
      if (typeof indexedDB === 'undefined') {
        return null;
      }
      const { Dexie } = await import('dexie');
      const base = new Dexie('fasoguardian-actions');
      base.version(1).stores({ actions: '++id' });
      return base.table<ActionGardee, number>('actions') as unknown as TableDActions;
    })();
    return this.table;
  }
}

/**
 * Ajoute une clé d'idempotence aux écritures rejouables et les garde dans la file quand le réseau manque : le
 * composant reçoit alors une `ActionMiseEnAttente`, dont le message dit que l'action partira plus tard.
 */
export const intercepteurHorsLigne: HttpInterceptorFn = (requete, suivant) => {
  const genre = requete.context.get(REJOUABLE);
  if (!genre || requete.context.get(RENVOI)) {
    return suivant(requete);
  }
  const cle = requete.headers.get('Idempotency-Key') ?? nouvelleCle();
  const avecCle = requete.clone({ setHeaders: { 'Idempotency-Key': cle } });
  const file = inject(FileHorsLigne);
  return suivant(avecCle).pipe(
    catchError((erreur: unknown) =>
      erreur instanceof HttpErrorResponse && erreur.status === 0
        ? from(file.garder(avecCle, genre, cle)).pipe(switchMap(() => throwError(() => new ActionMiseEnAttente(genre))))
        : throwError(() => erreur),
    ),
  );
};
