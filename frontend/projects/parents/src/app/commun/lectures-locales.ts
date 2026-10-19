import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, from, of, switchMap, tap, throwError } from 'rxjs';

import { FicheSante } from 'api';

/** Lecture gardée sur l'appareil : la dernière réponse reçue pour cette adresse, et son heure. */
export interface LectureGardee {
  readonly url: string;
  readonly corps: unknown;
  /** Date ISO de la réponse gardée. */
  readonly recueLe: string;
}

/** Table Dexie minimale dont le service a besoin ; isolée pour que les essais la remplacent. */
export interface TableDeLectures {
  get(url: string): Promise<LectureGardee | undefined>;
  put(lecture: LectureGardee): Promise<unknown>;
  clear(): Promise<void>;
}

/**
 * Lectures consultables hors ligne (FG-DOC-06 §5.4, tableau 8) : la liste et la fiche des enfants, leurs
 * contacts, leurs informations médicales critiques et la dernière position connue, toujours datée. Rien
 * d'autre : ni trajets, ni alertes, ni historique.
 */
const LECTURES = [/^\/api\/v1\/enfants$/, /^\/api\/v1\/enfants\/[0-9a-f-]{36}$/, /^\/api\/v1\/enfants\/[0-9a-f-]{36}\/(contacts|sante|position)$/];

/** De la fiche médicale, seuls les éléments marqués critiques restent sur l'appareil. */
function aGarder(url: string, corps: unknown): unknown {
  if (url.endsWith('/sante') && corps) {
    const fiche = corps as FicheSante;
    return { ...fiche, elements: fiche.elements.filter((element) => element.critique) };
  }
  return corps;
}

/** État du réseau vu par l'application, et heure des données gardées qu'elle affiche faute de réseau. */
@Injectable({ providedIn: 'root' })
export class LecturesLocales {
  protected table: Promise<TableDeLectures | null> | null = null;

  /** Faux quand le téléphone n'a plus de réseau, ou qu'une lecture vient d'échouer faute de réseau. */
  readonly enLigne = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  /** Heure de la plus ancienne copie affichée depuis la perte du réseau (date ISO), ou null. */
  readonly copieDu = signal<string | null>(null);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.retablir());
      window.addEventListener('offline', () => this.enLigne.set(false));
    }
  }

  estGardee(url: string): boolean {
    return LECTURES.some((motif) => motif.test(url));
  }

  async garder(url: string, corps: unknown): Promise<void> {
    try {
      await (await this.ouvrir())?.put({ url, corps: aGarder(url, corps), recueLe: new Date().toISOString() });
    } catch {
      // Stockage indisponible : la lecture n'est simplement pas gardée.
    }
  }

  async relire(url: string): Promise<LectureGardee | null> {
    try {
      const lecture = (await (await this.ouvrir())?.get(url)) ?? null;
      if (lecture) {
        this.enLigne.set(false);
        const actuelle = this.copieDu();
        this.copieDu.set(actuelle && actuelle < lecture.recueLe ? actuelle : lecture.recueLe);
      }
      return lecture;
    } catch {
      return null;
    }
  }

  /** Une réponse du serveur est arrivée : le réseau est revenu. */
  retablir(): void {
    this.enLigne.set(true);
    this.copieDu.set(null);
  }

  async vider(): Promise<void> {
    try {
      await (await this.ouvrir())?.clear();
    } catch {
      // Rien à effacer si le stockage n'est pas disponible.
    }
    this.copieDu.set(null);
  }

  protected ouvrir(): Promise<TableDeLectures | null> {
    this.table ??= (async () => {
      if (typeof indexedDB === 'undefined') {
        return null;
      }
      const { Dexie } = await import('dexie');
      const base = new Dexie('fasoguardian-lectures');
      base.version(1).stores({ lectures: 'url' });
      return base.table<LectureGardee, string>('lectures') as unknown as TableDeLectures;
    })();
    return this.table;
  }
}

/** Réseau d'abord ; sans réseau, la dernière réponse gardée pour ces lectures, avec l'heure à laquelle elle date. */
export const intercepteurLecturesLocales: HttpInterceptorFn = (requete, suivant) => {
  const lectures = inject(LecturesLocales);
  if (requete.method !== 'GET' || requete.responseType !== 'json' || !lectures.estGardee(requete.urlWithParams)) {
    return suivant(requete);
  }
  return suivant(requete).pipe(
    tap((evenement) => {
      if (evenement instanceof HttpResponse) {
        lectures.retablir();
        void lectures.garder(requete.urlWithParams, evenement.body);
      }
    }),
    catchError((erreur: unknown) =>
      erreur instanceof HttpErrorResponse && erreur.status === 0
        ? from(lectures.relire(requete.urlWithParams)).pipe(
            switchMap((lecture) => (lecture ? of(new HttpResponse({ status: 200, body: lecture.corps, url: requete.urlWithParams })) : throwError(() => erreur))),
          )
        : throwError(() => erreur),
    ),
  );
};
