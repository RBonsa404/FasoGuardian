import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

export type PreferenceTheme = 'systeme' | 'sombre' | 'clair';

/** Clé du choix d'apparence gardé sur l'appareil ; le même script la lit avant le premier affichage (theme.js). */
export const CLE_THEME = 'fg.theme';

const FONDS = { sombre: '#0A0E1A', clair: '#F5F7FB' } as const;

/**
 * Apparence de l'application : celle du système par défaut, ou le thème choisi par la personne. Le thème
 * sombre est celui des jetons de base ; le clair s'applique par l'attribut `data-theme="clair"` de la page.
 */
@Injectable({ providedIn: 'root' })
export class Theme {
  private readonly page = inject(DOCUMENT);
  private readonly systemeClair = signal(false);

  readonly preference = signal<PreferenceTheme>(this.lire());
  /** Thème effectivement affiché. */
  readonly actif = computed<'sombre' | 'clair'>(() => {
    const preference = this.preference();
    return preference === 'systeme' ? (this.systemeClair() ? 'clair' : 'sombre') : preference;
  });

  constructor() {
    const media = this.page.defaultView?.matchMedia?.('(prefers-color-scheme: light)');
    if (media) {
      this.systemeClair.set(media.matches);
      media.addEventListener?.('change', (evenement) => {
        this.systemeClair.set(evenement.matches);
        this.appliquer();
      });
    }
    this.appliquer();
  }

  choisir(preference: PreferenceTheme): void {
    this.preference.set(preference);
    try {
      if (preference === 'systeme') {
        this.page.defaultView?.localStorage.removeItem(CLE_THEME);
      } else {
        this.page.defaultView?.localStorage.setItem(CLE_THEME, preference);
      }
    } catch {
      // Sans stockage, le choix vaut pour la visite en cours.
    }
    this.appliquer();
  }

  private appliquer(): void {
    const racine = this.page.documentElement;
    if (this.actif() === 'clair') {
      racine.setAttribute('data-theme', 'clair');
    } else {
      racine.removeAttribute('data-theme');
    }
    this.page.querySelector('meta[name="theme-color"]')?.setAttribute('content', FONDS[this.actif()]);
  }

  private lire(): PreferenceTheme {
    try {
      const garde = this.page.defaultView?.localStorage.getItem(CLE_THEME);
      return garde === 'sombre' || garde === 'clair' ? garde : 'systeme';
    } catch {
      return 'systeme';
    }
  }
}
