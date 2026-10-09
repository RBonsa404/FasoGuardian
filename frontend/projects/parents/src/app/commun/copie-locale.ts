import { Injectable, inject } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';

import { ClientBracelet, ClientFamille } from 'api';

/**
 * Ce qui reste consultable d'un enfant sans session ni réseau (écran 14, US-PAR-019) : son identité, les
 * informations médicales que le parent a marquées critiques et le numéro de son bracelet. Rien d'autre
 * n'est gardé sur l'appareil : ni position, ni contacts, ni historique.
 */
export interface FicheLocale {
  readonly id: string;
  readonly prenom: string;
  readonly nom: string;
  /** Date ISO (AAAA-MM-JJ). */
  readonly dateNaissance: string;
  readonly ecole: string | null;
  readonly quartier: string | null;
  readonly groupeSanguin: string | null;
  /** Éléments médicaux critiques, sous la forme « Allergie : arachides ». */
  readonly critiques: readonly { readonly type: string; readonly libelle: string }[];
  readonly bracelet: string | null;
  /** Date ISO de la copie. */
  readonly copieLe: string;
}

/** Table Dexie minimale dont le service a besoin ; isolée pour que les essais la remplacent. */
export interface TableDeFiches {
  toArray(): Promise<FicheLocale[]>;
  bulkPut(fiches: FicheLocale[]): Promise<unknown>;
  clear(): Promise<void>;
}

/**
 * Copie locale des fiches, dans IndexedDB. Elle est rafraîchie à chaque ouverture du tableau de bord et
 * effacée à la déconnexion : un appareil dont le parent s'est déconnecté ne garde rien. IndexedDB peut manquer
 * (navigation privée, stockage refusé) : la copie est alors simplement absente, sans erreur à l'écran.
 */
@Injectable({ providedIn: 'root' })
export class CopieLocale {
  private readonly famille = inject(ClientFamille);
  private readonly bracelets = inject(ClientBracelet);
  protected table: Promise<TableDeFiches | null> | null = null;
  /** Avance à chaque effacement : une synchronisation commencée avant ne doit plus rien écrire. */
  private effacements = 0;

  /** Fiches gardées sur l'appareil, de la plus récemment copiée à la plus ancienne. */
  async fiches(): Promise<FicheLocale[]> {
    try {
      const table = await this.ouvrir();
      return table ? (await table.toArray()).sort((a, b) => b.copieLe.localeCompare(a.copieLe)) : [];
    } catch {
      return [];
    }
  }

  /** Relit les fiches auprès du serveur et remplace la copie ; un échec laisse la copie précédente. */
  async synchroniser(): Promise<void> {
    const effacementsAuDepart = this.effacements;
    try {
      const enfants = await firstValueFrom(this.famille.mesEnfants());
      const copieLe = new Date().toISOString();
      const fiches = await Promise.all(
        enfants.map(async (enfant): Promise<FicheLocale> => {
          const sante = await firstValueFrom(this.famille.sante(enfant.id).pipe(catchError(() => of(null))));
          const bracelet = await firstValueFrom(this.bracelets.bracelet(enfant.id).pipe(catchError(() => of(null))));
          return {
            id: enfant.id,
            prenom: enfant.prenom,
            nom: enfant.nom,
            dateNaissance: enfant.dateNaissance,
            ecole: enfant.profil.ecole,
            quartier: enfant.profil.quartier,
            groupeSanguin: sante?.groupeSanguin ?? null,
            critiques: (sante?.elements ?? []).filter((element) => element.critique).map((element) => ({ type: element.type, libelle: element.libelle })),
            bracelet: bracelet?.numeroSerie ?? null,
            copieLe,
          };
        }),
      );
      const table = await this.ouvrir();
      // Le parent s'est déconnecté pendant la relecture : rien ne doit revenir sur l'appareil.
      if (table && this.effacements === effacementsAuDepart) {
        await table.clear();
        await table.bulkPut(fiches);
      }
    } catch {
      // Hors ligne ou stockage indisponible : la copie précédente, s'il y en a une, reste valable.
    }
  }

  /** Efface tout ce qui a été gardé sur l'appareil. */
  async vider(): Promise<void> {
    this.effacements++;
    try {
      await (await this.ouvrir())?.clear();
    } catch {
      // Rien à effacer si le stockage n'est pas disponible.
    }
  }

  /** Dexie n'est chargé qu'au premier besoin : il ne pèse pas sur l'ouverture de l'application. */
  protected ouvrir(): Promise<TableDeFiches | null> {
    this.table ??= (async () => {
      if (typeof indexedDB === 'undefined') {
        return null;
      }
      const { Dexie } = await import('dexie');
      const base = new Dexie('fasoguardian');
      base.version(1).stores({ fiches: 'id' });
      return base.table<FicheLocale, string>('fiches');
    })();
    return this.table;
  }
}
