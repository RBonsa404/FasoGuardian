import { TestBed } from '@angular/core/testing';

import { FgChoixTheme } from './choix-theme';
import { CLE_THEME, Theme } from './theme';

describe('apparence', () => {
  beforeEach(() => {
    localStorage.removeItem(CLE_THEME);
    document.documentElement.removeAttribute('data-theme');
  });

  it('applique le thème choisi à la page et le garde sur l’appareil', () => {
    const theme = TestBed.inject(Theme);

    theme.choisir('clair');
    expect(document.documentElement.getAttribute('data-theme')).toBe('clair');
    expect(localStorage.getItem(CLE_THEME)).toBe('clair');
    expect(theme.actif()).toBe('clair');

    theme.choisir('sombre');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem(CLE_THEME)).toBe('sombre');
  });

  it('revient à l’apparence du système quand la personne ne choisit plus', () => {
    const theme = TestBed.inject(Theme);
    theme.choisir('clair');

    theme.choisir('systeme');

    expect(localStorage.getItem(CLE_THEME)).toBeNull();
    expect(theme.preference()).toBe('systeme');
    // L'environnement d'essai ne déclare pas de préférence claire : le système vaut sombre.
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('retrouve le choix gardé à l’ouverture suivante', () => {
    localStorage.setItem(CLE_THEME, 'clair');

    const theme = TestBed.inject(Theme);

    expect(theme.preference()).toBe('clair');
    expect(document.documentElement.getAttribute('data-theme')).toBe('clair');
  });

  it('propose trois choix et marque celui qui est en vigueur', async () => {
    const fixture = TestBed.createComponent(FgChoixTheme);
    await fixture.whenStable();
    const boutons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')];

    expect(boutons.map((b) => b.textContent?.trim())).toEqual(['Système', 'Sombre', 'Clair']);
    expect(boutons.map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false']);

    boutons[2].click();
    await fixture.whenStable();

    expect(boutons.map((b) => b.getAttribute('aria-pressed'))).toEqual(['false', 'false', 'true']);
    expect(document.documentElement.getAttribute('data-theme')).toBe('clair');
  });
});
