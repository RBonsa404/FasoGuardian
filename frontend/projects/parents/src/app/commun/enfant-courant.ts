import { Injectable, signal } from '@angular/core';

const CLE = 'fg.enfant-courant';

/**
 * Enfant affiché sur le tableau de bord, que l'onglet « Carte » reprend. Gardé pour la session de l'onglet
 * seulement : c'est un confort d'affichage, pas une donnée.
 */
@Injectable({ providedIn: 'root' })
export class EnfantCourant {
  readonly id = signal<string | null>(lire());

  choisir(id: string): void {
    this.id.set(id);
    try {
      sessionStorage.setItem(CLE, id);
    } catch {
      // Stockage indisponible (navigation privée) : le choix vaut pour la page ouverte.
    }
  }
}

function lire(): string | null {
  try {
    return sessionStorage.getItem(CLE);
  } catch {
    return null;
  }
}
