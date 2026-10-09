import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('coque du site', () => {
  it('porte la navigation, le lien vers l’espace parents et le pied de page', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('main#contenu')).not.toBeNull();
    const liens = [...page.querySelectorAll('header nav a')].map((a) => a.textContent?.trim());
    expect(liens).toEqual(['Le bracelet', 'Fonctionnement', 'Offres', 'Écoles', 'Points relais', 'Espace parents']);
    expect(page.querySelector('header a[href="/app/"]')).not.toBeNull();
    expect([...page.querySelectorAll('footer nav')].map((n) => n.getAttribute('aria-label'))).toEqual(['Produit', 'Aide', 'Légal']);
  });

  it('replie la navigation dans un menu sur petit écran', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const bouton = page.querySelector('button[aria-controls="menu-mobile"]') as HTMLButtonElement;

    expect(page.querySelector('#menu-mobile')).toBeNull();
    bouton.click();
    await fixture.whenStable();

    expect(bouton.getAttribute('aria-expanded')).toBe('true');
    expect(page.querySelectorAll('#menu-mobile a').length).toBe(6);
  });
});
